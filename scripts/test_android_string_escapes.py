#!/usr/bin/env python3
"""Regression tests for scripts/check_android_string_escapes.py."""
from __future__ import annotations

import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_android_string_escapes import validate_escapes  # noqa: E402


class AndroidStringEscapeTests(unittest.TestCase):
    def test_valid_unicode_escape(self) -> None:
        self.assertEqual(validate_escapes(r"Letter: \u0041"), [])

    def test_valid_escaped_literal_u(self) -> None:
        self.assertEqual(validate_escapes(r"Documentation: \\u{str}"), [])

    def test_rejects_unicode_escape_with_braces(self) -> None:
        self.assertTrue(validate_escapes(r"Custom \u{quality}"))

    def test_rejects_windows_user_path(self) -> None:
        self.assertTrue(validate_escapes(r"Path: C:\Users\Videos"))

    def test_rejects_short_unicode_escape(self) -> None:
        self.assertTrue(validate_escapes(r"Code: \u12"))

    def test_rejects_uppercase_unicode_escape_prefix(self) -> None:
        self.assertTrue(validate_escapes(r"Code: \U1234"))

    def test_rejects_regex_escape(self) -> None:
        self.assertTrue(validate_escapes(r"Pattern: \d+"))

    def test_accepts_supported_simple_escapes(self) -> None:
        self.assertEqual(validate_escapes("Line1\\nLine2\\t\\\\\\'\\\""), [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
