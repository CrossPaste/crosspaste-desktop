package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.crosspaste.paste.PasteSyncProcessManager
import com.crosspaste.ui.base.PasteProgressbar
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.mediumRoundedCornerShape
import com.valentinilk.shimmer.shimmer
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.PreSidePreviewView() {
    val pasteSyncProcessManager = koinInject<PasteSyncProcessManager<Long>>()

    val processMap by pasteSyncProcessManager.processMap.collectAsState()

    val singleProcess = processMap[pasteData.id]

    val process = singleProcess?.process?.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AppUIColors.pasteBackground)
                .shimmer(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(huge)
                    .background(AppUIColors.pasteShimmerColor),
        ) {
            process?.let {
                PasteProgressbar(it.value)
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(medium)
                    .clip(mediumRoundedCornerShape)
                    .background(AppUIColors.pasteShimmerColor),
        ) {
        }
    }
}
