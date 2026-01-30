package com.example.focusguard.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores notification metadata (NOT content).
 * 
 * Index on (packageName, senderName, timestamp) helps with:
 * 1. Fast lookup for duplicate checking
 * 2. Ordered queries by timestamp
 */
@Entity(
    tableName = "notification_table",
    indices = [Index(value = ["packageName", "senderName", "timestamp"])]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val senderName: String,
    val timestamp: Long
    // NOTICE: NO CONTENT/TEXT FIELD HERE. PRIVACY FIRST.
)
