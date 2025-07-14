package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.app.AppControl
import com.crosspaste.app.AppSize
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteData
import com.crosspaste.i18n.Copywriter
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.ui.base.MenuItemView
import com.crosspaste.ui.base.PasteTooltipAreaView
import com.crosspaste.ui.base.PasteTypeIconView
import com.crosspaste.ui.base.clipboard
import com.crosspaste.ui.base.favorite
import com.crosspaste.ui.base.measureTextWidth
import com.crosspaste.ui.base.moreVertical
import com.crosspaste.ui.base.noFavorite
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.getFontWidth
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.DesktopAppUIFont
import com.crosspaste.utils.DateUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun PasteMenuView(
    pasteData: PasteData,
    toShow: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val appControl = koinInject<AppControl>()
    val pasteDao = koinInject<PasteDao>()
    val pasteMenuService = koinInject<DesktopPasteMenuService>()

    var parentBounds by remember { mutableStateOf(Rect.Zero) }
    var showMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var job: Job? by remember { mutableStateOf(null) }

    var showPopup by remember { mutableStateOf(false) }

    var hoverMenu by remember { mutableStateOf(false) }
    var hoverCopy by remember { mutableStateOf(false) }
    var hoverFavorite by remember { mutableStateOf(false) }
    var currentFavorite by remember(pasteData.id) { mutableStateOf(pasteData.favorite) }
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
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { parentBounds = it.boundsInWindow() }
                .onPointerEvent(PointerEventType.Enter) {
                    if (!showMenu && !it.buttons.areAnyPressed) {
                        startShowing()
                    }
                }.onPointerEvent(PointerEventType.Move) {
                    if (!showMenu && !it.buttons.areAnyPressed) {
                        startShowing()
                    }
                }.onPointerEvent(PointerEventType.Exit) {
                    hideIfNotHovered(parentBounds.topLeft + it.position)
                }.clip(tiny2XRoundedCornerShape)
                .background(if (showMenu) AppUIColors.generalBackground else Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        MoreMenuItem(
            background =
                if (hoverMenu) {
                    AppUIColors.topBackground
                } else {
                    Color.Transparent
                },
            hoverMenu = { hoverMenu = it },
        ) {
            showPopup = !showPopup
        }

        if (showMenu) {
            CopyMenuItem(
                tint = AppUIColors.importantColor,
                background =
                    if (hoverCopy) {
                        AppUIColors.topBackground
                    } else {
                        Color.Transparent
                    },
                hoverCopy = { hoverCopy = it },
            ) {
                pasteMenuService.copyPasteData(pasteData)
            }

            FavoriteMenuItem(
                currentFavorite = currentFavorite,
                tint = AppUIColors.importantColor,
                background =
                    if (hoverFavorite) {
                        AppUIColors.topBackground
                    } else {
                        Color.Transparent
                    },
                hoverFavorite = { hoverFavorite = it },
            ) {
                if (appControl.isFavoriteEnabled()) {
                    pasteDao.setFavorite(
                        pasteData.id,
                        !currentFavorite,
                    )
                    currentFavorite = !currentFavorite
                }
            }

            DetailMenuItem(
                pasteData = pasteData,
                tint = AppUIColors.importantColor,
                background =
                    if (hoverSource) {
                        AppUIColors.topBackground
                    } else {
                        Color.Transparent
                    },
            ) {
                hoverSource = it
            }
        }
    }

    if (showPopup) {
        Popup(
            alignment = Alignment.TopEnd,
            offset =
                IntOffset(
                    with(density) { ((-40).dp).roundToPx() },
                    with(density) { tiny2X.roundToPx() },
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
            MoreMenuItems(pasteData) {
                showPopup = false
                showMenu = false
                toShow(false)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun MoreMenuItem(
    background: Color,
    hoverMenu: (Boolean) -> Unit,
    switchPopup: () -> Unit,
) {
    val appSize = koinInject<AppSize>()
    val copywriter = koinInject<GlobalCopywriter>()
    val menuText = copywriter.getText("menu")

    PasteTooltipAreaView(
        Modifier
            .fillMaxWidth()
            .height(appSize.mainPasteSize.height / 4),
        text = menuText,
        computeTooltipPlacement = {
            val textWidth = measureTextWidth(menuText, DesktopAppUIFont.tipsTextStyle)
            TooltipPlacement.ComponentRect(
                anchor = Alignment.BottomStart,
                alignment = Alignment.BottomEnd,
                offset = DpOffset(-textWidth - medium, (-20).dp),
            )
        },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onPointerEvent(
                        eventType = PointerEventType.Enter,
                        onEvent = {
                            hoverMenu(true)
                        },
                    ).onPointerEvent(
                        eventType = PointerEventType.Exit,
                        onEvent = {
                            hoverMenu(false)
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(tiny2XRoundedCornerShape)
                        .background(background),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = moreVertical(),
                    contentDescription = "info",
                    modifier =
                        Modifier
                            .size(large)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        switchPopup()
                                    },
                                )
                            },
                    tint = AppUIColors.importantColor,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun CopyMenuItem(
    tint: Color,
    background: Color,
    hoverCopy: (Boolean) -> Unit,
    copyPasteDataAction: () -> Unit,
) {
    val appSize = koinInject<AppSize>()
    val copywriter = koinInject<GlobalCopywriter>()
    val copyText = copywriter.getText("copy")

    PasteTooltipAreaView(
        Modifier
            .fillMaxWidth()
            .height(appSize.mainPasteSize.height / 4),
        text = copyText,
        computeTooltipPlacement = {
            val textWidth = measureTextWidth(copyText, DesktopAppUIFont.tipsTextStyle)
            TooltipPlacement.ComponentRect(
                anchor = Alignment.BottomStart,
                alignment = Alignment.BottomEnd,
                offset = DpOffset(-textWidth - medium, (-20).dp),
            )
        },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onPointerEvent(
                        eventType = PointerEventType.Enter,
                        onEvent = {
                            hoverCopy(true)
                        },
                    ).onPointerEvent(
                        eventType = PointerEventType.Exit,
                        onEvent = {
                            hoverCopy(false)
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(tiny2XRoundedCornerShape)
                        .background(background),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier =
                        Modifier
                            .size(medium)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        copyPasteDataAction()
                                    },
                                )
                            },
                    painter = clipboard(),
                    contentDescription = "Copy",
                    tint = tint,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun FavoriteMenuItem(
    currentFavorite: Boolean,
    tint: Color,
    background: Color,
    hoverFavorite: (Boolean) -> Unit,
    setFavorite: () -> Unit,
) {
    val appSize = koinInject<AppSize>()
    val copywriter = koinInject<GlobalCopywriter>()
    val favoriteText = copywriter.getText(if (currentFavorite) "remove_from_favorites" else "favorite")

    PasteTooltipAreaView(
        Modifier
            .fillMaxWidth()
            .height(appSize.mainPasteSize.height / 4),
        text = favoriteText,
        computeTooltipPlacement = {
            val textWidth = measureTextWidth(favoriteText, DesktopAppUIFont.tipsTextStyle)
            TooltipPlacement.ComponentRect(
                anchor = Alignment.BottomStart,
                alignment = Alignment.BottomEnd,
                offset = DpOffset(-textWidth - medium, (-20).dp),
            )
        },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onPointerEvent(
                        eventType = PointerEventType.Enter,
                        onEvent = {
                            hoverFavorite(true)
                        },
                    ).onPointerEvent(
                        eventType = PointerEventType.Exit,
                        onEvent = {
                            hoverFavorite(false)
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(tiny2XRoundedCornerShape)
                        .background(background),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier =
                        Modifier
                            .size(medium)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        setFavorite()
                                    },
                                )
                            },
                    painter = if (currentFavorite) favorite() else noFavorite(),
                    contentDescription = "Favorite",
                    tint = tint,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun DetailMenuItem(
    pasteData: PasteData,
    tint: Color,
    background: Color,
    hoverSource: (Boolean) -> Unit,
) {
    val appSize = koinInject<AppSize>()
    val copywriter = koinInject<GlobalCopywriter>()
    val detailInfo = getDetailInfo(copywriter, pasteData)
    PasteTooltipAreaView(
        Modifier
            .fillMaxWidth()
            .height(appSize.mainPasteSize.height / 4),
        text = detailInfo,
        computeTooltipPlacement = {
            val textWidth = measureTextWidth(detailInfo, DesktopAppUIFont.tipsTextStyle)
            TooltipPlacement.ComponentRect(
                anchor = Alignment.BottomStart,
                alignment = Alignment.BottomEnd,
                offset = DpOffset(-textWidth - medium, -xxLarge),
            )
        },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onPointerEvent(
                        eventType = PointerEventType.Enter,
                        onEvent = {
                            hoverSource(true)
                        },
                    ).onPointerEvent(
                        eventType = PointerEventType.Exit,
                        onEvent = {
                            hoverSource(false)
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(tiny2XRoundedCornerShape)
                        .background(background),
                contentAlignment = Alignment.Center,
            ) {
                PasteTypeIconView(
                    pasteData = pasteData,
                    tint = tint,
                    background = background,
                    size = medium,
                )
            }
        }
    }
}

private val PointerEvent.position get() = changes.first().position

@Composable
fun MoreMenuItems(
    pasteData: PasteData,
    hideMore: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteMenuService = koinInject<DesktopPasteMenuService>()
    Box(
        modifier =
            Modifier
                .wrapContentSize()
                .background(Color.Transparent)
                .shadow(small),
    ) {
        val menuTexts =
            listOf(
                copywriter.getText("open"),
                copywriter.getText("delete"),
            )

        val maxWidth = getFontWidth(menuTexts)

        Column(
            modifier =
                Modifier
                    .width(maxWidth)
                    .wrapContentHeight()
                    .clip(tiny2XRoundedCornerShape)
                    .background(AppUIColors.menuBackground),
        ) {
            MenuItemView(copywriter.getText("open")) {
                pasteMenuService.openPasteData(pasteData)
                hideMore()
            }
            MenuItemView(copywriter.getText("delete")) {
                pasteMenuService.deletePasteData(pasteData)
                hideMore()
            }
        }
    }
}

fun getDetailInfo(
    copywriter: Copywriter,
    pasteData: PasteData,
): String {
    val infos = mutableListOf<String>()
    pasteData.source?.let {
        infos.add(
            "${copywriter.getText("source")}: $it",
        )
    }
    val typeText = pasteData.getTypeText()
    infos.add(
        "${copywriter.getText("type")}: ${copywriter.getText(typeText)}",
    )
    pasteData.createTime.let {
        infos.add(
            "${copywriter.getText("create_time")}: ${copywriter.getDate(
                DateUtils.epochMillisecondsToLocalDateTime(it),
            )}",
        )
    }
    return infos.joinToString("\n")
}
