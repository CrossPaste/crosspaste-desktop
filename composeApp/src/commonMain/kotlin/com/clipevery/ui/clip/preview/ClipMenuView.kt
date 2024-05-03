package com.clipevery.ui.clip.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.clipevery.LocalKoinApplication
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipData
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.ClipIconButton
import com.clipevery.ui.base.MenuItem
import com.clipevery.ui.base.getMenWidth
import com.clipevery.ui.base.starRegular
import com.clipevery.ui.base.starSolid
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipMenuView(clipData: ClipData) {
    val current = LocalKoinApplication.current
    val density = LocalDensity.current
    val clipDao = current.koin.get<ClipDao>()
    val copywriter = current.koin.get<GlobalCopywriter>()

    var showPopup by remember { mutableStateOf(false) }

    var buttonPosition by remember { mutableStateOf(Offset.Zero) }
    var buttonSize by remember { mutableStateOf(Size(0.0f, 0.0f)) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ClipIconButton(
            radius = 12.dp,
            onClick = {
                showPopup = !showPopup
            },
            modifier =
                Modifier
                    .background(Color.Transparent, CircleShape)
                    .onGloballyPositioned { coordinates ->
                        buttonPosition = coordinates.localToWindow(Offset.Zero)
                        buttonSize = coordinates.size.toSize()
                    },
        ) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = "info",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colors.primary,
            )
        }

        if (showPopup) {
            Popup(
                alignment = Alignment.TopEnd,
                offset =
                    IntOffset(
                        with(density) { ((-40).dp).roundToPx() },
                        with(density) { (10.dp).roundToPx() },
                    ),
                onDismissRequest = {
                    if (showPopup) {
                        showPopup = false
                    }
                },
                properties =
                    PopupProperties(
                        focusable = true,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true,
                    ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .wrapContentSize()
                            .background(Color.Transparent)
                            .shadow(15.dp),
                ) {
                    val menuTexts =
                        arrayOf(
                            copywriter.getText(getTypeText(clipData.clipType)),
                            copywriter.getText(if (clipData.favorite) "Delete_Favorite" else "Favorite"),
                            copywriter.getText("Delete"),
                            copywriter.getText("Remove_Device"),
                        )

                    val maxWidth = getMenWidth(menuTexts)

                    Column(
                        modifier =
                            Modifier
                                .width(maxWidth)
                                .wrapContentHeight()
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colors.surface),
                    ) {
                        MenuItem(copywriter.getText(getTypeText(clipData.clipType)), enabledInteraction = false) {}

                        Divider()

                        MenuItem(copywriter.getText(if (clipData.favorite) "Delete_Favorite" else "Favorite")) {
                            clipDao.setFavorite(clipData.id, !clipData.favorite)
                            showPopup = false
                        }
                        MenuItem(copywriter.getText("Delete")) {
                            runBlocking {
                                clipDao.markDeleteClipData(clipData.id)
                            }
                            showPopup = false
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            modifier =
                Modifier.size(16.dp).onClick {
                    clipDao.setFavorite(clipData.id, !clipData.favorite)
                },
            painter = if (clipData.favorite) starSolid() else starRegular(),
            contentDescription = "Favorite",
            tint = if (clipData.favorite) Color(0xFFFFCE34) else MaterialTheme.colors.onSurface,
        )
    }
}
