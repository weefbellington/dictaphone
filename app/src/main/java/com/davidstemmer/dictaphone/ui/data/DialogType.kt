package com.davidstemmer.dictaphone.ui.data

import android.net.Uri

sealed interface DialogType {
    data object None: DialogType
    data class EditFilename(val recordingUri: Uri): DialogType
}