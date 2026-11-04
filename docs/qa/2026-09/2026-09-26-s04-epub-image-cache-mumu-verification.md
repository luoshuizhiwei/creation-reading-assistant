# S04 单书 EPUB 图片缓存清理：MuMu 模拟器验证

> ## ⚠️ 结论性质：**MuMu 模拟器验证，不是真机验收**
>
> 本轮全部操作在 **MuMu 模拟器实例 1**（`127.0.0.1:16416`，`SM-S9280` / Android 12 / SDK 32）上完成。
> **模拟器不等于真机**：文件系统语义（尤其删除失败与文件锁）、SAF provider 行为、
> 系统版本差异均可能不同。本报告的 PASS **不可**当作真机验收结论，也不替代真机验收。

- **日期**：2026-09-26
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支 / HEAD**：`codex/recover-worktree-20260920` / `5aed5939600e491843e3c3c44453921bd6273a233c`（全程未变）
- **被测 APK**：`E:\creation-reading-assistant\codex-s04-build-20260925\project-builds\app\outputs\apk\qahost\app-qahost.apk`
- **证据目录（E 盘）**：`E:\creation-reading-assistant\codex-s04-verify-20260926\`（7.4 MB：截图 26 张、UI dump 28 份、DB 对账文本 10 份、一次性样本）
- **唯一新增仓库文件**：本报告。未改源码、未 commit / push / reset / checkout。
- **真实手机 `c49ac6cf` 全程零命令**（见 §8 合规自证）。

---

## 1. 判定汇总

| 项 | 判定 |
|---|---|
| 本地 APK 哈希 / 包名核对 | **PASS** |
| 安装（确认脚本 → FALLBACK 直装） | **FALLBACK**（**不是**确认循环 PASS，原因见 §3） |
| 安装后设备侧 APK 哈希 = 新哈希 | **PASS** |
| 既有 21 本书安装前后未变 | **PASS**（逐字节一致） |
| **① 含图 EPUB 单书清缓存回收图片目录** | **PASS** |
| **② 图片目录内残留 ⇒ 拒绝且全保留** | **PASS** |
| 既有 21 本书在测试后未变 | **PASS**（books 逐字节一致） |
| FATAL / ANR（本包） | **PASS**（0 条） |
| **⚠️ 既有书的阅读进度被我的导航误改** | **见 §7 —— 本轮引入了一处非预期副作用** |

**整体：PASS（限 §9 未覆盖范围）；但 §7 记录了一处由本次验证操作引入的既有数据改动，须由总指挥裁决。**

---

## 2. 前置核对（全部通过）

| # | 项 | 值 |
|---|---|---|
| 1 | `adb devices` | 两个设备：真机 `c49ac6cf`、模拟器 `127.0.0.1:16416`；**后续每条命令均 `-s 127.0.0.1:16416`** |
| 2 | 模拟器身份 | `SM-S9280`，Android 12，SDK 32，`mCurrentRotation=ROTATION_0`（竖屏） |
| 3 | 本地 APK SHA-256 | `15e2a9ea049995ee80288759f84c316f9664a2d41caaddca4d00d403ee54b51c` = 预期值 **逐字节一致** |
| 4 | 包名 | `com.creationreadingassistant.qahost`（`aapt` 与设备实测一致） |
| 5 | 安装前设备包状态 | 已装，`versionCode=3` / `versionName=0.5.1`，`lastUpdateTime=2026-09-25 13:24:18`，旧 APK 哈希 `24525998…0794b`（**正是集成报告声明已失效的旧哈希**） |
| 6 | 模拟器启动 | MuMu 实例 1 原为关闭状态，用 `MuMuManager.exe control --vmindex 1 launch` 启动（`player_state=start_finished`，`adb_port=16416`） |

> MuMu 实例 1 的 ADB 端口由 `configs/vm_config.json` 的 `port_forward.host_port=16416` 与
> `info -v 1` 的 `adb_port: 16416` 双重确认为本实例，非实例 0（16384）。

---

## 3. 安装：确认脚本未成立，走 FALLBACK

命令与**真实退出码**：

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File "android/scripts/install_with_confirm.ps1" \
  -Apk "E:\creation-reading-assistant\codex-s04-build-20260925\project-builds\app\outputs\apk\qahost\app-qahost.apk" \
  -PackageName com.creationreadingassistant.qahost -Serial 127.0.0.1:16416
```

**退出码：`2`**

原始输出关键行（全文见 `install-mumu.log`）：

