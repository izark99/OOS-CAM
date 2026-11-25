package io.github.ooscam.capture

import android.app.Application
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import io.github.ooscam.core.StorageTarget
import io.github.ooscam.core.segmentFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class CaptureStorageTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val tree = Uri.parse("content://com.android.externalstorage.documents/tree/1A2B-3C4D%3AOOS")

    @Test
    fun chooser_deviceRequested_isDevice() {
        assertEquals(StorageChoice.Device, CaptureStorageChooser.choose(StorageTarget.DEVICE, tree, listOf(PersistedGrant(tree, read = true, write = true))))
    }

    @Test
    fun chooser_sdWithPersistedWriteGrant_isThatFolder() {
        assertEquals(StorageChoice.Sd(tree), CaptureStorageChooser.choose(StorageTarget.SD, tree, listOf(PersistedGrant(tree, read = true, write = true))))
    }

    @Test
    fun chooser_sdWithoutPickedFolder_fallsBackToDevice() {
        assertEquals(StorageChoice.Device, CaptureStorageChooser.choose(StorageTarget.SD, null, emptyList()))
    }

    @Test
    fun chooser_sdWhosePermissionWasLostOrReadOnly_fallsBackToDevice() {
        assertEquals(StorageChoice.Device, CaptureStorageChooser.choose(StorageTarget.SD, tree, emptyList()))
        assertEquals(StorageChoice.Device, CaptureStorageChooser.choose(StorageTarget.SD, tree, listOf(PersistedGrant(tree, read = true, write = false))))
    }

    @Test
    fun mediaStoreEntry_isPendingVideoInMoviesOosCamWithCoreName() {
        val name = segmentFileName(LocalDateTime.of(2032, 1, 2, 3, 4, 5), 1)
        val v = CaptureMediaStore.pendingValues(name)
        assertEquals("OOSCam_20320102_030405_part01.mp4", v.getAsString(MediaStore.Video.Media.DISPLAY_NAME))
        assertEquals("Movies/OOS Cam", v.getAsString(MediaStore.Video.Media.RELATIVE_PATH))
        assertEquals("video/mp4", v.getAsString(MediaStore.Video.Media.MIME_TYPE))
        assertEquals(1, v.getAsInteger(MediaStore.Video.Media.IS_PENDING))
    }

    @Test
    fun mediaStoreCommit_clearsPendingEvenForDamagedFilesSoTheLibraryCanListThem() {
        assertEquals(0, CaptureMediaStore.publishValues().getAsInteger(MediaStore.Video.Media.IS_PENDING))
    }

    @Test
    fun sdFolder_isRememberedWithPersistablePermissionAndForgotten() {
        CaptureStoragePrefs.setSdTree(app, tree)
        assertEquals(tree, CaptureStoragePrefs.sdTree(app))
        CaptureStoragePrefs.setSdTree(app, null)
        assertNull(CaptureStoragePrefs.sdTree(app))
    }

    @Test
    fun sinkFor_matchesTheChoice() {
        assertTrue(CaptureStorage.sinkFor(app, StorageChoice.Device) is MediaStoreSegmentSink)
        assertTrue(CaptureStorage.sinkFor(app, StorageChoice.Sd(tree)) is SafSegmentSink)
    }
}
