package com.davidstemmer.dictaphone.ui.compose

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.davidstemmer.dictaphone.data.AudioMetadata
import com.davidstemmer.dictaphone.ui.data.AudioState
import com.davidstemmer.dictaphone.ui.data.DialogType
import com.davidstemmer.dictaphone.ui.data.UiState
import com.davidstemmer.dictaphone.ui.theme.MyApplicationTheme
import kotlin.random.Random

object MainScreen {
    @Composable
    fun Content(@PreviewParameter(UiStateProvider::class) uiState: UiState,
                modifier: Modifier = Modifier,
                onPlay: MediaSelectedCallback,
                onStop: MediaSelectedCallback,
                onRecord: RecordingCallback? = null) {
        MyApplicationTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                MainScaffold(
                    modifier = modifier,
                    uiState = uiState,
                    onPlay = onPlay,
                    onStop = onStop,
                    onRecord = onRecord ?: {})
            }
        }
    }

    @Composable
    private fun MainScaffold(modifier: Modifier = Modifier,
                             uiState: UiState,
                             onPlay: MediaSelectedCallback,
                             onStop: MediaSelectedCallback,
                             onRecord: RecordingCallback) {
        Scaffold(
            modifier = modifier,
            floatingActionButton = {
                RemoveWhen(condition = uiState.audioState is AudioState.Playback) {
                    RecordAudioButton(
                        audioState = uiState.audioState,
                        hasRecordAudioPermission = uiState.permissions.recordAudio,
                        onRecord = onRecord,
                    )
                }
            }
        ) { innerPadding ->
            val listModifier = modifier
                .fillMaxWidth()
                .padding(innerPadding)
            MediaPlayer(
                modifier = listModifier,
                listData = uiState.audioMetadata,
                audioState = uiState.audioState,
                onPlay = onPlay,
                onStop = onStop)
        }
    }
}


class UiStateProvider : PreviewParameterProvider<UiState> {

    private val audioFiles = (0..10).map { i ->
        AudioMetadata(
            mediaId = i.toString(),
            uri = Uri.EMPTY,
            name = "recording$i",
            createdDate = 0L,
            durationMs = Random.nextLong(100000L))
    }


    private val state1 = UiState(
        audioMetadata = audioFiles,
        audioState = AudioState.Idle,
        showDialog = DialogType.None
    )

    override val values: Sequence<UiState> = sequenceOf(
        state1
    )
}
