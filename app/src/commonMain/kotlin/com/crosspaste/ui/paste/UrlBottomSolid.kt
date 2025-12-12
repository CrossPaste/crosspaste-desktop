package com.crosspaste.ui.paste

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont
import com.crosspaste.ui.theme.AppUIFont.bottomSolidTitleTextStyle
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.utils.getUrlUtils

@Composable
fun UrlBottomSolid(
    modifier: Modifier = Modifier,
    title: String? = null,
    url: String,
    maxLines: Int,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            val contentColor = MaterialTheme.colorScheme.contentColorFor(AppUIColors.topBackground)

            if (title != null) {
                Text(
                    text = title,
                    style =
                        bottomSolidTitleTextStyle.copy(color = contentColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = tiny4X),
                )
            }

            val urlStyle =
                AppUIFont.mediumBodyTextStyle.copy(
                    color = contentColor.copy(alpha = 0.65f),
                    fontWeight = FontWeight.Normal,
                )

            val displayMaxLines = if (title == null) maxLines else maxLines - 1

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
                            url = url,
                            maxLines = displayMaxLines,
                            textMeasurer = textMeasurer,
                            constraints = constraints,
                            style = urlStyle,
                            ellipsisPosition = 0.6f,
                        )
                    }

                Text(
                    text = displayText,
                    style = urlStyle,
                    maxLines = displayMaxLines,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
