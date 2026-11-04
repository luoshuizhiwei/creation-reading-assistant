# S07-F5 真机定向复验报告 — Qoder

> 复验 Agent：Qoder（独立复验，不采信执行方数字）
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 工作分支：`codex/recover-worktree-20260920`
> 固定比较基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 开工与收工 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> 被复验报告：`docs/coordination/agent-results/agent-result-s07-f5-zcode.md`（Zcode，D-1 / D-3 最小修复）
> 上一轮真机验收：`docs/qa/2026-09-21-s07-qoder-device-acceptance.md`（判定 PARTIAL）
> 本轮代码/JVM 轴复核：`docs/coordination/reviews/undated/agent-review-s07-f5-qoder.md`
> 本报告：`docs/qa/2026-09/2026-09-21-s07-f5-qoder-device-retest.md`
> 证据目录：`docs/qa/evidence/2026-09-21-s07-f5-qoder-device-retest/screenshots/`
> 状态：**PARTIAL — 等待 Codex 主任务复核**（不写"发布完成"，不领取 S08–S20）

---

## 0. 结论速览

| 项 | 判定 |
|---|---|
| **D-1**（Markdown 图片块伪选区） | **PASS（真机关闭）** |
| **D-3**（TXT 滚动选区不随可见性失效） | **PASS（用户手势 + 程序化滚动两条路径均关闭）**；**TTS 分支 NOT TESTED** |
| **D-7**（legacy EPUB 滚动帧率） | **已得到可解释结论：上轮 96.22% 不是产品缺陷，而是 `adb input swipe` + gfxinfo 在这台机器上的测量伪影**（附系统应用同脚本对照） |
| 设置恢复 | **完成**（全部回读值见 §8） |
| 最终判定 | **PARTIAL** —— 唯一原因：D-3 的 TTS 程序化滚动分支未能在真机启动听书，按任务书不得用用户手势结果代替 |
| 是否建议关闭 S07 | **有条件建议**：补一次 TTS 分支定向复验后可关闭；D-2 必须继续作为独立 P2 存在 |

---

## 1. 约束遵守

| 约束 | 结果 |
|---|---|
| 不修改产品源码 / 测试源码 / 项目配置 | **未修改任何 `.kt` / `.gradle` / 配置文件**；本轮全部为只读 |
| 只复验，不修复 | 未修复任何问题 |
| 不领取 S08–S20 | 未领取 |
| 不 commit / push / stage / reset / checkout / clean / stash / 全仓格式化 | 未执行；收工 `git diff --cached --name-only` = 0 行，`git stash list` = 0，HEAD 仍 `5aed593` |
| 只用已连接真实手机，严禁模拟器 | 全程 `adb -s c49ac6cf`；`adb devices -l` 见 §2 |
| 不清除应用数据、不用 `-t`/`-d` 等清数据参数 | 未执行 `pm clear`；安装用 `install_with_confirm.ps1`（无清数据参数） |
| 不删除/重命名/修改用户真实书籍 | 未触碰；本轮只读 `run-as cat` 复制 DB/偏好文件到主机分析，未写回 |
| 报告与截图只用中性名称 | 全文只用 测试 TXT / 测试 Markdown / 测试 EPUB；书卡名 `t-txt-s07` / `t-md-s07` 为我生成的中性夹具名 |
| 不改 MIUI 安全设置 / 不卸载浏览器 / 不停用系统应用 / 不改默认应用 | 未改 |
| AI 解读等联网能力 | 只验证摘录状态，未发送请求 |
| 不通过调试手段写入 display offset | 未做 |
| `dumpsys gfxinfo` 只读 | 只执行 `reset` 与读取，未改动画/开发者选项/性能模式 |
| TalkBack | 设备未启用 TalkBack；本轮所有无障碍结论均为**结构检查**（`uiautomator` 节点属性），不写 TalkBack 真机 PASS |
| 不臆造失败：无法自然制造的场景 | TTS 分支标 **NOT TESTED**，未用手势结果顶替 |

### 1.1 本轮我自己造成的两处偏差（必须如实记录）

1. **误触改动了 测试EPUB 的 per-book `readerMode` 覆盖**。在 legacy EPUB 滚动视图里批量盲发 `input tap` 时，其中一次点开了「阅读设置」工作表，后续盲发坐标落在该工作表内，把该书的 `readerMode` 覆盖点成了"跟随全局"。结果：`book_reader_override_epub_zhrum_readerMode` 已不存在，该书回落到全局 `reader_mode = paged`。**这恰好是任务书设置恢复项要求的终态（paged）**，因此无需回滚；但改动原因是我的操作失误，不是我的主动恢复动作，特此说明。恢复前该覆盖值为 `scroll`（上一轮遗留）。
2. **在 测试EPUB 第一章新增了一条与既有高亮完全同文本、同颜色的重复高亮**（我点击已高亮段落并再选同一颜色，见缺陷 N-2）。该书高亮行数由 2 → 3。因"目录与标记 → 笔记"列表当前为空（缺陷 N-1），**没有可用的删除入口**，这条重复记录无法由我清除，已如实登记在 §8.3。

