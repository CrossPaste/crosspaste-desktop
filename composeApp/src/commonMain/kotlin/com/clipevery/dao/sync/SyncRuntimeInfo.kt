package com.clipevery.dao.sync

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class SyncRuntimeInfo: RealmObject {
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
    var connectHostAddress: String? = null
    var connectState: Int = 0
    var allowSend: Boolean = true
    var allowReceive: Boolean = true
    var createTime: RealmInstant = RealmInstant.now()
    var modifyTime: RealmInstant = RealmInstant.now()
}
