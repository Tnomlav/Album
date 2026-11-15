#!/usr/bin/env python3
"""Check that a built APK carries the version in version.properties.

The version used to be bumped by the packaging task, so the APK and the file
could disagree (and did: the installed build was one patch ahead of the repo).
This script is the guard: CI runs it after every release build, and it can be
run locally before publishing.

Usage:
    python3 scripts/check_apk_version.py [path/to/app.apk]
"""

import glob
import os
import pathlib
import re
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
DEFAULT_APK = ROOT / "app/build/outputs/apk/release/app-arm64-v8a-release.apk"


def read_version_properties():
    values = {}
    for line in (ROOT / "version.properties").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def find_aapt2():
    for env in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk = os.environ.get(env)
        if not sdk:
            continue
        matches = sorted(glob.glob(os.path.join(sdk, "build-tools", "*", "aapt2")))
        if matches:
            return matches[-1]
    found = shutil.which("aapt2")
    if found:
        return found
    raise SystemExit("aapt2 not found: set ANDROID_HOME or put aapt2 on PATH")


def apk_version(apk, aapt2):
    output = subprocess.run(
        [aapt2, "dump", "badging", str(apk)],
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    match = re.search(r"versionCode='(\d+)' versionName='([^']*)'", output)
    if not match:
        raise SystemExit(f"could not read the version from {apk}")
    return int(match.group(1)), match.group(2)


def main():
    apk = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_APK
    if not apk.is_file():
        raise SystemExit(f"APK not found: {apk}")

    properties = read_version_properties()
    expected_name = ".".join(
        properties.get(key, "0") for key in ("VERSION_MAJOR", "VERSION_MINOR", "VERSION_PATCH")
    )
    expected_code = int(properties.get("VERSION_CODE", "0"))

    code, name = apk_version(apk, find_aapt2())
    if (code, name) != (expected_code, expected_name):
        print(
            "version mismatch: "
            f"APK has {name} ({code}), version.properties has {expected_name} ({expected_code})",
            file=sys.stderr,
        )
        return 1

    print(f"version ok: {name} ({code})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