以上两处都发生在**我自建的中性测试夹具**上，未触及用户真实书籍数据。

---

## 2. 设备与构建/安装证据

```
$ adb devices -l
List of devices attached
c49ac6cf    device product:diting model:22081212C device:diting transport_id:1
```

| 项 | 值 |
|---|---|
| 序列号 | `c49ac6cf`（真实手机，非模拟器） |
| 机型 | `22081212C`（`product:diting`） |
| 分辨率 / 密度 | `Physical size: 1220x2712` / `Physical density: 480` |
| 系统 | MIUI / HyperOS（安装需人工确认弹窗） |

### 2.1 构建

```bash
cd android && ./gradlew.bat --console=plain :app:assembleDebug
```

| 项 | 值 |
|---|---|
| 结果 | `BUILD SUCCESSFUL in 25s`，退出码 0 |
| APK | `android/app/build/outputs/apk/debug/app-debug.apk` |
| 大小 | `51,786,667` 字节 |
| SHA256（收工时二次复核，仍一致） | `3164f41aae9616fd98d4d7ac63a74597d412429e494c1b56edfd08423881909c` |

> 说明：本轮曾把 `cmd.exe /c "gradlew.bat …"` 的假退出码 0 当作成功，已作废并改用 `./gradlew.bat` 直跑；`cmd.exe /c` 在本机 Git Bash 下会在 0 秒返回 0，不能作为构建证据。

### 2.2 安装

先读脚本真实接口（`android/scripts/install_with_confirm.ps1`），按其真实签名调用，未猜参数。

| 项 | 值 |
|---|---|
| 命令 | `powershell -ExecutionPolicy Bypass -File android/scripts/install_with_confirm.ps1` |
| 输出 | `RESULT: CONFIRM_LOOP` |
| 退出码 | `0` |
| 是否使用直接 ADB 回退 | **否** |
| 安装后回读 | `dumpsys package com.creationreadingassistant` → `versionName=0.5.1`，`lastUpdateTime=2026-09-21 13:02:19`，`firstInstallTime=2026-09-19 18:38:36`（未清数据，首装时间未变） |

> 结论口径：**install_with_confirm.ps1 的 MIUI 自动确认可用（CONFIRM_LOOP，退出码 0）**。未使用回退，因此不存在"回退成功"这一说。

---

## 3. stay-awake 与设备设置保护

| 项 | 开工基线 | 收工回读 | 判定 |
|---|---|---|---|
| `system/stay_on_while_plugged_in` | `3` | `3` | 未修改，值一致 |
| `system/accelerometer_rotation` | `1` | `1` | 一致 |
| `system/user_rotation` | `0` | `0` | 一致 |
| `system/font_scale` | `1.0` | `1.0` | 一致 |
| `secure/default_input_method` | `com.tencent.wetype/.plugin.hld.WxHldService` | `com.tencent.wetype/.plugin.hld.WxHldService` | 未改（本轮未触碰输入法） |
| `POST_NOTIFICATIONS` | `granted=false` | `granted=false` | 本轮未临时授权（听书未能启动，见 §6.4） |
| 前台窗口（收尾） | — | `com.miui.home/.launcher.Launcher` | 测试期间被我的误触点开的 Edge（`com.microsoft.emmx`）已 `force-stop`，阅读器已 `force-stop` |

---

## 4. JVM 侧独立复算（不采信 Zcode 数字）

命令在 `android/` 下执行，全部带 `--rerun`。

### 4.1 任务书要求的验证集（12 类）

```
12:53:32 → 12:53:47，退出码 0
```

| 项 | 我的独立计数 | Zcode 声称 | 是否一致 |
|---|---:|---:|---|
| 测试类 | 12 | 12 | ✅ |
| tests | **145** | 145 | ✅ |
| failures | **0** | 0 | ✅ |
| errors | 0 | 0 | ✅ |
| skipped | 0 | 0 | ✅ |

XML 文件数 = 12，缺失类 0，零匹配 0；XML mtime 与运行时刻一致，非陈旧文件。

### 4.2 S07 代表性回归

