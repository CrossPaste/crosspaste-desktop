package com.clipevery.ui.devices

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.clipevery.LocalKoinApplication
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.SyncRuntimeInfoDao
import com.clipevery.ui.PageViewContext
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

@Composable
fun DevicesView(currentPageViewContext: MutableState<PageViewContext>) {
    val current = LocalKoinApplication.current
    val syncRuntimeInfoDao = current.koin.get<SyncRuntimeInfoDao>()
    val syncRuntimeInfosFlow = syncRuntimeInfoDao.getAllSyncRuntimeInfos().asFlow()

    var rememberSyncRuntimeInfos by remember { mutableStateOf(emptyList<SyncRuntimeInfo>()) }

    LaunchedEffect(syncRuntimeInfosFlow) {
        rememberSyncRuntimeInfos = syncRuntimeInfosFlow.toList()
    }

    for ((index, syncRuntimeInfo) in rememberSyncRuntimeInfos.withIndex()) {
        DeviceItemView(syncRuntimeInfo, currentPageViewContext)
        if (index != rememberSyncRuntimeInfos.size - 1) {
            Divider(modifier = Modifier.fillMaxWidth())
        }
    }
}
