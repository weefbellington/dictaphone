package com.davidstemmer.dictaphone.routing

import android.net.Uri
import android.util.Log
import com.davidstemmer.dictaphone.media.SimpleMediaRecorder
import com.davidstemmer.dictaphone.switchboard.EffectRouter
import com.davidstemmer.dictaphone.switchboard.MediaRecorder
import com.davidstemmer.dictaphone.switchboard.RecordingStatusChanged
import com.davidstemmer.dictaphone.switchboard.RouterScope
import com.davidstemmer.dictaphone.switchboard.SetAudioState
import com.davidstemmer.dictaphone.switchboard.Switchboard
import com.davidstemmer.dictaphone.switchboard.SwitchboardMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.Closeable

class MediaRecorderRouter(private val recorder: SimpleMediaRecorder): EffectRouter<MediaRecorder> {

    override fun canHandle(message: SwitchboardMessage) =
        message is MediaRecorder

    private var recordingUri: Uri? = null
    private var recordingFileHandle: Closeable? = null

    override fun RouterScope.handle(
        state: Switchboard.State,
        action: MediaRecorder
    ) {
        when (action) {
            is MediaRecorder.ToggleRecording -> toggleRecording(state)
            is MediaRecorder.StartRecording -> startRecording()
            is MediaRecorder.StopRecording -> stopRecording()
        }
    }

    private fun RouterScope.toggleRecording(state: Switchboard.State) {
        when (state.audioState) {
            Switchboard.AudioState.Idle -> {
                dispatch(MediaRecorder.StartRecording)
            }
            Switchboard.AudioState.Recording.Started -> {
                dispatch(MediaRecorder.StopRecording)
            }
            else -> {}
        }
    }

    private fun RouterScope.startRecording() = launch(Dispatchers.IO) {
        when (val result = recorder.startRecording()) {
            is SimpleMediaRecorder.StartResult.Failure -> {
                dispatch(SetAudioState.Idle)
                output(RecordingStatusChanged.Failed)
            }
            is SimpleMediaRecorder.StartResult.Success -> {
                recordingUri = result.uri
                recordingFileHandle = result.fileHandle
                dispatch(SetAudioState.RecordingStarted)
                output(RecordingStatusChanged.Started)
            }
        }
    }

    private fun RouterScope.stopRecording() = launch(Dispatchers.IO) {
        when (val result = recorder.stopRecording()) {
            is SimpleMediaRecorder.StopResult.Failure -> {
                Log.w("RecordingRouter", result.e)
                dispatch(SetAudioState.Idle)
                output(RecordingStatusChanged.Failed)
            }
            is SimpleMediaRecorder.StopResult.Success -> {
                val uri = recordingUri ?: Uri.EMPTY
                dispatch(SetAudioState.Idle)
                output(RecordingStatusChanged.Stopped(uri))
            }
        }
        recordingFileHandle?.close()
        recordingUri = null
    }
}