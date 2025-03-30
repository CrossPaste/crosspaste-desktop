package com.crosspaste.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.crosspaste.clean.CleanTime
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.BaseColor
import com.crosspaste.ui.base.MenuItem
import com.crosspaste.ui.base.getMenWidth
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
                .shadow(15.dp),
    ) {
        val cleanTimeMenuTexts =
            CleanTime.entries.map { cleanTime ->
                "${cleanTime.quantity} ${copywriter.getText(cleanTime.unit)}"
            }.toTypedArray()

        val maxWidth =
            getMenWidth(cleanTimeMenuTexts) {
                28.dp
            }

        Column(
            modifier =
                Modifier
                    .width(maxWidth)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surface),
        ) {
            cleanTimeMenuTexts.forEachIndexed { index, text ->
                MenuItem(
                    text = text,
                    extendContent =
                        if (index == selectIndex) {
                            {
                                Spacer(modifier = Modifier.width(16.dp))
                                Icon(
                                    modifier = Modifier.size(20.dp),
                                    imageVector = Icons.Outlined.Check,
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
