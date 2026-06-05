package com.crosspaste.dto.sync

import com.crosspaste.app.AppInfo
import com.crosspaste.db.sync.HostInfo
import com.crosspaste.utils.DateUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SyncInfo(
    val appInfo: AppInfo,
    val endpointInfo: EndpointInfo,
) {
    /**
     * Merge [other]'s freshly-advertised endpoint into this one. [other] wins on metadata
     * and its addresses are stamped newest; the host list is recency-ordered and capped
     * via [HostInfo.mergeRecent] so accumulated addresses stay bounded (#4499).
     */
    fun merge(
        other: SyncInfo,
        now: Long = DateUtils.nowEpochMilliseconds(),
    ): SyncInfo {
        val mergedList =
            HostInfo.mergeRecent(
                existing = endpointInfo.hostInfoList,
                incoming = other.endpointInfo.hostInfoList,
                now = now,
            )

        val appInfo = other.appInfo
        val endpointInfo =
            EndpointInfo(
                deviceId = other.endpointInfo.deviceId,
                deviceName = other.endpointInfo.deviceName,
                platform = other.endpointInfo.platform,
                hostInfoList = mergedList,
                port = other.endpointInfo.port,
            )
        return SyncInfo(appInfo, endpointInfo)
    }

    /**
     * Stamp and bound this endpoint's host list on first sight (no prior entry to merge
     * with). Keeps the wire-supplied addresses but applies recency + the capacity cap.
     */
    fun withStampedHostInfo(now: Long = DateUtils.nowEpochMilliseconds()): SyncInfo =
        copy(
            endpointInfo =
                endpointInfo.copy(
                    hostInfoList =
                        HostInfo.mergeRecent(
                            existing = emptyList(),
                            incoming = endpointInfo.hostInfoList,
                            now = now,
                        ),
                ),
        )

    override fun toString(): String = Json.encodeToString(this)
}
