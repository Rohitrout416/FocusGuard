package com.example.focusguard.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository that manages Focus Mode state and notification data.
 * 
 * CATEGORIZATION LOGIC:
 * - Primary: Sender has isPrimary = true
 * - Spam (Secondary): Sender has isPrimary = false (Default)
 * 
 * FOCUS MODE LOGIC:
 * - Blocked: Focus Mode ON AND Sender isVip = false
 * - Allowed: Focus Mode OFF OR Sender isVip = true
 */
class FocusRepository(context: Context) {
    private val prefs = context.getSharedPreferences("focusguard_prefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(context)
    private val notificationDao = database.notificationDao()
    private val senderScoreDao = database.senderScoreDao()

    companion object {
        private const val KEY_FOCUS_MODE = "focus_mode_active"
        private const val DUPLICATE_WINDOW_MS = 5000L
    }

    // ========== Focus Mode ==========
    
    fun isFocusModeActive(): Boolean {
        return prefs.getBoolean(KEY_FOCUS_MODE, false)
    }

    fun setFocusModeActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_FOCUS_MODE, active).apply()
    }

    // ========== VIP / Primary Configuration ==========
    
    fun getAllSenders(): Flow<List<SenderScoreEntity>> {
        return senderScoreDao.getAllSenders()
    }
    
    fun getVipSendersFlow(): Flow<Set<String>> {
        return senderScoreDao.getVipSenderIds().map { it.toSet() }
    }

    suspend fun setSenderPrimary(senderId: String, isPrimary: Boolean) {
        val existing = senderScoreDao.getSenderScore(senderId)
        val newItem = existing?.copy(isPrimary = isPrimary, lastUpdated = System.currentTimeMillis()) 
            ?: SenderScoreEntity(senderId = senderId, isPrimary = isPrimary)
        senderScoreDao.upsert(newItem)
    }

    suspend fun setSenderVip(senderId: String, isVip: Boolean) {
        val existing = senderScoreDao.getSenderScore(senderId)
        val newItem = existing?.copy(isVip = isVip, lastUpdated = System.currentTimeMillis()) 
            ?: SenderScoreEntity(senderId = senderId, isVip = isVip)
        senderScoreDao.upsert(newItem)
    }

    // ========== Notifications ==========
    
    fun getPrimaryNotifications(): Flow<List<NotificationEntity>> {
        return notificationDao.getAllNotifications().map { notifications ->
            notifications.filter { notif ->
                val senderId = "${notif.packageName}:${notif.senderName}"
                val config = senderScoreDao.getSenderScore(senderId)
                config?.isPrimary == true
            }
        }
    }

    fun getSpamNotifications(): Flow<List<NotificationEntity>> {
        return notificationDao.getAllNotifications().map { notifications ->
            notifications.filter { notif ->
                val senderId = "${notif.packageName}:${notif.senderName}"
                val config = senderScoreDao.getSenderScore(senderId)
                // Default to SPAM if no config exists (or isPrimary is false)
                config == null || config.isPrimary == false
            }
        }
    }

    suspend fun saveNotification(senderName: String, packageName: String) {
        val senderId = "$packageName:$senderName"
        
        // Ensure sender exists in config (defaults to Spam/Non-VIP)
        if (senderScoreDao.getSenderScore(senderId) == null) {
            senderScoreDao.upsert(SenderScoreEntity(senderId = senderId))
        }

        // Duplicate Check
        val now = System.currentTimeMillis()
        val sinceTimestamp = now - DUPLICATE_WINDOW_MS
        val recentCount = notificationDao.countRecentFromSender(packageName, senderName, sinceTimestamp)
        
        if (recentCount > 0) {
            return
        }

        val entity = NotificationEntity(
            senderName = senderName,
            packageName = packageName,
            timestamp = now
        )
        notificationDao.insert(entity)
    }

    suspend fun clearNotifications() {
        notificationDao.clearAll()
    }
}
