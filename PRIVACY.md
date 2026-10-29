# Privacy Policy

**InstaTube** (`com.roshan.instatube`), branded for **Lazy Mayank · XBOREX**.

## What we collect

InstaTube contains no analytics, advertising SDKs, telemetry, or third-party tracking SDKs. The optional floating shortcut uses Android's Usage Access locally to detect whether Instagram is currently foregrounded; that app-usage event data is not uploaded or shared by InstaTube.

## How data is handled on your device

- Instagram and YouTube links you explicitly paste or share are resolved from your device to fetch media streams.
- Downloaded videos, audio tracks, and cover images are saved locally to your device (`Downloads/InstaTube` by default, or your chosen folder).
- App preferences and optional YouTube sign-in credentials are stored locally on your device.
- When the optional floating assistant is enabled, InstaTube observes clipboard-change events locally but does not read clipboard contents while in the background. It only shows its shortcut if Instagram is foreground and a clipboard change has recently occurred. Clipboard content is inspected only after you tap the shortcut and InstaTube has come to the foreground. The assistant does not use an Accessibility Service and does not read other apps' screens.
- The overlay is shown only while Instagram (`com.instagram.android`) is detected as the foreground app. It requires the separate **Display over other apps** and **Usage Access** settings, is off by default, can be disabled in Settings, and has a persistent notification with a stop action.
- Download progress is shown in Android notifications when notifications are allowed. Notification permission can be changed at any time in system settings.

## Permissions

- **Internet & Network State**: Fetch media streams and detect Data Saver mode.
- **Storage / document-tree access**: Save downloads to the folder you select.
- **Notifications & foreground services**: Show download progress/results and the optional link assistant's active status.
- **Display over other apps**: Show the user-enabled floating shortcut only over Instagram.
- **Usage Access**: Detect the foreground app locally so the shortcut does not appear over unrelated apps.
- **Vibrate**: Provide tactile haptic feedback.

## Control and retention

Turn off **Floating Instagram assistant** in InstaTube Settings to remove the overlay and stop monitoring. Revoking either Android permission also disables the assistant. Clearing the app's data removes local preferences; downloaded files remain in the chosen download folder until you delete them.
