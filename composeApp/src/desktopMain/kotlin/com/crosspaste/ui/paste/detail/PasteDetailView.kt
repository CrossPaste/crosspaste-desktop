package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.crosspaste.app.AppSize
import org.koin.compose.koinInject

@Composable
fun PasteDetailView(
    detailView: @Composable () -> Unit,
    detailInfoView: @Composable () -> Unit,
) {
    val appSize = koinInject<AppSize>()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.size(appSize.searchWindowDetailViewDpSize).padding(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
        ) {
            detailView()
        }

        HorizontalDivider(
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
