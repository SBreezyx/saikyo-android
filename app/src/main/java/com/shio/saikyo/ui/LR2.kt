package com.shio.saikyo.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shio.saikyo.R
import com.shio.saikyo.ui.media.toTimestamp
import com.shio.saikyo.ui.primitives.DefaultNavBar
import com.shio.saikyo.ui.primitives.HorizontalWhitespace
import com.shio.saikyo.ui.primitives.LoadingSpinner
import com.shio.saikyo.ui.primitives.NavBackButton
import com.shio.saikyo.ui.primitives.PrimaryDestinations
import com.shio.saikyo.ui.primitives.VerticalWhitespace
import com.shio.saikyo.ui.theme.SaikyoTheme
import com.shio.saikyo.util.verticalScrollbar
import com.shio.saikyo.youtubedl.AudioStreamInfo
import com.shio.saikyo.youtubedl.ClosedCaptionStreamInfo
import com.shio.saikyo.youtubedl.VideoManifest
import com.shio.saikyo.youtubedl.VideoStreamInfo
import com.shio.saikyo.youtubedl.YoutubeDL
import com.shio.saikyo.youtubedl.getInfo
import com.shio.saikyo.youtubedl.getStream
import com.shio.saikyo.youtubedl.mux
import com.shio.saikyo.youtubedl.toSubrip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

data class DownloadState(
    val vsDownloadProgress: Float = -1f,
    val asDownloadProgress: Float = -1f,
    val ccDownloadProgress: Float = -1f,
    val muxing: Boolean = false,
) {
    companion object {
        val Inactive = DownloadState()
    }
}

data class LRState(
    val urlText: String = "",
    val videoId: String = "",
    val fetchingManifest: Boolean = false,
    val manifest: VideoManifest? = null,
    val thumbnail: Bitmap? = null,
    val selectedVideoStreamIx: Int = -1,
    val selectedAudioStreamIx: Int = -1,
    val selectedCaptionStreamIx: Int = -1,
    val downloadState: DownloadState = DownloadState.Inactive
)

fun MutableStateFlow<LRState>.updateDownload(fn: (DownloadState) -> DownloadState) = update {
    it.copy(downloadState = fn(it.downloadState))
}

fun Long.sizeMB() = "%.2f MB".format(this.toFloat() / 1_000_000)

fun Int.sampleRate() = "%.1f kHz".format(this.toFloat() / 1000)

