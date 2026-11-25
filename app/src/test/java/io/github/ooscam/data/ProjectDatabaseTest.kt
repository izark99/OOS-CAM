package io.github.ooscam.data

import android.app.Application
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ooscam.core.EditProject
import io.github.ooscam.core.ProjectClip
import io.github.ooscam.core.SpeedRange
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class ProjectDatabaseTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(app, OOSCamDatabase::class.java).allowMainThreadQueries().build()
    private val dao = db.projectDao()

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), OOSCamDatabase::class.java)

    @After
    fun tearDown() = db.close()

    private val project = EditProject(
        id = "p1", muted = true, exportHeight = 1080,
        clips = listOf(ProjectClip(1, "content://m/b", 62_500, 720, 1_500, 61_500), ProjectClip(0, "content://m/a", 95_000, 1080, 2_000, 92_000)),
        speedRanges = listOf(SpeedRange(0, 30_000, 60_000, BigDecimal("1.5")), SpeedRange(1, 1_000, 2_000, BigDecimal("0.25"))),
    )

    @Test
    fun schemaVersion2_isExportedToAppSchemas() {
        val dir = File("schemas/${OOSCamDatabase::class.java.name}")
        assertTrue(File(dir, "2.json").isFile)
        assertTrue("version 1 stays for the migration test", File(dir, "1.json").isFile)
    }

    @Test
    fun migration1To2_keepsSessionsAndSegments_andAddsProjectTables() {
        helper.createDatabase(DB_NAME, 1).use { v1 ->
            v1.execSQL(
                "INSERT INTO recording_session (id, startedAt, lens, width, height, fps, audioOn, segmentMinutes, target, stopReason) " +
                    "VALUES ('s1', 1000, 'back', 1920, 1080, 30, 1, 30, 'device', 'user')",
            )
            v1.execSQL(
                "INSERT INTO segment (id, sessionId, segment_index, uri, startOffsetMs, durationMs, bytes) " +
                    "VALUES (1, 's1', 1, 'content://m/1', 0, 95000, 123)",
            )
        }
        helper.runMigrationsAndValidate(DB_NAME, 2, true, *OOSCamDatabase.MIGRATIONS).use { v2 ->
            v2.query("SELECT stopReason FROM recording_session WHERE id = 's1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("user", c.getString(0))
            }
            v2.query("SELECT uri, durationMs FROM segment WHERE sessionId = 's1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("content://m/1", c.getString(0))
                assertEquals(95_000L, c.getLong(1))
            }
            v2.execSQL("INSERT INTO edit_project (id, name, createdAt, updatedAt, muted, exportHeight) VALUES ('p', 'n', 1, 1, 0, 720)")
            v2.execSQL("INSERT INTO project_clip (projectId, clip_order, sourceUri, sourceDurationMs, sourceHeight, inMs, outMs) VALUES ('p', 0, 'u', 1000, 720, 0, 1000)")
            v2.execSQL("INSERT INTO speed_range (projectId, clip_order, startMs, endMs, speed) VALUES ('p', 0, 0, 500, '1.5')")
        }
    }

    @Test
    fun saveAndLoad_roundTripsTheProject_withExactSpeedsAndClipOrder() {
        dao.save(project, name = "Trip", now = 10)
        val loaded = dao.load("p1")!!
        assertEquals(listOf(0, 1), loaded.clips.map { it.order })
        assertEquals(project.clips.sortedBy { it.order }, loaded.clips)
        assertEquals(listOf("1.5", "0.25"), loaded.speedRanges.map { it.speed.toPlainString() })
        assertEquals(project.copy(clips = project.clips.sortedBy { it.order }), loaded)
    }

    @Test
    fun saveAgain_replacesClipsAndRanges_andKeepsCreatedAt() {
        dao.save(project, name = "Trip", now = 10)
        val edited = project.copy(muted = false, clips = project.clips.filter { it.order == 0 }, speedRanges = emptyList())
        dao.save(edited, name = "Trip", now = 20)
        val loaded = dao.load("p1")!!
        assertEquals(1, loaded.clips.size)
        assertTrue(loaded.speedRanges.isEmpty())
        assertEquals(false, loaded.muted)
        val row = dao.project("p1")!!
        assertEquals(10L to 20L, row.createdAt to row.updatedAt)
    }

    @Test
    fun deleteProject_removesItsClipsAndRanges() {
        dao.save(project, name = "Trip", now = 10)
        dao.deleteProject("p1")
        assertNull(dao.load("p1"))
        assertTrue(dao.clipsOf("p1").isEmpty())
        assertTrue(dao.rangesOf("p1").isEmpty())
    }

    @Test
    fun projects_areListedNewestFirst() {
        dao.save(project, name = "old", now = 10)
        dao.save(project.copy(id = "p2"), name = "new", now = 20)
        assertEquals(listOf("p2", "p1"), dao.projects().map { it.id })
    }

    private companion object {
        const val DB_NAME = "migration-test-v2"
    }
}
