package com.example.focusguard.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: NotificationEntity)

    @Query("SELECT * FROM notification_table ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>
    
    @Query("DELETE FROM notification_table")
    suspend fun clearAll()

    @Query("DELETE FROM notification_table WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("""
        SELECT COUNT(*) FROM notification_table 
        WHERE packageName = :packageName 
        AND senderName = :senderName 
        AND timestamp > :sinceTimestamp
    """)
    suspend fun countRecentFromSender(packageName: String, senderName: String, sinceTimestamp: Long): Int
}

@Dao
interface SenderScoreDao {
    @Query("SELECT * FROM sender_score_table WHERE senderId = :id LIMIT 1")
    suspend fun getSenderScore(id: String): SenderScoreEntity?
    
    // Get all senders with score >= 3 (VIPs)
    @Query("SELECT senderId FROM sender_score_table WHERE userFeedback >= 3")
    fun getVipSenderIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(senderScore: SenderScoreEntity)
}

@Database(
    entities = [NotificationEntity::class, SenderScoreEntity::class], 
    version = 2, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun senderScoreDao(): SenderScoreDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focus_guard_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
