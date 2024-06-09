package com.shio.saikyo.parse

import android.text.Spanned
import android.text.TextUtils
import androidx.annotation.OptIn
import androidx.media3.common.text.Cue
import androidx.media3.common.text.Cue.AnchorType
import androidx.media3.common.util.Log
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import com.shio.saikyo.db.Subtitle
import java.nio.charset.Charset
import java.util.regex.Matcher
import java.util.regex.Pattern

// Fractional positions for use when alignment tags are present.
const val START_FRACTION = 0.08f;
const val END_FRACTION = 1 - START_FRACTION;
const val MID_FRACTION = 0.5f;

private const val TAG = "SubripDecoder"

// Some SRT files don't include hours or milliseconds in the timecode, so we use optional groups.
const val SUBRIP_TIMECODE = "(?:(\\d+):)?(\\d+):(\\d+)(?:,(\\d+))?"
val SUBRIP_TIMING_LINE =
    Pattern.compile("\\s*($SUBRIP_TIMECODE)\\s*-->\\s*($SUBRIP_TIMECODE)\\s*")

// NOTE: Android Studio's suggestion to simplify '\\}' is incorrect [internal: b/144480183].
val SUBRIP_TAG_PATTERN = Pattern.compile("\\{\\\\.*?\\}")
val SUBRIP_ALIGNMENT_TAG = Regex("\\{\\\\an[1-9]\\}")

// Alignment tags for SSA V4+.
const val ALIGN_BOTTOM_LEFT = "{\\an1}"
const val ALIGN_BOTTOM_MID = "{\\an2}"
const val ALIGN_BOTTOM_RIGHT = "{\\an3}"
const val ALIGN_MID_LEFT = "{\\an4}"
const val ALIGN_MID_MID = "{\\an5}"
const val ALIGN_MID_RIGHT = "{\\an6}"
const val ALIGN_TOP_LEFT = "{\\an7}"
const val ALIGN_TOP_MID = "{\\an8}"
const val ALIGN_TOP_RIGHT = "{\\an9}"

