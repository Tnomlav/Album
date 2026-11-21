#!/usr/bin/env python3
"""Print the CHANGELOG section for the version in version.properties.

The release workflow uses this to build both the GitHub Release body and the
notes inside album-update.json, so the published notes and the changelog
cannot drift apart.
"""

import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]


def read_version():
    values = {}
    for line in (ROOT / "version.properties").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return ".".join(values[key] for key in ("VERSION_MAJOR", "VERSION_MINOR", "VERSION_PATCH"))


def main():
    version = read_version()
    marker = f"## v{version}"
    lines = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8").splitlines()
    start = next((index for index, line in enumerate(lines) if line.startswith(marker)), None)
    if start is None:
        raise SystemExit(f"CHANGELOG.md has no section for v{version}")

    end = len(lines)
    for index in range(start + 1, len(lines)):
        if lines[index].startswith("## "):
            end = index
            break

    body = "\n".join(lines[start + 1:end]).strip()
    if not body:
        raise SystemExit(f"CHANGELOG.md section for v{version} is empty")
    print(body)


if __name__ == "__main__":
    main()
