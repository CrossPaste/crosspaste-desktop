package com.clipevery.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val enter: String = "↵"

@Composable
fun KeyboardView(
    textStyle: TextStyle =
        TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            fontFamily = MaterialTheme.typography.body1.fontFamily,
        ),
    keyboardValue: String,
) {
    val textMeasurer = rememberTextMeasurer()
    val size = textMeasurer.measure(keyboardValue, textStyle).size
    val dpSize = with(LocalDensity.current) { DpSize(size.width.toDp(), size.height.toDp()) }

    Row(
        modifier =
            Modifier.size(dpSize.plus(DpSize(10.dp, 10.dp)))
                .background(MaterialTheme.colors.background)
                .clip(RoundedCornerShape(2.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = keyboardValue,
            style = textStyle,
            color = MaterialTheme.colors.onBackground,
        )
    }
}
