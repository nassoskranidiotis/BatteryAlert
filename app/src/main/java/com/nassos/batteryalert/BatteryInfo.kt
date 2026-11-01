package com.nassos.batteryalert

import android.content.Intent
import android.os.BatteryManager

data class BatteryInfo(
    val level: Int,
    val plugType: Int,
    val temperatureC: Float,
    val full: Boolean,
) {
    val plugged get() = plugType != 0

    val plugLabel
        get() = when (plugType) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Φορτιστής"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Ασύρματη"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "Βάση"
            else -> "Μπαταρία"
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
