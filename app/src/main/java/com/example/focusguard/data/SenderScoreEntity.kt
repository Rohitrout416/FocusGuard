package com.example.focusguard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sender_score_table")
data class SenderScoreEntity(
    @PrimaryKey val senderId: String, // format: "packageName:senderName"
    val baseScore: Int = 0,
    val userFeedbackPositive: Int = 0,
    val userFeedbackNegative: Int = 0,
    val messageCount: Int = 0,
    val lastMessageTimestamp: Long = 0
)
