# Delivering a huck build

There's one way to ship a build: tag a release, which `.github/workflows/
windows-package.yml` builds on a real Windows runner and attaches the
`.msi` to a GitHub Release. Unlike zouk's signed and notarized `.app`/
`.pkg`, huck's `.msi` is **not code-signed** -- see "Code signing" below
for why, and what it means for anyone installing it.

## What the release build produces

Pushing a `vX.Y.Z` tag triggers `windows-package.yml`, which runs
`.\gradlew.bat packageMsi` on a real `windows-latest` GitHub Actions
runner (jpackage shells out to the WiX Toolset, which has to run on the
platform it targets) and attaches the resulting `.msi` to a new GitHub
Release, using `docs/releases/<tag>.md` as the release notes body.

## Code signing: deferred, on purpose

The `.msi` isn't code-signed. Windows SmartScreen may show an "Unknown
publisher" warning on first run -- click **More info** then **Run
anyway** to continue. This was a deliberate call, not an oversight:

- A traditional OV/EV certificate runs $200-500/year, and since June 2023
  CA/Browser Forum rules require the private key live on a hardware
  token or HSM rather than a plain file -- awkward from an ephemeral
  GitHub Actions runner without a cloud-HSM-backed CA (DigiCert
  KeyLocker, SSL.com eSigner).
- Azure Artifact Signing (formerly "Trusted Signing") is the cheaper,
  CI-friendly alternative (~$10/month, no hardware token, an official
  GitHub Action, and it directly supports signing `.msi` files), with
  identity verified against an individual's Azure billing account for a
  US/Canada-based solo developer.
- Either way, signing no longer buys an instant "no warning" first
  launch: Microsoft removed EV's SmartScreen-bypass privilege in 2024, so
  both OV and EV now build reputation the same way, based on download
  volume and publisher history over time. An unsigned file never builds
  any reputation at all; a freshly signed one still takes a while.

Given huck is a personal tool with a small audience, the cost and
identity-verification overhead (submitting ID documents, waiting on
Microsoft's review) wasn't judged worth it as of this writing. Revisit if
usage ever grows past "a few people I know."

## Pre-flight checklist for cutting a release

- [ ] Commit and push all changes (`git status` should be clean).
- [ ] `make check` passes -- this is what actually gates a clean run now
      (see `docs/COWORK.md`'s "`make check` now actually terse" entry for
      why that matters).
- [ ] Bump `build.gradle.kts`'s `version` and `packageVersion` to the new
      tag -- both must match, and both must match the tag below, or the
      packaged filename and the release drift out of sync.
- [ ] `git checkout main && git pull`, then **`git log -1` right before
      tagging** and actually read the commit it shows -- tagging from a
      stale local `main` ships whatever that commit was, not what you
      think you're releasing (see zouk's own `docs/DELIVERY.md` for the
      `v1.6.0`/`v1.7.0` incidents this guards against).
- [ ] Write `docs/releases/vX.Y.Z.md` (see existing files for the
      template) and commit it -- `windows-package.yml`'s release step
      reads this via `body_path` instead of auto-generating notes. It has
      to exist *before* the tag is pushed: tagging triggers the workflow
      immediately, with no manual step afterward to attach notes with.
- [ ] Tag the release (annotated, `vX.Y.Z`; see existing tags for style)
      and push the tag -- this triggers `.github/workflows/
      windows-package.yml`, which builds the `.msi` on a real Windows
      runner and attaches it to a GitHub Release.
- [ ] Once that run finishes, check the Actions tab for a green run and
      confirm the Release page shows the `.msi` attached with the right
      release notes body.
