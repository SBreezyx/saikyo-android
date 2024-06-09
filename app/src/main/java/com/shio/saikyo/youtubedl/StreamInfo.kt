package com.shio.saikyo.youtubedl

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class ClosedCaptionStreamInfo(
    val url: String,

    // Should be BCP47
    val lang: String,
    val label: String,
)

@Serializable
data class VideoStreamInfo(
    val itag: Int = -1,
    val url: String = "",
    val mimeType: String = "",
    val bitrate: Int = -1,
    val width: Int = -1,
    val height: Int = -1,
    val contentLength: Long = -1,
    val quality: String = "",
    val fps: Int = -1,
    val qualityLabel: String = "",
    val averageBitrate: Int = -1,
    val approxDurationMs: Long = -1
)

@Serializable
data class AudioStreamInfo(
    val itag: Int = -1,
    val url: String = "",
    val mimeType: String = "",
    val bitrate: Int = -1,
    val contentLength: Long = -1,
    val quality: String = "",
    val averageBitrate: Int = -1,
    val audioQuality: String = "",
    val approxDurationMs: Long = -1,
    val audioSampleRate: Int = -1,
    val audioChannels: Int = -1
)

@Serializable
data class ThumbnailStreamInfo (
    val width: Int,
    val height: Int,
    val url: String,
)

data class DownloadInfo<T>(
    val data: T,

    // Progress indicator. 0 is not done, 1 is done. 0.5 = 50%
    val progress: Float
)

typealias Stream = Flow<DownloadInfo<ByteArray>>
typealias VideoStream = Stream
typealias AudioStream = Stream
typealias ClosedCaptionStream = Flow<DownloadInfo<TimedText>>
typealias ThumbnailStream = Flow<DownloadInfo<ByteArray>>