package com.example.focusguard.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.focusguard.MainActivity
import com.example.focusguard.R

object NotificationHelper {
    const val CHANNEL_ID = "focusguard_alerts"
    const val CHANNEL_NAME = "FocusGuard Alerts"
    const val FOCUS_STATUS_CHANNEL_ID = "focus_status"
    const val FOCUS_STATUS_NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Alerts Channel (Default importance)
            val alertChannel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alerts for repeated or urgent messages"
            }
            notificationManager.createNotificationChannel(alertChannel)
            
            // Focus Status Channel (Low importance, silent)
            val focusChannel = NotificationChannel(FOCUS_STATUS_CHANNEL_ID, "Focus Mode Status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows when Focus Mode is active"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(focusChannel)
        }
    }

    fun getFocusStatusNotification(context: Context): android.app.Notification {
        // Ensure channel exists before building notification
        createNotificationChannel(context)
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(context, FOCUS_STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use app icon
            .setContentTitle("Focus Mode Active")
            .setContentText("Notifications are being filtered")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun showRepeatedMessageAlert(context: Context, senderName: String, appName: String, id: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // Use system icon for now
            .setContentTitle("Repeated messages detected")
            .setContentText("$senderName on $appName is messaging continuously.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // Handle missing permission if necessary, though Listener usually implies some access
        }
    }

    fun showPrimaryEscalationAlert(context: Context, senderName: String, appName: String, id: Int) {
         val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Increased activity")
            .setContentText("Multiple messages from $senderName on $appName.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // Handle permission
        }
    }
    fun showMilestoneNotification(
        context: Context, 
        hours: Int, 
        disableIntent: PendingIntent
    ) {
        val message = "You've stayed focused for $hours hours. Keep it up! \uD83D\uDCAA"
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Using generic icon for now
            .setContentTitle("🎉 Great Focus Session")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_delete,
                "Disable Focus Milestones",
                disableIntent
            )

        try {
             NotificationManagerCompat.from(context).notify(com.example.focusguard.receivers.MilestoneActionReceiver.NOTIFICATION_ID_MILESTONE, builder.build())
        } catch (e: SecurityException) {
            // Permission
        }
    }
}
