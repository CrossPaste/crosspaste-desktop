package com.crosspaste.ui.search.side

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.search
import com.crosspaste.ui.model.PasteSearchViewModel
import com.crosspaste.ui.search.center.searchTrailingIcon
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.huge
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
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(huge),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        modifier =
                            Modifier.focusTarget()
                                .focusRequester(focusRequester)
                                .onFocusEvent {
                                    logger.debug { "onFocusEvent $it" }
                                }
                                .onFocusChanged {
                                    logger.debug { "onFocusChanged $it" }
                                }
                                .fillMaxHeight()
                                .widthIn(max = 800.dp),
                        value = inputSearch,
                        leadingIcon = {
                            Icon(
                                painter = search(),
                                contentDescription = "search",
                            )
                        },
                        trailingIcon = {
                            searchTrailingIcon()
                        },
                        onValueChange = { pasteSearchViewModel.updateInputSearch(it) },
                        keyboardOptions = KeyboardOptions.Default.copy(autoCorrectEnabled = true),
                        visualTransformation = VisualTransformation.None,
                        placeholder = {
                            Text(
                                modifier = Modifier.wrapContentSize(),
                                text = copywriter.getText("search_pasteboard"),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        isError = false,
                        singleLine = true,
                        colors =
                            TextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledTextColor = Color.Transparent,
                                errorTextColor = MaterialTheme.colorScheme.error,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                errorCursorColor = Color.Red,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.secondary,
                                disabledIndicatorColor = Color.Transparent,
                                errorIndicatorColor = MaterialTheme.colorScheme.error,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                errorContainerColor = Color.Transparent,
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                disabledPlaceholderColor = Color.Transparent,
                                errorPlaceholderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                            ),
                        textStyle = MaterialTheme.typography.bodyLarge,
                    )
                }
                Column(modifier = Modifier.fillMaxSize()) {
                }
            }
        }
    }
}
