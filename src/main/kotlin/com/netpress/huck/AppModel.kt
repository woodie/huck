package com.netpress.huck

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.io.File
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
// Widened to the full protocol (cachedFile/save/delete alongside fetchScans) now that
// selection/delete are ported -- ScanClient already had all four methods, just not declared
// against this interface yet.
interface ScanFetching {
    suspend fun fetchScans(): List<ScanEntry>

    suspend fun cachedFile(
        scan: ScanEntry,
        cacheDirectory: File,
    ): File

    suspend fun save(
        scan: ScanEntry,
        destination: File,
        cacheDirectory: File,
    ): File

    suspend fun delete(scan: ScanEntry)
}

// Ports zouk's AppModel (Sources/ZoukKit/AppModel.swift). savingMessage/savedMessage and the
// thumbnail/save/open flows they support still aren't ported (real PDF thumbnails need PDFBox,
// a new dependency; the save panel needs a JVM file-chooser integration) -- see docs/COWORK.md
// "Current status" for what's real vs. deferred. selectedScanID/pendingDelete/delete() are real
// now. mutableStateOf properties (not a StateFlow) mirror Swift's @Published directly and
// read/write fine in plain Kotest specs with no Compose test rule needed -- only recomposition
// tracking needs a composition.
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
    var selectedScanID: String? by mutableStateOf(null)
    var pendingDelete: ScanEntry? by mutableStateOf(null)
        private set

    // Also reused by delete()'s "Couldn't delete ..." flash, matching zouk -- savedMessage
    // itself (the save-flow success message) isn't ported yet since save() isn't wired to a
    // real file-save panel here.
    var savingMessage: String? by mutableStateOf(null)
        private set

    private var client: ScanFetching? = null

    val selectedScan: ScanEntry?
        get() = scans.firstOrNull { it.id == selectedScanID }

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
        this.client = client
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
        selectedScanID = null
        client = null
    }

    // selectedScanID toggles, matching zouk's toggle(_:) -- clicking a selected cell deselects it.
    fun toggle(scan: ScanEntry) {
        selectedScanID = if (selectedScanID == scan.id) null else scan.id
    }

    // Footer trash button only; a real right-click "Move to Trash" (zouk's context menu) isn't
    // ported yet, so there's no path that skips this confirmation here.
    fun requestDelete(scan: ScanEntry) {
        selectedScanID = scan.id
        pendingDelete = scan
    }

    fun cancelDelete() {
        pendingDelete = null
    }

    // Failure flashes savingMessage rather than state = Failed(...), matching zouk -- a delete
    // failure shouldn't knock the whole screen back to the error view.
    suspend fun delete(scan: ScanEntry) {
        val client = this.client ?: return
        isBusy = true
        try {
            client.delete(scan)
            scans = scans.filterNot { it.id == scan.id }
            if (selectedScanID == scan.id) selectedScanID = null
        } catch (e: Exception) {
            savingMessage = "Couldn't delete ${scan.name}."
            delay(2_000)
            savingMessage = null
        } finally {
            isBusy = false
            pendingDelete = null
        }
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
