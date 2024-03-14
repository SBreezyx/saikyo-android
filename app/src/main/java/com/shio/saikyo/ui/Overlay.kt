package com.shio.saikyo.ui

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastJoinToString
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.toPointF
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shio.saikyo.ocr.BoundingBox
import com.shio.saikyo.i18n.LangCode
import com.shio.saikyo.ocr.OcrResult
import com.shio.saikyo.ocr.Sz
import com.shio.saikyo.i18n.TextFlow
import com.shio.saikyo.ocr.contains
import com.shio.saikyo.util.filterMap
import kotlin.math.pow

fun Offset.toIntOffset(): IntOffset {
    return IntOffset(x.toInt(), y.toInt())
}

fun Point.toIntOffset(): IntOffset {
    return IntOffset(x, y)
}

private enum class OverlayDestinations {
    Floating,
    Screenshotting,
    Analyzing,
    Displaying,
}

private fun cropToContent(frame: Rect, base: Bitmap): Bitmap {
    return Bitmap.createBitmap(base, frame.left, frame.top, frame.width(), frame.height())
}

private fun getHit(offset: Offset, charBoundingBoxes: List<BoundingBox>): Int {
    val px = offset.x.toInt()
    val py = offset.y.toInt()

    // TODO: binary search
    for (ix in charBoundingBoxes.indices) {
        if (charBoundingBoxes[ix].contains(px, py)) {
            return ix
        }
    }

    return -1
}

private fun minDistToIndexed(bbs: List<BoundingBox>, offset: Offset): Int {
    val sqDists = bbs.filterMap { _, bb ->
        val (cx, cy) = bb.topLeft
        (offset.x - cx).pow(2) + (offset.y - cy).pow(2)
    }

    return sqDists.indexOfMin()
}

private fun <T : Comparable<T>> List<T>.indexOfMin(fromIx: Int = 0, untilIx: Int = this.size): Int {
    var m = this[fromIx]
    var i = fromIx
    for (ix in fromIx until untilIx) {
        val candidate = this[ix]
        if (candidate < m) {
            m = candidate
            i = ix
        }
    }

    return i
}

