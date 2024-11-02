package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.app.AppSize
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.paste.item.PasteColor
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.ui.base.UISupport
import org.koin.compose.koinInject

@Composable
fun ColorPreviewView(
    pasteData: PasteData,
    onDoubleClick: () -> Unit,
) {
    pasteData.getPasteItem()?.let {
        val appSize = koinInject<AppSize>()
        val uiSupport = koinInject<UISupport>()
        val pasteMenuService = koinInject<DesktopPasteMenuService>()

        val pasteColor = it as PasteColor

        PasteSpecificPreviewContentView(
            pasteMainContent = {
                Row(
                    modifier =
                        Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        onDoubleClick()
                                    },
                                )
                            },
                ) {
                    PasteContextMenuView(
                        items =
                            pasteMenuService.fileMenuItemsProvider(
                                pasteData = pasteData,
                                pasteItem = it,
                            ),
                    ) {
                        Row(
                            modifier =
                                Modifier.pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            uiSupport.openColorPicker(pasteColor.color)
                                        },
                                    )
                                },
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(pasteColor.color).copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(80.dp)
                                            .shadow(
                                                elevation = 1.dp,
                                                shape = RoundedCornerShape(5.dp),
                                                spotColor = Color.Black.copy(alpha = 0.1f),
                                            )
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color(pasteColor.color)),
                                )
                            }

                            Column(
                                modifier =
                                    Modifier.fillMaxHeight()
                                        .wrapContentWidth()
                                        .padding(horizontal = 8.dp)
                                        .padding(bottom = 8.dp),
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                Text(
                                    text = pasteColor.toHexString(),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style =
                                        TextStyle(
                                            fontWeight = FontWeight.Light,
                                            fontSize = 10.sp,
                                        ),
                                )
                                Text(
                                    text = pasteColor.toRGBAString(),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style =
                                        TextStyle(
                                            fontWeight = FontWeight.Light,
                                            fontSize = 10.sp,
                                        ),
                                )
                            }
                        }
                    }
                }
            },
            pasteRightInfo = { toShow ->
                PasteMenuView(pasteData = pasteData, toShow = toShow)
            },
        )
    }
}
