package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.CostCategory
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Reminder
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReminderKind
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReminderStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Erinnerungen: Status, km-Prognose und naechste Faelligkeit - 1:1-Port von `reminders.py` im
 * Server (dort haelt `tests/test_reminders.py` die Faelle fest). Regelaenderungen dort MUESSEN
 * hier mitgezogen werden.
 *
 * - **Status:** `overdue`, wenn das Datum VERSTRICHEN ist (der Faelligkeitstag selbst zaehlt noch
 *   als "bald faellig") oder der km-Stand erreicht ist; `due_soon` innerhalb des Vorlaufs; sonst
 *   `ok`. Ohne jede Faelligkeit: `done`, wenn schon einmal erledigt, sonst `ok`.
 * - **km-Prognose:** juengster km-Stand der Ladevorgaenge, Fahrleistung aus 90 Tagen mit dem
 *   letzten Stand VOR dem Fenster als Anker, mindestens 14 Tage Spanne, sonst die ganze Historie.
 * - **Erledigt:** Arten mit festem Kalendertag (Reifenwechsel, Versicherung, Steuer, THG) lassen
 *   den ALTEN Faelligkeitstag in Wiederholungsschritten weiterlaufen, bis er nach dem
 *   Erledigungsdatum liegt; alle anderen rechnen ab dem Erledigungsdatum. km ab dem km-Stand beim
 *   Erledigen.
 *
 * Datumsangaben sind Kalendertage in der Zeitzone des Geraets ([LocalFeeAllocator.day]).
 */
object LocalReminderCalculator {

    private const val MILEAGE_WINDOW_DAYS = 90L
    private const val MIN_MILEAGE_SPAN_DAYS = 14L
    private const val DEFAULT_LEAD_DAYS = 30
    private const val DEFAULT_LEAD_KM = 1000

    /** Arten, deren Faelligkeit an einem festen Kalendertag haengt. */
    val FIXED_DATE_KINDS = setOf(
        ReminderKind.TIRE_WINTER, ReminderKind.TIRE_SUMMER, ReminderKind.INSURANCE,
        ReminderKind.TAX, ReminderKind.THG
    )

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Aktueller km-Stand und Fahrleistung eines Fahrzeugs. */
    data class Mileage(val currentKm: Int?, val kmPerDay: Double?)

    data class State(
        val status: ReminderStatus,
        /** Datum, an dem `due_km` bei der aktuellen Fahrleistung erreicht ist. */
        val kmForecastDate: LocalDate?,
        val kmRemaining: Int?,
        val daysRemaining: Long?,
        /** Frueheres aus Datum und Prognose - zum Sortieren und fuer die Mitteilungen. */
        val nextDueDate: LocalDate?
    )

    /** Eine Erinnerung samt Auswertung, fuer Liste, Dashboard und Mitteilungen. */
    data class Evaluated(
        val reminder: Reminder,
        val state: State,
        val mileage: Mileage,
        val vehicleName: String?
    )

    /** Aus den Ladevorgaengen EINES Fahrzeugs (beliebige Reihenfolge), wie `mileage()`. */
    fun mileage(sessions: List<ChargingSession>): Mileage {
        val points = sessions.mapNotNull { s -> s.odometerKm?.let { s.startTime to it } }
            .sortedWith(compareBy<Pair<Long, Int>>({ it.first }, { it.second }))
        if (points.isEmpty()) return Mileage(null, null)
        val (lastTime, lastKm) = points.last()
        val windowStart = lastTime - MILEAGE_WINDOW_DAYS * MILLIS_PER_DAY
        // Anker: der letzte Stand VOR dem Fenster, damit die Spanne das ganze Fenster abdeckt.
        var anchor = points.lastOrNull { it.first < windowStart } ?: points.first()
        var span = days(anchor.first, lastTime)
        if (span < MIN_MILEAGE_SPAN_DAYS) {
            anchor = points.first()
            span = days(anchor.first, lastTime)
        }
        val rate = if (span >= MIN_MILEAGE_SPAN_DAYS && lastKm >= anchor.second) {
            (lastKm - anchor.second).toDouble() / span
        } else null
        return Mileage(lastKm, rate)
    }

    /** `timedelta.days`: ganze Tage, abgerundet. */
    private fun days(from: Long, to: Long): Long = Math.floorDiv(to - from, MILLIS_PER_DAY)

    private const val MILLIS_PER_DAY = 86_400_000L

