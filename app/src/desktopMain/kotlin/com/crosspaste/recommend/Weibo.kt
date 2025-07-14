package com.crosspaste.recommend

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.weibo
import com.crosspaste.ui.theme.AppUISize.xxLarge
import java.net.URLEncoder

class Weibo(
    private val uiSupport: UISupport,
) : RecommendationPlatform {
    override val platformName: String = "Weibo"

    @Composable
    override fun ButtonPlatform(onClick: () -> Unit) {
        ButtonContentView(onClick) {
            Image(
                painter = weibo(),
                contentDescription = "weibo",
                modifier = Modifier.size(xxLarge),
            )
        }
    }

    override fun action(recommendationService: RecommendationService) {
        val encodedText = URLEncoder.encode(recommendationService.getRecommendText(), "UTF-8")
        val url = "https://service.weibo.com/share/share.php?title=$encodedText"
        uiSupport.openUrlInBrowser(url)
    }
}
