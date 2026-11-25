package io.github.ooscam.library

import io.github.ooscam.data.CaptureDao

/** One video file as the media store reports it. */
data class MediaFile(
    val uri: String,
    val displayName: String,
    val durationMs: Long,
    val height: Int,
    val bytes: Long,
    val addedAt: Long,
    val inOosCamFolder: Boolean,
)

/** MediaStore (and SAF documents) behind an interface so the library logic runs on the JVM. */
interface MediaSource {
    /** Videos in Movies/OOS Cam. */
    fun listFolder(): List<MediaFile>

    /** Any readable video by URI (SD card documents are not in the folder listing); null when gone. */
    fun describe(uri: String): MediaFile?

    fun isFinalized(uri: String): Boolean

    fun delete(uri: String): Boolean
}

data class LibraryItem(
    val uri: String,
    val displayName: String,
    val durationMs: Long,
    val height: Int,
    val damaged: Boolean,
)

/** A recording session (segments as one stack), or a single file with no session (sessionId null). */
data class LibraryGroup(val sessionId: String?, val startedAt: Long, val items: List<LibraryItem>)

/** Library data: sessions and their segments from Room, files from the media store, damaged detection. */
class LibraryRepository(private val dao: CaptureDao, private val media: MediaSource) {
    fun load(): List<LibraryGroup> {
        val folder = media.listFolder().associateBy { it.uri }
        val claimed = mutableSetOf<String>()
        val groups = mutableListOf<LibraryGroup>()
        for (session in dao.allSessions()) {
            val items = dao.segmentsOf(session.id).mapNotNull { segment ->
                claimed += segment.uri
                val file = folder[segment.uri] ?: media.describe(segment.uri) ?: return@mapNotNull null
                item(file, fallbackDurationMs = segment.durationMs)
            }
            if (items.isNotEmpty()) groups += LibraryGroup(session.id, session.startedAt, items)
        }
        for (file in folder.values) {
            if (file.uri !in claimed) groups += LibraryGroup(null, file.addedAt, listOf(item(file, fallbackDurationMs = 0)))
        }
        return groups.sortedByDescending { it.startedAt }
    }

    /** Deletes the files and their segment rows; a finished session with no segment left goes too. */
    fun delete(uris: Collection<String>) {
        for (uri in uris) {
            media.delete(uri)
            dao.deleteSegmentByUri(uri)
        }
        dao.deleteFinishedSessionsWithoutSegments()
    }

    /** L3 "Delete damaged": removes every unfinalized file in the library; returns their URIs. */
    fun deleteDamaged(): List<String> {
        val damaged = load().flatMap { group -> group.items.filter { it.damaged }.map { it.uri } }
        delete(damaged)
        return damaged
    }

    private fun item(file: MediaFile, fallbackDurationMs: Long) = LibraryItem(
        uri = file.uri,
        displayName = file.displayName,
        durationMs = if (file.durationMs > 0) file.durationMs else fallbackDurationMs,
        height = file.height,
        damaged = !media.isFinalized(file.uri),
    )
}
