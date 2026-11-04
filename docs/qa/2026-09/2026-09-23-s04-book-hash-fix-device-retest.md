# S04 TXT/MD 主书哈希修复真机独立复验报告

**验收日期**：2026-09-23  
**验收执行者**：WorkBuddy (Antigravity)  
**复核状态**：Codex 独立回读见 [复核报告](2026-09-23-s04-book-hash-fix-device-codex-review.md)  
**工作仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`  
**分支**：`codex/recover-worktree-20260920`  
**验收设备**：Xiaomi 22011211C（Serial: `c49ac6cf`，Android 14，屏幕方向：竖屏 rotation=0，`stay_on_while_plugged_in=3`）  
**前置参考**：
- [`docs/qa/2026-09/2026-09-23-s04-book-hash-fix-codex-review.md`](2026-09-23-s04-book-hash-fix-codex-review.md)（Codex 代码轴复核）
- [`docs/qa/2026-09/2026-09-23-s04-isolated-source-refresh-codex-review.md`](2026-09-23-s04-isolated-source-refresh-codex-review.md)（Codex 前序真机复核）
- [`docs/qa/2026-09/2026-09-23-s04-isolated-source-refresh-device-acceptance.md`](2026-09-23-s04-isolated-source-refresh-device-acceptance.md)（上轮真机报告）
- [`docs/qa/evidence/2026-09-23-s04-source-refresh/README.md`](../evidence/2026-09-23-s04-source-refresh/README.md)（脱敏截图证据）

---

## 1. 验收裁决

**WorkBuddy 裁决：PASS（仅本次隔离 TXT 小文件 V4 路径；Markdown、EPUB 和大文件未做真机复验）**

| 验收维度 | 裁决 | 核心依据 |
|---|---|---|
| **books.content_hash 同步修复** | **PASS** | 新构建 Debug APK 安装后，隔离测试 TXT 在 V4（460 字节，MD5: `82fd5133...`）同步刷新后，`books.content_hash` 精确推进为 V4 MD5，且绝对不再是 V1（`4ac50fb1...`）或 V3（`1b79b626...`）的陈旧哈希。 |
| **books 与 source_refs 哈希一致性** | **PASS** | `books.content_hash` 与 `library_source_refs.content_hash` 均等于 `82fd5133f19480e1c00b51d8e1327f34`，两表哈希 100% 严格一致。 |
| **内部正文副本落盘一致性** | **PASS** | 设备应用私有目录内部正文文件大小为 460 字节，`md5sum` 输出为 `82fd5133f19480e1c00b51d8e1327f34`，与数据库双表记录及外部源文件完全吻合。 |
| **单书搜索索引与分词闭环** | **PASS** | 后台自动完成单书索引重建（coverage=full 1/1）；V4 新专属测试词（`secrets04v4retestterm` 及中文分词）精准入库；V3 旧专属词（`secrets04confirmedterm`、`紫电` 等）全量清除为 0 hits。 |
| **原有书籍选定字段不变** | **PASS（限定范围）** | 原有 11 本在四张表的选定字段前后摘要相同；未覆盖所有表、所有列或外部来源文件。 |
| **安装脚本原生通过** | **PASS** | `android/scripts/install_with_confirm.ps1` 退出码 0，结果为 `CONFIRM_LOOP`（MIUI 安装确认原生通过，未走直接安装回退）。 |

---

## 2. 操作前基线与旧包问题复现

在安装新包及触发任何变更前，对当前设备现场进行取证核对：

### 2.1 测试对象存在性与隔离性核实
- **测试书 ID**：`37ed045b-e32e-401e-8623-a219e8ae098d`，格式 `txt`，确属上轮留下的隔离测试书。
- **外部来源**：位于专用隔离子目录 `.../cra-s04-isolated-test/test-source-s04-refresh.txt`。

### 2.2 证明旧安装包尚未包含本轮修复（现场复核）
通过私有管道拉取设备数据库副本（只读），核查测试书状态：
- `books.size`: `419`
- `books.content_hash`: `4ac50fb1f4d52196556df5190fb4ac37`（**仍停留在 V1 旧哈希**）
- `library_source_refs.content_hash`: `1b79b626f94a6e1869e402ff575912cf`（**已推进至 V3 哈希**）
- **结论**：两表哈希存在确凿差异，直接证实旧 APK 确实存在 `books.content_hash` 未同步更新的问题。

### 2.3 原有 11 本书基线摘要锁定
使用选定字段（`books`、`book_files`、`reading_progress`、`library_source_refs`）计算原有 11 本书数据摘要：
- **基线哈希**：`7320496393c459602e3f12cb4555f0c07b35c93b95c8c1ad2405522b74cb2f95`
- **当前哈希**：`7320496393c459602e3f12cb4555f0c07b35c93b95c8c1ad2405522b74cb2f95`（Match: `True`）

### 2.4 外部测试来源 V3 备份
从手机拉取隔离测试源文件并备份到仓库外私有临时目录：
- 备份路径：`.../cra-device-backup-20260923/retest_pre/test-source-s04-refresh_v3_backup.txt`
- 大小：419 字节，MD5: `1b79b626f94a6e1869e402ff575912cf`。

---

## 3. 新 Debug APK 构建与安装

### 3.1 构建命令与 APK 签名指纹
- **构建命令**：`./gradlew :app:assembleDebug`（在 `android/` 目录执行）
- **退出码**：`0`（耗时 28s，41 actionable tasks: 3 executed, 38 up-to-date）
- **产物路径**：`android/app/build/outputs/apk/debug/app-debug.apk`
- **产物大小**：`52,481,150` 字节
- **产物 SHA-256**：`85788A8008629E220EC76DDC986150E557F9D0BB3438B0AA36C9013582B79A5F`（不同于上一轮未修复包 `160BE144...`）

### 3.2 安装过程与退出码
- **执行命令**：
  ```powershell
  pwsh -ExecutionPolicy Bypass -File android/scripts/install_with_confirm.ps1 -Serial c49ac6cf -Apk android/app/build/outputs/apk/debug/app-debug.apk
  ```
- **退出码**：`0`
- **关键脚本输出**：
  ```
  adb devices entries=1; serial 'c49ac6cf' listed as device: True
  TAP installer control 'android:id/button3' at 351,2522
  TAP installer control 'com.miui.packageinstaller:id/checkbox' at 610,2008
  TAP installer control 'com.miui.packageinstaller:id/second_button' at 610,2356
  RESULT: CONFIRM_LOOP (installed through the MIUI installer UI)
  Installed com.creationreadingassistant successfully.
  ```
- **安装状态判定**：**原生确认循环通过（CONFIRM_LOOP）**，未触发直接安装回退。

---

## 4. V4 测试源构造与端到端同步

### 4.1 V4 测试文件元数据
在仓库外私有目录构造全新的 V4 测试文件，替换手机端隔离测试文件：
- **私有源文件路径**：`.../cra-device-backup-20260923/test-source-s04-refresh_v4.txt`
- **字节大小**：`460` 字节
- **文件 MD5**：`82fd5133f19480e1c00b51d8e1327f34`
- **文件 SHA-256**：`0eaeeedcab04e8bf605a2ff08e091f3768b69b17d3c3d7d899a659db05d8b399`
- **新专属词条**：`SECRETS04V4RETESTTERM`、`九霄`、`云龙`、`罡气`、`纯阳`、`真火`、`苍龙`、`吐息`。
- **已删除旧词**：`SECRETS04CONFIRMEDTERM`、`紫电`、`惊雷`、`独创青鸾秘术`、`高阶心法`。

### 4.2 书架下拉刷新与「重新同步」
1. 推送 V4 至手机端隔离测试路径：`/sdcard/.../cra-s04-isolated-test/test-source-s04-refresh.txt`。
2. 拉起应用进入书架，执行下拉刷新手势（swipe）。
3. 界面弹出标准确认弹窗：「**同步源文件更新？** 检测到 1 本书（test-source-s04-refresh）的源文件已被修改，是否重新同步正文？同步后将重置章节与阅读进度。」
4. 点击「**重新同步**」按钮（tap 862, 1612）。
5. 弹窗平滑关闭，书架卡片角标恢复正常，等待后台单书索引重建稳定。

---

## 5. 核心验收结果：数据库与正文对照

### 5.1 前后版本哈希对照总表

| 检查项 | V1（初始导入） | V3（上轮更新） | V4（本轮复验预期） | 本轮设备端实测值 | 校验判定 |
|---|---|---|---|---|---|
| **文件大小** | 394 字节 | 419 字节 | **460 字节** | **460 字节** | **MATCH** |
| **文件 MD5** | `4ac50fb1...` | `1b79b626...` | **`82fd5133...`** | **`82fd5133...`** | **MATCH** |
| **books.size** | 394 | 419 | **460** | **460** | **MATCH** |
| **books.content_hash** | `4ac50fb1...` | `4ac50fb1...`（旧 BUG 残留） | **`82fd5133...`** | **`82fd5133f19480e1c00b51d8e1327f34`** | **FIXED / PASS** |
| **source_refs.content_hash** | `4ac50fb1...` | `1b79b626...` | **`82fd5133...`** | **`82fd5133f19480e1c00b51d8e1327f34`** | **MATCH / PASS** |
| **两表哈希是否完全一致** | 一致 | **不一致（阻断）** | **一致** | **True（完全一致）** | **PASS** |
| **内部正文文件大小** | 394 字节 | 419 字节 | **460 字节** | **460 字节** | **MATCH** |
| **内部正文文件 md5sum** | `4ac50fb1...` | `1b79b626...` | **`82fd5133...`** | **`82fd5133f19480e1c00b51d8e1327f34`** | **MATCH** |

### 5.2 单书搜索索引与倒排分词核验

- **索引覆盖度表 (`search_index_coverage`)**：
  - `book_id`: `37ed045b-e32e-401e-8623-a219e8ae098d`
  - `coverage`: `full`（1/1 章）
  - `indexed_at`: `1790164168686`（随 V4 刷新完成重索）
- **倒排词命中统计 (`search_terms`)**：
  - **新专属词** `secrets04v4retestterm`: **1 hit**
  - **新专属中文词** `九霄`: **1 hit** / `云龙`: **3 hits** / `罡气`: **2 hits** / `纯阳`: **1 hit** / `真火`: **1 hit** / `苍龙`: **1 hit** / `吐息`: **1 hit**
  - **旧专属词** `secrets04confirmedterm`: **0 hits**（已清除）
  - **旧专属中文词** `紫电`: **0 hits** / `惊雷`: **0 hits** / `独创青鸾秘术`: **0 hits** / `高阶心法`: **0 hits**（已清除）

### 5.3 原有 11 本书基线摘要对账（零污染）

```
Original 11 books hash match: True
Current hash:  7320496393c459602e3f12cb4555f0c07b35c93b95c8c1ad2405522b74cb2f95
Baseline hash: 7320496393c459602e3f12cb4555f0c07b35c93b95c8c1ad2405522b74cb2f95
Count of books:    11
Count of files:    11
Count of progress: 7
Count of refs:     11
```
- 原有 11 本书的选定字段摘要在安装新 APK、执行 V4 更新同步、索引重建后均 100% 保持一致，零意外数据改变。

---

## 6. 关键命令与执行退出码

| 步骤 | 执行命令（参数经脱敏整理） | 退出码 | 观察与结果 |
|---|---|---|---|
| 设备核对 | `adb devices` | 0 | 确认设备 `c49ac6cf`，竖屏 rotation=0 |
| 常亮原值核查 | `adb -s c49ac6cf shell settings get global stay_on_while_plugged_in` | 0 | 原值为 3，全程保持原值 |
| 操作前数据库拉取 | `adb -s c49ac6cf exec-out run-as com.creationreadingassistant cat databases/...` | 0 | 成功提取数据库只读副本 |
| 操作前基线与旧包核验 | `python -c "..."` | 0 | 证实旧包 `books.content_hash` 未更新，原有 11 本书基线哈希匹配 |
| V3 文件私有备份 | `adb -s c49ac6cf pull .../test-source-s04-refresh.txt ...` | 0 | 备份 V3（419 字节）至私有目录 |
| 新 APK 构建 | `./gradlew :app:assembleDebug` | 0 | 成功生成新 APK，SHA-256 为 `85788A80...` |
| 安装新 APK | `pwsh -ExecutionPolicy Bypass -File android/scripts/install_with_confirm.ps1 ...` | 0 | `RESULT: CONFIRM_LOOP`，MIUI 安装确认原生通过 |
| 启动应用 | `adb -s c49ac6cf shell "am start -n .../.MainActivity"` | 0 | 应用拉起并置于前台 |
| V4 测试源推送 | `adb -s c49ac6cf push .../test-source-s04-refresh_v4.txt ...` | 0 | 460 字节 V4 文件推送就绪 |
| 下拉刷新手势 | `adb -s c49ac6cf shell input swipe 600 800 600 1800 300` | 0 | 成功触发下拉刷新 |
| 点击「重新同步」 | `adb -s c49ac6cf shell input tap 862 1612` | 0 | 触发正文替换、元数据推进与单书索引重建 |
| 操作后数据库拉取 | `adb -s c49ac6cf exec-out run-as com.creationreadingassistant cat databases/...` | 0 | 成功拉取更新后数据库只读副本 |
| 全量字段与分词对账 | `python -c "..."` | 0 | `books.content_hash` 精确等于 V4 MD5，双表哈希一致，新词命中旧词清除，原有书籍摘要匹配 |
| 内部正文 md5sum 核验 | `adb -s c49ac6cf shell "run-as com.creationreadingassistant md5sum ..."` | 0 | 确认为 `82fd5133f19480e1c00b51d8e1327f34` |
| 常亮最终检查 | `adb -s c49ac6cf shell settings get global stay_on_while_plugged_in` | 0 | 确认保持原值 3 |

---

## 7. 证据产物与脱敏截图清单

脱敏证据保存在 [`docs/qa/evidence/2026-09-23-s04-source-refresh/redacted/`](../evidence/2026-09-23-s04-source-refresh/README.md)：

- [`06_retest_v4_shelf_state.png`](../evidence/2026-09-23-s04-source-refresh/redacted/06_retest_v4_shelf_state.png)：新构建 Debug APK 安装后，隔离测试 TXT 执行 V4 同步完成后的书架状态。测试书角标恢复正常，除测试书外的其他 11 本书封面与书名已 100% 不透明脱敏遮盖。
- 本地私有未脱敏原图与数据库副本均存放在仓库外部的临时私有目录（`C:\Users\23254\.gemini\antigravity\scratch\cra-device-backup-20260923\`），严禁进入版本库。

---

## 8. 现场保留对象清单

根据要求（“测试书与其来源结束后继续保留，未经用户明确决定不要删除”），保留以下隔离现场证据供复核：

1. **设备外部测试目录**：`/sdcard/.../cra-s04-isolated-test/`（专用隔离子目录，不影响原有书籍）。
2. **设备外部测试文件**：`test-source-s04-refresh.txt`（460 字节，MD5: `82fd5133f19480e1c00b51d8e1327f34`）。
3. **应用内测试书籍**：ID `37ed045b-e32e-401e-8623-a219e8ae098d`，标题 `test-source-s04-refresh`。

---

## 9. 未覆盖项声明

1. **EPUB 来源更新（UNCOVERED ON DEVICE）**：
   - 本次修复与复验针对 TXT/MD 小文件文本正文刷新（`reselectPlainText`）。EPUB 格式的更新刷新逻辑保留在后续专门片中验收。
2. **大于 32 MiB 大文件哈希置 null 策略（UNCOVERED ON DEVICE）**：
   - 超过 32 MiB 的大文件由于避免手机端 OOM 采用候选指纹机制，属于非小文件边界，已由 JVM 代码规范约定，设备端不在此最小复验范围内。
3. **系统 Provider 故障注入（UNCOVERED ON DEVICE）**：
   - 系统 ContentProvider 崩溃/断电等异常降级路径本轮未在真实设备模拟；现有 JVM 只覆盖部分 provider 返回 null 场景，不能宣称这些具体异常已闭环。
