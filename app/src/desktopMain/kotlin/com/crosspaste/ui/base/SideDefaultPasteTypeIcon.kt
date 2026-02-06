package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.crosspaste.paste.getIconData
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.xxxxLarge

@Composable
fun PasteDataScope.SideDefaultPasteTypeIcon(modifier: Modifier = Modifier) {
    val type by remember(pasteData.id) { mutableStateOf(pasteData.getType()) }

    val iconData = type.getIconData()

    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .wrapContentWidth()
                .clip(RoundedCornerShape(topStart = tiny2X, bottomStart = tiny2X))
                .background(iconData.bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = iconData.imageVector,
            contentDescription = "Paste Icon",
            modifier =
                modifier
                    .padding(start = tiny, end = tiny)
                    .size(xxxxLarge),
            tint = iconData.color,
        )
    }
}
