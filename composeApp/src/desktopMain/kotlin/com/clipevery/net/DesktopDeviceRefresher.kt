package com.clipevery.net

import com.clipevery.utils.ioDispatcher
import kotlinx.coroutines.withContext

class DesktopDeviceRefresher(private val clientHandlerManager: ClientHandlerManager): DeviceRefresher {

    override suspend fun refresh(checkAction: CheckAction) {
        withContext(ioDispatcher) {
            clientHandlerManager.checkConnects(checkAction)
        }
    }
}
