package com.crosspaste.ui.base

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.i18n.GlobalCopywriter
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ExpandView(
    title: String,
    defaultExpand: Boolean = false,
    content: @Composable () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    var hover by remember { mutableStateOf(false) }
    var expand by remember { mutableStateOf(defaultExpand) }

    val backgroundColor =
        if (hover) {
            MaterialTheme.colors.surface
        } else {
            Color.Transparent
        }

    val languageArrow: Painter =
        if (expand) {
            arrowDown()
        } else {
            arrowLeft()
        }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .wrapContentHeight(),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(42.dp)
                    .padding(horizontal = 10.dp)
                    .combinedClickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                        onClick = {
                            expand = !expand
                        },
                    )
                    .onPointerEvent(
                        eventType = PointerEventType.Enter,
                        onEvent = {
                            hover = true
                        },
                    )
                    .onPointerEvent(
                        eventType = PointerEventType.Exit,
                        onEvent = {
                            hover = false
                        },
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .height(26.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(backgroundColor),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val paddingValue = if (expand || hover) 10.dp else 5.dp
                val animatedPadding = animateDpAsState(targetValue = paddingValue)

                Row(
                    modifier =
                        Modifier.wrapContentSize()
                            .padding(horizontal = animatedPadding.value),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        modifier = Modifier.size(15.dp),
                        painter = languageArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colors.onBackground,
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = copywriter.getText(title),
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif,
                        style = TextStyle(fontWeight = FontWeight.Light),
                    )
                }
            }
        }

        if (expand) {
            content()
        }
    }
}
