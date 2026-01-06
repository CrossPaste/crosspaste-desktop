package com.crosspaste.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.composables.icons.fontawesome.FontAwesome
import com.composables.icons.fontawesome.brands.Reddit
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.mediumRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class Reddit(
    private val uiSupport: UISupport,
) : AppSharePlatform {
    override val platformName: String = "Reddit"

    @Composable
    override fun ButtonPlatform() {
        Box(
            modifier =
                Modifier
                    .size(huge)
                    .background(
                        Color(0xFFFFE9E0),
                        mediumRoundedCornerShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = FontAwesome.Brands.Reddit,
                contentDescription = "reddit",
                modifier = Modifier.size(xxLarge),
                tint = Color(0xFFFF4500),
            )
        }
    }

    override suspend fun action(appShareService: AppShareService) {
        val encodedTitle =
            withContext(ioDispatcher) {
                URLEncoder.encode(appShareService.getShareTitle(), "UTF-8")
            }
        val appUrl = appShareService.getShareUrl()
        val url = "https://www.reddit.com/submit?title=$encodedTitle&url=$appUrl"
        uiSupport.openUrlInBrowser(url)
    }
}
