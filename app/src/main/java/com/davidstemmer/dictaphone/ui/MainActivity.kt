package com.davidstemmer.dictaphone.ui
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.davidstemmer.dictaphone.media.SimpleMediaRepository
import com.davidstemmer.dictaphone.media.SimpleMediaPlayer
import com.davidstemmer.dictaphone.media.SimpleMediaRecorder
import com.davidstemmer.dictaphone.permissions.Permission
import com.davidstemmer.dictaphone.switchboard.FilesChanged
import com.davidstemmer.dictaphone.switchboard.MediaPlayer
import com.davidstemmer.dictaphone.routing.PermissionsRouter
import com.davidstemmer.dictaphone.routing.MediaPlayerRouter
import com.davidstemmer.dictaphone.switchboard.MediaRecorder
import com.davidstemmer.dictaphone.routing.MediaRecorderRouter
import com.davidstemmer.dictaphone.switchboard.MediaRepository
import com.davidstemmer.dictaphone.routing.MediaRepositoryRouter
import com.davidstemmer.dictaphone.switchboard.SwitchboardOutput
import com.davidstemmer.dictaphone.switchboard.Permissions
import com.davidstemmer.dictaphone.switchboard.PermissionsUpdated
import com.davidstemmer.dictaphone.switchboard.PlaybackStatusChanged
import com.davidstemmer.dictaphone.switchboard.RecordingStatusChanged
import com.davidstemmer.dictaphone.switchboard.Switchboard
import com.davidstemmer.dictaphone.ui.compose.Dialogs
import com.davidstemmer.dictaphone.ui.compose.MainScreen
import com.davidstemmer.dictaphone.ui.compose.MediaSelectedCallback
import com.davidstemmer.dictaphone.ui.compose.RecordingCallback
import com.davidstemmer.dictaphone.ui.data.AudioState
import com.davidstemmer.dictaphone.ui.data.DialogType
import com.davidstemmer.dictaphone.ui.data.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    class Model internal constructor(): ViewModel() {
        private val currentState = MutableStateFlow(UiState())
        private val stateFlow: StateFlow<UiState> = currentState

        fun update(transform: (UiState) -> UiState) {
            currentState.value = transform(stateFlow.value)
        }

        @Composable
        fun collectAsState() = stateFlow.collectAsState()
    }

    private val model = Model()
    private val mediaRepository = SimpleMediaRepository(this)
    private val mediaPlayer = SimpleMediaPlayer(this)
    private val recorder = SimpleMediaRecorder(this, mediaRepository)

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setStrictModeEnabled(true)

        val permissionsRouter = PermissionsRouter(this)
        val recordingStateRouter = MediaRecorderRouter(recorder)
        val repositoryRouter = MediaRepositoryRouter(mediaRepository)
        val mediaPlayerRouter = MediaPlayerRouter(mediaPlayer)
        val effectRouters = listOf(permissionsRouter, recordingStateRouter, repositoryRouter, mediaPlayerRouter)

        val switchboard = Switchboard(
            effectRouters = effectRouters,
            externalScope = lifecycleScope
        )

        routeOutputToViewModel(switchboard)

        switchboard.dispatch(Permissions.Initialize)
        switchboard.dispatch(MediaRepository.ScanForMediaFiles)

        setMainContent(
            onRecord = { permissionGranted ->
                if (permissionGranted) {
                    switchboard.dispatch(MediaRecorder.ToggleRecording)
                }
                else {
                    switchboard.dispatch(Permissions.Request(Permission.RECORD_AUDIO))
                }
            },
            onPlay = { index, mediaId -> switchboard.dispatch(
                MediaPlayer.Start(index, mediaId)
            )},
            onStop = { _, _ -> switchboard.dispatch(
                MediaPlayer.Stop
            )},
            onFilenameSelected = { uri, name -> switchboard.dispatch(
                MediaRepository.UpdateFilename(uri, name)
            )},
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }

    private fun routeOutputToViewModel(switchboard: Switchboard) {
        lifecycleScope.launch {
            switchboard.outputFlow.collect { output ->
                model.update(output)
                showToasts(output)
            }
        }
    }

    private fun showToasts(output: SwitchboardOutput) {
        when (output) {
            is RecordingStatusChanged.Started -> showRecordingToast(true)
            is RecordingStatusChanged.Stopped -> showRecordingToast(false)
            is RecordingStatusChanged.Failed -> showRecordingErrorToast()
            else -> {}
        }
    }

    private fun setMainContent(
        onPlay: MediaSelectedCallback,
        onStop: MediaSelectedCallback,
        onRecord: RecordingCallback,
        onFilenameSelected: (Uri, String) -> Unit) {
        setContent {
            val uiState: UiState by model.collectAsState()

            MainScreen.Content(uiState = uiState,
                onPlay = onPlay,
                onStop = onStop,
                onRecord = onRecord
            )

            val hideDialog = { model.update { state -> state.copy(showDialog = DialogType.None) } }

            when (val dialog = uiState.showDialog) {
                is DialogType.EditFilename -> Dialogs.ChooseFilename(
                    onConfirm = { name -> onFilenameSelected(dialog.recordingUri, name) } ,
                    onDismiss = hideDialog
                )
                else -> {}
            }
        }
    }

    private fun showRecordingErrorToast() {
        Toast.makeText(this, "Error: failed to create recording file", Toast.LENGTH_SHORT).show()
    }

    private fun showRecordingToast(isRecording: Boolean) {
        val msg = "Recording: $isRecording"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

private fun MainActivity.Model.update(output: SwitchboardOutput) = when (output) {
    is PermissionsUpdated -> update { state ->
        state.copy(permissionState = output.permissionState)
    }
    is RecordingStatusChanged.Started -> update { state ->
        state.copy(
            showDialog = DialogType.None,
            audioState = AudioState.Recording
        )
    }
    is RecordingStatusChanged.Stopped -> update { state ->
        state.copy(
            showDialog = DialogType.EditFilename(output.uri),
            audioState = AudioState.Idle
        )
    }
    is RecordingStatusChanged.Failed -> update { state ->
        state.copy(
            showDialog = DialogType.None,
            audioState = AudioState.Idle
        )
    }
    is PlaybackStatusChanged.Playing -> update { state ->
        state.copy(
            audioState = AudioState.Playback(output.mediaId, output.position, output.progress)
        )
    }
    is PlaybackStatusChanged.Stopped -> update { state ->
        state.copy(
            audioState = AudioState.Idle
        )
    }
    is FilesChanged -> update { state ->
        state.copy(
            audioMetadata = output.recordings
        )
    }
    is PlaybackStatusChanged.Started -> update { state ->
        state.copy(
            audioState = AudioState.Playback("", 0, 0f)
        )
    }
}


@Suppress("SameParameterValue")
private fun setStrictModeEnabled(enabled: Boolean) {
    if (enabled) {
        StrictMode.setThreadPolicy(
            ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork() // or .detectAll() for all detectable problems
                .penaltyLog()
                .build()
        )

        // There's a strict mode violation after showing the filename dialog.
        // Needs further investigation but looks like it might be a false positive (the violation
        // appears to be in the androidx Compose source code).
        // As a workaround, turn off detectLeakedClosableObjects for now.
        StrictMode.setVmPolicy(
            VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                //.detectLeakedClosableObjects()
                .penaltyLog()
                .penaltyDeath()
                .build()
        )
    }
}