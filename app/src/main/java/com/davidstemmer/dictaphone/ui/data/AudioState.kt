package com.davidstemmer.dictaphone.ui.data

sealed interface AudioState {
    data object Idle: AudioState
    data object Recording: AudioState
    data class Playback(val id: String, val position: Long, val progress: Float) : AudioState
}