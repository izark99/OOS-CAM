package io.github.ooscam.capture

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CapturePermissionPolicyTest {
    private val none = PermissionSnapshot(camera = false, mic = false, notifications = false)
    private val all = PermissionSnapshot(camera = true, mic = true, notifications = true)

    @Test
    fun firstTap_onAndroid13Plus_asksCameraMicAndNotifications() {
        assertEquals(
            listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS),
            CapturePermissionPolicy.toRequest(none, micWanted = true, sdkInt = 33),
        )
    }

    @Test
    fun firstTap_onAndroid12_neverAsksNotifications() {
        assertEquals(
            listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            CapturePermissionPolicy.toRequest(none, micWanted = true, sdkInt = 31),
        )
    }

    @Test
    fun micOff_doesNotAskForTheMic_andGrantedOnesAreNotAskedAgain() {
        assertEquals(
            listOf(Manifest.permission.POST_NOTIFICATIONS),
            CapturePermissionPolicy.toRequest(none.copy(camera = true), micWanted = false, sdkInt = 34),
        )
        assertEquals(emptyList<String>(), CapturePermissionPolicy.toRequest(all, micWanted = true, sdkInt = 34))
    }

    @Test
    fun cameraDenied_blocksRecordAndShowsTheSheet() {
        assertEquals(RecordDecision.Blocked, CapturePermissionPolicy.decide(all.copy(camera = false), micWanted = true, micToastShown = false, sdkInt = 34))
    }

    @Test
    fun micDenied_recordsWithMicOffAndShowsTheToastOnce() {
        val denied = all.copy(mic = false)
        assertEquals(
            RecordDecision.Start(micOn = false, micOffToast = true, notificationBanner = false),
            CapturePermissionPolicy.decide(denied, micWanted = true, micToastShown = false, sdkInt = 34),
        )
        assertEquals(
            RecordDecision.Start(micOn = false, micOffToast = false, notificationBanner = false),
            CapturePermissionPolicy.decide(denied, micWanted = true, micToastShown = true, sdkInt = 34),
        )
    }

    @Test
    fun notificationsDenied_stillRecordsWithTheBanner() {
        assertEquals(
            RecordDecision.Start(micOn = true, micOffToast = false, notificationBanner = true),
            CapturePermissionPolicy.decide(all.copy(notifications = false), micWanted = true, micToastShown = false, sdkInt = 33),
        )
    }

    @Test
    fun micToggledOff_recordsSilentlyWithoutToast() {
        assertEquals(
            RecordDecision.Start(micOn = false, micOffToast = false, notificationBanner = false),
            CapturePermissionPolicy.decide(all.copy(mic = false), micWanted = false, micToastShown = false, sdkInt = 34),
        )
    }

    @Test
    fun snapshot_readsGrantedPermissionsFromTheContext() {
        val app: Application = ApplicationProvider.getApplicationContext()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        assertEquals(PermissionSnapshot(camera = true, mic = false, notifications = false), PermissionSnapshot.read(app))
    }
}
