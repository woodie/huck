package com.netpress.huck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.netpress.huck.ConnectionState
import com.netpress.huck.ScanEntry
import java.time.Instant

// Ports zouk's ScanGridView (Sources/ZoukKit/ScanGridView.swift), narrowed to a plain scrollable
// list for this pass. Deliberately NOT ported yet, see docs/COWORK.md "Current status": PDF
// thumbnails (needs a JVM PDF renderer like PDFBox, PDFKit is macOS-only), the dog-eared
// placeholder shape, double-click download+open, the right-click context menu, the "saving..."
// toast, and the delete-confirmation dialog. Selection/delete aren't wired up either --
// AppModel doesn't expose selectedScanID/pendingDelete yet.
//
// Toolbar matches zouk's real one -- a refresh icon button, not a text button, and a host field
// in place of a separate "Change server" button. Enter in the field re-runs connect() against
// whatever's currently typed, so switching servers doesn't require leaving this screen.
@Composable
fun ScanGridView(
    state: ConnectionState,
    scans: List<ScanEntry>,
    isBusy: Boolean,
    hostInput: String,
    onHostInputChange: (String) -> Unit,
    onSubmitHost: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onRefresh, enabled = !isBusy) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }

            OutlinedTextField(
                value = hostInput,
                onValueChange = onHostInputChange,
                modifier =
                    Modifier.weight(1f).onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            onSubmitHost()
                            true
                        } else {
                            false
                        }
                    },
                singleLine = true,
            )
        }

        Divider()

        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            when {
                state is ConnectionState.Failed ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.message, style = MaterialTheme.typography.body1)
                        Button(onClick = onRefresh) { Text("Try Again") }
                    }

                scans.isEmpty() ->
                    Text(if (isBusy) "Loading..." else "No scans found.")

                else ->
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(scans, key = { it.id }) { scan -> ScanRow(scan) }
                    }
            }
        }

        Divider()

        Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text("${scans.size} scans", style = MaterialTheme.typography.caption)
        }
    }
}

@Composable
private fun ScanRow(scan: ScanEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(scan.name, style = MaterialTheme.typography.body2)
        Text(
            "${scan.humanSize} • ${scan.timeAgo(Instant.now())}",
            style = MaterialTheme.typography.caption,
        )
    }
}