    fun evaluate(reminder: Reminder, m: Mileage, today: LocalDate = LocalDate.now()): State {
        val dueDay = reminder.dueDate?.let { LocalFeeAllocator.day(it) }
        val dueKm = reminder.dueKm
        val leadDays = reminder.leadDays
        val leadKm = reminder.leadKm

        val daysRemaining = dueDay?.let { ChronoUnit.DAYS.between(today, it) }
        var kmRemaining: Int? = null
        var forecast: LocalDate? = null
        if (dueKm != null && m.currentKm != null) {
            val remaining = dueKm - m.currentKm
            kmRemaining = remaining
            val rate = m.kmPerDay
            if (remaining <= 0) {
                forecast = today
            } else if (rate != null && rate != 0.0) {
                // Python `round()` rundet halbe Werte zur geraden Zahl - Math.rint ebenso.
                forecast = today.plusDays(Math.rint(remaining / rate).toLong())
            }
        }

        val status = when {
            dueDay == null && dueKm == null ->
                if (reminder.lastDoneDate != null) ReminderStatus.DONE else ReminderStatus.OK
            (daysRemaining != null && daysRemaining < 0) || (kmRemaining != null && kmRemaining <= 0) ->
                ReminderStatus.OVERDUE
            (daysRemaining != null && daysRemaining <= leadDays) || (kmRemaining != null && kmRemaining <= leadKm) ->
                ReminderStatus.DUE_SOON
            else -> ReminderStatus.OK
        }

        return State(
            status = status,
            kmForecastDate = forecast,
            kmRemaining = kmRemaining,
            daysRemaining = daysRemaining,
            nextDueDate = listOfNotNull(dueDay, forecast).minOrNull()
        )
    }

    /** Vorschlag fuer die erste Faelligkeit einer Vorlage mit festem Tag (Reifenwechsel). */
    fun firstDueDate(kind: ReminderKind, today: LocalDate = LocalDate.now()): LocalDate? {
        val (month, day) = when (kind) {
            ReminderKind.TIRE_WINTER -> 10 to 15
            ReminderKind.TIRE_SUMMER -> 4 to 15
            else -> return null
        }
        val candidate = LocalDate.of(today.year, month, day)
        return if (!candidate.isBefore(today)) candidate else LocalDate.of(today.year + 1, month, day)
    }

    /** Naechste Faelligkeit nach "Erledigt", wie `next_due()`. */
    fun nextDue(reminder: Reminder, doneDay: LocalDate, doneKm: Int?): Pair<LocalDate?, Int?> {
        var nextDay: LocalDate? = null
        val months = reminder.repeatMonths
        val oldDay = reminder.dueDate?.let { LocalFeeAllocator.day(it) }
        if (months != null && months != 0) {
            if (reminder.kindValue in FIXED_DATE_KINDS && oldDay != null) {
                var k = 1L
                var candidate = LocalFeeAllocator.addMonths(oldDay, months.toLong())
                while (!candidate.isAfter(doneDay)) {
                    k += 1
                    candidate = LocalFeeAllocator.addMonths(oldDay, months * k)
                }
                nextDay = candidate
            } else {
                nextDay = LocalFeeAllocator.addMonths(doneDay, months.toLong())
            }
        }
        val repeatKm = reminder.repeatKm
        val nextKm = if (repeatKm != null && repeatKm != 0 && doneKm != null) doneKm + repeatKm else null
        return nextDay to nextKm
    }

    /** Kostenkategorie, die "Erledigt" vorschlaegt; THG ist eine Einnahme und bekommt keine. */
    fun costCategory(kind: ReminderKind): CostCategory? = kind.costCategory

    /** Dringendste zuerst: ueberfaellig, bald faellig, dann nach naechstem Datum (ohne zuletzt). */
    val urgencyOrder: Comparator<Evaluated> =
        compareByDescending<Evaluated> { it.state.status.rank }
            .thenBy { it.state.nextDueDate == null }
            .thenBy { it.state.nextDueDate ?: LocalDate.MAX }

    /**
     * Alle Erinnerungen mit Auswertung, dringendste zuerst. [sessions] und [reminders] muessen
     * dieselbe Fahrzeug-ID-Form tragen (siehe LocalDataStore.reminderBasis).
     */
    fun evaluateAll(
        reminders: List<Reminder>,
        sessions: List<ChargingSession>,
        vehicleNames: Map<String, String>,
        today: LocalDate = LocalDate.now()
    ): List<Evaluated> {
        val byVehicle = sessions.groupBy { it.vehicleId }
        val mileages = HashMap<String, Mileage>()
        return reminders.map { r ->
            val m = mileages.getOrPut(r.vehicleId) { mileage(byVehicle[r.vehicleId].orEmpty()) }
            Evaluated(r, evaluate(r, m, today), m, vehicleNames[r.vehicleId])
        }.sortedWith(urgencyOrder)
    }

    /** Lokale Mitternacht eines Kalendertags in Epoch-Millis, wie die Datumsfelder gespeichert sind. */
    fun epochMillis(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

    fun day(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
