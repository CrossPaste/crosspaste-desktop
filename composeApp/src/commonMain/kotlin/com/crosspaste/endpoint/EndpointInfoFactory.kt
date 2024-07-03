package com.crosspaste.endpoint

import com.crosspaste.dao.sync.HostInfo

interface EndpointInfoFactory {

    fun createEndpointInfo(hostInfoFilter: (HostInfo) -> Boolean = { true }): EndpointInfo
}
