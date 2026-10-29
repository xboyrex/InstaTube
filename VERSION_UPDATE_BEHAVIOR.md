# Version and update behavior — InstaTube 3.0.0

- The installed baseline is `versionName = 3.0.0`, `versionCode = 300`.
- The home-screen version badge reads `BuildConfig.VERSION_NAME`; it is not a hard-coded label.
- The GitHub release dialog appears only when the newest stable release version is semantically newer than the installed version.
- Equal or older releases hide stale prompts. Manual checks tell the user when they are already current.
- Downloaded APKs are checked for the same application ID, a version name matching the release tag, a version newer than the installed app, and a higher version code before the installer opens.
- The release tag must match `versionName`; future releases must also increase `versionCode` (for example, `3.0.1` with a code greater than `300`).
- The release APK must be signed with the same signing certificate as the installed app for Android to accept it as an in-place update. The project currently uses the Gradle debug signing config; for long-lived public releases, configure a persistent private release keystore in CI and keep its credentials in GitHub Actions secrets. Never commit the keystore or passwords.
