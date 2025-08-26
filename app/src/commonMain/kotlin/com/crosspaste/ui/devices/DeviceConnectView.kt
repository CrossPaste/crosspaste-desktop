package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.base.DialogService
import com.crosspaste.ui.base.MenuItemView
import com.crosspaste.ui.base.PasteDialogFactory
import com.crosspaste.ui.base.PasteIconButton
import com.crosspaste.ui.base.moreVertical
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.getFontWidth
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import org.koin.compose.koinInject

@Composable
fun DeviceScope.DeviceConnectView() {
    HoverableDeviceBarView { background ->
        DeviceConnectStateView(background)
        DeviceMenuButton(background)
    }
}

@Composable
private fun DeviceScope.DeviceMenuButton(background: Color) {
    val copywriter = koinInject<GlobalCopywriter>()
    val dialogService = koinInject<DialogService>()
    val pasteDialogFactory = koinInject<PasteDialogFactory>()
    val syncManager = koinInject<SyncManager>()

    val density = LocalDensity.current

    var showPopup by remember { mutableStateOf(false) }

    val onBackground = MaterialTheme.colorScheme.contentColorFor(background)

    PasteIconButton(
        size = large2X,
        onClick = {
            showPopup = !showPopup
        },
        modifier =
            Modifier
                .background(Color.Transparent, CircleShape)
                .padding(horizontal = tiny),
    ) {
        Icon(
            painter = moreVertical(),
            contentDescription = "info",
            modifier = Modifier.size(large),
            tint = onBackground,
        )
    }

    if (showPopup) {
        Popup(
            alignment = Alignment.TopEnd,
            offset =
                IntOffset(
                    x = with(density) { ((-40).dp).roundToPx() },
                    y = with(density) { (large2X).roundToPx() },
                ),
            onDismissRequest = {
                if (showPopup) {
                    showPopup = false
                }
            },
            properties =
                PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
        ) {
            Box(
                modifier =
                    Modifier
                        .wrapContentSize()
                        .background(Color.Transparent)
                        .shadow(small),
            ) {
                val menuTexts =
                    listOf(
                        copywriter.getText("add_note"),
                        copywriter.getText("remove_device"),
                    )

                val maxWidth = getFontWidth(menuTexts)

                Column(
                    modifier =
                        Modifier
                            .width(maxWidth)
                            .wrapContentHeight()
                            .clip(tiny2XRoundedCornerShape)
                            .background(AppUIColors.menuBackground),
                ) {
                    MenuItemView(copywriter.getText("add_note")) {
                        onEdit(dialogService, pasteDialogFactory, syncManager)
                        showPopup = false
                    }
                    MenuItemView(copywriter.getText("remove_device")) {
                        val id = syncRuntimeInfo.appInstanceId
                        syncManager.removeSyncHandler(id)
                        showPopup = false
                    }
                }
            }
        }
    }
}
