package com.clipevery.ui

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.clipevery.LocalKoinApplication
import com.clipevery.path.PathProvider
import kotlin.io.path.absolutePathString

@Composable
fun DebugView() {
    val current = LocalKoinApplication.current
    val pathProvider = current.koin.get<PathProvider>()
    Text(
        text = pathProvider.clipAppPath.absolutePathString()
    )
}
