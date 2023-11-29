package com.clipevery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import com.clipevery.PageType

@Composable
fun AboutUI(currentPage: MutableState<PageType>) {
    WindowDecoration(currentPage, "About")
    AboutContentUI()
}

@Composable
fun AboutContentUI() {
    Column(modifier = Modifier.fillMaxSize()) {  }
}