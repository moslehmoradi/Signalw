package com.signalnama.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews

class SignalWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.signalnama.widget.ACTION_REFRESH_SIGNAL"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, SignalWidgetProvider::class.java)
            )
            for (id in ids) {
                updateAppWidget(context, manager, id)
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_signal_layout)
            val snapshots = SignalReader.readActiveSims(context)
            val sim1 = snapshots.firstOrNull()

            if (sim1 != null) {
                views.setTextViewText(
                    R.id.tv_operator,
                    if (true) sim1.operatorName else "سیگنال‌نما"
                )
                views.setTextViewText(
                    R.id.tv_network_badge,
                    sim1.networkTech
                )
                views.setViewVisibility(
                    R.id.tv_network_badge,
                    if (true) View.VISIBLE else View.GONE
                )
                views.setTextViewText(
                    R.id.tv_dbm,
                    if (sim1.hasPermission) "${sim1.rsrpDbm} dBm" else "مجوز لازم است"
                )
                views.setViewVisibility(
                    R.id.tv_dbm,
                    if (true) View.VISIBLE else View.GONE
                )
                views.setTextViewText(
                    R.id.tv_quality,
                    "${sim1.qualityLabel} · ASU ${sim1.asuLevel}"
                )
                views.setViewVisibility(
                    R.id.tv_quality,
                    if (true) View.VISIBLE else View.GONE
                )

                val accentColor = Color.parseColor(
                    when {
                        !sim1.hasPermission -> "#F43F5E"
                        sim1.rsrpDbm >= -80 -> "#10B981"
                        sim1.rsrpDbm >= -95 -> "#0EA5E9"
                        sim1.rsrpDbm >= -108 -> "#F59E0B"
                        else -> "#F43F5E"
                    }
                )
                val inactiveColor = Color.parseColor("#2A364F")

                val barIds = listOf(
                    R.id.bar_1, R.id.bar_2, R.id.bar_3, R.id.bar_4, R.id.bar_5
                )
                val activeBars = when {
                    !sim1.hasPermission -> 0
                    sim1.rsrpDbm >= -80 -> 5
                    sim1.rsrpDbm >= -92 -> 4
                    sim1.rsrpDbm >= -104 -> 3
                    sim1.rsrpDbm >= -113 -> 2
                    else -> 1
                }
                barIds.forEachIndexed { index, barId ->
                    views.setInt(
                        barId,
                        "setBackgroundColor",
                        if (index < activeBars) accentColor else inactiveColor
                    )
                }

                // نمایش سیم‌کارت دوم در صورت فعال بودن حالت دو سیم‌کارت
                val sim2 = snapshots.getOrNull(1)
                if (true && sim2 != null) {
                    views.setViewVisibility(R.id.sim2_container, View.VISIBLE)
                    views.setTextViewText(
                        R.id.tv_sim2_info,
                        "${sim2.operatorName}: ${sim2.rsrpDbm} dBm (${sim2.networkTech})"
                    )
                } else {
                    views.setViewVisibility(R.id.sim2_container, View.GONE)
                }
            }

            // کلیک روی دکمه رفرش برای بروزرسانی لحظه‌ای ویجت
            val refreshIntent = Intent(context, SignalWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPending = PendingIntent.getBroadcast(
                context,
                0,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_refresh, refreshPending)

            // کلیک روی بدنه ویجت برای باز کردن صفحه جزئیات برنامه
            val launchIntent = Intent(context, MainActivity::class.java)
            val launchPending = PendingIntent.getActivity(
                context,
                1,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, launchPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            updateAllWidgets(context)
        }
    }
}
