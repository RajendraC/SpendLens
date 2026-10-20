#!/usr/bin/env bash
# Collects the useful part of a failed CI job (compiler errors, failed tests, lint errors)
# and publishes it as a check result on the commit, so it can be read from the
# GitHub API or the "Checks" tab without downloading the full job log.
# Usage: ci/report-failure.sh "<check name>"
set -u
name="${1:-Failure details}"
out=failure-details.md
: > "$out"

section() { printf '\n### %s\n\n```\n' "$1" >> "$out"; }
endsection() { printf '```\n' >> "$out"; }

# 1. Gradle and compiler output saved by earlier steps
if ls logs/*.log >/dev/null 2>&1; then
  section "Build output (errors and failures)"
  grep -h -E "^e: |^w: .*(deprecated|unchecked)|error:|FAILED|What went wrong|Execution failed|Could not|Unresolved|Exception|AssertionError|expected|Caused by|tests completed|Lint found|Error:" logs/*.log \
    | grep -v -E "^\s+at (org\.gradle|java\.base|jdk\.|kotlin\.coroutines|sun\.)" \
    | awk '!seen[$0]++' | head -250 >> "$out"
  endsection
fi

# 1a. 16 KB page-size check output, if any
if [ -s logs/align.log ]; then
  section "16 KB page-size check"
  cat logs/align.log >> "$out"
  endsection
fi

# 1b. SDK packages listed by the job, if any
if [ -s logs/sdk-images.txt ]; then
  section "Emulator images and platforms available"
  cat logs/sdk-images.txt >> "$out"
  endsection
fi

# 2. Failed unit / instrumented tests, from the JUnit XML reports
xmls=$(find . -path ./node_modules -prune -o \( -path "*/test-results/*" -o -path "*/androidTest-results/*" \) -name "*.xml" -print 2>/dev/null)
if [ -n "$xmls" ]; then
  section "Failed tests"
  python3 - $xmls >> "$out" <<'PY'
import sys, xml.etree.ElementTree as ET
shown = 0
for path in sys.argv[1:]:
    try:
        root = ET.parse(path).getroot()
    except Exception:
        continue
    for case in root.iter("testcase"):
        for tag in ("failure", "error"):
            f = case.find(tag)
            if f is None:
                continue
            shown += 1
            if shown > 40:
                continue
            text = (f.get("message") or "") + "\n" + (f.text or "")
            lines = [l for l in text.splitlines() if l.strip()]
            keep = [l for l in lines if not l.strip().startswith("at ") or "spendlens" in l][:25]
            print(f"--- {case.get('classname')}.{case.get('name')}")
            print("\n".join(keep))
print(f"\n{shown} failed test case(s)")
PY
  endsection
fi

# 3. Lint errors
for f in app/build/reports/lint-results-debug.xml app/build/intermediates/lint_intermediate_text_report/debug/*/lint-results-debug.txt; do
  [ -f "$f" ] || continue
  section "Lint ($f)"
  case "$f" in
    *.xml)
      python3 - "$f" >> "$out" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
n = 0
for i in root.iter("issue"):
    if i.get("severity") not in ("Error", "Fatal"):
        continue
    n += 1
    loc = i.find("location")
    where = f"{loc.get('file')}:{loc.get('line')}" if loc is not None else "?"
    print(f"{i.get('id')}: {i.get('message')}\n    at {where}")
print(f"\n{n} lint error(s)")
PY
      ;;
    *) head -150 "$f" >> "$out" ;;
  esac
  endsection
done

# Trim to the check-run size limit and publish
python3 - "$out" <<'PY'
import sys
p = sys.argv[1]
s = open(p, encoding="utf-8", errors="replace").read()
s = s.replace("/home/runner/work/SpendLens/SpendLens/", "")
if len(s) > 60000:
    s = s[:60000] + "\n... (cut)\n```\n"
open(p, "w", encoding="utf-8").write(s)
PY

cat "$out"

if [ -n "${GITHUB_TOKEN:-}" ] && [ -n "${GITHUB_REPOSITORY:-}" ]; then
  python3 - "$out" "$name" > check.json <<'PY'
import json, os, sys
text = open(sys.argv[1], encoding="utf-8").read()
print(json.dumps({
    "name": sys.argv[2],
    "head_sha": os.environ["GITHUB_SHA"],
    "status": "completed",
    "conclusion": "neutral",
    "output": {"title": sys.argv[2], "summary": "Errors collected from the failed job.", "text": text or "(nothing collected)"},
}))
PY
  gh api "repos/$GITHUB_REPOSITORY/check-runs" --input check.json >/dev/null \
    && echo "Published check: $name" || echo "::warning::could not publish failure details"
fi
