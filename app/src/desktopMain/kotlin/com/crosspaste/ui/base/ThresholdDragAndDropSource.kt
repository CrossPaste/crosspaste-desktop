package com.crosspaste.ui.base

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropSourceModifierNode
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.crosspaste.ui.theme.AppUISize.tiny

/**
 * Like Compose's `Modifier.dragAndDropSource`, but a drag only starts once the primary button
 * is held and the pointer has travelled more than [dragThreshold] from the press point.
 *
 * Compose's default desktop detector uses a mouse slop of 0.125dp, so the sub-pixel jitter of
 * an ordinary click is enough to start a platform drag and swallow the click.
 */
fun Modifier.thresholdDragAndDropSource(
    drawDragDecoration: DrawScope.() -> Unit,
    dragThreshold: Dp = tiny,
    transferData: (Offset) -> DragAndDropTransferData?,
): Modifier =
    this then
        ThresholdDragAndDropSourceElement(
            drawDragDecoration = drawDragDecoration,
            dragThreshold = dragThreshold,
            transferData = transferData,
        )

private data class ThresholdDragAndDropSourceElement(
    val drawDragDecoration: DrawScope.() -> Unit,
    val dragThreshold: Dp,
    val transferData: (Offset) -> DragAndDropTransferData?,
) : ModifierNodeElement<ThresholdDragAndDropSourceNode>() {
    override fun create() =
        ThresholdDragAndDropSourceNode(
            drawDragDecoration = drawDragDecoration,
            dragThreshold = dragThreshold,
            transferData = transferData,
        )

    override fun update(node: ThresholdDragAndDropSourceNode) {
        node.drawDragDecoration = drawDragDecoration
        node.dragThreshold = dragThreshold
        node.transferData = transferData
    }
}

private class ThresholdDragAndDropSourceNode(
    var drawDragDecoration: DrawScope.() -> Unit,
    var dragThreshold: Dp,
    var transferData: (Offset) -> DragAndDropTransferData?,
) : DelegatingNode(),
    LayoutAwareModifierNode {

    private var size: IntSize = IntSize.Zero

    private val dragAndDropNode =
        delegate(
            DragAndDropSourceModifierNode { offset ->
                transferData(offset)?.let {
                    startDragAndDropTransfer(
                        transferData = it,
                        decorationSize = size.toSize(),
                        drawDragDecoration = drawDragDecoration,
                    )
                }
            },
        )

    init {
        delegate(
            SuspendingPointerInputModifierNode {
                awaitEachGesture {
                    val press = awaitPointerEvent()
                    if (press.type != PointerEventType.Press || !press.buttons.isPrimaryPressed) {
                        return@awaitEachGesture
                    }
                    val down = press.changes.first()
                    val thresholdPx = dragThreshold.toPx()
                    while (true) {
                        val change =
                            awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                ?: return@awaitEachGesture
                        if (!change.pressed) return@awaitEachGesture
                        if ((change.position - down.position).getDistance() > thresholdPx) {
                            dragAndDropNode.requestDragAndDropTransfer(down.position)
                            return@awaitEachGesture
                        }
                    }
                }
            },
        )
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        dragAndDropNode.onPlaced(coordinates)
    }

    override fun onRemeasured(size: IntSize) {
        this.size = size
        dragAndDropNode.onRemeasured(size)
    }
}