```
adb devices entries=2; serial '127.0.0.1:16416' listed as device: True
...app-qahost.apk: 1 file pushed, 0 skipped. 35.7 MB/s (52149270 bytes in 1.393s)
Error type 3
Error: Activity class {com.miui.packageinstaller/com.miui.packageInstaller.InstallStart} does not exist.
...
FALLBACK: confirm loop did not complete (installer UI never appeared (MIUI security popup or wrong VIEW handler); UI dumps parsed with errors: 0 (other UI action errors: 0)); trying direct adb install.
RESULT: FALLBACK_DIRECT_INSTALL (installed via direct adb install; NOT a confirm-loop PASS)
```

**原因**：MuMu 是 AOSP 镜像，**没有 MIUI 包安装器**（`com.miui.packageinstaller` 不存在），
脚本的确认循环**在本环境结构性不可用** —— 不是超时、也不是被安全弹窗拦截。
脚本按设计走了既有授权的定向 `adb install -r` 回退。

**⇒ 本轮安装结果记为 FALLBACK，不记为确认循环 PASS。**

安装后回读设备侧 APK 哈希：

```
15e2a9ea049995ee80288759f84c316f9664a2d41caaddca4d00d403ee54b51c
```

= 新 APK 哈希 **逐字节一致**，安装确实生效（且 `lastUpdateTime` 已从 13:24 推进）。

---

## 4. 既有 21 本书的安装前后对账

安装前后各取一次全量 `books` 快照（id / format / content_status / size / local_content_path）：

```bash
adb -s 127.0.0.1:16416 shell "run-as com.creationreadingassistant.qahost sqlite3 databases/creation_reading_assistant_native \
  \"select id||'|'||format||'|'||coalesce(content_status,'')||'|'||coalesce(size,0)||'|'||coalesce(local_content_path,'') from books where deleted_at is null order by id;\""
```

| | 结果 |
|---|---|
| 安装前 | 21 本（`baseline-21-books.txt`） |
| 安装后 | 21 本（`post-install-21-books.txt`） |
| `diff` | **IDENTICAL to baseline** |

⇒ 安装过程未改动任何既有书。

---

## 5. ① 含图 EPUB 单书清缓存：图片目录被回收（PASS）

### 5.1 一次性样本

自建 `s04-verify-A.epub`（1675 B）：`mimetype` + `META-INF/container.xml` +
`OEBPS/content.opf`（manifest 含 3 个 `image/png` 项）+ `OEBPS/ch1.xhtml`（含 3 个 `<img src>`）
+ 3 个自生成 64×64 PNG。推送后设备侧哈希与本地一致
（`4ee33961de2410c25828ad57b17881e91b001d7f6a6bf24c950f846b17c79fbe`）。

经 App 内「导入书籍 → 选择文件」SAF 选择器导入 ⇒ 入库为 **`epub_r9whhx`**（1675 B，`available`）。

### 5.2 实际打开含图章节

在阅读器打开该书，截图 `shot-09-readerA.png` **肉眼可见 3 个红色方块图片**已渲染。
随后私有目录出现抽取产物：

```
files/books/epub/epub_r9whhx/
├── img/-qpqlrz-2.png   135 B
├── img/-qpqlrz-3.png   135 B
└── img/-qpqlrz-4.png   135 B
```

三张图 SHA-256 均为 `3b0ea0a9bfd9b1475120871b03f13abe717e4736bfbf828fbe15f6f2360b1afe`
（与源 PNG 内容一致，三张为同一张图的副本）。

### 5.3 只对这本样本执行单书清缓存

路径：书架页 ⋯ → 批量选择 → 仅勾选 `S04 Verify A`（截图 `shot-19-selected.png` 显示「已选 1 本」）
→ 底栏「清缓存」。

### 5.4 前后对账

| 项 | 清理前 | 清理后 |
|---|---|---|
| `books/epub/epub_r9whhx.epub` | 存在，1675 B | **不存在** |
| `books/epub/epub_r9whhx/img/` | 存在，3 张 PNG | **整个目录不存在** |
| `books.content_status` | `available` | `missing` |
| `books.local_content_path` | `/data/.../epub_r9whhx.epub` | `NULL` |
| `books.local_uri` | `content://...s04-verify-A.epub` | `NULL` |
| `book_files.deleted_at` | `NULL` | `2026-09-25T20:18:19.638Z` |

**UI 证据**：清理后书卡出现「**正文未在本机**」角标（`shot-20-after-clear.png`），与 DB 的
`missing` 一致。

**⇒ 图片缓存目录确实被回收 —— 这正是本次修复的目标行为，PASS。**

> 修复前行为（集成报告 §2.1）：`clearBookCache` 只删 `<id>.epub`，`<id>/img/` 被留成无主目录。
> 本轮实测确认该目录现已被回收。

