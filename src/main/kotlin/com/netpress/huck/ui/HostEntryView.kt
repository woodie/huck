package com.netpress.huck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Ports zouk's HostEntryView (Sources/ZoukKit/HostEntryView.swift): app icon, a network-access
// notice, a host field, and a Connect button that's disabled until something's typed.
//
// fillMaxSize() + a centered Arrangement is what actually centers this block in the window --
// a bare Column sizes to its content and sits top-start otherwise (same fix as ConnectingView,
// confirmed missing on a real run). 40dp padding + 16dp spacing (zouk's SwiftUI numbers) plus
// Material's OutlinedTextField, which carries more built-in vertical padding than SwiftUI's
// TextField, also overflowed the 280dp minimum window height and clipped the Connect button --
// tightened padding/spacing here, and wrapped in verticalScroll as a floor so nothing is ever
// unreachable regardless of window size, font scale, or platform text metrics. OutlinedTextField
// itself was later swapped for the shared HostTextField (see that file) -- its ~56dp forced
// minimum height was confirmed too tall on a real run.
@Composable
fun HostEntryView(
    hostInput: String,
    onHostInputChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        AppIconImage(modifier = Modifier.size(64.dp))

        Text(
            "You may be prompted for network access.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.caption,
        )

        HostTextField(
            value = hostInput,
            onValueChange = onHostInputChange,
            modifier = Modifier.width(280.dp),
            textAlign = TextAlign.Center,
            onSubmit = { if (hostInput.isNotBlank()) onConnect() },
        )

        Button(onClick = onConnect, enabled = hostInput.isNotBlank()) {
            Text("Connect")
        }
    }
}
