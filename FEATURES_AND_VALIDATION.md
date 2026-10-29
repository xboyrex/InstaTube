# InstaTube 3.0.0 — final preflight report

- IconKitchen assets are wired to both Android launcher resources and in-app brand marks.
- Home version badge reads the Gradle-generated app version; no hard-coded `2.0` badge remains.
- Update detection uses semantic version precedence and rejects equal/older versions.
- Downloaded APK validation checks application ID, release tag/versionName match, signing-certificate continuity, and a higher versionCode before installation.
- GitHub release tags are checked against Gradle `versionName`.
- Release-note Markdown is cleaned before display; a dedicated action opens the release page.
- Required maintainer links, resource strings, Kotlin package paths, foreground service configuration, and IconKitchen files are checked by `scripts/validate_source.py`.

## Checks run

- `python3 scripts/validate_source.py`: PASS.
- YAML parse and `bash -n` on workflow run blocks and push script: PASS.
- Standalone Kotlin tests for version comparison/release-note cleanup: PASS.
- Exact IconKitchen asset equality for mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi: PASS.
- ZIP integrity checks: performed after packaging.
- Gradle JUnit / APK build: blocked because `services.gradle.org` could not resolve DNS from this environment.

## Production update note

Set these repository secrets before publishing tagged production releases if a persistent signing key is available: `INSTATUBE_RELEASE_KEYSTORE_BASE64`, `INSTATUBE_RELEASE_KEYSTORE_PASSWORD`, `INSTATUBE_RELEASE_KEY_ALIAS`, and `INSTATUBE_RELEASE_KEY_PASSWORD`. Never commit the keystore. Debug-signed artifacts may not update an installed APK signed by a different certificate.
