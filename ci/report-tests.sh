#!/usr/bin/env bash
# Publishes the instrumented test results (passed / skipped / failed per test) as a neutral
# check on the commit, so each emulator's outcome can be read without downloading artifacts.
# Usage: ci/report-tests.sh "<check name>"
set -u
name="${1:-Device results}"
out=device-results.md
python3 - "$out" <<'PY'
import glob, sys, xml.etree.ElementTree as ET
rows, counts = [], {"passed": 0, "skipped": 0, "failed": 0}
for path in sorted(glob.glob("app/build/outputs/androidTest-results/**/*.xml", recursive=True)):
    try:
        root = ET.parse(path).getroot()
    except Exception:
        continue
    for case in root.iter("testcase"):
        if case.find("failure") is not None or case.find("error") is not None:
            status = "failed"
        elif case.find("skipped") is not None:
            status = "skipped"
        else:
            status = "passed"
        counts[status] += 1
        cls = (case.get("classname") or "").rsplit(".", 1)[-1]
        rows.append(f"| {cls} | {case.get('name')} | {status} | {case.get('time', '')} s |")
with open(sys.argv[1], "w") as f:
    f.write(f"{counts['passed']} passed, {counts['skipped']} skipped, {counts['failed']} failed\n\n")
    f.write("| Class | Test | Result | Time |\n| --- | --- | --- | --- |\n")
    f.write("\n".join(rows) if rows else "| (no results found) | | | |")
    f.write("\n")
PY
if [ -s logs/ready.log ]; then
  { printf '\n### Emulator readiness\n\n```\n'; tail -10 logs/ready.log; printf '```\n'; } >> "$out"
fi
if [ -s logs/seed.log ]; then
  { printf '\n### SMS seeding\n\n```\n'; tail -20 logs/seed.log; printf '```\n'; } >> "$out"
fi
cat "$out"

if [ -n "${GITHUB_TOKEN:-}" ] && [ -n "${GITHUB_REPOSITORY:-}" ]; then
  python3 - "$out" "$name" > results.json <<'PY'
import json, os, sys
text = open(sys.argv[1], encoding="utf-8").read()
print(json.dumps({
    "name": sys.argv[2],
    "head_sha": os.environ["GITHUB_SHA"],
    "status": "completed",
    "conclusion": "neutral",
    "output": {"title": sys.argv[2], "summary": text.splitlines()[0] if text else "no results", "text": text[:60000]},
}))
PY
  gh api "repos/$GITHUB_REPOSITORY/check-runs" --input results.json >/dev/null \
    && echo "Published check: $name" || echo "::warning::could not publish device results"
fi
