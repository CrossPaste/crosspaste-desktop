package com.crosspaste.ui.paste.side.preview

import androidx.compose.runtime.Composable
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteType
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.paste.preview.PrePreviewView

@Composable
fun PasteDataScope.SidePreviewView() {
    if (pasteData.pasteState == PasteState.LOADING) {
        PrePreviewView()
    } else {
        when (pasteData.getType()) {
            PasteType.TEXT_TYPE -> TextSidePreviewView()
            PasteType.COLOR_TYPE -> ColorSidePreviewView()
            PasteType.URL_TYPE -> UrlSidePreviewView()
            PasteType.HTML_TYPE -> HtmlSidePreviewView()
            PasteType.RTF_TYPE -> RtfSidePreviewView()
            PasteType.IMAGE_TYPE -> ImageSidePreviewView()
            PasteType.FILE_TYPE -> FilesSidePreviewView()
        }
    }
}
