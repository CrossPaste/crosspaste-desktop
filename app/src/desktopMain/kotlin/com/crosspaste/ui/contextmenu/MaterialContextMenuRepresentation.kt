package com.crosspaste.ui.contextmenu

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.rememberPopupPositionProviderAtPosition
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Chevron_right
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.scan

/**
 * Material-styled replacement for `io.github.dzirbel:compose-material-context-menu`. The upstream
 * library stalled at 0.3.1 and crashes under Compose Multiplatform 1.11 (its `TextManager` API is
 * gone); we re-implement only the slice CrossPaste uses: a [ContextMenuRepresentation] that
 * renders dividers, nested groups, and items with optional leading/trailing icons.
 */
@ExperimentalComposeUiApi
@Composable
@Suppress("ComposableNaming")
fun MaterialContextMenuRepresentation(
    measurements: ContextMenuMeasurements = ContextMenuMeasurements(),
    colors: ContextMenuColors =
        ContextMenuColors(
            surface = MaterialTheme.colorScheme.surface,
            text = MaterialTheme.colorScheme.onSurface,
            itemHover = MaterialTheme.colorScheme.primary,
            itemHoverText = MaterialTheme.colorScheme.onPrimary,
        ),
    showScrollbarOnOverflow: Boolean = true,
): ContextMenuRepresentation =
    MaterialContextMenuRepresentation(
        params =
            ContextMenuParams(
                measurements = measurements,
                colors = colors,
                showScrollbarOnOverflow = showScrollbarOnOverflow,
            ),
    )

@ExperimentalComposeUiApi
class MaterialContextMenuRepresentation(
    private val params: ContextMenuParams,
) : ContextMenuRepresentation {
    @Composable
    override fun Representation(
        state: ContextMenuState,
        items: () -> List<ContextMenuItem>,
    ) {
        val status = state.status
        if (status !is ContextMenuState.Status.Open) return

        val popupPositionProvider =
            rememberPopupPositionProviderAtPosition(
                positionPx = status.rect.topLeft,
                windowMargin = params.measurements.windowMargin,
            )

        ContextMenuPopup(
            params = params,
            popupPositionProvider = popupPositionProvider,
            onDismissRequest = { state.status = ContextMenuState.Status.Closed },
            items = items,
        )
    }
}

