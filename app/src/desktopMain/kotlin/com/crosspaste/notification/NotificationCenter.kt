package com.crosspaste.notification

import com.crosspaste.presist.OneFilePersist
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.util.UUID

@Serializable
data class SavedNotification(
    val id: String,
    val titleKey: String,
    val messageKey: String?,
    val messageType: MessageType,
    val createdAt: Long,
)

@Serializable
private data class NotificationHistory(
    val notifications: List<SavedNotification> = emptyList(),
    val deliveredOnceKeys: Set<String> = emptySet(),
)

/** Local history for notices that explicitly opt in. Toasts use NotificationManager. */
class NotificationCenter(
    private val storage: OneFilePersist,
) {
    private val logger = KotlinLogging.logger {}
    private val mutex = Mutex()
    private var history: NotificationHistory? = null
    private val savedNotifications = MutableStateFlow<List<SavedNotification>?>(null)
    val notifications: StateFlow<List<SavedNotification>?> = savedNotifications.asStateFlow()

    suspend fun load(): Result<Unit> = withHistory { }

    /**
     * Returns true only when the notice was saved. Use a stable onceKey per
     * notice kind, since delivery keys outlive the corresponding entries.
     */
    suspend fun save(
        titleKey: String,
        messageKey: String? = null,
        messageType: MessageType = MessageType.Info,
        onceKey: String? = null,
    ): Result<Boolean> =
        withHistory { current ->
            if (onceKey != null && onceKey in current.deliveredOnceKeys) {
                false
            } else {
                val notice =
                    SavedNotification(
                        id = UUID.randomUUID().toString(),
                        titleKey = titleKey,
                        messageKey = messageKey,
                        messageType = messageType,
                        createdAt = DateUtils.nowEpochMilliseconds(),
                    )
                commit(
                    current.copy(
                        notifications = listOf(notice) + current.notifications.take(MAX_ENTRIES - 1),
                        deliveredOnceKeys = current.deliveredOnceKeys + listOfNotNull(onceKey),
                    ),
                )
                true
            }
        }

    suspend fun clearAll(): Result<Unit> =
        withHistory { current ->
            if (current.notifications.isNotEmpty()) {
                commit(current.copy(notifications = emptyList()))
            }
        }

    private fun loadHistory(): NotificationHistory =
        try {
            storage.read(NotificationHistory::class) ?: NotificationHistory()
        } catch (e: SerializationException) {
            // Let a failed backup propagate. A later retry can recover without overwriting the original.
            val backupPath = storage.quarantine()
            logger.warn(e) { "Notification history is corrupt; moved it to $backupPath" }
            NotificationHistory()
        }

    private fun commit(updated: NotificationHistory) {
        storage.save(updated)
        publish(updated)
    }

    private fun publish(updated: NotificationHistory) {
        history = updated
        savedNotifications.value = updated.notifications
    }

    private suspend fun <T> withHistory(action: (NotificationHistory) -> T): Result<T> =
        withContext(ioDispatcher) {
            mutex.withLock {
                runCatching {
                    action(history ?: loadHistory().also(::publish))
                }.onFailure { logger.error(it) { "Notification history operation failed" } }
            }
        }

    private companion object {
        const val MAX_ENTRIES = 100
    }
}
