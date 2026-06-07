package com.shadowtalk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Helper object for checking and requesting runtime permissions.
 * ShadowTalk needs storage (or media audio) access and microphone access.
 */
object PermissionHelper {

    /** All permissions the app needs on first launch. */
    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses granular media permission for audio files
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.RECORD_AUDIO
            )
        } else {
            // Android 12 and below use legacy external storage read permission
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    /**
     * Returns true when every required permission has been granted.
     */
    fun hasAllPermissions(context: Context): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Returns true when the user can read audio files from storage.
     */
    fun hasStoragePermission(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns true when the user has granted microphone access.
     */
    fun hasRecordPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Launches the system permission dialog for all required permissions.
     * Call this once when the activity starts.
     */
    fun requestAllPermissions(launcher: ActivityResultLauncher<Array<String>>) {
        launcher.launch(requiredPermissions())
    }

    /**
     * Shows a rationale dialog when the user previously denied a permission.
     * Returns true if any permission should show rationale text.
     */
    fun shouldShowRationale(activity: FragmentActivity): Boolean {
        return requiredPermissions().any { permission ->
            activity.shouldShowRequestPermissionRationale(permission)
        }
    }
}
