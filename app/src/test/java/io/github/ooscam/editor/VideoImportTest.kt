package io.github.ooscam.editor

import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.core.app.ApplicationProvider
import io.github.ooscam.library.MediaFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoImportTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun file(uri: String, durationMs: Long, height: Int = 1080) = MediaFile(uri, uri.substringAfterLast('/'), durationMs, height, 1, 0, false)

    @Test
    fun pickedVideos_becomeClipSources_inPickOrder() {
        val files = mapOf("content://p/2" to file("content://p/2", 4_000, 720), "content://p/1" to file("content://p/1", 9_000))
        val result = VideoImport.toClips(listOf("content://p/2", "content://p/1"), files::get)
        assertEquals(listOf(ClipSource("content://p/2", "2", 4_000, 720), ClipSource("content://p/1", "1", 9_000, 1080)), result.clips)
        assertEquals(0, result.skipped)
    }

    @Test
    fun unreadableOrZeroLengthVideos_areSkippedAndCounted() {
        val files = mapOf("content://p/ok" to file("content://p/ok", 1_000), "content://p/zero" to file("content://p/zero", 0))
        val result = VideoImport.toClips(listOf("content://p/gone", "content://p/zero", "content://p/ok"), files::get)
        assertEquals(listOf("content://p/ok"), result.clips.map { it.uri })
        assertEquals(2, result.skipped)
    }

    @Test
    fun pickerRequest_isVideoOnly_andAllowsUpToTheClipLimit() {
        assertEquals(ActivityResultContracts.PickVisualMedia.VideoOnly, VideoImport.request().mediaType)
        assertEquals(10, VideoImport.MAX_ITEMS)
        val intent = VideoImport.contract().createIntent(app, VideoImport.request())
        assertTrue(intent.action != null)
    }

    @Test
    fun manifest_declaresNoStoragePermission_becauseThePickerNeedsNone() {
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().toList()
        assertFalse(requested.toString(), "android.permission.READ_MEDIA_VIDEO" in requested)
        assertFalse(requested.toString(), "android.permission.READ_EXTERNAL_STORAGE" in requested)
        assertTrue("android.permission.CAMERA" in requested)
    }
}
