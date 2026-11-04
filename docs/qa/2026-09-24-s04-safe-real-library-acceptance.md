# S04 阶段 A · 独立真机验收报告

- **日期**：2026-09-24
- **执行者**：独立验收窗口（未参与本轮产品代码修改）
- **被测对象**：`android/app/build/outputs/apk/debug/app-debug.apk`
- **设备**：Xiaomi 22081212C · Android 15 · 竖屏 · `adb -s <serial>`
- **执行范围**：方案 §0 阶段 A = **层 1（正常替换）+ 层 2（来源被拒后原正文仍可读）**
- **方案依据**：`docs/qa/2026-09/2026-09-24-s04-safe-real-library-acceptance-plan.md`
- **证据目录**：`docs/qa/evidence/2026-09-24-s04-safe-real-library/`

> **脱敏约定**：本报告只写「测试 TXT」「测试 EPUB」；书 id 一律写 `<bookId-TXT>` / `<bookId-EPUB>`；
> 隔离测试目录写 `<isolated-dir>`；仓库外私有快照目录写 `<repo-external-private-dir>`；
> 设备序列号写 `<serial>`。不含真实书名、私人父目录名或私有路径。

---

## 0. 结论摘要

| 项 | 结论 |
|---|---|
| 前置条件（方案 §5.2 v4 ⑨） | **已确认**：`files/datastore/*.preferences_pb` 已按「已包含，但仅保存原始二进制」更正 |
| APK 身份 | **PASS**：设备 `base.apk` SHA-256 与本地候选逐字节一致 |
| 安装 | **安装回退**（脚本 `FALLBACK_DIRECT_INSTALL`，exit 2）—— **不记为脚本 PASS** |
| 层 1 · 测试 TXT | **PASS**（来源更新 + 重新定位文件两条入口都推进正确） |
| 层 1 · 测试 EPUB | **FAIL** —— 来源刷新为 **no-op**，且产生「新 size + 旧 hash」自相矛盾配对；**已按方案 §6 停止条件停止该路径** |
| 层 1 · 进度行为 | **弹窗文案与实测不符**：重同步前后 `reading_progress` 全字段逐字节未变 |
| 层 2 · 三种来源拒绝 | **PASS**（三次注入，内部正文逐字节未变、测试书 DB 零推进）；**但发现 EPUB 路径会留下孤儿副本文件**，见 §4.2.1 |
| 既有书**所选字段**摘要 | **一致**（S01→S05 五次快照四表摘要全等） |
| 层 3 · 残留守卫 | **未执行**（阶段 B，未获授权） |
| Compose instrumentation | **BLOCKED**（MIUI 宿主拦截，零结果不作数） |

> ⚠️ 本报告**不主张**「用户所有数据零变化」。摘要只覆盖 §5.4 列明的四张表的**被选字段**，
> 未覆盖表、未选列、外部来源文件、DataStore 语义、封面缓存等均不在其中（见 §7）。

---

## 1. 前置条件核对

### 1.1 方案 §5.2 v4 ⑨ 的归档范围更正

**核对结果：已更正。**

方案 §5.2 的覆盖表现为三段：

| 段 | 内容 |
|---|---|
| 已覆盖 | `databases/`、`files/`、`shared_prefs/` |
| **已包含，但仅保存原始二进制** | `files/datastore/*.preferences_pb` —— 因 `tar` 整体导出 `files/` 而随归档；**不等于**已验证 DataStore 语义、也不等同于做过恢复演练 |
| 未覆盖 | `cache/`、应用外部目录、SAF 原始书库、设备级设置、其他应用 |

`files/datastore/*.preferences_pb` **不再**列在「未覆盖」中，与「`files/` 已整体导出」自洽。✔

### 1.2 设备与显示状态

| 命令 | 退出码 | 输出 |
|---|---|---|
| `adb devices` | 0 | 恰好 1 台 `<serial>	device`（无模拟器 / MuMu） |
| `adb -s <serial> shell getprop ro.product.model` | 0 | `22081212C` |
| `adb -s <serial> shell getprop ro.build.version.release` | 0 | `15` |
| `adb -s <serial> shell settings get system user_rotation` | 0 | `0`（竖屏，未改动） |
| `adb -s <serial> shell settings get global stay_on_while_plugged_in` | 0 | **`3`** |

> **亮屏设置**：本轮**从未修改** `stay_on_while_plugged_in`。上述 `3` 是操作前读值，
> 收尾时再次读取仍为 `3` —— 前后一致，**无需恢复动作**。
> 本轮亦**未**修改横屏设置或任何系统安全设置。

### 1.3 APK 身份（逐字节）

