package com.example.focusguard

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.focusguard.data.FocusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Intercepts all notifications.
 * When Focus Mode is ON: suppresses all clearable notifications and stores metadata.
 * Uses a cached VIP list for synchronous decision making.
 */
class NotificationListener : NotificationListenerService() {

    private lateinit var repository: FocusRepository
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    // VIP Cache for fast lookup
    private var vipCache: Set<String> = emptySet()
    
    // Deduplication: track recent senders
    private val recentSenders = mutableMapOf<String, Long>()
    private val SENDER_COOLDOWN_MS = 2000L

    override fun onCreate() {
        super.onCreate()
        repository = FocusRepository(applicationContext)
        Log.d("FocusGuard", "NotificationListener started")
        
        // Start observing VIP list for cache (with distinctUntilChanged to reduce updates)
        scope.launch {
            repository.getVipSendersFlow()
                .distinctUntilChanged()
                .collect { vipSet ->
                    vipCache = vipSet
                    Log.d("FocusGuard", "VIP Cache updated: ${vipSet.size} senders")
                }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel() // Cancel all coroutines to prevent leaks
        Log.d("FocusGuard", "NotificationListener destroyed")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // 1. Check Focus Mode FIRST
        if (!repository.isFocusModeActive()) {
            return
        }

        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val senderName = extras.getString("android.title") ?: "Unknown"
        val senderKey = "$packageName:$senderName"

        // 2. VIP CHECK (Fast Path - In Memory)
        if (vipCache.contains(senderKey)) {
            Log.d("FocusGuard", "VIP ALLOWED: $senderName")
            return // Let it pass through
        }

        // 3. BLOCKING LOGIC: Cancel immediately if clearable
        if (sbn.isClearable) {
            try {
                cancelNotification(sbn.key)
                Log.d("FocusGuard", "BLOCKED: $senderName")
            } catch (e: Exception) {
                Log.e("FocusGuard", "Failed to cancel: ${e.message}")
            }
        } else {
            // Don't modify ongoing (music/calls)
            return 
        }

        // 4. STORAGE LOGIC: Save if unique and not summary
        val now = System.currentTimeMillis()

        // Sync check for cooldown (filtering saving, not blocking)
        synchronized(recentSenders) {
            val lastTime = recentSenders[senderKey]
            if (lastTime != null && (now - lastTime) < SENDER_COOLDOWN_MS) {
                Log.d("FocusGuard", "SKIP SAVING (Duplicate): $senderName")
                return
            }
            recentSenders[senderKey] = now
            if (recentSenders.size > 50) recentSenders.entries.removeIf { now - it.value > 60_000 }
        }

        // Async Save
        scope.launch {
            try {
                // Don't save summaries
                val isSummary = sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0
                if (!isSummary) {
                    repository.saveNotification(senderName, packageName)
                }
            } catch (e: Exception) {
                Log.e("FocusGuard", "Error saving: ${e.message}")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
    }
}
