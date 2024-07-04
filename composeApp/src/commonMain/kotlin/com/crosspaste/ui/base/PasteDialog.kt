package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.i18n.GlobalCopywriter

class PasteDialog(
    val key: Any,
    val width: Dp = 300.dp,
    val title: String,
    private val dynamicContent: @Composable () -> Unit,
) {

    @Composable
    fun content() {
        Box(
            modifier =
                Modifier.background(Color.Transparent)
                    .clip(RoundedCornerShape(10.dp))
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {},
                            onTap = {},
                            onLongPress = {},
                            onPress = {},
                        )
                    },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier.shadow(5.dp, RoundedCornerShape(10.dp))
                        .wrapContentSize()
                        .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier =
                        Modifier.wrapContentSize()
                            .background(MaterialTheme.colors.background),
                ) {
                    Column(
                        modifier =
                            Modifier.width(width)
                                .wrapContentHeight(),
                    ) {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .height(40.dp)
                                    .background(MaterialTheme.colors.surface),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val current = LocalKoinApplication.current
                            val copywriter = current.koin.get<GlobalCopywriter>()
                            Text(
                                text = copywriter.getText(title),
                                color = MaterialTheme.colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                            )
                        }
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .wrapContentHeight()
                                    .background(MaterialTheme.colors.background),
                        ) {
                            dynamicContent()
                        }
                    }
                }
            }
        }
    }
}
