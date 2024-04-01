package com.davidstemmer.dictaphone.ui.data

import com.davidstemmer.dictaphone.data.AudioMetadata
import com.davidstemmer.dictaphone.data.Permissions

data class UiState(
    val showDialog: DialogType = DialogType.None,
    val audioMetadata: List<AudioMetadata> = listOf(),
    val audioState: AudioState = AudioState.Idle,
    val permissions: Permissions = Permissions(),
    val completions: Int = 0
)