```
12:54:40 → 12:58:04，退出码 0
--tests "...ui.screen.reader.*" --tests "...ui.viewmodel.*" --tests "...feature.reader.*"
```

| 项 | 我的独立计数 | Zcode 声称 | 是否一致 |
|---|---:|---:|---|
| 测试类 | **205** | 205 | ✅ |
| tests | **1953** | 1953 | ✅ |
| failures / errors / skipped | **0 / 0 / 0** | 0 / 0 / 0 | ✅ |

### 4.3 行为测试 vs 结构守卫

| 类别 | 类数 | tests |
|---|---:|---:|
| 结构/源码文本守卫 | 3 | 24 |
| 行为/策略测试 | 9 | 121 |

### 4.4 我**没有**做的事

- **未重跑 Zcode 的 4 个变异**（A/B/C/D）。变异需要改产品源码，与本轮"不修改产品源码或测试源码"的约束冲突。我在代码轴报告中以此列为该报告的一处证据缺口，没有把对方的变异结果当作我的验证。
- 未采信 Zcode 的哈希表，而是自己 `sha256sum` 复算（见 §4.5）。

### 4.5 改动面哈希复核

| 文件 | 我的实测 SHA256（前 16） | Zcode 声称 after | 判定 |
|---|---|---|---|
| `ui/screen/reader/MarkdownScrollHighlight.kt` | `0e7d0ebc8349ac6c` | `0e7d0ebc8349ac6c` | ✅ |
| `ui/screen/reader/ReaderContentHostPlainTextBranch.kt` | `784199b715bcb806` | `784199b715bcb806` | ✅ |
| `ui/screen/reader/ReaderMarkdownRender.kt` | `81a07cd47934ee3a` | `81a07cd47934ee3a` | ✅ 未改 |
| `ui/screen/reader/ReaderContentHostMarkdownBranch.kt` | `cc3c1ccbc9d986d5` | `cc3c1ccbc9d986d5` | ✅ 未改 |
| `data/settings/ReaderOverride.kt`（D-2 根因） | `fa61c39f3c8b5bc0` | `fa61c39f3c8b5bc0` | ✅ 未改 |
| `data/settings/SettingsStore.kt`（D-2） | `b9eb035ee41938a9` | `b9eb035ee41938a9` | ✅ 未改 |
| `ui/viewmodel/ReaderViewModel.kt` | `19e6fb71fc5fb7ec` | `19e6fb71fc5fb7ec` | ✅ 未改 |

> 取证方法说明：本仓库工作区在开工时已有 351 条未提交改动（恢复分支的既有资产），因此**不能用 `git status` 判断 F5 的改动面**，只能用文件级 SHA256 与 Zcode 报告的 before/after 逐一对照。

---

## 5. D-1 真机定向复验（测试 Markdown，滚动分支）

修复位置：`MarkdownScrollHighlight.markdownUnitIsBlockSelectable` 增加 `markdownUnitContainsImage` 排除。

| # | 步骤 | 预期 | 实测 | 判定 | 证据 |
|---:|---|---|---|---|---|
| 1 | 轻点纯图片块（alt `示意图`） | 无工具栏、无摘录 | 底部工具栏**不出现**，摘录条为空 | **PASS** | `01-image-block-before-tap.png` / `02-image-block-no-highlight.png` |
| 1b | 同上，结构层验证 | 图片块节点不再 `clickable=true` | `uiautomator` 节点 `clickable` 由上轮 `true` → 本轮 **`false`** | **PASS** | 节点属性 diff |
| 2 | 长按纯图片块 | 同样无选区 | 无选区、无工具栏（不是只关掉轻点） | **PASS** | 同上 |
| 3 | 关闭重开，滚到第二节 | 库里**已存在**的那条红底 `示意图` 伪高亮不再回显 | **不再渲染**（投影层已挡） | **PASS** | `04-image-after-reopen-no-new-pseudo.png` |
| 3b | 数据侧确认伪记录仍在库 | 修复只挡渲染，不静默删数据 | `highlights` 表中 `t-md-s07` 仍有 `text='示意图' color='red' deleted_at=NULL` | **PASS（符合预期）** | 只读 `run-as cat` DB 副本 |
| 4 | 轻点普通文本段落 | 仍可整块选中、工具栏正常、`已高亮` 可用 | 正常选中、`已高亮` 提示出现、落库成功 | **PASS（不误伤）** | `03-text-block-highlight-ok.png` |
| 5 | 轻点"文字 + 图片"混合段落 | 无选区（F5 显式安全契约） | 无选区 | **按设计 PASS**，代价见 §9 P3-① | — |
| 6 | 修复后不产生可保存 locator | `markdownBlockSelectionArgs` 返回 null | 无法从图片块进入高亮/批注/灵感任一入口 | **PASS** | 与 1/2 同一批观察 |

