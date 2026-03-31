#!/usr/bin/env python3
"""
Parse the gradle dependencyUpdates JSON report and apply stable updates to gradle files.

Usage:   python3 scripts/update_deps.py
Reads:   build/dependencyUpdates/report.json
Writes:  Updated gradle files; GitHub Actions outputs to $GITHUB_OUTPUT
"""

import json
import os
import re
import sys
from pathlib import Path

REPORT = Path("build/dependencyUpdates/report.json")
GRADLE_FILES = [Path("app/build.gradle.kts"), Path("build.gradle.kts")]

UNSTABLE_KEYWORDS = ["alpha", "beta", "rc", "cr", "preview", "snapshot", "dev", "ea"]


def is_stable(version: str) -> bool:
    v = version.lower()
    return not any(kw in v for kw in UNSTABLE_KEYWORDS)


def apply_updates(deps: list) -> list:
    """Apply updates to gradle files. Returns list of (display_name, old_ver, new_ver, is_plugin)."""
    updates = []
    file_contents = {f: f.read_text() for f in GRADLE_FILES if f.exists()}

    for dep in deps:
        group = dep["group"]
        name = dep["name"]
        current = dep["version"]
        available = dep.get("available", {})
        new_ver = available.get("release") or available.get("milestone")

        if not new_ver or not is_stable(new_ver) or new_ver == current:
            continue

        is_plugin = name.endswith(".gradle.plugin")

        if is_plugin:
            # Handles: id("plugin.id") version "X.Y.Z" [apply false]
            plugin_id = group
            pattern = re.compile(
                r'(id\(["\']' + re.escape(plugin_id) + r'["\']\)\s+version\s+["\'])'
                + re.escape(current)
                + r'(["\'])'
            )
            for fpath, content in file_contents.items():
                new_content = pattern.sub(rf'\g<1>{new_ver}\2', content)
                if new_content != content:
                    file_contents[fpath] = new_content
                    updates.append((plugin_id, current, new_ver, True))
                    break
        else:
            # Handles: "group:name:version" coordinate strings
            old_coord = f"{group}:{name}:{current}"
            new_coord = f"{group}:{name}:{new_ver}"
            for fpath, content in file_contents.items():
                if old_coord in content:
                    file_contents[fpath] = content.replace(old_coord, new_coord)
                    updates.append((f"{group}:{name}", current, new_ver, False))
                    break

    for fpath, content in file_contents.items():
        fpath.write_text(content)

    return updates


def write_output(key: str, value: str, dest: str) -> None:
    with open(dest, "a") as f:
        f.write(f"{key}={value}\n")


def write_multiline_output(key: str, value: str, dest: str) -> None:
    with open(dest, "a") as f:
        f.write(f"{key}<<__EOF__\n{value}\n__EOF__\n")


def main():
    gh_output = os.environ.get("GITHUB_OUTPUT", "/dev/stdout")

    if not REPORT.exists():
        print(f"ERROR: {REPORT} not found — did dependencyUpdates run?", file=sys.stderr)
        sys.exit(1)

    report = json.loads(REPORT.read_text())
    deps = report.get("outdated", {}).get("dependencies", [])
    updates = apply_updates(deps)

    if not updates:
        print("No stable dependency updates found.")
        write_output("updated", "false", gh_output)
        write_multiline_output("summary", "All dependencies are current. No updates available.", gh_output)
        return

    lib_updates    = [(n, o, v) for n, o, v, p in updates if not p]
    plugin_updates = [(n, o, v) for n, o, v, p in updates if p]

    summary_lines = []
    if lib_updates:
        summary_lines.append(f"Libraries ({len(lib_updates)}):")
        for name, old, new in lib_updates:
            summary_lines.append(f"  {name}: {old} -> {new}")
    if plugin_updates:
        if summary_lines:
            summary_lines.append("")
        summary_lines.append(f"Plugins ({len(plugin_updates)}):")
        for name, old, new in plugin_updates:
            summary_lines.append(f"  {name}: {old} -> {new}")

    summary = "\n".join(summary_lines)
    print(f"Applied {len(updates)} update(s):\n{summary}")

    write_output("updated", "true", gh_output)
    write_output("count", str(len(updates)), gh_output)
    write_multiline_output("summary", summary, gh_output)


if __name__ == "__main__":
    main()
