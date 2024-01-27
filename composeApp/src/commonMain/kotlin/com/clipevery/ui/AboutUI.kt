package com.clipevery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier

@Composable
fun AboutUI(currentPageViewContext: MutableState<PageViewContext>) {
    WindowDecoration(currentPageViewContext, "About")
    AboutContentUI()
}

@Composable
fun AboutContentUI() {
    Column(modifier = Modifier.fillMaxSize()) {  }
}