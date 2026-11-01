package com.nassos.batteryalert

import android.app.NotificationManager
import android.content.Context
import android.database.Cursor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.OpenableColumns

/**
 * Alert sounds are played by the app instead of the notification channel, because a channel's
 * sound can't be changed after creation and can't play files picked from storage.
 */
object AlertSound {
    private const val MAX_PLAY_MS = 30_000L

    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stop() }
    private var player: MediaPlayer? = null

    fun play(context: Context, uri: Uri?, alarmStream: Boolean, respectDnd: Boolean = true) {
        stop()
        if (uri == null) return
        if (respectDnd && !alarmStream && isDndActive(context)) return

        val attrs = AudioAttributes.Builder()
            .setUsage(if (alarmStream) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = MediaPlayer()
        try {
            p.setAudioAttributes(attrs)
            p.setDataSource(context.applicationContext, uri)
            p.setOnCompletionListener { stop() }
            p.prepare()
            p.start()
            player = p
            handler.postDelayed(stopRunnable, MAX_PLAY_MS)
        } catch (e: Exception) {
            p.release()
            // The chosen file may have been deleted; fall back to the default sound once.
            val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (uri != fallback) play(context, fallback, alarmStream, respectDnd)
        }
    }

    fun stop() {
        handler.removeCallbacks(stopRunnable)
        player?.run {
            runCatching { stop() }
            release()
        }
        player = null
    }

    fun vibrate(context: Context) {
        val vm = context.getSystemService(VibratorManager::class.java) ?: return
        val pattern = longArrayOf(0, 400, 200, 400, 200, 700)
        vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun isDndActive(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        return nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
    }

    /** Human-readable name for a stored sound value (see [AlertConfig.sound]). */
    fun title(context: Context, sound: String?): String = when (sound) {
        null -> "Προεπιλογή συστήματος"
        "" -> "Χωρίς ήχο"
        else -> {
            val uri = Uri.parse(sound)
            displayName(context, uri)
                ?: runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
                ?: "Προσαρμοσμένος ήχος"
        }
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        val cursor: Cursor? = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use { if (it.moveToFirst()) it.getString(0)?.substringBeforeLast('.') else null }
    }.getOrNull()
}
