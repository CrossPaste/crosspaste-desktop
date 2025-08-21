package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.PasteText
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUIFont.pasteTextStyle
import com.crosspaste.ui.theme.AppUIFont.previewAutoSize
import com.crosspaste.ui.theme.AppUISize.medium
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.TextSidePreviewView() {
    getPasteItem(PasteText::class).let { pasteText ->
        val copywriter = koinInject<GlobalCopywriter>()
        SidePasteLayoutView(
            pasteBottomContent = {
                BottomGradient(copywriter.getText("character_count", "${pasteText.text.length}"))
            },
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(medium)) {
                BasicText(
                    modifier = Modifier.fillMaxSize(),
                    text = AnnotatedString(pasteText.previewText()),
                    maxLines = 9,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    style = pasteTextStyle,
                    autoSize = previewAutoSize,
                )
            }
        }
    }
}
