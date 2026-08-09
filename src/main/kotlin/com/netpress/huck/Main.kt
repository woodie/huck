package com.netpress.huck

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.netpress.huck.resources.Res
import com.netpress.huck.resources.small
import com.netpress.huck.ui.AboutWindow
import com.netpress.huck.ui.ContentView
import org.jetbrains.compose.resources.painterResource
import java.awt.Desktop
import java.awt.Dimension

// The JVM entry point -- Kotlin has no equivalent to Swift's @main App struct, so this file has
// no direct counterpart in zouk. It just hosts the Window and hands off to ContentView, which is
// the real root view and is named/shaped to match Sources/ZoukKit/ContentView.swift.
fun main() =
    application {
        var showAbout by remember { mutableStateOf(false) }

        // macOS's app-menu "About Huck" item; unsupported on Windows, which has no equivalent
        // system hook -- see docs/COMMENTS.md.
        LaunchedEffect(Unit) {
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
                    desktop.setAboutHandler { showAbout = true }
                }
            }
        }

        Window(
            onCloseRequest = ::exitApplication,
            // Matches zouk's real window title ("Zouk scan retriever") -- confirmed against a
            // real screenshot comparison. Set once here, not per-screen: Window's title doesn't
            // change as ContentView branches between HostEntryView/ConnectingView/ScanGridView.
            title = "Huck scan retriever",
            state = rememberWindowState(size = DpSize(360.dp, 310.dp)),
            // Without this, AWT/Swing (what Window is built on) falls back to the generic Java
            // coffee-cup icon for the title bar and Windows taskbar -- confirmed on a real
            // packaged-.msi run. Same Res.drawable.small AppIconImage already uses in-app and
            // icons/icon.ico (generated from it) uses for the installed .exe/Start Menu entry, so
            // all three icons -- window, taskbar, and installer -- now actually match.
            icon = painterResource(Res.drawable.small),
        ) {
            // Window's size (above) only sets the initial size -- it's still user-resizable below that unless minimumSize is set on the underlying AWT window too.
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(360, 310)
            }

            // Windows has no system About menu the way macOS does -- this is that platform's
            // entry point to the same dialog. Harmless, if redundant, on macOS too.
            MenuBar {
                Menu("Help") {
                    Item("About Huck", onClick = { showAbout = true })
                }
            }

            val model = remember { AppModel() }
            val scope = rememberCoroutineScope()

            MaterialTheme {
                ContentView(model = model, scope = scope)
            }
        }

        if (showAbout) {
            AboutWindow(onCloseRequest = { showAbout = false })
        }
    }
