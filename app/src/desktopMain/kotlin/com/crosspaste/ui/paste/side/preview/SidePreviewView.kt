package com.crosspaste.ui.paste.side.preview

import androidx.compose.runtime.Composable
import com.crosspaste.db.paste.PasteData
import com.crosspaste.db.paste.PasteState
import com.crosspaste.db.paste.PasteType
import com.crosspaste.ui.paste.preview.PrePreviewView

@Composable
fun SidePreviewView(pasteData: PasteData) {
    if (pasteData.pasteState == PasteState.LOADING) {
        PrePreviewView(pasteData)
    } else {
        when (pasteData.getType()) {
            PasteType.TEXT_TYPE -> TextSidePreviewView(pasteData)
            PasteType.COLOR_TYPE -> ColorSidePreviewView(pasteData)
            PasteType.URL_TYPE -> UrlSidePreviewView(pasteData)
            PasteType.HTML_TYPE -> HtmlSidePreviewView(pasteData)
            PasteType.RTF_TYPE -> RtfSidePreviewView(pasteData)
            PasteType.IMAGE_TYPE -> ImageSidePreviewView(pasteData)
            PasteType.FILE_TYPE -> FilesSidePreviewView(pasteData)
        }
    }
}
