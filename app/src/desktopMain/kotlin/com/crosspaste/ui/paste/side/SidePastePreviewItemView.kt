package com.crosspaste.ui.paste.side

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.ui.base.HighlightedCard
import com.crosspaste.ui.model.FocusedElement
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.paste.PasteContextMenuView
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.small3XRoundedCornerShape
import org.koin.compose.koinInject

@Composable
fun SidePastePreviewItemView(
    pasteData: PasteData,
    selected: Boolean,
    onPress: () -> Unit,
    onDoubleTap: () -> Unit,
    pasteContent: @Composable PasteData.() -> Unit,
) {
    val appSize = koinInject<DesktopAppSize>()
    val pasteMenuService = koinInject<DesktopPasteMenuService>()
    val pasteSelectionViewModel = koinInject<PasteSelectionViewModel>()
    val focusedElement by pasteSelectionViewModel.focusedElement.collectAsState()
    Row(
        modifier =
            Modifier.size(appSize.sidePasteSize)
                .clip(small3XRoundedCornerShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onPress()
                        },
                        onDoubleTap = {
                            onDoubleTap()
                        },
                    )
                }
                .border(
                    3.dp,
                    if (selected) {
                        if (focusedElement == FocusedElement.PASTE_LIST) {
                            AppUIColors.importantColor
                        } else {
                            AppUIColors.lightBorderColor
                        }
                    } else {
                        Color.Transparent
                    },
                    small3XRoundedCornerShape,
                ),
    ) {
        HighlightedCard(
            modifier =
                Modifier.fillMaxSize(),
            shape = small3XRoundedCornerShape,
            containerColor = AppUIColors.pasteBackground,
        ) {
            PasteContextMenuView(
                items = pasteMenuService.pasteMenuItemsProvider(pasteData),
            ) {
                pasteData.pasteContent()
            }
        }
    }
}
