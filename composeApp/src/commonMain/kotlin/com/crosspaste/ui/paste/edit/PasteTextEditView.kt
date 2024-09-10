package com.crosspaste.ui.paste.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.PasteText
import com.crosspaste.paste.plugin.type.TextUpdater
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteRealm
import com.crosspaste.ui.PageViewContext
import com.crosspaste.ui.WindowDecoration
import com.crosspaste.ui.base.PasteTooltipIconView
import com.crosspaste.ui.base.save
import com.crosspaste.utils.getCodecsUtils

@Composable
fun PasteTextEditView(currentPageViewContext: MutableState<PageViewContext>) {
    WindowDecoration(currentPageViewContext, "text_edit")
    PasteTextEditContentView(currentPageViewContext.value.context as PasteData)
}

@Composable
fun PasteTextEditContentView(pasteData: PasteData) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val pasteRealm = current.koin.get<PasteRealm>()
    val textUpdater = current.koin.get<TextUpdater>()
    val codecsUtils = getCodecsUtils()

    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        pasteData.getPasteItem()?.let {
            var text by remember { mutableStateOf((it as PasteText).text) }

            TextField(
                modifier = Modifier.fillMaxSize(),
                value = text,
                onValueChange = {
                    text = it
                },
                textStyle =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 14.sp,
                    ),
            )

            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd),
            ) {
                PasteTooltipIconView(
                    painter = save(),
                    text = copywriter.getText("save"),
                    contentDescription = "save text",
                ) {
                    if (text != (it as PasteText).text && text.isNotEmpty()) {
                        val textBytes = text.toByteArray()
                        val hash = codecsUtils.hash(textBytes)
                        pasteRealm.update { realm ->
                            textUpdater.updateText(text, textBytes.size.toLong(), hash, it, realm)
                        }
                    }
                }
            }
        }
    }
}
