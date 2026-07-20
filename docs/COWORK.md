# Picking up huck in a new Cowork session

Context for whoever opens this repo cold, with none of the prior conversation
history. Cross-project conventions (git locks, sandbox toolchain gaps,
pushing, comments, code style) are in `~/workspace/woodie/docs/COWORK.md`.

## What this is

A Windows desktop port of [`zouk`](https://github.com/woodie/zouk)'s scan
client, written in Kotlin using Compose Multiplatform for Desktop. The
motivation: Woodie wants to build more desktop/mobile apps, but app-store
submission (App Store/Play Store) is enough friction to be worth avoiding
where possible -- a Windows desktop app distributed as a plain `.msi`
sidesteps that entirely, and doubles as a real project to learn Kotlin,
Compose Desktop, and 2026-era Windows packaging along the way.

`zouk` (Swift, macOS) is the feature reference. `docs/COMMENTS.md` in `zouk`
and its own `docs/COWORK.md` are the source of truth for what the real
client actually does -- `Sources/ZoukKit/` there has `ScanClient.swift`,
`ScanEntry.swift`, `ScanGridView.swift`, `AppModel.swift`,
`ConnectingView.swift`, `HostEntryView.swift`, `RunningDogView.swift` as the
real feature surface. All of it has now been read and a first real vertical
slice ported -- see "Current status" below for exactly what's real versus
still stubbed.

## Why Kotlin/Compose Desktop, and why this doesn't need a Windows machine

Compose Multiplatform for Desktop targets Windows/macOS/Linux from one
Kotlin codebase on the JVM -- develop entirely on macOS, no Windows hardware
needed day to day. The one place Windows hardware traditionally matters is
producing a real native installer: `jpackage`'s `packageMsi` task only runs
on the platform it's packaging for, since it shells out to the WiX Toolset
under the hood. `.github/workflows/windows-package.yml` solves that with a
`windows-latest` GitHub Actions runner -- a real, ephemeral Windows machine
building the `.msi`, free and uncapped since this repo is public. What CI
*can't* judge: does it actually feel native (fonts, DPI scaling, dark/light
theme, native file dialogs, drag-and-drop from Explorer). That still wants
occasional real access -- a cheap cloud Windows VM or a borrowed machine
periodically, not for every change.

## Current status

