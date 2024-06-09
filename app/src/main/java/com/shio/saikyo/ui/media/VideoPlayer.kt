package com.shio.saikyo.ui.media

import android.app.Activity
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.shio.saikyo.db.PlaybackState
import com.shio.saikyo.db.MediaSubtitles
import com.shio.saikyo.ui.ContextMenu
import com.shio.saikyo.ui.ContextMenuItem
import com.shio.saikyo.ui.LocalTextProcessors
import com.shio.saikyo.ui.primitives.NavBackButton
import com.shio.saikyo.ui.theme.SaikyoTheme
import com.shio.saikyo.util.getVibratorManager

const val ASPECT_16_9 = 16 / 9f

enum class GestureKind {
    DEFAULT,
    SUBTITLE
}

fun Vibrator.heavyClick() = vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))

class ImmersiveModeController(
    ctx: Context,
    val orientation: Int
) : DefaultLifecycleObserver {
    private val activity = (ctx as Activity)

    override fun onStart(owner: LifecycleOwner) {
        activity.requestedOrientation = orientation
    }

    override fun onResume(owner: LifecycleOwner) {
        val window = activity.window
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        val window = activity.window
        WindowInsetsControllerCompat(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
    }
}

// TODO: combine with Immersive mode controller for saving and restoring volume + brightness
class BrightnessController(ctx: Context) {
    private val window = (ctx as Activity).window
    private var brightness: Float

    init {
        val initBrightness = Settings.System.getFloat(
            ctx.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS
        )

        brightness = initBrightness / 256
    }

    fun increaseBrightness(amount: Float = 0.01f) {
        brightness = (brightness + amount).coerceIn(0.01f, 1f)
        setBrightness()
    }

    fun decreaseBrightness(amount: Float = 0.01f) {
        brightness = (brightness - amount).coerceIn(0.01f, 1f)
        setBrightness()
    }

    private fun setBrightness() {
        val attr = window.attributes
        attr.screenBrightness = brightness
        window.attributes = attr
    }
}

@Composable
fun rememberImmersiveMode(
    orientation: Int = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
): LifecycleObserver {
    val observer = ImmersiveModeController(LocalContext.current, orientation)
    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return observer
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackControls(
    title: String,
    showingSubtitles: Boolean,
    playbackRate: Float,
    isPlaying: Boolean,
    currTs: Long,
    maxTs: Long,
    navBack: () -> Unit,
    onPrevClicked: (() -> Unit),
    onPlayPauseClicked: () -> Unit,
    onNextClicked: (() -> Unit),
    onSliderSeek: (Float) -> Unit,
    onSliderSeeked: () -> Unit,
    onShowSubtitleClicked: () -> Unit,
    onPlaybackSpeedClicked: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier
        .fillMaxSize()
        .background(Color(0xA0000000))
) {

    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                color = Color.White,
                overflow = TextOverflow.Ellipsis, // TODO: think of something better than this
            )
        },
        navigationIcon = {
            NavBackButton(navBack, color = Color.White)
        },
        actions = {
            ClosedCaptionsButton(
                showingSubtitles = showingSubtitles,
                onShowSubtitleClicked = onShowSubtitleClicked
            )

            PlaybackSpeedButton(
                playbackRate = playbackRate,
                onPlaybackSpeedClicked = onPlaybackSpeedClicked
            )
        },
        colors = TopAppBarDefaults.topAppBarColors().copy(containerColor = Color.Transparent),
    )

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxSize()
            .weight(1f)
    ) {

        PreviousItemButton(onPrevClicked = onPrevClicked)

        PlayPauseButton(
            isPlaying = isPlaying,
            onPlayPauseClicked = onPlayPauseClicked
        )

        NextItemButton(onNextClicked = onNextClicked)
    }

    TimelineSlider(
        currTimestamp = currTs,
        maxTimestamp = maxTs,
        onSliderSeek = onSliderSeek,
        onSliderSeeked = onSliderSeeked,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp)
    )
}


