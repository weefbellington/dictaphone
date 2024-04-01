package com.davidstemmer.dictaphone.ui
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.CheckResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.davidstemmer.dictaphone.media.MediaRepository
import com.davidstemmer.dictaphone.media.SimpleMediaPlayer
import com.davidstemmer.dictaphone.media.SimpleMediaRecorder
import com.davidstemmer.dictaphone.permissions.Permission
import com.davidstemmer.dictaphone.permissions.PermissionRequest
import com.davidstemmer.dictaphone.permissions.hasPermission
import com.davidstemmer.dictaphone.routing.PlaybackRouter
import com.davidstemmer.dictaphone.routing.RecordingRouter
import com.davidstemmer.dictaphone.routing.RepositoryRouter
import com.davidstemmer.dictaphone.routing.Switchboard
import com.davidstemmer.dictaphone.ui.compose.Dialogs
import com.davidstemmer.dictaphone.ui.compose.MainScreen
import com.davidstemmer.dictaphone.ui.compose.MediaSelectedCallback
import com.davidstemmer.dictaphone.ui.compose.RecordingCallback
import com.davidstemmer.dictaphone.ui.data.AudioState
import com.davidstemmer.dictaphone.ui.data.DialogType
import com.davidstemmer.dictaphone.ui.data.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.davidstemmer.dictaphone.routing.Switchboard.Action
import com.davidstemmer.dictaphone.routing.Switchboard.Callback
import com.davidstemmer.dictaphone.routing.Switchboard.Output
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
    private val mediaRepository = MediaRepository(this)
    private val mediaPlayer = SimpleMediaPlayer(this)
    private val recorder = SimpleMediaRecorder(this, mediaRepository)

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setStrictModeEnabled(false)

        val recordingStateRouter = RecordingRouter(recorder)
        val repositoryRouter = RepositoryRouter(mediaRepository)
        val playbackRouter = PlaybackRouter(mediaPlayer)
        val effectRouters = listOf(recordingStateRouter, repositoryRouter, playbackRouter)

        val switchboard = Switchboard(
            effectRouters = effectRouters,
            externalScope = lifecycleScope
        )

        routeOutputToViewModel(switchboard)
        initializeMediaListener(switchboard)
        initializePermissions(switchboard)

        switchboard.dispatch(Action.FetchRecordingData)

        // This request must be initialized in onCreate because it registers an ActivityResult.
        // Doing it later (in a callback, for example) will trigger an IllegalStateException/
        val recordAudioPermissionRequest = createRecordAudioPermissionRequest(switchboard)

        setMainContent(
            onRecord = { permissionGranted ->
                if (permissionGranted) { switchboard.dispatch(Action.ToggleRecording) }
                else { recordAudioPermissionRequest.launch() }
            },
            onPlay = { index, mediaId -> switchboard.dispatch(Action.StartPlayback(index, mediaId)) },
            onStop = { _, _ -> switchboard.dispatch(Action.StopPlayback) },
            onFilenameSelected = { uri, name ->
                switchboard.dispatch(Action.UpdateFilename(uri, name))
            },

        )
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }

    private fun initializeMediaListener(switchboard: Switchboard) {
        mediaPlayer.addListener { event ->
            val action = when (event) {
                is SimpleMediaPlayer.Event.PlaylistFinished -> Callback.MediaPlayer.PlaylistFinished
            }
            switchboard.dispatch(action)
        }
    }

    private fun routeOutputToViewModel(switchboard: Switchboard) {
        lifecycleScope.launch {
            switchboard.outputFlow.collect { output ->
                model.update(output)
                showToasts(output)
            }
        }
    }

    private fun showToasts(output: Output) {
        when (output) {
            is Output.RecordingStatusChanged.Started -> showRecordingToast(true)
            is Output.RecordingStatusChanged.Stopped -> showRecordingToast(false)
            is Output.RecordingStatusChanged.Failed -> showRecordingErrorToast()
            else -> {}
        }
    }

    private fun initializePermissions(switchboard: Switchboard) {
        if (hasPermission(Permission.RECORD_AUDIO)) {
            switchboard.dispatch(Callback.PermissionsRequest.RecordAudio(granted = true))
        }
    }

    @CheckResult
    private fun createRecordAudioPermissionRequest(switchboard: Switchboard): PermissionRequest {
        return PermissionRequest(this, Permission.RECORD_AUDIO) { granted ->
            switchboard.dispatch(Callback.PermissionsRequest.RecordAudio(granted))
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

private fun MainActivity.Model.update(output: Output) = when (output) {
    is Output.PermissionsUpdated -> update { state ->
        state.copy(permissions = output.permissions)
    }
    is Output.RecordingStatusChanged.Started -> update { state ->
        state.copy(
            showDialog = DialogType.None,
            audioState = AudioState.Recording
        )
    }
    is Output.RecordingStatusChanged.Stopped -> update { state ->
        state.copy(
            showDialog = DialogType.EditFilename(output.uri),
            audioState = AudioState.Idle
        )
    }
    is Output.RecordingStatusChanged.Failed -> update { state ->
        state.copy(
            showDialog = DialogType.None,
            audioState = AudioState.Idle
        )
    }
    is Output.PlaybackStatusChanged.Playing -> update { state ->
        state.copy(
            audioState = AudioState.Playback(output.mediaId, output.position, output.progress)
        )
    }
    is Output.PlaybackStatusChanged.Stopped -> update { state ->
        state.copy(
            audioState = AudioState.Idle
        )
    }
    is Output.FilesChanged -> update { state ->
        state.copy(
            audioMetadata = output.recordings
        )
    }
    is Output.PlaybackStatusChanged.Started -> update { state ->
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