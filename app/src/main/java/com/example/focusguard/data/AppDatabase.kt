package com.example.focusguard.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Dao
interface NotificationDao {
    @Insert
    suspend fun insert(notification: NotificationEntity)

    @Query("SELECT * FROM notification_table ORDER BY timestamp DESC")
    fun getAllNotifications(): kotlinx.coroutines.flow.Flow<List<NotificationEntity>>
    
    @Query("DELETE FROM notification_table")
    suspend fun clearAll()
}

@Dao
interface SenderScoreDao {
    @Query("SELECT * FROM sender_score_table WHERE senderId = :id LIMIT 1")
    suspend fun getSenderScore(id: String): SenderScoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(senderScore: SenderScoreEntity)
}

@Database(entities = [NotificationEntity::class, SenderScoreEntity::class], version = 1, exportSchema = false)
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
