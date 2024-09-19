package com.crosspaste.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.arrowBack
import org.koin.compose.koinInject

@Composable
fun WindowDecoration(title: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(MaterialTheme.colorScheme.background.copy(0.64f)),
    ) {
        DecorationUI(title)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun DecorationUI(title: String) {
    val currentScreenContext = LocalPageViewContent.current

    val copywriter = koinInject<GlobalCopywriter>()

    var hoverReturn by remember { mutableStateOf(false) }

    Box {
        Column(
            modifier =
                Modifier.wrapContentSize()
                    .padding(start = 10.dp, end = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier =
                        Modifier.wrapContentSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (hoverReturn) {
                                    MaterialTheme.colorScheme.background
                                } else {
                                    Color.Transparent
                                },
                            )
                            .onPointerEvent(
                                eventType = PointerEventType.Enter,
                                onEvent = {
                                    hoverReturn = true
                                },
                            )
                            .onPointerEvent(
                                eventType = PointerEventType.Exit,
                                onEvent = {
                                    hoverReturn = false
                                },
                            )
                            .onClick { currentScreenContext.value = currentScreenContext.value.returnNext() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(modifier = Modifier.width(10.dp).height(40.dp))
                    Icon(
                        painter = arrowBack(),
                        contentDescription = "return",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    Text(
                        text = copywriter.getText("return_"),
                        style =
                            TextStyle(
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 22.sp,
                            ),
                    )
                    Spacer(modifier = Modifier.width(10.dp).height(40.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    modifier = Modifier,
                    text = copywriter.getText(title),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 22.sp,
                    style = TextStyle(fontWeight = FontWeight.Bold),
                    fontFamily = FontFamily.SansSerif,
                )
            }
        }
    }
}
