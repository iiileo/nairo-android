package com.nairo.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_events")
data class NotificationEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val body: String,
    val ticker: String,
    val postedAt: Long,
    val channelHint: String,
    val amountHint: String?,
    val rawKey: String,
)
