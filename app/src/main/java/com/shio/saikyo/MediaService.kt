package com.shio.saikyo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.shio.saikyo.ui.media.CustomSessionCommands
import com.shio.saikyo.ui.media.PlaybackEventist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.properties.Delegates
import kotlin.properties.Delegates.notNull


fun <T : Any> Bundle.putMediaUri(uri: T) = putString("mediaUri", uri.toString())

fun Bundle.getMediaUri() = getString("mediaUri")

fun Bundle.getSubIndex() = getInt("subIx")

suspend fun scanForMedia(
    ctx: Context,
    videoSink: MutableMap<String, MediaItem>,
    mp3sSink: MutableMap<String, MediaItem>,
    sleepTimeMs: Long = 30_000
) {
    val cr = ctx.contentResolver
    val rootUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)


    while (true) {
        val res = cr.query(
            rootUri,
            arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.RELATIVE_PATH,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.TITLE,
                MediaStore.Files.FileColumns.DURATION,
            ),
            null,
            null
        )

        res.use { cursor ->
            while (cursor!!.moveToNext()) {
                when (cursor.getInt(0)) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> {

                    }

                    MediaStore.Files.FileColumns.MEDIA_TYPE_SUBTITLE -> {

                    }

//                    MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> {
//
//                    }

                    else -> {

                    }
                }


            }
        }



        delay(sleepTimeMs)
    }
}

@UnstableApi
class BackgroundLanguageReactor: MediaLibraryService()
    , MediaLibraryService.MediaLibrarySession.Callback
{
    private lateinit var player: PlaybackEventist
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var coro: CoroutineScope
    private var isForeground by notNull<Boolean>()

    val videos = mutableMapOf<String, MediaItem>()
    val mp3s = mutableMapOf<String, MediaItem>()

    override fun onCreate() {
        super.onCreate()

        player = PlaybackEventist(
            ExoPlayer.Builder(this)
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setUseLazyPreparation(false)
                .build(),
            (application as SaikyoApp).mediaDb
        )

        mediaSession = MediaLibrarySession.Builder(this, player, this)
            .setCustomLayout(buildList {
                add(
                    CommandButton.Builder()
                        .setDisplayName(getString(R.string.previous_subtitle_btn_desc))
                        .setSessionCommand(CustomSessionCommands.SeekPreviousSubtitle)
                        .setIconResId(R.drawable.skip_previous_24dp_fill)
                        .build()
                )

                add(
                    CommandButton.Builder()
                        .setDisplayName(getString(R.string.next_subtitle_btn_desc))
                        .setSessionCommand(CustomSessionCommands.SeekNextSubtitle)
                        .setIconResId(R.drawable.skip_next_24dp_fill)
                        .build()
                )
            })
            .build()

        isForeground = true

        coro = CoroutineScope(Dispatchers.IO)

        coro.launch {
            scanForMedia(this@BackgroundLanguageReactor, videos, mp3s)
        }
    }

//    override fun onBind(intent: Intent?): IBinder? = when {
//        intent?.action == SERVICE_INTERFACE -> super.onBind(intent)
//
//        else -> null
//    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaSession
    }

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo) = when {
        session.isMediaNotificationController(controller) -> {
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .remove(Player.COMMAND_SEEK_TO_NEXT)
                        .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build()
                )
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(CustomSessionCommands.SeekPreviousSubtitle)
                        .add(CustomSessionCommands.SeekNextSubtitle)
                        .build()
                )
                .build()
        }

        else -> {
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(CustomSessionCommands.SeekPreviousSubtitle)
                        .add(CustomSessionCommands.SeekToSubtitle)
                        .add(CustomSessionCommands.SeekNextSubtitle)
                        .add(CustomSessionCommands.Backgrounding)
                        .add(CustomSessionCommands.Foregrounding)
                        .build()
                )
                .build()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
//        super.onTaskRemoved(rootIntent)
    }

    override fun onMediaButtonEvent(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        intent: Intent
    ): Boolean = when (intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)!!.keyCode) {
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
            player.seekToPreviousSubtitle()
            true
        }

        KeyEvent.KEYCODE_MEDIA_NEXT -> {
            player.seekToNextSubtitle()
            true
        }

        else -> {
            super.onMediaButtonEvent(session, controllerInfo, intent)
        }
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        var res = SessionResult.RESULT_SUCCESS
        var bundle = Bundle.EMPTY
        when (customCommand) {
            CustomSessionCommands.SeekPreviousSubtitle -> player.seekToPreviousSubtitle()

            CustomSessionCommands.SeekToSubtitle -> {
                player.seekToCurrentSubtitle(args.getSubIndex())
            }

            CustomSessionCommands.SeekNextSubtitle -> player.seekToNextSubtitle()

            CustomSessionCommands.Backgrounding -> {
                isForeground = false
            }

            CustomSessionCommands.Foregrounding -> {
                isForeground = true
            }

            else -> {
                res = SessionResult.RESULT_ERROR_NOT_SUPPORTED
            }
        }

        return Futures.immediateFuture(SessionResult(res, bundle))
    }


    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        // TODO: proper media browsing
        var res = LibraryResult.ofError<ImmutableList<MediaItem>>(LibraryResult.RESULT_ERROR_NOT_SUPPORTED)
        when (parentId) {
            "/video" -> res = LibraryResult.ofItemList(videos.values.toList(), LibraryParams.Builder().build())

            "/audio" -> res = LibraryResult.ofItemList(mp3s.values.toList(), LibraryParams.Builder().build())

            else -> {}
        }

        return Futures.immediateFuture(res)
    }


    override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
        super.onDisconnected(session, controller)
    }

    override fun onDestroy() {
        mediaSession.release()
        coro.cancel()
        super.onDestroy()
    }
}
