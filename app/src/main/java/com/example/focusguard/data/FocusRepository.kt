package com.example.focusguard.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository that manages Focus Mode state and notification data.
 */
class FocusRepository(context: Context) {
    private val prefs = context.getSharedPreferences("focusguard_prefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(context)
    private val notificationDao = database.notificationDao()
    private val senderScoreDao = database.senderScoreDao()

    companion object {
        private const val KEY_FOCUS_MODE = "focus_mode_active"
        private const val VIP_THRESHOLD = 3
        private const val DUPLICATE_WINDOW_MS = 5000L
    }

    // ========== Focus Mode ==========
    
    fun isFocusModeActive(): Boolean {
        return prefs.getBoolean(KEY_FOCUS_MODE, false)
    }

    fun setFocusModeActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_FOCUS_MODE, active).apply()
    }

    // ========== VIP Senders ==========
    
    // Original suspend check (DB lookup) - useful if needed ad-hoc
    suspend fun isVipSender(senderId: String): Boolean {
        val score = senderScoreDao.getSenderScore(senderId)
        return score != null && score.userFeedback >= VIP_THRESHOLD
    }
    
    // New: Observable VIP list for caching
    fun getVipSendersFlow(): Flow<Set<String>> {
        return senderScoreDao.getVipSenderIds().map { it.toSet() }
    }

    // ========== Notifications ==========
    
    fun getAllNotifications(): Flow<List<NotificationEntity>> {
        return notificationDao.getAllNotifications()
    }

    fun getPriorityNotifications(): Flow<List<NotificationEntity>> {
        return notificationDao.getAllNotifications().map { notifications ->
            notifications.filter { notif ->
                val senderId = "${notif.packageName}:${notif.senderName}"
                val score = senderScoreDao.getSenderScore(senderId)
                score == null || (!score.isSpam)
            }
        }
    }

    fun getSpamNotifications(): Flow<List<NotificationEntity>> {
        return notificationDao.getAllNotifications().map { notifications ->
            notifications.filter { notif ->
                val senderId = "${notif.packageName}:${notif.senderName}"
                val score = senderScoreDao.getSenderScore(senderId)
                score?.isSpam == true
            }
        }
    }

    suspend fun saveNotification(senderName: String, packageName: String) {
        val now = System.currentTimeMillis()
        val sinceTimestamp = now - DUPLICATE_WINDOW_MS
        
        val recentCount = notificationDao.countRecentFromSender(packageName, senderName, sinceTimestamp)
        if (recentCount > 0) {
            Log.d("FocusGuard", "DB SKIP: Duplicate from $senderName")
            return
        }

        val entity = NotificationEntity(
            senderName = senderName,
            packageName = packageName,
            timestamp = now
        )
        notificationDao.insert(entity)
        
        updateSenderStats("$packageName:$senderName")
    }

    suspend fun clearNotifications() {
        notificationDao.clearAll()
    }

    suspend fun deleteNotification(id: Int) {
        notificationDao.delete(id)
    }

    // ========== Sender Scores ==========
    
    private suspend fun updateSenderStats(senderId: String) {
        val existing = senderScoreDao.getSenderScore(senderId)
        val now = System.currentTimeMillis()
        
        if (existing == null) {
            senderScoreDao.upsert(
                SenderScoreEntity(
                    senderId = senderId,
                    msgCount = 1,
                    lastBurstTime = now
                )
            )
        } else {
            senderScoreDao.upsert(
                existing.copy(
                    msgCount = existing.msgCount + 1,
                    lastBurstTime = now
                )
            )
        }
    }

    suspend fun markAsImportant(senderId: String) {
        val existing = senderScoreDao.getSenderScore(senderId)
        if (existing != null) {
            senderScoreDao.upsert(existing.copy(userFeedback = existing.userFeedback + 1, isSpam = false))
        } else {
            senderScoreDao.upsert(SenderScoreEntity(senderId = senderId, userFeedback = 1))
        }
    }

    suspend fun markAsSpam(senderId: String) {
        val existing = senderScoreDao.getSenderScore(senderId)
        if (existing != null) {
            senderScoreDao.upsert(existing.copy(userFeedback = existing.userFeedback - 1, isSpam = true))
        } else {
            senderScoreDao.upsert(SenderScoreEntity(senderId = senderId, userFeedback = -1, isSpam = true))
        }
    }

    suspend fun isSpam(senderId: String): Boolean {
        return senderScoreDao.getSenderScore(senderId)?.isSpam ?: false
    }
}
