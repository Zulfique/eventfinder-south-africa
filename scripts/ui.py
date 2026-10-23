#!/usr/bin/env python
"""Android UI helper: dump visible labels + tap coordinates.

Usage:
  python scripts/ui.py dump                 # print label\tx\ty for every node
  python scripts/ui.py tap "label"           # tap the node whose text/desc matches
  python scripts/ui.py tapxy X Y            # tap absolute coordinates
  python scripts/ui.py back                 # press back
  python scripts/ui.py shot out.png          # screenshot
  python scripts/ui.py log                  # dump recent app logcat
  python scripts/ui.py clicktext "Allow"    # find by exact text, tap its center
"""
import re
import subprocess
import sys
import time
import os

ADB = r"C:\Users\27635\AppData\Local\Android\Sdk\platform-tools\adb.exe"
TMP = r"C:\Users\27635\AppData\Local\Temp\opencode"
os.makedirs(TMP, exist_ok=True)


def adb(*args, timeout=90):
    return subprocess.run(
        [ADB, *args], capture_output=True, text=True, timeout=timeout
    )


def dump_nodes():
    """Return a list of (label, x, y, text, desc) for every node in the view."""
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    res = adb("shell", "cat", "/sdcard/ui.xml")
    xml = res.stdout
    nodes = []
    # Each node carries text, content-desc and bounds in any attribute order.
    for m in re.finditer(r"<node\b([^>]*)/?>", xml):
        attrs = m.group(1)

        def attr(name):
            am = re.search(rf'\b{name}="([^"]*)"', attrs)
            return am.group(1) if am else ""

        text = attr("text")
        desc = attr("content-desc")
        bm = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', attrs)
        if not bm:
            continue
        x1, y1, x2, y2 = map(int, bm.groups())
        label = text or (f"[{desc}]" if desc else "")
        if not label:
            continue
        nodes.append((label, (x1 + x2) // 2, (y1 + y2) // 2, text, desc))
    return nodes


def find(label, exact=False):
    nodes = dump_nodes()
    if exact:
        for n in nodes:
            if n[3] == label or n[4] == label:
                return n
    else:
        low = label.lower()
        for n in nodes:
            if low in n[3].lower() or low in n[4].lower():
                return n
    return None


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    cmd = sys.argv[1]
    args = sys.argv[2:]

    if cmd == "dump":
        for label, x, y, _, _ in dump_nodes():
            print(f"{label}\t{x}\t{y}")
    elif cmd == "tap":
        target = args[0]
        node = find(target, exact=(len(args) > 1 and args[1] == "exact"))
        if not node:
            print(f"NOT FOUND: {target}")
            sys.exit(1)
        print(f"TAP {node[0]} @ {node[1]},{node[2]}")
        adb("shell", "input", "tap", str(node[1]), str(node[2]))
        time.sleep(1.5)
    elif cmd == "tapxy":
        adb("shell", "input", "tap", args[0], args[1])
        time.sleep(1.5)
    elif cmd == "back":
        adb("shell", "input", "keyevent", "4")
        time.sleep(1.2)
    elif cmd == "shot":
        out = args[0] if args else os.path.join(TMP, "shot.png")
        with open(out, "wb") as f:
            p = subprocess.run(
                [ADB, "exec-out", "screencap", "-p"], stdout=f, timeout=60
            )
        print(out)
    elif cmd == "log":
        res = adb("logcat", "-d", "-t", args[0] if args else "300")
        print(res.stdout)
    elif cmd == "start":
        adb("shell", "am", "start", "-n", "com.eventfinder.app/.MainActivity")
        time.sleep(3)
    elif cmd == "stop":
        adb("shell", "am", "force-stop", "com.eventfinder.app")
    elif cmd == "clearlog":
        adb("logcat", "-c")


if __name__ == "__main__":
    main()
