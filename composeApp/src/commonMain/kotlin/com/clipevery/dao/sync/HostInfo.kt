package com.clipevery.dao.sync

import io.realm.kotlin.types.EmbeddedRealmObject
import kotlinx.serialization.Serializable

@Serializable
class HostInfo : EmbeddedRealmObject {
    var hostName: String = ""
    var hostAddress: String = ""
}