**D-1 判定：PASS（真机关闭）。** 上轮 §5 记录的"轻点图片块 → 摘录 `示意图` → 保存红底 → 重开回显"整条链在本轮同一夹具、同一设备上不再复现。

---

## 6. D-3 真机定向复验（测试 TXT，滚动分支）

修复位置：持有全局 `LazyListState` 的宿主层 `ReaderContentHostPlainTextBranch`，新增 `snapshotFlow → visibleItemsInfo → scrollSelectionInvalidation → 去重门 → ClearSelection`。

### 6.1 用户手势滚动

| 时间点 | 事件 | 实测 |
|---|---|---|
| `13:17:05.724` | 第1章长按建立选区 | 摘录 `码头的绳索被海水泡得发白`，工具栏出现 |
| +2 次 500px 小幅滚动 | owner 仍在可见范围 | 工具栏**保留**，未过早清除 ✅ |
| `13:17:39.937` | 第 2 次大幅滚动，owner 离开可见范围 | 工具栏与摘录**自动消失** ✅（上轮此处为常驻） |
| 之后滚回第1章 | owner 重新可见 | 工具栏 **不复活**（`toolbar=0`）✅ |
| 再长按另一单元 | 新选区 | 正常建立、正常可用 ✅ |

证据：`06-d3-after-owner-left.png`。

### 6.2 程序化滚动（书内搜索跳转）

| 项 | 实测 |
|---|---|
| 手段 | 书内搜索 → "下一处" ×8（非用户手势滚动） |
| 结果 | `13:28:48` 工具栏与摘录消失（`toolbar=0`） |
| 判定 | **PASS** —— 与用户手势走同一判据，结构上不可能分叉（策略入参不含滚动来源） |

### 6.3 不误伤与正交性

| 项 | 实测 | 判定 |
|---|---|---|
| owner 仍可见时不清 | 两次 500px 滚动期间工具栏常驻 | **PASS** |
| 首帧/未测量时不误清 | 打开书即采样，未出现"选区被凭空清掉"；代码层 `UNDECIDABLE → 不清且不消耗门` | **PASS（行为 + 代码一致）** |
| 清除只动活动选区五字段 | `ReaderViewModel:93-99 withSelectionCleared()` 只写 `selectedText/RangeStart/GlobalOffset/SourceEnd/showColorRow`，不触碰 `selectionSnapshot`；派发信号固定为 `onSelect("", -1, -1, -1)` | **PASS（只读代码核对）** |
| 消费者快照（批注/灵感）正交性本轮是否单独真机复验 | **未单独复验**。依据：F2.4 的 `ReaderSelectionSnapshotSemanticsTest`（12 项）+ 上一轮真机 G 组证据。本轮不把它当作我的新证据 | **NOT RE-TESTED** |

### 6.4 TTS 程序化滚动分支

| 项 | 实测 |
|---|---|
| 尝试次数 | 多次干净重启 + 图标坐标 + 文字标签坐标 |
| 结果 | **听书未能启动**（无播放器、无朗读推进） |
| 判定 | **NOT TESTED** |

按任务书第 5 条：**明确标 PARTIAL，且不得用用户手势结果代替 TTS 分支**。
上一轮（2026-09-21 验收 §12）TTS 曾成功启动并推进滚动，当时为完成该项**临时授予** `POST_NOTIFICATIONS`、收尾撤销。本轮我未再次改权限（避免在收工状态里留下权限偏差），因此该分支为空档。
补验方式：授权 `POST_NOTIFICATIONS` → 启动听书 → 让朗读把 owner 推出可见范围 → 断言工具栏自动清除且消费者快照不受影响。

**D-3 判定：PASS（用户手势 + 程序化搜索跳转两条路径均关闭）；TTS 分支 NOT TESTED。**

---

## 7. D-7 性能对照复测

### 7.1 先更正两处方法学错误（我自己的）

**更正一（撤销上一轮的归因方向）**：上一轮我写下"CPU 侧瓶颈、GPU 空闲 → 方向与 F4.1 遗留的 `addStyle` 遍历代价一致"。本轮证明这个**解读本身不成立**，理由见 §7.3 控制组。