| 来源 | SHA-256 |
|---|---|
| 本地候选 `android/app/build/outputs/apk/debug/app-debug.apk` | `33282ff501d276193dc9c23c008cb15270fde9872acf0cb8335bb55742275892` |
| 设备 `run-as sha256sum <base.apk 路径>` | `33282ff501d276193dc9c23c008cb15270fde9872acf0cb8335bb55742275892` |

**一致 ⇒ PASS。** 被测包就是本轮 C 的候选包，未拿旧包验新代码。

源码候选与 APK 的时序（证明 APK 不早于源码）：

| 文件 | mtime |
|---|---|
| `ShelfImporter.kt` | 2026-09-24 16:31:18 |
| `EpubRepository.kt` | 2026-09-24 20:15:37 |
| `EpubParser.kt` | 2026-09-19 23:58:15 |
| **`app-debug.apk`** | **2026-09-24 20:23:16** |

三个相关源码均早于 APK 构建时间。✔

### 1.4 安装：**安装回退**（不记为脚本 PASS）

| 项 | 记录 |
|---|---|
| 命令 | `android/scripts/install_with_confirm.ps1 -Apk <apk> -Serial <serial>` |
| 脚本参数（先读后跑） | `-Apk`、`-Serial`（默认 `<serial>`）、`-PackageName`、`-TestApk`、`-NoDirectInstall`、`-TimeoutSeconds`（默认 90） |
| **退出码** | **2** |
| **结果** | **`FALLBACK_DIRECT_INSTALL`** |
| 脚本原话 | `FALLBACK: confirm loop did not complete (timeout after 90s without reaching the install-complete state; UI dumps parsed with errors: 0 (other UI action errors: 0)); trying direct adb install.` |
| 失败证据 | 保留于仓库外私有目录 `install-script.log`（脚本逐次 tap 记录，确认循环反复点到 `android:id/button3` 与 `com.miui.packageinstaller:id/second_button` 而始终未达完成态） |

**判定：这是安装回退，不是脚本 PASS。** 后续功能验收在确认 serial 后改用
`adb -s <serial> install -r <apk>` 继续。

- 全程**未卸载**、**未清除应用数据**、**未要求用户手动改 MIUI 设置**。

---

## 2. 指定范围恢复点与快照

### 2.1 恢复点（唯一）

```
adb -s <serial> exec-out run-as com.creationreadingassistant tar -cf - databases files shared_prefs
    > <repo-external-private-dir>\cra-s04-stageA-pre.tar
```

| 项 | 值 |
|---|---|
| 大小 | 2,269,997,056 B |
| SHA-256 | `662e0efb9cc2195bd21205509e8bb09eec200dc900bf743fda9b82af38a4cb02` |
| `tar -t` 退出码 | 0 |
| 顶层条目 | 恰好 `databases`、`files`、`shared_prefs` 三项 |

> ⚠️ **边界**：这是**指定范围的恢复点**，**不是**「应用数据全量备份」。
> 它**只能恢复上表三个顶层目录**；`cache/`（含 `cache/books/<bookId>`、`cache/dictionary-cache`、`cache/epub/<id>.epub`）、
> 应用外部目录、**用户 SAF 书库中的原始来源文件**、设备级设置**均无法由它恢复**。
> `files/datastore/*.preferences_pb` 因 `tar` 整体导出 `files/` 而**已随归档**，
> 但归档只保存**原始字节** —— **不等于**已验证 DataStore 语义，也**不等于**做过恢复演练。
> 本轮**未使用**该恢复点做任何回滚。

### 2.2 全程快照链

| 快照 | 大小 (B) | SHA-256 | 时点 |
|---|---|---|---|
| `cra-s04-stageA-pre.tar` | 2,269,997,056 | `662e0efb…a4cb02` | 操作前基线 |
| `snap-01-postimport.tar` | 2,270,000,640 | `0374915b…613e4e` | 隔离测试书导入后 |
| `snap-02-progress.tar` | 1,120,616,960 | `41834520…87a3ec` | 造出阅读进度后 |
| `snap-03-synced.tar` | 2,270,006,784 | `c6b55d0d…97af91` | 来源更新（重同步）后 |
| `snap-04-relocate.tar` | 2,270,019,072 | `79555ce7…4a1e59` | 重新定位文件后 |
| `snap-05-layer2.tar` | 2,270,021,120 | `1480a781…e940e1` | 层 2 三次注入后 |

全部快照 `tar -t` 退出码 0，顶层条目均为 `databases` / `files` / `shared_prefs`。
快照文件与解出的 DB 副本全部存放于**仓库外私有目录**，未入库、未上传。

### 2.3 隔离边界

