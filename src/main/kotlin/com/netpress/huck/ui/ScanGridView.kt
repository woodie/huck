package com.netpress.huck.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.netpress.huck.ConnectionState
import com.netpress.huck.ScanEntry
import java.time.Instant

// Ports zouk's ScanGridView (Sources/ZoukKit/ScanGridView.swift). Real now: the grid layout,
// selection (click a cell to toggle, click empty space to deselect), double-click to
// download-and-open, a right-click context menu (Download and Open / Download to... / Fast
// Download / Move to Trash), the footer bar (savedMessage, then selected scan's date/size + a
// delete button, falling back to a scan count -- same priority order as zouk), a floating
// "Saving ...…" capsule overlay, the delete-confirmation dialog, and real PDF thumbnails (via
// AppModel.thumbnail(for:), PDFBox-backed -- see its own comment for why). DogEaredDocumentIcon
// below is zouk's own placeholder shape, still used for the gap between a cell appearing and its
// thumbnail finishing (or a render that fails).
//
// Toolbar matches zouk's real one -- a refresh icon button, not a text button, and a host field
// in place of a separate "Change server" button. Enter in the field re-runs connect() against
// whatever's currently typed, so switching servers doesn't require leaving this screen.
@Composable
fun ScanGridView(
    state: ConnectionState,
    scans: List<ScanEntry>,
    selectedScanID: String?,
    selectedScan: ScanEntry?,
    pendingDelete: ScanEntry?,
    savingMessage: String?,
    savedMessage: String?,
    isBusy: Boolean,
    hostInput: String,
    onHostInputChange: (String) -> Unit,
    onSubmitHost: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (ScanEntry) -> Unit,
    onDeselectAll: () -> Unit,
    onOpen: (ScanEntry) -> Unit,
    onDownloadWithoutOpening: (ScanEntry) -> Unit,
    onFastDownload: (ScanEntry) -> Unit,
    onRequestDelete: (ScanEntry) -> Unit,
    onConfirmDelete: (ScanEntry) -> Unit,
    onCancelDelete: () -> Unit,
    // Right-click "Move to Trash" -- distinct from onRequestDelete/onConfirmDelete, since zouk's
    // own context menu deliberately skips the confirmation dialog for this path (see
    // AppModel.requestDelete's comment).
    onDeleteImmediately: (ScanEntry) -> Unit,
    loadThumbnail: suspend (ScanEntry) -> ImageBitmap?,
) {
    // A Box, not just the Column directly -- the Column holds the real layout (toolbar/content/
    // footer), and the saving-message capsule below is a sibling overlay on top of it, matching
    // zouk's own .overlay { ... } modifier (which applies to the whole VStack, not just one
    // piece of it).
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                // Padding tightened 8dp -> 4dp (roughly 80% of the original total band height,
                // matching the footer's own 40dp -> 32dp below) -- confirmed too tall on a real
                // side-by-side of both bars against zouk.
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularIconButton(
                    onClick = onRefresh,
                    enabled = !isBusy,
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Refresh",
                )

                HostTextField(
                    value = hostInput,
                    onValueChange = onHostInputChange,
                    modifier = Modifier.weight(1f),
                    onSubmit = onSubmitHost,
                )
            }

            Divider()

            Box(
                // weight(1f), not fillMaxSize() -- this Column also has a Divider + footer Row
                // below it. fillMaxSize() alone claims the *entire* window height regardless of
                // what follows in the Column, leaving the footer measured at zero height (present
                // but invisible) rather than sharing space with it. Confirmed on a real run: the
                // footer never actually rendered, even before selection/delete were ported.
                modifier =
                    Modifier.weight(1f).fillMaxWidth().padding(12.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDeselectAll,
                    ),
            ) {
                when {
                    // Both of these get their own fillMaxSize() + Center Box -- the parent Box
                    // above is left at its default top-start alignment specifically so the grid
                    // branch below (which already fills/positions itself correctly) isn't also
                    // recentered as a side effect. Confirmed on a real run: the empty-state message
                    // was rendering top-left instead of centered like zouk's real Spacer-wrapped
                    // VStack.
                    state is ConnectionState.Failed ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(state.message, style = MaterialTheme.typography.body1)
                                Button(onClick = onRefresh) { Text("Try Again") }
                            }
                        }

                    scans.isEmpty() ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (isBusy) "Loading..." else "No scans found.")
                        }

                    else ->
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            items(scans, key = { it.id }) { scan ->
                                ScanThumbnailCell(
                                    scan = scan,
                                    isSelected = scan.id == selectedScanID,
                                    onToggle = { onToggle(scan) },
                                    onOpen = { onOpen(scan) },
                                    onDownloadWithoutOpening = { onDownloadWithoutOpening(scan) },
                                    onFastDownload = { onFastDownload(scan) },
                                    onDeleteImmediately = { onDeleteImmediately(scan) },
                                    loadThumbnail = loadThumbnail,
                                )
                            }
                        }
                }
            }

            Divider()

            ScanGridFooter(
                savedMessage = savedMessage,
                selectedScan = selectedScan,
                scanCount = scans.size,
                onDelete = onRequestDelete,
            )
        }

        // Matches zouk's own overlay { ... } -- a translucent capsule, centered over the whole
        // screen, shown only while a save is in flight (or during delete()'s "Couldn't delete
        // ..." failure flash). thinMaterial has no direct Compose equivalent (no built-in
        // background blur), so this uses a plain semi-transparent Surface instead -- reads as a
        // toast/HUD rather than a true frosted-glass panel, a real but minor visual gap.
        if (savingMessage != null) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colors.surface.copy(alpha = 0.9f),
                elevation = 4.dp,
            ) {
                Text(
                    savingMessage,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }

    // presenting uses pendingDelete, not selectedScan, matching zouk -- title mirrors the web
    // listing's confirm() text via ScanEntry.timeAgo.
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text("Delete this scan from ${pendingDelete.timeAgo(Instant.now())}?") },
            confirmButton = {
                TextButton(onClick = { onConfirmDelete(pendingDelete) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ScanGridFooter(
    savedMessage: String?,
    selectedScan: ScanEntry?,
    scanCount: Int,
    onDelete: (ScanEntry) -> Unit,
) {
    Row(
        // A fixed height, not vertical padding sized to content -- otherwise this row grows
        // whenever the selected-scan branch is showing (its CircularIconButton is 24dp tall,
        // noticeably more than the caption text alone), confirmed via a real screenshot: the
        // footer visibly grew taller the moment a scan got selected. 32dp (tightened from an
        // initial 40dp, roughly 80% -- confirmed too tall on a real side-by-side against zouk)
        // still comfortably fits the 24dp icon either way, so the footer's height stays
        // constant across all three states.
        modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // savedMessage takes priority over the selected scan's own info, matching zouk's footer
        // exactly (if let saved = model.savedMessage { ... } else if let scan = ... ).
        when {
            savedMessage != null -> Text(savedMessage, style = MaterialTheme.typography.caption)

            selectedScan != null ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    selectedScan.formattedDate?.let { Text(it, style = MaterialTheme.typography.caption) }
                    Text(selectedScan.humanSize, style = MaterialTheme.typography.caption)
                    CircularIconButton(
                        onClick = { onDelete(selectedScan) },
                        icon = Icons.Filled.Delete,
                        contentDescription = "Delete this scan",
                    )
                }

            else ->
                Text(
                    when (scanCount) {
                        0 -> ""
                        1 -> "1 scan"
                        else -> "$scanCount scans"
                    },
                    style = MaterialTheme.typography.caption,
                )
        }
    }
}

// Ports zouk's CircularIconButtonStyle exactly, rather than fighting Material's IconButton.
// IconButton forces a 48dp minimum touch target (IconButtonDefaults' defaultMinSize) regardless
// of the icon inside it -- confirmed too big on a real run ("adding icons makes the header and
// footer explode"), the same oversized-default problem the Connect button had. An explicit
// Modifier.size() override fixed the footprint but not a second issue it exposed: Material's
// default ripple/hover indication has its own fixed unbounded radius independent of the
// container's actual size, so it kept overflowing past the smaller button on hover (confirmed
// via a real screenshot -- a gray circle visibly bigger than the icon's own bounds). Simplest
// fix was to stop using IconButton here entirely and port zouk's real button style instead: a
// plain clickable Box, no system ripple, with a circular tint that only appears on press/hover
// (9% opacity hovered, 22% pressed, matching CircularIconButtonStyle's own numbers exactly) --
// sized to the icon, not a fixed touch target.
@Composable
private fun CircularIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val tintAlpha =
        when {
            isPressed -> 0.22f
            isHovered -> 0.09f
            else -> 0f
        }

    Box(
        modifier =
            modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colors.onSurface.copy(alpha = tintAlpha))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colors.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

// Zouk's own placeholder for a scan whose thumbnail isn't cached yet (Finder-style dog-eared
// page). Ported as plain vector paths -- no PDF rendering involved, so this needed no new
// dependency to bring over as-is, unlike the real thumbnail image it stands in for.
@Composable
private fun DogEaredDocumentIcon(modifier: Modifier = Modifier) {
    val foldPx = with(LocalDensity.current) { 14.dp.toPx() }
    val pageShape =
        remember(foldPx) {
            GenericShape { size, _ ->
                moveTo(0f, 0f)
                lineTo(size.width - foldPx, 0f)
                lineTo(size.width, foldPx)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
        }
    val foldShape =
        remember(foldPx) {
            GenericShape { size, _ ->
                moveTo(size.width - foldPx, 0f)
                lineTo(size.width, foldPx)
                lineTo(size.width - foldPx, foldPx)
                close()
            }
        }
    val strokeColor = MaterialTheme.colors.onSurface.copy(alpha = 0.35f)

    Box(
        modifier =
            modifier
                .shadow(elevation = 3.dp, shape = pageShape, clip = false)
                .background(MaterialTheme.colors.surface, pageShape)
                .border(1.dp, strokeColor, pageShape),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .background(MaterialTheme.colors.onSurface.copy(alpha = 0.25f), foldShape)
                    .border(1.dp, strokeColor, foldShape),
        )
    }
}

// macOS's real selection accent (System Blue), not Material's default purple/indigo primary --
// confirmed on a real side-by-side against zouk: the selection tint was barely visible (wrong,
// pale-purple hue) and missing zouk's actual glow entirely.
private val SelectionBlue = Color(0xFF0A84FF)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScanThumbnailCell(
    scan: ScanEntry,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onDownloadWithoutOpening: () -> Unit,
    onFastDownload: () -> Unit,
    onDeleteImmediately: () -> Unit,
    loadThumbnail: suspend (ScanEntry) -> ImageBitmap?,
) {
    // Keyed on scan.id, matching zouk's @State private var image: NSImage? + .task { ... } --
    // a fresh LaunchedEffect (and thumbnail fetch) per distinct scan, not per recomposition.
    var image by remember(scan.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(scan.id) { image = loadThumbnail(scan) }

    // ContextMenuArea/ContextMenuItem has no icon or separator support (label + onClick only),
    // so the menu itself is a manually triggered DropdownMenu instead -- see docs/COMMENTS.md.
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        // detectTapGestures(onTap, onDoubleTap) replaces the plain clickable() this started
        // with -- clickable() only recognizes single taps, so a real double-click just fired
        // onToggle twice in a row (select, then immediately deselect), confirmed on a real run.
        // detectTapGestures gives double-tap explicit precedence itself (a second tap within
        // the system's double-tap timeout consumes both taps and fires only onDoubleTap,
        // matching zouk's own .exclusively(before:) between its two TapGesture recognizers) --
        // no manual timing/state needed here.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
                Modifier
                    .pointerInput(scan.id) {
                        detectTapGestures(onTap = { onToggle() }, onDoubleTap = { onOpen() })
                    }.onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary)) {
                        menuExpanded = true
                    },
        ) {
            Box(
                modifier =
                    Modifier
                        // Zouk's selection isn't just a flat tint -- it's a 15%-opacity fill
                        // *plus* a colored shadow/glow (55% opacity, 7pt radius), which is what
                        // actually makes it read as a soft blue halo around the thumbnail rather
                        // than a flat background square. shadow()/background() must come BEFORE
                        // padding() here -- modifier order matters: padding placed first
                        // (outermost) confines the paint to the exact icon bounds, since it
                        // shrinks the space handed to the modifiers after it, leaving no visible
                        // margin at all except through the icon's own fold-shaped notch
                        // (confirmed on a real run -- that's exactly where the blue was peeking
                        // through and nowhere else). Padding placed last (innermost) instead only
                        // pushes the *child* inward, so shadow/background paint across the full
                        // padded box, with the icon sitting inset within it.
                        .shadow(
                            elevation = if (isSelected) 7.dp else 0.dp,
                            shape = RoundedCornerShape(10.dp),
                            clip = false,
                            ambientColor = SelectionBlue.copy(alpha = 0.55f),
                            spotColor = SelectionBlue.copy(alpha = 0.55f),
                        ).background(
                            if (isSelected) SelectionBlue.copy(alpha = 0.15f) else Color.Transparent,
                            RoundedCornerShape(10.dp),
                        ).padding(14.dp),
            ) {
                val thumbnailImage = image
                if (thumbnailImage != null) {
                    Image(
                        bitmap = thumbnailImage,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier =
                            Modifier
                                .size(width = 76.dp, height = 96.dp)
                                .background(MaterialTheme.colors.surface)
                                .clip(RoundedCornerShape(6.dp)),
                    )
                } else {
                    DogEaredDocumentIcon(modifier = Modifier.size(width = 76.dp, height = 96.dp))
                }
            }

            Text(
                scan.name,
                style = MaterialTheme.typography.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) Color.White else MaterialTheme.colors.onSurface,
                modifier =
                    Modifier
                        .background(if (isSelected) SelectionBlue else Color.Transparent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            ScanContextMenuItem(Icons.Filled.OpenInNew, "Download and Open") {
                menuExpanded = false
                onOpen()
            }
            ScanContextMenuItem(Icons.Filled.CloudDownload, "Download to…") {
                menuExpanded = false
                onDownloadWithoutOpening()
            }
            ScanContextMenuItem(Icons.Filled.FileDownload, "Fast Download") {
                menuExpanded = false
                onFastDownload()
            }
            Divider()
            // Skips the confirmation dialog deliberately, matching zouk's own comment on this
            // exact menu item -- only the footer trash button confirms.
            ScanContextMenuItem(Icons.Filled.Delete, "Move to Trash") {
                menuExpanded = false
                onDeleteImmediately()
            }
        }
    }
}

// Shrinks Material's default DropdownMenuItem sizing toward zouk's native menu -- see docs/COMMENTS.md.
@Composable
private fun ScanContextMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        onClick = onClick,
        modifier = Modifier.height(24.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.body2)
    }
}
