package com.nairo.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_events ORDER BY postedAt DESC")
    fun observeAll(): Flow<List<NotificationEvent>>

    @Query("SELECT COUNT(*) FROM notification_events WHERE rawKey = :rawKey")
    suspend fun countByRawKey(rawKey: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: NotificationEvent): Long

    @Query("DELETE FROM notification_events")
    suspend fun clearAll()
}
