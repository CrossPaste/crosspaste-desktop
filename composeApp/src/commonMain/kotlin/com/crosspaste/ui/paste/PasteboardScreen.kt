package com.crosspaste.ui.paste

import androidx.compose.runtime.Composable

// openTopBar is used on mobile to expand the top bar
// Desktop does not need to do anything
@Composable
expect fun PasteboardScreen(openTopBar: () -> Unit = {})
