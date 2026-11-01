package com.nassos.batteryalert

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * Foreground service that listens to battery changes. ACTION_BATTERY_CHANGED can't be received
 * through the manifest, so a long-lived registered receiver is the only reliable way.
 */
class BatteryMonitorService : Service() {

    private lateinit var prefs: AlertPrefs
    private lateinit var nm: NotificationManager
    private var last: BatteryInfo? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onBattery(BatteryInfo.from(intent))
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AlertPrefs(this)
        nm = getSystemService(NotificationManager::class.java)
        Notifications.createChannels(this)

        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val info = BatteryInfo.from(sticky)
        val notification = Notifications.monitor(this, info.level, info.plugged, prefs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Notifications.ID_MONITOR, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.ID_MONITOR, notification)
        }

        ContextCompat.registerReceiver(
            this, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Settings may have changed: re-check thresholds against the latest reading.
        last?.let { evaluate(it, forceNotificationUpdate = true) }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onBattery(info: BatteryInfo) {
        val prev = last
        last = info
        evaluate(info, forceNotificationUpdate = prev == null || prev.level != info.level || prev.plugged != info.plugged)
    }

    private fun evaluate(info: BatteryInfo, forceNotificationUpdate: Boolean) {
        if (forceNotificationUpdate) {
            nm.notify(Notifications.ID_MONITOR, Notifications.monitor(this, info.level, info.plugged, prefs))
        }

        val charge = prefs.config(AlertKind.CHARGE)
        if (!info.plugged) {
            if (prefs.firedAt(AlertKind.CHARGE) != -1) {
                // Unplugged after a charge alert: the user reacted, so silence and clear it.
                prefs.setFiredAt(AlertKind.CHARGE, -1)
                nm.cancel(Notifications.alertId(AlertKind.CHARGE))
                AlertSound.stop()
            }
        } else if (charge.enabled && info.level >= charge.target && prefs.firedAt(AlertKind.CHARGE) != charge.target) {
            prefs.setFiredAt(AlertKind.CHARGE, charge.target)
            fire(AlertKind.CHARGE, charge, info.level)
        }

        val low = prefs.config(AlertKind.LOW)
        if (info.plugged) {
            if (prefs.firedAt(AlertKind.LOW) != -1) {
                prefs.setFiredAt(AlertKind.LOW, -1)
                nm.cancel(Notifications.alertId(AlertKind.LOW))
            }
        } else if (low.enabled && info.level <= low.target && prefs.firedAt(AlertKind.LOW) != low.target) {
            prefs.setFiredAt(AlertKind.LOW, low.target)
            fire(AlertKind.LOW, low, info.level)
        }
    }

    private fun fire(kind: AlertKind, config: AlertConfig, level: Int) {
        nm.notify(Notifications.alertId(kind), Notifications.alert(this, kind, level))
        AlertSound.play(this, config.soundUri, prefs.useAlarmStream)
        if (config.vibrate) AlertSound.vibrate(this)
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BatteryMonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BatteryMonitorService::class.java))
        }
    }
}
