package com.shio.saikyo.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


@Composable
fun Modifier.verticalScrollbar(
    state: LazyListState,
    width: Dp = 4.dp,
    fadeDuration: Int = 1000,
    color: Color = MaterialTheme.colorScheme.primary
): Modifier = if (state.canScrollForward || state.canScrollBackward) {
    val isScrolling = state.isScrollInProgress

    // Upon scroll, set the opacity of the bar to 1f and let it animate to 0.
    // When not being scrolled / finished animating, leave the opacity at 0f
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(isScrolling) {
        if (isScrolling) {
            alpha.snapTo(1f)
        } else {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = fadeDuration,
                    delayMillis = 100
                ),
            )
        }
    }

    // It is possible for the number of visible items to change -- keep it stable
    val numVisibleItems = remember { mutableIntStateOf(state.layoutInfo.visibleItemsInfo.size) }
    drawWithContent {
        drawContent()

        val layoutInfo = state.layoutInfo
        val visibleItemsInfo = layoutInfo.visibleItemsInfo
        if (visibleItemsInfo.isNotEmpty() && alpha.value > 0f) /* should draw the scroll bar */ {
            val (vwWidth, vwHeight) = this.size
            val firstVisible = visibleItemsInfo.first()

            val elementHeight = vwHeight / layoutInfo.totalItemsCount

            val scrollbarWidth = width.toPx()
            val scrollbarHeight = numVisibleItems.intValue * elementHeight

            val scrollbarOffsetY = elementHeight * (
                    firstVisible.index - firstVisible.offset.toFloat() / firstVisible.size.toFloat()
            ) /* above is - firstOffset since the offset itself is negative */
            val scrollbarOffsetX = vwWidth - width.toPx()

            drawRect(
                color = color,
                topLeft = Offset(scrollbarOffsetX, scrollbarOffsetY),
                size = Size(scrollbarWidth, scrollbarHeight),
                alpha = alpha.value
            )
        }
    }
} else {
    this
}

@Composable
fun Modifier.verticalScrollbar(
    state: ScrollState,
    width: Dp = 4.dp,
    fadeDuration: Int = 1000,
    color: Color = MaterialTheme.colorScheme.primary
): Modifier = if (state.canScrollForward || state.canScrollBackward) {
    val isScrolling = state.isScrollInProgress

    // Upon scroll, set the opacity of the bar to 1f and let it animate to 0.
    // When not being scrolled / finished animating, leave the opacity at 0f
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(isScrolling) {
        if (isScrolling) {
            alpha.snapTo(1f)
        } else {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = fadeDuration,
                    delayMillis = 100
                ),
            )
        }
    }

    drawWithContent {
        drawContent()

        if (alpha.value > 0f) /* should draw Scrollbar */ {
            val scrollbarWidth = width.toPx()
            val scrollbarHeight = (state.viewportSize - state.maxValue).toFloat()

            val scrollbarOffsetY = state.value.toFloat()
            val scrollbarOffsetX = this.size.width - width.toPx()

            drawRect(
                color = color,
                topLeft = Offset(scrollbarOffsetX, scrollbarOffsetY),
                size = Size(scrollbarWidth, scrollbarHeight),
                alpha = alpha.value
            )
        }
    }
} else {
    // no need to draw a scroll-bar -- all the items are visible on-screen.
    this
}.verticalScroll(state)