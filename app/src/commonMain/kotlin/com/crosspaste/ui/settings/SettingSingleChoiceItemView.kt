package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont
import com.crosspaste.ui.theme.AppUISize.xxLarge
import org.koin.compose.koinInject

@Composable
fun SettingSingleChoiceItemView(
    text: String,
    isFinalText: Boolean = false,
    painter: Painter? = null,
    tint: Color =
        MaterialTheme.colorScheme.contentColorFor(
            AppUIColors.generalBackground,
        ),
    modes: List<String>,
    getCurrentSingleChoiceValue: () -> String,
    onChange: (String) -> Unit,
) {
    SettingItemView(
        isFinalText = isFinalText,
        painter = painter,
        text = text,
        tint = tint,
    ) {
        val copywriter = koinInject<GlobalCopywriter>()
        var selectedIndex by remember {
            val value = getCurrentSingleChoiceValue()
            val index = modes.indexOfFirst { it == value }
            mutableStateOf(index)
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.height(xxLarge)) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    shape =
                        SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = modes.size,
                        ),
                    onClick = {
                        onChange(mode)
                        selectedIndex = index
                    },
                    selected = index == selectedIndex,
                    colors = AppUIColors.segmentedButtonColors,
                    label = {
                        Text(
                            text = copywriter.getText(mode),
                            style = AppUIFont.buttonTextStyle,
                        )
                    },
                )
            }
        }
    }
}
