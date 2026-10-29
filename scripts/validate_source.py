#!/usr/bin/env python3
"""Fast, dependency-free preflight checks for InstaTube's Android source tree."""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
MAIN = APP / "src" / "main"
JAVA = MAIN / "java"
RES = MAIN / "res"
MANIFEST = MAIN / "AndroidManifest.xml"
STRINGS = RES / "values" / "strings.xml"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"VALIDATION FAILED: {message}")


def main() -> int:
    xml_files = sorted(RES.rglob("*.xml"))
    for path in xml_files:
        ET.parse(path)
    ET.parse(MANIFEST)

    strings_text = STRINGS.read_text(encoding="utf-8")
    invalid_escapes = re.findall(
        r"(?<!\\)\\u(?:\{[^}]*\}|(?![0-9A-Fa-f]{4}(?![0-9A-Fa-f])))",
        strings_text,
    )
    require(not invalid_escapes, f"invalid Android Unicode escape(s): {invalid_escapes}")

    resource_names: set[str] = set()
    for path in RES.rglob("*.xml"):
        tree = ET.parse(path)
        for node in tree.iter():
            name = node.attrib.get("name")
            if name:
                resource_names.add(name)

    string_refs: list[tuple[Path, str]] = []
    kotlin_files = sorted(JAVA.rglob("*.kt"))
    package_mismatches: list[str] = []
    for path in kotlin_files:
        content = path.read_text(encoding="utf-8")
        string_refs.extend((path, name) for name in re.findall(r"R\.string\.([A-Za-z0-9_]+)", content))
        match = re.search(r"^package\s+([\w.]+)", content, re.MULTILINE)
        expected_package = ".".join(path.relative_to(JAVA).parent.parts)
        if not match or match.group(1) != expected_package:
            package_mismatches.append(f"{path.relative_to(ROOT)}: expected {expected_package}")

    missing_strings = sorted({name for _, name in string_refs if name not in resource_names})
    require(not missing_strings, f"unresolved R.string references: {missing_strings}")
    require(not package_mismatches, "Kotlin package/path mismatch: " + "; ".join(package_mismatches))

    manifest = ET.parse(MANIFEST).getroot()
    android_ns = "{http://schemas.android.com/apk/res/android}"
    permissions = {entry.get(android_ns + "name") for entry in manifest.findall("uses-permission")}
    app_node = manifest.find("application")
    require(app_node is not None, "AndroidManifest.xml has no <application>")
    services = {entry.get(android_ns + "name") for entry in app_node.findall("service")}
    for permission in (
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.PACKAGE_USAGE_STATS",
    ):
        require(permission in permissions, f"manifest permission missing: {permission}")
    for service in (".QuickDownloadService", ".DownloadProgressService", ".LinkAssistantService"):
        require(service in services, f"manifest service missing: {service}")

    source_text = "\n".join(path.read_text(encoding="utf-8") for path in kotlin_files)
    require(source_text.count("InlineDownloadNotifications.start") >= 6, "per-save notification hooks are missing")
    require("fun pauseAllActive()" in (JAVA / "com/roshan/instatube/Downloads.kt").read_text(encoding="utf-8"),
            "queue pause-on-timeout implementation is missing")
    require("Downloads.pauseAllActive()" in (JAVA / "com/roshan/instatube/DownloadProgressService.kt").read_text(encoding="utf-8"),
            "queue timeout handler does not pause active work")
    require((ROOT / "app/src/test/java/com/roshan/instatube/DownloadProgressMathTest.kt").is_file(),
            "download progress regression tests are missing")
    policy_test = ROOT / "app/src/test/java/com/roshan/instatube/LinkAssistantPolicyTest.kt"
    require(policy_test.is_file(), "overlay/contrast regression tests are missing")
    policy = (JAVA / "com/roshan/instatube/LinkAssistantPolicy.kt").read_text(encoding="utf-8")
    service = (JAVA / "com/roshan/instatube/LinkAssistantService.kt").read_text(encoding="utf-8")
    main_activity = (JAVA / "com/roshan/instatube/MainActivity.kt").read_text(encoding="utf-8")
    require("enabled && isInstagramPackage(foregroundPackage)" in policy,
            "floating shortcut still incorrectly depends on clipboard callback")
    require("overlaySubtitleView?.text = LinkAssistantPolicy.subtitleForClipboardState(clipboardChangeIsRecent)" in service,
            "floating assistant clipboard status update is missing")
    require("inspectClipboardForSupportedLink(force = true)" in main_activity,
            "overlay clipboard handoff is missing")
    require("shortcutAppearsWhileInstagramIsForegroundEvenIfClipboardListenerDidNotFire" in policy_test.read_text(encoding="utf-8"),
            "floating assistant fallback regression test is missing")

    # User supplied IconKitchen export must fully replace the packaged launcher resource set.
    for density in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
        density_dir = RES / f"mipmap-{density}"
        for icon in ("ic_launcher.png", "ic_launcher_round.png", "ic_launcher_background.png",
                     "ic_launcher_foreground.png", "ic_launcher_monochrome.png"):
            require((density_dir / icon).is_file(), f"IconKitchen launcher asset missing: {density}/{icon}")
        require(not list(density_dir.glob("ic_launcher*.webp")),
                f"old WebP launcher resource remains and may conflict: {density}")
    for icon_xml in (RES / "mipmap-anydpi-v26/ic_launcher.xml", RES / "mipmap-anydpi-v26/ic_launcher_round.xml"):
        require(icon_xml.is_file(), f"adaptive launcher XML missing: {icon_xml.relative_to(ROOT)}")
        icon_text = icon_xml.read_text(encoding="utf-8")
        require("@mipmap/ic_launcher_foreground" in icon_text and "@mipmap/ic_launcher_background" in icon_text,
                f"adaptive launcher does not use the IconKitchen mipmaps: {icon_xml.relative_to(ROOT)}")

    settings = (JAVA / "com/roshan/instatube/SettingsScreen.kt").read_text(encoding="utf-8")
    for url in (
        "https://github.com/xboyrex",
        "https://github.com/xboyrex/InstaTube",
        "https://t.me/RoranoaxZORO",
        "https://mayank-droid.pages.dev",
        "https://onyx-services.pages.dev",
        "https://otaku-hub-pro.pages.dev",
        "https://portfolio-hbo.pages.dev",
        "https://mi-unlock.pages.dev",
    ):
        require(url in settings, f"maintainer link is missing: {url}")

    gradle = (APP / "build.gradle.kts").read_text(encoding="utf-8")
    app_id = re.search(r'applicationId\s*=\s*"([^"]+)"', gradle)
    version = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    version_code = re.search(r'versionCode\s*=\s*(\d+)', gradle)
    require(app_id is not None and app_id.group(1) == "com.roshan.instatube", "unexpected applicationId")
    require(version is not None and version.group(1) == "3.0.0", "versionName/release baseline mismatch")
    require(version_code is not None and int(version_code.group(1)) == 300,
            "versionCode/release baseline mismatch")
    version_ui = (JAVA / "com/roshan/instatube/InstaTubeScreen.kt").read_text(encoding="utf-8")
    require('text = "v${BuildConfig.VERSION_NAME}"' in version_ui,
            "home version badge is not connected to the actual app version")
    brand_mark = (JAVA / "com/roshan/instatube/AppIcons.kt").read_text(encoding="utf-8")
    require('painterResource(R.drawable.instatube_brand_logo)' in brand_mark,
            "in-app brand mark is not using the IconKitchen bitmap asset")
    require((RES / 'drawable-nodpi/instatube_brand_logo.png').is_file(),
            "IconKitchen in-app bitmap is missing")
    updater = (JAVA / "com/roshan/instatube/Updates.kt").read_text(encoding="utf-8")
    require("compareAppVersions(archiveVersion, update.version) != 0" in updater,
            "downloaded release APK is not checked against its GitHub tag")
    require("archiveVersionCode <= BuildConfig.VERSION_CODE.toLong()" in updater,
            "downloaded release APK is not checked for a higher versionCode")
    require("signingFingerprints(archive) != signingFingerprints(installed)" in updater,
            "downloaded release APK is not checked for signing-certificate continuity")
    require((ROOT / "app/src/test/java/com/roshan/instatube/VersionComparisonTest.kt").is_file(),
            "version comparison regression tests are missing")
    require('painterResource(R.drawable.instatube_brand_logo)' in updater,
            "update dialog is not using the IconKitchen brand bitmap")

    print(f"PASS: {len(xml_files) + 1} XML documents parsed")
    print(f"PASS: {len(string_refs)} R.string references; no missing string resources")
    print(f"PASS: {len(kotlin_files)} Kotlin files have matching package paths")
    print("PASS: Unicode escapes, foreground-service permissions, overlay fallback, IconKitchen assets, feature wiring, update comparison/asset validation, tests, links, and version metadata")
    return 0


if __name__ == "__main__":
    sys.exit(main())
