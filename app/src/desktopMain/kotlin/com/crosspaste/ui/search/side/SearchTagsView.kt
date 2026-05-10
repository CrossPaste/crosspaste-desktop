package com.crosspaste.ui.search.side

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.zIndex
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.crosspaste.app.AppControl
import com.crosspaste.db.paste.PasteTagDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.DesktopPasteTagMenuService
import com.crosspaste.paste.PasteTag
import com.crosspaste.paste.PasteTag.Companion.createDefaultPasteTag
import com.crosspaste.ui.LocalSearchWindowInfoState
import com.crosspaste.ui.base.PasteContextMenuView
import com.crosspaste.ui.model.PasteSearchViewModel
import com.crosspaste.ui.paste.PasteTagScope
import com.crosspaste.ui.paste.createPasteTagScope
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

private val LONG_PRESS_TIMEOUT = 200.milliseconds

@Composable
fun SearchTagsView() {
    val appControl = koinInject<AppControl>()
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteTagDao = koinInject<PasteTagDao>()
    val pasteSearchViewModel = koinInject<PasteSearchViewModel>()
    val tagMenuService = koinInject<DesktopPasteTagMenuService>()

    val searchWindowInfo = LocalSearchWindowInfoState.current

    val searchBaseParams by pasteSearchViewModel.searchBaseParams.collectAsState()

    val tagList by pasteSearchViewModel.tagList.collectAsState()

    var newTag by remember { mutableStateOf<PasteTag?>(null) }

    val editingMap by PasteTagScope.isEditingMap

    val scope = rememberCoroutineScope()

    val lazyListState = rememberLazyListState()

    var draggingTagId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var displayList by remember { mutableStateOf(tagList) }

    LaunchedEffect(tagList) {
        if (draggingTagId == null) {
            displayList = tagList
        }
    }

    LaunchedEffect(searchWindowInfo.show) {
        newTag = null
        PasteTagScope.resetEditing()
    }

    LazyRow(
        state = lazyListState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tiny),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(displayList, key = { _, item -> item.id }) { _, tag ->
            val currentTag by rememberUpdatedState(tag)

            val isSelected = currentTag.id == searchBaseParams.tag
            val isDragging = draggingTagId == tag.id

            val tagScope =
                remember(
                    currentTag.id,
                    currentTag.name,
                    currentTag.color,
                ) {
                    createPasteTagScope(currentTag)
                }

            // Outermost wrapper of each LazyRow item slot. zIndex/transform/background MUST live
            // here so they layer the dragged item above its sibling lazy items — applying them
            // deeper inside (e.g. inside PasteContextMenuView) only layers within that subtree.
            Box(
                modifier =
                    Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationX = if (isDragging) dragOffsetX else 0f
                            if (isDragging) {
                                scaleX = 1.05f
                                scaleY = 1.05f
                            }
                        }.then(
                            if (isDragging) {
                                // Lift effect + solid background so the dragged chip clearly
                                // highlights on long-press and occludes neighbors.
                                Modifier
                                    .shadow(elevation = tiny3X, shape = CircleShape)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        shape = CircleShape,
                                    )
                            } else {
                                Modifier
                            },
                        ),
            ) {
                if (editingMap[tag.id] == true) {
                    tagScope.EditableTagChip { name ->
                        if (name.isBlank()) {
                            pasteTagDao.deletePasteTagBlock(tag.id)
                        } else {
                            pasteTagDao.updatePasteTagName(tag.id, name)
                        }
                        tagScope.stopEditing()
                    }
                } else {
                    PasteContextMenuView(
                        items = tagMenuService.menuItemsProvider(tagScope),
                    ) {
                        tagScope.TagChip(
                            modifier =
                                Modifier.pointerInput(tag.id) {
                                    detectReorderDragGestures(
                                        onDragStart = { initialDrift ->
                                            draggingTagId = tag.id
                                            // Snap chip to the cursor's drift during the long press.
                                            dragOffsetX = initialDrift.x
                                        },
                                        onDragEnd = {
                                            val finalOrder = displayList.map { it.id }
                                            if (finalOrder != tagList.map { it.id }) {
                                                scope.launch {
                                                    pasteTagDao.updatePasteTagsSortOrder(finalOrder)
                                                }
                                            }
                                            draggingTagId = null
                                            dragOffsetX = 0f
                                        },
                                        onDragCancel = {
                                            // Revert any in-progress swap to the persisted order.
                                            displayList = tagList
                                            draggingTagId = null
                                            dragOffsetX = 0f
                                        },
                                        onDrag = { _, dragAmount ->
                                            dragOffsetX += dragAmount.x

                                            val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
                                            val draggedItem =
                                                visibleItems.firstOrNull { it.key == tag.id }
                                                    ?: return@detectReorderDragGestures
                                            val draggedCenter =
                                                draggedItem.offset + draggedItem.size / 2f + dragOffsetX

                                            // Center-crossed-center swap rule: swap only once the
                                            // dragged center has moved past the neighbor's center.
                                            // This builds hysteresis ≈ half a chip's width so a
                                            // post-swap layout update can't re-trigger the opposite
                                            // swap (preventing oscillation).
                                            val target =
                                                visibleItems.firstOrNull { other ->
                                                    if (other.key == tag.id) return@firstOrNull false
                                                    val otherCenter = other.offset + other.size / 2f
                                                    when {
                                                        other.offset > draggedItem.offset ->
                                                            draggedCenter > otherCenter
                                                        other.offset < draggedItem.offset ->
                                                            draggedCenter < otherCenter
                                                        else -> false
                                                    }
                                                } ?: return@detectReorderDragGestures

                                            val from = displayList.indexOfFirst { it.id == tag.id }
                                            val to = displayList.indexOfFirst { it.id == target.key }
                                            if (from == -1 || to == -1 || from == to) {
                                                return@detectReorderDragGestures
                                            }

                                            // Keep the chip visually under the cursor across the swap.
                                            dragOffsetX += (draggedItem.offset - target.offset).toFloat()
                                            displayList =
                                                displayList.toMutableList().apply {
                                                    add(to, removeAt(from))
                                                }
                                        },
                                    )
                                },
                            isSelected = isSelected,
                            onEdit = {
                                pasteSearchViewModel.updateTag(tag.id)
                            },
                        ) {
                            tagScope.startEditing()
                        }
                    }
                }
            }
        }

        newTag?.let {
            item {
                val scope = remember { createPasteTagScope(it) }

                scope.EditableTagChip { name ->
                    newTag = null
                    if (!name.isBlank()) {
                        pasteTagDao.createPasteTag(name, it.color)
                    }
                    PasteTagScope.resetEditing()
                }
            }
        }

        item {
            AssistChip(
                onClick = {
                    if (appControl.isCreateTagEnabled()) {
                        scope.launch {
                            newTag =
                                createDefaultPasteTag(
                                    name = copywriter.getText("unnamed"),
                                    maxSortOrder = pasteTagDao.getMaxSortOrder(),
                                )
                            PasteTagScope.resetEditing()
                        }
                    }
                },
                label = {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Add,
                        contentDescription = "Add",
                        modifier = Modifier.size(medium),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                modifier = Modifier.height(xxLarge),
                border = null,
                shape = CircleShape,
            )
        }
    }
}

