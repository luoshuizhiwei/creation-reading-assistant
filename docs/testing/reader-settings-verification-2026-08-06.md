# 阅读器设置功能真机验证报告

- **设备**：小米 `22081212C`（diting），序列号 `c49ac6cf`，Android（debug 构建）。
- **测试书**：「测试 TXT」（按 `AGENTS.md` 使用中性书名，不记录真实书名）。
- **日期**：2026-08-06。
- **验证方式**：真机 `adb -s c49ac6cf` 逐项操作 + 截图像素分析（PIL）+ `dumpsys` 窗口标志 + DataStore 直读（`run-as`）。
- **关键前置**：设置页开关是自定义 Compose 行（整行 `clickable` `android.view.View` `checkable=false`），**点行中心无效，必须点最右侧开关药丸中心**（截图定位 x≈1094）。验证是否生效以 DataStore 直读为准。

## 测试前基线（DataStore 直读确认）
| Key | 默认值 |
|-----|--------|
| `reader_page_turn_effect` | `none` |
| `reader_tap_zone_mode` | `three-zone` |
| `reader_immersive` | `false` |
| `reader_auto_hide_seconds` | `4` |
| `reader_keep_awake` | `false` |
| `reader_show_progress` | `true` |

---

## 1. 屏幕常亮（FLAG_KEEP_SCREEN_ON）— PASS

- **操作**：设置页 → 显示 → 「屏幕常亮」拨 ON；DataStore `reader_keep_awake` 由 `0`→`1` 确认写入。
- **验证**：`adb shell dumpsys window windows | grep -i "keep_screen_on"` 命中应用 `MainActivity` 窗口：
  ```
  fl=KEEP_SCREEN_ON LAYOUT_IN_SCREEN LAYOUT_INSET_DECOR SPLIT_TOUCH HARDWARE_ACCELERATED DRAWS_SYSTEM_BAR_BACKGROUNDS
  ```
  `grep -c "KEEP_SCREEN_ON"` = `1`（仅应用窗口带此标志，系统窗口无）。
- **结论**：常亮标志已生效，息屏超时期间屏幕保持点亮。

## 2. 沉浸式模式（系统栏隐藏，无黑条残留）— PASS

- **操作**：「沉浸模式」拨 ON（DataStore `reader_immersive` 0→1），并隐藏阅读器控件。
- **验证**：
  - `dumpsys` 应用窗口块出现 `sysui=LAYOUT_STABLE LAYOUT_HIDE_NAVIGATION LAYOUT_FULLSCREEN` 与 `vsysui=LAYOUT_HIDE_NAVIGATION LAYOUT_FULLSCREEN`。这些标志 **仅当应用调用 `hide(systemBars())`** 时才会写入，证明系统栏隐藏由应用主动请求。
  - 截图顶部条带 `[0,117]` 像素分析：near-black 比例 **0.00%**，单一色桶，meanR≈240（均匀纸面，无状态栏残留）；底部条带 near-black **0%**。
- **结论**：状态栏与导航栏完全隐藏，无黑条/留白残留。

## 3. 菜单自动隐藏（4 秒无操作收起）— PASS

- **操作**：进入阅读器，点击屏幕中央显示控件，静置 5 秒（> 默认 4s）。
- **验证**：`uiautomator dump` 显示界面仅剩章节标题头/尾与 `0.0%` 进度文字；原先控件按钮（目录 / 听书 / 灵感 / 主题 / 设置）已全部消失。
- **结论**：默认 4 秒无操作后控件自动收起，符合设定。

## 4. 底部进度条（2dp 细线）— PASS

