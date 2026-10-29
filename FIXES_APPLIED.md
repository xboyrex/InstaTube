# InstaTube 3.0.0 — Icon and OTA update fixes

## Branding and icon assets
- Replaced the app's in-screen `StudioBrandMark` drawing with the same IconKitchen bitmap used by the launcher.
- The home header, settings header, and update dialog now use `drawable-nodpi/instatube_brand_logo.png`.
- Re-copied the IconKitchen launcher, background, foreground, monochrome and round icons for mdpi through xxxhdpi; the adaptive icon XML remains wired to these resources.
- Replaced the hard-coded `2.0` home badge with `BuildConfig.VERSION_NAME`.

## Version/update behavior
- Baseline fixed at `versionName = 3.0.0`, `versionCode = 300`.
- Added SemVer comparison including stable-vs-prerelease ordering, normalized v/V prefixes, build metadata, and invalid tag rejection.
- Equal/older releases clear stale update dialogs; only a semantically newer stable release is offered.
- Update notes are normalized from GitHub Markdown, and the release-notes action opens the corresponding GitHub release page.
- Before opening Android's installer, the downloaded APK must have the same package ID and signing certificate, its versionName must match the release tag, and both versionName and versionCode must be newer than the installed build.
- The workflow rejects release tags that do not match Gradle `versionName`.
- Optional persistent signing is wired through repository secrets; without them, builds use debug signing and are suitable only for development. Public in-place updates require a consistent signing certificate.

## Validation
- `python3 scripts/validate_source.py`: PASS.
- Workflow YAML parse and embedded shell syntax: PASS.
- Standalone Kotlin compile/run for SemVer and release-note logic: PASS.
- IconKitchen asset hashes match all five Android density sets: PASS.
- XML resources and Kotlin package/path checks: PASS.
- Gradle/JUnit: NOT RUN. The wrapper cannot download its Gradle distribution because `services.gradle.org` DNS resolution fails in this environment.
