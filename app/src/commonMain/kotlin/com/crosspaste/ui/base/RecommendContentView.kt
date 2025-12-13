package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.recommend.RecommendationService
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.recommendTextTextStyle
import com.crosspaste.ui.theme.AppUIFont.recommendTitleTextStyle
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.mediumRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

@Composable
fun RecommendContentView() {
    val copywriter = koinInject<GlobalCopywriter>()
    val recommendationService = koinInject<RecommendationService>()
    var recommendText by remember {
        mutableStateOf(recommendationService.getRecommendText())
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AppUIColors.appBackground)
                .padding(horizontal = medium)
                .padding(bottom = medium)
                .clip(tinyRoundedCornerShape)
                .background(AppUIColors.generalBackground),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(xxxxLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(0.3f))

            Text(
                modifier = Modifier.padding(bottom = xxLarge),
                text = copywriter.getText("recommend_to_friends"),
                style = recommendTitleTextStyle,
            )

            Text(
                text = recommendText,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clip(mediumRoundedCornerShape)
                        .border(
                            width = tiny5X,
                            color = AppUIColors.darkBorderColor,
                            shape = mediumRoundedCornerShape,
                        ).background(AppUIColors.topBackground)
                        .padding(medium),
                style = recommendTextTextStyle,
                maxLines = Int.MAX_VALUE,
            )

            Spacer(modifier = Modifier.height(small3X))

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(medium),
                verticalArrangement = Arrangement.spacedBy(tiny2X),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(recommendationService.recommendPlatformList) { platform ->
                    platform.ButtonPlatform {
                        platform.action(recommendationService)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.7f))
        }
    }
}
