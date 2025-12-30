package com.crosspaste.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppUrls
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.CrossPasteLogoView
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.theme.AppUISize.giant
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.mediumRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxLargeRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import org.koin.compose.koinInject

@Composable
fun AboutContentView() {
    val appInfo = koinInject<AppInfo>()
    val appUrls = koinInject<AppUrls>()
    val uiSupport = koinInject<UISupport>()
    val copywriter = koinInject<GlobalCopywriter>()

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        shape = xxLargeRoundedCornerShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = tiny4X,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = medium, vertical = xxLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CrossPasteLogoView(
                size = giant,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(medium))

            // 2. App Name &amp;amp; Version Badge
            Text(
                text = "CrossPaste",
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Surface(
                modifier = Modifier.padding(top = tiny),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = medium, vertical = tiny3X),
                    text = "v${appInfo.displayVersion()}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(modifier = Modifier.height(xxLarge))

            // 3. Info Items List
            AboutInfoList(uiSupport, appUrls, copywriter)

            Spacer(modifier = Modifier.height(xLarge))

            // 4. Footer
            Text(
                text = "COMPILE FUTURE",
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
fun AboutInfoList(
    uiSupport: UISupport,
    appUrls: AppUrls,
    copywriter: GlobalCopywriter,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AboutInfoItem(
            icon = Icons.Default.Language,
            title = copywriter.getText("official_website"),
            onClick = { uiSupport.openCrossPasteWebInBrowser() },
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        AboutInfoItem(
            icon = Icons.Default.School,
            title = copywriter.getText("newbie_tutorial"),
            onClick = { uiSupport.openCrossPasteWebInBrowser("tutorial/pasteboard") },
        )
        AboutInfoItem(
            icon = Icons.Default.AutoAwesome,
            title = copywriter.getText("change_log"),
            onClick = { uiSupport.openUrlInBrowser(appUrls.changeLogUrl) },
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = medium, vertical = tiny3X),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        AboutInfoItem(
            icon = Icons.Default.Feedback,
            title = copywriter.getText("feedback"),
            onClick = { uiSupport.openUrlInBrowser(appUrls.issueTrackerUrl) },
        )
        AboutInfoItem(
            icon = Icons.Default.Mail,
            title = copywriter.getText("contact_us"),
            onClick = { uiSupport.openEmailClient("compile.future@gmail.com") },
        )
    }
}

@Composable
fun AboutInfoItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth scale feedback on press
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "scale")

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .scale(scale)
                .clip(mediumRoundedCornerShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                ),
        color = Color.Transparent,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(horizontal = medium, vertical = small2X),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(xxxLarge),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(large2X),
                )
            }

            Text(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = tiny),
                text = title,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(large2X),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
