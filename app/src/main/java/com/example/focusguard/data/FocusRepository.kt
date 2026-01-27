package com.example.focusguard.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Repository that manages Focus Mode state and notification data.
 * Acts as the single source of truth for the app.
 */
class FocusRepository(context: Context) {
    private val prefs = context.getSharedPreferences("focusguard_prefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(context)
    private val notificationDao = database.notificationDao()
    private val senderScoreDao = database.senderScoreDao()

    companion object {
        private const val KEY_FOCUS_MODE = "focus_mode_active"
        private const val BURST_WINDOW_MS = 60_000L // 60 seconds
        private const val BURST_THRESHOLD = 3
    }

    // ========== Focus Mode ==========
    
    fun isFocusModeActive(): Boolean {
        return prefs.getBoolean(KEY_FOCUS_MODE, false)
    }

    fun setFocusModeActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_FOCUS_MODE, active).apply()
    }

    // ========== Notifications ==========
    
    fun getAllNotifications(): Flow<List<NotificationEntity>> {
        return notificationDao.getAllNotifications()
    }

    suspend fun saveNotification(senderName: String, packageName: String) {
        val entity = NotificationEntity(
            senderName = senderName,
            packageName = packageName,
            timestamp = System.currentTimeMillis()
        )
        notificationDao.insert(entity)
        
        // Update sender score
        val senderId = "$packageName:$senderName"
        updateSenderOnMessage(senderId)
    }

    suspend fun clearNotifications() {
        notificationDao.clearAll()
    }

    suspend fun deleteNotification(id: Int) {
        notificationDao.delete(id)
    }

    // ========== Sender Scores ==========
    
    private suspend fun updateSenderOnMessage(senderId: String) {
        val existing = senderScoreDao.getSenderScore(senderId)
        val now = System.currentTimeMillis()
        
        if (existing == null) {
            // First message from this sender
            senderScoreDao.upsert(
                SenderScoreEntity(
                    senderId = senderId,
                    msgCount = 1,
                    lastBurstTime = now
                )
            )
        } else {
            // Update existing: increment count, check for burst
            val isBurst = (now - existing.lastBurstTime) < BURST_WINDOW_MS
            senderScoreDao.upsert(
                existing.copy(
                    msgCount = existing.msgCount + 1,
                    lastBurstTime = if (isBurst) existing.lastBurstTime else now
                )
            )
        }
    }

    suspend fun markAsImportant(senderId: String) {
        senderScoreDao.updateFeedback(senderId, +1)
        senderScoreDao.setSpam(senderId, false)
    }

    suspend fun markAsSpam(senderId: String) {
        senderScoreDao.updateFeedback(senderId, -1)
        senderScoreDao.setSpam(senderId, true)
    }

    /**
     * Calculate priority score for a sender.
     * Formula: baseScore + (msgCount * 0.1) + (userFeedback * 5) + burstBonus
     */
    suspend fun calculatePriority(senderId: String): Int {
        val score = senderScoreDao.getSenderScore(senderId) ?: return 0
        
        val burstBonus = if (score.msgCount >= BURST_THRESHOLD) 10 else 0
        
        return (
            score.baseScore +
            (score.msgCount * 0.1).toInt() +
            (score.userFeedback * 5) +
            burstBonus
        )
    }

    suspend fun isSpam(senderId: String): Boolean {
        return senderScoreDao.getSenderScore(senderId)?.isSpam ?: false
    }
}
