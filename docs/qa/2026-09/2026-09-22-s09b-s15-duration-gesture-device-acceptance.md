# S09-B + S15-F3 真实手机验收（窗口 D / Qoder，2026-09-22）

- 任务：S09-B + S15-F3 统一真机验收（**只验收，不修复**）
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`｜分支 `codex/recover-worktree-20260920`｜固定基点 `5aed5939600e491843e3c44453921bd6273a233c`
- 时间：2026-09-22 14:33–16:08（本机 +08:00）
- 证据目录：`docs/qa/evidence/2026-09-22-s09b-s15-duration-gesture-device-acceptance/`（19 个文件，19 MB）
- **最终判定：PASS WITH LIMITATIONS。** 人工真机矩阵（S09-B 三区/五分区/左右手 + 边界、S15-F3 五个安全入口 + 会话 pill + 阅读历史行 + 首页档案卡 + 单位跟随）全部按契约通过；
  **四个 instrumentation 测试类在本机 0 条完成（设备环境阻塞，不是产品失败）**，TalkBack 语音播报、已读完时长 pill、320dp 受控宽度等项记为未覆盖（第十节逐条列出）。

---

## 1. 设备

```
$ adb devices -l
List of devices attached
c49ac6cf               device product:diting model:22081212C device:diting transport_id=1
```

| 项 | 值 |
|---|---|
| serial | `c49ac6cf`（全程每条命令都显式 `-s c49ac6cf`） |
| 型号 / product | `22081212C` / `diting`（真机，非 `127.0.0.1:*`、非 MuMu、非任何 emulator 条目） |
| Android / SDK / MIUI | 15 / 35 / V816 |
| 屏幕 | Physical 1220×2712 @480dpi（≈406.7×904dp） |

`adb devices -l` 输出里**只有一个设备**且状态为 `device`，无 unauthorized/offline，因此不存在需要报 blocker 的连接问题；未使用任何虚拟设备。

## 2. 构建

```
.\gradlew.bat --console=plain :app:assembleDebug :app:assembleDebugAndroidTest
EXITCODE=0
BUILD SUCCESSFUL in 36s
72 actionable tasks: 16 executed, 7 from cache, 49 up-to-date
```

| 产物 | 路径（构建输出实际路径，与预期一致） | 大小 | SHA-256 |
|---|---|---|---|
| 主 APK | `android/app/build/outputs/apk/debug/app-debug.apk` | 52 481 207 B | `ebb18cf1cd312fe158d0c4cfde91a757300d5581fbc9aaeb335cddbe8e4e1bf9` |
| androidTest APK | `android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 1 469 896 B | `e2282ea552ad1955bdcd689119c68f4e20568816c0c09534893fabdf7991f493` |

构建时间 14:33（两个 APK 的 mtime 均为 14:33），安装与验收在其后进行。

## 3. 包名与安装

包名一律从**构建产物 manifest** 读出，不靠经验猜（`aapt2 dump packagename` + `apkanalyzer manifest print`，原文见 `01-packages.txt`、`01b-manifests.txt`）：

```
main application id      : com.creationreadingassistant
test application id      : com.creationreadingassistant.test
instrumentation target   : android:targetPackage="com.creationreadingassistant"
runner                   : android:name="androidx.test.runner.AndroidJUnitRunner"
```

两次安装都用仓库脚本，**均走 MIUI 安装确认循环成功，没有触发直装回退**：

| 步骤 | 命令要点 | 退出码 | 结果标记 |
|---|---|---|---|
| 主 APK | `install_with_confirm.ps1 -Apk .\app\build\outputs\apk\debug\app-debug.apk -Serial c49ac6cf -PackageName com.creationreadingassistant` | **0** | `RESULT: CONFIRM_LOOP (installed through the MIUI installer UI)` + `Installed com.creationreadingassistant successfully.` |
| androidTest APK | 同脚本 `-TestApk -PackageName com.creationreadingassistant.test`（真实 test package） | **0** | `RESULT: CONFIRM_LOOP …` + `Installed com.creationreadingassistant.test successfully.` |

