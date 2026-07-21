# Comments

Rationale, history, and design notes that don't belong as multi-line comments
in the source. Organized by file, then by the type/function each note is
attached to. See `humane`/`humane-ruby`/`humane-swift`/`humane-kotlin`'s own
`docs/COMMENTS.md` for the pattern this follows.

## build.gradle.kts

### Deprecated Compose Gradle-plugin aliases
`compose.material` and `compose.components.resources` are both deprecated as
of Compose Multiplatform 1.10+ ("Specify dependency directly"). Both are
declared here as direct coordinates instead
(`org.jetbrains.compose.material:material:1.11.0` and
`org.jetbrains.compose.components:components-resources:1.11.0`), tracking
the `org.jetbrains.compose` plugin version above.

### ktlint's parameter-list wrapping and supertype placement
ktlint's `function-signature` rule forces any function/constructor call with
2+ parameters onto one-param-per-line once `ktlintFormat` runs, and its
class-signature rule wants a class's supertype constructor call on its own
line when that call itself spans multiple lines (`class Foo :` then
`DescribeSpec({` indented below). Both surfaced the first time `make build`
actually ran `ktlintCheck`/`ktlintFormat` against this repo's test sources
and the copied-from-`next-caltrain-kotlin` `TestListener` block -- fixed by
running `./gradlew ktlintFormat` (see the `format` Makefile target) rather
than hand-editing indentation to guess what the formatter wanted.

### The `TestListener` reporter "not matching caltrain's format"
Woodie flagged that a real, green `make test` paste (`AppModelSpec`/
`ScanEntrySpec`, full nested checkmark tree) didn't look like
`next-caltrain-kotlin`'s output -- it printed literal `[32m✔[0m [90m...[0m`
text instead of actually coloring anything. Two things turned up here, only
the second of which was the real bug:

1. The naming/comments had drifted from caltrain's copy (SCREAMING_SNAKE_CASE
   `RESET`/`GREEN`/`RED`/`CYAN`/`GRAY` vs. lowercase) because this repo's
   `.editorconfig` never disabled ktlint's `standard:property-naming` rule,
   so every `ktlintFormat` run (every `make build`/`test`/`check`) silently
   lowercased them and stripped the fuller rationale comments. Real, worth
   fixing (see the `.editorconfig` entry below), but cosmetic -- it had zero
   effect on what actually gets printed at runtime.
