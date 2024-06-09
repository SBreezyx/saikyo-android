package com.shio.saikyo.ui.media

import android.os.Bundle
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.shio.saikyo.db.MediaDb
import com.shio.saikyo.db.PlaybackState
import com.shio.saikyo.db.Subtitle
import kotlinx.coroutines.runBlocking

object CustomSessionCommands {
    val SeekPreviousSubtitle = SessionCommand("prev-sub", Bundle.EMPTY)

    val SeekToSubtitle = SessionCommand("specific-sub", Bundle.EMPTY)

    val SeekNextSubtitle = SessionCommand("next-sub", Bundle.EMPTY)

    val Backgrounding = SessionCommand("backgrounding", Bundle.EMPTY)

    val Foregrounding = SessionCommand("foregrounding", Bundle.EMPTY)
}

fun MediaController.background() = sendCustomCommand(
    CustomSessionCommands.Backgrounding, Bundle.EMPTY
)

fun MediaController.foreground() = sendCustomCommand(
    CustomSessionCommands.Foregrounding, Bundle.EMPTY
)

fun Player.seekToPreviousSubtitle() = (this as? MediaController)?.sendCustomCommand(
    CustomSessionCommands.SeekPreviousSubtitle, Bundle.EMPTY
)

fun Player.seekToSubtitle(subIndex: Int) = (this as? MediaController)?.sendCustomCommand(
    CustomSessionCommands.SeekToSubtitle, Bundle().apply { putInt("subIx", subIndex) }
)

fun Player.seekToNextSubtitle() = (this as? MediaController)?.sendCustomCommand(
    CustomSessionCommands.SeekNextSubtitle, Bundle.EMPTY
)

fun Player.seekTo(percentage: Float) = seekTo(
    (duration * percentage).toLong()
)

fun Player.scrubForward(deltaMs: Long) = seekTo(currentPosition + deltaMs)

fun Player.scrubBackward(deltaMs: Long) = seekTo(currentPosition - deltaMs)

fun List<Subtitle>.indexOfLastSubtitle(ts: Long) = indexOfLast { it.startMs <= ts }

fun Long.toTimestamp(): String {
    var duration = this

    val ms2hour = 60 * 60 * 1000
    val ms2min = 60 * 1000
    val ms2sec = 1000

    val hours = duration / ms2hour; duration -= hours * ms2hour
    val minutes = duration / ms2min; duration -= minutes * ms2min
    val seconds = duration / ms2sec

    return if (hours == 0L) {
        "%d:%02d".format(minutes, seconds)
    } else {
        "%d:%d:%02d".format(hours, minutes, seconds)
    }
}

fun Int.toTimestamp() = (this * 1000L).toTimestamp()


fun Int.srtTimestamp(): String {
    var duration = this

    val ms2hour = 60 * 60 * 1000
    val ms2min = 60 * 1000
    val ms2sec = 1000

    val hours = duration / ms2hour; duration -= hours * ms2hour
    val minutes = duration / ms2min; duration -= minutes * ms2min
    val seconds = duration / ms2sec; duration -= seconds * ms2sec

    return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, duration)
}

fun Long.toSeconds(): Int = (this / 1000).toInt() + 1

@UnstableApi
class PlaybackEventist(
    val player: ExoPlayer,
    val mediaDb: MediaDb
) : ForwardingPlayer(player) {
    val pbData = mediaDb.playbackData()
    val mediaData = mediaDb.mediaData()

    var subIndex = -1
    var subs: List<Subtitle> = listOf()

    fun seekToPreviousSubtitle() {
        if (subIndex > 0) {
            seekToCurrentSubtitle(subIndex - 1)
        } else {
            seekTo(0)
        }
    }

    fun seekToCurrentSubtitle(subIx: Int = subIndex) {
        val sub = subs[subIx]

        pbData.putPlaybackState {
            it.copy(
                currTs = sub.startMs,
                currSubtitleIndex = subIndex,
                currSubtitleText = sub.text
            )
        }
        subIndex = subIx

        super.seekTo(sub.startMs)
    }

    fun seekToNextSubtitle() {
        if (subIndex < subs.lastIndex) {
            seekToCurrentSubtitle(subIndex + 1)
        } else {
            seekTo(duration)
        }
    }

    override fun seekTo(ts: Long) {
        val actualTs = ts.coerceIn(0, duration)

        subIndex = subs.indexOfLastSubtitle(ts)
        pbData.putPlaybackState {
            it.copy(
                currTs = ts,
                currSubtitleIndex = subIndex,
                currSubtitleText = subs.getOrNull(subIndex)?.text
            )
        }

        super.seekTo(actualTs)
    }

    override fun seekForward() = seekTo(currentPosition + seekForwardIncrement)

    override fun seekBack() = seekTo(currentPosition - seekBackIncrement)

    override fun play() {
        pbData.putPlaybackState {
            it.copy(isPlaying = true)
        }

        super.play()
    }

    override fun pause() {
        pbData.putPlaybackState {
            it.copy(isPlaying = false)
        }

        super.pause()
    }

    override fun setPlaybackSpeed(speed: Float) {
        pbData.putPlaybackState {
            it.copy(
                playbackSpeed = speed
            )
        }
        super.setPlaybackSpeed(speed)
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        pbData.putPlaybackState {
            it.copy(
                playbackSpeed = playbackParameters.speed
            )
        }
        super.setPlaybackParameters(playbackParameters)
    }

    override fun prepare() {
        val mediaUri = currentMediaItem!!.localConfiguration!!.uri

        val info = runBlocking { mediaData.getSubtitlesFor(mediaUri.toString()) }

        (1 until info.lengthMs.toSeconds()).forEach { sec ->
            val ts = sec.toLong() * 1000
            player.createMessage { _, _ ->
                subIndex = subs.indexOfLastSubtitle(ts)
                pbData.putPlaybackState {
                    it.copy(
                        currTs = ts,
                        currSubtitleIndex = subIndex,
                        currSubtitleText = subs.getOrNull(subIndex)?.text
                    )
                }
            }.apply {
                setPayload(mediaDb.playbackData())
                setPosition(ts)
                setDeleteAfterDelivery(false)
            }.send()
        }

        info.subs.forEachIndexed { ix, sub ->
            player.createMessage { _, _ ->
                pbData.putPlaybackState {
                    it.copy(currSubtitleIndex = ix, currSubtitleText = sub.text)
                }
            }.apply {
                setPosition(sub.startMs)
                setDeleteAfterDelivery(false)
            }.send()

            player.createMessage { _, _ ->
                pbData.putPlaybackState {
                    it.copy(currSubtitleIndex = ix, currSubtitleText = null)
                }
            }.apply {
                setPosition(sub.endMs)
                setDeleteAfterDelivery(false)
            }.send()
        }

        subs = info.subs
        pbData.putPlaybackState {
            it.copy(
                isPlaying = false,
                currTs = 0,
                playbackSpeed = 1f,
                currSubtitleIndex = -1,
                currSubtitleText = null,
            )
        }

        super.prepare()
    }

    override fun stop() {
        pbData.putPlaybackState { PlaybackState.UNINITIALIZED }

        super.stop()
    }
}