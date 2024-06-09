package com.shio.saikyo

import com.shio.saikyo.youtubedl.getInfo
import com.shio.saikyo.youtubedl.getStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.io.File
import kotlin.time.Duration

internal class VideoTest {
    @Test
    fun testCanGetTheStreamInfoOfAVideo() = runTest {
        val yt = com.shio.saikyo.youtubedl.YoutubeDL()

        val streams = yt.getInfo(VideoIds.VideoWithJapaneseCC)

        assertTrue(streams.videoStreams.size == 4)
        assertTrue(streams.audioStreams.size == 3)
    }

//    @Ignore("side effects")
    @Test
    fun testCanDownloadAVideoStream() = runTest(timeout = Duration.INFINITE) {
        val yt = com.shio.saikyo.youtubedl.YoutubeDL()
        val streams = yt.getInfo(VideoIds.Another)


        val hd1080p = streams.videoStreams.first()

        val vf = File("video2.mp4")
        yt.getStream(hd1080p).collect { (bytes, _) ->
            vf.appendBytes(bytes)
        }
    }
}