package com.crosspaste.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.CrossPasteLogoView
import com.crosspaste.ui.base.KeyboardView
import com.crosspaste.ui.base.NewVersionButton
import com.crosspaste.ui.base.enter
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.small3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.CrossPasteTheme.Theme
import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun CrossPasteSearchWindowContent() {
    val appInfo = koinInject<AppInfo>()
    val appSize = koinInject<DesktopAppSize>()
    val copywriter = koinInject<GlobalCopywriter>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val appUpdateService = koinInject<AppUpdateService>()

    val pasteSelectionViewModel = koinInject<PasteSelectionViewModel>()

    val requestFocus: () -> Unit = {
        appWindowManager.searchFocusRequester.requestFocus()
    }

    val scope = rememberCoroutineScope()

    val existNewVersion by appUpdateService.existNewVersion().collectAsState(false)

    Theme {
        Box(
            modifier =
                Modifier
                    .background(Color.Transparent)
                    .clip(appSize.appRoundedCornerShape)
                    .size(appSize.searchWindowSize)
                    .padding(small3X)
                    .onKeyEvent {
                        when (it.key) {
                            Key.Enter -> {
                                scope.launch(mainDispatcher) {
                                    appWindowManager.setSearchCursorWait()
                                    pasteSelectionViewModel.toPaste()
                                    appWindowManager.resetSearchCursor()
                                }
                                true
                            }
                            Key.DirectionUp -> {
                                pasteSelectionViewModel.upSelectedIndex()
                                true
                            }
                            Key.DirectionDown -> {
                                pasteSelectionViewModel.downSelectedIndex()
                                true
                            }
                            Key.N -> {
                                if (it.isCtrlPressed) {
                                    pasteSelectionViewModel.downSelectedIndex()
                                }
                                true
                            }
                            Key.P -> {
                                if (it.isCtrlPressed) {
                                    pasteSelectionViewModel.upSelectedIndex()
                                }
                                true
                            }
                            else -> {
                                false
                            }
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .shadow(tiny2X, small3XRoundedCornerShape)
                        .size(appSize.searchWindowContentSize)
                        .background(AppUIColors.searchBackground)
                        .border(tiny5X, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), small3XRoundedCornerShape),
                contentAlignment = Alignment.Center,
            ) {
                Column {
                    SearchInputView(requestFocus)

                    Row(modifier = Modifier.size(appSize.searchCoreContentSize)) {
                        SearchListView {
                            pasteSelectionViewModel.setSelectedIndex(it)
                            requestFocus()
                        }
                        VerticalDivider(thickness = tiny5X)
                        DetailPasteDataView()
                    }

                    Row(
                        modifier =
                            Modifier.height(40.dp)
                                .fillMaxWidth()
                                .background(AppUIColors.searchFootBackground)
                                .padding(horizontal = small3X),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CrossPasteLogoView(
                            size = xLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        Spacer(modifier = Modifier.width(small3X))

                        Text(
                            text = "CrossPaste ${appInfo.appVersion}",
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = TextUnit.Unspecified,
                                ),
                        )

                        if (existNewVersion) {
                            Spacer(modifier = Modifier.width(small3X))
                            NewVersionButton()
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        val prevAppName by appWindowManager.getPrevAppName().collectAsState(null)

                        prevAppName?.let {
                            Text(
                                text = "${copywriter.getText("paste_to")} $it",
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = TextUnit.Unspecified,
                                    ),
                            )
                            Spacer(modifier = Modifier.width(tiny))
                            Row(
                                modifier =
                                    Modifier.clickable {
                                        scope.launch(mainDispatcher) {
                                            appWindowManager.setSearchCursorWait()
                                            pasteSelectionViewModel.toPaste()
                                            appWindowManager.resetSearchCursor()
                                        }
                                    },
                            ) {
                                KeyboardView(keyboardValue = enter)
                            }
                        }
                    }
                }
            }
        }
    }
}
