package com.crosspaste.ui.base

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.crosspaste.i18n.GlobalCopywriter
import org.koin.compose.koinInject

class DesktopPasteDialog(
    override val key: Any,
    override val title: String,
    private val onDismissRequest: () -> Unit = {},
    private val content: @Composable () -> Unit,
) : PasteDialog {

    override fun onDismissRequest() {
        return onDismissRequest()
    }

    @Composable
    override fun content() {
        Dialog(
            onDismissRequest = onDismissRequest,
        ) {
            Card(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(32.dp)
                        .clip(RoundedCornerShape(16.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val copywriter = koinInject<GlobalCopywriter>()
                    Text(
                        text = copywriter.getText(title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    content.invoke()
                }
            }
        }
    }
}
