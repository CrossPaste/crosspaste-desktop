package com.crosspaste.ui.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import com.crosspaste.composeapp.generated.resources.Res
import com.crosspaste.composeapp.generated.resources.crosspaste_svg
import org.jetbrains.compose.resources.painterResource

class DesktopBaseViewProvider : BaseViewProvider {
    @Composable
    override fun ExpandView(
        title: String,
        icon: @Composable (() -> Painter?),
        defaultExpand: Boolean,
        horizontalPadding: Dp,
        titleBackgroundColor: Color,
        onTitleBackgroundColor: Color,
        backgroundColor: Color,
        content: @Composable (() -> Unit),
    ) {
        ExpandContentView(
            title = title,
            icon = icon,
            defaultExpand = defaultExpand,
            horizontalPadding = horizontalPadding,
            titleBackgroundColor = titleBackgroundColor,
            onTitleBackgroundColor = onTitleBackgroundColor,
            backgroundColor = backgroundColor,
            content = content,
        )
    }

    @Composable
    override fun CrossPasteLogoView(modifier: Modifier) {
        Box(modifier = modifier) {
            Icon(
                modifier = Modifier.fillMaxSize(),
                painter = painterResource(Res.drawable.crosspaste_svg),
                tint = MaterialTheme.colorScheme.onPrimary,
                contentDescription = "CrossPaste Logo",
            )
        }
    }
}
