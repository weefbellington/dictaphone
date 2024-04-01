package com.davidstemmer.dictaphone.routing

import com.davidstemmer.dictaphone.media.SimpleMediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.davidstemmer.dictaphone.routing.Switchboard.Action
import com.davidstemmer.dictaphone.routing.Switchboard.Callback

class PlaybackRouter(private val mediaPlayer: SimpleMediaPlayer): EffectRouter {

    private var progressMonitor: Job? = null

    override fun CoroutineScope.handle(state: Switchboard.State, action: Action, dispatch: (Action) -> Unit) {
        when(action) {
            is Action.StartPlayback -> launch(Dispatchers.Main) {
                startMonitoringProgress(dispatch)
                val itemsToPlay = state.recordings.subList(
                    fromIndex = action.index,
                    toIndex = state.recordings.size
                )
                mediaPlayer.play(itemsToPlay)
                dispatch(Callback.MediaPlayer.PlaybackStarted)
            }
            is Callback.MediaPlayer.PlaylistFinished -> launch(Dispatchers.Main) {
                stopMonitoringProgress()
                dispatch(Callback.MediaPlayer.PlaybackEnded)
            }
            is Action.StopPlayback -> launch(Dispatchers.Main) {
                mediaPlayer.stop()
                stopMonitoringProgress()
                dispatch(Callback.MediaPlayer.PlaybackEnded)
            }
            else -> {}
        }
    }

    private fun stopMonitoringProgress() {
        progressMonitor?.cancel()
        progressMonitor = null
    }

    private fun CoroutineScope.startMonitoringProgress(dispatch: (Action) -> Unit) {
        stopMonitoringProgress()
        progressMonitor = launch {
            while(isActive) {
                delay(100)
                dispatch(Callback.MediaPlayer.PlaybackUpdate(
                    mediaId = mediaPlayer.currentMediaId,
                    position = mediaPlayer.currentPosition,
                    progress = mediaPlayer.currentProgress)
                )
            }
        }
    }
}