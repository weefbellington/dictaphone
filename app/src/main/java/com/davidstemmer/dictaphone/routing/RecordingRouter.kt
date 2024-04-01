package com.davidstemmer.dictaphone.routing

import android.net.Uri
import android.util.Log
import com.davidstemmer.dictaphone.media.SimpleMediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.Closeable
import com.davidstemmer.dictaphone.routing.Switchboard.Action
import com.davidstemmer.dictaphone.routing.Switchboard.Callback

class RecordingRouter(private val recorder: SimpleMediaRecorder): EffectRouter {

    override fun CoroutineScope.handle(state: Switchboard.State, action: Action, dispatch: (Action) -> Unit) {
        if (action is Action.ToggleRecording) {
            when (val audioState = state.audioState) {
                is Switchboard.AudioState.Recording.Starting ->
                    startRecording(dispatch)
                is Switchboard.AudioState.Recording.Stopping ->
                    stopRecording(audioState.uri, audioState.fileHandle, dispatch)
                else -> {}
            }
        }
    }

    private fun CoroutineScope.startRecording(
        dispatch: (Action) -> Unit
    ) = launch(Dispatchers.IO) {
        val newAction = when (val result = recorder.startRecording()) {
            is SimpleMediaRecorder.StartResult.Failure ->
                Callback.RecordingState.RecordingFailed
            is SimpleMediaRecorder.StartResult.Success ->
                Callback.RecordingState.RecordingStarted(result.uri, result.fileHandle)
        }
        dispatch(newAction)
    }

    private fun CoroutineScope.stopRecording(
        uri: Uri,
        fileHandle: Closeable?,
        dispatch: (Action) -> Unit
    ) = launch(Dispatchers.IO) {
        val result = recorder.stopRecording()
        fileHandle?.close()

        when (result) {
            is SimpleMediaRecorder.StopResult.Failure -> {
                Log.w("RecordingRouter", result.e)
                dispatch(Callback.RecordingState.RecordingFailed)
            }
            is SimpleMediaRecorder.StopResult.Success -> {
                dispatch(Callback.RecordingState.RecordingStopped(uri))

            }
        }
    }
}