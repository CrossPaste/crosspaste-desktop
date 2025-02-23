package com.crosspaste.ui.paste

import androidx.compose.runtime.Composable
import com.crosspaste.paste.PasteSingleProcess

interface PasteboardViewProvider {

    @Composable
    fun PasteShimmer(singleProcess: PasteSingleProcess?)
}
