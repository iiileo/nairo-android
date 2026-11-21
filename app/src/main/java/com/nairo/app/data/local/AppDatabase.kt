package com.nairo.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [NotificationEvent::class, BoundAccount::class, PushOutbox::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun boundAccountDao(): BoundAccountDao
    abstract fun pushOutboxDao(): PushOutboxDao
}
