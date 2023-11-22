package com.clipevery

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        LocalQRCodeGenerator provides dependencies.qrCodeGenerator,
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
    val qrCodeGenerator = LocalQRCodeGenerator.current
    val coroutineScope = rememberCoroutineScope()
    var refreshJob: Job? by remember { mutableStateOf(null) }

    var qrImage by remember { mutableStateOf(qrCodeGenerator.generateQRCode(300, 300)) }

    DisposableEffect(Unit) {
        refreshJob = coroutineScope.launch {
            while (true) {
                qrImage = qrCodeGenerator.generateQRCode( 300, 300)
                delay(5000)
            }
        }
        onDispose {
            refreshJob?.cancel()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // This Box will be the 2:1 rectangle with the QR code and close button
        Box(
            modifier = Modifier
                .width(400.dp)
                .height(400.dp)
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = qrImage,
                contentDescription = "QR Code",
                modifier = Modifier.align(Alignment.Center)
            )
            Button(
                onClick = { refreshJob?.cancel() },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
    }}

@Composable
fun mainUI() {
    Text("mainUI")
}