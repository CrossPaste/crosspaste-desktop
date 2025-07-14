package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpSize
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny4XRoundedCornerShape
import com.crosspaste.ui.theme.DesktopAppUIFont.keyboardCharTextStyle

const val enter: String = "↵"

@Composable
fun KeyboardView(
    keyboardValue: String,
    background: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
) {
    val textMeasurer = rememberTextMeasurer()
    val size = textMeasurer.measure(keyboardValue, keyboardCharTextStyle).size
    val dpSize =
        with(LocalDensity.current) {
            DpSize(size.width.toDp(), size.height.toDp())
        }

    Row(
        modifier =
            Modifier
                .size(dpSize.plus(DpSize(small3X, small3X)))
                .clip(tiny4XRoundedCornerShape)
                .background(background),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = keyboardValue,
            style = keyboardCharTextStyle,
            color = MaterialTheme.colorScheme.contentColorFor(background),
        )
    }
}
