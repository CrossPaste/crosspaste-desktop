package com.clipevery.endpoint

import com.clipevery.dao.sync.HostInfo

interface EndpointInfoFactory {

    fun createEndpointInfo(hostInfoFilter: (HostInfo) -> Boolean = { true }): EndpointInfo
}
