package com.clipevery.ui.search

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.ClipSearchService
import com.clipevery.ui.clip.preview.ClipPreviewItemView
import com.clipevery.ui.clip.preview.ClipSpecificPreviewView

@Composable
fun DetialClipDataView() {
    val current = LocalKoinApplication.current
    val clipSearchService = current.koin.get<ClipSearchService>()

    clipSearchService.currentClipData.value?.let {
        ClipPreviewItemView(it) {
            ClipSpecificPreviewView(this)
        }
    } ?: run {
        Spacer(modifier = Modifier.fillMaxSize())
    }
}
