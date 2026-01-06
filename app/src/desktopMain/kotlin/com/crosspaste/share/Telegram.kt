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
import com.composables.icons.fontawesome.brands.Telegram
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.mediumRoundedCornerShape
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
        Box(
            modifier =
                Modifier
                    .size(huge)
                    .background(
                        Color(0xFFE9F6FB),
                        mediumRoundedCornerShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = FontAwesome.Brands.Telegram,
                contentDescription = "Telegram",
                modifier = Modifier.size(xxLarge),
                tint = Color(0xFF24A1DE),
            )
        }
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
