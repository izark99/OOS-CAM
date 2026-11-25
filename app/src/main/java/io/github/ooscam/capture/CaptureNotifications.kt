package io.github.ooscam.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.ooscam.MainActivity
import io.github.ooscam.R

/** The ongoing recording notification. It always has a Stop action; there is no way to hide it. */
object CaptureNotifications {
    const val CHANNEL_ID = "recording"
    const val NOTIFICATION_ID = 2001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.capture_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    fun build(context: Context, state: CaptureState.Recording): Notification {
        val stop = PendingIntent.getService(
            context,
            REQUEST_STOP,
            Intent(context, CaptureService::class.java).setAction(CaptureService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_capture_recording)
            .setContentTitle(context.getString(R.string.capture_notification_title))
            .setContentText(context.getString(R.string.capture_notification_segment, state.segmentIndex))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setWhen(state.startedAtWallMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.capture_notification_stop), stop)
            .build()
    }

    private const val REQUEST_STOP = 1
    private const val REQUEST_OPEN = 2
}
