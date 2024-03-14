package com.shio.saikyo.ocr

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import com.shio.saikyo.i18n.LangCode
import com.shio.saikyo.i18n.TextFlow
import com.shio.saikyo.i18n.TextLayout
import com.shio.saikyo.i18n.Whitespace
import com.shio.saikyo.i18n.orthographies
import com.shio.saikyo.util.forEach
import com.shio.saikyo.util.forEachJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.average
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.first
import kotlin.collections.forEach
import kotlin.collections.last
import kotlin.collections.listOf
import kotlin.collections.max
import kotlin.collections.min
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.set
import kotlin.collections.sortedWith
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt


data class TextCharacteristics(
    val lang: LangCode,
    val layoutDir: TextLayout,
    val layoutFlow: TextFlow,
)

data class BoundingBox(
    var topLeft: Point,
    var topRight: Point,
    var bottomRight: Point,
    var bottomLeft: Point,
)

fun BoundingBox.contains(px: Int, py: Int): Boolean {
//    val mx = (topRight.first - topLeft.second).toDouble()
//    val my = (topRight.second - topLeft.second).toDouble()
//    val rot = atan2(my, mx) + Math.PI / 2
//
//    val rotX = cos(rot) * px - sin(rot) * py
//    val rotY = sin(rot) * px + cos(rot) * py
//
    val xs = intArrayOf(topLeft.x, topRight.x, bottomLeft.x, bottomRight.x)
    val ys = intArrayOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)

    var minX = Int.MAX_VALUE
    var maxX = Int.MIN_VALUE
    for (x in xs) {
        if (x < minX) {
            minX = x
        }

        if (x > maxX) {
            maxX = x
        }
    }

    var minY = Int.MAX_VALUE
    var maxY = Int.MIN_VALUE
    for (y in ys) {
        if (y < minY) {
            minY = y
        }

        if (y > maxY) {
            maxY = y
        }
    }

    return px in minX..<maxX && py in minY..<maxY
}

fun BoundingBox.center(): Point {
    val (tl, tr, br, bl) = this

    val centerX = (tl.x + tr.x + br.x + bl.x) / 4
    val centerY = (tl.y + tr.y + br.y + bl.y) / 4

    return Point(centerX, centerY)
}

fun BoundingBox.width(): Int {
    return sqrt(
        (topLeft.x - topRight.x).toFloat().pow(2) + (topLeft.y - topRight.y).toFloat().pow(2)
    ).roundToInt()
}

fun BoundingBox.height(): Int {
    return sqrt(
        (topLeft.x - bottomLeft.x).toFloat().pow(2) + (topLeft.y - bottomLeft.y).toFloat().pow(2)
    ).roundToInt()
}

data class Sz(
    val width: Int,
    val height: Int,
)


data class OcrResult(
    /**
     * Currently, the code uses Google's ML Kit and its accompanying OCR library to perform recognition.
     *
     * The result of this recognition is a nested list of text blocks -> lines -> "elements" (words) -> symbols (chars)
     *
     * The problem with this is that it does not lend itself well to the UI since the data is nested and attempts to find
     * taps on the screen would inevitably require lots of nested looping.
     *
     * Furthermore, the UI does not operate on the notion of "lines have words and characters", instead, we simply consider
     * each clickable point as a bounding box and have mappings of bounding boxes to their equivalent semantic relationship.
     *
     * Thus, the lists herein are maps (arrays) whose index is the bounding box ID and value is the text block/line/word ID.
     *
     * The bounding boxes of characters are kept, and the bounding boxes for lines and words and text blocks can be
     * reconstructed from these through an appropriate combination of the first and last corresponding char bounding box's corners.
     *
     * By iterating before and after any one particular char bounding box, it is possible to discover its siblings
     * and also which higher-order lexical construct it belongs to. The bounding boxes are sorted according to text layout and flow.
     */
    val characteristics: TextCharacteristics,

    val boundingBoxes: List<BoundingBox> = mutableListOf(),
    val text: String = String(),

    val bb2text: Map<Int, Int> = mutableMapOf(),
    val bb2textBlock: List<Int> = mutableListOf(),
    val bb2Line: List<Int> = mutableListOf(),
    val bb2Word: List<Int> = mutableListOf(),
)

