package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.onClick
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.paste.item.PasteText

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TextPreviewView(
    pasteData: PasteData,
    onDoubleClick: () -> Unit,
) {
    pasteData.getPasteItem()?.let {
        PasteSpecificPreviewContentView(
            pasteMainContent = {
                Row(
                    modifier =
                        Modifier.fillMaxSize().onClick(
                            onDoubleClick = onDoubleClick,
                            onClick = {},
                        ).padding(10.dp),
                ) {
                    Text(
                        modifier = Modifier.fillMaxSize(),
                        text = (it as PasteText).text,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 4,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        style =
                            TextStyle(
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colors.onBackground,
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
