package com.crosspaste.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny3X
import org.koin.compose.koinInject

@Composable
fun SettingItemsTitleView(title: String) {
    val copywriter = koinInject<GlobalCopywriter>()

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .wrapContentHeight()
                .background(AppUIColors.settingsTitleBackground)
                .padding(start = medium, top = small2X, bottom = tiny3X),
    ) {
        Text(
            text = copywriter.getText(title),
            color = MaterialTheme.colorScheme.contentColorFor(AppUIColors.settingsTitleBackground),
            style = MaterialTheme.typography.titleSmall,
        )
    }
}
