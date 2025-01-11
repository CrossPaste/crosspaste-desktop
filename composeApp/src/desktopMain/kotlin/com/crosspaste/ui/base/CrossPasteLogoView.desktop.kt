package com.crosspaste.ui.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.composeapp.generated.resources.Res
import com.crosspaste.composeapp.generated.resources.crosspaste_svg
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun CrossPasteLogoView(modifier: Modifier) {
    Box(modifier = modifier) {
        Icon(
            modifier = Modifier.fillMaxSize(),
            painter = painterResource(Res.drawable.crosspaste_svg),
            tint = MaterialTheme.colorScheme.onPrimary,
            contentDescription = "CrossPaste Logo",
        )
    }
}
