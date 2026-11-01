package com.nassos.batteryalert

import android.content.Intent
import android.os.BatteryManager
import androidx.annotation.StringRes

data class BatteryInfo(
    val level: Int,
    val plugType: Int,
    val temperatureC: Float,
    val full: Boolean,
) {
    val plugged get() = plugType != 0

    @get:StringRes
    val plugLabel
        get() = when (plugType) {
            BatteryManager.BATTERY_PLUGGED_AC -> R.string.plug_ac
            BatteryManager.BATTERY_PLUGGED_USB -> R.string.plug_usb
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> R.string.plug_wireless
            BatteryManager.BATTERY_PLUGGED_DOCK -> R.string.plug_dock
            else -> R.string.plug_none
        }

    companion object {
        /** Parses an [Intent.ACTION_BATTERY_CHANGED] broadcast. */
        fun from(intent: Intent?): BatteryInfo {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            return BatteryInfo(
                level = if (level < 0 || scale <= 0) 0 else level * 100 / scale,
                plugType = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0,
                temperatureC = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
                full = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL,
            )
        }
    }
}
