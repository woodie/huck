# Developing huck

Building from source and the project layout. For context on the
project's history, see [docs/COWORK.md](COWORK.md).

## Requirements

JDK 17+ (needed for `jpackage`'s native distribution packaging -- JDK 11
isn't enough). Only matters for building from source -- the packaged
`.msi` bundles its own JDK, so end users installing that don't need one.

[`humane-kotlin`](https://github.com/woodie/humane-kotlin) checked out as a
sibling directory (`../humane-kotlin` relative to this repo) -- this repo
depends on it via a Gradle composite build (`includeBuild("../humane-kotlin")`
in `settings.gradle.kts`), not a published artifact, so it has to exist on
disk to build at all.

## Building

```
make build    # ./gradlew ktlintFormat && ./gradlew build -x test
make test     # ./gradlew ktlintFormat && ./gradlew clean test -- verbose, full nested tree
make lint     # ./gradlew ktlintCheck
make check    # ./gradlew ktlintFormat && ./gradlew clean check -- terse, PASS on success
make package  # ./gradlew packageDistributionForCurrentOS -- .dmg on macOS
```

## Layout

- `src/main/kotlin/com/netpress/huck` -- model (`AppModel`), networking
  (`ScanClient`), data (`ScanEntry`), and `ui/` (`ContentView`,
  `HostEntryView`, `ScanGridView`, `ConnectingView`).
- `src/test/kotlin/com/netpress/huck` -- Kotest specs for the above.
- `docs/COMMENTS.md` -- rationale for non-obvious code, organized by file.
- `docs/COWORK.md` -- context for picking this project back up cold.
- `docs/releases/<tag>.md` -- hand-written release notes, read by
  `.github/workflows/windows-package.yml` on a tagged release.
- `.github/workflows/windows-package.yml` -- builds a real Windows `.msi`
  on a `windows-latest` GitHub Actions runner on every push/PR to `main`;
  on a pushed `vX.Y.Z` tag, also attaches that `.msi` to a GitHub Release
  using the matching `docs/releases/<tag>.md` as the release body.

## Reference implementation

[`zouk`](https://github.com/woodie/zouk) (Swift) is the feature reference
this should eventually match pixel-for-pixel; see its own `docs/COWORK.md`
and `docs/COMMENTS.md` for the source of truth on what the real client does.
