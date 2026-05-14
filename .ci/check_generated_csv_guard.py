#!/usr/bin/env python3
import datetime as dt
import re
import subprocess
import sys
from pathlib import Path

DATE_RE = re.compile(r"\b(\d{4}-\d{2}-\d{2})\b")


def sh(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True)


def earliest_match_mutation(base_ref: str) -> dt.date | None:
    diff = sh("diff", f"{base_ref}..HEAD", "--", "matches")
    dates: list[dt.date] = []
    for line in diff.splitlines():
        if not line.startswith(("+", "-")) or line.startswith(("+++", "---")):
            continue
        for m in DATE_RE.findall(line):
            dates.append(dt.date.fromisoformat(m))
    return min(dates) if dates else None


def changed_csv_files(base_ref: str) -> list[str]:
    out = sh("diff", "--name-only", f"{base_ref}..HEAD", "--", "web/public/csv")
    return [line.strip() for line in out.splitlines() if line.strip().endswith(".csv")]


def changed_files(base_ref: str) -> list[str]:
    out = sh("diff", "--name-only", f"{base_ref}..HEAD")
    return [line.strip() for line in out.splitlines() if line.strip()]


def generator_input_changed(base_ref: str) -> bool:
    prefixes = (
        "src/main/",
        "gradle/",
    )
    exact_paths = {
        "build.gradle.kts",
        "settings.gradle.kts",
        "gradle.properties",
        "club_events.txt",
        "tiers.csv",
    }
    return any(path in exact_paths or path.startswith(prefixes) for path in changed_files(base_ref))


def changed_dates_for_file(base_ref: str, file_path: str) -> list[dt.date]:
    diff = sh("diff", "-U0", f"{base_ref}..HEAD", "--", file_path)
    changed: list[dt.date] = []
    for line in diff.splitlines():
        if not line.startswith(("+", "-")) or line.startswith(("+++", "---")):
            continue
        m = DATE_RE.search(line)
        if m:
            changed.append(dt.date.fromisoformat(m.group(1)))
    return changed


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: check_generated_csv_guard.py <base-ref>", file=sys.stderr)
        return 2

    base_ref = sys.argv[1]
    d = earliest_match_mutation(base_ref)
    files = changed_csv_files(base_ref)

    if files and generator_input_changed(base_ref):
        print("Generated CSV files changed with generator/rating inputs; skipping match-date guard.")
        return 0

    if d is None:
        if files:
            print("Generated CSV files changed but no matches/ mutation detected.")
            print("If intentional, apply PR label: changes generated match output")
            for f in files:
                print(f" - {f}")
            return 1
        print("No matches/ mutation and no generated csv changes detected.")
        return 0

    violations: list[str] = []
    for f in files:
        for change_date in changed_dates_for_file(base_ref, f):
            if change_date < d:
                violations.append(f"{f}: changed date {change_date} < earliest match mutation {d}")

    if violations:
        print(f"Earliest match mutation date: {d}")
        print("Found generated csv changes before this date:")
        for v in violations:
            print(f" - {v}")
        print("If intentional, apply PR label: changes generated match output")
        return 1

    print(f"Earliest match mutation date: {d}")
    print("Generated csv changes respect the date guard.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
