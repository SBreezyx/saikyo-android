package com.shio.saikyo.ui.media

import android.app.Activity
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import com.shio.saikyo.db.PlaybackState
import com.shio.saikyo.db.Subtitle
import com.shio.saikyo.db.MediaSubtitles
import com.shio.saikyo.enableEdgeToEdge
import com.shio.saikyo.ui.primitives.LoadingSpinner
import com.shio.saikyo.ui.primitives.NavBackButton
import com.shio.saikyo.ui.theme.SaikyoTheme

// TODO: fix fading edges
fun Modifier.fadingEdge(
    color: Color = DefaultShadowColor,
    start: Dp = Dp.Unspecified,
    top: Dp = Dp.Unspecified,
    end: Dp = Dp.Unspecified,
    bottom: Dp = Dp.Unspecified,
) = drawWithContent {
    drawContent()

    val (width, height) = size
    val sides = arrayOf(start, top, end, bottom)
    val dims = arrayOf(
        Offset.Zero to Size(start.toPx(), height),
        Offset.Zero to Size(width, top.toPx()),
        Offset(width - end.toPx(), 0f) to Size(end.toPx(), height),
        Offset(0f, height - bottom.toPx()) to Size(width, bottom.toPx())
    )

    for ((side, offset) in sides.zip(dims)) {
        if (side != Dp.Unspecified) {
            val (tl, sz) = offset
            drawRect(
                brush = Brush.verticalGradient(
                    0f to color,
                    1f to Color.Transparent,
//                    start = first,
//                    end = last
                ),
                topLeft = tl,
                size = sz
            )
        }
    }
}

@Composable
fun Subtitle(
    sub: Subtitle,
    modifier: Modifier = Modifier,
    labelColor: Color = Color.LightGray,
    subColor: Color = LocalContentColor.current,
    onClick: () -> Unit = {}
) = Column(
    modifier = modifier
        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
) {
    Text(
        text = "${sub.startMs.toTimestamp()} -> ${sub.endMs.toTimestamp()}",
        color = labelColor,
        style = MaterialTheme.typography.labelSmall
    )

    Text(
        text = sub.text.trim(),
        color = subColor
    )
}

@Composable
fun AudioPlayerControls(
    isPlaying: Boolean,
    playbackRate: Float,
    currTs: Long,
    maxTs: Long,
    onSliderSeek: (Float) -> Unit,
    onSliderSeeked: () -> Unit,
    onPlaybackSpeedClicked: () -> Unit,
    onPreviousItemClicked: () -> Unit,
    onPlayPauseClicked: () -> Unit,
    onNextItemClicked: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
//    contentColor: Color = LocalContentColor.current,
) = Column(
    modifier = modifier
        .background(color = containerColor)
        .padding(8.dp)
) {
    TimelineSlider(
        currTimestamp = currTs,
        maxTimestamp = maxTs,
        onSliderSeek = onSliderSeek,
        onSliderSeeked = onSliderSeeked,
    )

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.align(Alignment.Center)) {
            PreviousItemButton(onPreviousItemClicked)
            PlayPauseButton(isPlaying = isPlaying, onPlayPauseClicked)
            NextItemButton(onNextItemClicked)
        }

        PlaybackSpeedButton(
            playbackRate = playbackRate,
            onPlaybackSpeedClicked = onPlaybackSpeedClicked,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayer(
    info: MediaSubtitles,
    ssState: PlaybackState,
    player: Player,
    navBack: () -> Unit,
) = CompositionLocalProvider(LocalContentColor provides Color.White) {

    val ctx = LocalContext.current
    DisposableEffect(key1 = Unit) {
        val win = (ctx as Activity).window
        val statusBarColor = win.statusBarColor
        val navBarColor = win.navigationBarColor

        ctx.enableEdgeToEdge(
            statusBars = Color.Black,
            navigationBarStyle = Color.DarkGray
        )

        onDispose {
            win.navigationBarColor = navBarColor
            win.statusBarColor = statusBarColor
        }
    }

    var selectingPlaybackRate by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    info.videoName,
                    maxLines = 1,
                    color = Color.White,
                    overflow = TextOverflow.Ellipsis, // TODO: think of something better than this
                )
            },
            navigationIcon = {
                NavBackButton(
                    onClick = {
                        player.stop()
                        navBack()
                    },
                )
            },
            colors = TopAppBarDefaults.topAppBarColors().copy(
                containerColor = Color.Black
            ),
            windowInsets = WindowInsets.statusBars,
        )

        val subs = info.subs
        val subIx = ssState.currSubtitleIndex + 1
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
//                .fadingEdge(top = 8.dp, bottom = 8.dp)
        ) {
            for (ix in 0 until subIx) item(key = ix) {
                Subtitle(subs[ix]) {
                    player.seekToSubtitle(ix)
                }
            }

            for (ix in subIx until subs.size) item(key = ix) {
                Subtitle(subs[ix], subColor = Color.DarkGray) {
                    player.seekToSubtitle(ix)
                }
            }
        }

        AudioPlayerControls(
            isPlaying = ssState.isPlaying,
            playbackRate = ssState.playbackSpeed,
            currTs = ssState.currTs,
            maxTs = info.lengthMs,
            onSliderSeek = { player.seekTo(it) },
            onSliderSeeked = { player.play() },
            onPlaybackSpeedClicked = { selectingPlaybackRate = true },
            onPreviousItemClicked = { player.seekToPreviousSubtitle() },
            onPlayPauseClicked = { if (ssState.isPlaying) player.pause() else player.play() },
            onNextItemClicked = { player.seekToNextSubtitle() },
            containerColor = Color.DarkGray,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
        )
    }

    if (selectingPlaybackRate) {
        PlaybackRateSheet(
            currRate = ssState.playbackSpeed,
            onRateChange = {
                player.setPlaybackSpeed(it)
            },
            onDismiss = {
                selectingPlaybackRate = false
            }
        )
    }
}



