package com.example.focusguard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_table")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val senderName: String,
    val timestamp: Long,
    val isPriority: Boolean = false // Result of our decision
    // NOTICE: NO CONTENT/TEXT FIELD HERE. PRIVACY FIRST.
)