private fun <T> keyWithHighestCount(mp: Map<T, Int>): T {
    var winnerKey: T? = null
    var max = Int.MIN_VALUE

    mp.forEach { (key, cnt) ->
        if (cnt > max) {
            max = cnt
            winnerKey = key
        }
    }

    return winnerKey!!
}

suspend fun execOcr(ocrClient: TextRecognizer, bmp: Bitmap): List<Text.TextBlock> {
    val done = Channel<List<Text.TextBlock>>(0, BufferOverflow.DROP_LATEST)
    ocrClient.process(bmp, 0)
        .addOnSuccessListener {
            done.trySend(it.textBlocks)
        }
        .addOnFailureListener {
            done.trySend(listOf())
        }

    val result = done.receive()
    done.close()

    return result
}

fun determineCharacteristics(textBlocks: List<Text.TextBlock>): TextCharacteristics {
    val langCounters = mutableMapOf<LangCode, Int>().apply {
        for (code in LangCode.entries) {
            this[code] = 0
        }
    }
    val flowCounters = mutableMapOf<TextFlow, Int>().apply {
        for (flow in TextFlow.entries) {
            this[flow] = 0
        }
    }

    for (tb in textBlocks) {
        for (ln in tb.lines) {
            val recognizedLang = ln.recognizedLanguage
            val bb = ln.boundingBox!!

            try {
                val enum = LangCode.valueOf(recognizedLang)
                langCounters[enum] = langCounters[enum]!! + 1
            } catch (_: IllegalArgumentException) {
                // do nothing... this is being unapologetically used as control flow
            }

            if (bb.height() > bb.width()) {
                flowCounters[TextFlow.VERTICAL] = flowCounters[TextFlow.VERTICAL]!! + 1
            } else {
                flowCounters[TextFlow.HORIZONTAL] = flowCounters[TextFlow.HORIZONTAL]!! + 1
            }
        }
    }

    val overallLang = keyWithHighestCount(langCounters)
    val overallFlow = keyWithHighestCount(flowCounters)

    // TODO: stop praying that nothing bad happens
    val overallDir = orthographies[overallLang]!![overallFlow]!! as TextLayout

    return TextCharacteristics(overallLang, overallDir, overallFlow)
}

fun determineCharacteristics(desiredLang: LangCode, textBlocks: List<Text.TextBlock>): TextCharacteristics {
    val langCounters = mutableMapOf<LangCode, Int>().apply {
        for (code in LangCode.entries) {
            this[code] = 0
        }
    }
    val flowCounters = mutableMapOf<TextFlow, Int>().apply {
        for (flow in TextFlow.entries) {
            this[flow] = 0
        }
    }

    for (tb in textBlocks) {
        for (ln in tb.lines) {
            val recognizedLang = ln.recognizedLanguage
            val bb = ln.boundingBox!!

            try {
                val enum = LangCode.valueOf(recognizedLang)
                langCounters[enum] = langCounters[enum]!! + 1
            } catch (_: IllegalArgumentException) {
                // do nothing... this is being unapologetically used as control flow
            }

            if (bb.height() > bb.width()) {
                flowCounters[TextFlow.VERTICAL] = flowCounters[TextFlow.VERTICAL]!! + 1
            } else {
                flowCounters[TextFlow.HORIZONTAL] = flowCounters[TextFlow.HORIZONTAL]!! + 1
            }
        }
    }

    val overallLang = keyWithHighestCount(langCounters)
    val overallFlow = keyWithHighestCount(flowCounters)

    // TODO: stop praying that nothing bad happens
    val overallDir = orthographies[overallLang]!![overallFlow]!! as TextLayout

    return TextCharacteristics(overallLang, overallDir, overallFlow)
}

