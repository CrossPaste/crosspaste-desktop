package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.PasteSyncProcessManager
import com.crosspaste.ui.base.PasteProgressbar
import com.crosspaste.ui.paste.PasteboardViewProvider
import org.koin.compose.koinInject

@Composable
fun PrePreviewView(pasteData: PasteData) {
    val pasteboardViewProvider = koinInject<PasteboardViewProvider>()
    val pasteSyncProcessManager = koinInject<PasteSyncProcessManager<Long>>()

    val processMap by pasteSyncProcessManager.processMap.collectAsState()

    val singleProcess = processMap[pasteData.id]

    val process = singleProcess?.process?.collectAsState()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(5.dp)),
    ) {
        process?.let {
            PasteProgressbar(it.value)
        }

        PasteSpecificPreviewContentView(
            backgroundColor = Color.Transparent,
            pasteMainContent = {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Transparent)
                            .clip(RoundedCornerShape(5.dp)),
                ) {
                    pasteboardViewProvider.PasteShimmer(singleProcess)
                }
            },
            pasteRightInfo = { toShow ->
                PasteMenuView(pasteData = pasteData, toShow = toShow)
            },
        )
    }
}
