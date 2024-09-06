package com.crosspaste.app

import com.crosspaste.dao.sync.HostInfo
import com.crosspaste.dto.sync.EndpointInfo

interface EndpointInfoFactory {

    fun createEndpointInfo(hostInfoFilter: (HostInfo) -> Boolean = { true }): EndpointInfo
}
