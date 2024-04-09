package com.davidstemmer.dictaphone.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.davidstemmer.dictaphone.data.AudioMetadata
import kotlin.math.min

class SimpleMediaPlayer(context: Context): Player.Listener {

    private val player by lazy {
        val player = ExoPlayer.Builder(context).build()
        player.addListener(this)
        player
    }

    val nextMediaItemIndex: Int
        get() = player.nextMediaItemIndex

    val currentMediaId: String
        get() = player.currentMediaItem?.mediaId ?: ""

    val currentProgress: Float
        get() = if (player.duration > 0) {
            min(player.currentPosition.toFloat() / player.duration.toFloat(), 1.0f)
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
        player.removeListener(this)
        player.release()
    }
}