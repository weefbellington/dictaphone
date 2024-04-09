package com.davidstemmer.dictaphone.switchboard

import com.davidstemmer.dictaphone.data.AudioMetadata
import com.davidstemmer.dictaphone.data.PermissionState
import com.davidstemmer.dictaphone.permissions.Permission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class Switchboard(
    private val externalScope: CoroutineScope,
    private val effectRouters: List<SwitchboardRouter> = listOf()
): ActionDispatcher {

    /**
     * Dispatch an Action to the switchboard. Each action will be processed by the following:
     * (1) action reducer (2) effect router (3) output router.
     * When the reducer emits a null value, steps (2) and (3) are skipped.
     * @param message
     */
    override fun dispatch(message: SwitchboardMessage) { externalScope.launch { messageFlow.emit(message) } }

    private val messageFlow = MutableSharedFlow<SwitchboardMessage>(replay = 0)

    private data class ReducerOutput(val state: State,
                                     val message: SwitchboardMessage,
                                     val result: ReducerResult
    )

    private enum class ReducerResult { SKIPPED, UPDATED }

    private val initialState: ReducerOutput = ReducerOutput(
        state = State(),
        message = SwitchboardMessage.Initialization,
        result = ReducerResult.UPDATED
    )

    private val reducerFlow = messageFlow
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
        .shareIn(scope = externalScope, started = SharingStarted.Eagerly, replay = 1)

    val outputFlow = reducerFlow
        .map { (state, action) -> routeOutput(state, action) }
        .filterNotNull()

    init {
        externalScope.launch {
            reducerFlow.collect { (state, action) ->
                routeEffects(state, action)
            }
        }
    }

    private fun reduce(state: State, message: SwitchboardMessage): State? {
        return when (message) {
            is SwitchboardMessage.Initialization -> state
            is MediaRepository.ScanForMediaFiles -> state
            is MediaRepository.UpdateFilename -> state
            is Permissions.Initialize -> state
            is Permissions.Update -> when (message.permission) {
                Permission.RECORD_AUDIO -> state.copy(
                    permissionState = state.permissionState.copy(recordAudio = message.allow)
                )
            }
            is Permissions.Request -> state
            is SetAudioState -> when (message) {
                is SetAudioState.Idle -> state.copy(
                    audioState = AudioState.Idle
                )
                is SetAudioState.PlaybackStarted -> state.copy(
                    audioState = AudioState.Playback.Started
                )
                is SetAudioState.RecordingStarted -> state.copy(
                    audioState = AudioState.Recording.Started
                )
            }
            is MediaRecorder.ToggleRecording -> state
            is MediaRecorder.StartRecording -> when (state.audioState) {
                is AudioState.Idle -> state.copy(
                    audioState = AudioState.Recording.Starting
                )
                else -> null
            }
            is MediaRecorder.StopRecording -> when (state.audioState) {
                is AudioState.Recording.Started -> state.copy(
                    audioState = AudioState.Recording.Stopping
                )
                else -> null
            }
            is MediaPlayer.Start -> when (state.audioState) {
                is AudioState.Idle -> state.copy(
                    audioState = AudioState.Playback.Starting(message.index, message.mediaId)
                )
                is AudioState.Playback.Started -> state
                else -> null
            }
            is MediaPlayer.Stop -> when (state.audioState) {
                is AudioState.Playback.Started -> state.copy(
                    audioState = AudioState.Playback.Stopping
                )
                else -> null
            }
            is MediaPlayer.StartProgressUpdates -> state
            is MediaPlayer.StopProgressUpdates -> state
            is MediaRepository.FileScanComplete -> state.copy(recordings = message.files)
            is SendOutput -> state
        }
    }

    private fun routeOutput(state: State, message: SwitchboardMessage): SwitchboardOutput? {
        return when (message) {
            is SendOutput -> message.outputFn(state)
            else -> null
        }
    }

    private fun CoroutineScope.routeEffects(state: State, oldMessage: SwitchboardMessage) {
        val routerScope = createRouterScope { action ->
            dispatch(action)
        }
        for (router: SwitchboardRouter in effectRouters) {
            with(router) {
                routerScope.tryHandle(state, oldMessage)
            }
        }
    }

    data class State(
        val recordings: List<AudioMetadata> = listOf(),
        val audioState: AudioState = AudioState.Idle,
        val permissionState: PermissionState = PermissionState()
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
            data object Started: AudioState
            data object Stopping: AudioState
        }
    }
}