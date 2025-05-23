package com.crosspaste.recommend

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.weibo
import java.net.URLEncoder

class Weibo(private val uiSupport: UISupport) : RecommendationPlatform {
    override val platformName: String = "Weibo"

    @Composable
    override fun ButtonPlatform(onClick: () -> Unit) {
        ButtonContentView(onClick) {
            Image(
                painter = weibo(),
                contentDescription = "weibo",
                modifier = Modifier.size(32.dp),
            )
        }
    }

    override fun action(recommendationService: RecommendationService) {
        val encodedText = URLEncoder.encode(recommendationService.getRecommendText(), "UTF-8")
        val url = "https://service.weibo.com/share/share.php?title=$encodedText"
        uiSupport.openUrlInBrowser(url)
    }
}
