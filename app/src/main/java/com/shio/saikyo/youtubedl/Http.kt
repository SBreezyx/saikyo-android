package com.shio.saikyo.youtubedl

import com.shio.saikyo.ui.media.srtTimestamp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.userAgent
import io.ktor.util.appendIfNameAbsent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue
import java.security.MessageDigest
import kotlin.math.min

@OptIn(ExperimentalSerializationApi::class)
val jsonFmt = Json {
    this.encodeDefaults = true
    this.explicitNulls = true
    this.ignoreUnknownKeys = true
}

object Constants {
    const val MAX_DOWNLOAD_RATE = 9_898_989L

    const val DEFAULT_USER_AGENT = "com.google.android.youtube/17.36.4 (Linux; U; Android 12; GB) gzip"

    const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player"

    // This key doesn't appear to change
    const val API_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"

    const val DEFAULT_HL = "en"

    const val YOUTUBE_DOMAIN = "youtube.com"

    val DEFAULT_COOKIE = Cookie(
        name = "SOCS",
        value = "CAISNQgDEitib3FfaWRlbnRpdHlmcm9udGVuZHVpc2VydmVyXzIwMjMwODI5LjA3X3AxGgJlbiACGgYIgLC_pwY",
        domain = YOUTUBE_DOMAIN
    )

    // The only client that can handle age-restricted videos without authentication is the
    // TVHTML5_SIMPLY_EMBEDDED_PLAYER client.
    // This client does require signature deciphering, so we only use it as a fallback.
    const val DEFAULT_CLIENT_NAME = "TVHTML5_SIMPLY_EMBEDDED_PLAYER"

    const val DEFAULT_EMBED_URL = "https://www.youtube.com"
}

@Serializable
data class AndroidTestSuite(
    val clientName: String = "ANDROID_TESTSUITE",
    val clientVersion: String = "1.9",
    val androidSdkVersion: Int = 30,
    val hl: String = "en",
    val gl: String = "US",
    val utcOffsetMinutes: Int = 0,
)

@Serializable
data class Android(
    val clientName: String = "ANDROID",
    val clientVersion: String = "17.36.4",
    val androidSdkVersion: Int = 32
)

@Serializable
data class TvHtml5(
    val clientName: String = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
    val clientVersion: String = "2.0",
    val hl: String = "en",
    val gl: String = "US",
    val utcOffsetMinutes: Int = 0,
)

@Serializable
@XmlSerialName("transcript")
data class TimedText(
//    val format: String,

/*    @XmlElement
    val transcript: Transcript*/

    val children: List<Text>

) {
//    @Serializable
//    @XmlSerialName("transcript")
//    data class Transcript(
//        val children: List<Text>
//    )

    @Serializable
    @XmlSerialName("text")
    data class Text(
        val start: Float,
        val dur: Float,

        @XmlValue
        val text: String
    )
}

fun TimedText.toSubrip() = children.mapIndexed { ix, tt ->
    buildString {
        append(ix)
        append("\n")
        val start = (tt.start * 1000)
        val end = start + 1000 * tt.dur
        append("${start.toInt().srtTimestamp()} --> ${end.toInt().srtTimestamp()}")
        append("\n")
        append(tt.text)
    }
}.joinToString("\n\n")

object ApiKey {
    data class Config(
        val key: String = Constants.API_KEY
    )

    val plugin = createClientPlugin("Internal Api Key", ApiKey::Config) {
        val theKey = pluginConfig.key

        onRequest { req, _ ->
            req.setApiKey(theKey)
        }
    }
}

object Localization {
    data class Config(
        val lang: String = "en",
    )

    val plugin = createClientPlugin("Localization", Localization::Config) {
        val (lang) = pluginConfig

        onRequest { req, _ ->
            req.setLocalization(lang)
        }
    }
}

object Origin {
    val plugin = createClientPlugin("origin") {
        onRequest { req, _ ->
            req.setOrigin()
        }
    }
}

class CookieContainer(
    initialCookies: List<Cookie>
) : CookiesStorage {
    val cookies = initialCookies.toMutableList()

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        if (requestUrl.host == Constants.YOUTUBE_DOMAIN) {
            cookies.add(cookie)
        } else {
            // do nothing; we filter out all the non-youtube domains
        }
    }

    override suspend fun get(requestUrl: Url): List<Cookie> {
        return cookies
    }

    override fun close() {
        // this is not an owner; nothing to do
    }
}

object Auth {
    data class Config(
        var cookies: List<Cookie> = listOf()
    )

