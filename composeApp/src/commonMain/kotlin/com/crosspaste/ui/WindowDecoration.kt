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
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import com.crosspaste.LocalKoinApplication
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.arrowBack

@Composable
fun WindowDecoration(
    currentPageViewContext: MutableState<PageViewContext>,
    title: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(MaterialTheme.colors.background.copy(0.64f)),
    ) {
        DecorationUI(currentPageViewContext, title)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun DecorationUI(
    currentPageViewContext: MutableState<PageViewContext>,
    title: String,
) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()

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
                                    MaterialTheme.colors.background
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
                            .onClick { currentPageViewContext.value = currentPageViewContext.value.returnNext() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(modifier = Modifier.width(10.dp).height(40.dp))
                    Icon(
                        painter = arrowBack(),
                        contentDescription = "return",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colors.primary,
                    )

                    Text(
                        text = copywriter.getText("return_"),
                        style =
                            TextStyle(
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colors.primary,
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
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 22.sp,
                    style = TextStyle(fontWeight = FontWeight.Bold),
                    fontFamily = FontFamily.SansSerif,
                )
            }
        }
    }
}
