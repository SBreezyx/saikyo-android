package com.shio.saikyo

import com.shio.saikyo.youtubedl.YoutubeDL
import com.shio.saikyo.youtubedl.getInfo
import com.shio.saikyo.youtubedl.getStream
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ClosedCaptionTest {

    @Test
    fun testCanGetListOfAvailableClosedCaptionsOnAVideo() = runTest {
        val yt = YoutubeDL()

        val info = yt.getInfo(VideoIds.VideoWithJapaneseCC)

        assertTrue(info.ccStreams.size == 2)
        assertTrue(info.ccStreams.first().lang == "ja")
    }

    @Test
    fun testCanGetASpecificCCTrackFromAVideo() = runTest {
        val yt = YoutubeDL()

        val info = yt.getInfo(VideoIds.VideoWithJapaneseCC)

        val jaCC = info.ccStreams.first()
        assertNotNull(jaCC)

        val (ccs, _) = yt.getStream(jaCC).single()

        assertTrue(ccs.children.size == 661)
    }

}