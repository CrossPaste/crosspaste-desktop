package com.crosspaste.e2e.peer

import com.crosspaste.app.AppInfo
import com.crosspaste.db.sync.HostInfo
import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.platform.Platform

/**
 * Builds the [SyncInfo] that describes this headless peer — the same shape the real app
 * advertises over mDNS and pushes on `/sync/telnet`. Shared by [BonjourAdvertiser] (one
 * announcement per interface) and the telnet address-push scenario (one header carrying the
 * peer's subnet addresses) so both present an identical identity to the target.
 *
 * No real server is bound, so [port] is the same placeholder marker the mDNS announcement
 * uses; the target may fail to reach back but the identity it records is still correct.
 */
internal fun buildPeerSyncInfo(
    appInfo: AppInfo,
    hostInfoList: List<HostInfo>,
    port: Int,
): SyncInfo =
    SyncInfo(
        appInfo = appInfo,
        endpointInfo =
            EndpointInfo(
                deviceId = appInfo.appInstanceId,
                deviceName = appInfo.userName,
                platform =
                    Platform(
                        name = Platform.UNKNOWN_OS,
                        arch = "x64",
                        bitMode = 64,
                        version = appInfo.appVersion,
                    ),
                hostInfoList = hostInfoList,
                port = port,
            ),
    )
