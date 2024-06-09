package com.shio.saikyo.ui.media

import android.content.ComponentName
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.shio.saikyo.BackgroundLanguageReactor
import com.shio.saikyo.SaikyoApp
import com.shio.saikyo.db.MediaData
import com.shio.saikyo.db.MediaSubtitles
import com.shio.saikyo.db.PlaybackState
import com.shio.saikyo.db.SubtitleData
import com.shio.saikyo.ui.primitives.LoadingSpinner
import kotlinx.coroutines.launch


@Composable
fun MediaPlayerScreen(
    asVideo: Boolean,
    mediaUriStr: String,
    subtitleUriStr: String,
    navBack: () -> Unit,
    vm: MediaPlayerVM = viewModel(factory = MediaPlayerVM.factory(mediaUriStr, subtitleUriStr, asVideo))
) {
    val ssState by vm.ssState.collectAsState()

    when {
        ssState == PlaybackState.UNINITIALIZED -> LoadingSpinner(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )

        asVideo -> VideoPlayer(vm.info, ssState, vm.controller, navBack)

        else -> AudioPlayer(vm.info, ssState, vm.controller, navBack)
    }
}

class MediaPlayerVM(
    val mediaUri: Uri,
    val asVideo: Boolean,
    val mediaData: MediaData,
    pbData: SubtitleData,
) : ViewModel() {
    lateinit var info: MediaSubtitles
        private set

    lateinit var controller: MediaController
        private set

    val ssState = pbData.getPlaybackState()

    fun init(controller: MediaController) = viewModelScope.launch {
        info = mediaData.getSubtitlesFor(mediaUri.toString())

        val mediaItem = MediaItem.Builder()
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(info.videoName)
                    // TODO: cache thumbnails .setArtworkUri()
                    .build()
            )
            .setUri(mediaUri)
            .build()

        controller.addMediaItem(mediaItem)

        controller.prepare()
        controller.play()

        this@MediaPlayerVM.controller = controller
    }

    override fun onCleared() {
        controller.stop()
        controller.clearMediaItems()
        controller.release()
    }

    companion object {
        @OptIn(UnstableApi::class)
        fun factory(
            mediaUriStr: String,
            subtitleUriStr: String,
            asVideo: Boolean,
        ) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!! as SaikyoApp
                val ctx = app.applicationContext
                val token = SessionToken(ctx, ComponentName(ctx, BackgroundLanguageReactor::class.java))

                val vm = MediaPlayerVM(
                    Uri.parse(mediaUriStr),
                    asVideo,
                    app.mediaDb.mediaData(),
                    app.mediaDb.playbackData()
                )
                val futures = MediaController.Builder(ctx, token).buildAsync()
                futures.addListener({
                    vm.init(futures.get())
                }, MoreExecutors.directExecutor())

                return@initializer vm
            }
        }
    }
}