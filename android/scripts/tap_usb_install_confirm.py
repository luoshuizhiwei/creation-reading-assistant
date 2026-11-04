"""Tap the MIUI/HyperOS "USB install" confirmation before its countdown expires.

Why this exists
---------------
MIUI injects `PmInjector.installVerify` into PackageManagerService. Every install
originating from the shell user (adb install, pm install, and Gradle ddmlib's
install session) pops up

    com.miui.securitycenter/com.miui.permcenter.install.AdbInstallActivity

titled "USB安装提示". Its decline button carries a countdown ("拒绝（8）"); when the
counter reaches zero the session is cancelled and reported as
`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. Nothing in Gradle taps
it, so connected tests that need to install a package cannot proceed.

Run this in the background while the install runs:

    python tap_usb_install_confirm.py --serial <serial> --seconds 900 &

Design notes (learned the hard way)
-----------------------------------
1. Detect with `dumpsys window` (fast) and only spend a full `uiautomator dump`
   once the dialog is actually in the foreground. A dump every cycle is too slow
   to reliably land inside the ~8 second window.
2. Match the accept button by `resource-id` (`android:id/button2`), never by its
   Chinese label: a native command's stdout decoded as CP936 turns "继续安装" into
   mojibake.
3. Do NOT exit after one tap when driving a Gradle run — Gradle installs more than
   one artifact (app APK and test APK) and each one raises its own dialog.
4. Pull the dump file and decode as UTF-8 explicitly; `exec-out cat` through a
   CP936 console corrupts attribute quoting and can make the XML unparseable.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import tempfile
import time
from pathlib import Path

TARGET_PACKAGE = "com.miui.securitycenter"
TARGET_ACTIVITY = "AdbInstallActivity"
ACCEPT_ID = "android:id/button2"

NODE_RE = re.compile(r"<node\b[^>]*?/>")
ATTR_RE = re.compile(r'(\S+?)="([^"]*)"')


def adb(serial: str, *args: str, timeout: float = 20.0) -> subprocess.CompletedProcess:
    return subprocess.run(["adb", "-s", serial, *args],
                          capture_output=True, timeout=timeout)


def center(bounds: str):
    m = re.match(r"^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$", bounds or "")
    if not m:
        return None
    x1, y1, x2, y2 = (int(m.group(i)) for i in range(1, 5))
    return (x1 + x2) // 2, (y1 + y2) // 2


def foreground(serial: str) -> str:
    """Cheap probe for the focused window; far faster than a UI dump."""
    try:
        out = adb(serial, "shell", "dumpsys", "window").stdout.decode("utf-8", "replace")
        for key in ("mCurrentFocus", "mFocusedApp"):
            m = re.search(rf"{key}=.*?([A-Za-z0-9_.]+)/([A-Za-z0-9_.]+)", out)
            if m:
                return f"{m.group(1)}/{m.group(2)}"
    except (subprocess.TimeoutExpired, OSError):
        pass
    return ""


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--seconds", type=float, default=900.0,
                    help="how long to keep watching (default 15 min)")
    ap.add_argument("--interval", type=float, default=0.25,
                    help="focus-probe period; the dialog only lives ~8s")
    ap.add_argument("--once", action="store_true",
                    help="stop after the first successful tap (NOT for Gradle runs)")
    args = ap.parse_args()

    remote = "/sdcard/cra-usb-confirm-watch.xml"
    tmp = Path(tempfile.gettempdir())
    deadline = time.monotonic() + args.seconds
    taps = 0

    print(f"watcher: serial={args.serial} seconds={args.seconds} "
          f"interval={args.interval} once={args.once}", flush=True)

    while time.monotonic() < deadline:
        if TARGET_ACTIVITY not in foreground(args.serial):
            time.sleep(args.interval)
            continue

        local = tmp / f"cra-usb-confirm-{time.time_ns()}.xml"
        xml = ""
        try:
            adb(args.serial, "shell", "uiautomator", "dump", remote)
            subprocess.run(["adb", "-s", args.serial, "pull", remote, str(local)],
                           capture_output=True, timeout=20)
            if local.exists():
                xml = local.read_text(encoding="utf-8", errors="replace")
        except (subprocess.TimeoutExpired, OSError) as exc:
            print(f"watcher: adb error {exc}", flush=True)
        finally:
            if local.exists():
                try:
                    local.unlink()
                except OSError:
                    pass
            try:
                adb(args.serial, "shell", "rm", "-f", remote)
            except Exception:
                pass

        target = None
        label = ""
        for node in NODE_RE.findall(xml):
            a = dict(ATTR_RE.findall(node))
            if (a.get("package") == TARGET_PACKAGE
                    and a.get("resource-id") == ACCEPT_ID
                    and a.get("enabled") == "true"):
                target = center(a.get("bounds", ""))
                label = a.get("text", "")
                break

        if not target:
            print(f"[{time.strftime('%H:%M:%S')}] dialog up but accept node not found "
                  f"(xml bytes={len(xml)})", flush=True)
            time.sleep(args.interval)
            continue

        x, y = target
        print(f"[{time.strftime('%H:%M:%S')}] TAP '{label}' ({ACCEPT_ID}) at {x},{y}",
              flush=True)
        adb(args.serial, "shell", "input", "tap", str(x), str(y))
        taps += 1
        if args.once:
            print(f"watcher: tapped once, taps={taps}", flush=True)
            return 0
        time.sleep(0.5)

    print(f"watcher: done, taps={taps}", flush=True)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("watcher: interrupted", flush=True)
        sys.exit(130)
