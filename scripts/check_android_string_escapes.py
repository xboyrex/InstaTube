#!/usr/bin/env python3
"""Validate Android resource value escapes in every local Gradle module.

Scans source resources only (*/src/main/res/values*/*.xml), not generated build
outputs. Backslash escape validation is performed left-to-right so ``\\u`` is
understood as an escaped literal backslash followed by ``u``, not a Unicode
escape. Exit status is nonzero if XML parsing or any escape validation fails.
"""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# Android resource strings commonly use these escapes. @ and ? can be escaped
# to prevent resource/interrogation syntax from being interpreted specially.
SIMPLE_ESCAPES = {"\\", "'", '"', "n", "t", "@", "?"}
HEX_DIGITS = frozenset("0123456789abcdefABCDEF")
VALUE_TAGS = {"string", "item", "plurals"}


def validate_escapes(value: str) -> list[str]:
    problems: list[str] = []
    index = 0
    while index < len(value):
        if value[index] != "\\":
            index += 1
            continue

        escape_offset = index
        if index + 1 >= len(value):
            problems.append(f"trailing backslash at value offset {escape_offset}")
            break

        escaped = value[index + 1]
        if escaped in SIMPLE_ESCAPES:
            index += 2
            continue

        if escaped == "u":
            digits = value[index + 2 : index + 6]
            if len(digits) == 4 and all(char in HEX_DIGITS for char in digits):
                index += 6
                continue
            problems.append(
                f"invalid \\u escape at value offset {escape_offset}: "
                f"{value[escape_offset:escape_offset + 12]!r}; expected four hex digits"
            )
            # Consume only the escape introducer. This lets the validator find
            # another malformed escape later in the same value.
            index += 2
            continue

        problems.append(
            f"unsupported escape \\{escaped} at value offset {escape_offset}; "
            "use a valid Android escape or double the backslash"
        )
        index += 2

    return problems


def resource_text(element: ET.Element) -> str:
    """Return all visible text, including text nested inside formatting tags."""
    return "".join(element.itertext())


def iter_source_values_dirs() -> list[Path]:
    found: set[Path] = set()
    # Discover resources in every checked-out Gradle module without relying on
    # a fixed list such as app/ and ui/.
    for source_res in ROOT.rglob("src/main/res"):
        if any(part in {".git", ".gradle", "build", ".idea"} for part in source_res.parts):
            continue
        for values_dir in source_res.glob("values*"):
            if values_dir.is_dir():
                found.add(values_dir)
    return sorted(found)


def validate_file(path: Path) -> list[str]:
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError) as exc:
        return [f"{path.relative_to(ROOT)}: could not parse resource XML: {exc}"]

    problems: list[str] = []
    for element in root.iter():
        if element.tag not in VALUE_TAGS:
            continue
        # Skip a plural parent: its child <item> entries are validated on their
        # own, so an error is attributed to the individual plural quantity.
        if element.tag == "plurals":
            continue
        name = element.attrib.get("name", "<unnamed>")
        label = f"{path.relative_to(ROOT)} ({element.tag}/{name})"
        for problem in validate_escapes(resource_text(element)):
            problems.append(f"{label}: {problem}")
    return problems


def main() -> int:
    values_dirs = iter_source_values_dirs()
    xml_files = sorted({file for directory in values_dirs for file in directory.glob("*.xml")})
    issues: list[str] = []
    checked_values = 0

    for xml_path in xml_files:
        file_issues = validate_file(xml_path)
        if file_issues and any("could not parse resource XML" in issue for issue in file_issues):
            issues.extend(file_issues)
            continue
        issues.extend(file_issues)
        try:
            root = ET.parse(xml_path).getroot()
        except ET.ParseError:
            continue
        checked_values += sum(1 for node in root.iter() if node.tag in {"string", "item"})

    if issues:
        print("Android resource escape validation FAILED:", file=sys.stderr)
        print("\n".join(f" - {issue}" for issue in issues), file=sys.stderr)
        print(f"Checked {len(xml_files)} resource XML file(s); {len(issues)} problem(s).", file=sys.stderr)
        return 1

    print(
        f"Android resource escape validation passed: {len(xml_files)} XML file(s), "
        f"{checked_values} string/item value(s), all discovered local modules."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
