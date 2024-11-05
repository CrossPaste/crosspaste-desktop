package com.crosspaste.ui.paste.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.crosspaste.app.AppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.plugin.type.TextTypePlugin
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteRealm
import com.crosspaste.ui.base.CustomTextField
import com.crosspaste.ui.base.PasteTooltipIconView
import com.crosspaste.ui.base.save
import com.crosspaste.utils.getCodecsUtils
import io.ktor.utils.io.core.*
import org.koin.compose.koinInject

@Composable
fun PasteTextEditContentView() {
    val appWindowManager = koinInject<AppWindowManager>()
    val screen by appWindowManager.screenContext.collectAsState()

    val pasteData = screen.context as PasteData
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteRealm = koinInject<PasteRealm>()
    val textUpdater = koinInject<TextTypePlugin>()
    val codecsUtils = getCodecsUtils()

    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
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
                textStyle =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onBackground,
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
                    if (text != pasteText.text && text.isNotEmpty()) {
                        val textBytes = text.toByteArray()
                        val hash = codecsUtils.hash(textBytes)
                        pasteRealm.update { realm ->
                            textUpdater.updateText(text, textBytes.size.toLong(), hash, pasteText, realm)
                        }
                    }
                }
            }
        }
    }
}
