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
dependency for what's a handful of GET/DELETE calls.

### `cachedFile`'s direct-to-destination write
Downloads straight to the cache path via `HttpResponse.BodyHandlers.ofFile`.
Swift's version downloads to a temp file first, then moves it into place --
more robust against a crash/interrupt mid-download leaving a corrupt cached
file behind. Not ported yet; worth doing before this client is trusted with
larger files.

## src/main/kotlin/com/netpress/huck/ScanEntry.kt

### `formattedDate` has no "Today"/"Yesterday" wording
Swift's `DateFormatter.doesRelativeDateFormatting = true` produces relative
day wording; `DateTimeFormatter.ofLocalizedDateTime` here doesn't have a
direct equivalent, so `formattedDate` always shows a full date. `timeAgo`
(via `Humane.distanceInTime`) is the relative-wording path instead.

## src/main/kotlin/com/netpress/huck/AppModel.kt

### Narrower than the Swift original
`selectedScanID`/`savingMessage`/`savedMessage`/`pendingDelete` and the
thumbnail/save/delete flows they support aren't ported yet -- see
`docs/COWORK.md`'s "Current status" for what's real versus deferred.

### `java.util.prefs.Preferences`, not `UserDefaults`
The direct JVM equivalent for persisting the last-connected host
(`zouk.lastHost` -- same key string as Swift, so a user's saved host would
carry over if the two ever needed to share state, though they don't today).

### `mutableStateOf` properties, not a `StateFlow`
Mirrors Swift's `@Published` directly: read/write fine in plain Kotest specs
with no Compose test rule needed, since only recomposition tracking (not
plain reads) requires an active composition.

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

### Footer given a fixed `height(40.dp)` instead of content-sized padding
Confirmed via a real screenshot: the footer visibly grew taller the moment
a scan got selected. Root cause -- the selected-scan branch includes
`CircularIconButton`, a fixed 28dp box, noticeably taller than the plain
caption-text line height the "N scans" branch uses; with only vertical
padding (no fixed height), the `Row` sized itself to whichever branch was
showing. `height(40.dp)` keeps the footer a constant height across all
three states (saving message / selected scan / scan count).

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

### Narrower than the Swift original
Real PDF thumbnails (needs a JVM PDF renderer like PDFBox -- `PDFKit` is
macOS-only), double-click download+open, the right-click context menu, the
save panel (needs a JVM file-chooser integration), and the `savedMessage`
toast aren't ported yet. Selection, `requestDelete`/`pendingDelete`/
`delete()`, and the footer bar are real now -- see `docs/COWORK.md`'s
"Not done yet".

## src/main/kotlin/com/netpress/huck/AppModel.kt

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
