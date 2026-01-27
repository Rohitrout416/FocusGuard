package com.example.focusguard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores metadata about held notifications.
 * PRIVACY: No message content is stored - only sender/app/time.
 */
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val senderName: String,
    val packageName: String,
    val timestamp: Long
    // NO content field - privacy first
)
