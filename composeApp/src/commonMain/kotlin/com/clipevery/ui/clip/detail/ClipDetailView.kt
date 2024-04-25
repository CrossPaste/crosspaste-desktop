package com.clipevery.ui.clip.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ClipDetailView(detailView: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().height(200.dp).padding(10.dp),
    ) {
        detailView()
    }
}
