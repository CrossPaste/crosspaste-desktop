package com.crosspaste.paste.plugin.process

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Discards non-file paste items larger than [com.crosspaste.config.AppConfig.maxNonFilePasteSize].
 *
 * Only non-file carrier types (text/html/rtf/url/color) are subject to the limit;
 * file and image pastes store their payload on disk and are governed by
 * maxSyncFileSize instead. Oversized items are silently dropped (flavor
 * degradation); a notification is pushed only when every item is discarded —
 * the resulting empty list makes PasteReleaseService mark-delete the paste row.
 */
class DiscardOversizedNonFilePlugin(
    private val configManager: CommonConfigManager,
    private val notificationManager: NotificationManager,
) : PasteProcessPlugin {

    companion object {
        private val NON_FILE_PASTE_TYPES =
            setOf(
                PasteType.TEXT_TYPE,
                PasteType.HTML_TYPE,
                PasteType.RTF_TYPE,
                PasteType.URL_TYPE,
                PasteType.COLOR_TYPE,
            )

        private val fileUtils = getFileUtils()

        fun isNonFilePasteType(pasteType: PasteType): Boolean = pasteType in NON_FILE_PASTE_TYPES
    }

    private val logger = KotlinLogging.logger {}

    override fun process(
        pasteCoordinate: PasteCoordinate,
        pasteItems: List<PasteItem>,
        source: String?,
    ): List<PasteItem> {
        val config = configManager.getCurrentConfig()
        if (!config.enabledNonFilePasteSizeLimit) {
            return pasteItems
        }
        val maxSize = fileUtils.bytesSize(config.maxNonFilePasteSize)
        val retainedItems =
            pasteItems.filter { pasteItem ->
                val oversized =
                    isNonFilePasteType(pasteItem.getPasteType()) && pasteItem.size > maxSize
                if (oversized) {
                    logger.info {
                        "Discard oversized ${pasteItem.getPasteType().name} paste item: " +
                            "size=${pasteItem.size}, limit=$maxSize, id=${pasteCoordinate.id}"
                    }
                }
                !oversized
            }
        if (retainedItems.isEmpty() && pasteItems.isNotEmpty()) {
            notificationManager.sendNotification(
                title = { it.getText("non_file_paste_discarded_too_large") },
                messageType = MessageType.Warning,
            )
        }
        return retainedItems
    }
}