- 循环内实际点到的控件（脚本自述）：`android:id/button3` → `com.miui.packageinstaller:id/checkbox` → `com.miui.packageinstaller:id/second_button`，**没有要求用户手工点击，也没有改 MIUI 安全设置**。
- **未出现 `FALLBACK_DIRECT_INSTALL`，未使用直接 `adb install`**，因此本轮不构成"MIUI 自动确认恢复"的额外证据之外的路径。
- 安装后复核（`02-install-verify.txt`）：两个 `pm path` 都命中新目录；`pm list instrumentation` 含 `com.creationreadingassistant.test/androidx.test.runner.AndroidJUnitRunner (target=com.creationreadingassistant)`。
- 脚本已知副作用：安装会把 `POST_NOTIFICATIONS` 静默置为 `granted=true`（本轮在安装后实测到 `granted=true`）。收尾已 `pm revoke` 并回读为 `granted=false`（第 9 节）。**说明：本轮没有在安装之前抓取该权限的原值**，`false` 依据的是上一轮同机实测（记忆记录），不是本轮 pre-snapshot。

## 4. Instrumentation（四类逐条，全部未跑完）

命令模板（每条单独执行，未合并）：

```
adb -s c49ac6cf shell am instrument -w -r -e class <类> \
  com.creationreadingassistant.test/androidx.test.runner.AndroidJUnitRunner
```

| # | 类 | numtests | started | completed | ADB/timeout rc | INSTRUMENTATION_CODE | 判定 |
|---|---|---|---|---|---|---|---|
| 1 | `ui.screen.ReaderScreenTest` | 37 | 1 | **0** | 124（240 s 超时）；第二次 124（150 s） | 未出现（进程仍在等待） | **BLOCKED（设备环境）** |
| 2 | `ui.screen.stats.DurationUnitToggleEntryTest` | 1 | 1 | 1（`initializationError`） | 0 | `-1`（跑完） | **测试寻址缺陷**（见下） |
| 2b | 修正后的真实 FQCN `ui.screen.stats.components.DurationUnitToggleEntryTest` | 10 | 1 | **0** | 124（120 s） | 未出现 | **BLOCKED（设备环境）** |
| 3 | `ui.screen.shelf.DurationPillGestureTest` | 7 | 1 | **0** | 124（90 s） | 未出现 | **BLOCKED（设备环境）** |
| 4 | `ui.screen.readinghistory.MyReadingTimelineRowGestureTest` | 8 | 1 | **0** | 124（90 s） | 未出现 | **BLOCKED（设备环境）** |

合计 **62 条计划用例（37+10+7+8），完成 0 条**。日志：`03-inst-ReaderScreenTest.txt`、`03-inst-ReaderScreenTest-attempt1-bgactivity.txt`、`04-inst-DurationUnitToggleEntryTest.txt`、`04a-inst-…-wrongFQCN-initializationError.txt`、`05-inst-DurationPillGestureTest.txt`、`06-inst-MyReadingTimelineRowGestureTest.txt`、`08-logcat-instrumentation-window.txt`。

**为什么判"设备环境阻塞"而不是产品/测试失败**（原始 logcat，`08-…txt`）：

```
TestRunner: run started: 37 tests
TestRunner: started: legacyEpubThreeZoneLastChapterRightZoneIsSilentNoOp(...ReaderScreenTest)
ActivityTaskManager: Abort background activity starts from 10949
ActivityTaskManager: START u0 {… cmp=com.creationreadingassistant/androidx.activity.ComponentActivity} … result code=102
```

runner 正常 `onCreate/onStart`、类名与首个方法都已打印，但 `createComposeRule` 需要的 `androidx.activity.ComponentActivity` 被 MIUI 以"后台拉起 Activity"策略拦截（`result code=102`），测试线程永久等待 Activity，因此没有任何方法能结束；前台始终是 `com.miui.home`，logcat 无 ANR/FATAL/crash。为排除"只是没在前台"这一变量，我把被测 App 先切到前台（`mCurrentFocus=…MainActivity`）后重跑一次，结果相同（attempt 2 也 124/0 完成）——**同一失败点只做了这两次有区别的尝试，之后停止盲跑**。

第 2 行的 `initializationError` 与手势无关：任务书给的 FQCN 少了一级包名。证据（原文栈）：

```
java.lang.ClassNotFoundException: com.creationreadingassistant.ui.screen.stats.DurationUnitToggleEntryTest
```

而 dex 里的真实类名是 `com.creationreadingassistant.ui.screen.stats.components.DurationUnitToggleEntryTest`（源文件放在 `…/stats/` 目录但 `package` 声明是 `…stats.components`，目录与包不一致）。**这是测试寻址/仓库一致性问题，不是产品缺陷**；最小建议见第十一节。