| 项 | 做法 |
|---|---|
| 隔离目录 | `<isolated-dir>`（用户书库根下的**新建**子目录） |
| 测试对象 | 从用户真实书库中的 TXT / EPUB 各**复制**一份进隔离目录，标识为「测试 TXT」「测试 EPUB」 |
| 对用户既有书的动作 | **无**。未修改、未重导、未删除任何既有书 |
| 设备端删除 | 全程**只对点名文件**执行 `rm -f`（一次，删除自己误建的临时文件），**未做任何通配符删除** |
| 测试副本与来源 | **默认保留**，未清理 |

**一次跨污染风险的拦截（记录在案）**：应用内书库浏览器的「智能识别」模式会**递归扫描整个书库根**，
首轮选中了 **15 个候选，其中包含用户真实、尚未导入的书**。该路径被**立即取消**，
改用「当前目录」模式，仅选中隔离目录内的 2 个测试文件，并在确认页核对
「已选择 2 项」**只含测试文件**后才继续。全程未对非隔离对象执行任何操作。

---

## 3. 层 1 · 正常替换

### 3.1 测试 TXT · 来源更新（V1 → V2）

| 步骤 | 命令 / 操作 | 退出码 | 观察 |
|---|---|---|---|
| 1 | 隔离目录内把来源覆盖为 V2（165 B → 214 B，MD5 `a059ed03…`） | 0 | `adb push` 成功 |
| 2 | 书架下拉刷新 → 出现确认弹窗 | — | 弹窗见 [e02](evidence/2026-09-24-s04-safe-real-library/e02_dialog_source_update.png) |
| 3 | 点「重新同步」 | — | 弹窗只列出 2 本**隔离测试书**，未涉及任何非隔离书 ⇒ 未触发取消条件 |
| 4 | `run-as md5sum` 内部副本 | 0 | `a059ed03…`，214 B = **V2** ✔ |

字段级推进（离线只读查询导出 DB 副本）：

| 字段 | 重同步前 | 重同步后 | 判定 |
|---|---|---|---|
| `books.content_hash` | `23a96dd1…` (V1) | `a059ed03…` (V2) | 推进 ✔ |
| `books.size` | 165 | 214 | 推进 ✔ |
| `books.updated_at` | `14:49:56.882Z` | `14:59:08.324Z` | 推进 ✔ |
| `book_files.size` | 165 | 214 | 推进 ✔ |
| `library_source_refs.size` | 165 | 214 | 推进 ✔ |
| `library_source_refs.content_hash` | `23a96dd1…` | `a059ed03…` | 推进 ✔ |
| `library_source_refs.last_modified` | `1790260979000` | `1790261897000` | 推进 ✔ |

**四表哈希口径一致**（`books.content_hash` = `library_source_refs.content_hash` = 内部副本 MD5），
**未**出现方案 §6 停止条件所指的陈旧哈希。正文可读，读到 V2 标记词。**PASS。**

### 3.2 测试 TXT · 重新定位文件（V2 → V3）

| 步骤 | 操作 | 观察 |
|---|---|---|
| 1 | 隔离目录内把来源覆盖为 V3（214 B → 253 B，MD5 `63c0b4e1…`，标记词 `STAGEATXTV3MARKER`） | `adb push` + 设备端 `md5sum` 核对一致 |
| 2 | 长按书卡 →「重新定位文件」→ 选 V3 | 入口副标题「更换本地文件并保留阅读记录」 |
| 3 | `run-as md5sum` 内部副本 | `63c0b4e1d0edc8dc228d1e42d5cd21db`，253 B = **V3** ✔ |

| 字段 | 重定位前 | 重定位后 | 判定 |
|---|---|---|---|
| `books.content_hash` | `a059ed03…` | `63c0b4e1…` | 推进 ✔ |
| `books.size` | 214 | 253 | 推进 ✔ |
| `books.updated_at` | `14:59:08.324Z` | `15:07:46.088Z` | 推进 ✔ |
| `book_files.size` | 214 | 253 | 推进 ✔ |
| `library_source_refs.size` | 214 | **214（未推进）** | 符合方案预期 |
| `library_source_refs.content_hash` | `a059ed03…` | **未推进** | 符合方案预期 |

> 方案 §6 层 1 第 3 步已预先说明：**「重新定位文件」不推进来源基线**
> （只有 `refreshFromSource` → `refreshSourceBaseline` 才写基线；`repairFileTyped` → `reselect*` 不写）。
> 实测与该预期一致。**可观察副作用**：重定位后书卡仍显示「可更新」角标
> （来源 253 ≠ 基线 214）——这是上述设计差异的直接后果，非本次新增缺陷。

正文可读，读到 `STAGEATXTV3MARKER`：见 [e05](evidence/2026-09-24-s04-safe-real-library/e05_txt_reader_v3.png)。**PASS。**

### 3.3 测试 EPUB · 来源更新（V1 → V2）—— **FAIL**

