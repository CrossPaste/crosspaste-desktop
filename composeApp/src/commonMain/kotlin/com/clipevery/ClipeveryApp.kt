package com.clipevery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.clipevery.clip.AbstractClipboard
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalResourceApi::class)
@Composable
fun ClipeveryApp(clipboard: AbstractClipboard, copyText: MutableState<String>) {
    MaterialTheme {
        val pid: Long = ProcessHandle.current().pid()
        var showImage by remember { mutableStateOf(false) }
        var start  by remember { mutableStateOf(true) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = {
                showImage = !showImage
            }) {
                Text(copyText.value)
            }
            Button(onClick = {
                if (start) {
                    clipboard.stop()
                } else {
                    clipboard.start()
                }
                start = !start
            }) {
                Text(start.toString() + " " + pid)
            }
            AnimatedVisibility(showImage) {
                Image(
                    painterResource("compose-multiplatform.xml"),
                    null
                )
            }
        }
    }
}