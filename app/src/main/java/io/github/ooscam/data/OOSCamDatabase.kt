package io.github.ooscam.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/** One recording session (docs/SPEC.md data model). stopReason is null while recording. */
@Entity(tableName = "recording_session")
data class RecordingSessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val lens: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val audioOn: Boolean,
    val segmentMinutes: Int,
    val target: String,
    val stopReason: String?,
)

/** One segment file of a session; index starts at 1. Room holds metadata only, never the video. */
@Entity(
    tableName = "segment",
    foreignKeys = [
        ForeignKey(
            entity = RecordingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId", "segment_index"], unique = true)],
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    @ColumnInfo(name = "segment_index") val index: Int,
    val uri: String,
    val startOffsetMs: Long,
    val durationMs: Long,
    val bytes: Long,
)

@Dao
interface CaptureDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSession(session: RecordingSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSegment(segment: SegmentEntity): Long

    @Query("UPDATE recording_session SET stopReason = :stopReason WHERE id = :id")
    fun finishSession(id: String, stopReason: String)

    @Query("SELECT * FROM recording_session WHERE id = :id")
    fun session(id: String): RecordingSessionEntity?

    @Query("SELECT * FROM segment WHERE sessionId = :sessionId ORDER BY segment_index")
    fun segmentsOf(sessionId: String): List<SegmentEntity>

    @Query("DELETE FROM recording_session WHERE id = :id")
    fun deleteSession(id: String)

    @Query("SELECT * FROM recording_session ORDER BY startedAt DESC")
    fun allSessions(): List<RecordingSessionEntity>

    @Query("DELETE FROM segment WHERE uri = :uri")
    fun deleteSegmentByUri(uri: String)

    /** A session still recording (stopReason null) is never removed. */
    @Query("DELETE FROM recording_session WHERE stopReason IS NOT NULL AND id NOT IN (SELECT sessionId FROM segment)")
    fun deleteFinishedSessionsWithoutSegments()
}

/**
 * Version 1: capture tables. Version 2 adds the editor tables (edit_project, project_clip, speed_range);
 * 1 to 2 only creates tables, so it is a Room auto-migration. Every schema change needs a migration test.
 */
@Database(
    entities = [
        RecordingSessionEntity::class,
        SegmentEntity::class,
        EditProjectEntity::class,
        ProjectClipEntity::class,
        SpeedRangeEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class OOSCamDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao

    abstract fun projectDao(): ProjectDao

    companion object {
        /** Hand-written migrations (none yet; 1 to 2 is automatic). */
        val MIGRATIONS: Array<Migration> = emptyArray()

        @Volatile private var instance: OOSCamDatabase? = null

        fun get(context: Context): OOSCamDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, OOSCamDatabase::class.java, "ooscam.db")
                .addMigrations(*MIGRATIONS)
                .build()
                .also { instance = it }
        }
    }
}