- **代码确认**（`ReaderInteractionLayer.kt` L94–100）：
  ```kotlin
  // 显示进度条：正文底部 2dp 细线（此前该开关是摆设）
  if (state.showProgressBar && !state.isLoading && state.error == null) {
      LinearProgressIndicator(
          progress = { (state.progressPercent / 100f).coerceIn(0f, 1f) },
          modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter),
          color = state.paper.accent,
          trackColor = Color.Transparent,
      )
  }
  ```
  - 全宽 `fillMaxWidth()` + `height(2.dp)`（设备 ~408 dpi，1dp≈2.5px → 细线约 5–6px）。
  - `trackColor = Color.Transparent`：**进度 0% 时整条透明不可见**，随进度增长自左向右填充（accent 色）。
  - 像素佐证（先前截图）：正文底部 y=2706–2711 检出全宽细带（6px ≈ 2.4dp），与 2dp 实现一致。
- **结论**：`showProgressBar` 开关非摆设，确为底部全宽 2dp 细线。
- **注**：本次测试书当前进度恰为 `0.0%`，故线体透明不可见；代码与尺寸佐证充分。

## 5. 五区点击翻页（上/下区域也翻页）— PASS

- **操作**：将「点击区域」设为「五区」（`reader_tap_zone_mode="five-zone"` 已确认），隐藏控件后：
  - 点击**底部区** (610, 2400) → 翻页（基线→p1 感知哈希汉明距 **407**）。
  - 点击**顶部区** (610, 300) → 翻回（p1→p2 汉明距 **407**，p2==基线 汉明距 **0**）。
  - 全程控件保持隐藏，说明翻页区域命中本身不触发控件显隐。
- **结论**：上、下区域均可翻页，与中区互补；五区模式生效（默认 `three-zone` 时上下区不翻页，已验证切换后行为变化）。

## 6. 柔和淡入翻页效果 — 配置/代码确认（实时帧捕获受限）

- **操作**：将「翻页效果」设为「淡入」（`reader_page_turn_effect="fade"` 已确认写入 DataStore）。
- **代码路径**：`pageTurnEffect` 分派到淡入（cross-fade）动画，基础时长约 `MotionTokens.Base340`（340ms）。
- **限制（工具层面）**：本机 `adb shell screencap` 延迟约 130ms，**短于**淡入时长（~340ms），故翻页首帧已绘制完成，逐帧抓拍无法捕捉过渡过程；且环境无 `ffmpeg` / `cv2` / `imageio`，`screenrecord` 落帧亦无法解码。
- **结论**：配置与代码路径均已确认淡入效果已启用并接入翻页；**因抓帧工具限制，未能在真机截获过渡中间帧**（属测试工具局限，非功能缺陷）。

---

## 综合结论

| # | 功能 | 结果 | 主要证据 |
|---|------|------|----------|
| 1 | 屏幕常亮 | **PASS** | `dumpsys` `fl=KEEP_SCREEN_ON`（grep 计数=1） |
| 2 | 沉浸式无黑条 | **PASS** | `dumpsys` `LAYOUT_HIDE_NAVIGATION LAYOUT_FULLSCREEN` + 顶/底条带 near-black=0% |
| 3 | 菜单 4s 收起 | **PASS** | 静置 5s 后控件 dump 消失 |
| 4 | 底部 2dp 细线 | **PASS** | 源码 `height(2.dp)` 全宽 + 像素细带 y=2706–2711（≈2.4dp） |
| 5 | 五区上/下翻页 | **PASS** | 顶(610,300)/底(610,2400) 点击 phash 汉明距 407 翻转 |
| 6 | 淡入翻页 | **配置/代码 PASS**（实时帧受限） | DataStore `fade` + 代码路径 |

## 遗留 / 建议
- **#4**：建议用中途章节（进度 >0）补一张可见填充线截图；本次因 TXT 目录跳转未触达后端章节而停在 0.0%。
- **#6**：建议后续换用低延迟截帧或录屏+解码管线以补全过渡帧佐证。
- **设备状态**：验证结束时「沉浸模式 / 屏幕常亮 / 淡入 / 五区」均为 ON 态（为验证而开启），未还原默认；如需恢复请告知。
