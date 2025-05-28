package com.crosspaste.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

object AppUISize {
    val zero = 0.dp
    val tiny6X = 0.5.dp
    val tiny5X = 1.dp
    val tiny4X = 2.dp
    val tiny3X = 4.dp
    val tiny2X = 6.dp
    val tiny = 8.dp
    val small3X = 10.dp
    val small2X = 12.dp
    val small = 14.dp
    val medium = 16.dp
    val large = 18.dp
    val large2X = 20.dp
    val xLarge = 24.dp
    val xxLarge = 30.dp
    val xxxLarge = 36.dp
    val xxxxLarge = 48.dp
    val huge = 60.dp
    val giant = 72.dp

    val zeroRoundedCornerShape = RoundedCornerShape(zero)
    val tiny4XRoundedCornerShape = RoundedCornerShape(tiny4X)
    val tiny3XRoundedCornerShape = RoundedCornerShape(tiny3X)
    val tiny2XRoundedCornerShape = RoundedCornerShape(tiny2X)
    val tinyRoundedCornerShape = RoundedCornerShape(tiny)
    val small3XRoundedCornerShape = RoundedCornerShape(small3X)
    val small2XRoundedCornerShape = RoundedCornerShape(small2X)
    val smallRoundedCornerShape = RoundedCornerShape(small)
    val mediumRoundedCornerShape = RoundedCornerShape(medium)

    val elevation: CardElevation
        @Composable
        get() =
            CardDefaults.cardElevation(
                defaultElevation = 0.8.dp,
                pressedElevation = tiny5X,
                hoveredElevation = 3.dp,
            )
}
