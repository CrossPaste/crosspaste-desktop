package com.clipevery.dao.sync

import com.clipevery.dto.sync.SyncInfo
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class SyncRuntimeInfo : RealmObject {
    @PrimaryKey
    var appInstanceId: String = ""
    var appVersion: String = ""
    var userName: String = ""
    var deviceId: String = ""
    var deviceName: String = ""
    var platformName: String = ""
    var platformArch: String = ""
    var platformBitMode: Int = 64
    var platformVersion: String = ""
    var hostList: RealmList<String> = realmListOf()
    var port: Int = 0
    var noteName: String? = null
    var connectHostAddress: String? = null
    var connectState: Int = SyncState.DISCONNECTED
    var allowSend: Boolean = true
    var allowReceive: Boolean = true
    var createTime: RealmInstant = RealmInstant.now()
    var modifyTime: RealmInstant = RealmInstant.now()
}

fun hostListEqual(
    hostList: List<String>,
    otherHostList: List<String>,
): Boolean {
    if (hostList.size != otherHostList.size) {
        return false
    }
    val sortHostInfoList = hostList.sortedWith { o1, o2 -> o1.compareTo(o2) }
    val otherSortHostInfoList = otherHostList.sortedWith { o1, o2 -> o1.compareTo(o2) }
    for (i in hostList.indices) {
        if (sortHostInfoList[i] != otherSortHostInfoList[i]) {
            return false
        }
    }
    return true
}

fun createSyncRuntimeInfo(syncInfo: SyncInfo): SyncRuntimeInfo {
    return SyncRuntimeInfo().apply {
        appInstanceId = syncInfo.appInfo.appInstanceId
        appVersion = syncInfo.appInfo.appVersion
        userName = syncInfo.appInfo.userName
        deviceId = syncInfo.endpointInfo.deviceId
        deviceName = syncInfo.endpointInfo.deviceName
        platformName = syncInfo.endpointInfo.platform.name
        platformArch = syncInfo.endpointInfo.platform.arch
        platformBitMode = syncInfo.endpointInfo.platform.bitMode
        platformVersion = syncInfo.endpointInfo.platform.version
        hostList = syncInfo.endpointInfo.hostList.toRealmList()
        port = syncInfo.endpointInfo.port
        createTime = RealmInstant.now()
    }
}
