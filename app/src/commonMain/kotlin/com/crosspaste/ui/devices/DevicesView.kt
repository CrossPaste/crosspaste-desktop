package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.ui.base.DialogButtonsView
import com.crosspaste.ui.base.DialogService
import com.crosspaste.ui.base.PasteDialogFactory
import org.koin.compose.koinInject

@Composable
fun MyDevicesView(syncRuntimeInfos: List<SyncRuntimeInfo>) {
    val dialogService = koinInject<DialogService>()
    val pasteDialogFactory = koinInject<PasteDialogFactory>()
    Box(contentAlignment = Alignment.TopCenter) {
        DevicesListView(syncRuntimeInfos) { syncRuntimeInfo ->
            dialogService.pushDialog(
                pasteDialogFactory.createDialog(
                    key = syncRuntimeInfo.deviceId,
                    title = "input_note_name",
                ) {
                    val syncRuntimeInfoDao = koinInject<SyncRuntimeInfoDao>()
                    var inputNoteName by remember { mutableStateOf(syncRuntimeInfo.noteName ?: "") }
                    var isError by remember { mutableStateOf(false) }

                    val cancelAction = {
                        dialogService.popDialog()
                    }

                    val confirmAction = {
                        if (inputNoteName == "") {
                            isError = true
                        } else {
                            syncRuntimeInfoDao.updateNoteName(
                                syncRuntimeInfo.appInstanceId,
                                inputNoteName,
                            )
                            dialogService.popDialog()
                        }
                    }

                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .wrapContentHeight(),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            DeviceNoteEditField(
                                cancelAction,
                                confirmAction,
                                inputNoteName,
                                isError,
                                { inputNoteName = it },
                                syncRuntimeInfo,
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
    syncRuntimeInfos: List<SyncRuntimeInfo>,
    onEdit: (SyncRuntimeInfo) -> Unit,
) {
    val deviceViewProvider = koinInject<DeviceViewProvider>()
    Column(modifier = Modifier.fillMaxWidth()) {
        for ((index, syncRuntimeInfo) in syncRuntimeInfos.withIndex()) {
            deviceViewProvider.DeviceConnectView(syncRuntimeInfo, true, onEdit)
            if (index != syncRuntimeInfos.size - 1) {
                HorizontalDivider()
            }
        }
    }
}