@Composable
fun ContextMenuItem(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(25),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        contentPadding = PaddingValues(all = 16.dp),
        border = null
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContextMenu(
    anchor: Offset,
    onDismiss: () -> Unit,
    onDrag: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    moreIcon: @Composable () -> Unit = {
        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "more actions")
    },
    items: @Composable (RowScope.() -> Unit)
) {

    Popup(
        offset = anchor.toIntOffset(),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            clippingEnabled = true,
            dismissOnClickOutside = false
        )
    ) {
        Row(
            modifier = modifier
                .shadow(2.dp)
                .background(
                    color = MaterialTheme.colorScheme.background,
                )
                .height(48.dp)
                .draggable2D(state = rememberDraggable2DState(onDelta = onDrag))

        ) {
            items(this)
            IconButton(
                onClick = { /*TODO*/ },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                content = moreIcon
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingControl(onDrag: (Offset) -> Unit, onTakeScreenshot: () -> Unit) {
    Button(
        onClick = onTakeScreenshot,
        shape = RoundedCornerShape(100),
        border = BorderStroke(4.dp, Color.Black),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(1f, 1f, 1f, 0.5f),
        ),
        modifier = Modifier
            .size(48.dp)
            .draggable2D(rememberDraggable2DState(onDrag))
    ) {}
}

@Composable
fun ScreenshotInterlude(onTakeScreenshot: suspend () -> Bitmap, onScreenshotTaken: (Bitmap) -> Unit) {
    LaunchedEffect(true) {
        val bmp = onTakeScreenshot()
        onScreenshotTaken(bmp)
    }
}

@Composable
fun AnalyzeInterlude(
    scrShot: Bitmap,
    startOcr: suspend (Bitmap, LangCode, Sz) -> OcrResult,
    startParsing: suspend (LangCode, String) -> List<Pair<Int, Int>>,
    onAnalysisFinished: (OcrResult, List<Pair<Int, Int>>) -> Unit
) {
    val win = LocalView.current.rootView
    val winConf = LocalViewConfiguration.current

    val cropped = remember {
        val frame = Rect()
        win.getWindowVisibleDisplayFrame(frame)
        cropToContent(frame, scrShot)
    }

    LaunchedEffect(true) {
        val desiredLang = LangCode.ja // jp rules

        val minBBSize = winConf.minimumTouchTargetSize
        val res = startOcr(cropped, desiredLang, Sz(minBBSize.width.value.toInt(), minBBSize.height.value.toInt()))
        val parsed = startParsing(res.characteristics.lang, res.text) // TODO: app settings and pass this

        onAnalysisFinished(res, parsed)
    }

    Box(Modifier.fillMaxSize()) {
        CircularProgressIndicator(
            modifier = Modifier
                .width(48.dp)
                .align(Alignment.Center),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}


sealed class SelectionHandleKind(val tr: Float, val sc: Float) {
    data object HorizontalBegin : SelectionHandleKind(-1f, 0f)
    data object HorizontalEnd : SelectionHandleKind(0f, 1f)
    data object VerticalBegin : SelectionHandleKind(0f, 1f)
    data object VerticalEnd : SelectionHandleKind(-1f, 0f)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectionHandle(
    anchor: IntOffset,
    modifier: Modifier = Modifier,
    kind: SelectionHandleKind = SelectionHandleKind.HorizontalBegin,
    onDrag: (Offset) -> Unit = {}, // offset in screen co-ordinates
) {
    val col = LocalTextSelectionColors.current.handleColor
    val density = LocalDensity.current.density

    val handleSize = 24

    val anchorOffset = (density * kind.tr * handleSize / 2).toInt()

    Popup(
        offset = anchor.copy(x = anchor.x + anchorOffset)
    ) {
        Canvas(
            modifier = Modifier
                .size(handleSize.dp)
                .minimumInteractiveComponentSize()
                .draggable2D(
                    rememberDraggable2DState(onDelta = onDrag)
                )
                .then(modifier)
        ) {
            val radius = size.width / 2
            val cOff = radius * kind.sc

            // pointy corner of the handle
            drawRect(topLeft = Offset.Zero, color = col, size = Size(radius, radius))

            // main rest of the body of the handle. centered on the bottom-right edge of the rectangle
            drawCircle(color = col, radius = radius, center = Offset(cOff, radius))
        }
    }
}


@Composable
fun OcrOverlay(
    ocr: OcrResult,
    onDismiss: () -> Unit
) {
    val (characteristics, charBBs, charStrs, bb2text, bb2tb, bb2ln, bb2wrd) = ocr

    val selectedColor = LocalTextSelectionColors.current.backgroundColor
    val unselectedColor = Color.Gray.copy(alpha = 0.3f)
    val clipboard = LocalClipboardManager.current

    var beginSelPos by remember { mutableStateOf(Offset.Unspecified) }
    var endSelPos by remember { mutableStateOf(Offset.Unspecified) }
    var ctxMenuPos by remember { mutableStateOf(Offset.Unspecified) }

    val selectionIx by remember {
        derivedStateOf {
            if (beginSelPos == Offset.Unspecified || endSelPos == Offset.Unspecified) {
                charBBs.size to charBBs.size
            } else {
                val maybeBegin = minDistToIndexed(charBBs, beginSelPos)
                val maybeEnd = minDistToIndexed(charBBs, endSelPos)

                // plus one on the right bound because it is C++-style one-past-the-end
                if (maybeBegin < maybeEnd) {
                    maybeBegin to (maybeEnd + 1)
                } else {
                    maybeEnd to (maybeBegin + 1)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val charIx = getHit(offset, charBBs)
                        if (charIx >= 0) {

                            beginSelPos = offset
                            endSelPos = offset

                            ctxMenuPos = offset
                        } else if (ctxMenuPos == Offset.Unspecified) {
                            onDismiss()
                        } else {
                            beginSelPos = Offset.Unspecified
                            endSelPos = Offset.Unspecified
                            ctxMenuPos = Offset.Unspecified
                        }
                    },
                    onLongPress = { offset ->
                        val ix = getHit(offset, charBBs)
                        if (ix >= 0) {
                            beginSelPos = offset
                            endSelPos = offset

                            ctxMenuPos = offset
                        } else {
                            // do nothing -- no long press on a char
                        }
                    },
                )
            }
    ) {

        // TODO: put the original screenshot here

        // TODO: put this into its own composable
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val (selFirst, selLast) = selectionIx

            for ((col, bounds) in arrayOf(
                unselectedColor to (0 to selFirst),
                selectedColor to (selFirst to selLast),
                unselectedColor to (selLast to charBBs.size)
            )) {
                val (from, to) = bounds
                for (bbID in from until to) {
                    val bb = charBBs[bbID]
                    val topLeft = bb.topLeft.toPointF()
                    val topRight = bb.topRight.toPointF()
                    val bottomRight = bb.bottomRight.toPointF()
                    val bottomLeft = bb.bottomLeft.toPointF()

                    drawPath(Path().apply {
                        moveTo(topLeft.x, topLeft.y)
                        lineTo(topRight.x, topRight.y)
                        lineTo(bottomRight.x, bottomRight.y)
                        lineTo(bottomLeft.x, bottomLeft.y)
                        close()
                    }, col)
                }
            }
        }

        // TODO: consider if the context menu + handles should be together...
        if (selectionIx.first in charBBs.indices && selectionIx.second in 1..charBBs.size) {
            when (characteristics.layoutFlow) {
                TextFlow.HORIZONTAL -> {
                    val beginAnchor = charBBs[selectionIx.first].bottomLeft
                    val endAnchor = charBBs[selectionIx.second - 1].bottomRight

                    SelectionHandle(
                        anchor = beginAnchor.toIntOffset(),
                        onDrag = { beginSelPos = beginSelPos.plus(it) },
                        kind = SelectionHandleKind.HorizontalBegin,
                    )

                    SelectionHandle(
                        anchor = endAnchor.toIntOffset(),
                        onDrag = { endSelPos = endSelPos.plus(it) },
                        kind = SelectionHandleKind.HorizontalEnd,
                    )
                }

                TextFlow.VERTICAL -> {
                    val beginAnchor = charBBs[selectionIx.first].topLeft
                    val endAnchor = charBBs[selectionIx.second - 1].bottomRight

                    SelectionHandle(
                        anchor = beginAnchor.toIntOffset(),
                        onDrag = { beginSelPos = beginSelPos.plus(it) },
                        kind = SelectionHandleKind.VerticalBegin
                    )

                    SelectionHandle(
                        anchor = endAnchor.toIntOffset(),
                        onDrag = { endSelPos = endSelPos.plus(it) },
                        kind = SelectionHandleKind.VerticalEnd
                    )
                }
            }
        }

        // TODO: or if the context menu and handles should be apart ...
        if (ctxMenuPos != Offset.Unspecified) {
            ContextMenu(
                ctxMenuPos,
                onDismiss = {
                    beginSelPos = Offset.Unspecified
                    endSelPos = Offset.Unspecified
                    ctxMenuPos = Offset.Unspecified
                },
                {
                    ctxMenuPos = ctxMenuPos.plus(it)
                }
            ) {
                ContextMenuItem(onClick = {
                    val textChars = bb2text[selectionIx.first]!! .. bb2text[selectionIx.second - 1]!!
                    val str = textChars.map {
                        charStrs[it]
                    }.fastJoinToString(separator = "")

                    clipboard.setText(AnnotatedString(str))
                    onDismiss()
                }) {
                    Text("Copy", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun OverlayControls(
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onDrag: (Offset) -> Unit,
    onTakeScreenshot: suspend () -> Bitmap,
    onStartOcr: suspend (Bitmap, LangCode, Sz) -> OcrResult,
    onStartParsing: suspend (LangCode, String) -> List<Pair<Int, Int>>
) {
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var ocr by remember { mutableStateOf<OcrResult?>(null) }
    val parsed = remember { mutableStateListOf<Pair<Int, Int>>() }

    val navCtrl = rememberNavController()
    NavHost(
        navController = navCtrl, startDestination = OverlayDestinations.Floating.name,
        enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None },

        ) {
        composable(OverlayDestinations.Floating.name) {
            FloatingControl(onDrag) {
                navCtrl.navigate(OverlayDestinations.Screenshotting.name)
            }
        }

        composable(OverlayDestinations.Screenshotting.name) {
            ScreenshotInterlude(onTakeScreenshot) { screenshot ->
                onMaximize()
                bmp = screenshot
                navCtrl.navigate(OverlayDestinations.Analyzing.name)
            }
        }

        composable(OverlayDestinations.Analyzing.name) {
            AnalyzeInterlude(scrShot = bmp!!, startOcr = onStartOcr, startParsing = onStartParsing) { ocrRes, parsedRes ->
                ocr = ocrRes
                parsed.clear()
                parsed.addAll(parsedRes)

                navCtrl.navigate(OverlayDestinations.Displaying.name)
            }
        }

        composable(
            OverlayDestinations.Displaying.name,
            enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None },
        ) {
            OcrOverlay(ocr!!) {
                // IMPORTANT: must navigate before minimizing because NavHost thinks
                // EVERYONE WANTS animations when they really DON'T
                navCtrl.navigate(
                    OverlayDestinations.Floating.name,
                    NavOptions.Builder().setEnterAnim(0).setExitAnim(0).setPopEnterAnim(0).setPopExitAnim(0).build()
                )
                onMinimize()
            }
        }
    }
}

@Preview
@Composable
fun ContextMenuPreview() {
    ContextMenu(Offset.Zero, onDismiss = { /*TODO*/ }, {}) {
        for (i in 0..2) {
            ContextMenuItem(onClick = { /*TODO*/ }) {
                Text("ji")
            }
        }

    }
}

@Preview
@Composable
fun SelectionHPreview() {
    Column {
        SelectionHandle(anchor = IntOffset.Zero)
//        SelectionHandle(anchor = IntOffset.Zero, modifier = Modifier.scale(scaleX = -1f, scaleY = 1f))
    }
}