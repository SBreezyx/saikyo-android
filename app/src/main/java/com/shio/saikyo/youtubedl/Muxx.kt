package com.shio.saikyo.youtubedl

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaMuxer.OutputFormat
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import java.io.FileDescriptor
import java.nio.ByteBuffer
import kotlin.math.min


fun envelopes(ol: Long, il: Long, ir: Long, or: Long): Boolean {
    return ol <= il && ir <= or
}


fun writeSamples(
    muxer: MediaMuxer,
    trackIndex: Int,
    extractor: MediaExtractor,
    bufferSize: Int = 2 shl 24 // 8 MiB
) {
    val buf = ByteBuffer.allocate(bufferSize)
    val info = MediaCodec.BufferInfo()

    do {
        extractor.readSampleData(buf, 0)

        info.apply {
            this.size = extractor.sampleSize.toInt()

            this.offset = 0

            @SuppressLint("WrongConstant")
            this.flags = extractor.sampleFlags

            this.presentationTimeUs = extractor.sampleTime
        }

        muxer.writeSampleData(trackIndex, buf, info)
        buf.rewind()
    } while (extractor.advance())
}


fun mux(
    videoFd: FileDescriptor, audioFd: FileDescriptor, outputFd: FileDescriptor,
    outputFormat: Int = OutputFormat.MUXER_OUTPUT_MPEG_4
) {
    val videoExtractor = MediaExtractor().apply {
        setDataSource(videoFd)
        selectTrack(0)
    }

    val audioExtractor = MediaExtractor().apply {
        setDataSource(audioFd)
        selectTrack(0)
    }

    val muxer = MediaMuxer(outputFd, outputFormat)
    val vf = videoExtractor.getTrackFormat(0)
    val af = audioExtractor.getTrackFormat(0)
    val videoTrackIndex = muxer.addTrack(vf)
    val audioTrackIndex = muxer.addTrack(af)

    muxer.start()

    writeSamples(muxer, videoTrackIndex, videoExtractor)
    writeSamples(muxer, audioTrackIndex, audioExtractor)

    muxer.stop()

    videoExtractor.release()
    audioExtractor.release()
    muxer.release()
}


/*
fun mix(
    resolver: ContentResolver,
    videoInfo: VideoInfo,

    vst: StreamMediaSource,
    ast: StreamMediaSource,
    cst: ClosedCaptionStream?,
    done: MutableStateFlow<Boolean>
) = thread {
    val videosRoot = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    val videoDetails = ContentValues().apply {
        put(MediaStore.Video.Media.IS_PENDING, 1)

        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.DISPLAY_NAME, videoInfo.title)
        put(MediaStore.Video.Media.AUTHOR, videoInfo.author)
        put(MediaStore.Video.Media.DESCRIPTION, videoInfo.description)
    }

    val videoUri = resolver.insert(videosRoot, videoDetails)

    if (videoUri != null) {
        val vfd = resolver.openFileDescriptor(videoUri, "rw")

        if (vfd != null) {
            val mx = MediaMuxer(vfd.fileDescriptor, OutputFormat.MUXER_OUTPUT_MPEG_4)

            mx.mux(vst, ast)

            mx.release()

            videoDetails.apply {
                clear()
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            resolver.update(videoUri, videoDetails, null)
            vfd.close()
        } else {
            resolver.delete(videoUri, null)
        }
    }


    val subRoot = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val subDetails = ContentValues().apply {
        put(MediaStore.Files.FileColumns.MIME_TYPE, "application/x-subrip")
        put(MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.MEDIA_TYPE_SUBTITLE)
        put(MediaStore.Files.FileColumns.DISPLAY_NAME, videoInfo.title)
    }

    val uri = resolver.insert(subRoot, subDetails)!!

    val output = resolver.openOutputStream(uri)
    output?.use {
        runBlocking {
            cst?.collect { (tt, _) ->
                val sub = tt.body.children.mapIndexed { ix, p ->
                    """
                        ${ix}
                        ${p.t.srtTimestamp()} --> ${(p.t + p.d).srtTimestamp()}
                        ${p.text}
                    """.trimIndent()
                }.joinToString("\n\n")

                output.write(sub.toByteArray())
            }
        }
    }

    done.value = true
}*/

//                val uri = resolver.insert(
//                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
//                    ContentValues().apply {
//                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
//                        put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
//                    }
//                )!!


//                val t1 = resolver.insert(
//                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
//                    ContentValues().apply {
//                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
//                        put(MediaStore.Video.Media.DISPLAY_NAME, displayName + "tmp")
//                    }
//                )!!
//
//                val t2 = resolver.insert(
//                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
//                    ContentValues().apply {
//                        put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
//                        put(MediaStore.Audio.Media.DISPLAY_NAME, displayName + "tmp")
//                    }
//                )!!
