package com.crosspaste.recommend

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.telegram
import com.crosspaste.ui.theme.AppUISize.xxLarge
import java.net.URLEncoder

class Telegram(
    private val uiSupport: UISupport,
) : RecommendationPlatform {
    override val platformName: String = "Telegram"

    @Composable
    override fun ButtonPlatform(onClick: () -> Unit) {
        ButtonContentView(onClick) {
            Image(
                painter = telegram(),
                contentDescription = "Telegram",
                modifier = Modifier.size(xxLarge),
            )
        }
    }

    override fun action(recommendationService: RecommendationService) {
        val encodedText = URLEncoder.encode(recommendationService.getRecommendText(), "UTF-8")
        val url = "https://t.me/share/url?text=$encodedText"
        uiSupport.openUrlInBrowser(url)
    }
}