fun beautifyLineBoundingBoxes(bbs: List<BoundingBox>, from: Int, to: Int, rot: Double, minBBSz: Sz) {
    // we first UNDO the rotation of the bounding boxes
    // then apply the beautification
    // and finally reapply it...
    // this could be sped up with a combined matrix transform -- explore if needed in the future.

    // cache for performance
    val cosRot = cos(rot)
    val sinRot = sin(rot)
    val (minWidth, minHeight) = minBBSz

    val (firstTL, firstTR, firstBR, firstBL) = bbs[from]
    val (lastTL, lastTR, lastBR, lastBL) = bbs[to - 1]

    val (lnCenterX, lnCenterY) = run {
        arrayOf(firstTL.x, lastTR.x, lastBR.x, firstBL.x).average().toInt() to
                arrayOf(firstTL.y, lastTR.y, lastBR.y, firstBL.y).average().toInt()
    }
    bbs.forEach(from, to) { bb ->
        val points = arrayOf(bb.topLeft, bb.topRight, bb.bottomRight, bb.bottomLeft)

        for (p in points) {
            p.x -= lnCenterX
            p.y -= lnCenterY
        }
        for (p in points) {
            val x = (cosRot * p.x + sinRot * p.y).toInt()
            val y = (-sinRot * p.x + cosRot * p.y).toInt()

            p.x = x
            p.y = y
        }
    }

    // fit the first and last line bounding boxes to the minimum requested bounding box size
    run {
        val lnWidth = lastTR.x - firstTL.x

        var topY = arrayOf(firstTL.y, firstTR.y, lastTL.y, lastTR.y).min()
        var botY = arrayOf(firstBL.y, firstBR.y, lastBL.y, lastBR.y).max()

        val lnHeight = botY - topY

        val extraX = (minWidth - lnWidth).coerceAtLeast(0) / 2
        val extraY = (minHeight - lnHeight).coerceAtLeast(0) / 2

        firstBL.x -= extraX
        lastTR.x += extraX
        firstBL.x -= extraX
        lastBR.x += extraX

        topY -= extraY
        botY += extraY
        firstTL.y = topY
        firstTR.y = topY
        firstBL.y = botY
        firstBR.y = botY

        lastTL.y = topY
        lastTR.y = topY
        lastBL.y = botY
        lastBR.y = botY
    }

    // fit the rest
    for (bbID in from + 1 until to) {
        val (pTL, pTR, pBR, pBL) = bbs[bbID - 1]
        val (cTL, cTR, cBR, cBL) = bbs[bbID]

        // fit the current bounding box's top and bottom baseline to the same (padded) Y value
        run {
            val topY = pTR.y
            val botY = pBR.y

            cTL.y = topY
            cTR.y = topY
            cBR.y = botY
            cBL.y = botY
        }

        // fit the prev and current bounding boxes' intra-x co-ordinates to be the midpoint between them
        run {
            val leftX = min(pTR.x, pBR.x)
            val rightX = min(cTL.x, cBL.x)
            val midX = (leftX + rightX) / 2

            pTR.x = midX
            pBR.x = midX
            cTL.x = midX
            cBL.x = midX
        }
    }

    bbs.forEach(from, to) { bb ->
        val points = arrayOf(bb.topLeft, bb.topRight, bb.bottomRight, bb.bottomLeft)

        for (p in points) {
            val x = (cosRot * p.x - sinRot * p.y).toInt()
            val y = (sinRot * p.x + cosRot * p.y).toInt()

            p.x = x
            p.y = y
        }
        for (p in points) {
            p.x += lnCenterX
            p.y += lnCenterY
        }
    }

    return // left in as a point to place a break-point
}