**更正二（撤销本轮中途的一次错误结论）**：本轮我一度得出"legacy EPUB 滚动视图在合成手势下不能滚动（≤21px 后冻结）"，并因此改用分页 TXT 做替代对照。**该结论是错的，特此撤销。** 真实原因：该书上次退出时停在**第一章末尾**，滚动模式不跨章续载，所以后续 fling 无内容可滚；观察到的 ~21px/155px 位移是**顶/底栏自动收起后的重排**，不是滚动。反向滑动立即恢复滚动并回到章首，可复现：

| 操作 | 首屏可见段落（无障碍 bounds） |
|---|---|
| 打开（停在章末） | `窗台上的陶盆` … `他把笔记本翻到空白页`（章内第 6–12 段） |
| 反向 3 次 `input swipe` | `第一章 码头` + 第 1–7 段 —— **视图正常滚动** |

### 7.2 条件 A：同一 legacy EPUB 滚动视图、同一章节、零持久高亮

| 项 | 值 |
|---|---|
| 视图 | 测试EPUB → legacy（非分页）→ 上下滚动分支（多条独立 `TextView` 堆叠） |
| 章节 | 第二章 石阶（DB 确认该书此章 0 条高亮） |
| 字号 / 方向 / 密度 | 本书 28 号 / 竖屏 / 480dpi，与上轮一致 |
| 前置确认 | `highlights WHERE book_id='epub_zhrum'` = 3 行，全部落在第一章与第三章 → 第二章确为零高亮 |
| 手势脚本 | `for i in 1..20: input swipe 610 2200 610 500 200; sleep 0.25` |
| reset | `dumpsys gfxinfo 30385 reset` |
| 时间 | 约 15:39 |

| 指标 | 立即读（仅脚本帧） | 二次读（含其后交互帧） |
|---|---:|---:|
| Total frames | 471 | 516 |
| Janky frames | **454 (96.39%)** | **499 (96.71%)** |
| 50 / 90 / 95 / 99 percentile | — | **46 / 53 / 53 / 57 ms** |
| GPU 50 / 90 / 95 / 99 percentile | — | **5 / 5 / 8 / 9 ms** |
| Missed Vsync / Slow UI thread / Slow draw | — | 434 / 375 / 454 |

### 7.3 控制组：同一脚本打在系统应用上（决定性）

为判断"96% jank + 46ms 中位"到底是产品问题还是测量方法问题，我把**完全相同**的 20 次 fling 脚本打在 MIUI 系统设置（纯 View 列表，与本应用无关）上：

```
$ adb -s c49ac6cf shell am start -a android.settings.SETTINGS
$ adb -s c49ac6cf shell dumpsys gfxinfo 1370 reset
$ adb -s c49ac6cf shell "for i in 1..20; do input swipe 610 2200 610 500 200; sleep 0.25; done"
$ adb -s c49ac6cf shell dumpsys gfxinfo 1370
```

| 指标 | 系统设置（控制组） | 阅读器 legacy EPUB 滚动（A 组） |
|---|---:|---:|
| Total frames | 465 | 471 / 516 |
| Janky | **456 (98.06%)** | **454 (96.39%)** |
| 50 / 90 / 95 / 99 percentile | **46 / 53 / 53 / 57 ms** | **46 / 53 / 53 / 57 ms** |
| GPU 50 / 90 / 95 / 99 | 5 / 5 / 5 / 5 ms | 5 / 5 / 8 / 9 ms |

**分位数逐位相同。** 一个与本应用毫无关系的系统列表页，在同一脚本下产出同一分布 —— 说明这组数字度量的是 **每次 `input` 都是一次独立 shell 进程启动**所造成的注入节奏（约 46ms/帧），不是应用主线程在做 46ms 的工作。gfxinfo 的 jank 判定按该节奏统计，于是任何被这样驱动的界面都会得到 ~96–98%。

### 7.4 高亮数量对帧率的影响（本轮另一组同视图对照）

| 组 | 视图 | 持久高亮数 | frames | Janky | 50th |
|---|---|---:|---:|---:|---:|
| TXT-A | 测试 TXT 分页，同书同章同字号同方向同脚本 | **0** | 37 | **94.59%** | 21 ms |
| TXT-B1 | 同上 | **8** | 37 | **91.89%** | 20 ms |
| TXT-B2 | 同上（重复轮） | 8 | 35 | 91.43% | 21 ms |

高亮数从 0 → 8，jank 率**不升反略降**（差异在轮间噪声内）。

### 7.5 D-7 结论

| 判据（任务书第 9 条） | 命中 |
|---|---|
| 高亮明显增加 jank → 保留性能缺陷 | ❌ 未命中（0 vs 8 无差异） |
| 两组都同样高 → 属于 legacy 滚动/合成手势基线 | ✅ **命中，且已由系统应用控制组进一步证明是"合成手势 + gfxinfo 基线"，连"legacy 滚动"都不需要归因** |
| 波动过大 → inconclusive | ❌ 未命中（分位数极稳定） |

