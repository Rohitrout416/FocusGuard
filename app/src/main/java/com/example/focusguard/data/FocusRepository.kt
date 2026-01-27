package com.example.focusguard.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

class FocusRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("focus_guard_prefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(context)

    // Focus Mode Logic
    fun isFocusModeActive(): Boolean {
        return prefs.getBoolean("is_focus_mode_active", false)
    }

    fun setFocusModeActive(apiKey: Boolean) {
        prefs.edit().putBoolean("is_focus_mode_active", apiKey).apply()
    }

    // Notification Logic
    suspend fun saveNotification(notification: NotificationEntity) {
        database.notificationDao().insert(notification)
    }

    fun getAllHeldNotifications(): Flow<List<NotificationEntity>> {
        return database.notificationDao().getAllNotifications()
    }
    
    suspend fun clearNotifications() {
        database.notificationDao().clearAll()
    }
}
