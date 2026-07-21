package com.netpress.huck.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.netpress.huck.AppModel
import com.netpress.huck.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Ports zouk's ContentView (Sources/ZoukKit/ContentView.swift): branch on AppModel.state ==
// .connecting first, then hasEverConnected, else the host entry screen. Named and shaped to
// match the Swift original -- this is the root view, Main.kt just hosts the Window around it.
// See docs/COWORK.md "Current status" for what's real versus still deferred.
@Composable
fun ContentView(
    model: AppModel,
    scope: CoroutineScope,
) {
    // Ports zouk's AppModel.init auto-connect (Task { await connect() } when autoConnect &&
    // !hostInput.isEmpty), moved here rather than into AppModel's constructor: AppModel never
    // launches its own coroutines elsewhere (the UI always does via scope.launch { ... }), and
    // AppModelSpec constructs AppModel directly expecting no side effects -- a constructor-fired
    // connect() would make a real network call during those tests via the default clientFactory.
    LaunchedEffect(Unit) {
        if (model.hostInput.isNotEmpty()) {
            model.connect()
        }
    }

    when {
        model.state == ConnectionState.Connecting -> ConnectingView()

        model.hasEverConnected ->
            ScanGridView(
                state = model.state,
                scans = model.scans,
                selectedScanID = model.selectedScanID,
                selectedScan = model.selectedScan,
                pendingDelete = model.pendingDelete,
                savingMessage = model.savingMessage,
                savedMessage = model.savedMessage,
                isBusy = model.isBusy,
                hostInput = model.hostInput,
                onHostInputChange = { model.hostInput = it },
                onSubmitHost = { scope.launch { model.connect() } },
                onRefresh = { scope.launch { model.connect() } },
                onToggle = { model.toggle(it) },
                onDeselectAll = { model.selectedScanID = null },
                // Double-click (context menu's "Download and Open") and the other three menu
                // items, matching zouk's real ScanThumbnailCell .contextMenu exactly.
                onOpen = { scope.launch { model.open(it) } },
                onDownloadWithoutOpening = { scope.launch { model.downloadWithoutOpening(it) } },
                onFastDownload = { scope.launch { model.fastDownload(it) } },
                // Skips the confirmation dialog deliberately, matching zouk's own comment on
                // this exact menu item and AppModel.requestDelete's own doc comment.
                onDeleteImmediately = { scope.launch { model.delete(it) } },
                onRequestDelete = { model.requestDelete(it) },
                // Dismisses immediately, before launching delete() -- unlike zouk's own
                // Task { await model.delete(scan); model.pendingDelete = nil }, which clears
                // pendingDelete only *after* awaiting. That ordering works fine in zouk because
                // SwiftUI's confirmationDialog auto-dismisses the instant any button is tapped,
                // as standard system behavior, independent of what the button's action closure
                // does. Compose's AlertDialog has no such auto-dismiss -- it stays visible for
                // exactly as long as pendingDelete stays non-null -- so clearing it only after
                // the network round-trip left the dialog visibly stuck open the whole time on a
                // real run (the file was actually being deleted the whole time; nothing in the
                // UI showed it). cancelDelete() reused here since the underlying action (close
                // the dialog now) is identical to the Cancel button's own.
                onConfirmDelete = {
                    model.cancelDelete()
                    scope.launch { model.delete(it) }
                },
                onCancelDelete = { model.cancelDelete() },
            )

        else ->
            HostEntryView(
                hostInput = model.hostInput,
                onHostInputChange = { model.hostInput = it },
                onConnect = { scope.launch { model.connect() } },
            )
    }
}
