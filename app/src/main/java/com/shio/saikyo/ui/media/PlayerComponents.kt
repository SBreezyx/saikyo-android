package com.shio.saikyo.ui.media

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.google.ai.client.generativeai.type.content
import com.shio.saikyo.R
import com.shio.saikyo.ui.theme.SaikyoTheme
import com.shio.saikyo.util.isApprox


@Composable
fun PlaybackSpeedButton(
    playbackRate: Float,
    onPlaybackSpeedClicked: () -> Unit,
    modifier: Modifier = Modifier
) = IconButton(
    onClick = onPlaybackSpeedClicked,
    modifier = modifier
) {
    val tint = if (playbackRate.isApprox(1f, eps = 0.005f)) {
        Color.White
    } else {
        MaterialTheme.colorScheme.primary
    }

    Icon(
        painter = painterResource(R.drawable.filled_speed_24),
        contentDescription = "Select playback speed",
        tint = tint
    )
}

@Composable
fun ClosedCaptionsButton(
    onShowSubtitleClicked: () -> Unit,
    modifier: Modifier = Modifier,
    showingSubtitles: Boolean = true
) = IconButton(
    onClick = onShowSubtitleClicked,
    modifier = modifier
) {
    val iconRes = if (showingSubtitles) {
        R.drawable.filled_subtitles_24
    } else {
        R.drawable.filled_subtitles_off_24
    }
    Icon(
        painter = painterResource(iconRes),
        contentDescription = "Turn subtitles on/off",
        tint = Color.White
    )
}

@Composable
fun PreviousItemButton(
    onPrevClicked: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current
) = IconButton(onPrevClicked, modifier = modifier) {
    // todo: get a proper icon
    Icon(
        painterResource(R.drawable.skip_previous_24dp_fill),
        contentDescription = null,
        tint = color,
    )
}

@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    onPlayPauseClicked: () -> Unit,
    modifier: Modifier = Modifier
) = IconButton(onPlayPauseClicked, modifier = modifier) {
    val iconRes = if (isPlaying) {
        R.drawable.filled_pause_24
    } else {
        R.drawable.filled_play_arrow_24
    }
    Icon(
        painterResource(iconRes),
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(56.dp)
    )
}

@Composable
fun NextItemButton(
    onNextClicked: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current
) = IconButton(onNextClicked, modifier = modifier) {
    // todo: get a proper icon
    Icon(
        painterResource(R.drawable.skip_next_24dp_fill),
        contentDescription = null,
        tint = color
    )
}

@Composable
fun TimelineSlider(
    currTimestamp: Long,
    maxTimestamp: Long,
    onSliderSeek: (Float) -> Unit,
    onSliderSeeked: () -> Unit,
    modifier: Modifier = Modifier,
    percentCompleted: Float = currTimestamp / maxTimestamp.toFloat()
) = Box(
    modifier = modifier
        .fillMaxWidth()
) {
    Text(
        text = currTimestamp.toTimestamp(),
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 8.dp)
    )

    Text(
        text = maxTimestamp.toTimestamp(),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = 8.dp)
    )

    Slider(
        value = percentCompleted,
        onValueChange = onSliderSeek,
        onValueChangeFinished = onSliderSeeked,
        modifier = Modifier.padding(top = 16.dp),
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer,
        )
    )
}

@Composable
fun PlaybackRateSheetActual(
    currRate: Float,
    onRateChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    minRate: Float = 0.01f,
    maxRate: Float = 2f,
) {
    val containerColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.contentColorFor(containerColor)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = containerColor,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Playback speed",
                    style = MaterialTheme.typography.titleLarge,
                    color = textColor,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                ) {
                    val selectedChipColors = SuggestionChipDefaults.suggestionChipColors(
                        labelColor = MaterialTheme.colorScheme.onSecondary,
                        containerColor = MaterialTheme.colorScheme.secondary,
                    )
                    val unselectedChipColors = SuggestionChipDefaults.suggestionChipColors(
                        labelColor = textColor
                    )
                    arrayOf(0.75f, 0.85f, 1f, 1.15f, 1.25f).map { rate ->
                        SuggestionChip(
                            onClick = {
                                onRateChange(rate)
                            },
                            label = {
                                Text(
                                    "%.2fx".format(rate),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            shape = MaterialTheme.shapes.large,
                            colors = if (currRate.isApprox(rate)) {
                                selectedChipColors
                            } else {
                                unselectedChipColors
                            }
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "%.2f".format(minRate),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor
                    )
                    Text(
                        text = "%.2fx".format(currRate),
                        modifier = Modifier.align(Alignment.TopCenter),
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor
                    )
                    Text(
                        text = "%.2f".format(maxRate),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor
                    )
                    Slider(
                        value = currRate,
                        onValueChange = onRateChange,
                        valueRange = (minRate..maxRate),
                        steps = 200,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Column {
                    IconButton(
                        onClick = {
                            onRateChange(currRate + 0.01f)
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.filled_arrow_up_24),
                            contentDescription = null, // TODO: add content description,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    IconButton(
                        onClick = { onRateChange(currRate - 0.01f) }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.filled_arrow_down_24),
                            contentDescription = null, // TODO: add content description),
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackRateSheet(
    currRate: Float,
    onRateChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    minRate: Float = 0.01f,
    maxRate: Float = 2f,
    onDismiss: () -> Unit = {},
) = ModalBottomSheet(
    // TODO: make this a bottom modal sheet
    onDismissRequest = onDismiss,
) {
    PlaybackRateSheetActual(
        currRate = currRate,
        onRateChange = onRateChange,
        modifier = modifier,
        contentPadding = contentPadding,
        minRate = minRate,
        maxRate = maxRate
    )
}

@PreviewScreenSizes
@Preview
@Composable
private fun PlaybackSheetPreview() = SaikyoTheme(darkTheme = true) {
    PlaybackRateSheetActual(
        currRate = 0.5f,
        onRateChange = {}
    )
}