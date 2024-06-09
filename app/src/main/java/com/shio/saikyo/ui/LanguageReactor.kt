package com.shio.saikyo.ui

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shio.saikyo.R
import com.shio.saikyo.SaikyoApp
import com.shio.saikyo.db.MediaData
import com.shio.saikyo.db.Subtitle
import com.shio.saikyo.db.Table
import com.shio.saikyo.parse.parse
import com.shio.saikyo.ui.primitives.DefaultAppBar
import com.shio.saikyo.ui.primitives.DefaultNavBar
import com.shio.saikyo.ui.primitives.HorizontalWhitespace
import com.shio.saikyo.ui.primitives.PrimaryDestinations
import com.shio.saikyo.ui.theme.SaikyoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.FileNotFoundException


fun <T> MediaMetadataRetriever.use(fn: (MediaMetadataRetriever) -> T): T {
    val res = fn(this)
    release()
    return res
}

fun genRepresentativeThumbnail(uri: Uri, ctx: Context): Bitmap? {
    val req = MediaMetadataRetriever().apply { setDataSource(ctx, uri) }
    val durationMs = req.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
    return req.use {
        val ts = (durationMs * 1000) / 2
        it.getScaledFrameAtTime(ts, MediaMetadataRetriever.OPTION_NEXT_SYNC, 600, 480)
    }
}

fun getLengthOfVideo(uri: Uri, ctx: Context): Long {
    val req = MediaMetadataRetriever().apply { setDataSource(ctx, uri) }
    return req.use {
        val res = it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        res?.toLong() ?: 0L
    }
}

fun Uri?.getDisplayName(res: ContentResolver): String = when (this) {
    null -> ""
    else -> {
        var name = ""
        res.query(
            this,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use {
            if (it.moveToFirst()) {
                name = it.getString(0)
            }
        }

        name
    }
}

fun genSubtitles(uri: Uri, ctx: Context): List<Subtitle> {
    val subripBytes = try {
        ctx.contentResolver.openInputStream(uri).use {
            it?.readBytes() ?: byteArrayOf()
        }
    } catch (e: FileNotFoundException) {
        return listOf()
    }

    val subtitles = parse(subripBytes)

    return subtitles
}

@Composable
fun ThumbnailPreview(
    thumbnail: ImageBitmap?,
    loadingThumbnail: Boolean,
    loadingSubtitles: Boolean,
    modifier: Modifier = Modifier
) = Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
) {
    val maxSize = Modifier.fillMaxSize()
    val bottom = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()

    if (thumbnail != null) {
        Image(
            thumbnail,
            stringResource(R.string.preview_thumbnail_desc),
            contentScale = ContentScale.Crop,
            modifier = maxSize
        )
    } else {
        Icon(
            painterResource(R.drawable.outline_video_file_24),
            stringResource(R.string.preview_thumbnail_desc),
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = maxSize
        )
    }

    if (loadingThumbnail || loadingSubtitles) {
        LinearProgressIndicator(modifier = bottom)
    }
}

@Composable
fun FilePickerButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) = Button(
    onClick = onClick,
    modifier = modifier
) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun PlayButton(
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) = Button(onClick, modifier = modifier, isEnabled) {
    Text(stringResource(R.string.play_btn_text))
}

@Composable
fun PlayAsAudioButton(
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) = Button(onClick, modifier = modifier, isEnabled) {
    Text(stringResource(R.string.play_as_audio_btn_text))
}

@Composable
fun LanguageReactor(
    navToHome: () -> Unit,
    navToSettings: () -> Unit,
    uiState: LRUiState,
    onVideoPicked: (Uri) -> Unit,
    onSubtitlePicked: (Uri) -> Unit,
    onPlayVideo: (String, String) -> Unit,
    onPlayAudio: (String, String) -> Unit,
) {
    Scaffold(
        topBar = { DefaultAppBar() },
        bottomBar = {
            DefaultNavBar(selectedIndex = 1) {
                add(PrimaryDestinations.home(onClick = navToHome))
                add(PrimaryDestinations.languageReactor())
                add(PrimaryDestinations.settings(onClick = navToSettings))
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val pickVidFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                onVideoPicked(uri)
            }
        }
        val pickSubFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                onSubtitlePicked(uri)
            }
        }

        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ThumbnailPreview(
                thumbnail = uiState.thumbnail,
                loadingThumbnail = uiState.thumbnailIsLoading,
                loadingSubtitles = uiState.subtitleIsLoading,
                modifier = Modifier
                    .size(256.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            FilePickerButton(uiState.videoName.ifEmpty { stringResource(R.string.video_file_select_placeholder) }) {
                pickVidFile.launch(arrayOf("video/*"))
            }

            FilePickerButton(uiState.subtitleName.ifEmpty { stringResource(R.string.subtitle_file_select_placeholder) }) {
                pickSubFile.launch(arrayOf("application/x-subrip"))
            }

            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                val areEnabled = remember(uiState) {
                    uiState.videoUri != Uri.EMPTY && uiState.subtitleUri != Uri.EMPTY && !uiState.subtitleIsLoading
                }
                PlayButton(areEnabled) {
                    onPlayVideo(uiState.videoUri.toString(), uiState.subtitleUri.toString())
                }

                HorizontalWhitespace(8.dp)

                PlayAsAudioButton(areEnabled) {
                    onPlayAudio(uiState.videoUri.toString(), uiState.subtitleUri.toString())
                }
            }
        }
    }
}

