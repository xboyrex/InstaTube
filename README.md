# InstaTube 3.0.0

**InstaTube** (`v3.0.0`) is a native Android media extraction studio for **Instagram** and **YouTube**, crafted with Jetpack Compose.

## Key Capabilities
- **Smart Clipboard Guard**: Automatically detects copied Instagram or YouTube links on launch and loads stream metadata, with optional **Instant Auto-Download**.
- **Instagram Engine**:
  - Save Reels, Videos, Photos, and Multi-Slide Carousels in original quality.
  - Direct `.m4a` audio stream extraction without re-encoding.
  - High-resolution `.jpg` cover artwork download & one-tap caption/hashtag copy.
  - Built-in public `@profile` browser with batch downloading.
- **YouTube Engine**:
  - Multi-threaded stream downloader with automatic video+audio muxing (`144p` up to `4K`).
  - Direct `.m4a` audio track download & max-resolution `.jpg` frame extraction.
  - Full support for YouTube Videos, Shorts, and `youtu.be` links.
- **Android 7.0 – 17 Ready**: Supports `minSdk = 24` through `compileSdk = 37` (`targetSdk = 36`).

## Developer & Ecosystem
- **Brand / Maintainer**: Lazy Mayank · まやんく · XBOREX ([GitHub](https://github.com/xboyrex))
- **Repository**: [github.com/xboyrex/InstaTube](https://github.com/xboyrex/InstaTube)
- **Telegram**: [t.me/RoranoaxZORO](https://t.me/RoranoaxZORO)
- **Mayank Droid**: [mayank-droid.pages.dev](https://mayank-droid.pages.dev)
- **Onyx Services**: [onyx-services.pages.dev](https://onyx-services.pages.dev)
- **Otaku Hub Pro**: [otaku-hub-pro.pages.dev](https://otaku-hub-pro.pages.dev)
- **Portfolio HBO**: [portfolio-hbo.pages.dev](https://portfolio-hbo.pages.dev)
- **Mi Unlock**: [mi-unlock.pages.dev](https://mi-unlock.pages.dev)


## Optional automation

- **Download notifications:** Android 13+ notification permission is requested in context. Long-running queue downloads show a persistent progress notification plus a separate result notification; direct in-app Instagram saves and audio/cover exports report item progress and completion too.
- **Floating Instagram assistant:** Opt-in only. It needs “Display over other apps” and “Usage access”. A draggable shortcut appears while Instagram is foreground after a recent clipboard-change event. Tap the shortcut to bring InstaTube forward; clipboard text is inspected only then, never by the background service.
- **Share-to-app:** Android's standard Share sheet remains available and is the most reliable path when the source app offers the link through `ACTION_SEND`.
- **Privacy:** No Accessibility Service is used. The assistant observes a clipboard-change event, not clipboard contents, while backgrounded. Disable it in Settings or via its persistent notification.
- **Progress accuracy:** Byte percentages are shown only when all active queue items have known sizes; otherwise the notification remains indeterminate. YouTube links newly enqueued into InstaTube are reported as queued, not falsely marked as already downloaded.

## Maintainer links

- GitHub: https://github.com/xboyrex
- Repository: https://github.com/xboyrex/InstaTube
- Telegram: https://t.me/RoranoaxZORO
- Mayank Droid: https://mayank-droid.pages.dev
- Onyx Services: https://onyx-services.pages.dev
- Otaku Hub Pro: https://otaku-hub-pro.pages.dev
- Portfolio HBO: https://portfolio-hbo.pages.dev
- Mi Unlock: https://mi-unlock.pages.dev

## Versioning and signed updates

The current release baseline is `3.0.0` (`versionCode = 300`). The in-app updater only prompts for a newer semantic version; equal or older release tags do not trigger a dialog. The downloaded APK must match the release tag, application ID, signing certificate, and a higher version code before Android's installer is launched.

For tagged production releases, set these GitHub Actions repository secrets to use a persistent private signing key: `INSTATUBE_RELEASE_KEYSTORE_BASE64`, `INSTATUBE_RELEASE_KEYSTORE_PASSWORD`, `INSTATUBE_RELEASE_KEY_ALIAS`, and `INSTATUBE_RELEASE_KEY_PASSWORD`. Never commit the keystore or its passwords. Without these secrets, CI falls back to debug signing for development artifacts; public APK updates can only install over an existing app if their signing certificate matches.
