# InstaTube

A fast, open-source Android client and media downloader for **Instagram** and **YouTube** built with a modern **Liquid Glass UI** (`Android 7.0` up to `Android 17`).

## Features

- **Universal Link Paste & Share Target**: Paste or share any Instagram or YouTube link directly into InstaTube.
- **Instagram Downloader**:
  - Video & Reel download in original resolution (`.mp4`)
  - Audio extraction & download (`.m4a`)
  - High-resolution Cover / Thumbnail download (`.jpg` with EXIF metadata)
  - One-tap Copy Caption & `@handle`
  - Carousel multi-select & bulk Profile Browser
- **YouTube & YouTube Music Suite**:
  - Multi-threaded video download (`144p` up to `4K 2160p`) and high-bitrate audio download (`.m4a`)
  - High-resolution YouTube Cover download & Copy Caption/Description
  - Ad-free YouTube playback, Shorts feed, and YouTube Music with synced Apple Music-style lyrics
  - SponsorBlock integration, background playback, Picture-in-Picture (PiP), and Android TV support
- **Liquid Glass Interface**: Real-time backdrop blur, lens refraction, chromatic aberration, and spring physics across navigation, sheets, and player controls.

## Build

```bash
./gradlew :app:assembleRelease
```

The signed release APK is written to `app/build/outputs/apk/release/app-release.apk`.

## Author

Developed by **Roshan**. Licensed under the [GPLv3 License](LICENSE).
