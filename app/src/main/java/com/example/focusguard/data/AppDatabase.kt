package com.example.focusguard.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter
    fun fromCategory(category: SenderCategory): String {
        return category.name
    }

    @TypeConverter
    fun toCategory(value: String): SenderCategory {
        return try {
            SenderCategory.valueOf(value)
        } catch (e: IllegalArgumentException) {
            SenderCategory.UNKNOWN
        }
    }
}

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
    
    @Query("SELECT * FROM sender_score_table ORDER BY lastUpdated DESC")
    fun getAllSenders(): Flow<List<SenderScoreEntity>>
    
    @Query("SELECT senderId FROM sender_score_table WHERE category = 'VIP'")
    fun getVipSenderIds(): Flow<List<String>>

    @Query("SELECT * FROM sender_score_table WHERE category = 'UNKNOWN'")
    fun getUnknownSenders(): Flow<List<SenderScoreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(senderScore: SenderScoreEntity)
}

@Database(
    entities = [NotificationEntity::class, SenderScoreEntity::class], 
    version = 5, 
    exportSchema = false
)
@TypeConverters(Converters::class)
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
