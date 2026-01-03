package com.crosspaste.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.theme.ThemeDetector
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSegmentedPicker(modifier: Modifier = Modifier) {
    val copywriter = koinInject<GlobalCopywriter>()
    val themeDetector = koinInject<ThemeDetector>()

    val themeState = LocalThemeState.current
    val isCurrentThemeDark = themeState.isCurrentThemeDark
    val isFollowSystem = themeState.isFollowSystem

    var selectedThemeIndex by remember {
        mutableStateOf(
            when {
                isFollowSystem -> 1
                isCurrentThemeDark -> 2
                else -> 0
            },
        )
    }

    val options = listOf("light", "system", "dark")
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                onClick = {
                    when (index) {
                        0 -> themeDetector.setThemeConfig(isFollowSystem = false, isUserInDark = false)
                        1 -> themeDetector.setThemeConfig(isFollowSystem = true)
                        2 -> themeDetector.setThemeConfig(isFollowSystem = false, isUserInDark = true)
                    }
                    selectedThemeIndex = index
                },
                selected = index == selectedThemeIndex,
            ) {
                Text(copywriter.getText(label), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
