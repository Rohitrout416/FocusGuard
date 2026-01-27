package com.example.focusguard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks sender importance scores for ranking.
 * The "brain" that learns from user feedback.
 */
@Entity(tableName = "sender_scores")
data class SenderScoreEntity(
    @PrimaryKey val senderId: String, // Format: "packageName:senderName"
    val baseScore: Int = 0,
    val userFeedback: Int = 0, // +1 for important, -1 for spam
    val msgCount: Int = 0,
    val lastBurstTime: Long = 0, // For detecting rapid-fire messages
    val isSpam: Boolean = false
)
