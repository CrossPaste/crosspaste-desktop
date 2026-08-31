package com.crosspaste.ui.devices

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.ui.theme.AppUISize.zero

/**
 * Platform-specific token input row. Desktop uses one text field per digit with
 * focus advancing between them; that same focus hop makes Android IMEs close and
 * reopen the soft keyboard on every keystroke, so mobile platforms may provide a
 * different implementation (e.g. a single hidden field driving digit boxes).
 */
@Composable
expect fun TokenInputRow(
    tokens: MutableList<String>,
    isError: Boolean,
    isLoading: Boolean,
    focusRequesters: List<FocusRequester>,
    confirmAction: () -> Unit,
    cancelAction: () -> Unit,
)

@Composable
fun DefaultTokenInputRow(
    tokens: MutableList<String>,
    isError: Boolean,
    isLoading: Boolean,
    focusRequesters: List<FocusRequester>,
    confirmAction: () -> Unit,
    cancelAction: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .graphicsLayer { alpha = if (isLoading) 0.5f else 1f },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        tokens.forEachIndexed { index, token ->
            TokenInputBox(
                token = token,
                index = index,
                isError = isError,
                isLoading = isLoading,
                focusRequesters = focusRequesters,
                onValueChange = { value ->
                    if (value.length <= 1 && value.all { it.isDigit() }) {
                        tokens[index] = value
                        if (value.isNotEmpty() && index < tokens.size - 1) {
                            focusRequesters[index + 1].requestFocus()
                        }
                    }
                },
                confirmAction = confirmAction,
                cancelAction = cancelAction,
            )
        }
    }
}

@Composable
fun TokenInputBox(
    token: String,
    index: Int,
    isError: Boolean,
    isLoading: Boolean,
    focusRequesters: List<FocusRequester>,
    onValueChange: (String) -> Unit,
    confirmAction: () -> Unit,
    cancelAction: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    var isFocused by remember { mutableStateOf(false) }

    val focusRequester = focusRequesters[index]

    val borderColor: Color =
        when {
            isError -> MaterialTheme.colorScheme.error
            isFocused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        }

    val borderWidth = if (isFocused || isError) tiny4X else tiny5X

    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest

    val textColor =
        if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }

    val mergedTextStyle =
        MaterialTheme.typography.headlineMedium.copy(
            color = textColor,
            textAlign = TextAlign.Center,
            lineHeight = 1.em,
            fontFamily = FontFamily.Monospace,
            lineHeightStyle =
                LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None,
                ),
        )

    Surface(
        modifier =
            modifier
                .width(xxxLarge)
                .height(xxxxLarge)
                .onFocusChanged { isFocused = it.isFocused }
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = shape,
                ).onKeyEvent {
                    if (isLoading) return@onKeyEvent false
                    handleKeyEvent(it, confirmAction, cancelAction)
                },
        shape = shape,
        color = containerColor,
        tonalElevation = if (isFocused) tiny4X else zero,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            PlatformTokenTextField(
                value = token,
                enabled = !isLoading,
                index = index,
                tokenCount = focusRequesters.size,
                onValueChange = {
                    if (it.length <= 1) {
                        onValueChange(it)
                    }
                },
                onBackspaceWhenEmpty = {
                    if (index > 0) {
                        focusRequesters[index - 1].requestFocus()
                    }
                },
                modifier = Modifier.focusRequester(focusRequester),
                textStyle = mergedTextStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        }
    }

    // Focus the first box as soon as it is actually focusable, so the cursor
    // blinks there on open and the user can type without clicking first. Keyed on
    // the FocusRequester (recreated once the pairing credential type resolves) and
    // on isLoading (boxes are disabled until the type is known): a plain
    // LaunchedEffect(Unit) fired once against the disabled box and never retried on
    // the enabled one, so focus was silently lost.
    if (index == 0) {
        LaunchedEffect(focusRequester, isLoading) {
            if (!isLoading) {
                focusRequester.requestFocus()
            }
        }
    }
}

fun handleKeyEvent(
    event: KeyEvent,
    confirmAction: () -> Unit,
    cancelAction: () -> Unit,
): Boolean =
    when (event.key) {
        Key.Enter -> {
            confirmAction()
            true
        }
        Key.Escape -> {
            cancelAction()
            true
        }
        else -> false
    }
