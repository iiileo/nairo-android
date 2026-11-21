package com.nairo.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BoundAccountDao {
    @Query("SELECT * FROM bound_accounts ORDER BY boundAt DESC")
    fun observeAll(): Flow<List<BoundAccount>>

    @Query("SELECT * FROM bound_accounts ORDER BY boundAt DESC")
    suspend fun listAll(): List<BoundAccount>

    @Query("SELECT * FROM bound_accounts WHERE callbackId = :callbackId LIMIT 1")
    suspend fun get(callbackId: String): BoundAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: BoundAccount)

    @Query("DELETE FROM bound_accounts WHERE callbackId = :callbackId")
    suspend fun delete(callbackId: String)

    @Query(
        """
        UPDATE bound_accounts
        SET lastHeartbeatAt = :at, lastHeartbeatOk = :ok, lastError = :error
        WHERE callbackId = :callbackId
        """,
    )
    suspend fun updateHeartbeat(callbackId: String, at: Long, ok: Boolean, error: String?)

    @Query("UPDATE bound_accounts SET channelsCsv = :csv WHERE callbackId = :callbackId")
    suspend fun updateChannels(callbackId: String, csv: String)
}
