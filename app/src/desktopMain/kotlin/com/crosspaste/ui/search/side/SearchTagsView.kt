package com.crosspaste.ui.search.side

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
    val editingMap by PasteTagScope.isEditingMap

    var newTag by remember { mutableStateOf<PasteTag?>(null) }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val dragState = rememberTagDragState(tagList)

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
        itemsIndexed(dragState.displayList, key = { _, item -> item.id }) { _, tag ->
            SearchTagItem(
                tag = tag,
                isSelected = tag.id == searchBaseParams.tag,
                isDragging = dragState.isDragging(tag.id),
                isEditing = editingMap[tag.id] == true,
                dragOffsetX = dragState.dragOffsetX,
                contextMenuItemsFor = tagMenuService::menuItemsProvider,
                onTagClick = { pasteSearchViewModel.updateTag(tag.id) },
                onSubmitName = { name ->
                    if (name.isBlank()) {
                        pasteTagDao.deletePasteTagBlock(tag.id)
                    } else {
                        pasteTagDao.updatePasteTagName(tag.id, name)
                    }
                },
                onDragStart = { initialDrift -> dragState.startDrag(tag.id, initialDrift.x) },
                onDragMove = { deltaX -> dragState.handleDragMove(deltaX, lazyListState.layoutInfo) },
                onDragEnd = {
                    dragState.finalizeDrag(tagList)?.let { finalOrder ->
                        scope.launch { pasteTagDao.updatePasteTagsSortOrder(finalOrder) }
                    }
                },
                onDragCancel = { dragState.cancelDrag(tagList) },
            )
        }

        newTag?.let {
            item {
                NewTagChip(
                    tag = it,
                    onSubmit = { name ->
                        newTag = null
                        if (!name.isBlank()) {
                            pasteTagDao.createPasteTag(name, it.color)
                        }
                        PasteTagScope.resetEditing()
                    },
                )
            }
        }

        item {
            AddTagChip(
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
            )
        }
    }
}

@Composable
private fun SearchTagItem(
    tag: PasteTag,
    isSelected: Boolean,
    isDragging: Boolean,
    isEditing: Boolean,
    dragOffsetX: Float,
    contextMenuItemsFor: (PasteTagScope) -> () -> List<ContextMenuItem>,
    onTagClick: () -> Unit,
    onSubmitName: suspend (String) -> Unit,
    onDragStart: (initialDrift: Offset) -> Unit,
    onDragMove: (deltaX: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val currentTag by rememberUpdatedState(tag)
    val tagScope =
        remember(currentTag.id, currentTag.name, currentTag.color) {
            createPasteTagScope(currentTag)
        }

    // zIndex/transform/background MUST live on the outermost LazyRow item slot so they
    // can layer the dragged item above its sibling lazy items — applying them deeper
    // inside (e.g. inside PasteContextMenuView) only layers within that subtree.
    Box(
        modifier = Modifier.tagDragVisuals(isDragging = isDragging, dragOffsetX = dragOffsetX),
    ) {
        if (isEditing) {
            tagScope.EditableTagChip { name ->
                onSubmitName(name)
                tagScope.stopEditing()
            }
        } else {
            PasteContextMenuView(items = contextMenuItemsFor(tagScope)) {
                tagScope.TagChip(
                    modifier =
                        Modifier.pointerInput(tag.id) {
                            detectReorderDragGestures(
                                onDragStart = onDragStart,
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragCancel,
                                onDrag = { _, dragAmount -> onDragMove(dragAmount.x) },
                            )
                        },
                    isSelected = isSelected,
                    onEdit = onTagClick,
                ) {
                    tagScope.startEditing()
                }
            }
        }
    }
}

@Composable
private fun NewTagChip(
    tag: PasteTag,
    onSubmit: suspend (String) -> Unit,
) {
    val tagScope = remember { createPasteTagScope(tag) }
    tagScope.EditableTagChip(onSubmit)
}

@Composable
private fun AddTagChip(onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
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

@Composable
private fun Modifier.tagDragVisuals(
    isDragging: Boolean,
    dragOffsetX: Float,
): Modifier {
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
    return this
        .zIndex(if (isDragging) 1f else 0f)
        .graphicsLayer {
            translationX = if (isDragging) dragOffsetX else 0f
            if (isDragging) {
                scaleX = 1.05f
                scaleY = 1.05f
            }
        }.then(
            if (isDragging) {
                // Lift effect + solid background so the dragged chip clearly highlights
                // on long-press and occludes neighbors.
                Modifier
                    .shadow(elevation = tiny3X, shape = CircleShape)
                    .background(color = backgroundColor, shape = CircleShape)
            } else {
                Modifier
            },
        )
}

@Stable
private class TagDragState(
    initial: List<PasteTag>,
) {
    var displayList by mutableStateOf(initial)
        private set

    var draggingTagId by mutableStateOf<Long?>(null)
        private set

    var dragOffsetX by mutableStateOf(0f)
        private set

    fun isDragging(tagId: Long): Boolean = draggingTagId == tagId

    fun syncFromSource(source: List<PasteTag>) {
        // Don't clobber the user's optimistic order while a drag is in progress.
        if (draggingTagId == null) displayList = source
    }

    fun startDrag(
        tagId: Long,
        initialDriftX: Float,
    ) {
        draggingTagId = tagId
        dragOffsetX = initialDriftX
    }

    fun handleDragMove(
        deltaX: Float,
        layoutInfo: LazyListLayoutInfo,
    ) {
        dragOffsetX += deltaX
        val draggingId = draggingTagId ?: return
        val visibleItems = layoutInfo.visibleItemsInfo
        val draggedItem = visibleItems.firstOrNull { it.key == draggingId } ?: return
        val draggedCenter = draggedItem.offset + draggedItem.size / 2f + dragOffsetX

        // Center-crossed-center swap rule: swap only once the dragged center has moved
        // past the neighbor's center. Builds hysteresis ≈ half a chip's width so a
        // post-swap layout update can't re-trigger the opposite swap (no oscillation).
        val target =
            visibleItems.firstOrNull { other ->
                if (other.key == draggingId) return@firstOrNull false
                val otherCenter = other.offset + other.size / 2f
                when {
                    other.offset > draggedItem.offset -> draggedCenter > otherCenter
                    other.offset < draggedItem.offset -> draggedCenter < otherCenter
                    else -> false
                }
            } ?: return

        val from = displayList.indexOfFirst { it.id == draggingId }
        val to = displayList.indexOfFirst { it.id == target.key }
        if (from == -1 || to == -1 || from == to) return

        // Keep the chip visually under the cursor across the swap.
        dragOffsetX += (draggedItem.offset - target.offset).toFloat()
        displayList = displayList.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * Clears drag state and returns the new id order if it differs from [persisted],
     * else null (caller can skip the DB write).
     */
    fun finalizeDrag(persisted: List<PasteTag>): List<Long>? {
        val finalIds = displayList.map { it.id }
        draggingTagId = null
        dragOffsetX = 0f
        return finalIds.takeIf { it != persisted.map { p -> p.id } }
    }

    fun cancelDrag(persisted: List<PasteTag>) {
        // Revert any in-progress swap to the persisted order.
        displayList = persisted
        draggingTagId = null
        dragOffsetX = 0f
    }
}

@Composable
private fun rememberTagDragState(source: List<PasteTag>): TagDragState {
    val state = remember { TagDragState(source) }
    LaunchedEffect(source) { state.syncFromSource(source) }
    return state
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
