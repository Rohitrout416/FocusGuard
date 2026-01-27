package com.example.focusguard

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.focusguard.data.FocusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Intercepts all notifications.
 * When Focus Mode is ON: suppresses and stores metadata (NOT content).
 * When Focus Mode is OFF: lets notifications pass through.
 */
class NotificationListener : NotificationListenerService() {

    private lateinit var repository: FocusRepository
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        repository = FocusRepository(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // 1. Check Focus Mode
        if (!repository.isFocusModeActive()) {
            return // Let notification pass through normally
        }

        // 2. Extract METADATA only (privacy-first)
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val senderName = extras.getString("android.title") ?: "Unknown"
        // NOTE: We do NOT extract android.text - privacy first!

        // 3. Suppress notification
        cancelNotification(sbn.key)
        Log.d("FocusGuard", "Suppressed: $senderName from $packageName")

        // 4. Store metadata in database
        scope.launch {
            repository.saveNotification(senderName, packageName)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
    }
}
