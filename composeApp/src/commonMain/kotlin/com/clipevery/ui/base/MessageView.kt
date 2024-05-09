package com.clipevery.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.clipevery.LocalKoinApplication

@Composable
fun MessageView() {
    val current = LocalKoinApplication.current
    val messageManager = current.koin.get<MessageManager>()

    messageManager.getCurrentMessageView()?.let { messageView ->
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clip(RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent()
                            }
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.shadow(5.dp, RoundedCornerShape(5.dp))) {
                Column(
                    modifier =
                        Modifier.width(320.dp)
                            .wrapContentHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colors.surface)
                            .padding(10.dp),
                ) {
                    messageView()
                }
            }
        }
    }
}
