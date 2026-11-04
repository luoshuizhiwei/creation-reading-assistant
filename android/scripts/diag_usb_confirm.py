"""Timing diagnosis for the MIUI USB-install confirmation dialog.

Starts an `adb install` in the background and, in parallel, polls for the
`AdbInstallActivity` dialog. On every sighting it records elapsed time and taps
the accept button, then re-checks whether the dialog disappeared.

The point is to answer: is the tap too late, or is it ineffective?

Usage:
    python diag_usb_confirm.py --serial <serial> --apk <path> [--watch 25]
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path

PACKAGE = "com.miui.securitycenter"
ACTIVITY = "AdbInstallActivity"
ACCEPT_ID = "android:id/button2"
NODE_RE = re.compile(r"<node\b[^>]*?/>")
ATTR_RE = re.compile(r'(\S+?)="([^"]*)"')


def adb(serial: str, *args: str, timeout: float = 20.0):
    return subprocess.run(["adb", "-s", serial, *args],
                          capture_output=True, timeout=timeout)


def center(bounds: str):
    m = re.match(r"^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$", bounds or "")
    if not m:
        return None
    x1, y1, x2, y2 = (int(m.group(i)) for i in range(1, 5))
    return (x1 + x2) // 2, (y1 + y2) // 2


def foreground(serial: str) -> str:
    """Cheap probe: current focus. Much faster than a full UI dump."""
    try:
        out = adb(serial, "shell", "dumpsys", "window").stdout.decode("utf-8", "replace")
        m = re.search(r"mCurrentFocus=.*?([A-Za-z0-9_.]+)/([A-Za-z0-9_.]+)", out)
        if m:
            return f"{m.group(1)}/{m.group(2)}"
        m2 = re.search(r"mFocusedApp=.*?([A-Za-z0-9_.]+)/([A-Za-z0-9_.]+)", out)
        if m2:
            return f"{m2.group(1)}/{m2.group(2)}"
    except Exception:
        pass
    return ""


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--apk", required=True)
    ap.add_argument("--watch", type=float, default=25.0)
    args = ap.parse_args()

    remote = "/sdcard/cra-diag-ui.xml"
    tmp = Path(tempfile.gettempdir())
    t0 = time.monotonic()

    def stamp() -> str:
        return f"[{time.monotonic() - t0:6.2f}s]"

    install_result: dict = {}

    def run_install():
        try:
            r = subprocess.run(
                ["adb", "-s", args.serial, "install", "-r", "-t", args.apk],
                capture_output=True, timeout=180,
            )
            install_result["rc"] = r.returncode
            install_result["out"] = r.stdout.decode("utf-8", "replace").strip()
            install_result["err"] = r.stderr.decode("utf-8", "replace").strip()
        except subprocess.TimeoutExpired:
            install_result["rc"] = "timeout"

    th = threading.Thread(target=run_install, daemon=True)
    print(f"{stamp()} starting install thread", flush=True)
    th.start()

    sightings = 0
    taps = 0
    deadline = time.monotonic() + args.watch

    while time.monotonic() < deadline:
        fg = foreground(args.serial)
        if ACTIVITY in fg:
            sightings += 1
            # Full dump only when the dialog is actually up.
            local = tmp / f"cra-diag-{time.time_ns()}.xml"
            adb(args.serial, "shell", "uiautomator", "dump", remote)
            adb(args.serial, "shell", "cat", remote)  # keep remote stable
            subprocess.run(["adb", "-s", args.serial, "pull", remote, str(local)],
                           capture_output=True)
            if local.exists():
                xml = local.read_text(encoding="utf-8", errors="replace")
                local.unlink(missing_ok=True)
            else:
                xml = ""
            target = None
            for node in NODE_RE.findall(xml):
                a = dict(ATTR_RE.findall(node))
                if a.get("package") == PACKAGE and a.get("resource-id") == ACCEPT_ID \
                        and a.get("enabled") == "true":
                    target = center(a.get("bounds", ""))
                    label = a.get("text", "")
                    break
            if target:
                x, y = target
                print(f"{stamp()} sighting#{sightings} focus={fg} "
                      f"tap {ACCEPT_ID} '{label}' at {x},{y}", flush=True)
                adb(args.serial, "shell", "input", "tap", str(x), str(y))
                taps += 1
                time.sleep(0.6)
                after = foreground(args.serial)
                gone = ACTIVITY not in after
                print(f"{stamp()}   after tap: focus={after or '(none)'} "
                      f"dialog_gone={gone}", flush=True)
            else:
                print(f"{stamp()} sighting#{sightings} focus={fg} "
                      f"but accept node NOT found (xml bytes={len(xml)})", flush=True)
            adb(args.serial, "shell", "rm", "-f", remote)
        time.sleep(0.25)
        if not th.is_alive() and install_result:
            break

    th.join(timeout=5)
    print(f"{stamp()} install result: {install_result}", flush=True)
    print(f"{stamp()} summary: sightings={sightings} taps={taps}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
