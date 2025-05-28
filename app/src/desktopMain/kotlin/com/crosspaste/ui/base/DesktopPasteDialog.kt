package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge
import org.koin.compose.koinInject

class DesktopPasteDialog(
    override val key: Any,
    override val title: String,
    private val onDismissRequest: () -> Unit = {},
    private val content: @Composable () -> Unit,
) : PasteDialog {

    @Composable
    override fun content() {
        Dialog(
            onDismissRequest = onDismissRequest,
        ) {
            Card(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(xxLarge)
                        .clip(small3XRoundedCornerShape)
                        .background(AppUIColors.dialogBackground),
            ) {
                Column(
                    modifier = Modifier.padding(medium),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val copywriter = koinInject<GlobalCopywriter>()
                    Text(
                        text = copywriter.getText(title),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = medium),
                    )
                    content.invoke()
                }
            }
        }
    }
}
