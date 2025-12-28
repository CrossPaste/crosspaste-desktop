package com.crosspaste.net

import com.crosspaste.db.sync.HostInfo

interface PasteBonjourService {

    fun request(
        appInstanceId: String,
        hostInfoList: List<HostInfo>,
    )

    fun close()
}
