package com.crosspaste.sync

import com.crosspaste.app.AppInfo
import com.crosspaste.db.sync.HostInfo
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncState
import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.platform.Platform

object SyncTestFixtures {

    val TEST_PLATFORM =
        Platform(
            name = "Desktop",
            arch = "x86_64",
            bitMode = 64,
            version = "1.0.0",
        )

    fun createAppInfo(
        appInstanceId: String = "test-app-1",
        appVersion: String = "1.0.0",
        appRevision: String = "abc123",
        userName: String = "testUser",
    ): AppInfo =
        AppInfo(
            appInstanceId = appInstanceId,
            appVersion = appVersion,
            appRevision = appRevision,
            userName = userName,
        )

    fun createEndpointInfo(
        deviceId: String = "test-device-1",
        deviceName: String = "Test Device",
        platform: Platform = TEST_PLATFORM,
        hostInfoList: List<HostInfo> = listOf(HostInfo(networkPrefixLength = 24, hostAddress = "192.168.1.100")),
        port: Int = 13129,
    ): EndpointInfo =
        EndpointInfo(
            deviceId = deviceId,
            deviceName = deviceName,
            platform = platform,
            hostInfoList = hostInfoList,
            port = port,
        )

    fun createSyncInfo(
        appInstanceId: String = "test-app-1",
        appVersion: String = "1.0.0",
        deviceId: String = "test-device-1",
        deviceName: String = "Test Device",
        hostInfoList: List<HostInfo> = listOf(HostInfo(networkPrefixLength = 24, hostAddress = "192.168.1.100")),
        port: Int = 13129,
    ): SyncInfo =
        SyncInfo(
            appInfo = createAppInfo(appInstanceId = appInstanceId, appVersion = appVersion),
            endpointInfo =
                createEndpointInfo(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    hostInfoList = hostInfoList,
                    port = port,
                ),
        )

    fun createSyncRuntimeInfo(
        appInstanceId: String = "test-app-1",
        appVersion: String = "1.0.0",
        userName: String = "testUser",
        deviceId: String = "test-device-1",
        deviceName: String = "Test Device",
        platform: Platform = TEST_PLATFORM,
        hostInfoList: List<HostInfo> = listOf(HostInfo(networkPrefixLength = 24, hostAddress = "192.168.1.100")),
        port: Int = 13129,
        connectHostAddress: String? = null,
        connectNetworkPrefixLength: Short? = null,
        connectState: Int = SyncState.DISCONNECTED,
        allowSend: Boolean = true,
        allowReceive: Boolean = true,
        noteName: String? = null,
    ): SyncRuntimeInfo =
        SyncRuntimeInfo(
            appInstanceId = appInstanceId,
            appVersion = appVersion,
            userName = userName,
            deviceId = deviceId,
            deviceName = deviceName,
            platform = platform,
            hostInfoList = hostInfoList,
            port = port,
            connectHostAddress = connectHostAddress,
            connectNetworkPrefixLength = connectNetworkPrefixLength,
            connectState = connectState,
            allowSend = allowSend,
            allowReceive = allowReceive,
            noteName = noteName,
        )

    fun createConnectedSyncRuntimeInfo(
        appInstanceId: String = "test-app-1",
        hostAddress: String = "192.168.1.100",
        port: Int = 13129,
    ): SyncRuntimeInfo =
        createSyncRuntimeInfo(
            appInstanceId = appInstanceId,
            connectHostAddress = hostAddress,
            connectNetworkPrefixLength = 24,
            connectState = SyncState.CONNECTED,
            port = port,
        )

    fun createConnectingSyncRuntimeInfo(
        appInstanceId: String = "test-app-1",
        hostAddress: String = "192.168.1.100",
        port: Int = 13129,
    ): SyncRuntimeInfo =
        createSyncRuntimeInfo(
            appInstanceId = appInstanceId,
            connectHostAddress = hostAddress,
            connectNetworkPrefixLength = 24,
            connectState = SyncState.CONNECTING,
            port = port,
        )

    fun createUnverifiedSyncRuntimeInfo(
        appInstanceId: String = "test-app-1",
        hostAddress: String = "192.168.1.100",
        port: Int = 13129,
    ): SyncRuntimeInfo =
        createSyncRuntimeInfo(
            appInstanceId = appInstanceId,
            connectHostAddress = hostAddress,
            connectNetworkPrefixLength = 24,
            connectState = SyncState.UNVERIFIED,
            port = port,
        )
}