// Press-and-hold-then-drag gesture that tolerates pointer movement during the long-press wait.
// Unlike `detectDragGesturesAfterLongPress`, motion within the timeout does not cancel the
// gesture, so users can press-and-immediately-start-moving without having to be perfectly still.
// The cursor's drift accumulated during the wait is reported via `onDragStart` so callers can
// snap the dragged element to its current cursor position.
private suspend fun PointerInputScope.detectReorderDragGestures(
    onDragStart: (initialDrift: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var lastChange: PointerInputChange = down

        val pressedThroughTimeout =
            withTimeoutOrNull(LONG_PRESS_TIMEOUT.inWholeMilliseconds) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) return@withTimeoutOrNull
                    lastChange = change
                }
            } == null

        if (!pressedThroughTimeout) return@awaitEachGesture

        // Consume the in-flight pointer change so the underlying clickable doesn't
        // see this gesture as a press-and-release tap.
        lastChange.consume()
        onDragStart(lastChange.position - down.position)

        val dragSucceeded =
            drag(down.id) { change ->
                onDrag(change, change.positionChange())
                change.consume()
            }

        // Consume the up/cancel event so a long-press-then-release (no actual drag)
        // doesn't fall through to the chip's onClick.
        currentEvent.changes.forEach { it.consume() }

        if (dragSucceeded) onDragEnd() else onDragCancel()
    }
}
