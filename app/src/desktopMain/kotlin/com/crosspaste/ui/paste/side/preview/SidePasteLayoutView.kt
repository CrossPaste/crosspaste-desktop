package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUIColors

@Composable
fun PasteDataScope.SidePasteLayoutView(
    pasteBottomContent: @Composable () -> Unit,
    pasteContent: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SidePasteTitleView()
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(AppUIColors.pasteBackground),
            contentAlignment = Alignment.Center,
        ) {
            pasteContent()
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                pasteBottomContent()
            }
        }
    }
}
