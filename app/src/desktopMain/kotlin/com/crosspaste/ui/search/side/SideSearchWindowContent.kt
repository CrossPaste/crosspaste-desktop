package com.crosspaste.ui.search.side

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.model.PasteSearchViewModel
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.CrossPasteTheme.Theme
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@Composable
fun SideSearchWindowContent() {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val logger = koinInject<KLogger>()
    val pasteSearchViewModel = koinInject<PasteSearchViewModel>()

    val inputSearch by pasteSearchViewModel.inputSearch.collectAsState()
    val showSearchWindow by appWindowManager.showSearchWindow.collectAsState()

    val focusRequester = appWindowManager.searchFocusRequester

    LaunchedEffect(showSearchWindow) {
        appWindowManager.searchComposeWindow?.let {
            if (showSearchWindow) {
                it.toFront()
                it.requestFocus()
                delay(16)
                focusRequester.requestFocus()
            }
        }
    }

    Theme {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(AppUIColors.appBackground),
        ) {
        }
    }
}
