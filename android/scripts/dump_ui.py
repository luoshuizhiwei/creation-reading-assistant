# -*- coding: utf-8 -*-
"""dump 当前 UI 节点: text|desc|bounds"""
import re, sys, subprocess

sys.stdout.reconfigure(encoding="utf-8")
serial = "c49ac6cf"
subprocess.run(["adb", "-s", serial, "shell", "uiautomator", "dump", "/sdcard/ui_now.xml"],
               capture_output=True)
subprocess.run(["adb", "-s", serial, "pull", "/sdcard/ui_now.xml",
                r"d:\develop\Code\Codex\creation-reading-assistant\android\scripts\ui_now.xml"],
               capture_output=True)
t = open(r"d:\develop\Code\Codex\creation-reading-assistant\android\scripts\ui_now.xml",
         encoding="utf-8").read()
pat = re.compile(r'text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="(\[[^\]]*\]\[[^\]]*\])"')
for m in pat.finditer(t):
    txt, desc, bounds = m.groups()
    if txt or desc:
        print(f"{txt}|{desc}|{bounds}")
