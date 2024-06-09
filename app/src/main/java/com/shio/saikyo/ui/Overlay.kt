package com.shio.saikyo.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastJoinToString
import androidx.compose.ui.window.Popup
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.toPointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shio.saikyo.MainActivity
import com.shio.saikyo.i18n.LangCode
import com.shio.saikyo.i18n.TextDirection
import com.shio.saikyo.ocr.BoundingBox
import com.shio.saikyo.ocr.OcrClient
import com.shio.saikyo.ocr.OcrResult
import com.shio.saikyo.ocr.center
import com.shio.saikyo.ocr.contains
import com.shio.saikyo.util.filterMap
import com.shio.saikyo.util.getAudioManager
import com.shio.saikyo.util.getWindowManager
import com.shio.saikyo.util.startOverlayActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.pow

typealias ScreenshotService = (Rect) -> Bitmap

fun blockingDelay(ms: Long) = runBlocking { delay(ms) }

class OverlayVM(
    private val screenshotSvc: ScreenshotService,
    private val ocrClient: OcrClient,
) : ViewModel() {
    private var _bmp = MutableStateFlow<Bitmap?>(null)
    val bmp = _bmp.asStateFlow()

    private var ocrJob: Job? = null
    private var _ocrResult = MutableStateFlow<OcrResult?>(null)
    val ocrResult = _ocrResult.asStateFlow()

    private var _activityRunning = MutableStateFlow(false)
    val activityRunning = _activityRunning.asStateFlow()

    private var _activityShouldExit = MutableStateFlow(false)
    val activityShouldExit = _activityShouldExit.asStateFlow()

    fun takeScreenshotAndStartOcr(croppingRect: Rect) {
        val bmp = screenshotSvc(croppingRect)
        _bmp.value = bmp
        ocrJob = viewModelScope.launch {
            // TODO: implement app-wide language settings
            _ocrResult.value = ocrClient.process(bmp, LangCode.ja)
        }
    }

    fun clearScreenshotAndOcr() {
        _bmp.value = null
        _ocrResult.value = null
    }

    fun activityRunningIs(isRunning: Boolean) {
        _activityRunning.value = isRunning
    }

    fun activityShouldExitIs(shouldExit: Boolean) {
        _activityShouldExit.value = shouldExit
    }

}

fun Offset.toIntOffset(): IntOffset {
    return IntOffset(x.toInt(), y.toInt())
}

fun Point.toIntOffset(): IntOffset {
    return IntOffset(x, y)
}

fun Point.toOffset() = Offset(x.toFloat(), y.toFloat())

fun cropToContent(frame: Rect, base: Bitmap): Bitmap {
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
fun FloatingControl(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = Button(
    onClick = onClick,
    shape = RoundedCornerShape(100),
    border = BorderStroke(4.dp, Color.Black),
    colors = ButtonDefaults.buttonColors(containerColor = Color(1f, 1f, 1f, 0.5f)),
    modifier = modifier,
    content = {}
)

@Composable
fun AnalyzingControl(
    modifier: Modifier = Modifier
) = CircularProgressIndicator(
    modifier = modifier
        .fillMaxSize()
        .background(Color(1f, 1f, 1f, 0.5f), CircleShape),
    color = MaterialTheme.colorScheme.primary,
    trackColor = MaterialTheme.colorScheme.surfaceVariant,
)


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
fun OverlayCanvas(
    characters: List<BoundingBox>,
    selFirst: Int,
    selLast: Int,
    modifier: Modifier = Modifier,
    selectedColor: Color = LocalTextSelectionColors.current.backgroundColor,
    unselectedColor: Color = Color.Gray.copy(alpha = 0.2f)
) = Canvas(
    modifier = modifier.fillMaxSize()
) {
    for ((color, bounds) in arrayOf(
        unselectedColor to (0 to selFirst),
        selectedColor to (selFirst to selLast),
        unselectedColor to (selLast to characters.size)
    )) {
        val (from, to) = bounds
        for (bbID in from until to) {
            val bb = characters[bbID]
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
            }, color)
        }
    }
}

