package com.crosspaste.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WindowDecoration() {
    val copywriter = koinInject<GlobalCopywriter>()

    val appSizeValue = LocalDesktopAppSizeValueState.current
    val navController = LocalNavHostController.current

    val backStackEntry by navController.currentBackStackEntryAsState()

    val routeName =
        backStackEntry?.let { getRouteName(it.destination) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(appSizeValue.windowDecorationHeight)
                .offset(y = -appSizeValue.windowDecorationHeight)
                .padding(start = medium)
                .padding(bottom = tiny),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (routeName != null) {
            Text(
                modifier = Modifier,
                text = copywriter.getText(routeName),
                color =
                    MaterialTheme.colorScheme.contentColorFor(
                        AppUIColors.appBackground,
                    ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
