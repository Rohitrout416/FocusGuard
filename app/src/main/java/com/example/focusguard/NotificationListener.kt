package com.example.focusguard

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationListener : NotificationListenerService() {

    // This function is called by the system whenever a new notification is posted.
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // Initialize Repository (In a real app, use Dependency Injection like Hilt)
        val repository = com.example.focusguard.data.FocusRepository(applicationContext)

        // 1. CHECK FOCUS MODE
        if (!repository.isFocusModeActive()) {
            return // Let it pass if not in focus mode
        }

        // 2. EXTRACT METADATA (Privacy-First)
        val packageName = sbn.packageName
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString("android.title") ?: "Unknown"
        // NOTICE: We are NOT extracting 'android.text' to respect privacy.
        // We only care about WHO sent it, not WHAT they said.

        // 3. AUTO-SUPPRESS (Dismiss from status bar)
        cancelNotification(sbn.key)
        
        // 4. STORE METADATA
        // We need a coroutine scope since database ops are suspending
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val entity = com.example.focusguard.data.NotificationEntity(
                packageName = packageName,
                senderName = title,
                timestamp = System.currentTimeMillis(),
                isPriority = false // Default for now
            )
            repository.saveNotification(entity)
            Log.d("FocusGuard", "Suppressed & Stored: $title from $packageName")
        }
    }

    // This is called when a notification is removed. We don't need it for now.
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
    }
}