2. **The actual bug**: the `RESET`/`GREEN`/`RED`/`CYAN`/`GRAY` string
   literals were missing their leading ANSI escape byte (`0x1B`/`ESC`)
   entirely -- `"[0m"` instead of `"<ESC>[0m"`. `cat -A` on this file showed
   plain `[0m`; the same command against caltrain's and humane-kotlin's real
   copies showed `^[[0m` (`cat -A`'s notation for a literal `ESC` byte).
   Confirmed with a live side-by-side screenshot: humane-kotlin's terminal
   rendered real green checkmarks, huck's printed the bracket codes as
   visible text, at the same time, in the same terminal app -- ruling out
   any copy/paste explanation. Root cause: that escape byte can't be typed
   through an ordinary text-editing tool call (it's non-printable), so
   whoever/whatever last wrote this file's string literals as plain text
   silently dropped it. Fixed with `printf`/`sed` from a shell (the only way
   to actually inject the real byte) rather than a text edit. `humane-kotlin`
   and `next-caltrain-kotlin` were unaffected -- confirmed via `cat -A`
   before touching anything.

### `ktlint { filter { ... } }` excluding `generated/`
The Compose plugin registers its generated `Res.kt`/`Drawable0.main.kt`
(under `build/generated/compose/resourceGenerator`) as a real Kotlin source
directory on the `main` source set, so ktlint linted it too -- naming
violations in JetBrains' own generated code that aren't ours to fix. Excluded
via `ktlint { filter { exclude { it.file.path.contains("/generated/") } } }`.

### `nativeDistributions { windows { iconFile.set(...) } }`
Without an explicit icon, `jpackage` falls back to a generic default (a
plain coffee-cup/Duke-style placeholder) for the installed `.exe`, Start
Menu entry, and uninstaller listing on Windows -- not a build failure, just
an unbranded result. `icons/icon.ico` is a multi-resolution icon (16
through 256px, generated via `convert ... -define icon:auto-resize=...`)
built from the same `small.png` already used by `AppIconImage.kt` for the
in-app icon (`HostEntryView`'s 64dp usage) -- rather than a separately
drawn asset, so the packaged app and the in-app icon actually match. No
macOS-side `iconFile` yet (the `Dmg` target has the same generic-icon gap),
since that wasn't asked for this pass.

### `includeAllModules = true`, after a real `NoClassDefFoundError` on the packaged .msi
The installed v0.2.0 `.msi` crashed the instant `connect()` ran --
`"java/net/http/HttpClient"` in a generic error dialog, OK closing the
whole app. Root cause: `jpackage` bundles a `jlink`-trimmed JDK image, not
a full JRE, and this file didn't declare which JDK modules that image
should include -- `java.net.http` (used by `ScanClient.kt`) wasn't in
whatever the plugin's default trimmed set turned out to be. `java.desktop`
(`FileDialog`/`Desktop.open` in `AppModel.kt`) and `java.prefs`
(`Preferences.userNodeForPackage`) are real candidates to hit the exact
same failure the first time those code paths actually run, since none of
the three are called from an easy-to-statically-detect top-level site.
Rather than hand-enumerate the full reachable module set and risk a second
(or third) round of this same bug discovered only on real hardware (no
local Kotlin/jpackage toolchain to test against in this account's usual
dev loop), `includeAllModules = true` bundles the complete JDK instead of
a trimmed one -- JetBrains' own documented tradeoff for this exact failure
mode, accepted here since correctness matters more than install size for a
single-purpose utility app.

### `.editorconfig`'s `ktlint_function_naming_ignore_when_annotated_with`
Compose's convention for `@Composable` UI functions is PascalCase
(`HostEntryView`, `ConnectingView`, `RunningDogView`, ...) -- they read as
view declarations, matching how SwiftUI names view structs, not as regular
lowerCamelCase functions. ktlint's `function-naming` rule doesn't know this
by default; the `.editorconfig` property is the documented fix for ktlint +
Compose Multiplatform projects.

## src/main/kotlin/com/netpress/huck/Main.kt

### Expression-body `fun main()`
`fun main() = application { ... }` with the lambda starting on the same line
as `=` failed ktlint's rule the first time this was written (`A multiline
expression should start on a new line`) -- an expression-bodied function
whose expression itself spans multiple lines needs that expression to start
on the line after `=`. Fixed by putting `application { ... }` on its own
indented line below `fun main() =`.

### No Swift counterpart
Kotlin/JVM has no equivalent to Swift's `@main App` struct, so this file
isn't a port of anything in `zouk` -- it just hosts the `Window` and hands
off to `ContentView`, which is the real root view and does have a direct
`zouk` counterpart (`ContentView.swift`).

### Window title: `"Huck scan retriever"`
Matches zouk's real window title (`"Zouk scan retriever"`), confirmed
against a real side-by-side screenshot comparison of the two apps. Set once
here rather than per-screen -- `Window`'s title doesn't change as
`ContentView` branches between `HostEntryView`/`ConnectingView`/
`ScanGridView`, and zouk's doesn't either.

### `Window`'s `icon` parameter, to replace the generic Java coffee-cup icon
Confirmed on a real packaged-`.msi` run: the title bar and Windows taskbar
both showed the default Java icon, since `Window` (AWT/Swing underneath)
falls back to that unless a real icon is set explicitly -- setting
`icons/icon.ico` via `nativeDistributions.windows.iconFile` (see
`build.gradle.kts`) only covers the installed `.exe`/Start Menu
entry/uninstaller, a separate jpackage-level setting, not the live running
window. `icon = painterResource(Res.drawable.small)` is the same source
`AppIconImage.kt` already uses in-app, so window, taskbar, and installer
icons all actually match now.

### Window height: 280 -> 315 -> 310
Both the initial size (`rememberWindowState`) and the enforced floor
(`window.minimumSize`) were originally 360x280, matching an early explicit
request before any of `HostEntryView`/`ConnectingView`/`ScanGridView` had
real content. Bumped to 315 after a real side-by-side screenshot against
zouk showed zouk's window consistently taller by roughly that margin, then
corrected to 310 after a follow-up real screenshot -- width stays 360, only
height moved either time.

## src/main/kotlin/com/netpress/huck/ScanClient.kt

### `java.net.http.HttpClient`, not a third-party HTTP library
Matches this account's stdlib-first posture in Go/Ruby -- no Ktor/OkHttp
dependency for what's a handful of GET/DELETE calls. Lives in
`ScanHttpClient.kt`'s `JdkHttpScanHttpClient` now, not directly in
`ScanClient` -- see that file's own comments for the `ScanHttpClient`
testability seam this enabled (matching zouk's own `ScanHTTPClient`
protocol), and `ScanClientSpec.kt` for the resulting real test coverage.

### `cachedFile`'s direct-to-destination write -- fixed
Used to download straight to the cache path via
`HttpResponse.BodyHandlers.ofFile`, unlike Swift's download-to-temp-then-move
(more robust against a crash/interrupt mid-download leaving a corrupt cached
file behind). Resolved as a side effect of adding `ScanHttpClient` (see
below): its `download(url:)` returns its own temp file, and `cachedFile`
moves that into place itself via `Files.move(..., REPLACE_EXISTING)`.

## src/main/kotlin/com/netpress/huck/ScanHttpClient.kt

### `ScanHttpClient` -- a testability seam, matching zouk's `ScanHTTPClient` protocol
`ScanClient` used to call `java.net.http.HttpClient` directly, which made it
untestable (`HttpClient`'s `send()` isn't something a fake can substitute
for cleanly -- it's a single generic method over a `BodyHandler`, not a
small per-verb surface). `ScanHttpClient` adds one method per verb
`ScanClient` actually needs (`get`/`download`/`delete`) instead of
mechanically porting `URLSession`'s own three methods
(`data(from:)`/`download(from:)`/`data(for:)`) -- `HttpClient` doesn't share
that shape, and a verb-per-method interface means the DELETE verb is
guaranteed correct by construction rather than something a test has to
separately check the way zouk's `ScanClientSpec` does (it inspects
`request.httpMethod` on its generic `data(for:)` fake). `JdkHttpScanHttpClient`
is the real implementation; `FakeScanHttpClient` (test sources) is the fake,
matching zouk's own `FakeHTTPClient`.

### `connectTimeout`/`REQUEST_TIMEOUT`, where `HttpClient.newHttpClient()` has none
`java.net.http.HttpClient`'s own defaults have no timeout at all, on either
the connect or the per-request side. Confirmed as a real gap after a real
run: a `delete()` whose DELETE request never completed left
`AppModel.isBusy` stuck `true` forever (nothing ever reached the
`catch`/`finally` to reset it), which disabled the refresh button and
generally "locked" the app until it was force-restarted (Woodie's exact
report: "the app seems to go to a locked-or-bust start where clicking the
refresh button won't work"). Without a timeout, a hung connection or an
unresponsive server suspends the calling coroutine indefinitely instead of
throwing -- there's no other path back to a usable UI state. Fixed with a
10s `connectTimeout` on the `HttpClient` plus a 30s `REQUEST_TIMEOUT`
applied via `.timeout(...)` on every `HttpRequest` builder
(`get`/`download`/`delete`) -- one constant for all three request kinds
(generous enough for a real file download over a slow local network, not
just a `files.json`/DELETE round-trip) rather than tuning each separately.

## src/main/kotlin/com/netpress/huck/ScanEntry.kt

### `formattedDate` has no "Today"/"Yesterday" wording
Swift's `DateFormatter.doesRelativeDateFormatting = true` produces relative
day wording; `DateTimeFormatter.ofLocalizedDateTime` here doesn't have a
direct equivalent, so `formattedDate` always shows a full date. `timeAgo`
(via `Humane.distanceInTime`) is the relative-wording path instead.

## src/main/kotlin/com/netpress/huck/AppModel.kt

### Narrower than the Swift original
Real PDF thumbnail caching isn't ported yet (needs PDFBox) -- see
`docs/COWORK.md`'s "Current status" for what's real versus deferred.
Selection, delete, and the full save/open/download/context-menu flow
(`open`/`downloadWithoutOpening`/`fastDownload`/`saveViaPanel`/`save`) are
all real now.

### `java.util.prefs.Preferences`, not `UserDefaults`
The direct JVM equivalent for persisting the last-connected host
(`zouk.lastHost` -- same key string as Swift, so a user's saved host would
carry over if the two ever needed to share state, though they don't today).

### `mutableStateOf` properties, not a `StateFlow`
Mirrors Swift's `@Published` directly: read/write fine in plain Kotest specs
with no Compose test rule needed, since only recomposition tracking (not
plain reads) requires an active composition.

### `ScanFetching` widened to the full protocol
`ScanClient` already had `cachedFile`/`save`/`delete` alongside `fetchScans`,
matching zouk's real `ScanFetching` protocol -- they just weren't declared
against the interface `AppModel` holds its client as, so `delete()` had no
way to reach them through the stored reference. Widened the interface,
added `override` to `ScanClient`'s three methods, and extended
`AppModelSpec.kt`'s `FakeScanFetching` with throwing stubs for
`cachedFile`/`save` (not exercised by any spec yet) and a real `onDelete`
callback (exercised by the new `delete()` specs). `onDelete` is declared
*before* `result` in the constructor, with a default, specifically so
existing trailing-lambda call sites (`FakeScanFetching { fixtureScans }`)
keep binding to `result` -- Kotlin trailing lambdas always bind to the last
parameter.

### `client` is now a stored property, not a `connect()`-local `val`
`delete()` needs the same connected client `connect()` already created --
previously it was a local variable inside `connect()` and discarded
afterward. Now stored as `private var client: ScanFetching?`, matching
zouk's own `private var client: (any ScanFetching)?`, cleared in
`changeServer()` the same way.

### `selectedScanID` is a plain public var, not a `toggle`-only private one
Matches zouk's `@Published public var selectedScanID: String?` exactly --
`ContentView`'s `onDeselectAll` sets it directly (`model.selectedScanID =
null`), the same way zouk's `.onTapGesture` does, rather than routing
through a dedicated deselect function that doesn't exist in the Swift
original either.

### `delete()` doesn't touch `pendingDelete`, matching zouk exactly
zouk's real `AppModel.delete(_:)` never references `pendingDelete` -- that's
deliberately left to the UI layer's button handler. See
`ui/ContentView.kt`'s own note below for why the *timing* of clearing it
still has to differ from zouk's, even though the model-layer split is
identical.

### `requestDelete()`'s footer-only confirmation vs. the context menu's direct `delete()`
The footer trash button still goes through `requestDelete`/`pendingDelete`/
the `AlertDialog`. `ScanThumbnailCell`'s right-click "Move to Trash" calls
`delete(_:)` directly instead, skipping the dialog entirely -- matching
zouk's own `.contextMenu` item and its explicit comment on that exact
choice ("Skips confirmation deliberately; see AppModel.requestDelete(_:)").
Two distinct paths to the same `delete()`, not a shared one, on purpose.

### `java.awt.FileDialog`, not `JFileChooser`, for `saveViaPanel`
The closest JVM equivalent to `NSSavePanel` is `java.awt.FileDialog` -- a
real native dialog (backed by the OS's actual save panel), not a
Swing-drawn `JFileChooser`. Run with a null owner `Frame` rather than
threading the real `ComposeWindow` through from `Main.kt`/`ContentView` --
functional (still a real native modal dialog) but not window-attached the
way zouk's `panel.runModal()` is; worth revisiting if that gap is ever
noticeable in practice. There's also no equivalent of zouk's
`ExtensionEnforcingPanelDelegate` (which rewrites a typed filename to
always keep the original extension) -- `FileDialog` has no delegate hook
for that, only a `FilenameFilter` for which files are *shown*, not
rewriting what the user types. A real, documented gap, not an oversight.

### `Desktop.getDesktop().open(file)`, the JVM equivalent of `NSWorkspace.shared.open(_:)`
Asks the OS to open the file with its default registered application, same
as zouk's `open(_:)` path. Unlike zouk's `Bool`-returning `open(_:)`, though,
`Desktop.open` throws on failure -- caught by the same `catch` block as a
save failure, so a save that succeeds but fails to *open* is misreported as
a lost-connection save failure. Narrow enough (a save succeeding but the OS
refusing to open the resulting file) not to hold up this pass on its own.

### `fastDownload`'s auto-naming via `ScanClient.uniqueDestination`
No save panel for this path (that's the point of "fast") -- destination is
computed the same Finder-style de-dup way a save panel's own
overwrite-avoidance would (`scan.pdf` -> `scan (1).pdf` rather than
overwriting), matching zouk exactly.

## src/main/kotlin/com/netpress/huck/ui/ContentView.kt

### `onConfirmDelete` clears `pendingDelete` before launching `delete()`
zouk's real button handler is `Task { await model.delete(scan);
model.pendingDelete = nil }` -- it clears `pendingDelete` only *after*
awaiting the delete. That works fine there because SwiftUI's
`.confirmationDialog` auto-dismisses the instant any button is tapped, as
standard system behavior, independent of what the button's action closure
does or how long it takes. Compose's `AlertDialog` has no equivalent
auto-dismiss -- it stays visible for exactly as long as `pendingDelete`
stays non-null, full stop. Clearing it only after the round-trip (matching
zouk's literal ordering) left the dialog visibly stuck open for the whole
DELETE request on a real run -- the file really was being deleted the
entire time, just with nothing in the UI suggesting anything was
happening, so the only way it "closed" was manually clicking Cancel
(force-dismissing it independent of whatever the real delete was doing).
`onConfirmDelete` now calls `model.cancelDelete()` immediately, then
launches `model.delete(it)` separately -- same model-layer split as zouk
(the view decides when to dismiss, not the model), just reordered for
Compose's different dialog-dismiss semantics.

## src/main/kotlin/com/netpress/huck/ui/RunningDogView.kt

### `ImageIO`, not Skia's `Codec` API
Decided against Skiko's `Codec`/`AnimationCodecPlayer` API for animated-GIF
frame decoding because its exact signature wasn't confidently known without
a way to compile and check; `javax.imageio.ImageIO`'s built-in GIF reader is
a sure thing from the JDK itself, at the cost of one extra
`toComposeImageBitmap()` conversion step per frame.

### `running_dog.gif` lives in `src/main/resources`, not `composeResources`
Two independent reasons: Compose Resources' documented supported image
formats are JPEG/PNG/BMP/WebP + Android vector XML -- GIF isn't one of them,
so there's no `Res.drawable` path that would animate it anyway. Separately,
Compose Resources' generated accessor naming requires lowercase filenames
with underscores, and the shipped asset was `RunningDog.gif` (PascalCase) --
renamed to `running_dog.gif` when moved. Read via
`Class.getResourceAsStream("/running_dog.gif")` (a plain JVM classpath
resource) instead of `Res.drawable`.

### `composite()`'s per-frame offset and disposal handling
An earlier version drew every decoded frame at canvas position `(0, 0)`,
which only renders correctly for a frame that happens to be full-canvas --
confirmed on a real run that every other frame in `running_dog.gif` is
smaller than the logical screen, positioned at its own `(left, top)` offset,
and rendered shifted up-left as a result. Fixed by keeping one running
`BufferedImage` canvas across all frames and reading each frame's real
offset plus its GIF89a disposal method from `ImageReader.getImageMetadata`'s
`ImageDescriptor`/`GraphicControlExtension` nodes (`imageLeftPosition`/
`imageTopPosition`, `disposalMethod`) -- `restoreToBackgroundColor` clears
the frame's own rectangle back to transparent afterward,
`restoreToPrevious` reverts the canvas to its pre-frame snapshot, anything
else (`none`/`doNotDispose`) leaves the canvas as-is for the next frame to
draw on top of, matching what the GIF spec actually says playback should do.

## src/main/kotlin/com/netpress/huck/ui/HostTextField.kt

### `BasicTextField`, not Material's `OutlinedTextField`
`OutlinedTextField` enforces a hardcoded ~56dp minimum height
(`TextFieldDefaults.MinHeight`) to reserve space for its floating label,
even with no label passed -- confirmed too tall on a real run, visually
dwarfing the 64dp app icon next to it on `HostEntryView` and not matching
zouk's compact native macOS field. `BasicTextField` plus a thin manual
`border()` sidesteps the floating-label sizing entirely: height is just
text line height plus this composable's own padding. Shared by
`HostEntryView` and `ScanGridView`'s toolbar rather than duplicated.

### `textAlign` defaults to `Start`, `HostEntryView` passes `Center`
A toolbar/URL-bar field (`ScanGridView`) reads left to right; zouk's actual
`HostEntryView` centers its host field, confirmed on a real run comparing
against zouk's own screenshots.

### `fontSize = 14.sp`, vertical padding 8dp -> 6dp
A follow-up real side-by-side screenshot showed the field still noticeably
taller than zouk's and its text noticeably larger, even after switching off
`OutlinedTextField`. Pinning the font size below `body1`'s default (~16sp)
and trimming vertical padding gets the field to roughly 80% of its previous
height without shrinking small enough to clip the cursor or descenders.
This, combined with restoring `HostEntryView`/`ConnectingView` to zouk's
real 40dp/16dp padding and spacing (see that file's comments), is what
actually fixed the Connect button getting clipped at the window's minimum
height -- freeing up vertical space by shrinking the field, rather than
compressing the surrounding layout.

## src/main/kotlin/com/netpress/huck/ui/AppIconImage.kt

### Defaults to `Res.drawable.small`, not `large`
The shipped artwork (`large.png`/`small.png`) is sized for two different
contexts; `small` is what `HostEntryView`'s 64dp usage actually needs.
`large` is available for a bigger context (an About panel) that isn't built
yet -- callers should pass it explicitly when they need it.

### No runtime fallback for a missing PNG
Swift's version falls back to a system glyph if `AppIcon.png` fails to load
from disk at runtime. Not ported here because Compose Resources resolves
`Res.drawable.small`/`.large` at compile time -- a missing PNG is a build
failure, not a runtime condition, so there's nothing to fall back to.

## src/main/kotlin/com/netpress/huck/ui/HostEntryView.kt and ConnectingView.kt

### `fillMaxSize()` + a centered `Arrangement`
A bare `Column` with no size modifier sizes to its content and sits
top-start of the `Window`'s content slot -- confirmed on a real run (the
running-dog animation and the host-entry icon both rendered top-left instead
of centered). Fixed by filling the window and using a centered `Arrangement`
so the whole block centers as a group while keeping spacing between children.

### Padding/spacing: tightened, then restored to zouk's real numbers
zouk's real SwiftUI numbers are 40dp padding and 16dp spacing. An earlier
pass tightened both to 24dp/8dp because Material's `OutlinedTextField`'s
built-in padding overflowed the 310dp minimum window height and clipped the
Connect button with no way to reach it, confirmed on a real run at the time.
`OutlinedTextField` is gone now (replaced by the compact `HostTextField`),
and `verticalScroll` on both views is already a floor against that overflow
regardless of spacing -- so once a real side-by-side screenshot showed
zouk's window consistently taller than ours by roughly the padding/spacing
difference, both were restored to zouk's actual 40dp/16dp with no
reintroduced risk.

### `HostEntryView`'s Connect button uses explicit gray `ButtonDefaults.buttonColors`
Material's default `Button` uses `MaterialTheme.colors.primary`, which reads
as a bright purple/indigo accent -- confirmed against a real side-by-side
screenshot next to zouk's plain native-gray Connect button. No custom
`MaterialTheme` color palette exists yet to fix this globally, so it's set
per-button here; worth revisiting as a theme-level fix if other buttons
(the toolbar refresh icon, "Try Again") need the same treatment.

### Connect button: explicit `height(28.dp)` + near-zero `contentPadding`
Material's `Button` enforces a 36dp minimum height (internally,
`Modifier.defaultMinSize(minHeight = ButtonDefaults.MinHeight)`) plus 8dp of
vertical content padding regardless of what's inside -- confirmed way too
big next to zouk's compact native button on a real run ("the connect
button is way too big"). An explicit `Modifier.height(28.dp)` applied at
the call site overrides that internal minimum (Button's own
`defaultMinSize` only expands into slack the incoming constraints still
allow, so a tighter external constraint wins), paired with
`contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)` and
the label set to `MaterialTheme.typography.body2` (smaller than the
default `button` style) so the shorter height doesn't clip the text.

## src/main/kotlin/com/netpress/huck/ui/ScanGridView.kt

### Refresh `IconButton` + a host field that submits on Enter
zouk's real toolbar is a refresh button plus a host `TextField`, not the
"Refresh"/"Change server" text buttons this started with. Matched by
swapping in `Icons.Filled.Refresh` and wiring the host field's
`onPreviewKeyEvent` to call `onSubmitHost()` on `Key.Enter` -- editing the
host and pressing Enter reconnects without leaving this screen, so there's
no separate "change server" action anymore.

### `CircularIconButton` replaces Material's `IconButton` entirely
Two real problems surfaced here in sequence. First: Material's `IconButton`
forces a 48dp minimum touch target (`IconButtonDefaults`' internal
`defaultMinSize`) regardless of the icon inside it -- confirmed on a real
run ("adding icons makes the header and footer explode"), the same
oversized-default problem the Connect button had. An explicit
`Modifier.size(28.dp)` fixed that footprint. Second, exposed only after
that fix: Material's default ripple/hover indication has its own fixed
unbounded radius independent of the container's actual size, so on hover it
kept drawing a gray circle visibly bigger than the shrunk 28dp button --
confirmed via another real screenshot. Rather than keep patching around
`IconButton`'s defaults, ported zouk's actual `CircularIconButtonStyle`
instead: a plain `clickable` `Box` with `indication = null` and a manual
circular tint (9% opacity hovered, 22% pressed -- zouk's own numbers
exactly), sized to the icon rather than a fixed touch target. Used for both
the toolbar's refresh button and the footer's delete button.

### Footer given a fixed `height(32.dp)` instead of content-sized padding
Confirmed via a real screenshot: the footer visibly grew taller the moment
a scan got selected. Root cause -- the selected-scan branch includes
`CircularIconButton`, a fixed 28dp box, noticeably taller than the plain
caption-text line height the "N scans" branch uses; with only vertical
padding (no fixed height), the `Row` sized itself to whichever branch was
showing. A fixed height keeps the footer a constant height across all
three states (saving message / selected scan / scan count) -- started at
40dp, then tightened to 32dp (roughly 80% of the original toolbar/footer
band height) after a real side-by-side against zouk showed both bars
still too tall. The toolbar `Row`'s own padding was tightened the same
8dp -> 4dp for the same reason, keeping the two bars proportional to each
other.

### The footer never actually rendered, even before selection existed
The content `Box` used `Modifier.fillMaxSize()` inside the outer `Column`,
which also holds a `Divider` + footer `Row` below it. `fillMaxSize()` claims
the *entire* window height regardless of later siblings -- with no `weight`,
the footer got measured at zero height every time, present in the tree but
never actually visible. This had been true since before selection/delete
were ported (the old plain-list version had the same "N scans" footer text,
equally invisible) -- just never obvious until a real screenshot showed a
selected scan with no footer info below it. Fixed with
`Modifier.weight(1f).fillMaxWidth()` instead, so the content area only
claims its share of the Column's remaining height, leaving room for the
footer.

### `SelectionBlue`, not `MaterialTheme.colors.primary`
Selection used Material's default `primary` (a purple/indigo) for both the
thumbnail tint and the filename chip -- confirmed on a real side-by-side
against zouk that this was barely visible (wrong, pale hue) and missing
zouk's actual glow. zouk's selection isn't a flat tint either: it's a
15%-opacity fill *plus* a colored shadow (`selectionTint.opacity(0.55)`,
7pt radius) -- the shadow is what actually reads as a soft halo around the
thumbnail. Fixed with an explicit `SelectionBlue = Color(0xFF0A84FF)`
(macOS's real System Blue accent) and a matching `Modifier.shadow(...,
ambientColor = ..., spotColor = ...)` on the thumbnail's wrapper `Box`.

A second, real bug here: modifier *order*. `padding()` needs to come after
`shadow()`/`background()` in the chain (i.e. be the innermost modifier), not
before. `Modifier.padding(14.dp).shadow(...).background(...)` confines the
paint to the exact space left over once padding is subtracted -- which,
since the child (the icon) fills that exact remaining space, means the tint
is completely hidden behind the icon except through its fold-shaped notch
(confirmed on a real run: that's the *only* place the blue was peeking
through). `Modifier.shadow(...).background(...).padding(14.dp)` instead
paints across the *full* box first, and padding only pushes the child
(icon) inward within it -- giving the icon real inset margin the tint/glow
is actually visible in.

### Footer's scan count pluralizes, unlike zouk's literal `"\(count) scans"`
zouk's real footer text is `"\(model.scans.count) scans"` unconditionally --
grammatically off at exactly one scan ("1 scans"). Deliberately diverges
from 1:1 parity here since Woodie asked for it directly: `0 -> ""`,
`1 -> "1 scan"`, `else -> "$scanCount scans"`.

### Empty/error states centered, grid left alone
The content `Box` has no `contentAlignment` (defaults to top-start) --
correct for the grid branch, which already fills/positions itself, but the
empty-state "No scans found." text and the Failed-state message were
rendering top-left instead of centered like zouk's real
`Spacer()`-wrapped `VStack`. Rather than set `contentAlignment = Center` on
the parent `Box` (which would also recenter the grid as a side effect),
each of those two branches gets its own nested
`Box(fillMaxSize(), contentAlignment = Center)`.

### `LazyVerticalGrid` + selection, ported from a real zouk screenshot
The plain list this started with never matched zouk -- a real screenshot
with a scan selected showed a grid of thumbnails, a blue-highlighted
thumbnail plus a blue filename chip for the selected cell, and a footer bar
(absolute date/time, size, trash icon) instead of inline per-row text.
`GridCells.Adaptive(minSize = 120.dp)` approximates zouk's
`GridItem(.adaptive(minimum: 120, maximum: 160), spacing: 20)` -- Compose's
`Adaptive` only takes one bound, not a min *and* max, so cells can grow
wider than zouk's 160pt ceiling on a wide window; not worth a custom
`GridCells` implementation for this pass.

### Deselect-on-background-tap
The content `Box` wrapping the grid gets its own
`clickable(indication = null) { onDeselectAll() }`, matching zouk's
`.onTapGesture { model.selectedScanID = nil }` on the content container.
Each `ScanThumbnailCell` has its own `clickable` for toggling, which
consumes the click before it reaches the parent -- so this only fires for
clicks that land in the grid's empty space/gutters, same as zouk.

### `DogEaredDocumentIcon` ported via `GenericShape`, not deferred like PDFBox
zouk's real placeholder for an uncached thumbnail (`AppModel.thumbnail(for:)`
returning nil) is drawn from two SwiftUI `Shape`s (`PageShape`/`FoldShape`) --
pure vector paths, no `PDFKit` involved. That meant it could be ported
directly via Compose's `GenericShape` (`moveTo`/`lineTo`/`close`, the same
shape-building primitives as SwiftUI's `Path`) without waiting on a PDFBox
integration. Every scan renders with this placeholder for now since real
thumbnail caching isn't ported -- see "Not done yet" in `docs/COWORK.md`.

### Delete confirmation via `AlertDialog`, not a native `confirmationDialog`
Compose has no direct equivalent to SwiftUI's `.confirmationDialog` (which
auto-adds a system Cancel button); `androidx.compose.material.AlertDialog`
needs both `confirmButton` and `dismissButton` supplied explicitly. Title
text matches zouk's exactly (`"Delete this scan from <timeAgo>?"`, using
`pendingDelete`, not `selectedScan` -- same distinction zouk draws since a
future right-click "Move to Trash" would skip this confirmation while still
wanting a title).

### `detectTapGestures(onTap, onDoubleTap)` replaces plain `clickable()`
`clickable()` only recognizes single taps -- wired to `onToggle`, a real
double-click just fired it twice in a row (select, then immediately
deselect), confirmed on a real run and reported directly ("Double-click
just selects then unselects, back and forth forever"). `detectTapGestures`
gives double-tap explicit precedence itself: a second tap within the
system's double-tap timeout consumes both taps and fires only
`onDoubleTap`, matching zouk's own `.exclusively(before:)` between its two
`TapGesture` recognizers -- no manual timing/state needed to reproduce that
here.

### `DropdownMenu`, not `ContextMenuArea`/`ContextMenuItem`
`ContextMenuArea`/`ContextMenuItem` (Compose Desktop's built-in right-click
menu API) has no icon or separator support in its public API -- confirmed
against the real published surface, `ContextMenuItem` is just a label +
`onClick`, nothing else. zouk's real `.contextMenu` has an SF Symbol icon on
each of its four items plus a `Divider()` ahead of the destructive "Move to
Trash" item, so matching it meant replacing `ContextMenuArea` with a
manually triggered `androidx.compose.material.DropdownMenu`/
`DropdownMenuItem` instead: a `menuExpanded` boolean opened via
`Modifier.onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary))`
(the documented Compose Desktop pattern for a non-primary-button click,
`@OptIn(ExperimentalFoundationApi::class)`) rather than `ContextMenuArea`'s
declarative `items = { ... }` lambda. `PointerMatcher` lives in
`androidx.compose.foundation` (same package as the `onClick` modifier
itself), not `androidx.compose.ui.input.pointer` alongside `PointerButton`
-- a real `make check` on woodie's Mac caught this as an actual
`Unresolved reference` compile failure the first time (an easy mistake
since the two types are used together everywhere and read like they'd
share a package), fixed by moving the import. Icon mapping:
`Icons.Filled.OpenInNew` (Download and Open), `.CloudDownload` (Download
to…), `.FileDownload` (Fast Download), `.Delete` (Move to Trash) -- the
first three needed `material-icons-extended` (see `build.gradle.kts`'s own
note) since `material-icons-core` only bundles the ~50 most-common icons
and none of those cover "download"/"open externally". Each menu item's
content lays out its own `Icon` + `Spacer` + `Text` via the
`ScanContextMenuItem` helper below, since `DropdownMenuItem` (unlike
`ContextMenuItem`) takes an actual Composable slot rather than a plain
string label. A `Divider()` sits immediately before the "Move to Trash"
item, matching zouk. Still matches zouk's real four-item order and behavior
exactly: "Download and Open" (`onOpen`), "Download to…"
(`onDownloadWithoutOpening`), "Fast Download" (`onFastDownload`), "Move to
Trash" (`onDeleteImmediately`, skipping the confirmation dialog -- see the
`AppModel.kt` note above). Double-click (`onDoubleTap`) still fires the
same `onOpen` as the menu's first item, matching zouk's own
`onTapGesture(count: 2) { Task { await model.open(scan) } }` -- unaffected
by this swap, since it goes through `detectTapGestures` on the same
`Column`, not through the menu itself.

### `ScanContextMenuItem`, shrinking Material's default `DropdownMenuItem`
Confirmed too big on a real side-by-side screenshot next to zouk's compact
native menu -- Material's default `DropdownMenuItem` is a 48dp-minimum-height
row with 16dp horizontal content padding, an unset (~24dp intrinsic) icon,
and `subtitle1` (16sp) text, all sized for a touch target rather than a
small native desktop menu. The oversized look came from all of those
together, not any single dimension, so all four got trimmed together rather
than picking one to fix: `Modifier.height(...)` (overriding
`DropdownMenuItem`'s internal `sizeIn(minHeight = 48.dp)` the same way
`CircularIconButton`'s own comment already explains for `Button`/
`IconButton` -- an explicit tighter external constraint wins over
`defaultMinSize`/`sizeIn`, which only expand into whatever slack the
incoming constraints still allow), a smaller `contentPadding`, an explicit
smaller `Icon` size, a smaller `Spacer`, and a smaller `Text` style. Four
real side-by-side passes against zouk's native menu so far: a first pass
at a literal ~60% of every dimension (28dp height, 10dp padding, 15dp
icon, 5dp spacer, `body2`/14sp text) read too big; a second pass (22dp
height, 6dp padding, 13dp icon, 4dp spacer, `caption`/12sp text) swung too
far the other way, confirmed directly ("we're now too far the other
way"); a third pass (24dp height, 8dp padding, 14dp icon, 5dp spacer,
back to `body2`/14sp text) got the font size right ("looks perfect",
confirmed directly) but still wanted "a touch more padding" -- landed
back on the first pass's 28dp height/10dp padding for that (keeping the
14dp icon and 5dp spacer from the third pass, not reverting those too),
since "more padding" was the only thing asked for this round. zouk's own
menu is almost certainly just SwiftUI's unstyled `.contextMenu`
default rather than a deliberately hand-tuned size (nothing in zouk's own
`docs/COWORK.md`/`docs/COMMENTS.md` suggests otherwise), so this is a
reasonable visual match rather than a pixel-exact port -- there's no real
"zouk's contextMenu row is Npt tall" number to target the way
`HostTextField`'s sizing had one, so further nudges here are just more
real-screenshot iteration, not a bug to root-cause.
Pulled into its own private composable (`icon`/`label`/`onClick`
parameters) rather than repeating all five modifiers/params at each of the
four call sites -- same reasoning as `CircularIconButton`'s own extraction
below, shared by the toolbar and footer buttons.

### `CircularIconButton`'s box/icon size: 28dp/intrinsic 24dp -> 24dp/18dp
Asked for "a touch smaller" with no specific target given, so this landed on
a modest trim rather than a drastic resize: the button's `Box` from 28dp to
24dp, and its `Icon` from Material's intrinsic default (24dp, unset) to an
explicit 18dp. Used for both the toolbar's refresh button and the footer's
delete button, so both got smaller together. The footer's own
`height(32.dp)` comment (previously citing "28dp icon" as that number's
reason) was updated to say 24dp to match, rather than left stale.

### The `savingMessage` capsule is a sibling overlay, not inline in the footer
Matches zouk's own `.overlay { ... }` modifier, which applies to the whole
`VStack`, not just one piece of it -- implemented as a `Surface` aligned
`Center` inside the outer `Box` that already wraps the whole `Column`
(toolbar/content/footer), shown only while `savingMessage != null` (a save
in flight, or `delete()`'s own "Couldn't delete ..." failure flash).
`thinMaterial` has no direct Compose equivalent (no built-in background
blur), so this uses a plain semi-transparent `Surface` instead -- reads as
a toast/HUD rather than a true frosted-glass panel, a real but minor visual
gap.

### Narrower than the Swift original
Real PDF thumbnails (needs a JVM PDF renderer like PDFBox -- `PDFKit` is
macOS-only) aren't ported yet. Selection, delete, and the full
save/open/download/context-menu flow are all real now -- see
`docs/COWORK.md`'s "Not done yet".
