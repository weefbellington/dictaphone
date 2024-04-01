package com.davidstemmer.dictaphone.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.davidstemmer.dictaphone.data.AudioMetadata

typealias EventListener = (SimpleMediaPlayer.Event) -> Unit

class SimpleMediaPlayer(context: Context): Player.Listener {

    private var isReloadingPlaylist = false

    sealed interface Event {
        /** Triggered when the playlist has run out of items. */
        data object PlaylistFinished: Event
    }

    private val player by lazy {
        val player = ExoPlayer.Builder(context).build()
        player.addListener(this)
        player
    }

    private val listeners = mutableListOf<EventListener>()

    val currentMediaId: String
        get() = player.currentMediaItem?.mediaId ?: ""

    val currentProgress: Float
        get() = if (player.duration > 0) {
            player.currentPosition.toFloat() / player.duration.toFloat()
        } else 0.0f

    /**
     * The playback position of the internal player.
     * @see Player.getCurrentPosition
     */
    val currentPosition: Long
        get() = player.currentPosition

    /**
     * Add each media item to the playlist and start playing. Current items in the playlist will
     * be discarded.
     *
     * @param dataList the items to play
     */
    fun play(dataList: List<AudioMetadata>) {
        isReloadingPlaylist = true
        player.clearMediaItems()
        dataList.forEach { item ->
            val mediaItem = MediaItem.Builder()
                .setMimeType("audio/3gpp")
                .setUri(item.uri)
                .setMediaId(item.mediaId)
                .build()
            player.addMediaItem(mediaItem)
        }
        player.prepare()
        player.play()
    }

    fun stop() {
        player.stop()
    }

    fun release() {
        stop()
        listeners.clear()
        player.removeListener(this)
        player.release()
    }

    fun addListener(listener: EventListener) {
        listeners.add(listener)
    }

    private fun notify(event: Event) {
        listeners.forEach { listener ->
            listener(event)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        // STATE_ENDED will trigger every time clearMediaItems() is called, but we only want to
        // notify listeners when the playlist reaches the end naturally. Set a flag before calling
        // clearMediaItems() and clear it in STATE_READY, after it begins to play.
        when (playbackState) {
            Player.STATE_ENDED -> {
                if (!isReloadingPlaylist) {
                    notify(Event.PlaylistFinished) }
                }
            Player.STATE_READY -> isReloadingPlaylist = false
            else -> {}
        }
    }
}