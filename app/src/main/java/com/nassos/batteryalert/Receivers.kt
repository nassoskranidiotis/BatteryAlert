package com.nassos.batteryalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts monitoring after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (AlertPrefs(context).monitoring) BatteryMonitorService.start(context)
    }
}

/** "Mute" action and swipe-away of an alert notification. */
class StopSoundReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AlertSound.stop()
}
