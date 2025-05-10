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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.crosspaste.app.AppSize
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.CrossPasteLogoView
import com.crosspaste.ui.base.KeyboardView
import com.crosspaste.ui.base.NewVersionButton
import com.crosspaste.ui.base.enter
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.theme.CrossPasteTheme.Theme
import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun CrossPasteSearchWindowContent() {
    val appInfo = koinInject<AppInfo>()
    val appSize = koinInject<AppSize>() as DesktopAppSize
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
                    .padding(10.dp)
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
                        .shadow(5.dp, RoundedCornerShape(10.dp))
                        .size(appSize.searchWindowContentSize)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column {
                    SearchInputView(requestFocus)

                    Row(modifier = Modifier.size(appSize.searchCoreContentSize)) {
                        SearchListView {
                            pasteSelectionViewModel.setSelectedIndex(it)
                            requestFocus()
                        }
                        VerticalDivider(thickness = 2.dp)
                        DetailPasteDataView()
                    }

                    Row(
                        modifier =
                            Modifier.height(40.dp)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CrossPasteLogoView(
                            modifier =
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .size(24.dp),
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "CrossPaste ${appInfo.appVersion}",
                            color = MaterialTheme.colorScheme.onSurface,
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = TextUnit.Unspecified,
                                ),
                        )

                        if (existNewVersion) {
                            Spacer(modifier = Modifier.width(10.dp))
                            NewVersionButton()
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        val prevAppName by appWindowManager.getPrevAppName().collectAsState(null)

                        prevAppName?.let {
                            Text(
                                text = "${copywriter.getText("paste_to")} $it",
                                color = MaterialTheme.colorScheme.onSurface,
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = TextUnit.Unspecified,
                                    ),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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
