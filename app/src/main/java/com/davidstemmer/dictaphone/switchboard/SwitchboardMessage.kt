package com.davidstemmer.dictaphone.switchboard

import android.net.Uri
import com.davidstemmer.dictaphone.data.AudioMetadata
import com.davidstemmer.dictaphone.permissions.Permission

sealed interface SwitchboardMessage {
    data object Initialization: SwitchboardMessage
}

sealed interface SetAudioState: SwitchboardMessage {
    data object PlaybackStarted: SetAudioState
    data object RecordingStarted: SetAudioState
    data object Idle: SetAudioState
}

sealed interface MediaRepository: SwitchboardMessage {
    data object ScanForMediaFiles: MediaRepository
    data class FileScanComplete(val files: List<AudioMetadata>): MediaRepository
    data class UpdateFilename(val uri: Uri, val name: String): MediaRepository
}

sealed interface Permissions: SwitchboardMessage {
    data object Initialize: Permissions
    data class Request(val permission: Permission): Permissions
    data class Update(val permission: Permission, val allow: Boolean): Permissions
}

sealed interface MediaPlayer: SwitchboardMessage {
    data class Start(val index: Int, val mediaId: String): MediaPlayer
    data object StartProgressUpdates: MediaPlayer
    data object StopProgressUpdates: MediaPlayer
    data object Stop: MediaPlayer
}

sealed interface MediaRecorder: SwitchboardMessage {
    data object ToggleRecording: MediaRecorder
    data object StartRecording: MediaRecorder
    data object StopRecording: MediaRecorder
}

data class SendOutput(val outputFn: (Switchboard.State) -> SwitchboardOutput): SwitchboardMessage {
    constructor(output: SwitchboardOutput): this({ _ -> output })
}