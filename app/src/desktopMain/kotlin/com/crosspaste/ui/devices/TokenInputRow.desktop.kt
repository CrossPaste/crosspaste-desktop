package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester

@Composable
actual fun TokenInputRow(
    tokens: MutableList<String>,
    isError: Boolean,
    isLoading: Boolean,
    focusRequesters: List<FocusRequester>,
    confirmAction: () -> Unit,
    cancelAction: () -> Unit,
) {
    DefaultTokenInputRow(
        tokens = tokens,
        isError = isError,
        isLoading = isLoading,
        focusRequesters = focusRequesters,
        confirmAction = confirmAction,
        cancelAction = cancelAction,
    )
}
