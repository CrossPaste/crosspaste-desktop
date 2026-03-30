package com.crosspaste.net.ws

import com.crosspaste.app.AppControl
import com.crosspaste.net.routing.SyncRoutingApi
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteboardService
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.getJsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WsMessageHandler(
    private val lazyAppControl: Lazy<AppControl>,
    private val lazyPasteboardService: Lazy<PasteboardService>,
    private val secureStore: SecureStore,
    private val lazySyncRoutingApi: Lazy<SyncRoutingApi>,
    private val wsSessionManager: WsSessionManager,
    private val scope: CoroutineScope,
) {
    private val appControl: AppControl get() = lazyAppControl.value
    private val pasteboardService: PasteboardService get() = lazyPasteboardService.value
    private val syncRoutingApi: SyncRoutingApi get() = lazySyncRoutingApi.value
    private val logger = KotlinLogging.logger {}
    private val json = getJsonUtils().JSON

    suspend fun handleMessage(
        appInstanceId: String,
        envelope: WsEnvelope,
    ) {
        logger.debug { "WS message from $appInstanceId: type=${envelope.type}" }

        when (envelope.type) {
            WsMessageType.HEARTBEAT -> {
                wsSessionManager.send(
                    appInstanceId,
                    WsEnvelope(type = WsMessageType.HEARTBEAT_ACK),
                )
            }

            WsMessageType.HEARTBEAT_ACK -> {
                // Peer acknowledged our heartbeat — connection is alive
                logger.debug { "Heartbeat ACK from $appInstanceId" }
            }

            WsMessageType.PASTE_PUSH -> {
                handlePastePush(appInstanceId, envelope)
            }

            WsMessageType.NOTIFY_EXIT -> {
                logger.info { "WS notify exit from $appInstanceId" }
                syncRoutingApi.markExit(appInstanceId)
            }

            WsMessageType.NOTIFY_REMOVE -> {
                logger.info { "WS notify remove from $appInstanceId" }
                syncRoutingApi.removeSyncHandler(appInstanceId)
            }

            else -> {
                logger.warn { "Unknown WS message type: ${envelope.type} from $appInstanceId" }
            }
        }
    }

    private suspend fun handlePastePush(
        appInstanceId: String,
        envelope: WsEnvelope,
    ) {
        val syncHandler =
            syncRoutingApi.getSyncHandler(appInstanceId) ?: run {
                logger.error { "WS paste_push: no sync handler for $appInstanceId" }
                return
            }

        if (!syncHandler.currentSyncRuntimeInfo.allowReceive) {
            logger.debug { "WS paste_push from $appInstanceId: user not allow receive" }
            return
        }

        if (!appControl.isReceiveEnabled()) {
            logger.debug { "WS paste_push from $appInstanceId: app not allow receive" }
            return
        }

        runCatching {
            val decrypted = secureStore.getMessageProcessor(appInstanceId).decrypt(envelope.payload)
            val pasteData = json.decodeFromString<PasteData>(decrypted.decodeToString())

            scope.launch {
                pasteboardService.tryWriteRemotePasteboard(pasteData)
            }
            appControl.completeReceiveOperation()
            logger.debug { "WS paste_push from $appInstanceId processed successfully" }
        }.onFailure { e ->
            logger.error(e) { "WS paste_push from $appInstanceId failed" }
        }
    }
}
