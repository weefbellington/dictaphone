package com.davidstemmer.dictaphone.switchboard

import android.net.Uri
import com.davidstemmer.dictaphone.data.AudioMetadata
import com.davidstemmer.dictaphone.data.PermissionState

sealed interface SwitchboardOutput

data class FilesChanged(val recordings: List<AudioMetadata>): SwitchboardOutput

data class PermissionsUpdated(val permissionState: PermissionState): SwitchboardOutput

sealed interface RecordingStatusChanged: SwitchboardOutput {
    data object Started: SwitchboardOutput
    data class Stopped(val uri: Uri): SwitchboardOutput
    data object Failed: SwitchboardOutput
}
sealed interface PlaybackStatusChanged: SwitchboardOutput {
    data object Started: SwitchboardOutput
    data class Playing(val mediaId: String, val position: Long, val progress: Float):
        SwitchboardOutput
    data object Stopped: SwitchboardOutput
}