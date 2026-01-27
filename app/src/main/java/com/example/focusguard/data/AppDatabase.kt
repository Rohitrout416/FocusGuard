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

// DAO for notifications
@Dao
interface NotificationDao {
    @Insert
    suspend fun insert(notification: NotificationEntity)

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("DELETE FROM notifications")
    suspend fun clearAll()

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun delete(id: Int)
}

// DAO for sender scores
@Dao
interface SenderScoreDao {
    @Query("SELECT * FROM sender_scores WHERE senderId = :id LIMIT 1")
    suspend fun getSenderScore(id: String): SenderScoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(score: SenderScoreEntity)

    @Query("UPDATE sender_scores SET isSpam = :isSpam WHERE senderId = :id")
    suspend fun setSpam(id: String, isSpam: Boolean)

    @Query("UPDATE sender_scores SET userFeedback = userFeedback + :delta WHERE senderId = :id")
    suspend fun updateFeedback(id: String, delta: Int)
}

// Database
@Database(
    entities = [NotificationEntity::class, SenderScoreEntity::class],
    version = 1,
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
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focusguard_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
