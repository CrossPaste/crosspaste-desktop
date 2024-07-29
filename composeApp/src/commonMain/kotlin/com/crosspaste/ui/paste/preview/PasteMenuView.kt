package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.LocalKoinApplication
import com.crosspaste.app.AppWindowManager
import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteboardService
import com.crosspaste.ui.base.MenuItem
import com.crosspaste.ui.base.MessageType
import com.crosspaste.ui.base.NotificationManager
import com.crosspaste.ui.base.PasteTooltipAreaView
import com.crosspaste.ui.base.TOOLTIP_TEXT_STYLE
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.clipboard
import com.crosspaste.ui.base.favorite
import com.crosspaste.ui.base.getMenWidth
import com.crosspaste.ui.base.noFavorite
import com.crosspaste.ui.devices.measureTextWidth
import com.crosspaste.ui.favoriteColor
import com.crosspaste.ui.search.PasteTypeIconView
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun PasteMenuView(
    pasteData: PasteData,
    toShow: (Boolean) -> Unit,
) {
    val current = LocalKoinApplication.current
    val density = LocalDensity.current
    val pasteDao = current.koin.get<PasteDao>()
    val appWindowManager = current.koin.get<AppWindowManager>()
    val pasteboardService = current.koin.get<PasteboardService>()
    val copywriter = current.koin.get<GlobalCopywriter>()
    val notificationManager = current.koin.get<NotificationManager>()
    val uiSupport = current.koin.get<UISupport>()

    var parentBounds by remember { mutableStateOf(Rect.Zero) }
    var cursorPosition by remember { mutableStateOf(Offset.Zero) }
    var showMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var job: Job? by remember { mutableStateOf(null) }

    var showPopup by remember { mutableStateOf(false) }

    var hoverMenu by remember { mutableStateOf(false) }
    var hoverCopy by remember { mutableStateOf(false) }
    var hoverFavorite by remember { mutableStateOf(false) }
    var hoverSource by remember { mutableStateOf(false) }

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
                .clip(RoundedCornerShape(5.dp))
                .background(if (showMenu) MaterialTheme.colors.surface.copy(0.72f) else Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        val menuText = copywriter.getText("menu")

        PasteTooltipAreaView(
            Modifier.fillMaxWidth().height(25.dp),
            text = menuText,
            computeTooltipPlacement = {
                val textWidth = measureTextWidth(menuText, TOOLTIP_TEXT_STYLE)
                TooltipPlacement.ComponentRect(
                    anchor = Alignment.BottomStart,
                    alignment = Alignment.BottomEnd,
                    offset = DpOffset(-textWidth - 16.dp, (-20).dp),
                )
            },
        ) {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .onPointerEvent(
                            eventType = PointerEventType.Enter,
                            onEvent = {
                                hoverMenu = true
                            },
                        )
                        .onPointerEvent(
                            eventType = PointerEventType.Exit,
                            onEvent = {
                                hoverMenu = false
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (hoverMenu) {
                                    MaterialTheme.colors.background
                                } else {
                                    Color.Transparent
                                },
                            ),
                ) {}
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "info",
                    modifier =
                        Modifier.size(18.dp)
                            .onClick {
                                showPopup = !showPopup
                            },
                    tint = MaterialTheme.colors.primary,
                )
            }
        }

        val copyText = copywriter.getText("copy")

        if (showMenu) {
            PasteTooltipAreaView(
                Modifier.fillMaxWidth().height(25.dp),
                text = copyText,
                computeTooltipPlacement = {
                    val textWidth = measureTextWidth(copyText, TOOLTIP_TEXT_STYLE)
                    TooltipPlacement.ComponentRect(
                        anchor = Alignment.BottomStart,
                        alignment = Alignment.BottomEnd,
                        offset = DpOffset(-textWidth - 16.dp, (-20).dp),
                    )
                },
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .onPointerEvent(
                                eventType = PointerEventType.Enter,
                                onEvent = {
                                    hoverCopy = true
                                },
                            )
                            .onPointerEvent(
                                eventType = PointerEventType.Exit,
                                onEvent = {
                                    hoverCopy = false
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (hoverCopy) {
                                        MaterialTheme.colors.background
                                    } else {
                                        Color.Transparent
                                    },
                                ),
                    ) {}

                    Icon(
                        modifier =
                            Modifier.size(16.dp).onClick {
                                appWindowManager.setMainCursorWait()
                                scope.launch(ioDispatcher) {
                                    pasteboardService.tryWritePasteboard(
                                        pasteData,
                                        localOnly = true,
                                        filterFile = false,
                                    )
                                    withContext(mainDispatcher) {
                                        appWindowManager.resetMainCursor()
                                        notificationManager.addNotification(
                                            copywriter.getText("copy_successful"),
                                            MessageType.Success,
                                        )
                                    }
                                }
                            },
                        painter = clipboard(),
                        contentDescription = "Copy",
                        tint = MaterialTheme.colors.primary,
                    )
                }
            }

            val favoriteText = copywriter.getText(if (pasteData.favorite) "delete_favorite" else "favorite")

            PasteTooltipAreaView(
                Modifier.fillMaxWidth().height(25.dp),
                text = favoriteText,
                computeTooltipPlacement = {
                    val textWidth = measureTextWidth(favoriteText, TOOLTIP_TEXT_STYLE)
                    TooltipPlacement.ComponentRect(
                        anchor = Alignment.BottomStart,
                        alignment = Alignment.BottomEnd,
                        offset = DpOffset(-textWidth - 16.dp, (-20).dp),
                    )
                },
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .onPointerEvent(
                                eventType = PointerEventType.Enter,
                                onEvent = {
                                    hoverFavorite = true
                                },
                            )
                            .onPointerEvent(
                                eventType = PointerEventType.Exit,
                                onEvent = {
                                    hoverFavorite = false
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (hoverFavorite) {
                                        MaterialTheme.colors.background
                                    } else {
                                        Color.Transparent
                                    },
                                ),
                    ) {}

                    Icon(
                        modifier =
                            Modifier.size(16.dp).onClick {
                                pasteDao.setFavorite(pasteData.id, !pasteData.favorite)
                            },
                        painter = if (pasteData.favorite) favorite() else noFavorite(),
                        contentDescription = "Favorite",
                        tint = favoriteColor(),
                    )
                }
            }

            val sourceAndTypeText = getSourceAndTypeText(copywriter, pasteData)
            PasteTooltipAreaView(
                Modifier.fillMaxWidth().height(25.dp),
                text = sourceAndTypeText,
                computeTooltipPlacement = {
                    val textWidth = measureTextWidth(sourceAndTypeText, TOOLTIP_TEXT_STYLE)
                    TooltipPlacement.ComponentRect(
                        anchor = Alignment.BottomStart,
                        alignment = Alignment.BottomEnd,
                        offset = DpOffset(-textWidth - 16.dp, (-30).dp),
                    )
                },
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .onPointerEvent(
                                eventType = PointerEventType.Enter,
                                onEvent = {
                                    hoverSource = true
                                },
                            )
                            .onPointerEvent(
                                eventType = PointerEventType.Exit,
                                onEvent = {
                                    hoverSource = false
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (hoverSource) {
                                        MaterialTheme.colors.background
                                    } else {
                                        Color.Transparent
                                    },
                                ),
                    ) {}
                    PasteTypeIconView(pasteData, size = 16.dp)
                }
            }
        }
    }

    if (showPopup) {
        Popup(
            alignment = Alignment.TopEnd,
            offset =
                IntOffset(
                    with(density) { ((-40).dp).roundToPx() },
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
                        copywriter.getText("open"),
                        copywriter.getText("delete"),
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
                    MenuItem(copywriter.getText("open")) {
                        uiSupport.openPasteData(pasteData)
                        showPopup = false
                        showMenu = false
                        toShow(false)
                    }
                    MenuItem(copywriter.getText("delete")) {
                        runBlocking {
                            pasteDao.markDeletePasteData(pasteData.id)
                        }
                        showPopup = false
                        showMenu = false
                        toShow(false)
                    }
                }
            }
        }
    }
}

private val PointerEvent.position get() = changes.first().position