@Composable
fun VideoPlayer(
    info: MediaSubtitles,
    ssState: PlaybackState,
    player: Player,
    navBack: () -> Unit,
) = CompositionLocalProvider(LocalContentColor provides Color.White) {
    rememberImmersiveMode()

    val ctx = LocalContext.current

    val brightnessManager = BrightnessController(ctx)
    val audioManager = ctx.getSystemService(AUDIO_SERVICE) as AudioManager
    val fm = LocalFocusManager.current
    val hf = ctx.getVibratorManager()

    var selectingPlaybackRate by remember { mutableStateOf(false) }
    var showingControls by remember { mutableStateOf(false) }
    var showingSubtitles by remember { mutableStateOf(true) }

    val txtProcessors = LocalTextProcessors.current
    val ctxMenu = remember {
        ContextMenu.from(
            ContextMenuItem.Copy,
            ContextMenuItem.Search(ctx),
            ContextMenuItem.SelectAll,
            *txtProcessors,
            maxVisible = 2
        )
    }

    var gestureKind by remember { mutableStateOf(GestureKind.DEFAULT) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Black)
    ) {
        VideoSurface(
            onInitialized = { player.setVideoSurfaceView(it) },
            modifier = Modifier
                .aspectRatio(ASPECT_16_9)
                .align(Alignment.Center)
        )

        when (gestureKind) {
            GestureKind.DEFAULT -> Box(
                Modifier
                    .fillMaxSize()
                    .tappable(
                        onClick = { showingControls = !showingControls },
                        onLeftDoubleClick = { player.seekBack() },
                        onRightDoubleClick = { player.seekForward() },
                        onLongPress = {
                            hf.heavyClick()
                        }

                    )
                    .horizontalDraggable(
                        onLeftDragLeft = {
                            player.seekToPreviousSubtitle()
                            hf.heavyClick()
                        },
                        onLeftDragRight = {
                            player.seekToNextSubtitle()
                            hf.heavyClick()

                        },
                    )
                    .horizontalDraggableAfterLongPress(
                        onLeftDragLeft = { player.scrubBackward(1_000) },
                        onLeftDragRight = { player.scrubForward(1_000) }
                    )
                    .verticalDraggable(
                        // here so vertical drags up/down don't trigger the onClick() handlers
                        {}, {}
                    )
                    .verticalDraggableAfterLongPress(
                        onLeftDragUp = { brightnessManager.increaseBrightness(0.05f) },
                        onLeftDragDown = { brightnessManager.decreaseBrightness(0.05f) },
                        onRightDragUp = {
                            audioManager.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                AudioManager.ADJUST_RAISE,
                                AudioManager.FLAG_VIBRATE
                            )
                        },
                        onRightDragDown = {
                            audioManager.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                AudioManager.ADJUST_LOWER,
                                AudioManager.FLAG_VIBRATE
                            )
                        }
                    )
            )

            GestureKind.SUBTITLE -> Box(
                Modifier
                    .fillMaxSize()
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        fm.clearFocus()
                    }
            )
        }

        val currSubtitle = ssState.currSubtitleText
        if (showingSubtitles && currSubtitle != null) {
            InteractiveSubtitle(
                subtitle = currSubtitle,
                contextMenu = ctxMenu,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .padding(horizontal = 96.dp)
                    .onFocusChanged {
                        if (it.hasFocus) {
                            player.pause()
                            gestureKind = GestureKind.SUBTITLE
                        } else {
                            gestureKind = GestureKind.DEFAULT
                            player.play()
                        }
                    }
            )
        }

        if (showingControls) {
            PlaybackControls(
                title = info.videoName,
                showingSubtitles = showingSubtitles,
                playbackRate = ssState.playbackSpeed,
                isPlaying = ssState.isPlaying,
                currTs = ssState.currTs,
                maxTs = info.lengthMs,
                navBack = navBack,
                onPrevClicked = { player.seekToPreviousSubtitle() },
                onPlayPauseClicked = {
                    if (ssState.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                },
                onNextClicked = { player.seekToNextSubtitle() },
                onSliderSeek = { seekPercentage ->
                    player.seekTo(seekPercentage)
                },
                onSliderSeeked = {
                    player.play()
                },
                onShowSubtitleClicked = {
                    showingSubtitles = !showingSubtitles
                },
                onPlaybackSpeedClicked = {
                    selectingPlaybackRate = true
                },
            )

            if (selectingPlaybackRate) {
                PlaybackRateSheet(
                    currRate = ssState.playbackSpeed,
                    onDismiss = {
                        selectingPlaybackRate = false
                    },
                    onRateChange = {
                        player.setPlaybackSpeed(it)
                    },
                )
            }
        }
    }
}


@Preview(
    widthDp = 1012,
    heightDp = 480, uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL, name = "kjh",
    group = "jg", apiLevel = 34,
)
@Composable
private fun PlaybackControlsLight() {
    SaikyoTheme {
        PlaybackControls(
            title = "swag",
            showingSubtitles = false,
            playbackRate = 1f,
            isPlaying = true,
            currTs = 3,
            maxTs = 234,
            navBack = { },
            onPrevClicked = {},
            onPlayPauseClicked = {},
            onNextClicked = {},
            onSliderSeek = {},
            onSliderSeeked = {},
            onShowSubtitleClicked = {},
            onPlaybackSpeedClicked = {}
        )
    }
}

@Preview(
    widthDp = 1012,
    heightDp = 480, group = "jg", name = "kkkkkk",
)
@Composable
private fun PlaybackControlsDark() {
    SaikyoTheme(darkTheme = true) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            PlaybackControls(
                title = "swag",
                showingSubtitles = false,
                playbackRate = 1f,
                isPlaying = true,
                currTs = 3,
                maxTs = 234,
                navBack = { },
                onPrevClicked = {},
                onPlayPauseClicked = {},
                onNextClicked = {},
                onSliderSeek = {},
                onSliderSeeked = {},
                onShowSubtitleClicked = {},
                onPlaybackSpeedClicked = {}
            )
        }
    }
}

@Preview(widthDp = 1012, heightDp = 480)
@Composable
private fun PlaybackRateLight() {
    SaikyoTheme {
        PlaybackRateSheet(currRate = 1f, onRateChange = {})
    }
}

@Preview(widthDp = 1012, heightDp = 480)
@Composable
private fun PlaybackRateDark() {
    SaikyoTheme(darkTheme = true) {
        PlaybackRateSheet(currRate = 1f, onRateChange = {})
    }
}