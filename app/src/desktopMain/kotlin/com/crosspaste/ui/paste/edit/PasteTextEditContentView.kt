package com.crosspaste.ui.paste.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.app.AppWindowManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.plugin.type.TextTypePlugin
import com.crosspaste.ui.base.CustomTextField
import com.crosspaste.ui.base.PasteTooltipIconView
import com.crosspaste.ui.base.save
import com.crosspaste.ui.theme.AppUIFont.pasteTextStyle
import org.koin.compose.koinInject

@Composable
fun PasteTextEditContentView() {
    val appWindowManager = koinInject<AppWindowManager>()
    val screen by appWindowManager.screenContext.collectAsState()

    val pasteData = screen.context as PasteData
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteDao = koinInject<PasteDao>()
    val textUpdater = koinInject<TextTypePlugin>()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart,
    ) {
        pasteData.getPasteItem(TextPasteItem::class)?.let { pasteText ->
            var text by remember { mutableStateOf(pasteText.text) }

            CustomTextField(
                modifier = Modifier.fillMaxSize(),
                value = text,
                onValueChange = {
                    text = it
                },
                textStyle = pasteTextStyle,
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
                    if (text != pasteText.text && text.isNotEmpty()) {
                        textUpdater.updateText(pasteData, text, pasteText, pasteDao)
                    }
                }
            }
        }
    }
}