| 步骤 | 操作 | 观察 |
|---|---|---|
| 1 | 隔离目录内把来源覆盖为 V2（1478 B → 1519 B，MD5 `9bba180d…`，含 `第二章 V2 内容 山岚 灯火`） | 设备端 `md5sum` 核对一致 |
| 2 | 书架下拉刷新 →「重新同步」 | 与 TXT 同一次弹窗，仅含 2 本测试书 |
| 3 | `run-as md5sum` 内部副本 | **`993d2aac890ceee16d7e7959e99c7ad5`，1478 B = 仍是 V1** ❌ |
| 4 | 打开测试 EPUB | 正文仍为 **`第二章 V1 内容 长夜 微光`**，见 [e04](evidence/2026-09-24-s04-safe-real-library/e04_epub_reader_still_v1.png) |

字段级对照：

| 字段 | 重同步前 | 重同步后 | 判定 |
|---|---|---|---|
| 内部副本 MD5 / 字节 | `993d2aac…` / 1478 | `993d2aac…` / 1478 | **未变** ❌ |
| `books.content_hash` | `993d2aac…` (V1) | `993d2aac…` (V1) | **未推进** ❌ |
| `books.size` | 1478 | 1478 | **未推进** ❌ |
| `book_files.size` | 1478 | 1478 | **未推进** ❌ |
| `library_source_refs.size` | 1478 | **1519** | **推进了** ⚠️ |
| `library_source_refs.content_hash` | `993d2aac…` | `993d2aac…` | **未推进** ⚠️ |
| `library_source_refs.last_modified` | `1790261015000` | `1790261897000` | 推进 ⚠️ |

**后果**：`library_source_refs` 落在 **「新 size(1519) + 旧 hash(993d2aac)」** 的自相矛盾配对上 ——
正是 `refreshSourceBaseline` 自己的注释明令禁止的组合（该注释要求「绝不沿用旧哈希，
未知就如实记为未知」）。该行现在描述的是**两次不同观测的混合**。

**该结果同时触发方案 §6 层 1 的停止条件**（`books.content_hash` 与 `library_source_refs.content_hash`
对应的内容不一致）。按用户指令「遇到缺陷保留现场并停止对应路径」，
**EPUB 来源更新路径就此停止**，未继续做「重新定位文件」的 EPUB 侧验证，未尝试任何修复。

**现场已保留**（只读）：内部副本、`snap-03-synced.tar`、`snap-05-layer2.tar`、截图 `04_epub_reopen_v1.png`
均在仓库外私有目录，未删、未回滚、未重试。

#### 3.3.1 机制定位（供开发窗口判定，验收窗口未改任何代码）

```
EpubRepository.replaceStoredEpub(...)
  └─ val validated = EpubParser.parse(context, selectedUri)     // ← 未传 expectedBookId

EpubParser.parse(...)
  └─ val id = expectedBookId ?: ("epub_" + uri.hashCode().toString(36))
     val epubFile = File(context.filesDir, "books/epub/$id.epub")
     if (!epubFile.exists() || epubFile.length() == 0L) { /* 才从 uri 复制 */ }
     // ↑ 已存在则直接沿用旧副本，来源字节从未被读取
```

`replaceStoredEpub` 未把 `stored.id` 传给 `parse`，于是 `parse` 用**来源 Uri 的哈希**推出一个
**不同的** id、命中「文件已存在」早退分支，把**替换前的旧内部副本**当成「已验证的新字节」，
再经 staging 覆盖回 target。随后 `refreshSourceBaseline` 的 `findInternalFile(ref.bookId)`
读到的仍是这份旧副本，于是算出**旧哈希** —— 而 `size` 走的是来源 provider，是**新值**。
两者叠加即得上述矛盾配对。

> **性质**：这不是本轮新引入的回归，而是该替换链路的既有实现缺口。它使 EPUB 的
> 「来源已更新」提示**永远无法被兑现** —— 用户看到角标消失、以为正文换了，实际读到的还是旧正文。
> 严重度由开发窗口判定；本报告只提供复现证据，不做修复。

### 3.4 进度行为（弹窗文案 vs 实测）

弹窗原文：「检测到 2 本书（…）的源文件已被修改，是否重新同步正文？**同步后将重置章节与阅读进度。**」
（见 [e02](evidence/2026-09-24-s04-safe-real-library/e02_dialog_source_update.png)）

**重同步前（S02）→ 重同步后（S03）的 `reading_progress` 全字段逐字段对照**：

