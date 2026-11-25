package io.github.ooscam.capture

import android.app.Application
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ooscam.data.OOSCamDatabase
import io.github.ooscam.data.RecordingSessionEntity
import io.github.ooscam.data.SegmentEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CaptureDatabaseTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(app, OOSCamDatabase::class.java).allowMainThreadQueries().build()
    private val dao = db.captureDao()

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), OOSCamDatabase::class.java)

    @After
    fun tearDown() = db.close()

    private fun session(id: String = "s1") = RecordingSessionEntity(
        id = id, startedAt = 1_000L, lens = "back", width = 1920, height = 1080, fps = 30,
        audioOn = true, segmentMinutes = 30, target = "device", stopReason = null,
    )

    private fun segment(index: Int, sessionId: String = "s1") = SegmentEntity(
        sessionId = sessionId, index = index, uri = "content://v/$index", startOffsetMs = index * 10L, durationMs = 10L, bytes = 99L,
    )

    @Test
    fun schemaVersion1_isExportedToAppSchemas() {
        val dir = File("schemas/${OOSCamDatabase::class.java.name}")
        assertTrue("run from :app, schema at ${dir.absolutePath}", File(dir, "1.json").isFile)
    }

    @Test
    fun migrationHelper_createsVersion1FromTheExportedSchemaAndValidatesIt() {
        helper.createDatabase(DB_NAME, 1).close()
        helper.runMigrationsAndValidate(DB_NAME, 1, true).close()
    }

    @Test
    fun segments_areStoredPerSessionInIndexOrder() {
        dao.insertSession(session())
        dao.insertSegment(segment(2))
        dao.insertSegment(segment(1))
        assertEquals(listOf(1, 2), dao.segmentsOf("s1").map { it.index })
    }

    @Test
    fun finishSession_recordsTheStopReason() {
        dao.insertSession(session())
        assertNull(dao.session("s1")!!.stopReason)
        dao.finishSession("s1", "low_storage")
        assertEquals("low_storage", dao.session("s1")!!.stopReason)
    }

    @Test
    fun deletingSession_deletesItsSegmentRowsButNoVideoFile() {
        dao.insertSession(session())
        dao.insertSegment(segment(1))
        dao.deleteSession("s1")
        assertEquals(emptyList<SegmentEntity>(), dao.segmentsOf("s1"))
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
