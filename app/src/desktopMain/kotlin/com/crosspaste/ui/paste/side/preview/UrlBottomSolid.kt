package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.DesktopAppUIFont
import com.crosspaste.utils.getUrlUtils

@Composable
fun UrlBottomSolid(
    title: String? = null,
    url: String,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(huge)
                .background(AppUIColors.topBackground)
                .padding(horizontal = small3X, vertical = small3X),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style =
                        DesktopAppUIFont.sideUrlTitleTextStyle.copy(
                            color =
                                MaterialTheme.colorScheme.contentColorFor(
                                    AppUIColors.topBackground,
                                ),
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.size(tiny4X))
            }
            val urlStyle =
                AppUIFont.mediumBodyTextStyle.copy(
                    color =
                        MaterialTheme.colorScheme
                            .contentColorFor(AppUIColors.topBackground)
                            .copy(alpha = 0.5f),
                )
            val maxLines = if (title == null) 2 else 1

            val density = LocalDensity.current

            val textMeasurer = rememberTextMeasurer()

            val urlUtils = getUrlUtils()

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val constraints =
                    with(density) {
                        Constraints(
                            maxWidth = this@BoxWithConstraints.maxWidth.toPx().toInt(),
                            maxHeight = Constraints.Infinity,
                        )
                    }

                val displayText =
                    remember(url, title, constraints) {
                        urlUtils.createMiddleEllipsisText(
                            text = url,
                            maxLines = maxLines,
                            textMeasurer = textMeasurer,
                            constraints = constraints,
                            style = urlStyle,
                            ellipsisPosition = 0.6f,
                        )
                    }

                Text(
                    text = displayText,
                    style = urlStyle,
                    maxLines = maxLines,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
