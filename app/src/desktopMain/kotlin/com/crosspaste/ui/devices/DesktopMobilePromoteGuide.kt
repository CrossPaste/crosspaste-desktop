package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.composables.icons.fontawesome.FontAwesome
import com.composables.icons.fontawesome.brands.Apple
import com.composables.icons.fontawesome.brands.GooglePlay
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Download
import com.crosspaste.app.MobilePromoteService
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import org.koin.compose.koinInject

@Composable
fun DesktopMobilePromoteGuide() {
    val copyWriter = koinInject<GlobalCopywriter>()
    val mobilePromoteService = koinInject<MobilePromoteService>()
    val uiSupport = koinInject<UISupport>()

    val promoteConfig by mobilePromoteService.config.collectAsState()
    val ios = promoteConfig.promote.ios
    val android = promoteConfig.promote.android
    val domestic = promoteConfig.promote.domestic
    val isChinese = copyWriter.getAbridge().startsWith("zh")
    val showDomestic = domestic.enabled && isChinese
    val showPromote = ios.enabled || android.enabled || showDomestic

    if (showPromote) {
        Spacer(modifier = Modifier.height(medium))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(medium))
        Text(
            text = copyWriter.getText("get_mobile_app"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(small2X))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(small2X, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(small2X),
        ) {
            if (ios.enabled) {
                StoreBadge(
                    icon = {
                        Icon(
                            imageVector = FontAwesome.Brands.Apple,
                            contentDescription = "App Store",
                            modifier = Modifier.size(medium),
                            tint = MaterialTheme.colorScheme.surface,
                        )
                    },
                    topText = "Download on the",
                    bottomText = "App Store",
                    onClick = { uiSupport.openUrlInBrowser(ios.url) },
                )
            }
            if (android.enabled) {
                StoreBadge(
                    icon = {
                        Icon(
                            imageVector = FontAwesome.Brands.GooglePlay,
                            contentDescription = "Google Play",
                            modifier = Modifier.size(medium),
                            tint = MaterialTheme.colorScheme.surface,
                        )
                    },
                    topText = "GET IT ON",
                    bottomText = "Google Play",
                    onClick = { uiSupport.openUrlInBrowser(android.url) },
                )
            }
            if (showDomestic) {
                StoreBadge(
                    icon = {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(medium),
                            tint = MaterialTheme.colorScheme.surface,
                        )
                    },
                    topText = copyWriter.getText("get_domestic_app"),
                    bottomText = "CrossPaste",
                    onClick = { uiSupport.openUrlInBrowser(domestic.url) },
                )
            }
        }
    }
}
