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
  floor against the 280dp minimum window height clipping content.
- `RunningDogView`'s real animated-GIF frames, decoded via `ImageIO` (see
  "Toolchain gap" below and `docs/COMMENTS.md` for why, not Skia's `Codec`).
- `ScanGridView`'s toolbar matches zouk's real one: a refresh `IconButton`,
  not a text button, and a host field that reconnects on Enter instead of a
  separate "change server" button.
- Kotest specs for `ScanEntry` and `AppModel` (the connection half only --
  see "Not done yet" below), using `kotlinx-coroutines-test`'s `runTest` so
  the 2-second connecting floor doesn't actually slow the suite down.
- `.github/workflows/windows-package.yml` has still never actually run --
  the `packageMsi` output path it uploads is Compose Desktop's documented
  default, not yet confirmed against a real CI run.

### Not done yet (in rough order)

1. `ScanGridView`'s PDF thumbnails (needs a JVM PDF renderer like PDFBox --
   `PDFKit` is macOS-only), the dog-eared placeholder shape shown before a
   thumbnail is cached, double-click download+open, the right-click context
   menu, the "saving..." toast, and the delete-confirmation dialog.
2. `AppModel` doesn't expose `selectedScanID`/`savingMessage`/
   `savedMessage`/`pendingDelete` yet -- needed before any of the above.
3. Confirm CI (`windows-package.yml`) actually produces a working `.msi` on
   a real `windows-latest` run -- still unconfirmed.

## Dependency on humane-kotlin

`settings.gradle.kts` does `includeBuild("../humane-kotlin")` -- a Gradle
composite build, not a published artifact. That means:

- `humane-kotlin` must exist as a sibling directory on disk
  (`~/workspace/humane-kotlin` next to `~/workspace/huck`) for local builds.
- `build.gradle.kts` depends on it via the coordinate `"com.netpress:humane-kotlin"`
  (matching that repo's own `group`/`rootProject.name`) -- Gradle substitutes
  this with the included build's project automatically; no version number
  needed for a composite-build substitution.
- CI (`windows-package.yml`) checks `humane-kotlin` out into a sibling
  directory explicitly (`actions/checkout`'s `path: humane-kotlin`, which
  lands as a sibling of huck's own checkout since that `path` is relative to
  `$GITHUB_WORKSPACE`'s parent) to reproduce the same layout on a fresh
  runner.
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
