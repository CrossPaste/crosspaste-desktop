package com.crosspaste.net

import com.crosspaste.platform.macos.api.MacosApi
import com.crosspaste.platform.macos.api.NetworkChangeCallback
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * macOS [NetworkStateMonitor] backed by `Network.framework`'s `NWPathMonitor`
 * (bridged through the MacosApi dylib).
 *
 * The native monitor fires an initial path update on [start] — a free cold-start
 * signal — and again on every interface up/down, IP change, or reachability change.
 */
class MacosNetworkStateMonitor : NetworkStateMonitor {

    private val logger = KotlinLogging.logger {}

    private val _networkChanges =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val networkChanges: Flow<Unit> = _networkChanges

    // Strong reference required: JNA does not retain the callback, so without this
    // field the native side could invoke a garbage-collected closure.
    private val callback =
        NetworkChangeCallback {
            _networkChanges.tryEmit(Unit)
        }

    override fun start() {
        runCatching {
            MacosApi.INSTANCE.startNetworkStateMonitor(callback)
        }.onFailure { e ->
            logger.warn(e) { "Failed to start macOS network state monitor" }
        }
    }

    override fun stop() {
        runCatching {
            MacosApi.INSTANCE.stopNetworkStateMonitor()
        }.onFailure { e ->
            logger.warn(e) { "Failed to stop macOS network state monitor" }
        }
    }
}
