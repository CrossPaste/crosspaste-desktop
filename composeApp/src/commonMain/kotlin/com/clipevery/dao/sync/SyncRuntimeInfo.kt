package com.clipevery.dao.sync

import io.realm.kotlin.ext.realmListOf
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
    var hostInfoList: RealmList<HostInfo> = realmListOf()
    var port: Int = 0
    var noteName: String? = null
    var connectHostAddress: String? = null
    var connectState: Int = SyncState.DISCONNECTED
    var allowSend: Boolean = true
    var allowReceive: Boolean = true
    var createTime: RealmInstant = RealmInstant.now()
    var modifyTime: RealmInstant = RealmInstant.now()
}

fun hostInfoListEqual(
    hostInfoList: RealmList<HostInfo>,
    otherHostInfoList: RealmList<HostInfo>,
): Boolean {
    if (hostInfoList.size != otherHostInfoList.size) {
        return false
    }
    val sortHostInfoList = hostInfoList.sortedWith { o1, o2 -> o1.hostAddress.compareTo(o2.hostAddress) }
    val otherSortHostInfoList = otherHostInfoList.sortedWith { o1, o2 -> o1.hostAddress.compareTo(o2.hostAddress) }
    for (i in 0 until hostInfoList.size) {
        if (sortHostInfoList[i].hostAddress != otherSortHostInfoList[i].hostAddress) {
            return false
        }
    }
    return true
}
