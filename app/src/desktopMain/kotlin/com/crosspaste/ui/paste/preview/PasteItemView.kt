package com.crosspaste.ui.paste.preview

import androidx.compose.runtime.Composable
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteType
import com.crosspaste.ui.paste.PasteDataScope

@Composable
fun PasteDataScope.PasteItemView() {
    if (pasteData.pasteState == PasteState.LOADING) {
        PrePreviewView()
    } else {
        when (pasteData.getType()) {
            PasteType.TEXT_TYPE -> TextPreviewView()
            PasteType.COLOR_TYPE -> ColorPreviewView()
            PasteType.URL_TYPE -> UrlPreviewView()
            PasteType.HTML_TYPE -> HtmlPreviewView()
            PasteType.RTF_TYPE -> RtfPreviewView()
            PasteType.IMAGE_TYPE -> ImagesPreviewView()
            PasteType.FILE_TYPE -> FilesPreviewView()
        }
    }
}
