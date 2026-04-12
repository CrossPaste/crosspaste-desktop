package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Download
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import org.koin.compose.koinInject

@Composable
fun DesktopPromoteGuide() {
    val copyWriter = koinInject<GlobalCopywriter>()
    val uiSupport = koinInject<UISupport>()

    Spacer(modifier = Modifier.height(medium))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(medium))
    Text(
        text = copyWriter.getText("get_other_platform_crosspaste"),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(small2X))
    StoreBadge(
        icon = {
            Icon(
                imageVector = MaterialSymbols.Rounded.Download,
                contentDescription = null,
                modifier = Modifier.size(medium),
                tint = MaterialTheme.colorScheme.surface,
            )
        },
        topText = copyWriter.getText("download"),
        bottomText = "CrossPaste",
        onClick = { uiSupport.openCrossPasteWebInBrowser("download") },
    )
}