@androidx.annotation.OptIn(UnstableApi::class)
@Preview
@Composable
private fun help() = SaikyoTheme {
    AudioPlayer(
        info = MediaSubtitles(
            "swag", 0, listOf(
                Subtitle(0, 0, "hi"),
                Subtitle(0, 0, "bye"),
                Subtitle(0, 0, "guy"),
            )
        ),
        ssState = PlaybackState.UNINITIALIZED,
        player = object : Player {
            override fun getApplicationLooper(): Looper {
                TODO("Not yet implemented")
            }

            override fun addListener(listener: Player.Listener) {
                TODO("Not yet implemented")
            }

            override fun removeListener(listener: Player.Listener) {
                TODO("Not yet implemented")
            }

            override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
                TODO("Not yet implemented")
            }

            override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
                TODO("Not yet implemented")
            }

            override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
                TODO("Not yet implemented")
            }

            override fun setMediaItem(mediaItem: MediaItem) {
                TODO("Not yet implemented")
            }

            override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
                TODO("Not yet implemented")
            }

            override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
                TODO("Not yet implemented")
            }

            override fun addMediaItem(mediaItem: MediaItem) {
                TODO("Not yet implemented")
            }

            override fun addMediaItem(index: Int, mediaItem: MediaItem) {
                TODO("Not yet implemented")
            }

            override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
                TODO("Not yet implemented")
            }

            override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
                TODO("Not yet implemented")
            }

            override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
                TODO("Not yet implemented")
            }

            override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
                TODO("Not yet implemented")
            }

            override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
                TODO("Not yet implemented")
            }

            override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>) {
                TODO("Not yet implemented")
            }

            override fun removeMediaItem(index: Int) {
                TODO("Not yet implemented")
            }

            override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
                TODO("Not yet implemented")
            }

            override fun clearMediaItems() {
                TODO("Not yet implemented")
            }

            override fun isCommandAvailable(command: Int): Boolean {
                TODO("Not yet implemented")
            }

            override fun canAdvertiseSession(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getAvailableCommands(): Player.Commands {
                TODO("Not yet implemented")
            }

            override fun prepare() {
                TODO("Not yet implemented")
            }

            override fun getPlaybackState(): Int {
                TODO("Not yet implemented")
            }

            override fun getPlaybackSuppressionReason(): Int {
                TODO("Not yet implemented")
            }

            override fun isPlaying(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getPlayerError(): PlaybackException? {
                TODO("Not yet implemented")
            }

            override fun play() {
                TODO("Not yet implemented")
            }

            override fun pause() {
                TODO("Not yet implemented")
            }

            override fun setPlayWhenReady(playWhenReady: Boolean) {
                TODO("Not yet implemented")
            }

            override fun getPlayWhenReady(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setRepeatMode(repeatMode: Int) {
                TODO("Not yet implemented")
            }

            override fun getRepeatMode(): Int {
                TODO("Not yet implemented")
            }

            override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
                TODO("Not yet implemented")
            }

            override fun getShuffleModeEnabled(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isLoading(): Boolean {
                TODO("Not yet implemented")
            }

            override fun seekToDefaultPosition() {
                TODO("Not yet implemented")
            }

            override fun seekToDefaultPosition(mediaItemIndex: Int) {
                TODO("Not yet implemented")
            }

            override fun seekTo(positionMs: Long) {
                TODO("Not yet implemented")
            }

            override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
                TODO("Not yet implemented")
            }

            override fun getSeekBackIncrement(): Long {
                TODO("Not yet implemented")
            }

            override fun seekBack() {
                TODO("Not yet implemented")
            }

            override fun getSeekForwardIncrement(): Long {
                TODO("Not yet implemented")
            }

            override fun seekForward() {
                TODO("Not yet implemented")
            }

            override fun hasPrevious(): Boolean {
                TODO("Not yet implemented")
            }

            override fun hasPreviousWindow(): Boolean {
                TODO("Not yet implemented")
            }

            override fun hasPreviousMediaItem(): Boolean {
                TODO("Not yet implemented")
            }

            override fun previous() {
                TODO("Not yet implemented")
            }

            override fun seekToPreviousWindow() {
                TODO("Not yet implemented")
            }

            override fun seekToPreviousMediaItem() {
                TODO("Not yet implemented")
            }

            override fun getMaxSeekToPreviousPosition(): Long {
                TODO("Not yet implemented")
            }

            override fun seekToPrevious() {
                TODO("Not yet implemented")
            }

            override fun hasNext(): Boolean {
                TODO("Not yet implemented")
            }

            override fun hasNextWindow(): Boolean {
                TODO("Not yet implemented")
            }

            override fun hasNextMediaItem(): Boolean {
                TODO("Not yet implemented")
            }

            override fun next() {
                TODO("Not yet implemented")
            }

            override fun seekToNextWindow() {
                TODO("Not yet implemented")
            }

            override fun seekToNextMediaItem() {
                TODO("Not yet implemented")
            }

            override fun seekToNext() {
                TODO("Not yet implemented")
            }

            override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
                TODO("Not yet implemented")
            }

            override fun setPlaybackSpeed(speed: Float) {
                TODO("Not yet implemented")
            }

            override fun getPlaybackParameters(): PlaybackParameters {
                TODO("Not yet implemented")
            }

            override fun stop() {
                TODO("Not yet implemented")
            }

            override fun release() {
                TODO("Not yet implemented")
            }

            override fun getCurrentTracks(): Tracks {
                TODO("Not yet implemented")
            }

            override fun getTrackSelectionParameters(): TrackSelectionParameters {
                TODO("Not yet implemented")
            }

            override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
                TODO("Not yet implemented")
            }

            override fun getMediaMetadata(): MediaMetadata {
                TODO("Not yet implemented")
            }

            override fun getPlaylistMetadata(): MediaMetadata {
                TODO("Not yet implemented")
            }

            override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {
                TODO("Not yet implemented")
            }

            override fun getCurrentManifest(): Any? {
                TODO("Not yet implemented")
            }

            override fun getCurrentTimeline(): Timeline {
                TODO("Not yet implemented")
            }

            override fun getCurrentPeriodIndex(): Int {
                TODO("Not yet implemented")
            }

            override fun getCurrentWindowIndex(): Int {
                TODO("Not yet implemented")
            }

            override fun getCurrentMediaItemIndex(): Int {
                TODO("Not yet implemented")
            }

            override fun getNextWindowIndex(): Int {
                TODO("Not yet implemented")
            }

            override fun getNextMediaItemIndex(): Int {
                TODO("Not yet implemented")
            }

            override fun getPreviousWindowIndex(): Int {
                TODO("Not yet implemented")
            }

            override fun getPreviousMediaItemIndex(): Int {
                TODO("Not yet implemented")
            }

            override fun getCurrentMediaItem(): MediaItem? {
                TODO("Not yet implemented")
            }

            override fun getMediaItemCount(): Int {
                TODO("Not yet implemented")
            }

            override fun getMediaItemAt(index: Int): MediaItem {
                TODO("Not yet implemented")
            }

            override fun getDuration(): Long {
                TODO("Not yet implemented")
            }

            override fun getCurrentPosition(): Long {
                TODO("Not yet implemented")
            }

            override fun getBufferedPosition(): Long {
                TODO("Not yet implemented")
            }

            override fun getBufferedPercentage(): Int {
                TODO("Not yet implemented")
            }

            override fun getTotalBufferedDuration(): Long {
                TODO("Not yet implemented")
            }

            override fun isCurrentWindowDynamic(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isCurrentMediaItemDynamic(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isCurrentWindowLive(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isCurrentMediaItemLive(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getCurrentLiveOffset(): Long {
                TODO("Not yet implemented")
            }

            override fun isCurrentWindowSeekable(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isCurrentMediaItemSeekable(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isPlayingAd(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getCurrentAdGroupIndex(): Int {
                TODO("Not yet implemented")
            }

            override fun getCurrentAdIndexInAdGroup(): Int {
                TODO("Not yet implemented")
            }

            override fun getContentDuration(): Long {
                TODO("Not yet implemented")
            }

            override fun getContentPosition(): Long {
                TODO("Not yet implemented")
            }

            override fun getContentBufferedPosition(): Long {
                TODO("Not yet implemented")
            }

            override fun getAudioAttributes(): AudioAttributes {
                TODO("Not yet implemented")
            }

            override fun setVolume(volume: Float) {
                TODO("Not yet implemented")
            }

            override fun getVolume(): Float {
                TODO("Not yet implemented")
            }

            override fun clearVideoSurface() {
                TODO("Not yet implemented")
            }

            override fun clearVideoSurface(surface: Surface?) {
                TODO("Not yet implemented")
            }

            override fun setVideoSurface(surface: Surface?) {
                TODO("Not yet implemented")
            }

            override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
                TODO("Not yet implemented")
            }

            override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
                TODO("Not yet implemented")
            }

            override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
                TODO("Not yet implemented")
            }

            override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
                TODO("Not yet implemented")
            }

            override fun setVideoTextureView(textureView: TextureView?) {
                TODO("Not yet implemented")
            }

            override fun clearVideoTextureView(textureView: TextureView?) {
                TODO("Not yet implemented")
            }

            override fun getVideoSize(): VideoSize {
                TODO("Not yet implemented")
            }

            override fun getSurfaceSize(): androidx.media3.common.util.Size {
                TODO("Not yet implemented")
            }

            override fun getCurrentCues(): CueGroup {
                TODO("Not yet implemented")
            }

            override fun getDeviceInfo(): DeviceInfo {
                TODO("Not yet implemented")
            }

            override fun getDeviceVolume(): Int {
                TODO("Not yet implemented")
            }

            override fun isDeviceMuted(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setDeviceVolume(volume: Int) {
                TODO("Not yet implemented")
            }

            override fun setDeviceVolume(volume: Int, flags: Int) {
                TODO("Not yet implemented")
            }

            override fun increaseDeviceVolume() {
                TODO("Not yet implemented")
            }

            override fun increaseDeviceVolume(flags: Int) {
                TODO("Not yet implemented")
            }

            override fun decreaseDeviceVolume() {
                TODO("Not yet implemented")
            }

            override fun decreaseDeviceVolume(flags: Int) {
                TODO("Not yet implemented")
            }

            override fun setDeviceMuted(muted: Boolean) {
                TODO("Not yet implemented")
            }

            override fun setDeviceMuted(muted: Boolean, flags: Int) {
                TODO("Not yet implemented")
            }

            override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {
                TODO("Not yet implemented")
            }
        },
        {})
}

