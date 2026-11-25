package io.github.ooscam.data

import android.content.Context
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
}

/** Version 1: capture tables only. Every schema change needs a migration and a migration test. */
@Database(entities = [RecordingSessionEntity::class, SegmentEntity::class], version = 1, exportSchema = true)
abstract class OOSCamDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao

    companion object {
        @Volatile private var instance: OOSCamDatabase? = null

        fun get(context: Context): OOSCamDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, OOSCamDatabase::class.java, "ooscam.db")
                .build()
                .also { instance = it }
        }
    }
}
