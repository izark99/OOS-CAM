package io.github.ooscam.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class PermissionSnapshot(val camera: Boolean, val mic: Boolean, val notifications: Boolean) {
    companion object {
        fun read(context: Context): PermissionSnapshot {
            fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
            return PermissionSnapshot(
                camera = granted(Manifest.permission.CAMERA),
                mic = granted(Manifest.permission.RECORD_AUDIO),
                notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    granted(Manifest.permission.POST_NOTIFICATIONS),
            )
        }
    }
}

sealed interface RecordDecision {
    /** Camera denied: show the permission sheet, Record stays disabled. */
    data object Blocked : RecordDecision

    data class Start(val micOn: Boolean, val micOffToast: Boolean, val notificationBanner: Boolean) : RecordDecision
}

/** The permissions matrix of docs/SPEC.md, as pure rules. */
object CapturePermissionPolicy {
    /** What the first tap on Record asks for (only what is missing and needed). */
    fun toRequest(now: PermissionSnapshot, micWanted: Boolean, sdkInt: Int): List<String> = buildList {
        if (!now.camera) add(Manifest.permission.CAMERA)
        if (micWanted && !now.mic) add(Manifest.permission.RECORD_AUDIO)
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU && !now.notifications) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** What happens after the request answered. */
    fun decide(now: PermissionSnapshot, micWanted: Boolean, micToastShown: Boolean, sdkInt: Int): RecordDecision {
        if (!now.camera) return RecordDecision.Blocked
        val micOn = micWanted && now.mic
        return RecordDecision.Start(
            micOn = micOn,
            micOffToast = micWanted && !now.mic && !micToastShown,
            notificationBanner = sdkInt >= Build.VERSION_CODES.TIRAMISU && !now.notifications,
        )
    }
}