| 字段 | 测试 TXT 前 → 后 | 测试 EPUB 前 → 后 |
|---|---|---|
| `book_id` | 一致 | 一致 |
| `progress_percent` | `0.0` → `0.0` 一致 | `78.57142639160156` → 同值 一致 |
| `last_read_at` | `2026-09-24T14:55:58.184Z` → 同值 一致 | `2026-09-24T14:55:47.261Z` → 同值 一致 |
| `updated_at` | 同值 一致 | 同值 一致 |
| `completion_state` | `finished` → `finished` 一致 | `finished` → `finished` 一致 |
| `current_location_json` | `{"offset":0,"locator_v2":{…}}` → 同值 一致 | `{"chapter":1,…,`offset":262,…}` → 同值 一致 |
| `completed_at` | `1790261692743` → 同值 一致 | `1790261737722` → 同值 一致 |
| `total_reading_time_ms` | `0` → `0` 一致 | `0` → `0` 一致 |
| `revision` / `device_id` / `payload` / `deleted_at` | 一致 | 一致 |

**结论：重同步**没有**重置章节与阅读进度 —— 全部字段（含 `updated_at`）逐字节未变。**

这不是「行还在」的弱证据：给出的是**每个字段的前后值**，`updated_at` 未推进即证明
该行**未被写过**。与源码事实吻合 —— `ShelfImporter` 中**不出现** `readingProgressDao` 调用，
`reselectEpub` / `reselectPlainText` 都不写 `reading_progress`。

⇒ **确认方案 §12.2 ④ 预判的文案—行为不一致**：弹窗承诺「重置章节与阅读进度」，
实际既不重置、也不更新。本轮实测为**文案与实际不符**（不是数据被误改）。

> 补充：S03→S05 之间两本书的 `last_read_at` / `updated_at` **有**推进，
> 那是**我自己打开书籍阅读**产生的正常进度写入（时间戳 23:03 / 23:04 / 23:20 / 23:18），
> 与替换链路无关。为避免混淆，判定只采用 S02→S03 这一段（替换操作前后、中间无阅读动作）。

---

## 4. 层 2 · 来源被拒后原正文仍可读

**覆盖边界（照方案 §6 层 2 写明）**：真机能安全触发的只有**「替换开始前来源即被拒」**这一类失败。
它**不**覆盖「target 已被改写后回滚」那一环 —— 后者需要故障注入，只能留在 JVM（见 §7）。

### 4.1 三种安全注入

| # | 注入 | 做法 | 实际错误文案（**实机原文**） |
|---|---|---|---|
| 1 | **损坏来源** | 90 B 非 ZIP 文件，对**测试 EPUB** 走「重新定位文件」 | `修复失败：zip END header not found` |
| 2 | **格式不符** | 对**测试 TXT** 选一个 EPUB 文件 | `修复失败：格式不一致：原书为 TXT，所选为 EPUB` |
| 3 | **空来源** | 0 B 文件，对**测试 TXT** 走「重新定位文件」 | `修复失败：文件内容为空` |

三条均只操作隔离测试书，未触碰任何非隔离书。截图：
[e06](evidence/2026-09-24-s04-safe-real-library/e06_inject1_corrupt_message.png) ·
[e07](evidence/2026-09-24-s04-safe-real-library/e07_inject2_format_message.png) ·
[e08](evidence/2026-09-24-s04-safe-real-library/e08_inject3_empty_message.png)。

> 注：注入 1 首次执行时截图错过了 Snackbar 显示窗口；已**重复执行一次**并在 1 秒内抓取，
> 得到上述原文与截图。重复执行不改变任何状态（两次内部副本 MD5 均未变）。

### 4.2 可观察证据

| 证据 | 预期 | 实测 | 判定 |
|---|---|---|---|
| 错误文案与注入对应 | 三条各对应 | 三条全部对应，无错配 | ✔ |
| 内部副本**字节数与 MD5 均不变** | 逐字节一致 | 测试 TXT：`63c0b4e1…` / 253 B 三次注入后不变；测试 EPUB：`993d2aac…` / 1478 B 不变 | ✔ |
| 打开测试书正文仍可读 | 读到原内容 | 测试 TXT 读到 `STAGEATXTV3MARKER`（[e05](evidence/2026-09-24-s04-safe-real-library/e05_txt_reader_v3.png)）；测试 EPUB 读到 `第二章 V1 内容 长夜 微光`（[e04](evidence/2026-09-24-s04-safe-real-library/e04_epub_reader_still_v1.png)） | ✔ |
| **测试书** DB 零推进 | 只对该测试书成立 | S03 → S05：测试 TXT 的 `books.content_hash/size/updated_at`、`book_files` 全部未变；`library_source_refs` 全部未变。测试 EPUB 同样全未变 | ✔ |
| 既有书**所选字段**摘要不变 | 摘要一致 | 四表摘要 S03 = S05（见 §5） | ✔ |
| `.repairing` / `.backup` **不得残留** | 无残留 | `find files -name "*.repairing" -o -name "*.backup"` 输出为空 —— 但**这只覆盖了这两种命名的中间态**，见下方 §4.2.1 的例外 | ✔（限该两种命名） |

