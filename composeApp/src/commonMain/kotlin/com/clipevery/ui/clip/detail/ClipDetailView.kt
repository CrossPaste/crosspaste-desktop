package com.clipevery.ui.clip.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun ClipDetailView(
    detailView: @Composable () -> Unit,
    detailInfoView: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.fillMaxWidth().height(200.dp).padding(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
        ) {
            detailView()
        }

        Divider(
            modifier = Modifier.fillMaxWidth().height(1.dp),
            thickness = 2.dp,
        )

        Column(
            modifier =
                Modifier.fillMaxWidth().padding(10.dp),
        ) {
            detailInfoView()
        }
    }
}
