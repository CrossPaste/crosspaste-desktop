package com.crosspaste.ui.paste

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont
import com.crosspaste.ui.theme.AppUIFont.bottomSolidTitleTextStyle
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.safeIsDirectory
import okio.Path

data class FileDisplayInfo(
    val title: String?,
    val subtitle: String,
)

fun getFileDisplayInfo(
    files: List<Path>,
    copywriter: GlobalCopywriter,
): FileDisplayInfo? {
    if (files.isEmpty()) return null

    if (files.size > 1) {
        val subtitle = files.joinToString(", ") { it.name }
        return FileDisplayInfo(null, subtitle)
    }

    val file = files[0]
    val title = file.name

    if (file.safeIsDirectory) {
        return FileDisplayInfo(title, copywriter.getText("folder"))
    }

    val subtitle = getFileUtils().formatBytes(file.toFile().length())
    return FileDisplayInfo(title, subtitle)
}

@Composable
fun FileBottomSolid(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String,
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
                        bottomSolidTitleTextStyle.copy(contentColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = tiny4X),
                )
            }

            val subStyle =
                AppUIFont.mediumBodyTextStyle.copy(
                    color = contentColor.copy(alpha = 0.65f),
                    fontWeight = FontWeight.Normal,
                )

            val displayMaxLines = if (title == null) 2 else 1

            Text(
                text = subtitle,
                style = subStyle,
                maxLines = displayMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
