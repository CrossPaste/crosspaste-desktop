package com.clipevery.ui.clip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clipevery.dao.clip.ClipData
import com.valentinilk.shimmer.shimmer

@Composable
fun PrePreviewView(clipData: ClipData) {
    Row(
        modifier = Modifier
            .width(400.dp)
            .height(150.dp)
            .background(MaterialTheme.colors.background)
            .shimmer(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(131.dp)
                .padding(10.dp)
                .background(Color.Gray)
        )

        Column(modifier = Modifier.height(150.dp).width(230.dp).padding(10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .height(37.dp)
                    .width(230.dp)
                    .padding(bottom = 5.dp)
                    .background(Color.Gray)
            )
            Box(
                modifier = Modifier
                    .height(37.dp)
                    .width(180.dp)
                    .padding(vertical = 5.dp)
                    .background(Color.Gray)
            )
            Box(
                modifier = Modifier
                    .height(37.dp)
                    .width(230.dp)
                    .padding(top = 5.dp)
                    .background(Color.Gray)
            )
        }
    }
}