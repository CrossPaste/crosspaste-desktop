package com.clipevery.net

interface DeviceRefresher {
    suspend fun refresh(checkAction: CheckAction)
}
