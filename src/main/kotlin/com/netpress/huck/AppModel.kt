package com.netpress.huck

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
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

// Ports zouk's AppModel (Sources/ZoukKit/AppModel.swift). Real PDF thumbnails still aren't
// ported (needs PDFBox, a new dependency) -- see docs/COWORK.md "Current status" for what's
// real vs. deferred. mutableStateOf properties (not a StateFlow) mirror Swift's @Published
// directly and read/write fine in plain Kotest specs with no Compose test rule needed -- only
// recomposition tracking needs a composition.
class AppModel(
    private val preferences: Preferences = Preferences.userNodeForPackage(AppModel::class.java),
    private val clientFactory: (URI) -> ScanFetching = { ScanClient(it) },
    // zouk's own defaults are FileManager's cachesDirectory/downloadsDirectory (both
    // macOS-specific APIs) -- these are the closest portable JVM equivalents: a temp-rooted
    // cache dir (no direct JVM equivalent of NSCachesDirectory) and the real ~/Downloads,
    // matching zouk's own fallback path (homeDirectoryForCurrentUser/Downloads) exactly.
    private val cacheDirectory: File = File(System.getProperty("java.io.tmpdir"), "huck/files"),
    private val downloadsDirectory: File = File(System.getProperty("user.home"), "Downloads"),
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

    // Also reused by delete()'s "Couldn't delete ..." flash, matching zouk. Shown as a floating
    // capsule overlay by ScanGridView, not inline in the footer -- matching zouk's own
    // .overlay { ... } placement, separate from the footer's savedMessage/selectedScan/count text.
    var savingMessage: String? by mutableStateOf(null)
        private set

    // Persistent (no auto-clear timer) so it isn't missed, matching zouk's own comment on this
    // exact property.
    var savedMessage: String? by mutableStateOf(null)
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
        savedMessage = null
        client = null
    }

    // selectedScanID toggles, matching zouk's toggle(_:) -- clicking a selected cell deselects
    // it. Clears savedMessage too, matching zouk -- a fresh selection shouldn't leave a stale
    // "File ... saved." message from a previous open()/fastDownload() lingering in the footer.
    fun toggle(scan: ScanEntry) {
        savedMessage = null
        selectedScanID = if (selectedScanID == scan.id) null else scan.id
    }

    // Footer trash button only -- goes through the confirmation dialog. The context menu's own
    // "Move to Trash" (ScanGridView's ScanThumbnailCell) calls delete(_:) directly instead,
    // skipping this, matching zouk's real .contextMenu item and its comment on that exact choice.
    fun requestDelete(scan: ScanEntry) {
        selectedScanID = scan.id
        pendingDelete = scan
    }

    fun cancelDelete() {
        pendingDelete = null
    }

    // Failure flashes savingMessage rather than state = Failed(...), matching zouk -- a delete
    // failure shouldn't knock the whole screen back to the error view.
    //
    // Doesn't touch pendingDelete at all, matching zouk's real AppModel.delete(_:) exactly --
    // that's deliberately left to the UI layer's button handler (ContentView's onConfirmDelete),
    // same as zouk's ScanGridView.swift does it (Task { await model.delete(scan);
    // model.pendingDelete = nil }).
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
        }
    }

    // Double-click and the context menu's "Download and Open", matching zouk's open(_:) exactly.
    suspend fun open(scan: ScanEntry) = saveViaPanel(scan, thenOpen = true)

    // Context menu's "Download to...".
    suspend fun downloadWithoutOpening(scan: ScanEntry) = saveViaPanel(scan, thenOpen = false)

    // Context menu's "Fast Download" -- no panel, auto-named via ScanClient.uniqueDestination
    // the same Finder-style way a save panel's own overwrite-avoidance would, matching zouk.
    suspend fun fastDownload(scan: ScanEntry) {
        if (client == null) return
        selectedScanID = scan.id
        savedMessage = null
        val destination = ScanClient.uniqueDestination(scan.name, downloadsDirectory)
        save(scan, destination, thenOpen = false)
    }

    // java.awt.FileDialog is the closest JVM equivalent to NSSavePanel -- an actual native
    // dialog (backed by the OS's real save panel on macOS/Windows), not a Swing-drawn
    // JFileChooser. Runs with a null owner Frame rather than threading the real ComposeWindow
    // through from Main.kt/ContentView -- functional (still a real native modal dialog) but not
    // window-attached the way zouk's panel.runModal() is; worth revisiting if that gap is ever
    // noticeable in practice. No equivalent of zouk's ExtensionEnforcingPanelDelegate (which
    // rewrites a typed filename to always keep the original extension) -- AWT's FileDialog has
    // no delegate hook for that, only a FilenameFilter for which files are *shown*, not
    // rewriting what the user types; a real, documented gap rather than an oversight.
    private fun chooseSaveDestination(scan: ScanEntry): File? {
        val dialog = FileDialog(null as Frame?, "Choose where to save ${scan.name}.", FileDialog.SAVE)
        dialog.directory = downloadsDirectory.absolutePath
        dialog.file = scan.name
        dialog.isVisible = true // Blocks until the panel closes, matching panel.runModal().
        val chosenFile = dialog.file ?: return null
        val chosenDirectory = dialog.directory ?: return null
        return File(chosenDirectory, chosenFile)
    }

    private suspend fun saveViaPanel(
        scan: ScanEntry,
        thenOpen: Boolean,
    ) {
        if (client == null) return
        selectedScanID = scan.id
        savedMessage = null
        val destination = chooseSaveDestination(scan) ?: return
        save(scan, destination, thenOpen)
    }

    // Desktop.getDesktop().open(file) is the JVM equivalent of NSWorkspace.shared.open(_:) --
    // asks the OS to open the file with its default registered application. Unlike zouk's
    // Bool-returning open(_:), Desktop.open throws on failure -- caught by the same catch block
    // below, so a save that succeeds but fails to *open* is misreported as a lost-connection
    // save failure. A real, documented simplification rather than an oversight; narrow enough
    // (a save succeeding but the OS refusing to open the resulting file) not to hold up this
    // pass on its own.
    private suspend fun save(
        scan: ScanEntry,
        destination: File,
        thenOpen: Boolean,
    ) {
        val client = this.client ?: return
        isBusy = true
        savingMessage = "Saving ${destination.name}…"
        try {
            client.save(scan, destination, cacheDirectory)
            if (thenOpen) Desktop.getDesktop().open(destination)
            savingMessage = null
            savedMessage = "File ${destination.name} saved."
        } catch (e: Exception) {
            savingMessage = null
            state = ConnectionState.Failed("Lost connection to $hostInput while saving ${scan.name}.")
        } finally {
            isBusy = false
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