@Composable
fun VideoStreamChip(
    vsi: VideoStreamInfo,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) = FilterChip(
    selected = isSelected,
    onClick = onClick,
    label = {
        val s = """
            ${vsi.mimeType.split(";")[0].split("/")[1].uppercase(Locale.getDefault())}
            (${vsi.qualityLabel})
            ${vsi.contentLength.sizeMB()}
        """.trimIndent()

        Text(s, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
    },
    modifier = modifier
)


@Composable
fun AudioStreamChip(
    asi: AudioStreamInfo,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) = FilterChip(
    selected = isSelected,
    onClick = onClick,
    label = {
        val s = """
            ${asi.mimeType.split(";")[0].split("/")[1].uppercase(Locale.getDefault())}
            (${asi.audioSampleRate.sampleRate()})
            ${asi.contentLength.sizeMB()}
        """.trimIndent()

        Text(s, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
    },
    modifier = modifier
)

@Composable
fun ClosedCaptionStreamChip(
    csi: ClosedCaptionStreamInfo,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) = FilterChip(
    selected = isSelected,
    onClick = onClick,
    label = {
        Text(csi.label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
    },
    modifier = modifier
)

@Preview
@Composable
private fun VSChipPreview() = SaikyoTheme {
    val vsi = VideoStreamInfo(
        itag = 0,
        url = "",
        mimeType = "video/mp4; codecs=...",
        bitrate = 0,
        width = 1920,
        height = 1080,
        contentLength = 129765349,
        quality = "hd1080",
        fps = 30,
        qualityLabel = "1080p60",
        averageBitrate = 0,
        approxDurationMs = 0,
    )

    VideoStreamChip(vsi = vsi, isSelected = false)
}


@Preview
@Composable
private fun ASChipPreview() = SaikyoTheme {
    val asi = AudioStreamInfo(
        itag = 0,
        url = "",
        mimeType = "audio/mp4a; codecs=...",
        bitrate = 0,
        contentLength = 7920664,
        quality = "tiny",
        audioQuality = "AUDIO_QUALITY_LOW",
        averageBitrate = 0,
        approxDurationMs = 0,
        audioSampleRate = 44100,
        audioChannels = 2
    )

    AudioStreamChip(asi = asi, isSelected = false)
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> StreamSection(
    sectionHeader: String,
    streams: Iterable<T>,
    chip: @Composable (Int, T) -> Unit,
    modifier: Modifier = Modifier,
    itemsPerRow: Int = 3
) = Column(
    modifier = modifier
) {
    Text(
        text = sectionHeader,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelMedium
    )
    VerticalWhitespace(height = 4.dp)
    FlowRow(
        maxItemsInEachRow = itemsPerRow,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        streams.mapIndexed { ix, st ->
            chip(ix, st)
        }
    }
}

@Preview
@Composable
private fun FlowyRowy() = SaikyoTheme {
    val vs = VideoStreamInfo(
        itag = 0,
        url = "",
        mimeType = "video/mp4; codecs=...",
        bitrate = 0,
        width = 1920,
        height = 1080,
        contentLength = 129765349,
        quality = "hd1080",
        fps = 30,
        qualityLabel = "1080p60",
        averageBitrate = 0,
        approxDurationMs = 0,
    )

    StreamSection(
        sectionHeader = "VIDEO STREAMS",
        streams = listOf(
            vs,
            vs.copy(contentLength = 433432),
            vs.copy(contentLength = 43342232),
            vs.copy(contentLength = 3422525),
            vs.copy(contentLength = 2345624672),
        ),
        { ix, vsi -> VideoStreamChip(vsi, ix == 2, onClick = {}) }
    )
}

@Preview
@Composable
private fun FlowyRowy2() = SaikyoTheme {
    val asi = AudioStreamInfo(
        itag = 0,
        url = "",
        mimeType = "audio/mp4a; codecs=...",
        bitrate = 0,
        contentLength = 7920664,
        quality = "tiny",
        audioQuality = "AUDIO_QUALITY_LOW",
        averageBitrate = 0,
        approxDurationMs = 0,
        audioSampleRate = 44100,
        audioChannels = 2
    )

    StreamSection(
        sectionHeader = "AUDIO STREAMS",
        streams = listOf(
            asi,
            asi.copy(contentLength = 433432),
            asi.copy(contentLength = 43342232),
            asi.copy(contentLength = 3422525),
            asi.copy(contentLength = 2345624672),
        ),
        { ix, asp -> AudioStreamChip(asp, ix == 2, onClick = {}) }
    )
}

@Composable
fun DownloadIndicator(
    label: String,
    progress: Float,
    modifier: Modifier = Modifier
) = Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically
) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    HorizontalWhitespace(width = 4.dp)
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f))
}

