package com.shio.saikyo.ui.gesture

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import kotlin.coroutines.cancellation.CancellationException

typealias DragGestureDetector = suspend PointerInputScope.(
    onDragStart: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (PointerInputChange, Float) -> Unit
) -> Unit

// SHAMELESSLY STOLEN FROM COMPOSE'S SOURCE CODE

fun PointerEvent.isPointerUp(
    pointerId: PointerId
): Boolean = changes.fastFirstOrNull { it.id == pointerId }?.pressed != true

suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId,
    hasDragged: (PointerInputChange) -> Boolean
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                // This is the last "up"
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else if (hasDragged(dragEvent)) {
            return dragEvent
        }
    }
}

suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    orientation: Orientation,
    motionConsumed: (PointerInputChange) -> Boolean,
    onDrag: (PointerInputChange, Float) -> Unit,
): PointerInputChange? {
    if (currentEvent.isPointerUp(pointerId)) {
        return null // The pointer has already been lifted, so the gesture is canceled
    }

    var pointer = pointerId
    while (true) {
        var motionChange = 0f
        val change = awaitDragOrUp(pointer) {
            val positionChange = it.positionChangeIgnoreConsumed()
            motionChange = if (orientation == Orientation.Horizontal) positionChange.x else positionChange.y

            return@awaitDragOrUp motionChange != 0.0f
        } ?: return null

        if (motionConsumed(change)) {
            return null
        }

        if (change.changedToUpIgnoreConsumed()) {
            return change
        }

        onDrag(change, motionChange)
        pointer = change.id
    }
}

suspend fun PointerInputScope.detectDirectionalDragGesturesAfterLongPress(
    orientation: Orientation,
    onDragStart: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (PointerInputChange, Float) -> Unit
) = awaitEachGesture {
    try {
        val down = awaitFirstDown(requireUnconsumed = false)
        val drag = awaitLongPressOrCancellation(down.id)

        if (drag != null) {
            onDragStart.invoke(drag.position)

            if (
                drag(drag.id, orientation, motionConsumed = { it.isConsumed }) { event, amt ->
                    onDrag(event, amt)
                    event.consume()
                } != null
            ) {
//                // consume up if we quit drag gracefully with the up
//                currentEvent.changes.fastForEach {
//                    if (it.changedToUp()) {
//                        it.consume()
//                    }
//                }
                onDragEnd()
            } else {
                onDragCancel()
            }
        }
    } catch (c: CancellationException) {
        onDragCancel()
        throw c
    }
}

// END THEFT

suspend fun PointerInputScope.detectHorizontalDragGesturesAfterLongPress(
    onDragStart: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onHorizontalDrag: (PointerInputChange, Float) -> Unit
) = detectDirectionalDragGesturesAfterLongPress(
    Orientation.Horizontal,
    onDragStart,
    onDragEnd,
    onDragCancel,
    onHorizontalDrag
)

suspend fun PointerInputScope.detectVerticalDragGesturesAfterLongPress(
    onDragStart: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onVerticalDrag: (PointerInputChange, Float) -> Unit
)  = detectDirectionalDragGesturesAfterLongPress(
    Orientation.Vertical,
    onDragStart,
    onDragEnd,
    onDragCancel,
    onVerticalDrag
)

