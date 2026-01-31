package com.example.focusguard.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SenderCategory {
    UNKNOWN, // Default for new senders. Shows in Primary list but silent. Blocked in Focus Mode.
    SPAM,    // Hidden in Spam list. Blocked.
    PRIMARY, // Shows in Primary list. Blocked in Focus Mode.
    VIP      // Shows in Primary list. Allowed in Focus Mode.
}

/**
 * Stores configuration/categorization for a specific sender.
 * SenderID format: "packageName:senderName"
 * Index on category for fast VIP/UNKNOWN lookups.
 */
@Entity(
    tableName = "sender_score_table",
    indices = [Index(value = ["category"])]
)
data class SenderScoreEntity(
    @PrimaryKey val senderId: String,
    
    // Categorization
    val category: SenderCategory = SenderCategory.UNKNOWN,
    
    // Stats
    val msgCount: Int = 0, // Track message frequency

    val lastUpdated: Long = System.currentTimeMillis()
)
