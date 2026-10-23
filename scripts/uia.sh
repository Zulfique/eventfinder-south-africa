#!/usr/bin/env bash
# Dump the current Android UI as "label<TAB>x<TAB>y" lines (tap coordinates).
# Usage: uia.sh [outfile]
ADB="/c/Users/27635/AppData/Local/Android/Sdk/platform-tools/adb.exe"
OUT="${1:-/c/Users/27635/AppData/Local/Temp/opencode/ui.txt}"
"$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
"$ADB" shell cat /sdcard/ui.xml > "$OUT"
python - "$OUT" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
pat = re.compile(r'<node[^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
for m in pat.finditer(xml):
    t, d = m.group(1), m.group(2)
    x1, y1, x2, y2 = map(int, m.group(3, 4, 5, 6))
    label = t or (f"[{d}]" if d else "")
    if not label:
        continue
    print(f"{label}\t{(x1+x2)//2}\t{(y1+y2)//2}")
PY
