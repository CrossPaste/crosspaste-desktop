package com.clipevery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ClipeveryApp(dependencies: Dependencies) {
    MaterialTheme {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ClipeveryCommon(dependencies)
        }
    }
}


@Composable
fun ClipeveryCommon(dependencies: Dependencies) {
    CompositionLocalProvider(
        LocalClipeveryServer provides dependencies.clipServer,
        LocalConfigManager provides dependencies.configManager,
        LocalFilePersist provides dependencies.filePersist,
        LocalSignalProtocol provides dependencies.signalProtocol,
    ) {
        ClipeveryWithProvidedDependencies()
    }
}

@Composable
fun ClipeveryWithProvidedDependencies() {
    val configManager = LocalConfigManager.current

    val config = remember { mutableStateOf(configManager.config) }

    if (!config.value.bindingState) {
        bindingQRCode()
    } else {
        mainUI()
    }
}


@Composable
fun bindingQRCode() {
    Text("bindingQRCode")
}

@Composable
fun mainUI() {
    Text("mainUI")
}