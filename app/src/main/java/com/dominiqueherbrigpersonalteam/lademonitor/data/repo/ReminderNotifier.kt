package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dominiqueherbrigpersonalteam.lademonitor.LademonitorApp
import com.dominiqueherbrigpersonalteam.lademonitor.MainActivity
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.notifications.ReminderAlarmReceiver
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Lokale Mitteilungen fuer Erinnerungen - in beiden Modi, auch ohne Server (der verschickt im
 * Server-Modus zusaetzlich Mails, siehe `notifications.py`).
 *
 * Je Erinnerung bis zu zwei Termine um 09:00 Uhr: zu Beginn des Vorlaufs (Faelligkeitsdatum minus
 * `lead_days`) und am naechsten Faelligkeitstag (frueheres aus Datum und km-Prognose). Nur Termine
 * in der Zukunft. Geplant wird mit [AlarmManager.set] - bewusst ungenau, damit keine Berechtigung
 * fuer exakte Alarme noetig ist; eine Mitteilung ein paar Minuten spaeter ist hier egal. Der
 * [ReminderAlarmReceiver] zeigt sie an und plant nach einem Neustart alles neu.
 *
 * Neu geplant wird komplett (alte Alarme weg, neue rein): nach jeder Aenderung an Erinnerungen,
 * nach jedem Sync und beim App-Start. Welche Alarme stehen, merkt sich die App in den
 * SharedPreferences - nur so lassen sie sich spaeter wieder abbestellen.
 */
object ReminderNotifier {
    private const val PREFS = "reminder_notifications"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PERMISSION_ASKED = "permissionAsked"
    private const val KEY_SCHEDULED = "scheduled"

    const val CHANNEL_ID = "reminders"
    const val ACTION_NOTIFY = "com.dominiqueherbrigpersonalteam.lademonitor.action.REMINDER_NOTIFY"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_NOTIFICATION_ID = "notificationId"

    private const val STAGE_LEAD = "lead"
    private const val STAGE_DUE = "due"
    private val NOTIFY_TIME: LocalTime = LocalTime.of(9, 0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    // Lazy statt lateinit: ein Sync kann rescheduleAsync() schon vor init() ausloesen.
    private val prefs: SharedPreferences by lazy {
        LademonitorApp.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val _enabled = MutableStateFlow(true)
    /** Schalter "Mitteilungen fuer Erinnerungen" in den Einstellungen, Standard an. */
    val enabled: StateFlow<Boolean> = _enabled

    fun init(context: Context) {
        _enabled.value = prefs.getBoolean(KEY_ENABLED, true)
        createChannel(context.applicationContext)
        rescheduleAsync()
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        rescheduleAsync()
    }

    /**
     * Ob beim Speichern einer Erinnerung nach der Mitteilungs-Berechtigung gefragt werden soll
     * (Android 13+): nur einmal, nur wenn sie fehlt und die Mitteilungen eingeschaltet sind.
     */
    fun shouldRequestPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (!_enabled.value || prefs.getBoolean(KEY_PERMISSION_ASKED, false)) return false
        return !hasPermission(context)
    }

    fun markPermissionRequested() {
        prefs.edit().putBoolean(KEY_PERMISSION_ASKED, true).apply()
    }

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun rescheduleAsync() {
        scope.launch { reschedule() }
    }

    /** Wirft nie - eine fehlende Mitteilung ist kein Grund, irgendetwas anderes scheitern zu lassen. */
    suspend fun reschedule() {
        mutex.withLock {
            try {
                val context = LademonitorApp.appContext
                val alarms = context.getSystemService(AlarmManager::class.java) ?: return
                for (key in prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()) {
                    val pending = PendingIntent.getBroadcast(
                        context, 0, baseIntent(context, key),
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                    )
                    if (pending != null) {
                        alarms.cancel(pending)
                        pending.cancel()
                    }
                }
                prefs.edit().putStringSet(KEY_SCHEDULED, emptySet()).apply()
                if (!_enabled.value || AppSettings.appMode.value == AppMode.UNDECIDED) return

                val zone = ZoneId.systemDefault()
                val now = System.currentTimeMillis()
                val scheduled = HashSet<String>()
                for (item in LocalDataStore.evaluatedReminders()) {
                    val reminder = item.reminder
                    val state = item.state
                    val name = listOfNotNull(reminder.displayTitle, item.vehicleName).joinToString(" · ")
                    val dueDay = reminder.dueDate?.let { LocalFeeAllocator.day(it) }

                    fun plan(stage: String, day: LocalDate, text: String) {
                        val at = day.atTime(NOTIFY_TIME).atZone(zone).toInstant().toEpochMilli()
                        if (at <= now) return
                        val key = "${reminder.id}/$stage"
                        val intent = baseIntent(context, key)
                            .putExtra(EXTRA_TITLE, name)
                            .putExtra(EXTRA_TEXT, text)
                            .putExtra(EXTRA_NOTIFICATION_ID, key.hashCode())
                        val pending = PendingIntent.getBroadcast(
                            context, 0, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        alarms.set(AlarmManager.RTC_WAKEUP, at, pending)
                        scheduled.add(key)
                    }

                    val next = state.nextDueDate
                    if (next != null) {
                        val text = if (dueDay != null && !next.isBefore(dueDay)) {
                            context.getString(R.string.reminder_notification_due_today)
                        } else {
                            // Die km-Prognose liegt vor dem Datum (oder es gibt keins).
                            context.getString(
                                R.string.reminder_notification_due_km,
                                reminder.dueKm?.let { Fmt.km(it) } ?: ""
                            )
                        }
                        plan(STAGE_DUE, next, text)
                    }
                    if (dueDay != null) {
                        val leadStart = dueDay.minusDays(reminder.leadDays.toLong())
                        if (leadStart != next && leadStart.isBefore(dueDay)) {
                            plan(
                                STAGE_LEAD, leadStart,
                                context.getString(
                                    R.string.reminder_notification_lead,
                                    Fmt.dateMedium(LocalReminderCalculator.epochMillis(dueDay))
                                )
                            )
                        }
                    }
                }
                prefs.edit().putStringSet(KEY_SCHEDULED, scheduled).apply()
            } catch (e: Exception) {
                // Siehe KDoc.
            }
        }
    }

    /** Identifiziert einen Alarm ueber die Daten-URI - Extras zaehlen beim Abgleich nicht. */
    private fun baseIntent(context: Context, key: String): Intent =
        Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ACTION_NOTIFY)
            .setData(Uri.parse("lademonitor-reminder://alarm/$key"))

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.reminder_notification_channel_description) }
        manager.createNotificationChannel(channel)
    }

    /** Vom [ReminderAlarmReceiver] aufgerufen, wenn ein Termin erreicht ist. */
    @SuppressLint("MissingPermission") // hasPermission() wird direkt davor geprueft
    fun post(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, true) || !hasPermission(context)) return
        createChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(intent.getStringExtra(EXTRA_TITLE) ?: context.getString(R.string.reminders_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0), notification)
        } catch (e: SecurityException) {
            // Berechtigung zwischen Pruefung und Anzeige entzogen.
        }
    }
}