> 因此：Codex 已确认的 `:app:compileDebugAndroidTestKotlin 通过` 只代表能编译；**本轮没有获得任何 instrumentation 运行结果**，不把它算作真机结论。

## 5. S09-B 人工真机矩阵（中性「测试 EPUB」，6 章）

判定手段：每次手势后用 uiautomator 层级读文档进度百分比与章节名（`prog.py`），并记录控制栏是否展开。

| 项 | 操作 | 观测 | 判定 |
|---|---|---|---|
| 三区 standard 中央 | 中央 (610,1356) 连点 2 次 | 进度恒为 37.5%，控制栏 开→闭→开 | **PASS**（只切控制栏一次/次，不翻页） |
| 三区 standard 左 | 左区 (150,1356) | 37.5% → 35.9%（一次一页），控制栏未展开 | **PASS** |
| 三区 standard 右 | 右区 (1070,1356) ×3 | 35.9→37.5→50.5（章末页到章首，百分比跳变属文档进度），控制栏未展开 | **PASS**（单点单页；跨章跳变另记） |
| 第一章 左区边界 | 跳 第一章（0.0%）后左区 ×3 | 进度恒 0.0%，控制栏未展开 | **PASS**（不翻页、不切控制栏） |
| 最后一章 右区边界 | 第六章末页 87.2% 后右区/底带 ×3 | 恒 87.2%，控制栏未展开 | **PASS** |
| 五分区（上下扩展）底带 | 84.0→85.6→87.2→（再 3 次不变） | 可用时下一页；到书末静默 | **PASS** |
| 五分区 顶带 | 第一章 0.0% 顶带 ×3 | 恒 0.0%，控制栏未展开 | **PASS** |
| 五分区 中央 / 左 / 右 | 37.5% 处 | 中央只切控制栏；左 50.5→37.5（上一页）；右 37.5→50.5（下一页） | **PASS** |
| 左右手镜像 | 切「左手模式」（说明文案变为"点击左侧区域翻下一页，右侧区域回退上一页"）后 | 左区 0.0→1.5%（下一页）；右区 1.5→0.0%（上一页）；底带仍为下一页 | **PASS**（左右镜像、上下带不被镜像） |
| 五分区预览语义 | 设置页 热区动作 预览 | 顶带 `ImageView content-desc="上一页"`、底带 `content-desc="下一页"`、左/中/右文字标签 上一页/菜单/下一页 | **PASS（语义树层面）** |
| TalkBack 人工播报 | 见第 10 节 | — | **未覆盖** |

**新发现（不修复，仅记录）**：控制栏展开时点击任一阅读热区，会**同时**收起控制栏并执行该热区动作（三区左区、五分区右区等 3 次独立观测一致：`pct` 变化 + `chrome_open` True→False 同帧发生）。多数阅读器会把"收起控制栏"的那一下吞掉，因此这是候选体验缺陷（第十一节 F-1）。

## 6. S15-F3 人工真机矩阵

单位事实源：`run-as com.creationreadingassistant cat files/datastore/duration_prefs.preferences_pb`（**只读**；文件不存在即从未写过 = 默认 HOURS）。测试前单位经**应用 UI** 确认：`我的 → 应用外观 → "时长按「天」显示"` 开关为**关**（截图 `10-apppearance-unit-off-HOURS.png`）→ 基线 HOURS。

