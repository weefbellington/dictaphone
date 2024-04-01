package com.davidstemmer.dictaphone.permissions

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

enum class Permission(val permissionName: String) {
    RECORD_AUDIO(Manifest.permission.RECORD_AUDIO)
}

fun ComponentActivity.hasPermission(permission: Permission) =
    ContextCompat.checkSelfPermission(
        this,
        permission.permissionName) == PackageManager.PERMISSION_GRANTED

private fun ComponentActivity.shouldShowPermissionRationale(permission: Permission): Boolean =
    ActivityCompat.shouldShowRequestPermissionRationale(this, permission.permissionName)


/**
 * Helper object for creating permission requests. Automatically calls registerForActivityResult
 * and triggers the callback when the permission request completes.
 *
 * You MUST create this object in Activity#onCreate, or you will get an exception in
 * registerForActivityResult.
 *
 * @property activity
 * @property permission
 * @property callback
 */
class PermissionRequest(
    private val activity: ComponentActivity,
    private val permission: Permission,
    private val callback: (Boolean) -> Unit
) {
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        callback)

    fun launch() {
        when {
            activity.hasPermission(permission) -> callback(true)
            activity.shouldShowPermissionRationale(permission) ->
                launcher.launch(permission.permissionName)

            else -> launcher.launch(permission.permissionName)
        }
    }
}