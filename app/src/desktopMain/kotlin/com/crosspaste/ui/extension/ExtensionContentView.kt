package com.crosspaste.ui.extension

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.ocr
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.NavigationManager
import com.crosspaste.ui.OCR
import com.crosspaste.ui.base.PainterData
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.settings.SettingListItem
import com.crosspaste.ui.settings.SettingSectionCard
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

@Composable
fun ExtensionContentView() {
    val navigateManager = koinInject<NavigationManager>()
    val themeExt = LocalThemeExtState.current

    var isProxyExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        item {
            ProxySection(
                isExpanded = isProxyExpanded,
                onToggle = { isProxyExpanded = !isProxyExpanded },
            )
        }

        item {
            SectionHeader("extension", topPadding = medium)
        }

        item {
            SettingSectionCard {
                SettingListItem(
                    title = "ocr_settings",
                    subtitle = "language_module_settings",
                    painter =
                        PainterData(
                            painter = painterResource(Res.drawable.ocr),
                            iconBg = themeExt.violetIconColor.bgColor,
                            tint = themeExt.violetIconColor.color,
                        ),
                    onClick = {
                        navigateManager.navigate(OCR)
                    },
                )
            }
        }
    }
}
