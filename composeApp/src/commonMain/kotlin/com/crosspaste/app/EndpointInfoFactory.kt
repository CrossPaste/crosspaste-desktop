package com.crosspaste.app

import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.realm.sync.HostInfo

interface EndpointInfoFactory {

    fun createEndpointInfo(hostInfoFilter: (HostInfo) -> Boolean = { true }): EndpointInfo
}