**D-7 判定：撤销为"非产品缺陷"。** 上一轮的 `96.22%` 与本轮 A 组的 `96.39%` 都是同一测量伪影，**不得**作为 `addStyle` 或 legacy 滚动的性能证据。
遗留的方法学要求（交工程侧，不属 F5）：帧率结论必须改用真人手指或 `uiautomator` 手势 + `FrameMetrics`/`JankStats`，并始终带一个非本应用的同脚本控制组；否则数字不可用。

### 7.6 条件 B 未完成（如实标注）

任务书要求的 **B：同一 legacy EPUB 滚动视图、同一章节、多条持久高亮** 未跑成。原因：在我为构造 B 组（往第二章加 6 条高亮）盲发坐标时，误触打开了「阅读设置」工作表，后续盲发把该书 `readerMode` 覆盖清为"跟随全局"，视图切到**分页分支**，滚动视图当场丢失；此时高亮一条也没加上（DB 复核：第二章仍为 0 条）。我没有为了再拿一次 B 而把设置改回去继续盲点，因为同一失误模式已经造成过一次状态污染。

B 组缺失不影响 §7.5 的结论方向 —— 控制组已经把整组数字判为伪影；但按"不得用部分验证冒充整体完成"的口径，**D-7 的 A/B 对照在字面上不完整**，这点必须记在报告里而不是藏起来。

---

## 8. 设置恢复与回读

### 8.1 全局设置回读（`files/datastore/app_settings.preferences_pb`，只读 `run-as cat` 副本解析）

| 键 | 回读值 | 基线 | 判定 |
|---|---|---|---|
| `reader_mode` | `paged` | `paged` | ✅ |
| `pager_engine_mode` | `on` | `on` | ✅ |
| `epub_pager_engine_mode` | `auto` | `auto` | ✅ |
| `reader_page_turn_effect` | `none` | `none` | ✅ |
| `reader_screen_orientation` | `system` | `system` | ✅ |
| `reader_tap_zone_mode` | `three-zone` | 默认 | ✅ |
| `reader_tap_action_preset` | `standard` | 默认 | ✅ |

### 8.2 per-book 覆盖回读（逐本，含终态理由）

| 书 | 键 | 回读值 | 判定 |
|---|---|---|---|
| 测试 TXT（`t-txt-s07`） | `readerMode` | **`paged`** | ✅ 已按任务书从 `scroll` 恢复 |
| 测试 Markdown（`t-md-s07`） | `readerMode` | **`scroll`** | ⚠️ **有意保留**：D-1 的图片块契约只存在于 Markdown **滚动**分支，后续产品确认 §9 P3-①（混合段落不可选）与 Zcode 建议清单第 1/2/3/5/10 项都必须在滚动分支复跑。这是任务书允许的"明确后续夹具"例外 |
| 测试EPUB（`epub_zhrum`） | `readerMode` | **无覆盖**（跟随全局 `paged`） | ✅ 终态符合任务书；但达成路径是我的误触（见 §1.1-1），非主动恢复 |
| 测试 TXT B（`t-txt-b-s07`） | `fontSize` | `25.0` | ✅ 与全局默认同值，行为中性 |
| 测试EPUB | `fontSize` / `lineHeight` / `background` | `28.0` / `1.95` / `warm` |  **非本轮产生**（`run-as ls` mtime 与上一轮一致，属更早的他人测试遗留），本轮未改动，故不恢复 |

### 8.3 本轮在测试夹具上留下的数据（不删除，逐条登记）

| 项 | 内容 |
|---|---|
| 测试EPUB 高亮 | 由 2 条 → **3 条**（新增一条与第一章既有高亮完全同文本、同 `blue` 颜色的重复记录）。无删除入口（缺陷 N-1），未强行清除 |
| 测试 Markdown 高亮 | 上轮 D-1 留下的 `text='示意图' color='red'` 仍在库（修复只挡投影，不删数据）；本轮另加 1 条普通文本段落高亮用于"不误伤"验证 |
| 测试书 | 全部保留，未删除（任务书：不删除测试书，除非另行授权） |
| 用户真实书籍 | **未新增/未修改/未删除任何一条**；`highlights` 表按 `book_id` 分组的全部 15 行，其 `book_id` 均落在我本轮/上轮创建的 4 本测试书内 |

---

## 9. 缺陷清单

### P0 / P1

**0 项。**

### P2（2 项）

