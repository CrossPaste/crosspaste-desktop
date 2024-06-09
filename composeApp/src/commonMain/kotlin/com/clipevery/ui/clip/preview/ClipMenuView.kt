package com.clipevery.ui.clip.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.onClick
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.areAnyPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.ClipboardService
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipData
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.MenuItem
import com.clipevery.ui.base.MessageType
import com.clipevery.ui.base.Toast
import com.clipevery.ui.base.ToastManager
import com.clipevery.ui.base.copy
import com.clipevery.ui.base.getMenWidth
import com.clipevery.ui.base.starRegular
import com.clipevery.ui.base.starSolid
import com.clipevery.ui.favoriteColor
import com.clipevery.ui.search.ClipTypeIconView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ClipMenuView(
    clipData: ClipData,
    toShow: (Boolean) -> Unit,
) {
    val current = LocalKoinApplication.current
    val density = LocalDensity.current
    val clipDao = current.koin.get<ClipDao>()
    val clipboardService = current.koin.get<ClipboardService>()
    val copywriter = current.koin.get<GlobalCopywriter>()
    val toastManager = current.koin.get<ToastManager>()

    var parentBounds by remember { mutableStateOf(Rect.Zero) }
    var cursorPosition by remember { mutableStateOf(Offset.Zero) }
    var showMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var job: Job? by remember { mutableStateOf(null) }

    var showPopup by remember { mutableStateOf(false) }

    fun startShowing() {
        if (job?.isActive == true) { // Don't restart the job if it's already active
            return
        }
        job =
            scope.launch {
                showMenu = true
                toShow(true)
            }
    }

    fun hide() {
        job?.cancel()
        job = null
        showMenu = false
        toShow(false)
    }

    fun hideIfNotHovered(globalPosition: Offset) {
        if (!parentBounds.contains(globalPosition)) {
            hide()
        }
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .onGloballyPositioned { parentBounds = it.boundsInWindow() }
                .onPointerEvent(PointerEventType.Enter) {
                    cursorPosition = it.position
                    if (!showMenu && !it.buttons.areAnyPressed) {
                        startShowing()
                    }
                }
                .onPointerEvent(PointerEventType.Move) {
                    cursorPosition = it.position
                    if (!showMenu && !it.buttons.areAnyPressed) {
                        startShowing()
                    }
                }
                .onPointerEvent(PointerEventType.Exit) {
                    hideIfNotHovered(parentBounds.topLeft + it.position)
                }
                .background(if (showMenu) MaterialTheme.colors.surface.copy(alpha = 0.12f) else Color.Transparent)
                .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            Icons.Outlined.MoreVert,
            contentDescription = "info",
            modifier =
                Modifier.size(18.dp)
                    .clickable {
                        showPopup = !showPopup
                    },
            tint = MaterialTheme.colors.primary,
        )

        if (showPopup) {
            Popup(
                alignment = Alignment.TopEnd,
                offset =
                    IntOffset(
                        with(density) { ((-30).dp).roundToPx() },
                        with(density) { (5.dp).roundToPx() },
                    ),
                onDismissRequest = {
                    if (showPopup) {
                        showPopup = false
                        showMenu = false
                        toShow(false)
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
                            showMenu = false
                            toShow(false)
                        }
                        MenuItem(copywriter.getText("Delete")) {
                            runBlocking {
                                clipDao.markDeleteClipData(clipData.id)
                            }
                            showPopup = false
                            showMenu = false
                            toShow(false)
                        }
                    }
                }
            }
        }

        if (showMenu) {
            ClipTypeIconView(clipData)

            Icon(
                modifier =
                    Modifier.size(16.dp).onClick {
                        runBlocking {
                            clipboardService.tryWriteClipboard(clipData, localOnly = true, filterFile = false)
                        }
                        toastManager.setToast(
                            Toast(
                                MessageType.Success,
                                copywriter.getText("Copy_Successful"),
                                3000,
                            ),
                        )
                    },
                painter = copy(),
                contentDescription = "Copy",
                tint = MaterialTheme.colors.onBackground,
            )

            Icon(
                modifier =
                    Modifier.size(16.dp).onClick {
                        clipDao.setFavorite(clipData.id, !clipData.favorite)
                    },
                painter = if (clipData.favorite) starSolid() else starRegular(),
                contentDescription = "Favorite",
                tint = favoriteColor(),
            )
        }
    }
}

private val PointerEvent.position get() = changes.first().position
