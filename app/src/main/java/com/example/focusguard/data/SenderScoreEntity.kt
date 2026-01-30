package com.example.focusguard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores configuration/categorization for a specific sender.
 * SenderID format: "packageName:senderName"
 */
@Entity(tableName = "sender_score_table")
data class SenderScoreEntity(
    @PrimaryKey val senderId: String,
    
    // Categorization: Primary vs Spam
    // Default is FALSE (Spam/Secondary) as per requirement: "All other messages... automatically placed in Spam"
    val isPrimary: Boolean = false, 

    // Focus Mode Bypass
    // Default is FALSE (Blocked during Focus)
    val isVip: Boolean = false,

    val lastUpdated: Long = System.currentTimeMillis()
)
