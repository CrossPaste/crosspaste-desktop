package com.crosspaste.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.reddit
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class Reddit(
    private val uiSupport: UISupport,
) : SharePlatform {
    override val platformName: String = "Reddit"

    @Composable
    override fun ButtonPlatform() {
        Image(
            painter = reddit(),
            contentDescription = "reddit",
            modifier = Modifier.size(xxLarge),
        )
    }

    override suspend fun action(shareService: ShareService) {
        val encodedTitle =
            withContext(ioDispatcher) {
                URLEncoder.encode(shareService.getShareTitle(), "UTF-8")
            }
        val appUrl = shareService.getShareUrl()
        val url = "https://www.reddit.com/submit?title=$encodedTitle&url=$appUrl"
        uiSupport.openUrlInBrowser(url)
    }
}
