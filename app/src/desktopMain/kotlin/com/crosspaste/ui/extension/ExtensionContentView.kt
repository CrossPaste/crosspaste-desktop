package com.crosspaste.ui.extension

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.ocr
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.NavigationManager
import com.crosspaste.ui.OCR
import com.crosspaste.ui.Route
import com.crosspaste.ui.base.ExpandViewProvider
import com.crosspaste.ui.base.arrowRight
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.xLarge
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

@Composable
fun ExtensionContentView() {
    val expandViewProvider = koinInject<ExpandViewProvider>()
    val navigateManager = koinInject<NavigationManager>()
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AppUIColors.appBackground)
                .padding(horizontal = medium)
                .padding(bottom = medium),
    ) {
        Column {
            expandViewProvider.ExpandView(
                barContent = {
                    expandViewProvider.ExpandBarView(
                        state = this.state,
                        title = "proxy",
                    )
                },
            ) {
                ProxyView()
            }

            Spacer(modifier = Modifier.height(medium))

            ExtensionSettingsList(
                onNavigate = { destination ->
                    navigateManager.navigate(destination)
                },
            )
        }
    }
}

@Composable
fun ExtensionSettingsList(onNavigate: (Route) -> Unit) {
    val copywriter = koinInject<GlobalCopywriter>()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        ExtensionSettingItem(
            title = copywriter.getText("ocr_settings"),
            description = copywriter.getText("language_module_settings"),
            icon = painterResource(Res.drawable.ocr),
            onClick = { onNavigate(OCR) },
        )
    }
}

@Composable
fun ExtensionSettingItem(
    title: String,
    description: String? = null,
    icon: Painter? = null,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = tiny4X),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    painter = it,
                    contentDescription = null,
                    modifier = Modifier.size(xLarge),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(medium))
            }

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = tiny3X),
                    )
                }
            }

            Icon(
                painter = arrowRight(),
                contentDescription = "Navigate",
                modifier = Modifier.size(large2X),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
