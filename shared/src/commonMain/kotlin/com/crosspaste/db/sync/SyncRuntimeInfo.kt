package com.crosspaste.db.sync

import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.platform.Platform
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getJsonUtils
import kotlinx.serialization.Serializable

@Serializable
data class SyncRuntimeInfo(
    val appInstanceId: String,
    val appVersion: String,
    val userName: String,
    val deviceId: String,
    val deviceName: String,
    val platform: Platform,
    val hostInfoList: List<HostInfo> = listOf(),
    val port: Int = 0,
    val noteName: String? = null,
    val connectNetworkPrefixLength: Short? = null,
    val connectHostAddress: String? = null,
    val connectState: Int = SyncState.DISCONNECTED,
    val allowSend: Boolean = true,
    val allowReceive: Boolean = true,
    val createTime: Long = DateUtils.nowEpochMilliseconds(),
    val modifyTime: Long = DateUtils.nowEpochMilliseconds(),
) {

    companion object {

        private val jsonUtils = getJsonUtils()

        fun mapper(
            appInstanceId: String,
            appVersion: String,
            userName: String,
            deviceId: String,
            deviceName: String,
            platformName: String,
            platformArch: String,
            platformBitMode: Long,
            platformVersion: String,
            hostInfo: String,
            port: Long,
            noteName: String?,
            connectNetworkPrefixLength: Long?,
            connectHostAddress: String?,
            connectState: Long,
            allowSend: Boolean,
            allowReceive: Boolean,
            createTime: Long,
            modifyTime: Long,
        ): SyncRuntimeInfo =
            SyncRuntimeInfo(
                appInstanceId = appInstanceId,
                appVersion = appVersion,
                userName = userName,
                deviceId = deviceId,
                deviceName = deviceName,
                platform =
                    Platform(
                        name = platformName,
                        arch = platformArch,
                        bitMode = platformBitMode.toInt(),
                        version = platformVersion,
                    ),
                hostInfoList = jsonUtils.JSON.decodeFromString(hostInfo),
                port = port.toInt(),
                noteName = noteName,
                connectNetworkPrefixLength = connectNetworkPrefixLength?.toShort(),
                connectHostAddress = connectHostAddress,
                connectState = connectState.toInt(),
                allowSend = allowSend,
                allowReceive = allowReceive,
                createTime = createTime,
                modifyTime = modifyTime,
            )

        fun createSyncRuntimeInfo(syncInfo: SyncInfo): SyncRuntimeInfo =
            SyncRuntimeInfo(
                appInstanceId = syncInfo.appInfo.appInstanceId,
                appVersion = syncInfo.appInfo.appVersion,
                userName = syncInfo.appInfo.userName,
                deviceId = syncInfo.endpointInfo.deviceId,
                deviceName = syncInfo.endpointInfo.deviceName,
                platform = syncInfo.endpointInfo.platform,
                hostInfoList = syncInfo.endpointInfo.hostInfoList,
                port = syncInfo.endpointInfo.port,
            )

        /**
         * Structural equality of host lists, ignoring order and [HostInfo.lastSeen].
         *
         * The prefix participates because it feeds address selection and same-subnet
         * checks — a same-address prefix change must count as a change and reach the
         * database. [HostInfo.lastSeen] is deliberately excluded: it is a local
         * recency stamp refreshed on every merge, so including it would turn every
         * re-advertisement into a spurious "change"; it only persists as a side
         * effect of writes triggered by real changes.
         */
        fun hostInfoListEqual(
            hostInfoList: List<HostInfo>,
            otherHostInfoList: List<HostInfo>,
        ): Boolean {
            if (hostInfoList.size != otherHostInfoList.size) {
                return false
            }
            val comparator = compareBy<HostInfo>({ it.hostAddress }, { it.networkPrefixLength })
            val sortHostInfoList = hostInfoList.sortedWith(comparator)
            val otherSortHostInfoList = otherHostInfoList.sortedWith(comparator)
            for (i in sortHostInfoList.indices) {
                if (sortHostInfoList[i].hostAddress != otherSortHostInfoList[i].hostAddress ||
                    sortHostInfoList[i].networkPrefixLength != otherSortHostInfoList[i].networkPrefixLength
                ) {
                    return false
                }
            }
            return true
        }
    }

    fun getDeviceDisplayName(): String = noteName ?: deviceName

    fun diffSyncInfo(syncInfo: SyncInfo): Boolean {
        if (port != syncInfo.endpointInfo.port) {
            return true
        }
        if (!hostInfoListEqual(hostInfoList, syncInfo.endpointInfo.hostInfoList)) {
            return true
        }
        if (appInstanceId != syncInfo.appInfo.appInstanceId) {
            return true
        }
        if (appVersion != syncInfo.appInfo.appVersion) {
            return true
        }
        if (userName != syncInfo.appInfo.userName) {
            return true
        }
        if (deviceId != syncInfo.endpointInfo.deviceId) {
            return true
        }
        if (deviceName != syncInfo.endpointInfo.deviceName) {
            return true
        }
        if (platform != syncInfo.endpointInfo.platform) {
            return true
        }
        return false
    }
}
