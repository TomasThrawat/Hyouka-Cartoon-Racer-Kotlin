#!/usr/bin/env python3
"""CI verifier for Hyouka Cartoon Racer Android builds.

Standard-library only. It validates build/test/smoke logs and the generated APK,
then writes a machine-readable JSON report for GitHub Actions artifacts.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from pathlib import Path


HARD_FAILURE_PATTERNS = (
    re.compile(r"FAILURE:\s+Build failed", re.IGNORECASE),
    re.compile(r"BUILD FAILED", re.IGNORECASE),
    re.compile(r"Execution failed for task", re.IGNORECASE),
    re.compile(r"FATAL EXCEPTION", re.IGNORECASE),
    re.compile(r"ANR in ", re.IGNORECASE),
    re.compile(r"AndroidRuntime", re.IGNORECASE),
)

COMPILER_WARNING_PATTERNS = (
    re.compile(r"^e:.*warning", re.IGNORECASE | re.MULTILINE),
    re.compile(r"^w:.*", re.MULTILINE),
    re.compile(r"\bwarning:\s+.*", re.IGNORECASE),
)

EXPECTED_SMOKE_MARKERS = (
    "MENU=HOW_TO",
    "HOW_TO=BACK",
    "MENU=PLAY",
    "STATE=COUNTDOWN",
    "STATE=RACING",
    "CONTROL=LEFT",
    "CONTROL=RIGHT",
    "CONTROL=BOOST",
)


def read_log(path: Path) -> str:
    if not path.exists():
        raise FileNotFoundError(path)
    return path.read_text(encoding="utf-8", errors="replace")


def collect_failures(text: str) -> list[str]:
    failures: list[str] = []
    for line in text.splitlines():
        if any(pattern.search(line) for pattern in HARD_FAILURE_PATTERNS):
            stripped = line.strip()
            if stripped and stripped not in failures:
                failures.append(stripped)
    return failures[:20]


def collect_compiler_warnings(text: str) -> list[str]:
    warnings: list[str] = []
    for line in text.splitlines():
        stripped = line.strip()
        if stripped and any(pattern.search(stripped) for pattern in COMPILER_WARNING_PATTERNS):
            if stripped not in warnings:
                warnings.append(stripped)
    return warnings[:50]


def verify_apk(path: Path) -> dict[str, object]:
    result: dict[str, object] = {
        "exists": path.exists(),
        "size_bytes": path.stat().st_size if path.exists() else 0,
        "valid_zip": False,
        "has_manifest": False,
    }
    if not path.exists() or path.stat().st_size == 0:
        return result

    try:
        with zipfile.ZipFile(path) as apk:
            bad_member = apk.testzip()
            result["valid_zip"] = bad_member is None
            result["has_manifest"] = "AndroidManifest.xml" in apk.namelist()
            if bad_member is not None:
                result["corrupt_member"] = bad_member
    except zipfile.BadZipFile:
        result["valid_zip"] = False
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build-log", type=Path)
    parser.add_argument("--test-log", type=Path)
    parser.add_argument("--smoke-log", type=Path)
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--report", type=Path, default=Path("ci-build-report.json"))
    args = parser.parse_args()

    logs: dict[str, str] = {}
    failures: list[str] = []
    compiler_warnings: list[str] = []
    missing_logs: list[str] = []

    for label, path in (
        ("build", args.build_log),
        ("test", args.test_log),
        ("smoke", args.smoke_log),
    ):
        if path is None:
            continue
        try:
            text = read_log(path)
        except FileNotFoundError:
            missing_logs.append(f"{label}:{path}")
            continue
        logs[label] = text
        failures.extend(collect_failures(text))
        compiler_warnings.extend(collect_compiler_warnings(text))

    failures = list(dict.fromkeys(failures))
    compiler_warnings = list(dict.fromkeys(compiler_warnings))

    apk = verify_apk(args.apk) if args.apk else {
        "exists": False,
        "size_bytes": 0,
        "valid_zip": False,
        "has_manifest": False,
    }

    smoke_missing_markers: list[str] = []
    if "smoke" in logs:
        for marker in EXPECTED_SMOKE_MARKERS:
            if marker not in logs["smoke"]:
                smoke_missing_markers.append(marker)

    report = {
        "status": "PASS" if not failures and not compiler_warnings and not smoke_missing_markers and not missing_logs and apk["valid_zip"] and apk["has_manifest"] else "FAIL",
        "python": f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}",
        "failures": failures,
        "compiler_warning_count": len(compiler_warnings),
        "compiler_warnings": compiler_warnings,
        "missing_logs": missing_logs,
        "smoke_missing_markers": smoke_missing_markers,
        "apk": apk,
    }

    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    print(f"CI VERIFIER: {report['status']}")
    print(f"Python: {report['python']}")
    print(f"Compiler warnings: {len(compiler_warnings)}")
    print(f"Hard failures: {len(failures)}")
    print(f"Missing logs: {len(missing_logs)}")
    print(f"Smoke markers missing: {len(smoke_missing_markers)}")
    print(f"APK size: {apk['size_bytes']} bytes")
    print(f"APK valid ZIP: {apk['valid_zip']}")
    print(f"APK manifest present: {apk['has_manifest']}")

    if compiler_warnings:
        print("\nCompiler warnings detected:")
        for warning in compiler_warnings:
            print(f"  {warning}")
    if failures:
        print("\nHard failures detected:")
        for failure in failures:
            print(f"  {failure}")
    if smoke_missing_markers:
        print("\nMissing smoke markers:")
        for marker in smoke_missing_markers:
            print(f"  {marker}")

    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