    fun String.sha1(): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = this.encodeToByteArray()
        md.update(bytes)
        val hash = md.digest()
        return hash.contentToString()
    }

    fun genAuthHeader(domain: String, cookies: List<Cookie>): String? {
        // TODO: confirm if the two loops can be combined
        val sessionId = cookies.find {
            it.name == "__Secure-3PAPISID"
        }?.value ?: cookies.find {
            it.name == "SAPISID"
        }?.value

        var header = sessionId
        if (!sessionId.isNullOrBlank()) {
            val ts = System.currentTimeMillis() / 1000

            val token = "$ts $sessionId $domain"
            val hash = token.sha1()

            header = "SAPISIDHASH ${ts}_${hash}"
        }

        return header
    }

    val plugin = createClientPlugin("auth", Auth::Config) {
        val client = client

        onRequest { req, _ ->
            req.setAuthorization(client.cookies(req.domain()))
        }
    }
}

fun HttpRequestBuilder.domain() = "${url.protocol.name}://${url.host}"

fun HttpRequestBuilder.setApiKey(key: String = Constants.API_KEY) {
    url.parameters.append("key", key)
}

fun HttpRequestBuilder.setLocalization(lang: String = Constants.DEFAULT_HL) {
    url.parameters.append("hl", lang)
}

fun HttpRequestBuilder.setOrigin(domain: String = domain()) {
    headers.append("Origin", domain)
}

fun HttpRequestBuilder.setUserAgent(ua: String = Constants.DEFAULT_USER_AGENT) = userAgent(ua)

fun HttpRequestBuilder.setAuthorization(cookies: List<Cookie>) {
    val auth = com.shio.saikyo.youtubedl.Auth.genAuthHeader(domain(), cookies)
    if (auth != null) {
        headers.appendIfNameAbsent("Authorization", auth)
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun TvHtml5.makeJsonRequest(videoId: String) = buildJsonObject {
    put("videoId", videoId)
    put("context", buildJsonObject {
        put("client", jsonFmt.encodeToJsonElement(this@makeJsonRequest))
        put("thirdparty", buildJsonObject {
            put("embedUrl", Constants.DEFAULT_EMBED_URL)
        })
    })
    put("playbackContext", buildJsonObject {
        put("contentPlaybackContext", buildJsonObject {
            put("signatureTimestamp", null)
        })
    })
}

fun AndroidTestSuite.makeJsonRequest(videoId: String) = buildJsonObject {
    put("videoId", videoId)
    put("context", buildJsonObject {
        put("client", jsonFmt.encodeToJsonElement(this@makeJsonRequest))
    })
}

fun Android.makeJsonRequest(videoId: String) = buildJsonObject {
    put("videoId", videoId)
    put("context", buildJsonObject {
        put("client", jsonFmt.encodeToJsonElement(this@makeJsonRequest))
    })
}

suspend fun HttpClient.getVideoInfo(videoId: String): Map<String, JsonElement> = coroutineScope {
    val android = async {
        val client = AndroidTestSuite()
        val res = post(Constants.PLAYER_URL) {
            contentType(ContentType.Application.Json)
            setBody(client.makeJsonRequest(videoId))
        }

        if (!res.status.isSuccess()) {
            println(res.status)
            throw Exception("ERROR: YOUTUBE DOWNLOAD FAILED")
        }

        res.body() as JsonObject
    }.await()

    val tv = async {
        val client = TvHtml5()
        val res = post(Constants.PLAYER_URL) {
            contentType(ContentType.Application.Json)
            setBody(client.makeJsonRequest(videoId))
        }

        if (!res.status.isSuccess()) {
            println(res.status)
            throw Exception("ERROR: YOUTUBE DOWNLOAD FAILED")
        }

        res.body() as JsonObject
    }.await()

    return@coroutineScope android.plus("captions" to tv.getOrDefault("captions", buildJsonObject { }))
}

suspend fun HttpClient.getCCData(ccUrl: String): TimedText {
    val req = prepareGet(ccUrl) {
        accept(ContentType.Text.Xml)
        // transcript timed text format
        for ((k, v) in arrayOf("format" to "1", "fmt" to "1")) {
            url.parameters.append(k, v)
        }
    }

    val res = req.execute()

    return res.body()
}

suspend fun HttpClient.getStreamData(streamUrl: String, from: Long, to: Long, cap: Long): ByteArray {
    val res = get(streamUrl) {
        val actualTo = min(to, cap)
        url.parameters.append("range", "${from}-${actualTo}")
    }

    if (!res.status.isSuccess()) {
        // TODO: solidify error-handling strategy
        println(res.status)
        throw Exception(res.toString())
    }

    return res.body()
}

suspend fun HttpClient.getThumbnailData(thumbUrl: String): ByteArray {
    return get(thumbUrl).body()
}