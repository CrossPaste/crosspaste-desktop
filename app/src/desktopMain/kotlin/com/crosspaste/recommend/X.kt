package com.crosspaste.recommend

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.x
import com.crosspaste.ui.theme.AppUISize.xxLarge
import java.net.URLEncoder

class X(
    private val uiSupport: UISupport,
) : RecommendationPlatform {
    override val platformName: String = "X"

    @Composable
    override fun ButtonPlatform(onClick: () -> Unit) {
        ButtonContentView(onClick) {
            Image(
                painter = x(),
                contentDescription = "Twitter/X",
                modifier = Modifier.size(xxLarge),
            )
        }
    }

    override suspend fun action(recommendationService: RecommendationService) {
        val encodedText = URLEncoder.encode(recommendationService.getRecommendText(), "UTF-8")
        val url = "https://x.com/intent/post?text=$encodedText"
        uiSupport.openUrlInBrowser(url)
    }
}
