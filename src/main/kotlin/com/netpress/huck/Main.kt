package com.netpress.huck

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.netpress.huck.ui.ContentView
import java.awt.Dimension

// The JVM entry point -- Kotlin has no equivalent to Swift's @main App struct, so this file has
// no direct counterpart in zouk. It just hosts the Window and hands off to ContentView, which is
// the real root view and is named/shaped to match Sources/ZoukKit/ContentView.swift.
fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Huck",
            state = rememberWindowState(size = DpSize(360.dp, 280.dp)),
        ) {
            // Window's size (above) only sets the initial size -- it's still user-resizable below that unless minimumSize is set on the underlying AWT window too.
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(360, 280)
            }

            val model = remember { AppModel() }
            val scope = rememberCoroutineScope()

            MaterialTheme {
                ContentView(model = model, scope = scope)
            }
        }
    }
