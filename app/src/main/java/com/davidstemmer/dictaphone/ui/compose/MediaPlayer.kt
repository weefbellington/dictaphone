package com.davidstemmer.dictaphone.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.davidstemmer.dictaphone.R
import com.davidstemmer.dictaphone.data.AudioMetadata
import com.davidstemmer.dictaphone.ui.data.AudioState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

typealias RecordingCallback = (Boolean) -> Unit
typealias MediaSelectedCallback = (Int, String) -> Unit

@Composable
fun MediaPlayer(modifier: Modifier = Modifier,
                listData: List<AudioMetadata>,
                audioState: AudioState,
                onPlay: MediaSelectedCallback,
                onStop: MediaSelectedCallback) {

    val listState = rememberLazyListState()

    LaunchedEffect(key1 = listData) {
        listState.animateScrollToItem(index = 0)
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        state = listState
    ) {
        itemsIndexed(
            listData,
            key = { _, data -> data.mediaId }
        ) { index, metadata ->
            AudioListItem(
                index = index,
                audioState = audioState,
                metadata = metadata,
                onPlay = onPlay,
                onStop = onStop
            )
            if (index < listData.size) {
                Divider(thickness = 1.dp)
            }
        }

    }
}

@Composable
private fun AudioListItem(modifier: Modifier = Modifier,
                          index: Int,
                          audioState: AudioState,
                          metadata: AudioMetadata,
                          onPlay: MediaSelectedCallback,
                          onStop: MediaSelectedCallback) {

    val isPlayingCurrentItem = audioState is AudioState.Playback && audioState.id == metadata.mediaId
    val (playbackPosition, playbackProgress) = when (audioState) {
        is AudioState.Playback -> audioState.position to audioState.progress
        else -> 0L to 0.0f
    }

    val onClick = when (audioState) {
        is AudioState.Idle -> onPlay
        is AudioState.Playback ->
            if (isPlayingCurrentItem) { onStop }
            else { onPlay }
        is AudioState.Recording -> null
    }

    val durationFormat = "%02d:%02d"
    val durationMinutes = TimeUnit.MILLISECONDS.toMinutes(metadata.durationMs)
    val durationSeconds = TimeUnit.MILLISECONDS.toSeconds(metadata.durationMs) % 60
    val durationText = String.format(durationFormat, durationMinutes, durationSeconds)

    val progressAlpha = if (isPlayingCurrentItem) { 1.0f } else { 0.0f }

    @Composable fun StartText() {
        if (isPlayingCurrentItem) {
            val progressMinutes = TimeUnit.MILLISECONDS.toMinutes(playbackPosition)
            val progressSeconds = TimeUnit.MILLISECONDS.toSeconds(playbackPosition) % 60
            val text = String.format(durationFormat, progressMinutes, progressSeconds)
            Text(text, color = ProgressIndicatorDefaults.linearColor)
        } else {
            val formatter = SimpleDateFormat("MM/dd/yyyy - hh:mm a", Locale.US)
            val text = formatter.format(Date(metadata.createdDate * 1000L))
            LowEmphasisAlpha { DisableWhen(audioState is AudioState.Recording) { Text(text) } }
        }
    }

    ListItem(
        leadingContent = {
            when (audioState) {
                is AudioState.Idle -> PlayIcon()
                is AudioState.Playback ->
                    if (isPlayingCurrentItem) {
                        StopIcon()
                    } else {
                        PlayIcon()
                    }
                is AudioState.Recording -> PlayIcon(Modifier.alpha(0.38f))
            }
        },
        headlineContent = {
            DisableWhen(audioState is AudioState.Recording) {
                Text(text = metadata.name, maxLines = 1)
            }
        },
        supportingContent = {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically){
                StartText()
                Spacer(modifier = Modifier.width(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.0f)
                        .height(4.dp)
                        .alpha(progressAlpha),
                    progress = playbackProgress
                )
                Spacer(modifier = Modifier.width(8.dp))
                DisableWhen(audioState is AudioState.Recording) { Text (text = durationText) }
            }

        },
        modifier = modifier
            .clickable {
                if (onClick != null) {
                    onClick(index, metadata.mediaId)
                }
            }
            .height(72.0.dp)
    )
}

@Composable
fun PlayIcon(modifier: Modifier = Modifier) {
    Image(
        modifier = modifier.fillMaxHeight(0.7f),
        imageVector = ImageVector.vectorResource(R.drawable.baseline_play_circle_24),
        contentDescription = stringResource(R.string.play_audio_content_description),
        contentScale = ContentScale.FillHeight,
        colorFilter = ColorFilter.tint(color = MaterialTheme.colorScheme.onBackground)
    )
}

@Composable
fun StopIcon(modifier: Modifier = Modifier) {
    Image(
        modifier = modifier.fillMaxHeight(0.7f),
        imageVector = ImageVector.vectorResource(R.drawable.baseline_stop_circle_24),
        contentDescription = stringResource(R.string.stop_audio_content_description),
        contentScale = ContentScale.FillHeight,
        colorFilter = ColorFilter.tint(color = MaterialTheme.colorScheme.onBackground)
    )
}

