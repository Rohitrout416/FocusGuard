package com.example.focusguard

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {

    // This function is called by the system whenever a new notification is posted.
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // For our MVP, we'll focus on messaging apps. Let's start with WhatsApp.
        val packageName = sbn.packageName
        if (packageName == "com.whatsapp") {
            val notification = sbn.notification
            val extras = notification.extras
            val title = extras.getString("android.title")
            val text = extras.getCharSequence("android.text")?.toString()

            // We use Log.d to print information to the Logcat, which is like a developer's console.
            // This is perfect for checking that our service is working.
            // 'd' stands for 'debug'.
            Log.d("NotificationListener", "WhatsApp Notification: Title='$title', Text='$text'")
        }
    }

    // This is called when a notification is removed. We don't need it for now.
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
    }
}
