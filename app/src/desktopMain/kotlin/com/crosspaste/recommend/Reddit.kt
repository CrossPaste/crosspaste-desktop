package com.crosspaste.recommend

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.reddit
import java.net.URLEncoder

class Reddit(private val uiSupport: UISupport) : RecommendationPlatform {
    override val platformName: String = "Reddit"

    @Composable
    override fun ButtonPlatform(onClick: () -> Unit) {
        ButtonContentView(onClick) {
            Image(
                painter = reddit(),
                contentDescription = "reddit",
                modifier = Modifier.size(32.dp),
            )
        }
    }

    override fun action(recommendationService: RecommendationService) {
        val encodedTitle = URLEncoder.encode(recommendationService.getRecommendTitle(), "UTF-8")
        val appUrl = recommendationService.getRecommendUrl()
        val url = "https://www.reddit.com/submit?title=$encodedTitle&url=$appUrl"
        uiSupport.openUrlInBrowser(url)
    }
}
