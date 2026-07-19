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
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Ports zouk's HostEntryView (Sources/ZoukKit/HostEntryView.swift): app icon, a network-access
// notice, a host field, and a Connect button that's disabled until something's typed.
//
// fillMaxSize() + a centered Arrangement is what actually centers this block in the window --
// a bare Column sizes to its content and sits top-start otherwise (same fix as ConnectingView,
// confirmed missing on a real run). Padding/spacing match zouk's real SwiftUI numbers (40dp
// padding, 16dp spacing) -- an earlier pass tightened these to 24dp/8dp because Material's
// OutlinedTextField's built-in padding overflowed the 315dp minimum window height and clipped
// the Connect button, but OutlinedTextField is gone now (see HostTextField) and verticalScroll
// below is a floor against that overflow regardless, so there's no longer a reason not to match
// zouk's real spacing -- restored after a real side-by-side screenshot showed zouk's window
// consistently taller than ours by roughly this same margin.
@Composable
fun HostEntryView(
    hostInput: String,
    onHostInputChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
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

        Button(
            onClick = onConnect,
            enabled = hostInput.isNotBlank(),
            // A native macOS button is a neutral gray, not an accent color -- Material's
            // default Button uses MaterialTheme.colors.primary, which read as bright purple
            // against zouk's plain gray Connect button in a real side-by-side screenshot.
            colors =
                ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFE0E0E0),
                    contentColor = Color.Black,
                    disabledBackgroundColor = Color(0xFFF0F0F0),
                    disabledContentColor = Color(0xFFAAAAAA),
                ),
        ) {
            Text("Connect")
        }
    }
}
