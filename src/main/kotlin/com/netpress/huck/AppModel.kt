package com.netpress.huck

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.prefs.Preferences

sealed class ConnectionState {
    data object Idle : ConnectionState()

    data object Connecting : ConnectionState()

    data object Connected : ConnectionState()

    data class Failed(
        val message: String,
    ) : ConnectionState()
}

// Test seam matching zouk's ScanFetching protocol -- lets AppModelSpec substitute a fake
// without a real network call, the same way Swift's protocol/dependency-injected client does.
interface ScanFetching {
    suspend fun fetchScans(): List<ScanEntry>
}

// Ports zouk's AppModel (Sources/ZoukKit/AppModel.swift). Deliberately narrower than the
// Swift original for this pass -- selectedScanID/savingMessage/savedMessage/pendingDelete
// and the thumbnail/save/delete flows they support aren't ported yet; see docs/COWORK.md
// "Current status" for what's real vs. deferred. mutableStateOf properties (not a
// StateFlow) mirror Swift's @Published directly and read/write fine in plain Kotest specs
// with no Compose test rule needed -- only recomposition tracking needs a composition.
class AppModel(
    private val preferences: Preferences = Preferences.userNodeForPackage(AppModel::class.java),
    private val clientFactory: (URI) -> ScanFetching = { ScanClient(it) },
) {
    var hostInput: String by mutableStateOf(preferences.get(HOST_KEY, ""))
    var state: ConnectionState by mutableStateOf(ConnectionState.Idle)
        private set
    var hasEverConnected: Boolean by mutableStateOf(false)
        private set
    var scans: List<ScanEntry> by mutableStateOf(emptyList())
        private set
    var isBusy: Boolean by mutableStateOf(false)
        private set

    // Floor, not a cap, so ConnectingView doesn't flash by on a fast local reconnect.
    suspend fun connect() {
        val baseUrl = baseUrlFrom(hostInput)
        if (baseUrl == null) {
            hasEverConnected = false
            state = ConnectionState.Failed("Enter a hostname or IP address, like scans.example.com or 10.0.1.111.")
            return
        }

        state = ConnectionState.Connecting
        isBusy = true
        val attemptStart = Instant.now()
        val client = clientFactory(baseUrl)
        try {
            scans = client.fetchScans()
            waitOutMinimumConnectingDuration(attemptStart)
            preferences.put(HOST_KEY, hostInput)
            hasEverConnected = true
            state = ConnectionState.Connected
        } catch (e: Exception) {
            waitOutMinimumConnectingDuration(attemptStart)
            hasEverConnected = false
            state = ConnectionState.Failed("Check that it's on the same network.")
        } finally {
            isBusy = false
        }
    }

    fun changeServer() {
        hasEverConnected = false
        state = ConnectionState.Idle
        scans = emptyList()
    }

    private suspend fun waitOutMinimumConnectingDuration(start: Instant) {
        val elapsed = Duration.between(start, Instant.now())
        if (elapsed < MINIMUM_CONNECTING_DURATION) {
            delay((MINIMUM_CONNECTING_DURATION - elapsed).toMillis())
        }
    }

    companion object {
        private const val HOST_KEY = "zouk.lastHost"
        private val MINIMUM_CONNECTING_DURATION: Duration = Duration.ofSeconds(2)

        fun baseUrlFrom(input: String): URI? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
            return runCatching { URI(withScheme) }.getOrNull()
        }
    }
}
