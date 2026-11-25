package io.github.ooscam.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ooscam.R
import io.github.ooscam.camera.formatElapsed

/** Interim library screen (Phase 3): grid, play, share, delete with confirm, multi-select, Edit selected. Phase 4 restyles it. */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onBack: () -> Unit,
    onTap: (LibraryTile) -> Unit,
    onLongPress: (LibraryTile) -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDeleteDamaged: () -> Unit,
    onEditSelected: () -> Unit,
    onImport: () -> Unit,
    onClearSelection: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = if (state.selecting) onClearSelection else onBack) {
                Text(stringResource(if (state.selecting) R.string.library_cancel_selection else R.string.library_back))
            }
            Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onImport) { Text(stringResource(R.string.library_import)) }
            if (state.hasDamaged) OutlinedButton(onClick = onDeleteDamaged) { Text(stringResource(R.string.library_delete_damaged)) }
        }
        Box(Modifier.weight(1f)) {
            when {
                state.loading -> Text(stringResource(R.string.library_loading))
                state.isEmpty -> Text(stringResource(R.string.library_empty))
                else -> LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(state.tiles, key = { it.key }) { tile ->
                        Tile(tile, picked = tile.key in state.selected, onTap = { onTap(tile) }, onLongPress = { onLongPress(tile) })
                    }
                }
            }
        }
        if (state.selecting) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShare) { Text(stringResource(R.string.library_share)) }
                OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.library_delete)) }
                Button(onClick = onEditSelected, enabled = state.canEdit) { Text(stringResource(R.string.library_edit_selected)) }
            }
        }
    }
    state.confirmDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text(stringResource(R.string.library_delete_title)) },
            text = { Text(pluralStringResource(R.plurals.library_delete_body, pending.uris.size, pending.uris.size)) },
            confirmButton = { TextButton(onClick = onConfirmDelete) { Text(stringResource(R.string.library_delete)) } },
            dismissButton = { TextButton(onClick = onCancelDelete) { Text(stringResource(R.string.library_keep)) } },
        )
    }
}

@Composable
private fun Tile(tile: LibraryTile, picked: Boolean, onTap: () -> Unit, onLongPress: () -> Unit) {
    Card(
        Modifier.aspectRatio(1f).semantics { selected = picked }.combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatElapsed(tile.durationMs), style = MaterialTheme.typography.labelLarge)
            if (tile.parts > 1) Text(pluralStringResource(R.plurals.library_parts, tile.parts, tile.parts), style = MaterialTheme.typography.labelSmall)
            if (tile.damaged) Text(stringResource(R.string.library_damaged), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            if (picked) Text(stringResource(R.string.library_selected), style = MaterialTheme.typography.labelSmall)
        }
    }
}
