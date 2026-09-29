package com.dominiqueherbrigpersonalteam.lademonitor.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.dominiqueherbrigpersonalteam.lademonitor.MainActivity
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.WidgetSnapshotWriter
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Homescreen-Widget - Gegenstueck zu `LademonitorWidget.swift`: Kosten und kWh des laufenden
 * Monats, ab mittlerer Breite zusaetzlich der letzte Ladevorgang. Zeigt nur, was
 * [WidgetSnapshotWriter] zuletzt geschrieben hat; ein Tipp oeffnet die App.
 */
class LademonitorWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        render(context, manager, id)
    }

    companion object {
        /** Ab dieser Breite (dp) passt der letzte Ladevorgang neben die Monatszahlen. */
        private const val WIDE_MIN_WIDTH_DP = 200

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LademonitorWidgetProvider::class.java))
            ids.forEach { render(context, manager, it) }
        }

        private fun money(v: Double) = Fmt.n("%.2f", v) + " €"

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_lademonitor)
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            val zone = ZoneId.systemDefault()
            views.setTextViewText(
                R.id.widget_month,
                Instant.now().atZone(zone).format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
            )

            val snapshot = WidgetSnapshotWriter.read(context)
            if (snapshot == null) {
                views.setTextViewText(R.id.widget_cost, "–")
                views.setTextViewText(R.id.widget_month_detail, context.getString(R.string.widget_placeholder))
                views.setViewVisibility(R.id.widget_last, View.GONE)
                manager.updateAppWidget(id, views)
                return
            }

            views.setTextViewText(R.id.widget_cost, money(snapshot.monthCost))
            views.setTextViewText(
                R.id.widget_month_detail,
                Fmt.n("%.1f", snapshot.monthKwh) + " kWh · " +
                    context.resources.getQuantityString(R.plurals.widget_sessions, snapshot.monthSessions, snapshot.monthSessions)
            )

            val width = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            if (width < WIDE_MIN_WIDTH_DP) {
                views.setViewVisibility(R.id.widget_last, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_last, View.VISIBLE)
                val start = snapshot.lastStartTime
                if (start == null) {
                    views.setTextViewText(R.id.widget_last_line1, context.getString(R.string.widget_no_session))
                    views.setTextViewText(R.id.widget_last_line2, "")
                } else {
                    val date = Instant.ofEpochMilli(start).atZone(zone)
                        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
                    views.setTextViewText(
                        R.id.widget_last_line1,
                        listOfNotNull(date, snapshot.lastProviderName).joinToString(" · ")
                    )
                    val soc = if (snapshot.lastSocStart != null && snapshot.lastSocEnd != null)
                        "${snapshot.lastSocStart} → ${snapshot.lastSocEnd} %" else null
                    views.setTextViewText(
                        R.id.widget_last_line2,
                        listOfNotNull(
                            snapshot.lastEnergyKwh?.let { Fmt.n("%.1f", it) + " kWh" },
                            snapshot.lastCost?.let { money(it) },
                            soc
                        ).joinToString(" · ")
                    )
                }
            }
            manager.updateAppWidget(id, views)
        }
    }
}
