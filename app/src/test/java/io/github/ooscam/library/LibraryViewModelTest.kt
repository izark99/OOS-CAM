package io.github.ooscam.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.ooscam.data.OOSCamDatabase
import io.github.ooscam.data.RecordingSessionEntity
import io.github.ooscam.data.SegmentEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryViewModelTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(app, OOSCamDatabase::class.java).allowMainThreadQueries().build()
    private val dao = db.captureDao()
    private val media = FakeMediaSource()
    private val vm by lazy { LibraryViewModel(LibraryRepository(dao, media), io = Runnable::run, main = Runnable::run) }

    @After
    fun tearDown() = db.close()

    /** Session "a" (2 parts, older) and session "b" (1 part, newer). */
    private fun twoSessions() {
        dao.insertSession(RecordingSessionEntity("a", 1_000, "back", 1920, 1080, 30, true, 30, "device", "user"))
        dao.insertSession(RecordingSessionEntity("b", 2_000, "back", 1920, 1080, 30, true, 30, "device", "user"))
        dao.insertSegment(SegmentEntity(sessionId = "a", index = 1, uri = "content://m/a1", startOffsetMs = 0, durationMs = 1, bytes = 1))
        dao.insertSegment(SegmentEntity(sessionId = "a", index = 2, uri = "content://m/a2", startOffsetMs = 0, durationMs = 1, bytes = 1))
        dao.insertSegment(SegmentEntity(sessionId = "b", index = 1, uri = "content://m/b1", startOffsetMs = 0, durationMs = 1, bytes = 1))
        media.add("content://m/a1", durationMs = 3_000)
        media.add("content://m/a2", durationMs = 2_000)
        media.add("content://m/b1", durationMs = 5_000)
        vm.refresh()
    }

    @Test
    fun refresh_showsOneTilePerSession_withPartsAndTotalDuration() {
        twoSessions()
        val tiles = vm.state.value.tiles
        assertEquals(listOf("b", "a"), tiles.map { it.key })
        assertEquals(2, tiles[1].parts)
        assertEquals(5_000, tiles[1].durationMs)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun emptyLibrary_isReportedAsEmpty() {
        vm.refresh()
        assertTrue(vm.state.value.isEmpty)
    }

    @Test
    fun longPress_entersSelection_andTapsToggleInTapOrder() {
        twoSessions()
        assertFalse(vm.state.value.selecting)
        vm.toggle("a")
        vm.toggle("b")
        assertTrue(vm.state.value.selecting)
        assertEquals(listOf("a", "b"), vm.state.value.selected)
        vm.toggle("a")
        assertEquals(listOf("b"), vm.state.value.selected)
        vm.toggle("b")
        assertFalse(vm.state.value.selecting)
    }

    @Test
    fun editSelected_givesNonDamagedPartsInSelectionOrderThenPartOrder() {
        twoSessions()
        media.damaged += "content://m/a2"
        vm.refresh()
        vm.toggle("b")
        vm.toggle("a")
        assertEquals(listOf("content://m/b1", "content://m/a1"), vm.editSelection().map { it.uri })
        assertTrue(vm.state.value.canEdit)
    }

    @Test
    fun editSelected_isDisabledWhenOnlyDamagedFilesAreSelected() {
        twoSessions()
        media.damaged += "content://m/b1"
        vm.refresh()
        vm.toggle("b")
        assertFalse(vm.state.value.canEdit)
        assertTrue(vm.editSelection().isEmpty())
    }

    @Test
    fun delete_asksForConfirmFirst_cancelKeepsEverything() {
        twoSessions()
        vm.toggle("a")
        vm.requestDeleteSelected()
        assertEquals(listOf("content://m/a1", "content://m/a2"), vm.state.value.confirmDelete?.uris)
        vm.cancelDelete()
        assertNull(vm.state.value.confirmDelete)
        assertTrue(media.deleted.isEmpty())
        assertEquals(2, vm.state.value.tiles.size)
    }

    @Test
    fun confirmDelete_deletesAllPartsOfSelectedSessions_reloadsAndLeavesSelection() {
        twoSessions()
        vm.toggle("a")
        vm.requestDeleteSelected()
        vm.confirmDelete()
        assertEquals(listOf("content://m/a1", "content://m/a2"), media.deleted)
        assertEquals(listOf("b"), vm.state.value.tiles.map { it.key })
        assertFalse(vm.state.value.selecting)
        assertNull(vm.state.value.confirmDelete)
    }

    @Test
    fun deleteDamaged_isOfferedOnlyWithDamagedFiles_andRemovesOnlyThose() {
        twoSessions()
        assertFalse(vm.state.value.hasDamaged)
        media.damaged += "content://m/a2"
        vm.refresh()
        assertTrue(vm.state.value.hasDamaged)
        assertTrue(vm.state.value.tiles.single { it.key == "a" }.damaged)
        vm.requestDeleteDamaged()
        assertEquals(listOf("content://m/a2"), vm.state.value.confirmDelete?.uris)
        vm.confirmDelete()
        assertEquals(listOf("content://m/a2"), media.deleted)
        assertFalse(vm.state.value.hasDamaged)
    }

    @Test
    fun clearSelection_leavesSelectionMode() {
        twoSessions()
        vm.toggle("a")
        vm.clearSelection()
        assertFalse(vm.state.value.selecting)
    }

    @Test
    fun playIntent_opensTheSystemPlayerWithReadGrant() {
        val intent = LibraryIntents.play("content://m/a1")
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse("content://m/a1"), intent.data)
        assertEquals("video/mp4", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun shareIntent_usesSendForOneFile_andSendMultipleForMany() {
        val one = LibraryIntents.share(listOf("content://m/a1"))
        assertEquals(Intent.ACTION_SEND, one.action)
        assertEquals(Uri.parse("content://m/a1"), one.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertTrue(one.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)

        val many = LibraryIntents.share(listOf("content://m/a1", "content://m/b1"))
        assertEquals(Intent.ACTION_SEND_MULTIPLE, many.action)
        assertEquals(2, many.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.size)
    }

    @Test
    fun shareSelected_givesEveryPartOfTheSelectedTiles() {
        twoSessions()
        vm.toggle("a")
        assertEquals(listOf("content://m/a1", "content://m/a2"), vm.selectedUris())
    }
}

@RunWith(RobolectricTestRunner::class)
class LibraryViewModelRefreshTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(app, OOSCamDatabase::class.java).allowMainThreadQueries().build()
    private val media = FakeMediaSource()
    private val vm = LibraryViewModel(LibraryRepository(db.captureDao(), media), io = Runnable::run, main = Runnable::run)

    @After
    fun tearDown() = db.close()

    @Test
    fun refresh_dropsSelectionOfFilesDeletedElsewhere_andKeepsTheRest() {
        media.add("content://m/x", addedAt = 1)
        media.add("content://m/y", addedAt = 2)
        vm.refresh()
        vm.toggle("content://m/x")
        vm.toggle("content://m/y")
        media.files.remove("content://m/x")
        vm.refresh()
        assertEquals(listOf("content://m/y"), vm.state.value.selected)
    }

    @Test
    fun singleFileTile_usesItsUriAsKey_andIsPlayable() {
        media.add("content://m/edit", name = "OOSCam_edit_20261125_101010.mp4", durationMs = 7_000)
        vm.refresh()
        val tile = vm.state.value.tiles.single()
        assertEquals("content://m/edit", tile.key)
        assertEquals(7_000, tile.durationMs)
        assertEquals("content://m/edit", LibraryIntents.play(tile.items.first().uri).data.toString())
    }
}
