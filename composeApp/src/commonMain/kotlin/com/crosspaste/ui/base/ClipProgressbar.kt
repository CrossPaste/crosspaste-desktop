package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun ClipProgressbar(process: Float) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (process > 0) {
                Row(
                    modifier =
                        Modifier.weight(process)
                            .fillMaxHeight()
                            .background(Color(0xFFC3FF93)),
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            if (process < 1) {
                Row(
                    modifier =
                        Modifier.weight(1 - process)
                            .fillMaxHeight()
                            .background(MaterialTheme.colors.background),
                ) {
                }
            }
        }
    }
}
