package io.github.ooscam.library

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executor

/** One grid tile: a whole session (parts as one stack) or a single file. */
data class LibraryTile(val key: String, val startedAt: Long, val items: List<LibraryItem>) {
    val parts: Int get() = items.size
    val durationMs: Long get() = items.sumOf { it.durationMs }
    val damaged: Boolean get() = items.any { it.damaged }
}

data class ConfirmDelete(val uris: List<String>)

data class LibraryUiState(
    val loading: Boolean = true,
    val tiles: List<LibraryTile> = emptyList(),
    val selected: List<String> = emptyList(),
    val confirmDelete: ConfirmDelete? = null,
) {
    val selecting: Boolean get() = selected.isNotEmpty()
    val isEmpty: Boolean get() = !loading && tiles.isEmpty()
    val hasDamaged: Boolean get() = tiles.any { it.damaged }
    val canEdit: Boolean get() = selectedTiles().any { tile -> tile.items.any { !it.damaged } }

    fun selectedTiles(): List<LibraryTile> = selected.mapNotNull { key -> tiles.firstOrNull { it.key == key } }
}

class LibraryViewModel(
    private val repository: LibraryRepository,
    private val io: Executor,
    private val main: Executor,
) {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state

    fun refresh() = io.execute {
        val tiles = repository.load().map { group -> LibraryTile(group.sessionId ?: group.items.first().uri, group.startedAt, group.items) }
        main.execute {
            val keys = tiles.map { it.key }.toSet()
            _state.value = _state.value.copy(loading = false, tiles = tiles, selected = _state.value.selected.filter { it in keys })
        }
    }

    /** Long press starts selection; while selecting, a tap toggles. Selection keeps tap order. */
    fun toggle(key: String) {
        val s = _state.value
        _state.value = s.copy(selected = if (key in s.selected) s.selected - key else s.selected + key)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selected = emptyList())
    }

    fun selectedUris(): List<String> = _state.value.selectedTiles().flatMap { tile -> tile.items.map { it.uri } }

    /** Clips for "Edit selected": selection order, then part order; damaged files are left out. */
    fun editSelection(): List<LibraryItem> = _state.value.selectedTiles().flatMap { tile -> tile.items.filter { !it.damaged } }

    fun requestDeleteSelected() {
        val uris = selectedUris()
        if (uris.isNotEmpty()) _state.value = _state.value.copy(confirmDelete = ConfirmDelete(uris))
    }

    fun requestDeleteDamaged() {
        val uris = _state.value.tiles.flatMap { tile -> tile.items.filter { it.damaged }.map { it.uri } }
        if (uris.isNotEmpty()) _state.value = _state.value.copy(confirmDelete = ConfirmDelete(uris))
    }

    fun cancelDelete() {
        _state.value = _state.value.copy(confirmDelete = null)
    }

    fun confirmDelete() {
        val pending = _state.value.confirmDelete ?: return
        _state.value = _state.value.copy(confirmDelete = null, selected = emptyList())
        io.execute { repository.delete(pending.uris) }
        refresh()
    }
}

object LibraryIntents {
    private const val MIME = "video/mp4"

    /** Opens the system video player. */
    fun play(uri: String): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(uri), MIME)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** The share sheet payload; the caller wraps it with Intent.createChooser. */
    fun share(uris: List<String>): Intent {
        val parsed = uris.map(Uri::parse)
        val intent = if (parsed.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, parsed.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(parsed))
        }
        return intent.setType(MIME).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
