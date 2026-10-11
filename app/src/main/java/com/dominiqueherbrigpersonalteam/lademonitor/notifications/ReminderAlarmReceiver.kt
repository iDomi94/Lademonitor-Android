package com.dominiqueherbrigpersonalteam.lademonitor.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.ReminderNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Zeigt eine faellige Erinnerung als Mitteilung an ([ReminderNotifier.ACTION_NOTIFY]) und plant
 * nach einem Neustart bzw. App-Update alle Termine neu - der AlarmManager vergisst sie dabei.
 * Nicht exportiert: die System-Broadcasts kommen trotzdem an, fremde Apps koennen aber keine
 * Mitteilungen im Namen dieser App ausloesen.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderNotifier.ACTION_NOTIFY -> ReminderNotifier.post(context, intent)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val pending = goAsync()
                scope.launch {
                    try {
                        ReminderNotifier.reschedule()
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
