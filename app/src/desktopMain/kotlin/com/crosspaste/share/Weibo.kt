package com.crosspaste.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.weibo
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class Weibo(
    private val uiSupport: UISupport,
) : SharePlatform {
    override val platformName: String = "Weibo"

    @Composable
    override fun ButtonPlatform() {
        Image(
            painter = weibo(),
            contentDescription = "weibo",
            modifier = Modifier.size(xxLarge),
        )
    }

    override suspend fun action(shareService: ShareService) {
        val encodedText =
            withContext(ioDispatcher) {
                URLEncoder.encode(shareService.getShareText(), "UTF-8")
            }
        val url = "https://service.weibo.com/share/share.php?title=$encodedText"
        uiSupport.openUrlInBrowser(url)
    }
}
