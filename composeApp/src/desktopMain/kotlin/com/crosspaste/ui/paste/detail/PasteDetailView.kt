package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.crosspaste.app.AppSize
import com.crosspaste.app.DesktopAppSize
import org.koin.compose.koinInject

@Composable
fun PasteDetailView(
    detailView: @Composable () -> Unit,
    detailInfoView: @Composable () -> Unit,
) {
    val appSize = koinInject<AppSize>() as DesktopAppSize

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.size(appSize.searchWindowDetailViewDpSize)
                    .padding(appSize.searchDetailPaddingValues)
                    .clip(appSize.searchDetailRoundedCornerShape),
        ) {
            detailView()
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth().height(1.dp),
            thickness = 2.dp,
        )

        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(appSize.searchInfoPaddingValues),
        ) {
            detailInfoView()
        }
    }
}
