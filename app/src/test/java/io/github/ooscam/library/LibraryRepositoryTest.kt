package io.github.ooscam.library

import android.app.Application
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

/** In-memory stand-in for MediaStore (Robolectric has no real media provider). */
class FakeMediaSource : MediaSource {
    val files = linkedMapOf<String, MediaFile>()
    val damaged = mutableSetOf<String>()
    val deleted = mutableListOf<String>()

    fun add(uri: String, name: String = uri.substringAfterLast('/'), durationMs: Long = 1_000, height: Int = 1080, addedAt: Long = 0, inFolder: Boolean = true) {
        files[uri] = MediaFile(uri, name, durationMs, height, 10, addedAt, inFolder)
    }

    override fun listFolder(): List<MediaFile> = files.values.filter { it.inOosCamFolder }

    override fun describe(uri: String): MediaFile? = files[uri]

    override fun isFinalized(uri: String): Boolean = uri !in damaged

    override fun delete(uri: String): Boolean {
        deleted += uri
        return files.remove(uri) != null
    }
}

@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(app, OOSCamDatabase::class.java).allowMainThreadQueries().build()
    private val dao = db.captureDao()
    private val media = FakeMediaSource()
    private val repo = LibraryRepository(dao, media)

    @After
    fun tearDown() = db.close()

    private fun session(id: String, startedAt: Long) = dao.insertSession(
        RecordingSessionEntity(id, startedAt, "back", 1920, 1080, 30, true, 30, "device", "user"),
    )

    private fun segment(sessionId: String, index: Int, uri: String, durationMs: Long = 500) =
        dao.insertSegment(SegmentEntity(sessionId = sessionId, index = index, uri = uri, startOffsetMs = 0, durationMs = durationMs, bytes = 1))

    @Test
    fun segmentsOfOneSession_areGroupedInIndexOrder_newestSessionFirst() {
        session("old", 1_000)
        session("new", 5_000)
        segment("old", 1, "content://m/1")
        segment("new", 2, "content://m/3")
        segment("new", 1, "content://m/2")
        listOf("content://m/1", "content://m/2", "content://m/3").forEach { media.add(it) }

        val groups = repo.load()

        assertEquals(listOf("new", "old"), groups.map { it.sessionId })
        assertEquals(listOf("content://m/2", "content://m/3"), groups[0].items.map { it.uri })
        assertEquals(5_000, groups[0].startedAt)
    }

    @Test
    fun unfinalizedSegment_isMarkedDamaged() {
        session("s", 1)
        segment("s", 1, "content://m/1")
        segment("s", 2, "content://m/2")
        media.add("content://m/1")
        media.add("content://m/2")
        media.damaged += "content://m/2"

        val items = repo.load().single().items

        assertFalse(items[0].damaged)
        assertTrue(items[1].damaged)
    }

    @Test
    fun segmentWhoseFileWasDeletedElsewhere_isLeftOut_andEmptySessionHidden() {
        session("s", 1)
        segment("s", 1, "content://m/gone")
        assertTrue(repo.load().isEmpty())
    }

    @Test
    fun fileInOosCamFolderNotInRoom_isItsOwnGroup_byAddedTime() {
        session("s", 2_000)
        segment("s", 1, "content://m/1")
        media.add("content://m/1")
        media.add("content://m/edit", name = "OOSCam_edit_20261125_101010.mp4", addedAt = 9_000)

        val groups = repo.load()

        assertEquals(2, groups.size)
        assertNull(groups[0].sessionId)
        assertEquals("content://m/edit", groups[0].items.single().uri)
        assertEquals("s", groups[1].sessionId)
    }

    @Test
    fun sdSegmentOutsideMediaStoreFolder_isFoundByUri() {
        session("s", 1)
        segment("s", 1, "content://saf/doc1")
        media.add("content://saf/doc1", inFolder = false)
        assertEquals("content://saf/doc1", repo.load().single().items.single().uri)
    }

    @Test
    fun durationFallsBackToRoomWhenMediaHasNone_andHeightComesFromMedia() {
        session("s", 1)
        segment("s", 1, "content://m/1", durationMs = 4_321)
        media.add("content://m/1", durationMs = 0, height = 720)
        val item = repo.load().single().items.single()
        assertEquals(4_321, item.durationMs)
        assertEquals(720, item.height)
    }

    @Test
    fun delete_removesFilesAndRows_andSessionWithoutSegments() {
        session("s", 1)
        segment("s", 1, "content://m/1")
        segment("s", 2, "content://m/2")
        media.add("content://m/1")
        media.add("content://m/2")

        repo.delete(listOf("content://m/1"))
        assertEquals(listOf("content://m/2"), repo.load().single().items.map { it.uri })
        assertEquals(listOf(2), dao.segmentsOf("s").map { it.index })

        repo.delete(listOf("content://m/2"))
        assertNull(dao.session("s"))
        assertTrue(repo.load().isEmpty())
    }

    @Test
    fun deleteDamaged_removesOnlyDamagedFiles() {
        session("s", 1)
        segment("s", 1, "content://m/1")
        segment("s", 2, "content://m/2")
        media.add("content://m/1")
        media.add("content://m/2")
        media.add("content://m/x")
        media.damaged += setOf("content://m/2", "content://m/x")

        val removed = repo.deleteDamaged()

        assertEquals(setOf("content://m/2", "content://m/x"), removed.toSet())
        assertEquals(listOf("content://m/1"), repo.load().flatMap { g -> g.items.map { it.uri } })
    }
}
