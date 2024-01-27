package com.clipevery.ui.devices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.ui.PageViewContext

@Composable
fun DeviceItemView(syncRuntimeInfo: SyncRuntimeInfo, currentPageViewContext: MutableState<PageViewContext>) {
    DeviceBarView(syncRuntimeInfo, currentPageViewContext, true)
}
