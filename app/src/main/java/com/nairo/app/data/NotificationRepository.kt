package com.nairo.app.data

import com.nairo.app.data.local.NotificationDao
import com.nairo.app.data.local.NotificationEvent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface NotificationRepository {
    val events: Flow<List<NotificationEvent>>
    suspend fun saveIfNew(event: NotificationEvent): Boolean
    suspend fun clear()
}

@Singleton
class DefaultNotificationRepository @Inject constructor(
    private val dao: NotificationDao,
) : NotificationRepository {
    override val events: Flow<List<NotificationEvent>> = dao.observeAll()

    override suspend fun saveIfNew(event: NotificationEvent): Boolean {
        if (dao.countByRawKey(event.rawKey) > 0) return false
        return dao.insert(event) > 0
    }

    override suspend fun clear() = dao.clearAll()
}
