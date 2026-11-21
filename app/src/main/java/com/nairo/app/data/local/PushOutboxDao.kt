package com.nairo.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PushOutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(item: PushOutbox): Long

    @Query(
        """
        SELECT * FROM push_outbox
        WHERE status = 'pending' AND attempts < :maxAttempts
        ORDER BY createdAt ASC
        LIMIT :limit
        """,
    )
    suspend fun pending(limit: Int = 20, maxAttempts: Int = 8): List<PushOutbox>

    @Query("SELECT * FROM push_outbox ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<PushOutbox>>

    @Query(
        """
        UPDATE push_outbox
        SET status = :status, attempts = :attempts, lastError = :error, updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateResult(
        id: Long,
        status: String,
        attempts: Int,
        error: String?,
        updatedAt: Long,
    )

    /** 手动重试：失败项重置为待上报并清零次数。 */
    @Query(
        """
        UPDATE push_outbox
        SET status = 'pending', attempts = 0, lastError = NULL, updatedAt = :now
        WHERE id = :id AND status = 'failed'
        """,
    )
    suspend fun resetForRetry(id: Long, now: Long): Int

    @Query(
        """
        UPDATE push_outbox
        SET status = 'pending', attempts = 0, lastError = NULL, updatedAt = :now
        WHERE status = 'failed'
        """,
    )
    suspend fun resetAllFailed(now: Long): Int

    @Query("SELECT COUNT(*) FROM push_outbox WHERE status = 'pending'")
    suspend fun pendingCount(): Int

    @Query("DELETE FROM push_outbox")
    suspend fun clearAll()
}