A first real vertical slice, confirmed building, testing, and running on
real hardware (not just written by inspection -- `make build`/`make test`
are both green, and the app has actually been launched and its screens
reviewed against zouk's real screenshots).

File and type names deliberately match `zouk`'s `Sources/ZoukKit/` 1:1, so
porting the next feature doesn't need a name-translation step:

| zouk (Swift)          | huck (Kotlin)         |
| --------------------- | --------------------- |
| `ContentView.swift`   | `ui/ContentView.kt`   |
| `AppModel.swift`      | `AppModel.kt`         |
| `ScanClient.swift`    | `ScanClient.kt`       |
| `ScanEntry.swift`     | `ScanEntry.kt`        |
| `HostEntryView.swift` | `ui/HostEntryView.kt` |
| `ConnectingView.swift`| `ui/ConnectingView.kt`|
| `ScanGridView.swift`  | `ui/ScanGridView.kt`  |
| `RunningDogView.swift`| `ui/RunningDogView.kt`|
| `AppIconImage.swift`  | `ui/AppIconImage.kt`  |

`Main.kt` is the one file with no Swift counterpart -- Kotlin/JVM has no
equivalent to Swift's `@main App` struct, so it just hosts the `Window` and
hands off to `ContentView`.

What's real:

- `AppModel`'s connection state machine (`ConnectionState.Idle/Connecting/
  Connected/Failed`), `connect()`'s 2-second minimum-connecting-duration
  floor, host persistence via `java.util.prefs.Preferences`, and
  `ScanClient`'s `files.json` fetch over `java.net.http.HttpClient`.
- `ContentView`'s real 3-way branch (`ConnectingView`/`ScanGridView`/
  `HostEntryView`), each centered in the window via `fillMaxSize()` + a
  centered `Arrangement` (a bare `Column` sizes to content and sits
  top-start otherwise -- caught on a real run, not by inspection).
  `HostEntryView`/`ConnectingView` are also wrapped in `verticalScroll` as a
  floor against the 310dp minimum window height clipping content.
- `RunningDogView`'s real animated-GIF frames, decoded via `ImageIO` (see
  "Toolchain gap" below and `docs/COMMENTS.md` for why, not Skia's `Codec`).
- `ScanGridView`'s toolbar matches zouk's real one: a refresh `IconButton`,
  not a text button, and a host field that reconnects on Enter instead of a
  separate "change server" button.
- `ScanGridView` is a real `LazyVerticalGrid` now, not a plain list: clicking
  a scan toggles selection (`AppModel.toggle`, matching zouk's real
  `toggle(_:)`), clicking empty space deselects, and the footer bar shows
  `savedMessage`, then the selected scan's `formattedDate`/`humanSize` plus
  a delete button that opens a real confirmation dialog
  (`AppModel.requestDelete`/`pendingDelete`/`delete`), falling back to a
  pluralized scan count -- same priority order as zouk. `ScanFetching` was
  widened to the full protocol (`cachedFile`/`save`/`delete`, not just
  `fetchScans`) since `ScanClient` already had all four methods -- they just
  weren't declared against the interface `AppModel` actually holds onto yet.
  `DogEaredDocumentIcon` (zouk's real placeholder shape for an uncached
  thumbnail) is ported directly via Compose's `GenericShape` -- it's just
  vector paths, no PDF rendering involved, so every scan renders with this
  placeholder for now (see "Not done yet" below for real thumbnails).
- Double-click (`detectTapGestures`) triggers download-and-open
  (`AppModel.open`), and a real right-click context menu (`ContextMenuArea`)
  matches zouk's four items exactly: Download and Open, Download to…
  (`downloadWithoutOpening`, via a native `java.awt.FileDialog` save panel),
  Fast Download (`fastDownload`, auto-named, no panel), and Move to Trash
  (`onDeleteImmediately`, which -- matching zouk -- skips the confirmation
  dialog the footer's trash button uses). A floating "Saving …" capsule
  (`savingMessage`) and a persistent "File … saved." footer message
  (`savedMessage`) drive user feedback around all of this, both matching
  zouk's own property names and priority. Confirmed working end-to-end on
  real hardware (menu, double-click, and select/deselect all verified).
- Kotest specs for `ScanEntry` and `AppModel` (connection, selection,
  `requestDelete`/`delete`) -- `open`/`downloadWithoutOpening`/
  `fastDownload`/`saveViaPanel`/`save` aren't unit-tested, matching zouk
  itself: they require a real modal file-dialog interaction, not something
  either codebase unit-tests. Uses `kotlinx-coroutines-test`'s `runTest` so
  the 2-second connecting floor and `delete()`'s 2-second failure flash
  don't actually slow the suite down.
- `.github/workflows/windows-package.yml` now also triggers on a `v*` tag
  push and attaches the built `.msi` to a real GitHub Release
  (`softprops/action-gh-release`) in that case, not just uploading it as a
  workflow-run artifact -- still unconfirmed against a real CI run (see
  "Not done yet" below).
- Visual fidelity against zouk on `HostEntryView`/`ConnectingView`, tuned
  through several rounds of real side-by-side screenshots (including exact
  pixel measurements at one point) rather than guessed once and left:
  window title (`"Huck scan retriever"`), window height (310dp, floor and
  initial size both), `RunningDogView`'s GIF frames rendering at their real
  per-frame offset instead of all at `(0, 0)`, `HostTextField` (a compact
  `BasicTextField` replacing Material's `OutlinedTextField`, whose forced
  ~56dp height and floating-label padding never matched zouk's native
  field), and the Connect button's color (explicit gray, not Material's
  default purple accent) and size (Material's `Button` forces a 36dp
  minimum height regardless of content, overridden with an explicit
  smaller one). See `docs/COMMENTS.md` for the specifics and the reasoning
  behind each -- there's real history here, not just final numbers.
  **`ScanGridView` (the grid/footer screen) has not had this same
  side-by-side treatment yet** -- only `HostEntryView`/`ConnectingView`
  have been checked against zouk's real screenshots pixel-for-pixel. It's
  functionally real (see above) but cell sizing, spacing, and the footer's
  exact layout are unverified against the Swift original.

### Not done yet (in rough order)

1. Real PDF thumbnails (needs a JVM PDF renderer like PDFBox -- `PDFKit` is
   macOS-only) -- every scan currently renders with `DogEaredDocumentIcon`,
   zouk's own placeholder for an uncached thumbnail, since that's a real
   `AppModel.thumbnail(for:)` cache miss state in the Swift original too.
2. `ScanGridView` itself hasn't been visually compared against zouk's real
   screenshots the way `HostEntryView`/`ConnectingView` have -- selection,
   the footer, and the save/open/context-menu flow are all confirmed
   *functionally* working on real hardware, but a same pixel-for-pixel
   side-by-side pass (cell sizing/spacing, footer layout) hasn't happened
   yet.
3. CI (`windows-package.yml`) is now confirmed producing a working `.msi`
   on a real `windows-latest` run and attaching it to a real GitHub
   Release on a `v*` tag push (`v0.2.0`:
   https://github.com/woodie/huck/releases/tag/v0.2.0). That installed
   `.msi` itself crashed on `connect()` with a `NoClassDefFoundError`
   (`java/net/http/HttpClient`) -- jpackage's trimmed JDK image didn't
   include the `java.net.http` module -- fixed in `v0.2.1` via
   `includeAllModules = true` (see `docs/COMMENTS.md`). Still needs a real
   installed-`.msi` smoke test to confirm `v0.2.1` actually launches and
   connects.

## Dependency on humane-kotlin

`settings.gradle.kts` does `includeBuild("../humane-kotlin")` -- a Gradle
composite build, not a published artifact. That means:

- `humane-kotlin` must exist as a sibling directory on disk
  (`~/workspace/humane-kotlin` next to `~/workspace/huck`) for local builds.
- `build.gradle.kts` depends on it via the coordinate `"com.netpress:humane-kotlin"`
  (matching that repo's own `group`/`rootProject.name`) -- Gradle substitutes
  this with the included build's project automatically; no version number
  needed for a composite-build substitution.
- CI (`windows-package.yml`) checks both repos out as explicit subpaths of
  `$GITHUB_WORKSPACE` (`path: huck`, `path: humane-kotlin`) so they land as
  true siblings on a fresh runner, then runs Gradle with
  `working-directory: huck`. An earlier version of this workflow checked
  huck out to `$GITHUB_WORKSPACE`'s root and humane-kotlin to `path:
  humane-kotlin`, on the assumption that `path` is relative to
  `$GITHUB_WORKSPACE`'s *parent* -- it isn't; it's relative to
  `$GITHUB_WORKSPACE` itself, so that actually nested humane-kotlin inside
  huck's own checkout instead of beside it. Confirmed by a real failed run
  (`Included build 'D:\a\huck\humane-kotlin' does not exist` -- exactly
  `../humane-kotlin` resolved from huck's checkout one level too high).
  Fixed by giving huck itself an explicit subpath rather than reaching for
  `actions/checkout`'s `allow_parent_path` (a real option for this, but not
  available on the `v4` major version this workflow pins).
- Chosen over publishing to Maven Central/GitHub Packages for now --
  simpler for two repos under one person's control, no signing/Sonatype
  ceremony, edits to `humane-kotlin` are picked up immediately with no
  version bump. Revisit if `humane-kotlin` ever needs to be consumed by a
  repo that isn't checked out locally alongside this one.

## Toolchain gap

The Cowork sandbox has no Kotlin/Gradle/Compose toolchain (only JDK 11; this
project needs 17+), so every change here is still written by reading
`zouk`'s real source and matching Kotlin's/Compose's actual (searched, not
assumed) APIs, then handed to Woodie to build/test/run on his own Mac. Per
`~/workspace/woodie/docs/COWORK.md`'s "Working on unfamiliar stacks": read,
edit by inspection, describe the change, then real hardware confirms it.
That loop has now actually happened several times this project (JDK
toolchain auto-provisioning, a deprecated Compose alias, ktlint's
parameter-wrapping and Compose-naming rules, the generated `Res` class
needing to be excluded from lint, and the window-centering/toolbar bugs
above) -- `make build`/`make test` are confirmed green and the app has been
run and its screens reviewed, but every future change still needs the same
real-hardware round trip before it's trusted.

For visual/layout tuning specifically, plain side-by-side screenshots
against zouk got most of the way there, but exact pixel measurements (e.g.
"this field is 72px tall, zouk's is 52px") turned a vague "still too tall"
into an actual calculation (assumed 2x Retina scale, worked out how much of
that gap was padding vs. line height) -- worth asking for real pixel
measurements rather than iterating blind on "smaller"/"a bit less" when
sizing needs to land close on the first real try.
