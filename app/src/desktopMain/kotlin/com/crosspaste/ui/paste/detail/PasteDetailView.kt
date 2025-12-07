package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.tiny5X

@Composable
fun PasteDataScope.PasteDetailView(
    detailView: @Composable PasteDataScope.() -> Unit,
    detailInfoView: @Composable PasteDataScope.() -> Unit,
) {
    val appSizeValue = LocalDesktopAppSizeValueState.current

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .size(appSizeValue.centerSearchWindowDetailViewDpSize)
                    .padding(appSizeValue.centerSearchDetailPaddingValues)
                    .clip(appSizeValue.centerSearchDetailRoundedCornerShape),
        ) {
            detailView()
        }

        HorizontalDivider(thickness = tiny5X)

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(appSizeValue.centerSearchInfoPaddingValues),
        ) {
            detailInfoView()
        }
    }
}
