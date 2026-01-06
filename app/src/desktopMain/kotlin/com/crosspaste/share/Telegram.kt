package com.crosspaste.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.telegram
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class Telegram(
    private val uiSupport: UISupport,
) : AppSharePlatform {
    override val platformName: String = "Telegram"

    @Composable
    override fun ButtonPlatform() {
        Image(
            painter = telegram(),
            contentDescription = "Telegram",
            modifier = Modifier.size(xxLarge),
        )
    }

    override suspend fun action(appShareService: AppShareService) {
        val encodedText =
            withContext(ioDispatcher) {
                URLEncoder.encode(appShareService.getShareText(), "UTF-8")
            }
        val url = "https://t.me/share/url?text=$encodedText"
        uiSupport.openUrlInBrowser(url)
    }
}
