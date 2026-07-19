package com.netpress.huck.ui

import androidx.compose.runtime.Composable
import com.netpress.huck.AppModel
import com.netpress.huck.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Ports zouk's ContentView (Sources/ZoukKit/ContentView.swift): branch on AppModel.state ==
// .connecting first, then hasEverConnected, else the host entry screen. Named and shaped to
// match the Swift original -- this is the root view, Main.kt just hosts the Window around it.
// See docs/COWORK.md "Current status" for what's real (this branch, connect(), the scan list,
// the toolbar host field) versus deferred (thumbnails, save/delete).
@Composable
fun ContentView(
    model: AppModel,
    scope: CoroutineScope,
) {
    when {
        model.state == ConnectionState.Connecting -> ConnectingView()

        model.hasEverConnected ->
            ScanGridView(
                state = model.state,
                scans = model.scans,
                isBusy = model.isBusy,
                hostInput = model.hostInput,
                onHostInputChange = { model.hostInput = it },
                onSubmitHost = { scope.launch { model.connect() } },
                onRefresh = { scope.launch { model.connect() } },
            )

        else ->
            HostEntryView(
                hostInput = model.hostInput,
                onHostInputChange = { model.hostInput = it },
                onConnect = { scope.launch { model.connect() } },
            )
    }
}
