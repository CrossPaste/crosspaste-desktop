package com.crosspaste.ui.devices

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.dao.sync.SyncRuntimeInfo
import com.crosspaste.dao.sync.SyncRuntimeInfoDao
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.PageViewContext
import com.crosspaste.ui.base.DialogButtonsView
import com.crosspaste.ui.base.DialogService
import com.crosspaste.ui.base.ExpandView
import com.crosspaste.ui.base.PasteDialog
import java.awt.event.KeyEvent

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
            dialogService.pushDialog(
                PasteDialog(
                    key = info.deviceId,
                    title = "do_you_trust_this_device?",
                    width = 320.dp,
                ) {
                    DeviceVerifyView(info)
                },
            )
        }
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colors.surface.copy(0.64f)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (syncManager.realTimeSyncRuntimeInfos.isNotEmpty()) {
                ExpandView("my_devices", defaultExpand = true) {
                    MyDevicesView(currentPageViewContext)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            ExpandView("nearby_devices", defaultExpand = true) {
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
            dialogService.pushDialog(
                PasteDialog(
                    key = syncRuntimeInfo.deviceId,
                    title = "input_note_name",
                    width = 260.dp,
                ) {
                    val syncRuntimeInfoDao = current.koin.get<SyncRuntimeInfoDao>()
                    var inputNoteName by remember { mutableStateOf("") }
                    var isError by remember { mutableStateOf(false) }

                    val focusRequester = remember { FocusRequester() }

                    val cancelAction = {
                        dialogService.popDialog()
                    }

                    val confirmAction = {
                        if (inputNoteName == "") {
                            isError = true
                        } else {
                            syncRuntimeInfoDao.update(syncRuntimeInfo) {
                                this.noteName = inputNoteName
                            }
                            dialogService.popDialog()
                        }
                    }

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }

                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .wrapContentHeight(),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                            TextField(
                                modifier =
                                    Modifier.wrapContentSize()
                                        .focusRequester(focusRequester)
                                        .onKeyEvent {
                                            when (it.key) {
                                                Key(KeyEvent.VK_ENTER) -> {
                                                    confirmAction()
                                                    true
                                                }
                                                Key(KeyEvent.VK_ESCAPE) -> {
                                                    cancelAction()
                                                    true
                                                }
                                                else -> {
                                                    false
                                                }
                                            }
                                        },
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
                                cancelAction = cancelAction,
                                confirmAction = confirmAction,
                            )
                        }
                    }
                },
            )
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
