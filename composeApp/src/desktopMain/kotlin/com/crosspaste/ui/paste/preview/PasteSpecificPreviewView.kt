package com.crosspaste.ui.paste.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteState
import com.crosspaste.realm.paste.PasteType
import io.realm.kotlin.ext.asFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun PasteSpecificPreviewView(pasteData: PasteData) {
    val pasteState by remember(pasteData) {
        pasteData.asFlow().map { it.obj?.pasteState }.distinctUntilChanged()
    }.collectAsState(initial = pasteData.pasteState)

    pasteState?.let {
        if (pasteState == PasteState.LOADING) {
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
}