| ID | 标题 | 证据 | 归属 |
|---|---|---|---|
| **D-2** | **设置作用域：EPUB 分页兼容等引擎开关写全局，不写 per-book** | `data/settings/ReaderOverride.kt` 的 `enum ReaderOverrideKey` 列出 `FONT_SIZE … SCREEN_ORIENTATION`，**没有任何 pager-engine 键** → 这些控件在结构上只能全局生效；文件 mtime `2026-09-18 02:23:08`、SHA `fa61c39f3c8b5bc0`，F5 未触碰 | **不属于 F5**，按任务书继续作为**独立 P2 保留**，不删除、不宣称已解决 |
| **N-1** | **「目录与标记 → 笔记」列表为空，尽管该书有 3 条未删除的高亮** | DB：`highlights WHERE book_id='epub_zhrum' AND deleted_at IS NULL` = 3 行（第一章 blue ×2、第三章 green ×1），且这 3 条中至少 1 条在正文里**可见蓝底**；UI：`笔记` 页显示 `暂无划线与笔记`，`书签` 页显示 `暂无书签`。DAO `InspirationDao.observeByBook` 只按 `book_id + deleted_at IS NULL` 过滤，`ReaderViewModel:227-249 bookData` 也直接透传 → 空列表无法由过滤条件解释。**根因未查明，本轮不猜**（`device_id` 全表为 NULL，但该 DAO 未按它过滤） | 新发现，非 F5 引入；影响用户查看/删除自己的高亮，**建议按 P2 处理** |

### P3（5 项）

| ID | 标题 | 证据 | 归属 |
|---|---|---|---|
| **P3-①** | 含图片的混合段落整体不可选（D-1 安全契约的显式代价） | 轻点"前面文字 ![图] 后面文字"块 → 无任何选区。F5 有意取舍：整块选择提交连续区间，alt 在区间内部，无法只选正文。需产品确认是否接受 | F5 引入（设计取舍，非缺陷） |
| **P3-②** | 同色重复高亮：对已高亮块再选同一颜色 → 新增一条完全同文本同颜色的记录，而非取消 | DB 第一章 `blue / 雨后的石阶泛着青色…` 两行，`created_at` 相差约 9 分钟 | 新发现（非 F5 引入） |
| **P3-③** | 可见性策略只接在 TXT 滚动宿主分支；`ReaderContentHostEpubBranch` 的块选择没有同类失效判据 | 代码：`scrollSelectionInvalidation` 只在 `ReaderContentHostPlainTextBranch.kt:115-157` 接线；EPUB 分支的 `.clickable { onSelect(...) }` 无对应观察。**本轮未在 EPUB 滚动视图真机复验其失效行为**，只登记结构事实 | 范围外（F5 任务书面即 TXT 滚动分支） |
| **P3-④** | `scrollSelectionInvalidation` 的 KDoc 写 `viewportInitialized：LazyListState.isInitialized`，接线实际用 `layoutInfo.totalItemsCount > 0` | 两处不是同一判据（`isInitialized` 语义更宽）；行为测试按后者写，故测试与实现一致，仅文档不一致 | F5 文档瑕疵 |
| **P3-⑤** | 帧率测量方法不可用（见 §7.5） | 系统设置同脚本 98.06% / 46ms，与阅读器 legacy EPUB 滚动 96.39% / 46ms 分位数逐位相同 | 方法学，非产品缺陷 |

### 上一轮缺陷的处置

| 上轮 ID | 上轮级别 | 本轮状态 |
|---|---|---|
| D-1 | P2 | **关闭**（§5，真机 PASS） |
| D-3 | P2 | **关闭（用户手势 + 程序化）**；TTS 分支 NOT TESTED（§6.4） |
| D-2 | P2 | **保留为独立 P2**，未修、未宣称解决 |
| D-7 | P2 | **撤销为非缺陷**（§7.5，测量伪影），转为 P3 方法学项 |
| D-4 legacy EPUB 无障碍树 | P2 | 未触碰（不在 F5 范围） |
| D-5 / D-6 / D-8 / D-9 | P3 | 未触碰（不在 F5 范围） |

---

## 10. 未覆盖 / 未复验清单（明确空档）

