#!/usr/bin/env python3
"""Check that built APKs carry the version in version.properties.

The version used to be bumped by the packaging task, so the APK and the file
could disagree (and did: the installed build was one patch ahead of the repo).
This script is the guard: CI runs it after every release build, and it can be
run locally before publishing.

Usage:
    python3 scripts/check_apk_version.py [path/to/app.apk ...]
    python3 scripts/check_apk_version.py --all

--all checks every app-*-release.apk under app/build/outputs/apk/release/.
"""

import glob
import os
import pathlib
import re
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
RELEASE_DIR = ROOT / "app/build/outputs/apk/release"
DEFAULT_APK = RELEASE_DIR / "app-arm64-v8a-release.apk"


def read_version_properties():
    values = {}
    for line in (ROOT / "version.properties").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def _version_key(path):
    name = os.path.basename(os.path.dirname(path))
    parts = [int(part) for part in re.findall(r"\d+", name)]
    return tuple(parts) if parts else (0,)


def find_aapt2():
    for env in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk = os.environ.get(env)
        if not sdk:
            continue
        matches = []
        for pattern in ("aapt2", "aapt2.exe"):
            matches.extend(glob.glob(os.path.join(sdk, "build-tools", "*", pattern)))
        if matches:
            return max(matches, key=_version_key)
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


def release_apks():
    return sorted(RELEASE_DIR.glob("app-*-release.apk"))


def main(argv):
    properties = read_version_properties()
    expected_name = ".".join(
        properties.get(key, "0") for key in ("VERSION_MAJOR", "VERSION_MINOR", "VERSION_PATCH")
    )
    expected_code = int(properties.get("VERSION_CODE", "0"))

    if argv == ["--all"]:
        apks = release_apks()
        if not apks:
            raise SystemExit(f"no release APKs found in {RELEASE_DIR}")
    elif argv:
        apks = [pathlib.Path(arg) for arg in argv]
    else:
        apks = [DEFAULT_APK]

    aapt2 = find_aapt2()
    failed = False
    for apk in apks:
        if not apk.is_file():
            print(f"APK not found: {apk}", file=sys.stderr)
            failed = True
            continue
        try:
            code, name = apk_version(apk, aapt2)
        except subprocess.CalledProcessError as error:
            print(f"could not read {apk}: {error}", file=sys.stderr)
            failed = True
            continue
        if (code, name) != (expected_code, expected_name):
            print(
                "version mismatch: "
                f"{apk} has {name} ({code}), version.properties has "
                f"{expected_name} ({expected_code})",
                file=sys.stderr,
            )
            failed = True
        else:
            print(f"version ok: {apk.name} -> {name} ({code})")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
