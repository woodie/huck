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
real feature surface. **None of that has been read or ported yet** -- see
"Current status" below.

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

Skeleton stage only, matching the "start minimal, prove the pipeline before
porting real features" decision this was built under:

- `Main.kt` opens a single Compose window with placeholder text -- no real
  `zouk` feature (scan discovery, grid view, host connection) is ported yet.
- It does call `Humane.humanSize(225_935)` and display the result, on
  purpose: this is the composite-build wiring check (see "Dependency on
  humane-kotlin" below) failing loudly if that wiring is ever broken, not
  a real product feature.
- `.github/workflows/windows-package.yml` has never actually run. The
  `packageMsi` output path it uploads
  (`build/compose/binaries/main/msi/*.msi`) is Compose Desktop's documented
  default, not yet confirmed against a real CI run.
- Nothing here has been compiled or run anywhere -- see "Toolchain gap"
  below.

### Not done yet (in rough order)

1. Confirm `make build`/`make test`/CI all actually work as written --
   everything above was written by inspection, not verified by compiling.
2. Read `zouk`'s real `Sources/ZoukKit/*.swift` (not yet done this session)
   to scope what the Kotlin port actually needs: `ScanClient`'s networking
   shape (decide the Kotlin equivalent -- `java.net.http.HttpClient` is
   dependency-free and matches this repo's "no third-party dep unless
   needed" posture so far; Ktor is the more idiomatic Kotlin choice if the
   networking gets non-trivial), `ScanEntry`'s data model, `ScanGridView`'s
   layout translated to Compose's `LazyVerticalGrid` or similar.
3. Decide how much of `AppModel`/`ConnectingView`/`HostEntryView`/
   `RunningDogView` is UI-only (straightforward Compose translation) vs.
   logic that deserves its own Kotest-covered module before any UI sits on
   top of it.

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
project needs 17+), so everything here was written by reading `zouk`'s and
`next-caltrain-kotlin`'s real source and matching Kotlin's actual language
semantics and Compose Multiplatform's actual (searched, not assumed) current
Gradle setup -- not verified by compiling. Per
`~/workspace/woodie/docs/COWORK.md`'s "Working on unfamiliar stacks": read,
edit by inspection, describe the change, then the build/test/package
commands above need to run on a real machine to confirm. `make build` is
the first thing worth running cold.