#### 4.2.1 例外披露：注入 1 在应用私有目录留下了一个**孤儿副本文件**

方案 §6 层 2 的「零残留」检查只点名了 `.repairing` 与 `.backup` 两种中间态命名。
逐文件清点 `files/books/` 后发现**第三种**残留，必须如实登记：

| 项 | 值 |
|---|---|
| 路径形态 | `files/books/epub/<derived-id>.epub`（`<derived-id>` 由**来源 Uri 字符串**的 hashCode 推出，与 `bookId` 不同） |
| 字节 / MD5 | 90 B / `9d1df8cc53da7ec121c900c19cdc5c49` |
| 内容 | 与本次注入的非 ZIP 文件**逐字节相同**（注入 1 的来源副本） |
| mtime | `2026-09-24 23:16:41`（= 注入 1 首次失败的时刻） |
| 首次出现 | `snap-05-layer2.tar` 有、`snap-04-relocate.tar` 无 ⇒ **确由本次注入产生** |
| DB 引用 | `books` / `book_files` / `library_source_refs` **均无任何行引用它**（全表 LIKE 检索为空） |

**机制**：`EpubParser.parse` 在**校验之前**就先把来源字节复制到
`files/books/epub/<derived-id>.epub`，随后才 `ZipFile(epubFile)` 抛错；
失败路径只删 staging，**不删已落位的副本**。于是「校验失败」会永久留下一个孤儿文件。

**这同时解释了两个观察**：

1. 为什么三次注入里**只有 EPUB 那条**留下残留（TXT 路径 `reselectPlainText` 在
   抛错前不落位，且 `catch` 会清理）—— 这是 EPUB 独有的行为；
2. **与 §9-1 的 EPUB 缺陷同源**：`parse` 的「文件已存在就直接沿用」早退分支
   （`if (!epubFile.exists() || epubFile.length() == 0L)`）正是孤儿文件能被后续调用
   当成「已验证字节」使用的原因。

**未清理**（按「遇到缺陷保留现场」保留）。它位于应用私有目录、无 DB 引用，
不影响任何书；如需清除，必须**点名该文件**、禁止通配符：

```
adb -s <serial> shell run-as com.creationreadingassistant rm -f files/books/epub/<derived-id>.epub
```

本报告**未执行**该删除。

**判定：PASS（限「来源拒绝」这一环）。** 未触发停止条件（内部副本字节从未变化）。

### 4.3 导入历史（核对是否留下假记录）

只读解析 `files/datastore/import_history.preferences_pb`（自 `snap-05` 导出）：

| 时点 | status | 对象 | error |
|---|---|---|---|
| 22:49:56 | success | 测试 EPUB | — |
| 22:49:56 | success | 测试 TXT | — |
| 22:59:08 | success | 测试 EPUB | — |
| 22:59:08 | success | 测试 TXT | — |
| 23:07:46 | success | 测试 TXT | — |
| 23:16:41 | failed | 测试 EPUB | `zip END header not found` |
| 23:17:27 | failed | 测试 EPUB | `zip END header not found` |
| 23:18:35 | failed | 测试 TXT | `格式不一致：原书为 TXT，所选为 EPUB` |
| 23:19:02 | failed | 测试 TXT | `格式不一致：原书为 TXT，所选为 EPUB` |
| 23:20:03 | failed | 测试 TXT | `文件内容为空` |

**成功路径只有 success 记录，失败路径只有 failed 记录 —— 没有把失败记成 success 的假记录。** ✔
（成功 3 条对应 TXT 的来源更新 / 重新定位 + EPUB 的来源更新；失败 5 条对应三次注入及其重跑。）

---

## 5. 既有书「所选字段」摘要

> **措辞边界（照方案 §5.4，不得放宽）**：以下摘要**只能证明「这些被选字段一致」**。
> 它**不能**扩大为「用户所有数据零变化」，也**不能**无条件写成「既有书零变化」——
> 未覆盖的表、未选的列、原有外部来源文件、DataStore、封面缓存等都不在摘要内。

摘要口径：对下列四张表的**既有书行**（按已知测试书 id 前缀剔除），对所选列做规范化后取 SHA-256。

| 表 | 被选列 |
|---|---|
| `books` | `id, title, format, original_file_name, content_hash, size, local_content_path, content_status, updated_at, deleted_at` |
| `book_files` | `book_id, file_name, format, content_hash, size, updated_at, deleted_at` |
| `reading_progress` | `book_id, progress_percent, last_read_at, total_reading_time_ms, completion_state, current_location_json, updated_at, completed_at, deleted_at` |
| `library_source_refs` | `book_id, root_id, provider_authority, document_id, display_name, format, size, last_modified, content_hash, candidate_fingerprint, last_seen_at, availability` |

