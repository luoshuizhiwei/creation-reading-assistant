# CRA-READ-NOTE-JOURNEY-ACCEPT-v2：三格式非起点冷读回源 + 可达个人注释（2026-10-03）

## 任务编号/版本

`CRA-READ-NOTE-JOURNEY-ACCEPT-v2` / v2，2026-10-03。

- **执行应用/模型**：Claude Code（桌面 Code 标签页）；实际路由标识 `hw726brokbuddy AI/global:deepseek-v4.1-flash`
- **执行标识**：`CRA-READ-NOTE-JOURNEY-ACCEPT-v2-20261003-1340`
- **角色**：本批唯一 MuMu / ADB / 设备构建 owner
- **状态**：**已交付待 Codex 核查**

## 输入指纹

| 项 | 值 |
|---|---|
| 活动仓库 / HEAD（预期=实际） | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` / `e39a78da9c62f02614207cffb36969a32492eaea` |
| 暂存区 | 空（本批前后一致） |
| 产品输入（只读） | `E:\creation-reading-assistant\next-round-integration-ready-v1\android`（883 文件） |
| `final-manifest.json` SHA-256 | `a0240a395c20251f480378ed07ba901c0c0061c79cd98ea4b43985b229ed491d`（与预期一致） |
| 参考 v1 | `E:\creation-reading-assistant\next-round-read-note-journey-accept-v1`、[v1 报告](2026-10-03-next-read-note-journey-accept-v1.md) |
| 上游核查 | [阅读/回滚交付 Root 核查](../../coordination/reviews/2026-10/2026-10-03-next-reader-rollback-delivery-codex-review.md)（指明 v1 缺口：locator 全为 0、个人注释未输入、test APK 与安装记录不一致） |

**占用核对（开工前）**：`CRA-RESELECT-ROLLBACK-FIX-v3` 为零设备任务，`reader-rollback-review` 为只读；设备上无运行中 instrumentation、无应用进程、`forward`/`reverse` 均为空。**两个 adb serial（`127.0.0.1:16416`、`emulator-5556`）实为同一台 MuMu**（fingerprint/uptime/`nemu*` 服务一致，唯一监听者 `MuMuVMMHeadless.exe`），本批统一使用 `127.0.0.1:16416`。

## 所有权与改动

- **唯一工作目录**：`E:\creation-reading-assistant\next-round-read-note-journey-accept-v2`
- **唯一仓库报告**：本文件
- **本任务 E 候选内改动（未回灌冻结输入）**：
  1. `app/build.gradle.kts` qahost 的 `applicationIdSuffix`：`.qahost` → **`.qahostjourneyv2`**（本批隔离后缀）
  2. **新增** `app/src/androidTest/java/com/creationreadingassistant/acceptance/ReadNoteJourneyV2AcceptanceTest.kt`（夹具与断言驱动；含一次断言口径修正，见「批次关联」）
  3. 新增 `ui.py`（真实 UI 执行器，由 v1 适配）、`rj.py`（仪器运行器）
- **产品源码与冻结输入一致**：未改产品、未恢复旧笔记页、未直写 DB 制造 PASS；未动另一回滚窗口的候选
- **未触碰**：实体手机、正式书库、原仓库、桌面资产、HOLD；未执行任何 Git 操作

## 环境

- Windows 11 / Git Bash；JDK 17.0.14；SDK build-tools 34.0.0；Python 3.13；独立任务缓存（复制完成后才启动 Gradle，全程 `--offline`）
- **设备**：MuMu 模拟器（按宿主进程 + TCP 对应 + guest 特征判定，**不凭型号字符串**；model 被模拟为 `SM-S9280`）。手机宽度竖屏 1080×1920，批中 `wm density 480`，**收尾已 `wm density reset` 回 280**
- **真实链路**：runner 隔离分支仅对精确 `com.creationreadingassistant.qahost` 生效，本后缀不匹配 ⇒ 生产 `App`。每条证据中 `applicationClass = com.creationreadingassistant.App`
- **执行方式**：沿用 v1 结论——Compose 仪器环境无法让本阅读器 readOnly `BasicTextField` 产生选区，**选区/保存/查看/回源由设备侧 uiautomator 驱动**，仪器只做夹具与持久数据断言

### 冻结双 APK（批次 B2，当前有效）

| APK | 包名 | 字节 | SHA-256 |
|---|---|---:|---|
| 宿主 | `com.creationreadingassistant.qahostjourneyv2` | 51,371,712 | `890c3a10be2b3faef0975c27f1a0f92e91cfb44310063600d1cdd314c2accae3` |
| androidTest | `com.creationreadingassistant.qahostjourneyv2.test` | 9,171,667 | `5197f404fcc957a111c104351b34ea994d8a2baddf220193841d2ecb9dffc2c2` |

`apksigner verify` 两包 exit 0，证书 `05249d78…b9a1`；宿主 manifest `application android:name="com.creationreadingassistant.App"`；androidTest `instrumentation android:name="com.creationreadingassistant.testing.CraIsolatedTestRunner"`、`targetPackage="com.creationreadingassistant.qahostjourneyv2"`。**安装后设备回读 SHA 与冻结件一致（host/test 均 True）**，记录在 `freeze/batch-association.json`。

## 执行与结果

### 合同结果汇总

| 合同 | 结果 | 说明 |
|---|---|---|
| **J1 TXT 非起点** | **PASS** | `offset=2191`、`progress=73.91%`；冷启动 PID 27691→27751；回源 24.4%→**73.9%** |
| **J2 EPUB 第 3 章** | **PASS** | `ci=2`、`co=108`、`chapterTitle=第 3 章`、`offset=822`、`progress=43.57%`；冷启动 PID 30355→30430；回源 78.0%（第 4 章）→**44.0%（第 3 章）** |
| **J3 MD 非起点** | **PARTIAL** | 保存与冷读 PASS；**回源 FAIL（产品行为，证据已存，未修产品）** |
| ├ MD 保存 | PASS | `offset=1270`、`chapterTitle=中性测试 Markdown` |
| ├ MD 冷启动读回 | PASS | PID 29253→29310，值一致 |
| └ MD 回源 | **FAIL** | 两次点击「跳转」均停在 8.7%，无提示、无位移 |
| **可达个人注释** | **PASS** | 经「编辑高亮笔记」真实输入并保存；落点 `highlights.note`；冷读 PID 2704→2768 |
| **R1 恢复** | **PASS** | 53/53 持久文件、3/3 原 APK、8/8 设置一致；映射空；临时文件清理 |

### 与 v1 的对照（关键：非起点）

| 格式 | v1 locator | v2 locator | 判定 |
|---|---|---|---|
| TXT | `offset=0, ci=0, co=0`（0.0%） | `offset=2191, co=2191`（73.91%） | **非起点成立** |
| EPUB | `offset=0, ci=0, co=0`（第一章 0.0%） | `offset=822, ci=2, co=108`（第 3 章 43.57%） | **第二章或后续成立**（ci=2） |
| Markdown | `offset=0, ci=0, co=0` | `offset=1270, co=1270` | **非起点成立**（保存侧） |

### 逐格式实际路径与定位断言

**J1 TXT**：打开书 → 翻页至 73.9% → 长按标记句（选区芯片显示「中性测试标记句甲甲甲」）→ 点「高亮」→ 选黄 → 界面「已高亮」→ **强停**（旧 PID 27691）→ 冷启动读回（新 PID 27751，值一致）→ 重开书并翻到标记句 → 回翻到 **24.4%（标记句不可见）** → 书内「笔记与标注」→ 点「跳转」→ **73.9%，标记句可见**。

**J2 EPUB**：打开书 → 翻至 **第 3 章 44.0%** → 长按标记句 → 保存 → 强停（30355）→ 冷启动（30430，值一致）→ 重开并翻到 **第 4 章 78.0%（标记句不可见）** → 书内笔记面板（条目标注「第 3 章」）→ 点「跳转」→ **第 3 章 44.0%，标记句可见**。

**J3 MD**：打开书 → 翻至标记句（77.9%）→ 长按 → 保存（`offset=1270`）→ 强停（29253）→ 冷启动（29310，值一致）→ 重开并移到 **8.7%（标记句不可见）** → 书内笔记面板点「跳转」→ **仍 8.7%，标记句不可见**；**重开面板重试一次，结果相同**。面板正常关闭，无错误提示，属**静默无位移**。

> MD 项按任务要求**保存复现证据后停止该失败路径**，未越权修改产品。证据：`device-evidence/md-jump-failure/`（含 `v2g-md-panel`、`v2g-md-jumped`、`v2g-md-retry-panel`、`v2g-md-retry-jumped`、`v2g-md-away` 的 XML 与截图）。

### 个人注释（可达性已验）

入口链：阅读器「更多」→「笔记与标注」→ 条目「**加笔记**」→「**编辑高亮笔记**」对话框（含「摘录：…」与「**笔记内容**」输入框）→ 真实键入 `中性个人注释内容丁丁丁` → 点「保存」→ 面板条目下显示该注释，按钮变为「编辑笔记」。

- **落点**：`highlights.note`（`notes` 表为 0）——本批实测「加笔记」写入的是**高亮行的批注字段**，而非新建 `notes` 行。最终状态 `neutralNoteInHighlights=1 neutralNoteInNotes=0`。
- **冷读**：强停后新进程读回，注释仍在。

### 批次关联（APK/驱动/安装/运行）

| 批次 | 驱动 SHA | 宿主 APK | 测试 APK | 安装回读 | 运行 |
|---|---|---|---|---|---|
| B1 | `0cafe8e67a10c2a5…` | `890c3a10…` | `79b09b55…` | 一致 | 三格式保存/冷读、MD 回源失败、注释断言 FAIL（口径错） |
| **B2** | `2b0db3436d3d9530…` | `890c3a10…` | `5197f404…` | **一致** | 注释断言修正后重跑：60/61/62/90 |

- **驱动重建即另起批次**：B1→B2 因修正 `rnj2_60/61` 断言（原只查 `notes` 表）而重建，B2 重新冻结、重新安装并核对回读后才运行。
- **不追溯绑定**：v1 报告中「最终 test APK 与 installed-apk-sha 不一致」（`b46ce1af…` vs `9c6e9f43…`）——v1 原始记录不足以判定旧运行对应哪个构建，故**保留「不确定」**，未用本批冻结件追溯绑定 v1 旧运行。

### 运行计数

- **方法运行 15 次**：13 PASS、1 FAIL（`rnj2_60` 首次，断言口径错误）、1 OTHER（`rnj2_30` 首次，ADB daemon 重启导致连接丢失，重跑 PASS）
- **功能合同 7 项**：6 PASS、1 FAIL（MD 回源）
- 失败与调试痕迹**全部保留**：`device-runs/` 下每个运行独立文件，未删除

## 原始证据

根目录：`E:\creation-reading-assistant\next-round-read-note-journey-accept-v2\`

| 路径 | 内容 |
|---|---|
| `device-runs/*.txt` | 15 份仪器原始输出（命令、exit、OK/FAILURES、异常栈）；**失败原件保留未删** |
| `device-evidence/rnj2_*.json` | 12 份设备侧 JSON（保存/冷读/注释/最终状态，含 PID 与 locator 明细） |
| `device-evidence/*.xml` | 65 份真实 UI 层级（每一步导航、选区、对话框、回源） |
| `device-evidence/*.png` | 77 张截图，**实测全部竖屏**（1080×1920，portrait 77/77；UI XML `rotation="0"` 65/65） |
| `device-evidence/md-jump-failure/` | MD 回源失败复现证据（5 组 XML + PNG） |
| `freeze/apk/*`、`freeze/meta/*` | 冻结双 APK、badging/manifest/signature 原输出 |
| `freeze/batch-association.json` | **逐批**驱动 SHA / 冻结 APK / 安装回读 / 运行清单 / 变更说明 |
| `freeze/device-run-index.json` | 运行索引与逐文件 SHA |
| `device-baseline/` | 前后对账：8 项设置、3 个原 APK、53 个持久文件、三格式强停前 PID |
| `logs/` | 2 次构建日志 |
| `ui.py`、`rj.py`、`android/…/ReadNoteJourneyV2AcceptanceTest.kt` | 执行器、运行器、唯一新增仪器类 |

测试书均为「测试 TXT / 测试 EPUB / 测试 Markdown」中性名称；注释内容为中性「中性个人注释内容丁丁丁」。

## 未覆盖项

- **MD 回源未关闭**：本批复现其静默无位移，属**待定位的产品行为**；未修产品、未定位根因（如 MD 分支是否消费 `offset`、是否被保存进度覆盖），需独立窄修任务。
- **本任务不关闭**：跨书检索、临时查阅、全部摘录笔记需求（按要求保留）。
- 个人注释只验**高亮批注**（`highlights.note`）一条；未覆盖独立 `notes` 行（含书签）的创建——本批 `notes` 表始终为 0，独立笔记行入口未在本批出现。
- 三格式各 1 条高亮、1 条注释；未覆盖多条、跨章、软删除/撤销、颜色变体（仍只选 yellow）。
- 未验：实体手机/MIUI、TalkBack、R8、正式签名/升级、WebDAV、正式书库、AI/续读卡/索引工程。
- v1 的 test APK 安装对应**仍不确定**，未由本批补清。

## 恢复与副作用

| 项 | 结果 |
|---|---|
| 新增宿主/测试包 | 已卸载（Success ×2）；`/data/user/0/…qahostjourneyv2` 与 `/sdcard/Android/data/…qahostjourneyv2` 确认**不存在** |
| 本批夹具 | 随卸载消失（三书行/正文/高亮/注释均在沙箱包私有目录） |
| 原宿主/测试 APK | 3/3 SHA 与开工基线相同 |
| 受保护持久数据 | `.qahost` files/databases(含 WAL/SHM)/shared_prefs **53/53 SHA 一致** |
| 系统设置 | 1080×1920 / 280dpi / rotation 0 / accelerometer_rotation 1 / timeout 60000 / stay_on 0 / night=**no** / 无代理 —— **8/8 与开工基线逐字节相同** |
| ADB 映射 | 本批未创建 forward/reverse；收尾均空 |
| 公共诊断临时文件 | 本批在 `/sdcard` 产生 `d.xml`、`rnj2-ui.xml`，**已按实际路径删除**；`/sdcard` 其他条目未动 |

**如实登记的边界**：本批开工实测夜间模式为 `no`（与 v1 收尾后的 `yes` 不同，属开工实测值），批中未再漂移，8/8 与基线一致。**卸载不等于实测 KeyStore 绝对零残留**——沙箱包主密钥别名与 `androidx.work.workdb` 随卸载消失，但未做 KeyStore 直接观测。**MuMu 不是实体手机验收**。

## 交付状态与下一步

**已交付待 Codex 核查。** 本批完成：三格式**非起点**保存与冷启动读回（TXT `offset=2191`、EPUB **第 3 章 `ci=2/co=108`**、MD `offset=1270`）、TXT 与 EPUB 的完整回源（含章节断言）、**可达个人注释**的真实输入/保存/冷读，以及逐批 APK/驱动/安装/运行关联与完整恢复对账。

**需要 Codex 裁决的一项（产品行为）**：MD 回源静默无位移（可复现，两次）。建议由独立窄修 owner 在 E 副本定位 MD 分支的定位消费逻辑，**本批不越权修产品、不启动后续任务。**

建议后续（由 Codex 决定）：
1. 独立核查本批证据，重点：三格式 locator 非起点字段、EPUB `ci=2` 与章节标签、冷启动 PID 对、MD 失败复现件、批次关联表。
2. 若核查通过：**非起点证据 3/3 中的 TXT/EPUB 两项、个人注释**可纳入；MD 回源单列返修。
3. 不合流到 D、不操作 Git、不启动后续任务。

**百分比**：本批合同 **6/7 = 85.7%**（MD 回源 FAIL）。这是**有限补验**的完成率，不等于全阅读能力或全产品完成率；与 v1 的 7/7 口径不同（v1 为首段旅程）。全产品统一分母仍未核定。