@Composable
private fun ContextMenuPopup(
    params: ContextMenuParams,
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: () -> Unit,
    items: () -> List<ContextMenuItem>,
) {
    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = onDismissRequest,
    ) {
        Surface(
            shape = params.measurements.popupShape,
            color = params.colors.surface,
            shadowElevation = params.measurements.elevation,
        ) {
            val scrollState = rememberScrollState()
            OptionalVerticalScroll(
                scrollState = scrollState,
                includeScrollbarWhenUsed = params.showScrollbarOnOverflow,
            ) {
                Column(
                    modifier =
                        Modifier
                            .width(IntrinsicSize.Max)
                            .padding(
                                top = params.measurements.menuTopPadding,
                                bottom = params.measurements.menuBottomPadding,
                            ).verticalScroll(scrollState),
                ) {
                    val resolvedItems = remember { items() }

                    val hoverInteractionSources =
                        remember(resolvedItems.size) {
                            List(resolvedItems.size) { MutableInteractionSource() }
                        }
                    val hoveredItem =
                        remember(hoverInteractionSources) {
                            hoverInteractionSources.hoveredIndex()
                        }.collectAsState(initial = -1)

                    resolvedItems.forEachIndexed { index, item ->
                        ContextMenuItemContent(
                            item = item,
                            params = params,
                            interactionSource = hoverInteractionSources[index],
                            onDismissRequest = onDismissRequest,
                            menuOpen = item is ContextMenuGroup && hoveredItem.value == index,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextMenuItemContent(
    item: ContextMenuItem,
    params: ContextMenuParams,
    interactionSource: MutableInteractionSource,
    onDismissRequest: () -> Unit,
    menuOpen: Boolean,
) {
    when (item) {
        is ContextMenuDivider -> DividerItem(params = params)
        is GenericContextMenuItem ->
            item.Content(
                onDismissRequest = onDismissRequest,
                params = params,
                modifier = Modifier.hoverable(interactionSource),
            )
        is ContextMenuGroup ->
            GroupItem(
                item = item,
                params = params,
                interactionSource = interactionSource,
                menuOpen = menuOpen,
                onDismissRequest = onDismissRequest,
            )
        is MaterialContextMenuItem ->
            MaterialItem(
                item = item,
                params = params,
                interactionSource = interactionSource,
                onDismissRequest = onDismissRequest,
            )
        else ->
            DefaultItem(
                item = item,
                params = params,
                interactionSource = interactionSource,
                onDismissRequest = onDismissRequest,
            )
    }
}

@Composable
private fun MaterialItem(
    item: MaterialContextMenuItem,
    params: ContextMenuParams,
    interactionSource: MutableInteractionSource,
    onDismissRequest: () -> Unit,
) {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val textColor = if (isHovered) params.colors.itemHoverText else params.colors.text
    Box(
        modifier =
            Modifier
                .itemRow(
                    params = params,
                    isHovered = isHovered,
                    interactionSource = interactionSource,
                ).clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    onDismissRequest()
                    item.onClick()
                }.padding(params.measurements.itemPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(params.measurements.iconPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.leadingIcon?.invoke()
            Box(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item.trailingIcon?.invoke()
        }
    }
}

@Composable
private fun GroupItem(
    item: ContextMenuGroup,
    params: ContextMenuParams,
    interactionSource: MutableInteractionSource,
    menuOpen: Boolean,
    onDismissRequest: () -> Unit,
) {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val highlighted = isHovered || menuOpen
    val textColor = if (highlighted) params.colors.itemHoverText else params.colors.text

    Box(
        modifier =
            Modifier.itemRow(
                params = params,
                isHovered = highlighted,
                interactionSource = interactionSource,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(params.measurements.itemPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = item.label,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(
                imageVector = MaterialSymbols.Rounded.Chevron_right,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(params.measurements.submenuIconSize),
            )
        }

        if (menuOpen) {
            ContextMenuPopup(
                params = params,
                popupPositionProvider =
                    rememberNestedDropdownPositionProvider(
                        windowMargin = params.measurements.windowMargin,
                        menuTopPadding = params.measurements.menuTopPadding,
                        menuBottomPadding = params.measurements.menuBottomPadding,
                    ),
                onDismissRequest = {
                    if (!isHovered) onDismissRequest()
                },
                items = item.items,
            )
        }
    }
}

@Composable
private fun DefaultItem(
    item: ContextMenuItem,
    params: ContextMenuParams,
    interactionSource: MutableInteractionSource,
    onDismissRequest: () -> Unit,
) {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val textColor = if (isHovered) params.colors.itemHoverText else params.colors.text
    Box(
        modifier =
            Modifier
                .itemRow(
                    params = params,
                    isHovered = isHovered,
                    interactionSource = interactionSource,
                ).clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    onDismissRequest()
                    item.onClick()
                }.padding(params.measurements.itemPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            modifier = Modifier.wrapContentHeight(),
            text = item.label,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DividerItem(params: ContextMenuParams) {
    Layout(
        modifier =
            Modifier
                .itemWidth(params)
                .drawWithContent {
                    val width: Float = params.measurements.dividerLineHeight.toPx()
                    val y: Float = (size.height - width) / 2
                    drawLine(
                        color = params.colors.divider,
                        start = Offset(x = 0f, y = y),
                        end = Offset(x = size.width, y = y),
                        strokeWidth = width,
                    )
                },
        measurePolicy = { _, constraints ->
            layout(
                width = constraints.minWidth,
                height = params.measurements.dividerHeight.roundToPx(),
            ) {}
        },
        content = {},
    )
}

@Stable
private fun Modifier.itemWidth(params: ContextMenuParams): Modifier =
    fillMaxWidth().widthIn(min = params.measurements.minWidth, max = params.measurements.maxWidth)

private fun Modifier.itemRow(
    params: ContextMenuParams,
    isHovered: Boolean,
    interactionSource: MutableInteractionSource,
): Modifier =
    this
        .itemWidth(params)
        .padding(horizontal = params.measurements.itemHorizontalMargin)
        .clip(params.measurements.itemShape)
        .background(if (isHovered) params.colors.itemHover else Color.Transparent)
        .heightIn(min = params.measurements.itemMinHeight)
        .hoverable(interactionSource)

@Composable
private fun OptionalVerticalScroll(
    scrollState: ScrollState,
    includeScrollbarWhenUsed: Boolean,
    content: @Composable () -> Unit,
) {
    val adapter = rememberScrollbarAdapter(scrollState)
    Layout(
        measurePolicy = { measurables, constraints ->
            val contentMeasurable = measurables[0]
            val needScrollbar =
                includeScrollbarWhenUsed && adapter.contentSize > adapter.viewportSize
            if (needScrollbar) {
                val scrollbarMeasurable = measurables[1]
                val scrollbarPlaceable = scrollbarMeasurable.measure(constraints)
                val contentPlaceable =
                    contentMeasurable.measure(
                        constraints.copy(maxWidth = constraints.maxWidth - scrollbarPlaceable.width),
                    )
                layout(contentPlaceable.width + scrollbarPlaceable.width, contentPlaceable.height) {
                    contentPlaceable.place(0, 0)
                    scrollbarPlaceable.place(contentPlaceable.width, 0)
                }
            } else {
                val contentPlaceable = contentMeasurable.measure(constraints)
                layout(contentPlaceable.width, contentPlaceable.height) {
                    contentPlaceable.place(0, 0)
                }
            }
        },
        content = {
            content()
            VerticalScrollbar(adapter = adapter)
        },
    )
}

/**
 * Tracks which item in [this] is currently hovered, emitting its index (or -1 when none have ever
 * been hovered). Last hovered index "sticks" so nested menus stay open while the pointer travels
 * across the gap to the submenu.
 */
internal fun Iterable<InteractionSource>.hoveredIndex(): Flow<Int> =
    this
        .map { interactionSource ->
            interactionSource.interactions
                .runningFold(initial = 0) { total, interaction ->
                    when (interaction) {
                        is HoverInteraction.Enter -> total + 1
                        is HoverInteraction.Exit -> total - 1
                        else -> total
                    }
                }.map { it > 0 }
        }.let { flows ->
            combine(flows) { hoveredStates ->
                hoveredStates.indexOfFirst { hovering -> hovering }
            }
        }.scan(initial = -1) { previous, current ->
            if (current != -1) current else previous
        }

@Composable
internal fun rememberNestedDropdownPositionProvider(
    windowMargin: Dp,
    menuTopPadding: Dp,
    menuBottomPadding: Dp,
): PopupPositionProvider {
    val density = LocalDensity.current
    val windowMarginPx = with(density) { windowMargin.roundToPx() }
    val menuTopPaddingPx = with(density) { menuTopPadding.roundToPx() }
    val menuBottomPaddingPx = with(density) { menuBottomPadding.roundToPx() }
    return remember(windowMarginPx, menuTopPaddingPx, menuBottomPaddingPx) {
        NestedDropdownPositionProvider(
            windowMarginPx = windowMarginPx,
            menuTopPaddingPx = menuTopPaddingPx,
            menuBottomPaddingPx = menuBottomPaddingPx,
        )
    }
}

/**
 * Positions a nested submenu popup so that its hovered item row aligns with the parent group
 * item that opened it: first item when shown below, last item when flipped above. The popup's
 * internal vertical padding (`menuTopPadding` / `menuBottomPadding`) is canceled out so the
 * visible hover background lines up across the popup boundary instead of being inset by it.
 */
internal class NestedDropdownPositionProvider(
    private val windowMarginPx: Int,
    private val menuTopPaddingPx: Int,
    private val menuBottomPaddingPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        var x = anchorBounds.right
        if (x + popupContentSize.width > windowSize.width - windowMarginPx) {
            x = anchorBounds.left - popupContentSize.width
        }

        val flipUp = anchorBounds.top + popupContentSize.height > windowSize.height - windowMarginPx
        var y =
            if (flipUp) {
                anchorBounds.bottom - popupContentSize.height + menuBottomPaddingPx
            } else {
                anchorBounds.top - menuTopPaddingPx
            }

        x = x.coerceAtLeast(windowMarginPx)
        y = y.coerceAtLeast(windowMarginPx)
        return IntOffset(x, y)
    }
}