| 快照 | `books` | `book_files` | `reading_progress` | `library_source_refs` |
|---|---|---|---|---|
| S01 导入后 | `38301342…761e7` | `e84cc354…520fd` | `9fb8bbe1…8acf4` | `9a5a60e7…fd720b` |
| S02 进度后 | 同上 | 同上 | 同上 | 同上 |
| S03 重同步后 | 同上 | 同上 | 同上 | 同上 |
| S04 重定位后 | 同上 | 同上 | 同上 | 同上 |
| S05 注入后 | 同上 | 同上 | 同上 | 同上 |

**四表摘要在五次快照间全部一致。** 另有只读旁证：`run-as ls -la files/books/` 中
**既有书目录的 mtime 全部保持操作前的时间戳**，只有两本测试书对应的目录
（及 `epub/` 目录本身）时间戳发生变化。

**结论（限定措辞）**：既有书在**上述四张表的被选字段**上保持一致。
**未**主张既有书整体「不受影响」，也**未**主张用户所有数据零变化。

---

## 6. 层 3 · 残留备份守卫 —— **未执行**

**本报告不覆盖层 3。** 阶段 B 需要在应用私有目录手工合成 `.backup` 残留，
属实质性设备写入，须用户**单独确认**（方案 §4.3 U3 / U4）。本轮**未获授权**，
因此：**未合成任何 `.backup`、未执行层 3 的任何步骤、未得出层 3 的任何结论。**

> 阶段 A 通过**不**意味着阶段 B 通过。本报告不得被引用为层 3 已覆盖。

---

## 7. 未覆盖项（照方案 §7 全表登记）

以下情形**不得**在用户真实书库上尝试，本轮**全部未覆盖**：

| # | 情形 | 本轮状态 |
|---|---|---|
| D1 | `renameTo(backup)` 失败 → `copyTo(backup)` **中途失败** | **未覆盖**（无注入接缝） |
| D2 | 后置持久化失败 ⇒ 从 backup 逐字节还原 | **未覆盖**（仅 JVM 有覆盖） |
| D3 | 协程取消 ⇒ 回滚 | **未覆盖**（仅 JVM 有覆盖） |
| D4 | 进程被杀 / 断电残留（K2/K3/K4） | **未覆盖**（本轮**未**杀进程、**未**断电） |
| D5 | 大文件（>32 MiB）`content_hash` 置 null | **未覆盖**（仅 JVM 有覆盖） |
| D6 | `renameTo` 跨卷 / 权限失败 | **未覆盖** |
| D7 | 同一本书被两个入口**并发**替换 | **未覆盖** |
| D8 | 备份复制长度校验 | **未覆盖**（已登记缺口） |

**恢复点未覆盖范围**（不得省略）：`cache/`（含 `cache/books/<bookId>` 同步中转、
`cache/dictionary-cache`、`cache/epub/<id>.epub` 迁移回退）、应用外部目录、
**用户 SAF 书库中的原始来源文件**与 SAF 权限状态、设备级设置、其他应用 / 系统数据。

**摘要未覆盖范围**：上表四张表之外的任何表、上表所列列之外的任何列、
DataStore 的**语义**（只随 `files/` 保存了原始字节）、封面缓存、搜索索引表、
外部来源文件本体。

**本轮明确未做的事**（对照用户指令逐条确认）：

- 未在应用私有目录合成 `.backup`；
- 未故意杀进程、未断电、未测试复制中途失败；
- 未执行任何设备端通配符删除；
- 未卸载应用、未清除应用数据、未要求用户改 MIUI 设置；
- 未修改横屏或系统安全设置；未改 `stay_on_while_plugged_in`；
- 未 commit / push / stage / reset / checkout / clean。

---

## 8. 零结果项

| 项 | 状态 | 说明 |
|---|---|---|
| Compose instrumentation | **BLOCKED** | MIUI 阻止 Compose instrumentation Activity 启动（`result code=102`，跨 09-22 / 09-23 / 09-24 三轮复现）。本轮取证方式为**手工 UI 操作 + `adb` 只读证据**，**不依赖** instrumentation。**零结果既非 PASS 也非 FAIL**，不得写成通过。 |

---

## 9. 待开发窗口判定的事项

