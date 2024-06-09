package com.shio.saikyo.ui.media

import android.view.SurfaceView
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.shio.saikyo.ui.gesture.DragGestureDetector
import com.shio.saikyo.ui.gesture.detectHorizontalDragGesturesAfterLongPress
import com.shio.saikyo.ui.gesture.detectVerticalDragGesturesAfterLongPress
import com.shio.saikyo.ui.theme.SaikyoTheme
import kotlin.math.abs

fun Modifier.dragger(
    onLeftDragBackward: () -> Unit,
    onLeftDragForward: () -> Unit,
    onRightDragBackward: () -> Unit = onLeftDragBackward,
    onRightDragForward: () -> Unit = onLeftDragForward,
    detector: DragGestureDetector
): Modifier = pointerInput(Unit) {
    var isLeft = false
    var draggedAmount = 0f
    val activationThreshold = viewConfiguration.minimumTouchTargetSize.width.toPx()
    detector(
        {
            isLeft = it.x < size.center.x
            draggedAmount = 0f
        },
        {},
        {},
    ) { ev, amt ->
        if (!ev.isConsumed) {
            ev.consume()
            draggedAmount += amt

            if (abs(draggedAmount) > activationThreshold) {
                val didDragForward = draggedAmount >= 0

                if (isLeft) {
                    if (didDragForward) {
                        onLeftDragForward()
                    } else {
                        onLeftDragBackward()
                    }
                } else {
                    if (didDragForward) {
                        onRightDragForward()
                    } else {
                        onRightDragBackward()
                    }
                }

                draggedAmount = 0f
            } else {
                // haven't dragged enough to activate
            }
        }
    }
}

fun Modifier.horizontalDraggable(
    onLeftDragLeft: () -> Unit,
    onLeftDragRight: () -> Unit,
    onRightDragLeft: () -> Unit = onLeftDragLeft,
    onRightDragRight: () -> Unit = onLeftDragRight,
) = dragger(
    onLeftDragLeft, onLeftDragRight,
    onRightDragLeft, onRightDragRight,
    PointerInputScope::detectHorizontalDragGestures,
)

fun Modifier.verticalDraggable(
    onLeftDragUp: () -> Unit,
    onLeftDragDown: () -> Unit,
    onRightDragUp: () -> Unit = onLeftDragUp,
    onRightDragDown: () -> Unit = onLeftDragDown,
) = dragger(
    onLeftDragUp, onLeftDragDown,
    onRightDragUp, onRightDragDown,
    PointerInputScope::detectVerticalDragGestures
)

fun Modifier.horizontalDraggableAfterLongPress(
    onLeftDragLeft: () -> Unit,
    onLeftDragRight: () -> Unit,
    onRightDragLeft: () -> Unit = onLeftDragLeft,
    onRightDragRight: () -> Unit = onLeftDragRight,
) = dragger(
    onLeftDragLeft, onLeftDragRight,
    onRightDragLeft, onRightDragRight,
    PointerInputScope::detectHorizontalDragGesturesAfterLongPress,
)

fun Modifier.verticalDraggableAfterLongPress(
    onLeftDragUp: () -> Unit,
    onLeftDragDown: () -> Unit,
    onRightDragUp: () -> Unit = onLeftDragUp,
    onRightDragDown: () -> Unit = onLeftDragDown,
) = dragger(
    onLeftDragUp, onLeftDragDown,
    onRightDragUp, onRightDragDown,
    PointerInputScope::detectVerticalDragGesturesAfterLongPress,
)

fun Modifier.tappable(
    onClick: () -> Unit,
    onLeftDoubleClick: () -> Unit,
    onRightDoubleClick: () -> Unit,
    onLongPress: () -> Unit,
) = pointerInput(Unit) {
    detectTapGestures(
        onTap = {
            onClick()
        },
        onDoubleTap = { offset: Offset ->
            val isLeft = offset.x < size.center.x
            if (isLeft) {
                onLeftDoubleClick()
            } else {
                onRightDoubleClick()
            }
        },
        onLongPress = {
            onLongPress()
        }
    )
}

fun Modifier.longPressable(onLongPress: () -> Unit) = pointerInput(Unit) {
    detectTapGestures(
        onLongPress = {
            onLongPress()
        }
    )
}


@Composable
fun VideoSurface(
    onInitialized: (SurfaceView) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).apply {
                onInitialized(this)
            }
        },
        modifier = modifier
    )
}

@Preview
@Composable
fun VSPreview() = SaikyoTheme {
    VideoSurface(onInitialized = {}, modifier = Modifier
        .width(640.dp)
        .height(480.dp)
    )
}