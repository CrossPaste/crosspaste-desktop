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
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.arrowBack
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WindowDecoration(title: String) {
    val appSize = koinInject<DesktopAppSize>()
    val appWindowManager = koinInject<AppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()

    var hoverReturn by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(appSize.windowDecorationHeight)
                .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        Box {
            Column(
                modifier =
                    Modifier.wrapContentSize()
                        .padding(start = small3X, end = large2X),
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
                                .clip(tiny2XRoundedCornerShape)
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
                        Spacer(modifier = Modifier.width(small3X).height(40.dp))
                        Icon(
                            painter = arrowBack(),
                            contentDescription = "return",
                            modifier = Modifier.size(large2X),
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
                        Spacer(modifier = Modifier.width(small3X).height(40.dp))
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
