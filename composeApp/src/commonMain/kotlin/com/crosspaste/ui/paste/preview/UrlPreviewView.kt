package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.onClick
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.realm.paste.PasteData

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UrlPreviewView(
    pasteData: PasteData,
    onDoubleClick: () -> Unit,
) {
    pasteData.getPasteItem()?.let {
        val pasteUrl = it as PasteUrl
        PasteSpecificPreviewContentView(
            pasteMainContent = {
                Row(
                    modifier =
                        Modifier.fillMaxSize()
                            .onClick(
                                onDoubleClick = onDoubleClick,
                                onClick = {},
                            ).padding(10.dp),
                ) {
                    Text(
                        modifier =
                            Modifier.fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        text = pasteUrl.url,
                        textDecoration = TextDecoration.Underline,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        style =
                            TextStyle(
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                            ),
                    )
                }
            },
            pasteRightInfo = { toShow ->
                PasteMenuView(pasteData = pasteData, toShow = toShow)
            },
        )
    }
}
