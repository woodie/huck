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
of centered). Fixed by filling the window and using
`Arrangement.spacedBy(8.dp, Alignment.CenterVertically)` so the whole block
centers as a group while keeping spacing between children.

### Tightened padding/spacing, wrapped in `verticalScroll`
zouk's SwiftUI numbers (40dp padding, 16dp spacing) plus Material's
`OutlinedTextField` -- which carries more built-in vertical padding than
SwiftUI's `TextField` -- overflowed the 280dp minimum window height and
clipped the Connect button with no way to reach it, also confirmed on a real
run. Tightened to 24dp/8dp and wrapped both views in `verticalScroll` as a
floor so nothing is ever unreachable regardless of window size, font scale,
or platform text metrics.

## src/main/kotlin/com/netpress/huck/ui/ScanGridView.kt

### Refresh `IconButton` + a host field that submits on Enter
zouk's real toolbar is a refresh button plus a host `TextField`, not the
"Refresh"/"Change server" text buttons this started with. Matched by
swapping in `Icons.Filled.Refresh` and wiring the host field's
`onPreviewKeyEvent` to call `onSubmitHost()` on `Key.Enter` -- editing the
host and pressing Enter reconnects without leaving this screen, so there's
no separate "change server" action anymore.

### Narrower than the Swift original
PDF thumbnails (needs a JVM PDF renderer like PDFBox -- `PDFKit` is
macOS-only), the dog-eared placeholder shape shown before a thumbnail is
cached, double-click download+open, the right-click context menu, the
"saving..." toast, and the delete-confirmation dialog aren't ported yet --
`AppModel` doesn't expose `selectedScanID`/`pendingDelete` yet either. See
`docs/COWORK.md`'s "Not done yet".
