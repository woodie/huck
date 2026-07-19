# huck

Windows client for scan servers.

A Kotlin/Compose Multiplatform Desktop port of
[`zouk`](https://github.com/woodie/zouk)'s scan-client feature set, targeting
Windows specifically (packaged as a real `.msi`, no Microsoft Store
submission required). Currently a skeleton -- see `docs/COWORK.md` for
current status and what's not built yet.

## Requirements

JDK 17+ (needed for `jpackage`'s native distribution packaging).
[`humane-kotlin`](https://github.com/woodie/humane-kotlin) checked out as a
sibling directory (`../humane-kotlin` relative to this repo) -- this repo
depends on it via a Gradle composite build, not a published artifact. See
`docs/COWORK.md` for why.

## Development

```
make build    # ./gradlew build -x test
make test     # ./gradlew test
make lint     # ./gradlew ktlintCheck
make check    # ./gradlew check
make package  # ./gradlew packageDistributionForCurrentOS -- .dmg on macOS
```

A real Windows `.msi` is built in CI (`.github/workflows/windows-package.yml`),
on a `windows-latest` GitHub Actions runner -- see `docs/COWORK.md` for why
that doesn't require owning Windows hardware.

## Reference implementation

[`zouk`](https://github.com/woodie/zouk) (Swift) is the feature reference
this should eventually match. `docs/COWORK.md` tracks what's ported so far.
