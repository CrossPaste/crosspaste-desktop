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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.recommend.RecommendationService
import com.crosspaste.ui.theme.AppUIColors
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
            Modifier.fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppUIColors.recommendedBackground),
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(0.3f))

            Text(
                modifier = Modifier.padding(bottom = 32.dp),
                text = copywriter.getText("recommend_to_friends"),
                style =
                    TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 40.sp,
                    ),
            )

            Text(
                text = recommendText,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                style =
                    TextStyle(
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                maxLines = Int.MAX_VALUE,
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
