package com.clipevery.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.SyncRuntimeInfoDao
import com.clipevery.sync.SyncManager
import com.clipevery.ui.PageViewContext
import com.clipevery.ui.base.ClipDialog
import com.clipevery.ui.base.DialogButtonsView
import com.clipevery.ui.base.DialogService
import com.clipevery.ui.base.ExpandView

@Composable
fun DevicesView(currentPageViewContext: MutableState<PageViewContext>) {
    val current = LocalKoinApplication.current
    val syncManager = current.koin.get<SyncManager>()
    val dialogService = current.koin.get<DialogService>()

    LaunchedEffect(Unit) {
        syncManager.resolveSyncs()
    }

    LaunchedEffect(syncManager.waitToVerifySyncRuntimeInfo?.deviceId) {
        syncManager.waitToVerifySyncRuntimeInfo?.let { info ->
            if (dialogService.dialog == null ||
                dialogService.dialog?.key != info.deviceId
            ) {
                dialogService.dialog =
                    ClipDialog(
                        key = info.deviceId,
                        title = "Do_you_trust_this_device?",
                        width = 320.dp,
                    ) {
                        DeviceVerifyView(info)
                    }
            }
        }
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(color = MaterialTheme.colors.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (syncManager.realTimeSyncRuntimeInfos.isNotEmpty()) {
                ExpandView("MyDevices", defaultExpand = true) {
                    MyDevicesView(currentPageViewContext)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            ExpandView("NearbyDevices", defaultExpand = true) {
                NearbyDevicesView()
            }
        }
    }
}

@Composable
fun MyDevicesView(currentPageViewContext: MutableState<PageViewContext>) {
    val current = LocalKoinApplication.current
    val dialogService = current.koin.get<DialogService>()
    Box(contentAlignment = Alignment.TopCenter) {
        DevicesListView(currentPageViewContext) { syncRuntimeInfo ->
            dialogService.dialog =
                ClipDialog(
                    key = syncRuntimeInfo.deviceId,
                    title = "Input_Note_Name",
                    width = 260.dp,
                ) {
                    val syncRuntimeInfoDao = current.koin.get<SyncRuntimeInfoDao>()
                    var inputNoteName by remember { mutableStateOf("") }
                    var isError by remember { mutableStateOf(false) }

                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .wrapContentHeight(),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                            TextField(
                                modifier = Modifier.wrapContentSize(),
                                value = inputNoteName,
                                onValueChange = { inputNoteName = it },
                                placeholder = {
                                    Text(
                                        modifier = Modifier.wrapContentSize(),
                                        text = syncRuntimeInfo.noteName ?: syncRuntimeInfo.deviceName,
                                        style =
                                            TextStyle(
                                                fontWeight = FontWeight.Light,
                                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                                                fontSize = 15.sp,
                                            ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                isError = isError,
                                singleLine = true,
                                colors =
                                    TextFieldDefaults.textFieldColors(
                                        textColor = MaterialTheme.colors.onBackground,
                                        disabledTextColor = Color.Transparent,
                                        backgroundColor = Color.Transparent,
                                        cursorColor = MaterialTheme.colors.primary,
                                        errorCursorColor = Color.Red,
                                        focusedIndicatorColor = MaterialTheme.colors.primary,
                                        unfocusedIndicatorColor = MaterialTheme.colors.secondaryVariant,
                                        disabledIndicatorColor = Color.Transparent,
                                    ),
                                textStyle =
                                    TextStyle(
                                        fontWeight = FontWeight.Light,
                                        color = MaterialTheme.colors.onBackground,
                                        fontSize = 15.sp,
                                        lineHeight = 5.sp,
                                    ),
                            )
                        }

                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .wrapContentHeight(),
                        ) {
                            DialogButtonsView(
                                height = 50.dp,
                                cancelAction = {
                                    dialogService.dialog = null
                                },
                                confirmAction = {
                                    if (inputNoteName == "") {
                                        isError = true
                                    } else {
                                        syncRuntimeInfoDao.update(syncRuntimeInfo) {
                                            this.noteName = inputNoteName
                                        }
                                        dialogService.dialog = null
                                    }
                                },
                            )
                        }
                    }
                }
        }
    }
}

@Composable
fun DevicesListView(
    currentPageViewContext: MutableState<PageViewContext>,
    onEdit: (SyncRuntimeInfo) -> Unit,
) {
    val current = LocalKoinApplication.current
    val syncManager = current.koin.get<SyncManager>()
    val rememberSyncRuntimeInfos = remember { syncManager.realTimeSyncRuntimeInfos }

    Column(modifier = Modifier.fillMaxWidth()) {
        for ((index, syncRuntimeInfo) in rememberSyncRuntimeInfos.withIndex()) {
            DeviceConnectView(syncRuntimeInfo, currentPageViewContext, true, onEdit)
            if (index != rememberSyncRuntimeInfos.size - 1) {
                Divider(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
