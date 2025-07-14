package com.crosspaste.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.settingItemsTitleTextStyle
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny2X
import org.koin.compose.koinInject

@Composable
fun SettingItemsTitleView(title: String) {
    val copywriter = koinInject<GlobalCopywriter>()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(AppUIColors.topBackground)
                .padding(start = medium, top = small2X, bottom = tiny2X),
    ) {
        Text(
            text = copywriter.getText(title),
            style = settingItemsTitleTextStyle,
        )
    }
}