suspend fun doOcr(ocrClient: TextRecognizer, bmp: Bitmap, desiredLang: LangCode, minBBSz: Sz): OcrResult {
    val ocrResult = execOcr(ocrClient, bmp)

    val characteristics = determineCharacteristics(desiredLang, ocrResult)
    val sortFn: (Rect, Rect) -> Int = when (characteristics.layoutDir) {
        TextLayout.LTR -> { lhs, rhs ->
            val bbLeft = lhs
            val bbRight = rhs

            val xl = bbLeft.centerX()
            val yl = bbLeft.centerY()

            val xr = bbRight.centerX()
            val yr = bbRight.centerY()

            when (val compVertical = yl.compareTo(yr)) {
                0 -> xl.compareTo(xr)
                else -> compVertical
            }
        }

        TextLayout.RTL -> { lhs, rhs ->
            val bbLeft = lhs
            val bbRight = rhs

            val xl = bbLeft.centerX()
            val yl = bbLeft.centerY()

            val xr = bbRight.centerX()
            val yr = bbRight.centerY()

            when (val compHorizontal = xr.compareTo(xl)) {
                0 -> yl.compareTo(yr)
                else -> compHorizontal
            }
        }
    }

    val sep = orthographies[characteristics.lang]!![Whitespace.SEPARATOR]!!.toString()
    val newline = orthographies[characteristics.lang]!![Whitespace.NEWLINE]!!.toString()

    val bbs = mutableListOf<BoundingBox>()
    val textBuilder = StringBuilder()
    val bb2text = mutableMapOf<Int, Int>()
    val bb2tb = mutableListOf<Int>()
    val bb2ln = mutableListOf<Int>()
    val bb2wrd = mutableListOf<Int>()

    var charID = 0
    var tbID = 0
    var lnID = 0
    var wrdID = 0
    var bbID = 0

    ocrResult.sortedWith { l, r -> sortFn(l.boundingBox!!, r.boundingBox!!) }.forEachJoin(
        // append newline to each text block (if the orthography allows for it) to simulate a logical "break" in text
        onJoin = { _, _ ->
            textBuilder.append(newline)
            charID += newline.length
        },
        onLoop = { tb ->
            tb.lines.sortedWith { l, r -> sortFn(l.boundingBox!!, r.boundingBox!!) }.forEachJoin(
                // add a separator (if the orthography has one) to the end of each since this a graphical
                // break but not a logical "text" break
                onJoin = { _, _ ->
                    textBuilder.append(sep)
                    charID += sep.length
                },
                onLoop = { ln ->
                    val lnBBStartIx = bbs.size
                    ln.elements.forEachJoin(
                        onJoin = { l, r ->
                            // manually add a "whitespace" bounding block between delimited chunks in a line
                            val left = l.symbols.last().cornerPoints!!
                            val right = r.symbols.first().cornerPoints!!

                            bbs.add(
                                BoundingBox(
                                    topLeft = left[1],
                                    topRight = right[0],
                                    bottomRight = right[3],
                                    bottomLeft = left[2]

                                )
                            )

                            textBuilder.append(sep)
                            bb2text[bbID] = charID
                            bb2tb.add(tbID)
                            bb2ln.add(lnID)

                            charID += sep.length
                            bbID += 1
                        },
                        onLoop = { elem ->
                            elem.symbols.forEach { sym ->
                                bbs.add(sym.cornerPoints!!.let {
                                    BoundingBox(
                                        topLeft = it[0],
                                        topRight = it[1],
                                        bottomRight = it[2],
                                        bottomLeft = it[3]
                                    )
                                })

                                textBuilder.append(sym.text)
                                bb2text[bbID] = charID
                                bb2tb.add(tbID)
                                bb2ln.add(lnID)

                                charID += 1
                                bbID += 1
                            }
                        }
                    )
                    val lnBBEndIx = bbs.size

                    beautifyLineBoundingBoxes(bbs, lnBBStartIx, lnBBEndIx, Math.toRadians(ln.angle.toDouble()), minBBSz)

                    lnID += 1
                }
            )

            tbID += 1
        }
    )

    return OcrResult(characteristics, bbs, textBuilder.toString(), bb2text, bb2tb, bb2ln, bb2wrd)
}
