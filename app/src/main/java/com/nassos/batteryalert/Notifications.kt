package com.nassos.batteryalert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object Notifications {
    private const val CH_MONITOR = "monitor"
    private const val CH_ALERTS = "alerts"

    const val ID_MONITOR = 1

    fun alertId(kind: AlertKind) = 10 + kind.ordinal

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_MONITOR, context.getString(R.string.channel_monitor), NotificationManager.IMPORTANCE_MIN).apply {
                description = context.getString(R.string.channel_monitor_desc)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_alerts_desc)
                // Sound and vibration are handled by AlertSound so the user can pick any sound.
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun openAppIntent(context: Context) = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    fun monitor(context: Context, level: Int, plugged: Boolean, prefs: AlertPrefs): Notification {
        val charge = prefs.config(AlertKind.CHARGE)
        val low = prefs.config(AlertKind.LOW)
        val text = when {
            plugged && charge.enabled -> context.getString(R.string.monitor_charging_target, charge.target)
            plugged -> context.getString(R.string.charging)
            low.enabled -> context.getString(R.string.monitor_battery_target, low.target)
            else -> context.getString(R.string.on_battery)
        }
        return NotificationCompat.Builder(context, CH_MONITOR)
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle(context.getString(R.string.monitor_title, level))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun alert(context: Context, kind: AlertKind, level: Int): Notification {
        val (title, text) = when (kind) {
            AlertKind.CHARGE ->
                context.getString(R.string.notif_charge_title, level) to context.getString(R.string.notif_charge_text)
            AlertKind.LOW ->
                context.getString(R.string.notif_low_title, level) to context.getString(R.string.notif_low_text)
        }
        val stopSound = PendingIntent.getBroadcast(
            context, kind.ordinal,
            Intent(context, StopSoundReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(if (kind == AlertKind.CHARGE) R.drawable.ic_bolt else R.drawable.ic_battery_low)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .setDeleteIntent(stopSound)
            .addAction(0, context.getString(R.string.mute), stopSound)
            .build()
    }
}