@Preview
@Composable
private fun DownloadIPreview() = SaikyoTheme {
    DownloadIndicator(
        label = "video",
        progress = 0.5f,
        modifier = Modifier.padding(4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LR2(
    navBack: () -> Unit,
    navToDict: () -> Unit,
    navToLr: () -> Unit,
    vm: LRVM = viewModel(factory = LRVM.factory())
) = Scaffold(
    topBar = {
        CenterAlignedTopAppBar(
            navigationIcon = { NavBackButton(onClick = navBack) },
            title = {
                Text(stringResource(R.string.app_name))
            }
        )
    },
) { insets ->
    val ctx = LocalContext.current

    val downloadDirSelector = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        if (it != null) {
            vm.startDownload(ctx.contentResolver, it)
        } else {
            Toast.makeText(ctx, "Download directory not selected", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(insets)
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScrollbar(rememberScrollState())
    ) {
        val theState = vm.uiState.collectAsState()
        val uiState = theState.value

        val dataIsAvailable = remember(uiState) {
            uiState.thumbnail != null && uiState.manifest != null
        }

        ThumbnailPreview(
            thumbnail = uiState.thumbnail?.asImageBitmap(),
            loadingThumbnail = uiState.fetchingManifest,
            loadingSubtitles = false,
            modifier = Modifier.size(256.dp)
        )

        if (dataIsAvailable) {
            val manifest = uiState.manifest!!

            VerticalWhitespace(4.dp)
            Text(manifest.metadata.title)
            Text(manifest.metadata.lengthSeconds.toTimestamp())

            VerticalWhitespace(4.dp)

            StreamSection(
                sectionHeader = "VIDEO STREAMS",
                streams = manifest.videoStreams,
                chip = { ix, vsi ->
                    VideoStreamChip(vsi = vsi, isSelected = uiState.selectedVideoStreamIx == ix) {
                        vm.selectVideoStream(ix)
                    }
                },
                modifier = Modifier.padding(4.dp)
            )

            StreamSection(
                sectionHeader = "AUDIO STREAMS",
                streams = manifest.audioStreams,
                chip = { ix, asi ->
                    AudioStreamChip(asi = asi, isSelected = uiState.selectedAudioStreamIx == ix) {
                        vm.selectAudioStream(ix)
                    }
                },
                modifier = Modifier.padding(4.dp)
            )

            StreamSection(
                sectionHeader = "SUBTITLES",
                streams = manifest.ccStreams,
                chip = { ix, csi ->
                    ClosedCaptionStreamChip(csi = csi, isSelected = uiState.selectedCaptionStreamIx == ix) {
                        vm.selectCaptionStream(ix)
                    }
                },
                modifier = Modifier.padding(4.dp)
            )

        }

        VerticalWhitespace(height = 8.dp)

        TextField(
            value = uiState.urlText,
            onValueChange = { vm.updateUrl(it) },
            label = { Text("Youtube Video URL") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        TextField(
            value = uiState.videoId,
            onValueChange = {},
            readOnly = true,
            label = { Text("Video ID") },
            modifier = Modifier.fillMaxWidth()

        )

        VerticalWhitespace(height = 8.dp)

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            val width = 128.dp

            Button(onClick = { vm.getVideoData(uiState.videoId) }, modifier = Modifier.width(width)) {
                Text("GO")
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    downloadDirSelector.launch(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY))
                },
                enabled = dataIsAvailable,
                modifier = Modifier.width(width)
            ) {
                Text("DOWNLOAD")
            }
        }

        val dl = uiState.downloadState
        if (dl != DownloadState.Inactive) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DownloadIndicator(label = "Video", progress = dl.vsDownloadProgress, modifier = Modifier.padding(4.dp))
                DownloadIndicator(label = "Audio", progress = dl.asDownloadProgress, modifier = Modifier.padding(4.dp))
                DownloadIndicator(
                    label = "Subtitle",
                    progress = dl.ccDownloadProgress,
                    modifier = Modifier.padding(4.dp)
                )

                if (dl.muxing) {
                    LoadingSpinner()
                }
            }
        }
    }
}


class LRVM(
    val yt: YoutubeDL
) : ViewModel() {
    private var _uiState = MutableStateFlow(LRState())
    val uiState = _uiState.asStateFlow()

    fun getVideoData(videoId: String) {
        _uiState.update {
            it.copy(fetchingManifest = true)
        }
        viewModelScope.launch {
            val info = yt.getInfo(videoId)

            val thumbInfo = info.thumbnailStreams.maxBy { it.width * it.height }

            val thumb = yt.getStream(thumbInfo).map { (bytes, _) ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.first()

            _uiState.update {
                it.copy(
                    fetchingManifest = false,
                    manifest = info,
                    thumbnail = thumb,
                    selectedVideoStreamIx = 0,
                    selectedAudioStreamIx = 0,
                    selectedCaptionStreamIx = 0,
                )
            }
        }
    }

    fun updateUrl(url: String) = _uiState.update {
        it.copy(
            urlText = url,
            videoId = Uri.parse(url).getQueryParameters("v").firstOrNull() ?: ""
        )
    }

    fun selectVideoStream(ix: Int) = _uiState.update {
        it.copy(selectedVideoStreamIx = ix)
    }

    fun selectAudioStream(ix: Int) = _uiState.update {
        it.copy(selectedAudioStreamIx = ix)
    }

    fun selectCaptionStream(ix: Int) = _uiState.update {
        it.copy(selectedCaptionStreamIx = ix)
    }

    fun startDownload(resolver: ContentResolver, destination: Uri) {
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(
            destination, DocumentsContract.getTreeDocumentId(destination)
        )

        _uiState.update {
            it.copy(downloadState = DownloadState(0f, 0f, 0f, false))
        }

        viewModelScope.launch(Dispatchers.IO) {
            val manifest = _uiState.value.manifest!!
            val displayName = manifest.metadata.title

            val avDl = async {
                val t1 = File.createTempFile("vid", "mp4")
                val t2 = File.createTempFile("aud", "m4a")

                val vsi = manifest.videoStreams[_uiState.value.selectedVideoStreamIx]
                val asi = manifest.audioStreams[_uiState.value.selectedAudioStreamIx]

                val vidDl = async {

                    yt.getStream(vsi).collect { (data, progress) ->
                        t1.appendBytes(data)

                        _uiState.updateDownload {
                            it.copy(vsDownloadProgress = progress)
                        }
                    }
                }

                val audDl = async {
                    yt.getStream(asi).collect { (data, progress) ->
                        t2.appendBytes(data)
                        _uiState.updateDownload { it.copy(asDownloadProgress = progress) }
                    }

                }

                vidDl.await(); audDl.await()

                _uiState.updateDownload {
                    it.copy(muxing = true)
                }


                val vfd = resolver.openAssetFileDescriptor(Uri.fromFile(t1), "r")
                val afd = resolver.openAssetFileDescriptor(Uri.fromFile(t2), "r")


                val uri = DocumentsContract.createDocument(
                    resolver, rootUri, "video/mp4", displayName
                )!!


                val ofd = resolver.openAssetFileDescriptor(uri, "rw")

                try {
                    if (vfd != null && afd != null && ofd != null) {
                        mux(vfd.fileDescriptor, afd.fileDescriptor, ofd.fileDescriptor)
                    }
                } finally {
                    vfd?.close()
                    afd?.close()
                    ofd?.close()

                    t1.delete()
                    t2.delete()
                }
            }

            val ccDl = async {
                val selectedIndex = _uiState.value.selectedCaptionStreamIx
                val csi = manifest.ccStreams[selectedIndex]

                val uri = DocumentsContract.createDocument(
                    resolver, rootUri,
                    "application/x-subrip", manifest.metadata.title
                )!! // TODO: proper error handling

                val output = resolver.openOutputStream(uri, "w")

                output?.use {
                    yt.getStream(csi).collect { (data, progress) ->
                        val subs = data.toSubrip()
                        output.write(subs.toByteArray())

                        _uiState.updateDownload {
                            it.copy(ccDownloadProgress = progress)
                        }
                    }
                }
            }

            avDl.await(); ccDl.await()

            _uiState.update {
                it.copy(downloadState = DownloadState.Inactive)
            }
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                LRVM(YoutubeDL())
            }
        }
    }
}


/*
@Composable
fun rememberMediaBrowserFuture(): ListenableFuture<MediaBrowser> {
    val ctx = LocalContext.current

    val token = remember { SessionToken(ctx, ComponentName(ctx, BackgroundLanguageReactor::class.java)) }
    val futureMb = remember {
        MediaBrowser.Builder(ctx, token).buildAsync()
    }

    DisposableEffect(key1 = token) {
        onDispose {
            futureMb.cancel(true)
        }
    }

    return futureMb
}

@Composable
fun NoPermission(
    onPermissionReconsidered: () -> Unit,
    modifier: Modifier = Modifier
) = CompositionLocalProvider(LocalContentColor provides Color.Gray) {
    val ctx = LocalContext.current
    val linkStyle = remember {
        SpanStyle(
            textDecoration = TextDecoration.Underline
        )
    }
    val openSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onPermissionReconsidered()
    }

    Text(
        buildAnnotatedString {
            append("No permission to read files.")
            append("\n")
            append("To view your media library, please grant access in ")
            append(buildAnnotatedString {
                val s = "settings"

                append(s)
                val link = LinkAnnotation.Clickable(
                    s,
                    style = linkStyle
                ) {
                    openSettings.launch(Intent().apply {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.fromParts("package", ctx.packageName, null)
                    })
                }
                addLink(link, 0, link.tag.length)
            })
        },
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
fun Gallery(
    mb: MediaBrowser,
    modifier: Modifier = Modifier
) {

}

@Composable
fun LR2(
    navBack: () -> Unit,
) {
    val ctx = LocalContext.current

    var allGranted by remember { mutableStateOf(ctx.checkAllGranted(perms.toList())) }

    var askedForPermission by remember(allGranted) { mutableStateOf(allGranted) }

    val permLauncher = rememberLauncherForActivityResultImmediate(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { mp ->
        allGranted = mp.values.all { it }
    }

    Scaffold(
        topBar = {
            DefaultAppBar(
                navigation = { NavBackButton(navBack) }
            )
        },
        bottomBar = { DefaultNavBar() }
    ) { insets ->
        Box(
            modifier = Modifier
                .padding(insets)
                .fillMaxSize()
        ) {
            if (allGranted) {
                var isConnected by remember { mutableStateOf(false) }
                val futureMb = rememberMediaBrowserFuture()

                futureMb.addListener({
                   isConnected = true
                }, MoreExecutors.directExecutor())

                if (isConnected) {
                    Gallery(mb = futureMb.get())
                } else {
                    LoadingSpinner()
                }

            } else if (!askedForPermission) {
                permLauncher.launch(perms)
                askedForPermission = true
            } else {
                NoPermission(
                    onPermissionReconsidered = {
                        allGranted = ctx.checkAllGranted(perms.toList())
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

class LR2VM(

) : ViewModel() {




    companion object {
        val factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SaikyoApp


                LR2VM()
            }
        }
    }
}*/