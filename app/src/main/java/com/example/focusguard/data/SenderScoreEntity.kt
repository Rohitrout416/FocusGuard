package com.example.focusguard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sender_score_table")
data class SenderScoreEntity(
    @PrimaryKey val senderId: String, // format: "packageName:senderName"
    val baseScore: Int = 0,
    val userFeedback: Int = 0, // positive = important, negative = spam
    val msgCount: Int = 0,
    val lastBurstTime: Long = 0L,
    val isSpam: Boolean = false
)
