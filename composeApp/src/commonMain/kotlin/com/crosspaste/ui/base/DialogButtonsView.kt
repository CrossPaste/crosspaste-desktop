package com.crosspaste.ui.base

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.i18n.GlobalCopywriter
import org.koin.compose.koinInject

@Composable
fun DialogButtonsView(
    height: Dp = 40.dp,
    cancelTitle: String = "cancel",
    confirmTitle: String = "confirm",
    cancelAction: () -> Unit,
    confirmAction: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    Column(
        modifier = Modifier.wrapContentSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Divider(modifier = Modifier.fillMaxWidth().width(1.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(height),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AdaptiveTextButton(
                text = copywriter.getText(cancelTitle),
                onClick = cancelAction,
                modifier = Modifier.weight(0.5f).height(height),
            )
            Divider(modifier = Modifier.fillMaxHeight().width(1.dp))
            AdaptiveTextButton(
                text = copywriter.getText(confirmTitle),
                onClick = confirmAction,
                modifier = Modifier.weight(0.5f).height(height),
            )
        }
    }
}

@Composable
fun AdaptiveTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var fontSize by remember { mutableStateOf(14.sp) }
    var readyToDraw by remember { mutableStateOf(false) }

    Row(
        modifier =
            modifier.fillMaxSize()
                .clickable(onClick = onClick)
                .padding(horizontal = 5.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colors.primary,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 0.sp,
            onTextLayout = { textLayoutResult ->
                if (!readyToDraw) {
                    if (textLayoutResult.hasVisualOverflow) {
                        fontSize *= 0.9f
                    } else {
                        readyToDraw = true
                    }
                }
            },
        )
    }
}
