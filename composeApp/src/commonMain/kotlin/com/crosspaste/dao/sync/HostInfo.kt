package com.crosspaste.dao.sync

import io.realm.kotlin.types.EmbeddedRealmObject
import kotlinx.serialization.Serializable

@Serializable
class HostInfo : EmbeddedRealmObject {

    companion object {}

    var networkPrefixLength: Short = 0
    var hostAddress: String = ""

    override fun toString(): String {
        return "HostInfo(networkPrefixLength=$networkPrefixLength, hostAddress='$hostAddress')"
    }
}
