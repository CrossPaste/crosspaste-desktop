package com.clipevery.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.clipevery.dao.signal.SignalDao
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.SyncRuntimeInfoDao
import com.clipevery.net.clientapi.DesktopSyncClientApi
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.signal.libsignal.protocol.state.SignalProtocolStore
import java.util.concurrent.ConcurrentHashMap

class DesktopClientHandlerManager(private val clipClient: ClipClient,
                                  private val signalProtocolStore: SignalProtocolStore,
                                  private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
                                  private val signalDao: SignalDao): ClientHandlerManager {

    private val logger = KotlinLogging.logger {}

    private val handlerMap: MutableMap<String, DesktopClientHandler>  = ConcurrentHashMap()

    var checking by mutableStateOf(false)


    override fun start() {
        // todo start network monitor server
        logger.info { "Starting DesktopClientHandlerManager" }
        val handlers = syncRuntimeInfoDao.getAllSyncRuntimeInfos()
            .map { syncRuntimeInfo -> createHandler(syncRuntimeInfo) }

        handlers.forEach { handler ->
            handlerMap.computeIfAbsent(handler.getId()) { handler }
        }
    }

    override fun stop() {
        // todo stop network monitor server
    }

    override suspend fun checkConnects(checkAction: CheckAction) {
        coroutineScope {
            handlerMap.values.forEach { handler ->
                launch {
                    if (handler.checkHandler(checkAction)) {
                        logger.info { "check ${handler.getId()} handler success" }
                    }
                }
            }
        }
    }

    override suspend fun checkConnect(id: String, checkAction: CheckAction): Boolean {
        handlerMap[id]?.let { handler ->
            return handler.checkHandler(checkAction)
        }
        return false
    }

    private fun createHandler(syncRuntimeInfo: SyncRuntimeInfo): DesktopClientHandler {
        val syncClientApi = DesktopSyncClientApi(clipClient)
        return DesktopClientHandler(syncRuntimeInfo, syncClientApi, signalProtocolStore, syncRuntimeInfoDao)
    }

    override fun addHandler(id: String) {
        try {
            syncRuntimeInfoDao.getSyncRuntimeInfo(id)?.let {
                val handler = createHandler(it)
                handlerMap.computeIfAbsent(handler.getId()) { handler }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to add handler for $id" }
        }
    }

    override fun removeHandler(id: String) {
        handlerMap.remove(id)
    }

}
