package com.netpress.huck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.netpress.huck.ConnectionState
import com.netpress.huck.ScanEntry
import java.time.Instant

// Ports zouk's ScanGridView (Sources/ZoukKit/ScanGridView.swift). Real now: the grid layout,
// selection (click a cell to toggle, click empty space to deselect), the footer bar (selected
// scan's date/size + a delete button, falling back to a scan count), and the delete-confirmation
// dialog. Still deferred, see docs/COWORK.md: real PDF thumbnails (needs PDFBox, a new
// dependency -- DogEaredDocumentIcon below is zouk's own placeholder shape for an uncached
// thumbnail, ported directly since it's just vector paths, no PDF rendering involved), the
// savingMessage flash during a real save (only delete()'s failure flash is wired up so far),
// double-click download+open, and the right-click context menu (Download/Fast Download/Move to
// Trash) -- so for now every scan renders as if its thumbnail were never cached.
//
// Toolbar matches zouk's real one -- a refresh icon button, not a text button, and a host field
// in place of a separate "Change server" button. Enter in the field re-runs connect() against
// whatever's currently typed, so switching servers doesn't require leaving this screen.
@Composable
fun ScanGridView(
    state: ConnectionState,
    scans: List<ScanEntry>,
    selectedScanID: String?,
    selectedScan: ScanEntry?,
    pendingDelete: ScanEntry?,
    savingMessage: String?,
    isBusy: Boolean,
    hostInput: String,
    onHostInputChange: (String) -> Unit,
    onSubmitHost: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (ScanEntry) -> Unit,
    onDeselectAll: () -> Unit,
    onRequestDelete: (ScanEntry) -> Unit,
    onConfirmDelete: (ScanEntry) -> Unit,
    onCancelDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Material's IconButton forces a 48dp minimum touch target (IconButtonDefaults'
            // defaultMinSize) regardless of the icon inside it -- the same oversized-default
            // problem HostEntryView's Connect button had, confirmed the same way here on a
            // real run ("adding icons makes the header and footer explode"). An explicit
            // Modifier.size() at the call site overrides it, same fix as the Connect button.
            IconButton(onClick = onRefresh, enabled = !isBusy, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }

            HostTextField(
                value = hostInput,
                onValueChange = onHostInputChange,
                modifier = Modifier.weight(1f),
                onSubmit = onSubmitHost,
            )
        }

        Divider()

        Box(
            // weight(1f), not fillMaxSize() -- this Column also has a Divider + footer Row
            // below it. fillMaxSize() alone claims the *entire* window height regardless of
            // what follows in the Column, leaving the footer measured at zero height (present
            // but invisible) rather than sharing space with it. Confirmed on a real run: the
            // footer never actually rendered, even before selection/delete were ported.
            modifier =
                Modifier.weight(1f).fillMaxWidth().padding(12.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDeselectAll,
                ),
        ) {
            when {
                state is ConnectionState.Failed ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.message, style = MaterialTheme.typography.body1)
                        Button(onClick = onRefresh) { Text("Try Again") }
                    }

                scans.isEmpty() ->
                    Text(if (isBusy) "Loading..." else "No scans found.")

                else ->
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(scans, key = { it.id }) { scan ->
                            ScanThumbnailCell(
                                scan = scan,
                                isSelected = scan.id == selectedScanID,
                                onToggle = { onToggle(scan) },
                            )
                        }
                    }
            }
        }

        Divider()

        ScanGridFooter(
            savingMessage = savingMessage,
            selectedScan = selectedScan,
            scanCount = scans.size,
            onDelete = onRequestDelete,
        )
    }

    // presenting uses pendingDelete, not selectedScan, matching zouk -- title mirrors the web
    // listing's confirm() text via ScanEntry.timeAgo.
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text("Delete this scan from ${pendingDelete.timeAgo(Instant.now())}?") },
            confirmButton = {
                TextButton(onClick = { onConfirmDelete(pendingDelete) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ScanGridFooter(
    savingMessage: String?,
    selectedScan: ScanEntry?,
    scanCount: Int,
    onDelete: (ScanEntry) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            savingMessage != null -> Text(savingMessage, style = MaterialTheme.typography.caption)

            selectedScan != null ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    selectedScan.formattedDate?.let { Text(it, style = MaterialTheme.typography.caption) }
                    Text(selectedScan.humanSize, style = MaterialTheme.typography.caption)
                    // Same 48dp-default override as the toolbar's refresh IconButton above --
                    // 28dp keeps a little breathing room around the Icon's own 24dp default
                    // size rather than clipping it against an exact-fit container.
                    IconButton(onClick = { onDelete(selectedScan) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete this scan")
                    }
                }

            else ->
                Text(
                    if (scanCount == 0) "" else "$scanCount scans",
                    style = MaterialTheme.typography.caption,
                )
        }
    }
}

// Zouk's own placeholder for a scan whose thumbnail isn't cached yet (Finder-style dog-eared
// page). Ported as plain vector paths -- no PDF rendering involved, so this needed no new
// dependency to bring over as-is, unlike the real thumbnail image it stands in for.
@Composable
private fun DogEaredDocumentIcon(modifier: Modifier = Modifier) {
    val foldPx = with(LocalDensity.current) { 14.dp.toPx() }
    val pageShape =
        remember(foldPx) {
            GenericShape { size, _ ->
                moveTo(0f, 0f)
                lineTo(size.width - foldPx, 0f)
                lineTo(size.width, foldPx)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
        }
    val foldShape =
        remember(foldPx) {
            GenericShape { size, _ ->
                moveTo(size.width - foldPx, 0f)
                lineTo(size.width, foldPx)
                lineTo(size.width - foldPx, foldPx)
                close()
            }
        }
    val strokeColor = MaterialTheme.colors.onSurface.copy(alpha = 0.35f)

    Box(
        modifier =
            modifier
                .shadow(elevation = 3.dp, shape = pageShape, clip = false)
                .background(MaterialTheme.colors.surface, pageShape)
                .border(1.dp, strokeColor, pageShape),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .background(MaterialTheme.colors.onSurface.copy(alpha = 0.25f), foldShape)
                    .border(1.dp, strokeColor, foldShape),
        )
    }
}

@Composable
private fun ScanThumbnailCell(
    scan: ScanEntry,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    val selectionTint = MaterialTheme.colors.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .padding(6.dp)
                    .background(
                        if (isSelected) selectionTint.copy(alpha = 0.15f) else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    ),
        ) {
            DogEaredDocumentIcon(modifier = Modifier.size(width = 76.dp, height = 96.dp))
        }

        Text(
            scan.name,
            style = MaterialTheme.typography.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isSelected) Color.White else MaterialTheme.colors.onSurface,
            modifier =
                Modifier
                    .background(if (isSelected) selectionTint else Color.Transparent, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
