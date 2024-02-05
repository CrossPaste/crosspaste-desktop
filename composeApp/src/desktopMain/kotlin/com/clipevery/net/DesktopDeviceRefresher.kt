package com.clipevery.net

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.clipevery.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DesktopDeviceRefresher(private val clientHandlerManager: ClientHandlerManager): DeviceRefresher {

    val logger = KotlinLogging.logger {}

    private var _refreshing = mutableStateOf(false)

    override val isRefreshing: State<Boolean> get() = _refreshing

    override fun refresh(checkAction: CheckAction) {
        _refreshing.value = true
        CoroutineScope(ioDispatcher).launch {
            logger.info { "start launch" }
            try {
                clientHandlerManager.checkConnects(checkAction)
            } catch (e: Exception) {
                logger.error(e) { "checkConnects error" }
            }
            delay(1000)
            logger.info { "set refreshing false" }
            _refreshing.value = false
        }
    }
}
