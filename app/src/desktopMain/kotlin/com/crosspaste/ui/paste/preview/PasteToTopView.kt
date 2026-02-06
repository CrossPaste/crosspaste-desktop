package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Arrow_upward_alt
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.PasteIconButton
import com.crosspaste.ui.base.PasteTooltipAreaView
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun PasteToTopView(toTopAction: () -> Unit) {
    val copywriter = koinInject<GlobalCopywriter>()
    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(end = xxLarge, bottom = xxLarge),
        verticalAlignment = Alignment.Bottom,
    ) {
        var transparency by remember { mutableStateOf(0.5f) }

        Spacer(modifier = Modifier.weight(1f))
        PasteTooltipAreaView(
            text = copywriter.getText("scroll_to_top"),
            delayMillis = 1000,
        ) {
            PasteIconButton(
                size = xxxLarge,
                onClick = {
                    toTopAction()
                },
                modifier =
                    Modifier
                        .background(AppUIColors.appBackground.copy(alpha = transparency), CircleShape)
                        .onPointerEvent(
                            eventType = PointerEventType.Enter,
                            onEvent = {
                                transparency = 1.0f
                            },
                        ).onPointerEvent(
                            eventType = PointerEventType.Exit,
                            onEvent = {
                                transparency = 0.5f
                            },
                        ),
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Arrow_upward_alt,
                    contentDescription = "To Top",
                    modifier = Modifier.size(xLarge),
                    tint = AppUIColors.importantColor.copy(alpha = transparency),
                )
            }
        }
    }
}