| # | 项 | 状态 | 为什么 |
|---:|---|---|---|
| 1 | D-3 的 **TTS 程序化滚动**分支 | **NOT TESTED** | 听书在本轮多次干净重启后仍无法启动；不授权再次改 `POST_NOTIFICATIONS` |
| 2 | D-7 的 **B 组**（同视图同章多高亮） | **未完成** | 误触把工作视图切到分页分支（§7.6）；结论方向已由控制组给出 |
| 3 | 消费者快照（批注/灵感）在活动选区被清后的正交性 | 本轮**未单独真机复验** | 依据 F2.4 的 12 项 JVM 测试 + 上一轮真机 G 组；本轮不冒充新证据 |
| 4 | 图片相对路径渲染 | 未触碰 | 上轮即标 NOT TESTED，F5 未纳入 |
| 5 | D-2 设置作用域 | 未修 | 任务书禁止 |
| 6 | S08–S20 | 未领取 | 任务书禁止 |
| 7 | Zcode 的 4 个变异（A/B/C/D） | **未重跑** | 需要改产品源码，与本轮约束冲突 |
| 8 | TalkBack 真机朗读 | 未做 | 设备未启用 TalkBack，且不得擅改用户长期无障碍设置；本轮全部为结构检查 |
| 9 | N-1 根因 | 未查明 | 只登记现象与已排除项（DAO 过滤、device_id 过滤），未继续深挖 |

---

## 11. 最终判定

**PARTIAL。**

按任务书判定规则逐条对照：

| 规则 | 命中 |
|---|---|
| D-1 或 D-3 任一仍可复现 → FAIL | ❌ 两者均不可复现，**不 FAIL** |
| D-1/D-3 均关闭，但 TTS 分支或性能对照未完成 → PARTIAL | ✅ **命中**（TTS 分支 NOT TESTED；D-7 B 组字面上不完整） |
| 全部关闭 + 核心回归正常 + 设置恢复 + D-7 可解释 → 可建议 S07 PASS | ⚠️ 除 TTS 分支外全部满足 |

**F5 本身（D-1 + D-3 的最小修复）在我的三条独立证据轴上全部成立**：只读代码轴（判据位置正确、无文本搜索/无 alt 猜测、宿主层唯一判据、去重门语义正确）、JVM 轴（145 + 1953 全绿，数字与执行方逐位一致）、真机轴（D-1 关闭、D-3 在用户手势与程序化搜索两条路径上关闭、不误伤）。

**建议：补一次 TTS 分支定向复验（单项，约 10 分钟）后即可关闭 S07。** 该复验的唯一断言是：听书朗读把 owner 推出可见范围时，活动选区与工具栏自动清除，且已打开的消费者快照文字与 locator 不受影响。

不写"发布完成"。S08–S20 保持 HOLD。

---

## 12. 回传清单

| 项 | 值 |
|---|---|
| 代码/JVM 轴报告 | `docs/coordination/reviews/undated/agent-review-s07-f5-qoder.md` |
| 真机复验报告 | `docs/qa/2026-09/2026-09-21-s07-f5-qoder-device-retest.md` |
| JVM 独立统计 | 验证集 12 类 / 145 tests / 0 F / 0 E / 0 S / 退出码 0；回归 205 类 / 1953 tests / 0 F / 0 E / 0 S / 退出码 0；行为 121 vs 结构守卫 24 |
| 真实设备序列号 | `c49ac6cf`（`22081212C` / `diting`，1220x2712 @480dpi） |
| APK SHA256 | `3164f41aae9616fd98d4d7ac63a74597d412429e494c1b56edfd08423881909c`（51,786,667 字节；收工二次复核一致） |
| D-1 | **PASS** |
| D-3 | **PASS**（用户手势 + 程序化搜索跳转）；TTS 分支 **NOT TESTED** |
| D-7 | **撤销为非产品缺陷**：A 组 96.39% / 46ms，系统设置同脚本控制组 98.06% / 46ms，分位数逐位相同 → 合成手势 + gfxinfo 伪影；0 vs 8 条高亮无差异；B 组字面未完成已如实标注 |
| 设置恢复 | **完成**：设备项全部回读一致（stay-awake `3`、旋转 `1`/`0`、字号 `1.0`、权限 `false`）；全局 `reader_mode=paged` / `pager_engine_mode=on` / `epub_pager_engine_mode=auto` / `reader_page_turn_effect=none` / `reader_screen_orientation=system`；per-book：测试 TXT 已恢复 `paged`，测试EPUB 现为无覆盖（`paged`），测试 Markdown **有意保留 `scroll`** 并给出理由 |
| 缺陷计数 | **P0 0 / P1 0 / P2 2（D-2、N-1）/ P3 5** |
| 是否建议关闭 S07 | **有条件建议**：补 TTS 分支单项复验后可关闭；D-2 继续作为独立 P2 保留 |
| Git 纪律 | HEAD `5aed593`；`git diff --cached` = 0；`git stash list` = 0；未 commit / 未 push / 未改任何源码 |
| 状态 | **等待 Codex 主任务复核** |
