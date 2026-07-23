# Picking up huck in a new Cowork session

Context for whoever opens this repo cold. Cross-project conventions (git
locks, sandbox toolchain gaps, pushing, comments, code style) are in
`~/workspace/woodie/docs/COWORK.md`. Read `README.md` first for the
user-facing description; for building from source and the release
process, see `docs/DEVELOPER.md`/`docs/DELIVERY.md`.

## What this is

A Windows desktop port of zouk's scan-client feature set, in Kotlin using
Compose Multiplatform for Desktop. zouk (Swift, macOS) is the feature
reference -- its own `docs/COMMENTS.md`/`docs/COWORK.md` are the source of
truth for what the real client does. File/type names match zouk's
`Sources/ZoukKit` 1:1 (`ContentView.swift` -> `ui/ContentView.kt`,
`AppModel.swift` -> `AppModel.kt`, etc.) so porting a feature doesn't need
a name-translation step.

## Toolchain gap

The Cowork sandbox has no Kotlin/Gradle/Compose toolchain (only JDK 11;
this project needs 17+), so every change here is written by reading
zouk's real source and matching Kotlin's/Compose's actual (searched, not
assumed) APIs, then handed to Woodie to build/test/run on his own Mac.
Per `~/workspace/woodie/docs/COWORK.md`'s "Working on unfamiliar stacks."

## Gotchas worth knowing

- **`ContextMenuArea`/`ContextMenuItem`** (Compose Desktop's built-in
  right-click menu) has no icon or separator support -- use a manually
  triggered `DropdownMenu`/`DropdownMenuItem` instead (see
  `ScanGridView.kt`'s `ScanContextMenuItem`, opened via
  `Modifier.onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary))`).
- **`PointerMatcher` lives in `androidx.compose.foundation`**, not
  `androidx.compose.ui.input.pointer` -- easy mistake since `PointerButton`
  (used at the same call sites) *is* in that package. Caught by a real
  `make check` compile failure once already.
- **Material's `Button`/`IconButton`/`DropdownMenuItem` all enforce
  oversized minimum touch targets** (36-48dp) that a naive
  `Modifier.size()` alone won't shrink -- an explicit tighter constraint
  wins over `defaultMinSize`/`sizeIn`, which only expands into whatever
  slack the incoming constraints still allow. See `docs/COMMENTS.md` for
  the specific instances (`CircularIconButton`, the Connect button,
  `ScanContextMenuItem`).
- **Gradle doesn't guarantee task ordering within one `./gradlew`
  invocation** -- `ktlintFormat` needs its own separate invocation before
  `ktlintCheck`/`clean check` runs against the result, or formatting
  fixes land too late to matter (see the Makefile's own comments on
  `build`/`test`/`check`).
- **`humane-kotlin` used to require a sibling checkout** (`../humane-kotlin`,
  via `settings.gradle.kts`'s `includeBuild`) since it wasn't a published
  artifact. Now that it's on Maven Central (`com.netpress:humane-kotlin`,
  starting `v0.1.1`), `build.gradle.kts` pins a real version instead -- no
  sibling checkout needed locally or in CI anymore (see
  `.github/workflows/windows-package.yml`'s single plain checkout, and
  humane-kotlin's own `docs/COWORK.md` for the publish setup this depends
  on). Bump the pinned version by hand when a new humane-kotlin release
  ships -- no lockfile to regenerate for a plain Kotlin/JVM
  `implementation(...)` dependency the way Ruby/Go's real fetch-based
  lockfiles need one.
- **`build.gradle.kts`'s `id("com.netpress.kotidy")`** (the shared test-output
  tree renderer -- see `~/workspace/kotidy`'s own `docs/COWORK.md`) used to
  need the same sibling-directory treatment as `humane-kotlin` above, via
  `pluginManagement { includeBuild("../kotidy") }`. `com.netpress.kotidy` is
  now approved and live on the Gradle Plugin Portal, so it's pinned as a
  normal versioned plugin (`id("com.netpress.kotidy") version "0.1.0"`,
  resolved via `gradlePluginPortal()` in `settings.gradle.kts`) -- no sibling
  checkout of `kotidy` needed anymore, unlike `humane-kotlin`.

## Not yet done

- `ScanGridView`'s cell sizing/spacing/footer layout hasn't had the same
  pixel-for-pixel side-by-side pass against zouk that
  `HostEntryView`/`ConnectingView` already got.
