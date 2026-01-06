package com.crosspaste.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.x
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.mediumRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class X(
    private val uiSupport: UISupport,
) : AppSharePlatform {
    override val platformName: String = "X"

    @Composable
    override fun ButtonPlatform() {
        Box(
            modifier =
                Modifier
                    .size(huge)
                    .background(
                        Color(0xFFF2F2F2),
                        mediumRoundedCornerShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = x(),
                contentDescription = "Twitter/X",
                modifier = Modifier.size(xxLarge),
            )
        }
    }

    override suspend fun action(appShareService: AppShareService) {
        val encodedText =
            withContext(ioDispatcher) {
                URLEncoder.encode(appShareService.getShareText(), "UTF-8")
            }
        val url = "https://x.com/intent/post?text=$encodedText"
        uiSupport.openUrlInBrowser(url)
    }
}
