package com.davidstemmer.dictaphone.ui.data

import com.davidstemmer.dictaphone.data.AudioMetadata
import com.davidstemmer.dictaphone.data.PermissionState

data class UiState(
    val showDialog: DialogType = DialogType.None,
    val audioMetadata: List<AudioMetadata> = listOf(),
    val audioState: AudioState = AudioState.Idle,
    val permissionState: PermissionState = PermissionState(),
    val completions: Int = 0
)