| 项 | 普通点击 | 长按（第 1 次） | 长按（第 2 次） | 判定 |
|---|---|---|---|---|
| 首页 今日阅读时长 | 单位不变、仍停在首页 | HOURS→**DAYS**，Toast「阅读时长已改为按「天」显示」，页面未跳转 | →HOURS | **PASS** |
| 统计 阅读时长摘要 | 单位不变、同屏 | →DAYS | →HOURS | **PASS** |
| 统计 创作/沉浸时长 | 单位不变 | →DAYS | →HOURS | **PASS** |
| 统计 热力图汇总（"3 天开卷 · 4 小时"） | 单位不变 | →DAYS | →HOURS | **PASS** |
| 年度账单 主值（"4 小时"） | 单位不变、仍在年度账单 | →DAYS | →HOURS | **PASS** |
| YearBill 唯一入口 | 主卡内只有主值 `clickable=true`；headline 引文与 subtitle「4 小时 2026」均 `clickable=false` | — | — | **PASS（无重复入口）** |
| Heatmap 单日格子 ×2 | 点击/长按单位都不变 | — | — | **PASS（无入口）** |
| 趋势柱 | 点击/长按单位不变 | — | — | **PASS（无入口）** |
| 会话时长 pill（书籍详情→阅读记录） | 点击 pill → 展开该条（出现"开始时间/结束时间"）；再点 → 收起 | 长按 pill → **单位切一次 DAYS，详情未展开**；再长按 → HOURS | 长按 pill 内边距边缘（文字盒外 12px）→ 单位仍切换、详情未展开 | **PASS**（命中盒=整个 pill） |
| 阅读历史行 整行点击 | 打开该书（进入阅读器，第三章 34.4%） | — | — | **PASS** |
| 阅读历史行 整行长按 | — | 打开「书籍详情与管理」工作表；单位不变 | — | **PASS** |
| 阅读历史行 行内时长坐标长按 | — | 同样打开管理工作表；**单位不变** | — | **PASS**（F3-B3 契约） |
| 阅读历史行 管理按钮 | 打开管理工作表 | — | — | **PASS** |
| 阅读历史行 时长文本语义 | 行内时长节点 `clickable=false`（改前为 true） | — | — | **PASS** |
| 首页阅读档案卡 普通点击 | 进入阅读档案（出现 全部/在读/搁置 过滤页） | — | — | **PASS** |
| 首页阅读档案卡 长按 | **单位不变（仍 HOURS）**；但抬手时触发了与点击相同的导航 | — | — | **单位面 PASS**；导航行为另记 F-3 |
| 已读完页 顶部「完读里程碑 0 分钟」 | 单位不变 | →DAYS | →HOURS | **PASS** |
| 已读完卡片 时长 pill | **未出现**（见 F-2） | — | — | **未覆盖** |
| HOURS/DAYS 文字跟随 | 本机所有时长均 < 24 h，两制渲染相同 → 真机无法用肉眼区分 | — | — | **未覆盖（等价证据不足）** |

## 7. 布局与小屏

| 项 | 证据 | 结论 |
|---|---|---|
| 默认宽度 1220×2712 @480dpi | 全程手势与截图 | 正常，无遮挡 |
| font_scale 1.0 | 基线值 | — |
| font_scale 1.3（临时改，已恢复） | `20-font13-yearbill-hero.png`、`21-font13-home-archive-card.png`、`22-font13-completed-pill.png` | 年度账单 headline 引文换两行、主值/subtitle 不叠压；首页档案卡「5 小时 9 分钟 · 7 本」完整不截断；完读里程碑「0 分钟」pill 含图标与内边距不截断。**唯一观察**：书籍状态图例「已读完」在 1.3 下折成两行（F-5） |
| 320dp 受控宽度 | 依赖 instrumentation 的受控布局用例（`ReaderScreenTest` 等） | **未获得**：四类 instrumentation 在本机跑不起来（第 4 节），因此本轮没有 320dp 自动化证据；未使用 `wm size/density` 伪造小屏 |

## 8. 截图与日志索引（全部中性标签，不含真实书名）

| 文件 | 内容 |
|---|---|
| `00-baseline.txt` / `09-rotation-baseline.txt` / `30-final-state.txt` | 测试前基线、旋转基线、结束态回读 |
| `01-packages.txt` / `01b-manifests.txt` / `02-install-verify.txt` | 包名、manifest、安装后复核 |
| `03-*` `04-*` `04a-*` `05-*` `06-*` | 四类 instrumentation 原始输出（含失败栈） |
| `08-logcat-instrumentation-window.txt` | 阻塞根因 logcat（`Abort background activity starts`、`result code=102`） |
| `10-apppearance-unit-off-HOURS.png` | 测试前单位 = HOURS（应用 UI 开关） |
| `11-home-today-longpress-toast-DAYS.png` | 首页今日时长长按后的 Toast「阅读时长已改为按「天」显示」，仍在首页 |
| `20/21/22-font13-*.png` | font_scale 1.3 三处布局 |
| `23-preset-left-center-right-before.png` | 验收开始时点击区域预设=左中右（用于证明收尾已复原） |

设备上出现的书名均为仓库测试夹具（`测试EPUB`、`t-txt-s07`、`t-md-b-s07` 等），本轮未打开、未修改、未删除任何用户真实书籍，也未执行管理菜单里的任何破坏性动作（只验证工作表能否打开）。

## 9. 状态恢复（逐项 before / after）

