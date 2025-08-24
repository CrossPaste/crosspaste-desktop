package com.crosspaste.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.crosspaste.app.AppSize
import com.crosspaste.ui.base.checkboxChecked
import com.crosspaste.ui.base.checkboxUnchecked
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import org.koin.compose.koinInject

@Composable
fun SettingCheckboxView(
    list: List<String>,
    getCurrentCheckboxValue: (Int) -> Boolean,
    onChange: (Int, Boolean) -> Unit,
) {
    list.forEachIndexed { index, content ->
        SettingCheckboxItemView(
            content = content,
            height = null,
            getCurrentCheckboxValue = { getCurrentCheckboxValue(index) },
            onChange = { onChange(index, it) },
        )
    }
}

@Composable
fun SettingCheckboxItemView(
    content: String,
    height: Dp? = null,
    tint: Color =
        MaterialTheme.colorScheme.contentColorFor(
            AppUIColors.generalBackground,
        ),
    getCurrentCheckboxValue: () -> Boolean,
    onChange: (Boolean) -> Unit,
) {
    val appSize = koinInject<AppSize>()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height ?: appSize.settingsItemHeight)
                .clickable {
                    val state = getCurrentCheckboxValue()
                    onChange(!state)
                }.padding(horizontal = small2X),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier =
                Modifier
                    .size(medium),
            painter =
                if (getCurrentCheckboxValue()) {
                    checkboxChecked()
                } else {
                    checkboxUnchecked()
                },
            contentDescription = null,
            tint = tint,
        )

        Spacer(modifier = Modifier.width(small2X))

        SettingsText(text = content)
    }
}
