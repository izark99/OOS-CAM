package io.github.ooscam.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle

/**
 * Starts recording only from a visible activity: Android 12+ forbids starting a foreground
 * service from the background, and recording must begin with a user tap.
 */
object CaptureLauncher {
    fun start(activity: ComponentActivity, config: CaptureConfig): Boolean {
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return false
        val cameraGranted = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!cameraGranted) return false
        ContextCompat.startForegroundService(activity, CaptureService.startIntent(activity, config))
        return true
    }

    fun stop(activity: ComponentActivity) {
        activity.startService(
            android.content.Intent(activity, CaptureService::class.java).setAction(CaptureService.ACTION_STOP),
        )
    }
}