@Composable
fun OcrOverlay(
    bmp: Bitmap,
    ocr: OcrResult,
    onExit: () -> Unit
) {
    val (characteristics, charBBs, charStrs, bb2text, bb2tb, bb2ln, bb2wrd) = ocr

    val selectedColor = LocalTextSelectionColors.current.backgroundColor
    val unselectedColor = Color.Gray.copy(alpha = 0.3f)

    var beginSelPos by remember { mutableStateOf(Offset.Unspecified) }
    var endSelPos by remember { mutableStateOf(Offset.Unspecified) }

    val ctxMenuItems = ContextMenu.from(
        ContextMenuItem.Copy,
        ContextMenuItem.Search(LocalContext.current),
        ContextMenuItem.SelectAll.copy(fn = {
            // ???? the fuck is wrong with me
            beginSelPos = charBBs.first().center().toOffset()
            endSelPos = charBBs.last().center().toOffset()
        }),
        *LocalTextProcessors.current,
        maxVisible = 3
    )


    val selectionRange by remember {
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
                        } else { // the context menu was showing
                            beginSelPos = Offset.Unspecified
                            endSelPos = Offset.Unspecified

//                            ctxMenu.hide()
                        }
                    },
                    onLongPress = { offset ->
                        val ix = getHit(offset, charBBs)
                        if (ix >= 0) {
                            beginSelPos = offset
                            endSelPos = offset
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
            val (selFirst, selLast) = selectionRange

            for ((color, bounds) in arrayOf(
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
                    }, color)
                }
            }
        }

        // TODO: consider if the context menu + handles should be together...
        if (selectionRange.first in charBBs.indices && selectionRange.second in 1..charBBs.size) {
            when (characteristics.textDirection) {
                TextDirection.HORIZONTAL -> {
                    val beginAnchor = charBBs[selectionRange.first].bottomLeft
                    val endAnchor = charBBs[selectionRange.second - 1].bottomRight

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

                TextDirection.VERTICAL -> {
                    val beginAnchor = charBBs[selectionRange.first].topLeft
                    val endAnchor = charBBs[selectionRange.second - 1].bottomRight

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
        if (beginSelPos != Offset.Unspecified && endSelPos != Offset.Unspecified) {
//            ctxMenu.text = (bb2text[selectionRange.first]!!..bb2text[selectionRange.second - 1]!!).map {
//                charStrs[it]
//            }.fastJoinToString(separator = "")

        }
    }
}

/*@Composable
fun Overlay2(
    onExit: () -> Unit,
    vm: OverlayVM = viewModel()
) {
    val ctx = LocalContext.current
    val txtProcessors = LocalTextProcessors.current

    val ocrResult by vm.ocrResult.collectAsState()
    val characters = ocrResult!!.boundingBoxes

    var selFirst by remember { mutableIntStateOf(0) }
    var selLast by remember { mutableIntStateOf(0) }

    val contextMenu = remember(ctx, txtProcessors) {
        ContextMenu.from(
            ContextMenuItem.Copy,
            ContextMenuItem("Search") { selectedTxt ->
                ctx.startActivity(Intent().apply {
                    action = Intent.ACTION_PROCESS_TEXT

                    putExtra(Intent.EXTRA_PROCESS_TEXT, selectedTxt)
                    setClass(ctx, UIActivity::class.java)
                    flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
                })
            },
            ContextMenuItem.SelectAll.copy(fn = {
                // ???? the fuck is wrong with me
                beginSelPos = charBBs.first().center().toOffset()
                endSelPos = charBBs.last().center().toOffset()
            }),
            *txtProcessors.map { app ->
                val label = app.loadLabel(ctx.packageManager).toString()
                ContextMenuItem(title = label) { selectedTxt ->
                    ctx.startActivity(Intent().apply {
                        action = Intent.ACTION_PROCESS_TEXT
                        component = ComponentName(app.packageName, app.name)
                        putExtra(Intent.EXTRA_PROCESS_TEXT, selectedTxt)
                    })
                }
            }.toTypedArray(),
            maxVisible = 3
        )
    }

    val fm = LocalFocusManager.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val ix = getHit(offset, characters)
                        if (ix >= 0) {
                            selFirst = ix
                            selLast = selFirst + 1
                        } else {
                            fm.clearFocus()
                        }
                    },
                    onLongPress = { offset ->
                        val ix = getHit(offset, characters)
                        if (ix >= 0) {
                            selFirst = ix
                            selLast = selFirst + 1
                        } else {
                            // do nothing -- no long press on a char
                        }
                    },
                )
            }
    ) {
        val img by vm.bmp.collectAsState()

        Image(
            bitmap = img!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        OverlayCanvas(ocrResult!!.boundingBoxes, selFirst, selLast)

        if (characters.indices.contains(selFirst) && characters.indices.contains(selLast)) {
            when (ocrResult!!.characteristics.textDirection) {
                TextDirection.HORIZONTAL -> {
                    val beginAnchor = characters[selFirst].bottomLeft
                    val endAnchor = characters[selLast].bottomRight

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

                TextDirection.VERTICAL -> {
                    val beginAnchor = charBBs[selectionRange.first].topLeft
                    val endAnchor = charBBs[selectionRange.second - 1].bottomRight

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
    }

    BackHandler(onBack = onExit)
}*/

@Composable
fun OverlayControls(
    onHide: () -> Unit,
    onShow: () -> Unit,
    modifier: Modifier = Modifier,
    vm: OverlayVM = viewModel()
) = Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
) {
    val ctx = LocalContext.current
    val wm = ctx.getWindowManager()
    val am = ctx.getAudioManager()

    val amReq = remember {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).build()
    }

    val bmp by vm.bmp.collectAsState()
    val ocrResult by vm.ocrResult.collectAsState()
    val activityIsRunning by vm.activityRunning.collectAsState()

    val fullSize = modifier.fillMaxSize()

    if (bmp == null) {
        FloatingControl(onClick = {
            onHide()
            am.requestAudioFocus(amReq)

            blockingDelay(20)

            vm.takeScreenshotAndStartOcr(wm.maximumWindowMetrics.bounds)

            vm.activityShouldExitIs(false)
            ctx.startOverlayActivity()

            onShow()
        }, modifier = fullSize)
    } else if (!(activityIsRunning && ocrResult != null)) {
        AnalyzingControl(modifier = fullSize)
    } else {
        FloatingControl(onClick = {
            vm.activityShouldExitIs(true)
            vm.clearScreenshotAndOcr()
            am.abandonAudioFocusRequest(amReq)
        }, modifier = fullSize)
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