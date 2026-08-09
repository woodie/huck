package com.netpress.huck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.netpress.huck.APP_VERSION
import com.netpress.huck.resources.Res
import com.netpress.huck.resources.small

// Ports zouk's ZoukApp.swift About panel (NSApplication.orderFrontStandardAboutPanel with a
// custom applicationName/credits) -- there's no cross-platform Compose/AWT equivalent that
// reuses the OS's own About panel chrome with overridden text, so this is a small standalone
// Window styled to match it instead, rather than the jpackage-generated default (raw package
// name, confusing macOS-only version). See docs/COMMENTS.md.
@Composable
fun AboutWindow(onCloseRequest: () -> Unit) {
    Window(
        onCloseRequest = onCloseRequest,
        title = "",
        resizable = false,
        state = rememberWindowState(size = DpSize(300.dp, 240.dp)),
    ) {
        Surface {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                AppIconImage(resource = Res.drawable.small, modifier = Modifier.size(52.dp))
                Text("Huck scan retriever", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Version $APP_VERSION")
                Text("© 2026 John Woodell", style = MaterialTheme.typography.caption)
            }
        }
    }
}
