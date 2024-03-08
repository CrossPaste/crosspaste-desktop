package com.clipevery.ui.devices

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.SyncRuntimeInfoDao
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.net.SyncRefresher
import com.clipevery.sync.SyncManager
import com.clipevery.ui.PageViewContext
import com.clipevery.ui.base.ClipIconButton

@Composable
fun DevicesView(currentPageViewContext: MutableState<PageViewContext>) {
    var editSyncRuntimeInfo by remember { mutableStateOf<SyncRuntimeInfo?>(null) }
    Box(contentAlignment = Alignment.TopCenter) {
        DevicesListView(currentPageViewContext) {
            editSyncRuntimeInfo = it
        }
        DevicesRefreshView()
        editSyncRuntimeInfo?.let { syncRuntimeInfo ->
            DeviceNoteNameEditView(syncRuntimeInfo) {
                editSyncRuntimeInfo = null
            }
        }
    }
}

@Composable
fun DeviceNoteNameEditView(syncRuntimeInfo: SyncRuntimeInfo, onComplete: () -> Unit) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val syncRuntimeInfoDao = current.koin.get<SyncRuntimeInfoDao>()
    var inputNoteName by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().graphicsLayer {
        alpha = 0.8f
    }.background(Color.White.copy(alpha = 0.2f))
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent()
                }
            }
        },
        contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.shadow(5.dp, RoundedCornerShape(5.dp))) {
            Column(modifier = Modifier.width(260.dp)
                .wrapContentHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(color = MaterialTheme.colors.background)
                .padding(horizontal = 10.dp)
                .padding(top = 10.dp, bottom = 6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.Start) {
                    Text(text = copywriter.getText("Input_Note_Name"),
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.onBackground)
                }
                TextField(
                    modifier = Modifier.wrapContentSize(),
                    value = inputNoteName,
                    onValueChange = { inputNoteName = it },
                    placeholder = { Text(
                        modifier = Modifier.wrapContentSize(),
                        text = syncRuntimeInfo.noteName ?: syncRuntimeInfo.deviceName,
                        style = TextStyle(
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    },
                    isError = isError,
                    singleLine = true,
                    colors = TextFieldDefaults.textFieldColors(
                        textColor = MaterialTheme.colors.onBackground,
                        disabledTextColor = Color.Transparent,
                        backgroundColor = Color.Transparent,
                        cursorColor = MaterialTheme.colors.primary,
                        errorCursorColor = Color.Red,
                        focusedIndicatorColor = MaterialTheme.colors.primary,
                        unfocusedIndicatorColor = MaterialTheme.colors.secondaryVariant,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    textStyle =  TextStyle(
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 15.sp,
                        lineHeight = 5.sp
                    )
                )
                Divider()
                Row {
                    Button(modifier = Modifier.width(115.dp),
                        onClick = {
                            onComplete()
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.secondaryVariant,
                            contentColor = MaterialTheme.colors.onBackground
                        )
                    ) {
                        Text(text = copywriter.getText("Cancel"))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(modifier = Modifier.width(115.dp),
                        onClick = {
                            if (inputNoteName == "") {
                                isError = true
                            } else {
                                syncRuntimeInfoDao.updateNoteName(syncRuntimeInfo, inputNoteName)
                                onComplete()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary,
                            contentColor = MaterialTheme.colors.onBackground
                        )
                    ) {
                        Text(text = copywriter.getText("Confirm"))
                    }
                }
            }
        }
    }
}

@Composable
fun DevicesListView(currentPageViewContext: MutableState<PageViewContext>, onEdit: (SyncRuntimeInfo) -> Unit) {
    val current = LocalKoinApplication.current
    val syncManager = current.koin.get<SyncManager>()
    val rememberSyncRuntimeInfos = remember { syncManager.realTimeSyncRuntimeInfos }

    Column(modifier = Modifier.fillMaxWidth()) {
        for ((index, syncRuntimeInfo) in rememberSyncRuntimeInfos.withIndex()) {
            DeviceBarView(syncRuntimeInfo, currentPageViewContext, true, onEdit)
            if (index != rememberSyncRuntimeInfos.size - 1) {
                Divider(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun DevicesRefreshView() {
    val current = LocalKoinApplication.current
    val syncRefresher = current.koin.get<SyncRefresher>()

    val isRefreshing by syncRefresher.isRefreshing

    val rotationDegrees = remember { Animatable(0f) }

    LaunchedEffect(isRefreshing) {
        while (isRefreshing) {
            rotationDegrees.animateTo(
                targetValue = rotationDegrees.value + 360,
                animationSpec = tween(
                    durationMillis = 1000,
                    easing = LinearEasing
                )
            )
        }
        rotationDegrees.snapTo(0f)
    }

    Column(modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom) {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End) {
            ClipIconButton(
                radius = 18.dp,
                onClick = {
                    if (!isRefreshing) {
                        syncRefresher.refresh(true)
                    }
                },
                modifier = Modifier
                    .padding(30.dp)
                    .background(
                        MaterialTheme.colors.primary,
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "info",
                    modifier = Modifier.padding(3.dp)
                        .size(25.dp)
                        .graphicsLayer(rotationZ = rotationDegrees.value ),
                    tint = Color.White
                )
            }
        }
    }
}

