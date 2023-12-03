package com.clipevery.model

import org.signal.libsignal.protocol.SignalProtocolAddress

data class SyncInfo(
    val appInfo: AppInfo,
    val endpointInfo: EndpointInfo,
    val state: SyncState
): SignalProtocolAddress(appInfo.appInstanceId, 1)