| # | 事项 | 证据 | 本报告立场 |
|---|---|---|---|
| **1** | **EPUB 来源刷新为 no-op**：替换链路把「来源字节」误判为「已存在的旧内部副本」，导致 EPUB 来源更新**永远不生效**，同时把 `library_source_refs` 写成「新 size + 旧 hash」的矛盾配对 | §3.3、`snap-03`、[e04](evidence/2026-09-24-s04-safe-real-library/e04_epub_reader_still_v1.png) | 只报缺陷 + 机制定位，**未修代码**。已按停止条件停止该路径 |
| **2** | **弹窗文案与实测不符**：承诺「同步后将重置章节与阅读进度」，实测全字段（含 `updated_at`）逐字节未变 | §3.4、S02/S03 字段级对照 | 只报事实，**未改文案** |
| **3** | **「重新定位文件」不推进来源基线**，导致重定位后书卡仍显示「可更新」 | §3.2 | 方案已预先说明该差异；此处作为**可观察副作用**登记，供判定是否为可接受 UX |
| **4** | **EPUB 解析失败留下孤儿副本**：`EpubParser.parse` 先落盘、后校验，校验失败只删 staging、不删已落位的副本，导致私有目录永久积累无人引用的文件。与 §9-1 同源（都是「文件已存在就直接沿用」那条早退分支） | §4.2.1、`snap-04` vs `snap-05` 差分 | 只报缺陷，**未清理**（按「保留现场」）。清理需点名文件，禁止通配符 |

> **§9-1 与 §9-4 同源**：`EpubParser.parse` 的
> `if (!epubFile.exists() || epubFile.length() == 0L)` 早退分支既是**孤儿文件的成因**，
> 也是**来源更新失效的成因** —— 后者是「早退命中旧副本」、前者是「校验失败仍落盘」。
> 修复时应把这两点一起考虑（落盘应移到校验成功之后，且替换路径必须传 `expectedBookId`）。

---

## 10. 按阶段收口

| 阶段 | 范围 | 结论 |
|---|---|---|
| **阶段 A** | 层 1 + 层 2 | 层 1 · 测试 TXT **PASS**；层 1 · 测试 EPUB **FAIL**（见 §9-1）；层 1 · 进度行为**与文案不符**（见 §9-2）；层 2 **PASS**（限「来源拒绝」这一环） |
| **阶段 B** | 层 3 | **未执行**（未获授权）。阶段 A 的结果**不**构成层 3 的任何结论 |

**总体判定**：阶段 A **部分通过**。隔离测试 TXT 的正常替换（两条入口）与来源拒绝的
原正文保全均已取得正向证据；**测试 EPUB 的来源更新路径存在真实产品缺陷，已在停止条件处
停下并保留现场**。该缺陷不影响层 2 的结论（层 2 只主张「失败后原正文仍逐字节可读」，
三次注入全部满足）。

---

## 11. 本报告未做的事

- 未修改任何产品源码、测试源码、Gradle 配置、脚本或 Git 状态；
- 未 commit / push / stage / reset / checkout / clean；
- 未修改或删除任何用户既有书、未重导、未做设备端通配符删除；
- 未使用恢复点做回滚；
- 报告内不含真实书名、私人目录名或私有路径。

## 12. 证据清单

| 文件 | 内容 |
|---|---|
| [e01_apk_identity_home.png](evidence/2026-09-24-s04-safe-real-library/e01_apk_identity_home.png) | 应用启动态（用于确认运行的是被测构建） |
| [e02_dialog_source_update.png](evidence/2026-09-24-s04-safe-real-library/e02_dialog_source_update.png) | 来源更新确认弹窗原文（含「重置章节与阅读进度」） |
| [e03_shelf_after_resync.png](evidence/2026-09-24-s04-safe-real-library/e03_shelf_after_resync.png) | 重同步后书架：两本测试书「可更新」角标已消失 |
| [e04_epub_reader_still_v1.png](evidence/2026-09-24-s04-safe-real-library/e04_epub_reader_still_v1.png) | **EPUB 缺陷证据**：重同步后正文仍为 V1；同时作为层 2 三次注入后 EPUB 仍可读原正文的证据（同一画面，未另拍） |
| [e05_txt_reader_v3.png](evidence/2026-09-24-s04-safe-real-library/e05_txt_reader_v3.png) | 测试 TXT 重定位后正文为 V3；同时作为层 2 三次注入后 TXT 仍可读原正文的证据 |
| [e06_inject1_corrupt_message.png](evidence/2026-09-24-s04-safe-real-library/e06_inject1_corrupt_message.png) | 注入 1 错误原文 |
| [e07_inject2_format_message.png](evidence/2026-09-24-s04-safe-real-library/e07_inject2_format_message.png) | 注入 2 错误原文 |
| [e08_inject3_empty_message.png](evidence/2026-09-24-s04-safe-real-library/e08_inject3_empty_message.png) | 注入 3 错误原文 |

所有截图均已**裁切脱敏**后入仓（只保留与结论相关的区域，去掉书库其余书籍与状态栏信息）。
原始未脱敏截图、六份 `.tar` 快照、解出的 DB 副本、`logcat` 全量导出、UI dump 与
`install-script.log` 均留在**仓库外私有目录**，未入库、未上传。
