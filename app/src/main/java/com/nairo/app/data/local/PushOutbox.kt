package com.nairo.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "push_outbox",
    indices = [Index(value = ["rawKey", "callbackId"], unique = true)],
)
data class PushOutbox(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawKey: String,
    val callbackId: String,
    val channel: String,
    val price: String,
    val status: String = STATUS_PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_DONE = "done"
        const val STATUS_FAILED = "failed"
    }
}
