package com.clipevery.ui.devices

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.clipevery.LocalKoinApplication
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.SyncRuntimeInfoDao
import com.clipevery.net.CheckAction
import com.clipevery.net.DeviceRefresher
import com.clipevery.ui.PageViewContext
import com.clipevery.ui.base.ClipIconButton
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults

@Composable
fun DevicesView(currentPageViewContext: MutableState<PageViewContext>) {
    Box(contentAlignment = Alignment.TopCenter) {
        DevicesListView(currentPageViewContext)
        DevicesRefreshView()
    }
}

@Composable
fun DevicesListView(currentPageViewContext: MutableState<PageViewContext>) {
    val current = LocalKoinApplication.current
    val syncRuntimeInfoDao = current.koin.get<SyncRuntimeInfoDao>()
    val syncRuntimeInfos = syncRuntimeInfoDao.getAllSyncRuntimeInfos()
    var rememberSyncRuntimeInfos by remember { mutableStateOf(syncRuntimeInfos) }

    LaunchedEffect(Unit) {
        val syncRuntimeInfosFlow = syncRuntimeInfos.asFlow()
        syncRuntimeInfosFlow.collect { changes: ResultsChange<SyncRuntimeInfo> ->
            when (changes) {
                is UpdatedResults -> {
                    changes.insertions // indexes of inserted objects
                    changes.insertionRanges // ranges of inserted objects
                    changes.changes // indexes of modified objects
                    changes.changeRanges // ranges of modified objects
                    changes.deletions // indexes of deleted objects
                    changes.deletionRanges // ranges of deleted objects
                    changes.list // the full collection of objects

                    rememberSyncRuntimeInfos = syncRuntimeInfoDao.getAllSyncRuntimeInfos()
                }
                else -> {
                    // types other than UpdatedResults are not changes -- ignore them
                }
            }
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        for ((index, syncRuntimeInfo) in rememberSyncRuntimeInfos.withIndex()) {
            DeviceItemView(syncRuntimeInfo, currentPageViewContext)
            if (index != rememberSyncRuntimeInfos.size - 1) {
                Divider(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun DevicesRefreshView() {
    val current = LocalKoinApplication.current
    val deviceRefresher = current.koin.get<DeviceRefresher>()

    val isRefreshing by deviceRefresher.isRefreshing

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
                        deviceRefresher.refresh(CheckAction.CheckAll)
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

