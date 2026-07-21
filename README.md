# huck

Windows client for scan servers.

A Kotlin/Compose Multiplatform Desktop port of
[`zouk`](https://github.com/woodie/zouk)'s scan-client feature set, targeting
Windows specifically (packaged as a real `.msi`, no Microsoft Store
submission required). A first real vertical slice is ported and confirmed
building/launching from a real `.msi`; the rest of `zouk`'s feature set
(real thumbnails, the remaining views) is still stubbed or not yet ported.

## Requirements

JDK 17+ (needed for `jpackage`'s native distribution packaging -- JDK 11
isn't enough).
[`humane-kotlin`](https://github.com/woodie/humane-kotlin) checked out as a
sibling directory (`../humane-kotlin` relative to this repo) -- this repo
depends on it via a Gradle composite build (`includeBuild("../humane-kotlin")`
in `settings.gradle.kts`), not a published artifact, so it has to exist on
disk to build at all.

## Development

```
make build    # ./gradlew build -x test
make test     # ./gradlew test
make lint     # ./gradlew ktlintCheck
make check    # ./gradlew check
make package  # ./gradlew packageDistributionForCurrentOS -- .dmg on macOS
```

A real Windows `.msi` is built in CI (`.github/workflows/windows-package.yml`)
on a `windows-latest` GitHub Actions runner -- a real, ephemeral Windows
machine (free and uncapped for a public repo), so packaging the `.msi`
(which shells out to the WiX Toolset, and has to run on the platform it
targets) doesn't require owning Windows hardware.

## Reference implementation

[`zouk`](https://github.com/woodie/zouk) (Swift) is the feature reference
this should eventually match.
