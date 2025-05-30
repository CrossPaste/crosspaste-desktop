package com.crosspaste.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import com.crosspaste.clean.CleanTime
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.BaseColor
import com.crosspaste.ui.base.MenuItem
import com.crosspaste.ui.base.check
import com.crosspaste.ui.theme.AppUIFont.getFontWidth
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.utils.ColorUtils
import org.koin.compose.koinInject

@Composable
fun CleanTimeMenuView(
    selectIndex: Int,
    closeMenu: (Int) -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()

    Box(
        modifier =
            Modifier
                .wrapContentSize()
                .background(Color.Transparent)
                .shadow(small),
    ) {
        val cleanTimeMenuTexts =
            CleanTime.entries.map { cleanTime ->
                "${cleanTime.quantity} ${copywriter.getText(cleanTime.unit)}"
            }.toTypedArray()

        val maxWidth =
            getFontWidth(cleanTimeMenuTexts) {
                small * 2
            }

        Column(
            modifier =
                Modifier
                    .width(maxWidth)
                    .wrapContentHeight()
                    .clip(tiny2XRoundedCornerShape)
                    .background(MaterialTheme.colorScheme.surface),
        ) {
            cleanTimeMenuTexts.forEachIndexed { index, text ->
                MenuItem(
                    text = text,
                    extendContent =
                        if (index == selectIndex) {
                            {
                                Spacer(modifier = Modifier.width(medium))
                                Icon(
                                    modifier = Modifier.size(large2X),
                                    painter = check(),
                                    contentDescription = "selected",
                                    tint =
                                        ColorUtils.getAdaptiveColor(
                                            backgroundColor = MaterialTheme.colorScheme.surface,
                                            BaseColor.Green.targetHue,
                                        ),
                                )
                            }
                        } else {
                            null
                        },
                ) {
                    closeMenu(index)
                }
            }
        }
    }
}
