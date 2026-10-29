# InstaTube 3.0.0 — IconKitchen replacement and floating assistant fix

## Icon replacement
- Integrated the supplied `IconKitchen-Output (3).zip` Android launcher export, not a newly generated substitute.
- Replaced old density-specific launcher WebP assets with IconKitchen PNGs at mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi.
- Updated adaptive and round adaptive launcher XML to use the IconKitchen foreground, background, and monochrome assets.
- Added pre-Android 8 launcher fallbacks; removed superseded in-project launcher vector drawables.

## Floating assistant
- The previous policy required both Instagram foreground detection and a clipboard-change event before it displayed. Some OEMs limit background clipboard callbacks, making the shortcut never appear.
- New behavior: when the user has explicitly enabled the assistant and granted overlay plus Usage Access, the shortcut appears while Instagram or Instagram Lite is foreground even if the clipboard callback was missed.
- The subtitle changes to “Clipboard changed · tap to check” when a recent clipboard event is observed. The service never reads clipboard text; the actual clipboard is read only after the user taps the shortcut and InstaTube is foreground.
- Fixed the `singleTask` `MainActivity` handoff: `onNewIntent()` now schedules the one-shot clipboard request when window focus exists; the existing focus callback remains the fallback.
- Added unit-policy regression tests for visibility independent of clipboard listener success, disabled mode, non-Instagram foreground apps, and the status hint.

## Validation
- `scripts/validate_source.py` checks resource XML, Kotlin package paths, string refs, required service permissions, IconKitchen assets in every density bucket, overlay fallback policy, and maintained links/version.
- Android runtime behavior still needs device verification with Overlay permission and Usage Access enabled.
