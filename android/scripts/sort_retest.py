# -*- coding: utf-8 -*-
"""
SortSheet 复测：书架排序切换全交互链帧测量（等效原 sortLargeShelfByTitle 场景）。
原基线交互：书架页点排序 chip（开弹层）-> 点"书名"-> 弹层退场+列表重排。
新 UI 交互（每轮，起点=书架页）：
  1) tap 打开书架整理 (235,215)
  2) tap 排序行 (484,1195)
  3) tap 排序项 书名(610,773)/最近阅读(574,429) 交替 -> setSortMode + popBackStack 回整理页
  4) keyevent BACK 回书架（书架以新排序显示，列表重排帧在此）
每轮：gfxinfo reset -> perfetto(FrameTimeline+atrace, 6s) -> 交互 -> 采窗口百分位 -> 拉 trace。
"""
import subprocess, time, sys, re, statistics

sys.stdout.reconfigure(encoding="utf-8")
SERIAL = "c49ac6cf"
PKG = "com.creationreadingassistant.benchmarktarget"
TAP_ORGANIZER = (235, 215)
TAP_SORT_ROW = (484, 1195)
TAP_TITLE = (610, 773)
TAP_RECENT = (574, 429)
ROUNDS = 12  # 前 2 轮 warmup

CONFIG_INLINE = (
    'buffers { size_kb: 65536 fill_policy: RING_BUFFER } '
    'data_sources { config { name: "android.surfaceflinger.frametimeline" } } '
    'data_sources { config { name: "linux.ftrace" ftrace_config { '
    'ftrace_events: "sched/sched_switch" ftrace_events: "power/suspend_resume" '
    'atrace_categories: "gfx" atrace_categories: "view" '
    'atrace_categories: "app" atrace_categories: "input" '
    'atrace_apps: "com.creationreadingassistant.benchmarktarget" } } } '
    'duration_ms: 6000'
)

def sh(args):
    return subprocess.run(["adb", "-s", SERIAL] + args, capture_output=True, text=True).stdout

def gfx_percentiles():
    out = sh(["shell", f"dumpsys gfxinfo {PKG}"])
    m = re.search(r"Total frames rendered: (\d+)\s*\nJanky frames: (\d+) \(([\d.]+)%\)", out)
    p = re.search(r"50th percentile: (\d+)ms\s*\n90th percentile: (\d+)ms\s*\n95th percentile: (\d+)ms\s*\n99th percentile: (\d+)ms", out)
    if not m or not p:
        return None
    return {"total": int(m.group(1)), "janky": int(m.group(2)), "janky_pct": m.group(3),
            "p50": int(p.group(1)), "p90": int(p.group(2)), "p95": int(p.group(3)), "p99": int(p.group(4))}

print("轮次|排序项|total|janky|jankyPct|p50ms|p90ms|p95ms|p99ms")
results = []
for i in range(ROUNDS):
    target, label = (TAP_TITLE, "书名") if i % 2 == 0 else (TAP_RECENT, "最近阅读")
    sh(["shell", f"dumpsys gfxinfo {PKG} reset"])
    trace_path = f"/data/misc/perfetto-traces/sort_retest_{i}.perfetto-trace"
    p = subprocess.Popen(["adb", "-s", SERIAL, "shell",
                          "perfetto", "--txt", "-c", "-", "-o", trace_path],
                         stdin=subprocess.PIPE, stdout=subprocess.DEVNULL)
    p.stdin.write(CONFIG_INLINE.encode("utf-8"))
    p.stdin.flush()
    p.stdin.close()  # perfetto 读完配置即开始采集（后台运行 6s）
    time.sleep(0.6)
    sh(["shell", f"input tap {TAP_ORGANIZER[0]} {TAP_ORGANIZER[1]}"])
    time.sleep(1.0)
    sh(["shell", f"input tap {TAP_SORT_ROW[0]} {TAP_SORT_ROW[1]}"])
    time.sleep(1.0)
    sh(["shell", f"input tap {target[0]} {target[1]}"])
    time.sleep(1.0)
    sh(["shell", "input keyevent 4"])  # 整理页 -> 书架
    time.sleep(2.6)  # 等 trace 结束
    p.wait(timeout=10)
    stats = gfx_percentiles()
    warm = "(warmup)" if i < 2 else ""
    if stats:
        print(f"{i}|{label}{warm}|{stats['total']}|{stats['janky']}|{stats['janky_pct']}%|"
              f"{stats['p50']}|{stats['p90']}|{stats['p95']}|{stats['p99']}")
        if i >= 2:
            results.append((i, label, stats))
    else:
        print(f"{i}|{label}{warm}|NO_DATA")
    subprocess.run(["adb", "-s", SERIAL, "pull", trace_path,
                    fr"d:\develop\Code\Codex\creation-reading-assistant\android\scripts\sort_retest_{i}.perfetto-trace"],
                   capture_output=True)
    time.sleep(0.3)

print()
print("=== 计入统计的 10 轮（每次排序切换全交互链窗口帧百分位）===")
for i, label, s in results:
    print(f"round {i:2d} -> {label}: total={s['total']} janky={s['janky']}({s['janky_pct']}%) "
          f"p50={s['p50']} p90={s['p90']} p95={s['p95']} p99={s['p99']}")
p95s = sorted(s["p95"] for _, _, s in results)
p50s = sorted(s["p50"] for _, _, s in results)
print(f"窗口 P95: 中位数={statistics.median(p95s)}ms 范围={p95s[0]}~{p95s[-1]}ms")
print(f"窗口 P50: 中位数={statistics.median(p50s)}ms 范围={p50s[0]}~{p50s[-1]}ms")