| 项 | 测试前 | 结束时回读 | 恢复 |
|---|---|---|---|
| `global stay_on_while_plugged_in` | 3 | 3 | ✔ 全程未改 |
| `system font_scale` | 1.0 | 1.0 | ✔（1.3 为临时） |
| `system accelerometer_rotation` | 1 | 1 | ✔（曾临时锁竖屏 0） |
| `system user_rotation` | 0 | 0 | ✔ |
| `secure enabled_accessibility_services` | 4 项服务串（litiaotiao / mipay SelectToSpeak / gkd / miui.screenshot） | **逐字相同** | ✔（TalkBack 已摘除） |
| `secure accessibility_enabled` | 1 | 1 | ✔ |
| `secure touch_exploration_enabled` | 0 | 0 | ✔ |
| `wm size` / `wm density` | Physical 1220×2712 / 480 | 同，无 override | ✔ 全程未改 |
| 时长单位 | HOURS（UI 开关为关） | HOURS | ✔ |
| `POST_NOTIFICATIONS` | 本轮未在安装前抓取（上一轮实测为 `false`） | `granted=false`（已 `pm revoke`） | ✔（含缺口说明） |
| 阅读位置 | 第三章 值夜 34.4% | 第三章 值夜 34.4%（重开书籍确认） | ✔ |
| 阅读器预设 | 点击区域=左中右、热区动作=标准 | 左中右 + 标准（说明文案回到"标准模式…"） | ✔ |
| 打开的工作表/弹层 | — | 全部关闭；前台为 `com.creationreadingassistant/MainActivity` | ✔ |
| 应用数据 | — | 未 `pm clear`、未卸载、未删书 | ✔ |

累计阅读时长从 4 h 40 m 变为 5 h 9 m —— 这是验收过程真实产生的阅读会话（打开书籍与翻页必然记录），属预期副作用，非数据破坏。

## 10. TalkBack 覆盖情况（如实）

- 设备装有 `com.google.android.marvin.talkback`，本轮**确实临时开启**了 TalkBack：走完 4 页教程、在权限弹窗处选择拒绝（未授予电话权限），并确认 TTS 真在发声（logcat 可见 `AudioTrack stop … packageName: com.google.android.marvin.talkback` 与 MIUI TTS 引擎 `SpeechSynthesizer.Speak` 流水）。
- 但**说了什么无法取证**：logcat 只有 `Speak/FinishSpeakStream` 标记，不含朗读文本；我无音频回听能力。因此以下条目按**未覆盖**处理，不用语义树冒充人工听读：
  - 五分区顶/底带是否读作"上一页/下一页"（语义树已确认 `content-desc` 正是这两个词）；
  - 边界不可用区是否播报"已到第一章/已到最后一章"；
  - 边界节点是否被读成"可点击"；
  - 激活边界是否误切控制栏（触控层已验证不切，语音层未验证）；
  - 长按动作名是否读作"切换时长显示单位"（源码常量 `DURATION_UNIT_TOGGLE_LABEL` 与 `BookGrid.kt:282` 惯例一致，但未听读）。
- 无障碍配置已逐字恢复（第 9 节）。

## 11. 观察与最小修复建议（等待 Codex 裁决，本轮不改代码）

| 编号 | 观察 | 级别 | 最小建议 |
|---|---|---|---|
| F-1 | 控制栏展开时点击阅读热区，同一手势既收起控制栏又执行翻页/上一章（3 次独立观测） | 候选 **P2**（体验，需产品确认是否有意） | 在 chrome 展开时让首个热区点击只负责收起（例如把 chrome 可见性纳入 tap 分发前置判断），或用 scrim 吞掉第一次 down |
| F-2 | 已读完卡片的时长 pill 依赖 `progress.total_reading_time_ms`，本机 3 本完读书该字段为 0（时长只落在 sessions），pill 因此不渲染 → 该项真机无法验收 | 数据面观察 / **P3**（若产品认为完读书应显示用时，则取数口径需统一） | 与 `bookDetailReadingTimeMs(progress, sessions)` 的口径对齐，或明确"完读卡不显示用时"的设计意图 |
| F-3 | 首页阅读档案卡长按抬手会触发导航（普通 `clickable` 的固有行为）；单位未被切换，符合 Codex 裁决 | **P3** 说明 | 若不希望"长按=跳转"，可给该卡显式 `onLongClick = {}` 空实现或保持现状并在文档说明 |
| F-4 | 年度账单 subtitle 读作「5 小时 2026」，缺分隔符 | **P3** 文案 | 改为「5 小时 · 2026」 |
| F-5 | font_scale 1.3 下"书籍状态"图例「已读完」折行两行 | **P3** 排版 | 图例文字 `maxLines=1` + 缩小点距，或允许换行但固定行高 |
| F-6 | 任务书/仓库里 `DurationUnitToggleEntryTest` 的文件目录（`ui/screen/stats/`）与包声明（`…stats.components`）不一致，导致按路径推 FQCN 得到 `ClassNotFoundException` | 测试一致性 **P3** | 把文件移到 `ui/screen/stats/components/` 或改包声明，使目录与包一致 |
| F-7 | 四个 Compose UI instrumentation 类在 MIUI 真机上被"后台拉起 Activity"策略拦截（`result code=102`），0 条完成 | 环境阻塞（非产品缺陷） | 需要一台允许 instrumentation 拉起 Activity 的设备/时段，或按项目既有做法在受控设备窗口跑；本轮已按未验证处理 |

