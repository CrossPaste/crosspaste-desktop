package com.crosspaste.ui.paste

import androidx.compose.runtime.Composable
import com.crosspaste.paste.PasteSingleProcess
import com.crosspaste.ui.paste.preview.PasteShimmerContentView

class DesktopPasteboardViewProvider : PasteboardViewProvider {
    @Composable
    override fun PasteShimmer(singleProcess: PasteSingleProcess?) {
        PasteShimmerContentView(singleProcess)
    }
}
