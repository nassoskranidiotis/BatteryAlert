package com.nassos.batteryalert

import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.core.content.edit

enum class AlertKind(val key: String, val defaultTarget: Int, val range: IntRange) {
    CHARGE("charge", 80, 50..100),
    LOW("low", 20, 5..50),
}

data class AlertConfig(
    val enabled: Boolean,
    val target: Int,
    /** null = system default notification sound, "" = silent, otherwise a content URI. */
    val sound: String?,
    val vibrate: Boolean,
) {
    val soundUri: Uri?
        get() = when (sound) {
            null -> Settings.System.DEFAULT_NOTIFICATION_URI
            "" -> null
            else -> Uri.parse(sound)
        }
}

class AlertPrefs(context: Context) {
    // applicationContext can still be null while an Activity or Service attaches its base context.
    private val sp = (context.applicationContext ?: context).getSharedPreferences("settings", Context.MODE_PRIVATE)

    var monitoring: Boolean
        get() = sp.getBoolean("monitoring", false)
        set(value) = sp.edit { putBoolean("monitoring", value) }

    /** Play alerts on the alarm stream so they are heard even in silent mode. */
    var useAlarmStream: Boolean
        get() = sp.getBoolean("alarm_stream", false)
        set(value) = sp.edit { putBoolean("alarm_stream", value) }

    /** App language tag ("el" / "en"), or null to follow the system language. */
    var language: String?
        get() = sp.getString("language", null)
        set(value) = sp.edit { putString("language", value) }

    fun config(kind: AlertKind) = AlertConfig(
        enabled = sp.getBoolean("${kind.key}_enabled", true),
        target = sp.getInt("${kind.key}_target", kind.defaultTarget),
        sound = if (sp.contains("${kind.key}_sound")) sp.getString("${kind.key}_sound", "") else null,
        vibrate = sp.getBoolean("${kind.key}_vibrate", true),
    )

    fun save(kind: AlertKind, config: AlertConfig) = sp.edit {
        putBoolean("${kind.key}_enabled", config.enabled)
        putInt("${kind.key}_target", config.target)
        if (config.sound == null) remove("${kind.key}_sound") else putString("${kind.key}_sound", config.sound)
        putBoolean("${kind.key}_vibrate", config.vibrate)
    }

    /** Target the alert last fired at, or -1. Persisted so a service restart doesn't re-alert. */
    fun firedAt(kind: AlertKind) = sp.getInt("${kind.key}_fired", -1)

    fun setFiredAt(kind: AlertKind, target: Int) = sp.edit { putInt("${kind.key}_fired", target) }
}
