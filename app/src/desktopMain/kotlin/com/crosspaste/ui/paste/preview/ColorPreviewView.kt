package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.crosspaste.app.AppSize
import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont
import com.crosspaste.ui.theme.AppUISize.giant
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny3X
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.ColorPreviewView() {
    getPasteItem(ColorPasteItem::class).let { pasteColor ->
        SimplePreviewContentView(pasteData) {
            val appSize = koinInject<AppSize>()
            val uiSupport = koinInject<UISupport>()
            Row(
                modifier =
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                uiSupport.openColorPicker(pasteData)
                            },
                        )
                    },
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(appSize.mainPasteSize.height)
                            .clip(tiny2XRoundedCornerShape)
                            .background(Color(pasteColor.color).copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(giant)
                                .clip(tiny2XRoundedCornerShape)
                                .background(Color(pasteColor.color)),
                    )
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .wrapContentWidth()
                            .padding(horizontal = tiny)
                            .padding(bottom = tiny),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = pasteColor.toHexString(),
                        color =
                            MaterialTheme.colorScheme.contentColorFor(
                                AppUIColors.pasteBackground,
                            ),
                        style = AppUIFont.propertyTextStyle,
                    )

                    Spacer(modifier = Modifier.height(tiny3X))

                    Text(
                        text = pasteColor.toRGBAString(),
                        color =
                            MaterialTheme.colorScheme.contentColorFor(
                                AppUIColors.pasteBackground,
                            ),
                        style = AppUIFont.propertyTextStyle,
                    )
                }
            }
        }
    }
}
