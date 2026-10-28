# InstaTube Definitive Resource Fix Report

## Changes made

1. Replaced the three named app resource values with minimal safe text to remove any possibility of malformed escape text in these declarations:
   - `quality_custom_description` → `Custom quality override`
   - `story_not_supported_title` → `Not supported`
   - `story_not_supported_body` → `Stories are not supported in this version.`
2. Rewrote `scripts/check_android_string_escapes.py` to scan every local module's `src/main/res/values*/*.xml` resources, parse XML, process backslashes left-to-right, detect malformed `\\u` escapes and other unsupported escapes, and exit nonzero on errors.
3. Added `scripts/test_android_string_escapes.py` with eight regression tests covering valid Unicode escapes, escaped literal `\\u`, malformed brace/short/uppercase Unicode escapes, Windows-style paths, regex escapes, and supported simple escapes.
4. Updated `.github/workflows/build.yml` to run the regression tests and validator before a clean Gradle build. A failure diagnostic step prints the checked-out commit, included Gradle modules, resource declarations, and values XML file list.
5. Removed generated Python bytecode from the source archive.

## Source-tree mismatch

The supplied ZIP's `settings.gradle.kts` includes only `:app`; it contains no `ui/` module and no `ui/src/main/res/values/strings.xml`. Therefore, this archive cannot directly patch a resource in a separate `ui` module referenced by an older or different build log. The app's `story_not_supported_title` was set to the safe placeholder, and the workflow now reports the module/resource inventory on failure to help identify a stale checkout or differing project layout.

## Validation results

- `python3 scripts/test_android_string_escapes.py`: 8 tests passed.
- `python3 scripts/check_android_string_escapes.py`: passed; 4 values XML files and 150 string/item value entries checked.
- Source XML parse check: passed.
- ZIP archive integrity: verified after packaging.
- Release build: **not verified**. `./gradlew clean :app:assembleRelease --no-daemon --stacktrace` was attempted, but the Gradle wrapper could not download Gradle 9.7.1 because DNS resolution for `services.gradle.org` failed (`UnknownHostException`).
