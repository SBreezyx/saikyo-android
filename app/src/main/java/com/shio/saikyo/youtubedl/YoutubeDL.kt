package com.shio.saikyo.youtubedl

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.xml.xml
import io.ktor.util.appendIfNameAbsent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue
import java.security.MessageDigest
import kotlin.math.min


enum class Playability {
    Public,
    Private,
    None,
}

data class VideoInfo(
    val id: String,
    val title: String,
    val lengthSeconds: Int,
    val channelId: String,
    val author: String,
    val description: String,
    val isLive: Boolean,
    val playability: Playability,
)

data class VideoManifest(
    val metadata: VideoInfo,
    val videoStreams: List<VideoStreamInfo>,
    val audioStreams: List<AudioStreamInfo>,
    val ccStreams: List<ClosedCaptionStreamInfo>,
    val audioTrack2ccTrack: Map<Int, List<Int>>,
    val thumbnailStreams: List<ThumbnailStreamInfo>,
)

suspend fun fetchVideoInfo(http: HttpClient, videoId: String): VideoManifest {
    val js = http.getVideoInfo(videoId)

    val videoDetails = js["videoDetails"]!!.jsonObject
    val playability = when {
        js["playabilityStatus"]!!.jsonObject["status"]!!.jsonPrimitive.content == "OK" -> {
            if (videoDetails["isPrivate"]!!.jsonPrimitive.boolean) {
                Playability.Private
            } else {
                Playability.Public
            }
        }

        else -> {
            Playability.None
        }
    }

    val videoStreams = mutableListOf<VideoStreamInfo>()
    val audioStreams = mutableListOf<AudioStreamInfo>()
    val fmts = js["streamingData"]!!.jsonObject["adaptiveFormats"]!!.jsonArray

    for (s in fmts) {
        if (s.jsonObject["mimeType"]!!.jsonPrimitive.content.startsWith("video")) {
            videoStreams.add(jsonFmt.decodeFromJsonElement(s) as VideoStreamInfo)
        } else {
            audioStreams.add(jsonFmt.decodeFromJsonElement(s) as AudioStreamInfo)
        }
    }

    val captions = js["captions"]!!.jsonObject["playerCaptionsTracklistRenderer"]!!
    val captionTracks = captions
        .jsonObject["captionTracks"]!!
        .jsonArray


    val ccs = mutableListOf<ClosedCaptionStreamInfo>()
    for (cct in captionTracks) {
        val url = cct.jsonObject["baseUrl"]!!.jsonPrimitive.content
        val lang = cct.jsonObject["languageCode"]!!.jsonPrimitive.content
        val label = cct.jsonObject["name"]!!.jsonObject["runs"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content


        ccs.add(ClosedCaptionStreamInfo(url, lang, label))
    }

    val audioTrx = captions.jsonObject["audioTracks"]!!.jsonArray
    val audio2cc = mutableMapOf<Int, List<Int>>()
    for ((i, atr) in audioTrx.withIndex()) {
        val captionIndicies = atr.jsonObject["captionTrackIndices"]!!.jsonArray
        audio2cc[i] = captionIndicies.map { i }
    }

    val videoTitle = videoDetails["title"]!!.jsonPrimitive.content
    val videoChannelId = videoDetails["channelId"]!!.jsonPrimitive.content
    val length = videoDetails["lengthSeconds"]!!.jsonPrimitive.int
    val videoDesc = videoDetails["shortDescription"]!!.jsonPrimitive.content
    val videoAuthor = videoDetails["author"]!!.jsonPrimitive.content
    val videoIsLive = videoDetails["isLiveContent"]!!.jsonPrimitive.boolean

    val thumbnailStreams = videoDetails["thumbnail"]!!.jsonObject["thumbnails"]!!.jsonArray.map {
        jsonFmt.decodeFromJsonElement(it) as ThumbnailStreamInfo
    }

    return VideoManifest(
        metadata = VideoInfo(
            id = videoId,
            title = videoTitle,
            lengthSeconds = length,
            channelId = videoChannelId,
            author = videoAuthor,
            description = videoDesc,
            isLive = videoIsLive,
            playability = playability,
        ),
        videoStreams = videoStreams,
        audioStreams = audioStreams,
        ccStreams = ccs,
        audioTrack2ccTrack = audio2cc,
        thumbnailStreams = thumbnailStreams
    )
}

/**
 * For most streams, YouTube limits transfer speed to match the video playback rate.
 * This helps them avoid unnecessary bandwidth, but for us it's a hindrance because
 * we want to download the stream as fast as possible.
 * To solve this, we divide the logical stream up into multiple segments and download
 * them all separately.
 */
fun fetchAVStreamData(http: HttpClient, streamUrl: String, contentLength: Long, maxSegmentLength: Long) = flow {
    var amtDone = 0L
    while (amtDone < contentLength) {
        val bytes = http.getStreamData(streamUrl, amtDone, amtDone + maxSegmentLength, contentLength)

        // this is how much we actually read
        amtDone += bytes.size

        emit(DownloadInfo(bytes, amtDone.toFloat() / contentLength))
    }
}

fun fetchCCStreamData(http: HttpClient, streamUrl: String) = flow {
    val xml = http.getCCData(streamUrl)

    emit(DownloadInfo(xml, 1f))
}

fun fetchThumbnailStreamData(http: HttpClient, streamUrl: String) = flow {
    val data = http.getThumbnailData(streamUrl)

    emit(DownloadInfo(data, 1f))
}

suspend fun YoutubeDL.getInfo(videoId: String) = fetchVideoInfo(http, videoId)

fun YoutubeDL.getStream(stream: AudioStreamInfo, maxDownloadRate: Long = this.maxDownloadRate): AudioStream =
    fetchAVStreamData(
        http, stream.url, stream.contentLength, maxDownloadRate
    )

fun YoutubeDL.getStream(stream: VideoStreamInfo, maxDownloadRate: Long = this.maxDownloadRate): VideoStream =
    fetchAVStreamData(
        http, stream.url, stream.contentLength, maxDownloadRate
    )

suspend fun YoutubeDL.getStreamWindow(
    stream: AudioStreamInfo,
    byteStart: Long = 0,
    byteEnd: Long = stream.contentLength
) = http.getStreamData(stream.url, byteStart, byteEnd, stream.contentLength)

suspend fun YoutubeDL.getStreamWindow(
    stream: VideoStreamInfo,
    byteStart: Long = 0,
    byteEnd: Long = stream.contentLength
) = http.getStreamData(stream.url, byteStart, byteEnd, stream.contentLength)

fun YoutubeDL.getStream(stream: ClosedCaptionStreamInfo): ClosedCaptionStream = fetchCCStreamData(http, stream.url)

fun YoutubeDL.getStream(stream: ThumbnailStreamInfo): ThumbnailStream = fetchThumbnailStreamData(http, stream.url)

class YoutubeDL(
    val http: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()

            xml(contentType = ContentType.Text.Xml)
        }
        install(ApiKey.plugin)
        install(Localization.plugin)
        install(Origin.plugin)
        install(UserAgent) {
            agent = Constants.DEFAULT_USER_AGENT
        }
        install(HttpCookies) {
            storage = CookieContainer(listOf(Constants.DEFAULT_COOKIE))
        }
        install(Auth.plugin)
        install(HttpRequestRetry) {
            retryIf(maxRetries = 5) { _, res ->
                res.status.value >= 500 && retryCount > 0
            }
        }
    },
    var maxDownloadRate: Long = Constants.MAX_DOWNLOAD_RATE
)