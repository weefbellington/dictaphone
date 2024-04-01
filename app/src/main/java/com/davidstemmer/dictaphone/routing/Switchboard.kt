package com.davidstemmer.dictaphone.routing

import android.net.Uri
import com.davidstemmer.dictaphone.data.AudioMetadata
import com.davidstemmer.dictaphone.data.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import java.io.Closeable



class Switchboard(
    private val externalScope: CoroutineScope,
    private val effectRouters: List<EffectRouter> = listOf()
) {
    /**
     * Dispatch an Action to the switchboard. Each action will be processed by the following:
     * (1) action reducer (2) effect router (3) output router.
     * When the reducer emits a null value, steps (2) and (3) are skipped.
     * @param action
     */
    fun dispatch(action: Action) { externalScope.launch { actionFlow.emit(action) } }

    private val actionFlow = MutableSharedFlow<Action>(replay = 0)

    private data class ReducerOutput(val state: State,
                                     val action: Action,
                                     val result: ReducerResult)

    private enum class ReducerResult { SKIPPED, UPDATED }

    private val initialState: ReducerOutput = ReducerOutput(
        state = State(),
        action = Action.Initialization,
        result = ReducerResult.UPDATED)

    val outputFlow = actionFlow
        .scan(initialState) { accumulator, action ->
            val oldState = accumulator.state
            when (val newState = reduce(oldState, action)) {
                null -> ReducerOutput(oldState, action, ReducerResult.SKIPPED)
                else -> ReducerOutput(newState, action, ReducerResult.UPDATED)
            }
        }
        .filter {
            reducedValue -> reducedValue.result == ReducerResult.UPDATED
        }
        .map { result ->
            routeEffects(result.state, result.action)
            result
        }
        .map {
            routeOutput(it.state, it.action)
        }
        .filterNotNull()
        .shareIn(scope = externalScope, started = SharingStarted.Eagerly, replay = 1)

    private fun reduce(state: State, action: Action): State? {
        return when (action) {
            is Callback.PermissionsRequest.RecordAudio ->
                state.copy(permissions = state.permissions.copy(recordAudio = action.granted))
            is Callback.RecordingState.RecordingStopped,
            is Callback.RecordingState.RecordingFailed -> state.copy(
                audioState = AudioState.Idle
            )
            is Callback.RecordingState.RecordingStarted -> state.copy(
                audioState = AudioState.Recording.Started(action.uri, action.fileHandle)
            )
            is Callback.Repository.DataFetchComplete ->
                state.copy(recordings = action.recordings)
            is Callback.MediaPlayer.PlaybackStarted -> state.copy(
                audioState = AudioState.Playback.Started
            )
            is Callback.MediaPlayer.PlaybackEnded -> state.copy(
                audioState = AudioState.Idle
            )
            is Callback.MediaPlayer.PlaylistFinished -> state
            is Callback.MediaPlayer.PlaybackUpdate -> state
            is Action.ToggleRecording -> when (val audioState = state.audioState) {
                is AudioState.Idle -> state.copy(
                    audioState = AudioState.Recording.Starting
                )
                is AudioState.Recording.Started -> state.copy(
                    audioState = AudioState.Recording.Stopping(
                        uri = audioState.uri,
                        fileHandle = audioState.fileHandle
                    )
                )
                is AudioState.Recording.Starting -> null
                is AudioState.Recording.Stopping -> null
                is AudioState.Playback.Starting -> null
                is AudioState.Playback.Started -> null
                is AudioState.Playback.Stopping -> null
            }

            is Action.StartPlayback -> when (state.audioState) {
                is AudioState.Idle -> state.copy(
                    audioState = AudioState.Playback.Starting(action.index, action.mediaId)
                )
                is AudioState.Playback.Started -> state
                else -> null
            }
            is Action.StopPlayback -> when (state.audioState) {
                is AudioState.Playback.Started -> state.copy(
                    audioState = AudioState.Playback.Stopping
                )
                else -> null
            }
            is Action.Initialization,
            is Action.FetchRecordingData,
            is Action.UpdateFilename -> state
        }
    }

    private fun routeOutput(state: State, action: Action): Output? {
        return when (action) {
            is Callback.PermissionsRequest.RecordAudio ->
                Output.PermissionsUpdated(state.permissions)
            is Callback.RecordingState.RecordingStarted ->
                Output.RecordingStatusChanged.Started
            is Callback.RecordingState.RecordingStopped ->
                Output.RecordingStatusChanged.Stopped(action.uri)
            is Callback.RecordingState.RecordingFailed ->
                Output.RecordingStatusChanged.Failed
            is Callback.Repository.DataFetchComplete ->
                Output.FilesChanged(action.recordings)
            is Callback.MediaPlayer.PlaybackEnded ->
                Output.PlaybackStatusChanged.Stopped
            is Callback.MediaPlayer.PlaybackUpdate ->
                Output.PlaybackStatusChanged.Playing(
                    action.mediaId,
                    action.position,
                    action.progress
                )
            is Callback.MediaPlayer.PlaybackStarted ->
                Output.PlaybackStatusChanged.Started
            is Action.StopPlayback ->
                Output.PlaybackStatusChanged.Stopped
            is Callback.MediaPlayer.PlaylistFinished,
            is Action.Initialization,
            is Action.StartPlayback,
            is Action.FetchRecordingData,
            is Action.ToggleRecording,
            is Action.UpdateFilename -> null
        }
    }

    private fun routeEffects(state: State, oldAction: Action) {
        for (router in effectRouters) {
            with (router) {
                externalScope.handle(state, oldAction) { newAction ->
                    dispatch(newAction)
                }
            }
        }
    }

    data class State(
        val recordings: List<AudioMetadata> = listOf(),
        val audioState: AudioState = AudioState.Idle,
        val permissions: Permissions = Permissions()
    )

    sealed interface AudioState {
        data object Idle: AudioState
        object Playback {
            data class Starting(val index: Int, val mediaId: String): AudioState
            data object Started : AudioState
            data object Stopping: AudioState
        }
        object Recording {
            data object Starting: AudioState
            data class Started(val uri: Uri, val fileHandle: Closeable?): AudioState
            data class Stopping(val uri: Uri, val fileHandle: Closeable?): AudioState
        }
    }

    sealed interface Action {
        data object Initialization: Action
        data object FetchRecordingData: Action
        data object ToggleRecording : Action
        data class UpdateFilename(val uri: Uri, val name: String): Action
        data class StartPlayback(val index: Int, val mediaId: String): Action
        data object StopPlayback: Action
    }

    sealed interface Callback: Action {
        object PermissionsRequest {
            data class RecordAudio(val granted: Boolean): Callback
        }
        object MediaPlayer {

            data object PlaybackStarted: Callback
            data class PlaybackUpdate(val mediaId: String,
                                      val position: Long,
                                      val progress: Float): Callback
            data object PlaylistFinished: Callback
            data object PlaybackEnded: Callback
        }
        object Repository {
            data class DataFetchComplete(val recordings: List<AudioMetadata>): Action
        }
        object RecordingState {
            data class RecordingStarted(val uri: Uri, val fileHandle: Closeable): Action
            data class RecordingStopped(val uri: Uri): Action
            data object RecordingFailed: Action
        }
    }

    sealed interface Output {

        data class FilesChanged(val recordings: List<AudioMetadata>): Output

        data class PermissionsUpdated(val permissions: Permissions): Output

        sealed interface RecordingStatusChanged: Output {
            data object Started: Output
            data class Stopped(val uri: Uri): Output
            data object Failed: Output
        }
        sealed interface PlaybackStatusChanged: Output {
            data object Started: Output
            data class Playing(val mediaId: String, val position: Long, val progress: Float): Output
            data object Stopped: Output
        }
    }
}

fun interface EffectRouter {
    fun CoroutineScope.handle(state: Switchboard.State, action: Switchboard.Action, dispatch: (Switchboard.Action) -> Unit)
}
