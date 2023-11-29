package com.clipevery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import com.clipevery.PageType

@Composable
fun SettingsUI(currentPage: MutableState<PageType>) {
    WindowDecoration(currentPage, "Settings")
    SettingsContentUI()
}

@Composable
fun SettingsContentUI() {
    Column(modifier = Modifier.fillMaxSize()) {  }
}