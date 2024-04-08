package com.clipevery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier

@Composable
fun AboutView(currentPageViewContext: MutableState<PageViewContext>) {
    WindowDecoration(currentPageViewContext, "About")
    AboutContentView()
}

@Composable
fun AboutContentView() {
    Column(modifier = Modifier.fillMaxSize()) { }
}
