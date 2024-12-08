package com.crosspaste.ui.paste.preview

import androidx.compose.runtime.Composable
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteState
import com.crosspaste.realm.paste.PasteType

@Composable
fun PasteSpecificPreviewView(pasteData: PasteData) {
    if (pasteData.pasteState == PasteState.LOADING) {
        PrePreviewView(pasteData)
    } else {
        when (pasteData.getType()) {
            PasteType.TEXT_TYPE -> TextPreviewView(pasteData)
            PasteType.COLOR_TYPE -> ColorPreviewView(pasteData)
            PasteType.URL_TYPE -> UrlPreviewView(pasteData)
            PasteType.HTML_TYPE -> HtmlToImagePreviewView(pasteData)
            PasteType.RTF_TYPE -> RtfToImagePreviewView(pasteData)
            PasteType.IMAGE_TYPE -> ImagesPreviewView(pasteData)
            PasteType.FILE_TYPE -> FilesPreviewView(pasteData)
        }
    }
}