---

## 6. ② 图片目录内残留 ⇒ 拒绝且全保留（PASS）

### 6.1 一次性样本 B

自建 `s04-verify-B.epub`（1477 B，2 张图），导入为 **`epub_rqy3w4`**。同样打开含图章节，
`img/` 下生成 2 张 PNG。

### 6.2 植入标记（仅在我自己的样本目录内）

```bash
adb -s 127.0.0.1:16416 shell "run-as com.creationreadingassistant.qahost sh -c \
  'printf S04VERIFY-BACKUP-MARKER > files/books/epub/epub_rqy3w4/img/marker.backup'"
```

标记：23 B，SHA-256 `b45eb44f1a8f73278b4add4b1bf4e073…`（写入前记录）。

### 6.3 执行单书清缓存 → 被拒绝

### 6.4 前后对账（全部保留）

| 项 | 操作前 | 操作后 |
|---|---|---|
| `<id>.epub` | 存在 1477 B | **存在 1477 B（未变）** |
| `img/-qpqlrz-2.png` | 135 B | **存在 135 B** |
| `img/-qpqlrz-3.png` | 135 B | **存在 135 B** |
| `img/marker.backup` | 23 B | **存在 23 B（内容未变）** |
| `books.content_status` | `available` | **`available`（未变）** |
| `books.local_content_path` | `…/epub_rqy3w4.epub` | **未变** |
| `book_files.deleted_at` | `NULL` | **`NULL`（未变）** |

**UI 证据**（`shot-26-refusal-toast.png`，逐字）：

> 1 本检测到未完成替换的残留备份，已整本保留：该备份可能是该书仅存的正文，未删除也未自动恢复，书架信息未改动

**⇒ 拒绝成立、两侧全保留、DB 指针未动、UI 如实说明，PASS。**

### 6.5 标记清理

验证完成后**仅删除我自己创建的那个 `marker.backup`**：

```bash
adb -s 127.0.0.1:16416 shell "run-as com.creationreadingassistant.qahost rm files/books/epub/epub_rqy3w4/img/marker.backup"
```

复核：`find files/books -name "*.backup" -o -name "marker*"` → **空**（全包无残留标记）。
B 的 `img/` 恢复为 2 张 PNG，未被本次清理波及。

> 过程中为补拍 UI 证据曾**再次**植入同一标记并**再次删除**；两次删除均退出码 0，
> 最终状态为空。除该标记外未触碰任何其他书的文件或数据库行。

---

## 7. ⚠️ 非预期副作用：既有书 `sample-021` 的进度被改动

**这是本轮验证操作引入的，必须如实记录。**

### 7.1 现象

最终 `reading_progress` 比基线**多了一行**：

```
a29a42cb-ba2b-4a57-8de6-840f165b9f97 | pct=0.0 | finished | upd=2026-09-25T20:19:56.091Z | completed_at=1790367577764
```

该书是**既有 21 本之一**（`sample-021.md`），基线中**没有**它的进度行。

### 7.2 发生了什么

时间线（设备时钟）：

| 时刻 | 事件 |
|---|---|
| 20:19:37 | `chapter_reads` 新增 `a29a42cb / chapter 0` |
| 20:19:56 | `reading_progress` 新增 `a29a42cb` 行（`pct=0.0`, `finished`） |
| 20:21:34 | `epub_rqy3w4`（样本 B）的 `chapter_reads` 行 |

我在 §6 之前为**定位样本 B** 在书架页点击书卡时，**误点开了既有书 `sample-021`**
（其书卡当时位于样本 B 相邻位置），阅读器随即在打开时写入初始进度与「已读章节」。

### 7.3 影响范围（已核实）

| 项 | 状态 |
|---|---|
| `sample-021` 的**正文文件** | **未变**：143 B，SHA-256 `dcfa7daa31c1cfacd371d12676fc4f65ef50388d45940b820f4fc92563e911a6`，mtime 仍为 `2026-09-25 10:45` |
| `books` 行 | **未变**：`available` / 143 / 路径 / `imported_at` / `updated_at` 全部同基线 |
| 其他 20 本既有书 | **全部未变**（books 逐字节一致；5 条既有进度行**逐字节一致**） |
| 丢失的数据 | **无**（无行被删除；`reading_sessions` / `notes` / `highlights` 该书均为 0 条） |
| 新增的数据 | 1 条 `reading_progress` 行 + 1 条 `chapter_reads` 行（均为「打开过」的自然产物） |

**可见影响**：`sample-021` 的书卡会从「未开始」变为「已完成」分组（`completion_state=finished`
且 `pct=0.0`）。这是阅读器打开书籍时的正常写入行为，不是清缓存逻辑的缺陷。

