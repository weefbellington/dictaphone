package com.davidstemmer.dictaphone.routing

import com.davidstemmer.dictaphone.media.SimpleMediaPlayer
import com.davidstemmer.dictaphone.switchboard.EffectRouter
import com.davidstemmer.dictaphone.switchboard.MediaPlayer
import com.davidstemmer.dictaphone.switchboard.PlaybackStatusChanged
import com.davidstemmer.dictaphone.switchboard.RouterScope
import com.davidstemmer.dictaphone.switchboard.SetAudioState
import com.davidstemmer.dictaphone.switchboard.Switchboard
import com.davidstemmer.dictaphone.switchboard.SwitchboardMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MediaPlayerRouter(private val mediaPlayer: SimpleMediaPlayer) : EffectRouter<MediaPlayer> {
    override fun canHandle(message: SwitchboardMessage) =
        message is MediaPlayer

    private var progressMonitor: Job? = null

    private fun stopMonitoringProgress() {
        progressMonitor?.cancel()
        progressMonitor = null
    }

    private fun RouterScope.startMonitoringProgress() {
        stopMonitoringProgress()
        progressMonitor = launch {
            while(isActive) {
                delay(100)
                if (mediaPlayer.nextMediaItemIndex == -1 && mediaPlayer.currentProgress == 1.0f) {
                    dispatch(MediaPlayer.StopProgressUpdates)
                    dispatch(SetAudioState.Idle)
                    output(PlaybackStatusChanged.Stopped)
                }
                else {
                    output(
                        PlaybackStatusChanged.Playing(
                        mediaId = mediaPlayer.currentMediaId,
                        position = mediaPlayer.currentPosition,
                        progress = mediaPlayer.currentProgress                    )
                    )
                }
            }
        }
    }

    override fun RouterScope.handle(state: Switchboard.State, action: MediaPlayer) {
        when(action) {
            is MediaPlayer.Start -> launch(Dispatchers.Main) {
                val itemsToPlay = state.recordings.subList(
                    fromIndex = action.index,
                    toIndex = state.recordings.size
                )
                mediaPlayer.play(itemsToPlay)
                dispatch(SetAudioState.PlaybackStarted)
                dispatch(MediaPlayer.StartProgressUpdates)
                output(PlaybackStatusChanged.Started)
            }
            is MediaPlayer.Stop  -> launch(Dispatchers.Main) {
                mediaPlayer.stop()
                dispatch(SetAudioState.Idle)
                dispatch(MediaPlayer.StopProgressUpdates)
                output(PlaybackStatusChanged.Stopped)
            }
            is MediaPlayer.StartProgressUpdates -> launch (Dispatchers.Main) {
                startMonitoringProgress()
            }
            is MediaPlayer.StopProgressUpdates -> launch (Dispatchers.Main) {
                stopMonitoringProgress()
            }
        }
    }

}