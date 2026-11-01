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
            NotificationChannel(CH_MONITOR, "Παρακολούθηση μπαταρίας", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Μόνιμη ειδοποίηση όσο τρέχει η παρακολούθηση"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Ειδοποιήσεις μπαταρίας", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Ειδοποιήσεις όταν η μπαταρία φτάσει στα όρια που έχεις ορίσει"
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
            plugged && charge.enabled -> "Φορτίζει · ειδοποίηση στο ${charge.target}%"
            plugged -> "Φορτίζει"
            low.enabled -> "Σε μπαταρία · ειδοποίηση στο ${low.target}%"
            else -> "Σε μπαταρία"
        }
        return NotificationCompat.Builder(context, CH_MONITOR)
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle("Μπαταρία $level%")
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
            AlertKind.CHARGE -> "Η μπαταρία έφτασε στο $level%" to "Μπορείς να αποσυνδέσεις τον φορτιστή."
            AlertKind.LOW -> "Χαμηλή μπαταρία: $level%" to "Σύνδεσε τον φορτιστή."
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
            .addAction(0, "Σίγαση", stopSound)
            .build()
    }
}