### 7.4 处置建议（**未自行执行**）

该行可用 App 内「取消已读完 / 标记为未读」还原为无进度状态，或保留（该书确实被打开过）。
**是否还原请总指挥裁决** —— 我不在未授权的情况下改动既有书数据。

---

## 8. 既有 21 本书测试后复核与日志

| 检查 | 结果 |
|---|---|
| `books` 全表（剔除 2 本一次性样本）与基线 `diff` | **IDENTICAL**（换行归一后） |
| 5 条既有 `reading_progress` 行与基线 `diff` | **5/5 逐字节一致** |
| 既有 `books/epub/epub_me8lux/`（含既有 `img/` 空目录） | 未变（mtime 仍 10:50） |
| 既有书行数 | 21（未增减） |
| **本包 FATAL EXCEPTION** | **0 条** |
| **本包 ANR** | **0 条** |
| crash buffer 中唯一 FATAL | `com.huawei.hwid.core`（华为 HMS 框架，**与本包无关**） |

---

## 9. 未覆盖范围（不得外推）

1. **不是真机验收。** 全部结论仅限 MuMu 模拟器实例 1（Android 12 / SDK 32 / AOSP 镜像）。
   真机的文件锁语义、SAF provider、系统版本差异均未覆盖。
2. **未覆盖 `ClearedWithRemains`（删除失败）分支。** 集成报告用例 4 依赖 Windows 独占删除语义，
   在模拟器上无法可靠构造「图片被占用」；本轮**未验证**该分支的 UI 反馈。
3. **未覆盖 `AbortedMidway`（预检通过、删除中发现新残留）。** 与集成报告 §5-3 记录一致，
   该路径在仓储层确定性触发需要新增接缝，本轮未构造。
4. **未覆盖 TXT/MD 侧清理路径。** 本轮只验证 EPUB 图片目录这一新增位置。
5. **未覆盖「清空离线书籍与资源」（`OfflineBooksResidueClear`）路径。**
6. **未做全量清理入口验证**（按要求排除）。
7. **未测量实际释放的磁盘字节数。** 样本仅 405 B 图片，空间收益无法有意义地测量。
8. **未做性能/并发验证**（清理期间的竞态、大量书籍批处理）。
9. **安装未走确认循环**（§3），故「MIUI 确认 UI 流程」在本环境未被验证 —— 该流程在 AOSP 上不存在。
10. **未验证取消/中断场景**（清理中途切后台、杀进程）。

---

## 10. 合规自证

| 动作 | 是否执行 |
|---|---|
| `adb devices` 核对 + 每条命令显式 `-s 127.0.0.1:16416` | ✅ |
| **对真实手机 `c49ac6cf` 执行命令** | ❌ **零命令**（仅出现在 `adb devices` 列表输出中） |
| 本地 APK 哈希 / 包名 / 安装前后设备状态核对 | ✅ |
| 安装（仅 qahost 包，经确认脚本 → FALLBACK） | ✅ 退出码 2，如实记为 FALLBACK |
| 清理既有 21 本书 / 删除 / 注入残留到既有书 | ❌ 未执行 |
| 对一次性样本执行清缓存与残留拒绝 | ✅ 各 1 次 |
| 清理自己创建的标记 | ✅ 已删净，全包无残留 |
| 正式包 `com.creationreadingassistant` | ❌ 未安装、未启动、未修改（真机上的正式包 `lastUpdateTime` 仍为 `2026-09-25 02:12:17`） |
| 源码修改 / commit / push / reset / checkout | ❌ 未执行 |
| 大体积产物落盘位置 | ✅ 全部在 `E:\creation-reading-assistant\codex-s04-verify-20260926\`（7.4 MB） |
| 未覆盖其他窗口文件 | ✅ 唯一新增仓库文件 = 本报告 |

**唯一的既有数据改动**是 §7 记录的 `sample-021` 进度行（由误点打开引入），已完整记录并待裁决。

---

## 11. 一句话结论

**在 MuMu 模拟器上，S04 图片缓存清理的两条目标行为均成立**：含图 EPUB 单书清缓存后
`books/epub/<id>/img/` 确实被回收（含 DB 指针清空与「正文未在本机」UI 反馈）；
图片目录内存在 `.backup` 残留时整本拒绝、两侧与 DB 全部保留，且 UI 如实说明。
安装走的是 **FALLBACK 直装**（模拟器无 MIUI 安装器），**不是**确认循环 PASS。
**模拟器结论不等于真机验收**；且本轮误触导致既有书 `sample-021` 多了一条进度行，已如实记录。
