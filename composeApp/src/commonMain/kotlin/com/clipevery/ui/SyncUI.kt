package com.clipevery.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.clipevery.LocalKoinApplication
import com.clipevery.dao.SyncDao

@Composable
fun Syncs() {
    val current = LocalKoinApplication.current
    val syncDao = current.koin.get<SyncDao>()
    val syncInfos = syncDao.getAllSyncInfos()

    for ((index, syncInfo) in syncInfos.withIndex()) {
        SyncItem(syncInfo)
        if (index != syncInfos.size - 1) {
            Divider(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Preview
@Composable
fun test() {
    Column(modifier = Modifier.fillMaxSize()) {
        Syncs()
    }
}

