package com.crosspaste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.crosspaste.app.AppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.arrowBack
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WindowDecoration(title: String) {
    val appWindowManager = koinInject<AppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()

    var hoverReturn by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
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
                    val returnBackground =
                        if (hoverReturn) {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }

                    Row(
                        modifier =
                            Modifier.wrapContentSize()
                                .clip(RoundedCornerShape(6.dp))
                                .background(returnBackground)
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
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            appWindowManager.returnScreen()
                                        },
                                    )
                                },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(modifier = Modifier.width(10.dp).height(40.dp))
                        Icon(
                            painter = arrowBack(),
                            contentDescription = "return",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.contentColorFor(returnBackground),
                        )

                        Text(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            text = copywriter.getText("return_"),
                            color = MaterialTheme.colorScheme.contentColorFor(returnBackground),
                            style =
                                MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Light,
                                    lineHeight = TextUnit.Unspecified,
                                ),
                        )
                        Spacer(modifier = Modifier.width(10.dp).height(40.dp))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        modifier = Modifier,
                        text = copywriter.getText(title),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style =
                            MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                    )
                }
            }
        }
    }
}
