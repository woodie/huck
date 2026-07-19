package com.netpress.huck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Ports zouk's ConnectingView (Sources/ZoukKit/ConnectingView.swift) -- the running-dog
// animation plus a caption, shown for AppModel's 2-second-minimum ConnectionState.Connecting.
//
// fillMaxSize() + a centered Arrangement is what actually centers the dog in the window --
// a bare Column with no size modifier just sizes to its content and sits top-start of the
// Window's content slot, which is why an earlier pass rendered top-left instead of centered
// (confirmed on a real run). Padding/spacing match zouk's real SwiftUI numbers (40dp padding,
// 16dp spacing) -- restored after a real side-by-side screenshot showed zouk's window
// consistently taller than ours by roughly this same margin (same reasoning as HostEntryView:
// an earlier pass tightened these out of caution about the 315dp minimum window height, but
// verticalScroll below is already a floor against that regardless).
@Composable
fun ConnectingView() {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        RunningDogView(modifier = Modifier.size(128.dp))
        Text("Fetching scans...", style = MaterialTheme.typography.caption)
    }
}
