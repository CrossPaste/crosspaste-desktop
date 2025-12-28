package com.crosspaste.net

import com.crosspaste.db.sync.HostInfo

interface PasteBonjourService {

    fun refreshAll()

    fun refreshTarget(
        appInstanceId: String,
        hostInfoList: List<HostInfo>,
    )

    fun close()
}