## 12. 最终分级

**PASS WITH LIMITATIONS。**

- 通过（真机人工，本轮直接观测）：S09-B 三区/五分区/左右手 + 首尾边界 + 中央区不翻页；S15-F3 五个安全时长入口的"点击不切、长按恰好切一次、无副作用"、YearBill 唯一入口、Heatmap 仅汇总入口、会话 pill 的点击展开/长按切单位/整盒命中、阅读历史行的四态归属（含 pill 坐标长按归整行管理）、首页档案卡不劫持单位、单位在两种显示制下的持久化状态与复原。
- 限制 / 未覆盖：四个 instrumentation 类（62 条计划用例，0 完成，环境阻塞）；TalkBack 语音播报内容；已读完卡片时长 pill（本机数据下不出现）；HOURS/DAYS 的肉眼可见差异（本机无 ≥24 h 时长）；320dp 受控宽度自动化证据。
- 不以构建通过替代真机结论：`:app:compileDebugKotlin`/`:app:compileDebugAndroidTestKotlin` 通过只说明可编译，本报告未把它计入任何验收项。

## 13. 边界声明

- 未修改任何 Kotlin / 测试 / Gradle / 脚本 / 配置文件；本轮唯一新增是本报告与 `docs/qa/evidence/2026-09-22-s09b-s15-duration-gesture-device-acceptance/` 下的截图、日志与 instrumentation 原始输出。抽查源文件 mtime 可复核：`MyReadingScreen.kt` 13:07、`BookDetailStatsSection.kt` 13:22、`DurationDisplay.kt` 14:16（他窗口写入）、`build.gradle.kts` 09-17 —— 全部早于本轮第一次设备/构建动作 14:33。
- 未 commit / push / stage / stash / reset / checkout / clean，未全仓格式化：
  - **采集时点要如实说明**：本轮没有在第一台设备动作（14:33）之前单独跑 `git status`；同工作树最近一次前置快照是 13:26 的 **382** 条（上一切片结束时采的），结束（16:10）为 **383** 条。因此"开始/结束"的差量不靠条目数推断，而按显式路径核对：
  - `git status --short -- docs/qa/` 里属于本轮的只有 `?? docs/qa/2026-09/2026-09-22-s09b-s15-duration-gesture-device-acceptance.md` 一行 —— 证据目录落在早已折叠显示的 `?? docs/qa/evidence/` 之下，不额外增加条目数；
  - `git diff --cached --name-only | wc -l` = **0**，HEAD 仍是 `5aed5939600e491843e3c44453921bd6273a233c`，分支 `codex/recover-worktree-20260920`。
- 未进入原始仓库 `D:\develop\Code\Codex\creation-reading-assistant`。注意：仓库脚本 `android/scripts/dump_ui.py` 会把 UI dump 写进**原始仓库**路径，因此本轮**没有使用它**，改用只写本机 temp 目录的自建 dump 脚本。
- 未使用 MuMu 或任何模拟器；全程只有 `c49ac6cf` 一台真机，所有设备命令都显式带 `-s c49ac6cf`。
- 未删除/重命名/重新导入/修改任何书籍数据，未执行管理菜单中的破坏性动作，未 `pm clear`，未卸载；DataStore 仅 `run-as cat` 只读，权限变更只有把安装脚本静默授予的 `POST_NOTIFICATIONS` 撤销回原值。
- 未要求用户手工点击任何 MIUI 弹窗；未修改 MIUI 安全设置。

完成报告后停止，不领取下一任务。
