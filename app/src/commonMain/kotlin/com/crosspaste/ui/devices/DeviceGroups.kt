package com.crosspaste.ui.devices

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.scan

/**
 * Online / offline split for the device list.
 *
 * [lastOnline] carries each device's previously resolved group so a CONNECTING
 * transition inherits where the device was, instead of letting it jump between
 * lists on every re-probe.
 */
data class DeviceGroups(
    val online: List<SyncRuntimeInfo> = emptyList(),
    val offline: List<SyncRuntimeInfo> = emptyList(),
    val lastOnline: Map<String, Boolean> = emptyMap(),
) {
    val hasDevices: Boolean
        get() = online.isNotEmpty() || offline.isNotEmpty()

    /**
     * Sticky grouping: a flaky device that polling re-probes flips
     * DISCONNECTED -> CONNECTING -> DISCONNECTED every cycle. Bucketing on the raw
     * connectState would make it jump between the online and offline lists (and
     * flip the offline count) on each flip. CONNECTING is a transition, so its
     * group is decided by where the device was last resolved to: a previously
     * online device stays online while re-probing, a previously offline device
     * stays offline. Only terminal states (CONNECTED / attention / DISCONNECTED)
     * actually move a device. The raw connectState is left untouched so the device
     * row can still show the live "reconnecting" indicator.
     */
    fun next(list: List<SyncRuntimeInfo>): DeviceGroups {
        val nextLastOnline = HashMap<String, Boolean>(list.size)
        val (online, offline) =
            list.partition { info ->
                val isOnline =
                    when (info.connectState) {
                        SyncState.DISCONNECTED -> false
                        SyncState.CONNECTING -> lastOnline[info.appInstanceId] ?: false
                        else -> true
                    }
                nextLastOnline[info.appInstanceId] = isOnline
                isOnline
            }
        return DeviceGroups(online, offline, nextLastOnline)
    }
}

/** Groups a stream of device lists into sticky online / offline buckets. */
fun Flow<List<SyncRuntimeInfo>>.scanDeviceGroups(): Flow<DeviceGroups> =
    scan(DeviceGroups()) { previous, list -> previous.next(list) }
