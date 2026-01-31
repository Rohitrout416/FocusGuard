package com.example.focusguard.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import com.example.focusguard.util.AppUtils
import com.example.focusguard.util.NotificationHelper

class FocusRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("focusguard_prefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(context)
    private val notificationDao = database.notificationDao()
    private val senderScoreDao = database.senderScoreDao()

    companion object {
        private const val KEY_FOCUS_MODE = "focus_mode_active"
        private const val DUPLICATE_WINDOW_MS = 5000L
        private const val KEY_FOCUS_START_TIME = "focus_start_time"
        private const val KEY_DAILY_FOCUS_TOTAL = "daily_focus_total"
        private const val KEY_LAST_RESET_DAY = "last_reset_day"
        private const val KEY_MILESTONES_ENABLED = "milestones_enabled"
    }

    // ========== Focus Mode ==========
    
    fun areMilestonesEnabled(): Boolean {
        return prefs.getBoolean(KEY_MILESTONES_ENABLED, true) // Default true
    }

    fun setMilestonesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MILESTONES_ENABLED, enabled).apply()
        if (!enabled) {
             androidx.work.WorkManager.getInstance(context).cancelUniqueWork("focus_milestone_work")
        }
    }
    
    fun isFocusModeActive(): Boolean {
        return prefs.getBoolean(KEY_FOCUS_MODE, false)
    }

    fun setFocusModeActive(active: Boolean) {
        val now = System.currentTimeMillis()
        val workManager = androidx.work.WorkManager.getInstance(context)

        if (active) {
            // Start Session
            prefs.edit()
                .putBoolean(KEY_FOCUS_MODE, true)
                .putLong(KEY_FOCUS_START_TIME, now)
                .apply()
            
            // Start Foreground Service for persistent notification
            try {
                val serviceIntent = android.content.Intent(context, com.example.focusguard.service.FocusStatusService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d("FocusGuard", "FocusStatusService start requested")
            } catch (e: Exception) {
                Log.e("FocusGuard", "Failed to start FocusStatusService: ${e.message}")
            }

            // Schedule Milestone Work (if enabled) with battery constraints
            if (areMilestonesEnabled()) {
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiresBatteryNotLow(true) // Don't run on low battery
                    .build()
                    
                val milestoneWork = androidx.work.OneTimeWorkRequestBuilder<com.example.focusguard.workers.FocusMilestoneWorker>()
                    .setInitialDelay(2, java.util.concurrent.TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()
                
                workManager.enqueueUniqueWork(
                    "focus_milestone_work",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    milestoneWork
                )
            }

        } else {
            // End Session
            val startTime = prefs.getLong(KEY_FOCUS_START_TIME, 0L)
            
            // Stop Foreground Service
            try {
                context.stopService(android.content.Intent(context, com.example.focusguard.service.FocusStatusService::class.java))
                Log.d("FocusGuard", "FocusStatusService stop requested")
            } catch (e: Exception) {
                Log.e("FocusGuard", "Failed to stop FocusStatusService: ${e.message}")
            }

            // Cancel Milestone Work
            workManager.cancelUniqueWork("focus_milestone_work")
            
            if (startTime > 0) {
                val sessionDuration = now - startTime
                // Update Daily Total
                val today = java.time.LocalDate.now().toString()
                val lastResetDay = prefs.getString(KEY_LAST_RESET_DAY, "")
                
                var currentTotal = if (lastResetDay == today) prefs.getLong(KEY_DAILY_FOCUS_TOTAL, 0L) else 0L
                currentTotal += sessionDuration
                
                prefs.edit()
                    .putBoolean(KEY_FOCUS_MODE, false)
                    .putLong(KEY_FOCUS_START_TIME, 0L)
                    .putString(KEY_LAST_RESET_DAY, today)
                    .putLong(KEY_DAILY_FOCUS_TOTAL, currentTotal)
                    .apply()
            } else {
                prefs.edit().putBoolean(KEY_FOCUS_MODE, false).apply()
            }
        }
    }

    fun getFocusMetrics(): Pair<Long, Long> {
        val focusActive = isFocusModeActive()
        val now = System.currentTimeMillis()
        
        // 1. Calculate Current Session
        val startTime = prefs.getLong(KEY_FOCUS_START_TIME, 0L)
        val currentSessionMs = if (focusActive && startTime > 0) now - startTime else 0L
        
        // 2. Calculate Daily Total
        val today = java.time.LocalDate.now().toString()
        val lastResetDay = prefs.getString(KEY_LAST_RESET_DAY, "")
        val storedTotal = if (lastResetDay == today) prefs.getLong(KEY_DAILY_FOCUS_TOTAL, 0L) else 0L
        
        val totalDailyMs = storedTotal + currentSessionMs
        
        return Pair(currentSessionMs, totalDailyMs)
    }

    // ========== Sender Configuration ==========
    
    fun getAllSenders(): Flow<List<SenderScoreEntity>> {
        return senderScoreDao.getAllSenders()
    }
    
    fun getVipSendersFlow(): Flow<Set<String>> {
        return senderScoreDao.getVipSenderIds().map { it.toSet() }
    }

    fun getUnknownSendersFlow(): Flow<List<SenderScoreEntity>> {
        return senderScoreDao.getUnknownSenders()
    }

    suspend fun setSenderCategory(senderId: String, category: SenderCategory) {
        val existing = senderScoreDao.getSenderScore(senderId)
        val newItem = existing?.copy(category = category, lastUpdated = System.currentTimeMillis()) 
            ?: SenderScoreEntity(senderId = senderId, category = category)
        senderScoreDao.upsert(newItem)
    }

    // ========== Notifications ==========
    
    /**
     * Single shared Flow that combines notifications + sender categories once.
     * All filtered flows derive from this to avoid redundant combine operations.
     */
    private val categorizedNotifications: Flow<List<Pair<NotificationEntity, SenderCategory>>> = 
        notificationDao.getAllNotifications().combine(senderScoreDao.getAllSenders()) { notifications, senders ->
            val senderMap = senders.associateBy { it.senderId }
            notifications.map { notif ->
                val senderId = "${notif.packageName}:${notif.senderName}"
                notif to (senderMap[senderId]?.category ?: SenderCategory.UNKNOWN)
            }
        }

    // 🟡 UNKNOWN - derive from shared flow
    fun getUnknownNotifications(): Flow<List<NotificationEntity>> {
        return categorizedNotifications.map { pairs ->
            pairs.filter { it.second == SenderCategory.UNKNOWN }.map { it.first }
        }
    }

    // 🔵 PRIMARY - derive from shared flow
    fun getPrimaryNotifications(): Flow<List<NotificationEntity>> {
        return categorizedNotifications.map { pairs ->
            pairs.filter { it.second == SenderCategory.PRIMARY }.map { it.first }
        }
    }

    // ⭐ VIP - derive from shared flow
    fun getVipNotifications(): Flow<List<NotificationEntity>> {
        return categorizedNotifications.map { pairs ->
            pairs.filter { it.second == SenderCategory.VIP }.map { it.first }
        }
    }

    // ⚫ SPAM - derive from shared flow
    fun getSpamNotifications(): Flow<List<NotificationEntity>> {
        return categorizedNotifications.map { pairs ->
            pairs.filter { it.second == SenderCategory.SPAM }.map { it.first }
        }
    }


    init {
        NotificationHelper.createNotificationChannel(context)
    }

    suspend fun saveNotification(senderName: String, packageName: String) {
        val senderId = "$packageName:$senderName"
        val appName = com.example.focusguard.util.AppUtils.getAppName(context, packageName)
        
        // 1. Get existing or create new info
        var scoreEntity = senderScoreDao.getSenderScore(senderId)
        if (scoreEntity == null) {
            scoreEntity = SenderScoreEntity(senderId = senderId, category = SenderCategory.UNKNOWN, msgCount = 0)
        }
        
        // 2. Increment Message Count
        val newScoreEntity = scoreEntity.copy(
            msgCount = scoreEntity.msgCount + 1,
            lastUpdated = System.currentTimeMillis()
        )
        senderScoreDao.upsert(newScoreEntity)

        // 3. Duplicate Check for Notification List
        val now = System.currentTimeMillis()
        val sinceTimestamp = now - DUPLICATE_WINDOW_MS
        val recentCount = notificationDao.countRecentFromSender(packageName, senderName, sinceTimestamp)
        
        if (recentCount > 0) {
             // Even if duplicate, check for Unknown alert (pure frequency)
            if (newScoreEntity.category == SenderCategory.UNKNOWN && newScoreEntity.msgCount == 4) {
                 com.example.focusguard.util.NotificationHelper.showRepeatedMessageAlert(context, senderName, appName, senderId.hashCode())
            }
            return
        }

        // 4. Save Notification
        val entity = NotificationEntity(
            senderName = senderName,
            packageName = packageName,
            timestamp = now
        )
        notificationDao.insert(entity)
        
        // 5. Post-Save Alerts
        
        // Unknown: 4th message (Alert if not triggered above, but doing it here ensures it fires once per threshold)
        // We checked above for duplicates. If we are here, it wasn't a duplicate.
        // We should check again to be consistent? Or just check once?
        // Let's rely on msgCount.
        if (newScoreEntity.category == SenderCategory.UNKNOWN && newScoreEntity.msgCount == 4) {
             com.example.focusguard.util.NotificationHelper.showRepeatedMessageAlert(context, senderName, appName, senderId.hashCode())
        }
        
        // Primary: Escalation (3 messages in 60 seconds)
        if (newScoreEntity.category == SenderCategory.PRIMARY) {
            val oneMinuteAgo = System.currentTimeMillis() - 60000
            val recentPrimary = notificationDao.countRecentFromSender(packageName, senderName, oneMinuteAgo)
            if (recentPrimary >= 3) {
                 com.example.focusguard.util.NotificationHelper.showPrimaryEscalationAlert(context, senderName, appName, senderId.hashCode())
            }
        }
    }

    suspend fun clearNotifications() {
        notificationDao.clearAll()
    }
}