@SuppressLint("InlinedApi")
@Composable
fun LanguageReactor(
    navToHome: () -> Unit,
    navToSettings: () -> Unit,
    navToVideoPlayer: (String, String) -> Unit,
    navToAudioPlayer: (String, String) -> Unit,
    vm: LanguageReactorVM = viewModel(factory = LanguageReactorVM.factory)
) {
    val ctx = LocalContext.current
    val uiState by vm.uiState.collectAsState()
    LanguageReactor(
        navToHome = navToHome,
        navToSettings = navToSettings,
        uiState = uiState,
        onVideoPicked = { vm.setVideoFile(it, ctx) },
        onSubtitlePicked = { vm.setSubtitleFile(it, ctx) },
        onPlayVideo = { v, s -> navToVideoPlayer(v, s) },
        onPlayAudio = { a, s -> navToAudioPlayer(a, s) }
    )
}

data class LRUiState(
    val thumbnail: ImageBitmap? = null,

    val videoUri: Uri = Uri.EMPTY,
    val videoName: String = "",
    val thumbnailIsLoading: Boolean = false,

    val subtitleUri: Uri = Uri.EMPTY,
    val subtitleName: String = "",
    val subtitleIsLoading: Boolean = false
) {
    companion object {
        val Uninitialized = LRUiState()
    }
}

class LanguageReactorVM(
    val mediaData: MediaData
) : ViewModel() {
    private var _uiState = MutableStateFlow(LRUiState.Uninitialized)
    val uiState = _uiState.asStateFlow()

    fun setVideoFile(videoUri: Uri, ctx: Context) {
        viewModelScope.launch {
            // for UI effect.
            _uiState.update { it.copy(thumbnailIsLoading = true) }
            delay(1_000)

            // pray this doesn't blow up
            _uiState.update {
                it.copy(
                    thumbnail = genRepresentativeThumbnail(videoUri, ctx)?.asImageBitmap(),
                    videoUri = videoUri,
                    videoName = videoUri.getDisplayName(ctx.contentResolver),
                    thumbnailIsLoading = false
                )
            }
        }
    }

    fun setSubtitleFile(subtitleUri: Uri, ctx: Context) {
        viewModelScope.launch {
            // for UI effect.
            _uiState.update { it.copy(subtitleIsLoading = true) }
            delay(1_000)

            val videoUri = _uiState.value.videoUri
            val videoUriStr = videoUri.toString()
            val videoId = videoUriStr.hashCode()

            mediaData.insertVideo(
                Table.VideoMetadata(
                    videoUri = videoUri.toString(),
                    id = videoId,
                    subtitleUri = subtitleUri.toString(),
                    name = _uiState.value.videoName,
                    lengthMs = getLengthOfVideo(videoUri, ctx),
                )
            )

            val subs = genSubtitles(subtitleUri, ctx)
            mediaData.insertSubtitles(subs.mapIndexed { ix, sub ->
                Table.Subtitle(
                    videoId = videoId,
                    subtitleIndex = ix,
                    startMs = sub.startMs,
                    endMs = sub.endMs,
                    text = sub.text
                )
            })

            // pray this doesn't blow up
            _uiState.update {
                it.copy(
                    subtitleUri = subtitleUri,
                    subtitleName = subtitleUri.getDisplayName(ctx.contentResolver),
                    subtitleIsLoading = false
                )
            }
        }
    }

    companion object {
        val factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SaikyoApp

                LanguageReactorVM(app.mediaDb.mediaData())
            }
        }
    }
}

@Preview
@Composable
private fun LRPreview() = SaikyoTheme {
    LanguageReactor(
        navToHome = {},
        navToSettings = {},
        uiState = LRUiState.Uninitialized,
        onVideoPicked = {},
        onSubtitlePicked = {},
        onPlayVideo = { _, _ -> },
        onPlayAudio = { _, _ -> }
    )
}