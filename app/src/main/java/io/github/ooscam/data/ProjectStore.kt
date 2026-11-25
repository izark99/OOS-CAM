package io.github.ooscam.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import io.github.ooscam.core.EditProject
import io.github.ooscam.core.ProjectClip
import io.github.ooscam.core.SpeedRange
import java.math.BigDecimal

/** An editor project (docs/SPEC.md data model). exportHeight is 720 or 1080. */
@Entity(tableName = "edit_project")
data class EditProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val muted: Boolean,
    val exportHeight: Int,
)

/** One clip of a project; clip_order is 0..9 (order is an SQL keyword). */
@Entity(
    tableName = "project_clip",
    primaryKeys = ["projectId", "clip_order"],
    foreignKeys = [ForeignKey(entity = EditProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
)
data class ProjectClipEntity(
    val projectId: String,
    @ColumnInfo(name = "clip_order") val order: Int,
    val sourceUri: String,
    val sourceDurationMs: Long,
    val sourceHeight: Int,
    val inMs: Long,
    val outMs: Long,
)

/** A speed range in the source time of its clip; speed is the exact decimal text ("1.5"), never a float. */
@Entity(
    tableName = "speed_range",
    foreignKeys = [ForeignKey(entity = EditProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["projectId"])],
)
data class SpeedRangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    @ColumnInfo(name = "clip_order") val clipOrder: Int,
    val startMs: Long,
    val endMs: Long,
    val speed: String,
)

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertProject(project: EditProjectEntity)

    @Insert
    fun insertClips(clips: List<ProjectClipEntity>)

    @Insert
    fun insertRanges(ranges: List<SpeedRangeEntity>)

    @Query("DELETE FROM project_clip WHERE projectId = :projectId")
    fun deleteClips(projectId: String)

    @Query("DELETE FROM speed_range WHERE projectId = :projectId")
    fun deleteRanges(projectId: String)

    @Query("SELECT * FROM edit_project WHERE id = :id")
    fun project(id: String): EditProjectEntity?

    @Query("SELECT * FROM edit_project ORDER BY updatedAt DESC")
    fun projects(): List<EditProjectEntity>

    @Query("SELECT * FROM project_clip WHERE projectId = :projectId ORDER BY clip_order")
    fun clipsOf(projectId: String): List<ProjectClipEntity>

    @Query("SELECT * FROM speed_range WHERE projectId = :projectId ORDER BY id")
    fun rangesOf(projectId: String): List<SpeedRangeEntity>

    @Query("DELETE FROM edit_project WHERE id = :id")
    fun deleteProject(id: String)

    /** Replaces the whole project (row, clips, ranges) in one transaction; createdAt is kept. */
    @Transaction
    fun save(project: EditProject, name: String, now: Long) {
        val createdAt = project(project.id)?.createdAt ?: now
        // REPLACE deletes the old row, which cascades to its clips and ranges.
        upsertProject(EditProjectEntity(project.id, name, createdAt, now, project.muted, project.exportHeight))
        deleteClips(project.id)
        deleteRanges(project.id)
        insertClips(project.clips.map { ProjectClipEntity(project.id, it.order, it.sourceUri, it.sourceDurationMs, it.sourceHeight, it.inMs, it.outMs) })
        insertRanges(project.speedRanges.map { SpeedRangeEntity(projectId = project.id, clipOrder = it.clipOrder, startMs = it.startMs, endMs = it.endMs, speed = it.speed.toPlainString()) })
    }

    @Transaction
    fun load(id: String): EditProject? {
        val p = project(id) ?: return null
        return EditProject(
            id = p.id,
            muted = p.muted,
            exportHeight = p.exportHeight,
            clips = clipsOf(id).map { ProjectClip(it.order, it.sourceUri, it.sourceDurationMs, it.sourceHeight, it.inMs, it.outMs) },
            speedRanges = rangesOf(id).map { SpeedRange(it.clipOrder, it.startMs, it.endMs, BigDecimal(it.speed)) },
        )
    }
}
