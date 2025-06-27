package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small

@Composable
fun BottomSolid(
    title: String? = null,
    url: String,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(AppUIColors.topBackground)
                .padding(horizontal = medium, vertical = small),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .wrapContentHeight(),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style =
                        AppUIFont.generalTitleTextStyle.copy(
                            color =
                                MaterialTheme.colorScheme.contentColorFor(
                                    AppUIColors.topBackground,
                                ),
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.size(small))
            }
            Text(
                text = url,
                style =
                    AppUIFont.mediumBodyTextStyle.copy(
                        color =
                            MaterialTheme.colorScheme.contentColorFor(
                                AppUIColors.topBackground,
                            ).copy(alpha = 0.5f),
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
    }
}
