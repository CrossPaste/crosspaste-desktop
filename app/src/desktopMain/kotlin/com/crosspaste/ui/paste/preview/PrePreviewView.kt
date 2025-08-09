package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteSyncProcessManager
import com.crosspaste.ui.base.PasteProgressbar
import com.crosspaste.ui.paste.PasteboardViewProvider
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
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
                .clip(tiny2XRoundedCornerShape),
    ) {
        process?.let {
            PasteProgressbar(it.value)
        }

        PasteSpecificPreviewContentView(
            pasteMainContent = {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(tiny2XRoundedCornerShape),
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
