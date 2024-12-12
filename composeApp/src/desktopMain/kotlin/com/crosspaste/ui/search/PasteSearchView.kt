package com.crosspaste.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppSize
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.CrossPasteTheme.Theme
import com.crosspaste.ui.CrossPasteTheme.darken
import com.crosspaste.ui.base.KeyboardView
import com.crosspaste.ui.base.crosspasteIcon
import com.crosspaste.ui.base.enter
import com.crosspaste.ui.base.menuItemReminderTextStyle
import com.crosspaste.ui.model.PasteSelectionViewModel
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

    val requestFocus = {
        appWindowManager.searchFocusRequester.requestFocus()
    }

    val scope = rememberCoroutineScope()

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
                        .background(MaterialTheme.colorScheme.secondaryContainer)
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
                                .background(MaterialTheme.colorScheme.surface.darken(0.1f))
                                .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = crosspasteIcon(),
                            contentDescription = "CrossPaste",
                            modifier =
                                Modifier.size(25.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "CrossPaste ${appInfo.appVersion}",
                            style =
                                TextStyle(
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = FontFamily.SansSerif,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 14.sp,
                                ),
                        )

                        if (appUpdateService.existNewVersion()) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Row(
                                modifier =
                                    Modifier.width(32.dp)
                                        .height(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Red)
                                        .clickable {
                                            appUpdateService.jumpDownload()
                                        },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "new!",
                                    color = Color.White,
                                    style = menuItemReminderTextStyle,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        appWindowManager.getPrevAppName()?.let {
                            Text(
                                text = "${copywriter.getText("paste_to")} $it",
                                style =
                                    TextStyle(
                                        fontWeight = FontWeight.Normal,
                                        fontFamily = FontFamily.SansSerif,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontSize = 14.sp,
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
