# -*- coding: utf-8 -*-
"""逐帧采集：排序切换交互链期间的所有帧（gfxinfo framestats PROFILEDATA），
计算 frameOverrunMs 近似 = (FrameCompleted - IntendedVsync) - 16.67ms 截止。"""
import subprocess, time, sys, re, statistics

sys.stdout.reconfigure(encoding="utf-8")
SERIAL = "c49ac6cf"
PKG = "com.creationreadingassistant.benchmarktarget"
TAP_ORGANIZER = (235, 215)
TAP_SORT_ROW = (484, 1195)
TAP_TITLE = (610, 773)
TAP_RECENT = (574, 429)
DEADLINE_NS = 16_666_667

def sh(args):
    return subprocess.run(["adb", "-s", SERIAL] + args, capture_output=True, text=True).stdout

def collect_frames():
    """返回窗口内帧的 (cpu_ns, overrun_ns) 列表
    列: 2=IntendedVsync 9=FrameDeadline 16=FrameCompleted"""
    out = sh(["shell", f"dumpsys gfxinfo {PKG} framestats"])
    m = re.search(r"---PROFILEDATA---\n(.*?)\n---PROFILEDATA---", out, re.S)
    if not m:
        return []
    frames = []
    for line in m.group(1).strip().splitlines()[1:]:
        cols = line.split(",")
        if len(cols) >= 17:
            try:
                intended = int(cols[2]); deadline = int(cols[9]); completed = int(cols[16])
                if intended > 0 and completed > intended and deadline > 0:
                    frames.append((completed - intended, completed - deadline))
            except ValueError:
                pass
    return frames

def pct(vals, q):
    vals = sorted(vals)
    if not vals: return float("nan")
    k = (len(vals) - 1) * q / 100.0
    f = int(k); c = min(f + 1, len(vals) - 1)
    return vals[f] + (vals[c] - vals[f]) * (k - f)

def ensure_focus():
    out = sh(["shell", "dumpsys window | grep mCurrentFocus"])
    if PKG not in out:
        sh(["shell", "am", "start", "-n",
            f"{PKG}/com.creationreadingassistant.MainActivity"])
        time.sleep(2.0)
        # 回到底部书架 tab（如不在书架）
        probe = sh(["shell", "uiautomator", "dump", "/sdcard/p.xml"])
        out2 = sh(["shell", "cat", "/sdcard/p.xml"])
        if "打开书籍" not in out2 and "打开书架整理" not in out2:
            sh(["shell", "input", "tap", "366", "2651"])
            time.sleep(2.0)

all_cpu, all_overrun = [], []
for i in range(12):
    target, label = (TAP_TITLE, "书名") if i % 2 == 0 else (TAP_RECENT, "最近阅读")
    ensure_focus()
    sh(["shell", f"dumpsys gfxinfo {PKG} reset"])
    time.sleep(0.3)
    sh(["shell", f"input tap {TAP_ORGANIZER[0]} {TAP_ORGANIZER[1]}"])
    time.sleep(1.0)
    sh(["shell", f"input tap {TAP_SORT_ROW[0]} {TAP_SORT_ROW[1]}"])
    time.sleep(1.0)
    sh(["shell", f"input tap {target[0]} {target[1]}"])
    time.sleep(1.0)
    sh(["shell", "input keyevent 4"])
    time.sleep(1.2)
    frames = collect_frames()
    if not frames:
        print(f"round {i:2d} -> {label}: NO_FRAMES（可能失焦）")
        continue
    if i < 2:
        print(f"round {i} ({label}) warmup: {len(frames)} frames")
        continue
    cpu_ms = [c / 1e6 for c, _ in frames]
    overrun_ms = [o / 1e6 for _, o in frames]
    all_cpu += cpu_ms
    all_overrun += overrun_ms
    ovr_pos = sorted([v for v in overrun_ms if v > 0], reverse=True)
    print(f"round {i:2d} -> {label}: frames={len(frames)} cpuP50={pct(cpu_ms,50):.1f} cpuP95={pct(cpu_ms,95):.1f} "
          f"overrunP95={pct(overrun_ms,95):.2f} maxOverrun={max(overrun_ms):.1f} topOverrun={['%.1f'%v for v in ovr_pos[:5]]}")

print()
print(f"=== 合计 10 轮 {len(all_overrun)} 帧 ===")
print(f"frameDurationCpuMs: P50={pct(all_cpu,50):.2f} P90={pct(all_cpu,90):.2f} P95={pct(all_cpu,95):.2f} P99={pct(all_cpu,99):.2f} max={max(all_cpu):.2f}")
print(f"frameOverrunMs:     P50={pct(all_overrun,50):.2f} P90={pct(all_overrun,90):.2f} P95={pct(all_overrun,95):.2f} P99={pct(all_overrun,99):.2f} max={max(all_overrun):.2f}")
print("overrun TOP10(ms):", " / ".join(f"{v:.1f}" for v in sorted(all_overrun, reverse=True)[:10]))
