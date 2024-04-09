package com.davidstemmer.dictaphone.routing

import androidx.activity.ComponentActivity
import androidx.annotation.CheckResult
import com.davidstemmer.dictaphone.permissions.Permission
import com.davidstemmer.dictaphone.permissions.PermissionRequest
import com.davidstemmer.dictaphone.permissions.hasPermission
import com.davidstemmer.dictaphone.switchboard.EffectRouter
import com.davidstemmer.dictaphone.switchboard.Permissions
import com.davidstemmer.dictaphone.switchboard.PermissionsUpdated
import com.davidstemmer.dictaphone.switchboard.RouterScope
import com.davidstemmer.dictaphone.switchboard.Switchboard
import com.davidstemmer.dictaphone.switchboard.SwitchboardMessage
import com.davidstemmer.dictaphone.switchboard.SwitchboardOutput

class PermissionsRouter(private val activity: ComponentActivity): EffectRouter<Permissions> {

    private lateinit var recordAudioRequest: PermissionRequest
    override fun canHandle(message: SwitchboardMessage) =
        message is Permissions

    override fun RouterScope.handle(
        state: Switchboard.State,
        action: Permissions
    ) {
        when (action) {
            is Permissions.Initialize -> {
                initializePermissions()
                recordAudioRequest = createRecordAudioPermissionRequest { granted ->
                    dispatch(
                        Permissions.Update(
                        Permission.RECORD_AUDIO,
                        granted)
                    )
                }
            }
            is Permissions.Request -> when (action.permission) {
                Permission.RECORD_AUDIO -> recordAudioRequest.launch()
            }
            is Permissions.Update -> {
                output(PermissionsUpdated(state.permissionState))
            }
        }
    }

    private fun RouterScope.initializePermissions() {
        if (activity.hasPermission(Permission.RECORD_AUDIO)) {
            val action = Permissions.Update(
                permission = Permission.RECORD_AUDIO,
                allow = true
            )
            dispatch(action)
        }
    }

    @CheckResult
    private fun createRecordAudioPermissionRequest(callback: (Boolean) -> Unit): PermissionRequest {
        return PermissionRequest(activity, Permission.RECORD_AUDIO, callback)
    }

}