val SUPPORTED_CHARSETS = setOf(
    Charsets.US_ASCII,
    Charsets.UTF_8,
    Charsets.UTF_16,
    Charsets.UTF_16BE,
    Charsets.UTF_16LE
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun detectUtfCharset(data: ParsableByteArray, defaultCharset: Charset = Charsets.UTF_8): Charset {
    val charset = data.readUtfCharsetFromBom()
    return charset ?: defaultCharset
}

fun processLine(str: String, tags: MutableList<String>): String {
    var line = str
    line = line.trim { it <= ' ' }
    var removedCharacterCount = 0
    val processedLine = java.lang.StringBuilder(line)
    val matcher = SUBRIP_TAG_PATTERN.matcher(line)
    while (matcher.find()) {
        val tag = matcher.group()
        tags.add(tag)
        val start = matcher.start() - removedCharacterCount
        val tagLength = tag.length
        processedLine.replace(start, start + tagLength, "")
        removedCharacterCount += tagLength
    }
    return processedLine.toString()
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun buildCue(text: Spanned, alignmentTag: String?): Cue {
    val cue = Cue.Builder().setText(text)
    if (alignmentTag == null) {
        return cue.build()
    }
    when (alignmentTag) {
        ALIGN_BOTTOM_LEFT, ALIGN_MID_LEFT, ALIGN_TOP_LEFT -> {
            cue.positionAnchor = Cue.ANCHOR_TYPE_START
        }

        ALIGN_BOTTOM_RIGHT, ALIGN_MID_RIGHT, ALIGN_TOP_RIGHT -> {
            cue.positionAnchor = Cue.ANCHOR_TYPE_END
        }

        ALIGN_BOTTOM_MID, ALIGN_MID_MID, ALIGN_TOP_MID -> {
            cue.positionAnchor = Cue.ANCHOR_TYPE_MIDDLE
        }

        else -> {
            cue.positionAnchor = Cue.ANCHOR_TYPE_MIDDLE
        }
    }

    when (alignmentTag) {
        ALIGN_BOTTOM_LEFT, ALIGN_BOTTOM_MID, ALIGN_BOTTOM_RIGHT -> {
            cue.lineAnchor = Cue.ANCHOR_TYPE_END
        }

        ALIGN_TOP_LEFT, ALIGN_TOP_MID, ALIGN_TOP_RIGHT -> {
            cue.lineAnchor = Cue.ANCHOR_TYPE_START
        }

        ALIGN_MID_LEFT, ALIGN_MID_MID, ALIGN_MID_RIGHT -> {
            cue.lineAnchor = Cue.ANCHOR_TYPE_MIDDLE
        }

        else -> {
            cue.lineAnchor = Cue.ANCHOR_TYPE_MIDDLE
        }
    }

    return cue
        .setPosition(getFractionalPositionForAnchorType(cue.positionAnchor))
        .setLine(getFractionalPositionForAnchorType(cue.lineAnchor), Cue.LINE_TYPE_FRACTION)
        .build()
}

fun parseTimecodeMs(matcher: Matcher, groupOffset: Int): Long {
    val hours = matcher.group(groupOffset + 1)
    var timestampMs = if (hours != null) hours.toLong() * 60 * 60 * 1000 else 0
    timestampMs += matcher.group(groupOffset + 2)!!.toLong() * 60 * 1000
    timestampMs += matcher.group(groupOffset + 3)!!.toLong() * 1000

    val millis = matcher.group(groupOffset + 4)

    if (millis != null) {
        timestampMs += millis.toLong()
    }

    return timestampMs
}

fun getFractionalPositionForAnchorType(@AnchorType anchorType: Int): Float {
    return when (anchorType) {
        Cue.ANCHOR_TYPE_START -> START_FRACTION
        Cue.ANCHOR_TYPE_MIDDLE -> MID_FRACTION
        Cue.ANCHOR_TYPE_END -> END_FRACTION
        Cue.TYPE_UNSET -> throw IllegalArgumentException()
        else -> throw IllegalArgumentException()
    }
}

@OptIn(UnstableApi::class)
fun parse(rawData: ByteArray): List<Subtitle> {
    val subripData = ParsableByteArray(rawData, rawData.size)
    val charset = detectUtfCharset(subripData)

    // this guarantees readLine() will never throw
    if (charset !in SUPPORTED_CHARSETS) {
        return listOf()
    }

    val subtitles = mutableListOf<Subtitle>()

    var currentLine: String?
    while (subripData.readLine(charset).also { currentLine = it } != null) {
        if (currentLine!!.isEmpty()) {
            // Skip blank lines.
            continue
        }

        // Parse and check the index line.
        try {
            currentLine!!.toInt()
        } catch (e: NumberFormatException) {
            Log.w(TAG, "Skipping invalid index: $currentLine")
            continue
        }

        // Read and parse the timing line.
        currentLine = subripData.readLine(charset)
        if (currentLine == null) {
            Log.w(TAG, "Unexpected end")
            break
        }

        val matcher = SUBRIP_TIMING_LINE.matcher(currentLine!!)
        var startTimeMs: Long? = null
        var endTimeMs: Long? = null
        if (matcher.matches()) {
            startTimeMs = parseTimecodeMs(matcher, 1)
            endTimeMs = parseTimecodeMs(matcher, 6)
        }

        if (startTimeMs == null || endTimeMs == null) {
//            Log.w(TAG, "Skipping invalid timing: $currentLine")
            continue
        }


        // Read and parse the text and tags.
        currentLine = subripData.readLine(charset)
        val textBuilder = StringBuilder()
        val tags = mutableListOf<String>()
        while (!TextUtils.isEmpty(currentLine)) {
            if (textBuilder.isNotEmpty()) {
                textBuilder.append("\n")
            }
            textBuilder.append(processLine(currentLine!!, tags))
            currentLine = subripData.readLine(charset)
        }
//        val text = Html.fromHtml(textBuilder.toString(), Html.FROM_HTML_MODE_LEGACY)
//        var alignmentTag: String? = null
//        for (i in tags.indices) {
//            val tag = tags[i]
//            if (tag.matches(SUBRIP_ALIGNMENT_TAG)) {
//                alignmentTag = tag
//                // Subsequent alignment tags should be ignored.
//                break
//            }
//        }

        subtitles.add(Subtitle(startTimeMs, endTimeMs, textBuilder.toString()))
    }

    return subtitles
}