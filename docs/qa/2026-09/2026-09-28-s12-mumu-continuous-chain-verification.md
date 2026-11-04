# S12 MuMu qahost 隔离验收（补验）— 开源阅读 finished 标记连续链路原始证据重采

> **Codex 2026-09-28 独立复核：MuMu 单章 TXT 隔离链路限定通过。** E 盘已补原始 seed `OK (1 test)`、生产导入日志、逐阶段 sqlite3 文件、受控 ZIP/测试源码、UI XML 与前后文件哈希。实读 `db/01-after-import.txt` 为 `reading/99.4000015258789/legado_v1`，`db/03-after-move-exit.txt` 为 `reading/99.4003753662109/locator_v2`；报告对未移动退出和稳定加载采样有对应原件。清理后 36 个既有文件、7 个 shared_prefs、6 个 datastore 哈希一致，主 DB 哈希 `d938…`→`7bd0…` 不同，且未逐行核对既有 33 本书，故只接受本轮隔离行/文件/设置范围，不称全库逐字节恢复。实体手机、EPUB 与多章可换算分支未验。

> **本报告补齐 `2026-09-28-s12-mumu-finished-marker-continuous-acceptance.md`
> 顶部 Codex 复核指出的证据缺口。** 原复核认定：UI 行为可限定接受，
> 但执行时的原始生产导入日志、逐阶段 DB 查询输出、一次性 seed 输入与测试结果、
> APK 哈希回读、操作后逐文件哈希清单**均未保存**，因此报告中的
> `99.4000015 / reading`、不移动退出 `updated_at` 不变、移动后 `locator_v2`、
> 既有库「逻辑还原」当时只能视为执行者陈述；且 `10-reopen-no-prompt.png`
> 停在加载画面，单帧不能证明稳定后不再提示。
>
> **本轮先核实：上轮的原始终端记录确实未留存**（终端标签为空，E 盘原工件目录仅 23 个文件），
> 故按任务约定在**同一 qahost 隔离书上重采最小链路**，并把每一步的原始输出落盘。

| 字段 | 值 |
|------|-----|
| 日期 | 2026-09-28 |
| 设备 | MuMu `emulator-5556`（SM-S9280 / API 32，物理密度 280，验收期间 override 420） |
| 验收构建 | `com.creationreadingassistant.qahost`（debug，`testBuildType=qahost`） |
| 主 APK SHA-256 | `e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a` |
| 测试 APK SHA-256 | `a2696cf1a0fd705179ca5ba8b92b29e3c5586bd341e85774f5d1796460284002` |
| 隔离书 ID | `00000000-0000-4000-8000-000000000012` |
| 受控导入 ZIP SHA-256 | `9779598645815b3732fc460d9400d862288365efd9a0838cfda991eb46230a71`（267 B） |
| 隔离正文 SHA-256 | `eb85f521af64137f219ee2b9a380bf3e8d431d08b37b6b2a30dde12ec5523bad`（5 000 002 B） |
| 一次性 seed 源码 SHA-256 | `024e9a7c92faa0e034a0ae10d0c694bea8e8f44bec2df9ba26528e6ea2f69bee` |
| 工件目录 | `E:\creation-reading-assistant\codex-c-final-isolated-20260928\s12\` |
| 源码改动 | 无（一次性 seed 测试已从仓库删除，仓库无新增/修改产品源文件） |

## 结论

| # | 项 | 结果 |
|---|----|------|
| ① | 受控末章估算 → 生产导入器 → 真 Room：`reading` / 99.4% / `legado_position_v1` | **PASS**（设备 sqlite3 原始输出） |
| ① | 书架 UI 与 DB 一致（`测试 · 第200章 · 已读 99%`） | **PASS** |
| ① | 「在读」筛选包含该书 / 「已完成」筛选不含 | **PASS** |
| ② | 打开阅读器 → **已加载**且提示可见 | **PASS**（提示与已加载正文同帧） |
| ② | 不移动退出：`updated_at` 与 marker **不变** | **PASS**（前后逐字段相同） |
| ② | 重开：提示再次可见 | **PASS** |
| ② | 翻页：正文推进 | **PASS** |
| ② | 移动+退出：转成原生 `locator_v2`，`legado_v1` marker 清除 | **PASS** |
| ② | 再重开：**加载完成后**无提示（多点采样，非单帧） | **PASS**（5 次采样跨 18 s，0 提示） |
| ③ | 原始证据完整（导入日志 / 逐阶段 sqlite3 / 截图 / XML / 前后哈希清单） | **PASS** |
| ④ | 主 DB **逐字节**还原 | **未做到，只报行级 + 文件级还原**（见 §5.1） |
| ⑤ | 设备状态与隔离数据清理 | **PASS** |

**没有任何一项 FAIL。**

---

## 1. 一次性 seed（真实生产导入器 + 真 Room）

```
adb -s emulator-5556 shell am instrument -w -r \
  -e class com.creationreadingassistant.feature.sync.S12FinishedImportDeviceSeedTest \
  com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
→ Time: 0.143 / OK (1 test) / INSTRUMENTATION_CODE: -1     EXIT=0
                                            （logs/01-instrument-seed.log）
```

### 生产导入日志（设备 logcat 原文，`logs/02-logcat-seed.log`）

```
I S12Seed: ISOLATED_TEXT path=/data/user/0/com.creationreadingassistant.qahost/files/books/00000000-0000-4000-8000-000000000012/test_s12_isolated.txt size=5000002 sha256=eb85f521af64137f219ee2b9a380bf3e8d431d08b37b6b2a30dde12ec5523bad
I S12Seed: IMPORT zipSize=267 zipSha256=9779598645815b3732fc460d9400d862288365efd9a0838cfda991eb46230a71
I S12Seed: IMPORT report scanned=1 imported=1 unchanged=0 unmatched=0 ambiguous=0 unsupported=0 invalid=0
I S12Seed: SEEDED db_row book_id=00000000-0000-4000-8000-000000000012 completion_state=reading readingState=READING progress_percent=99.4 completed_at=null updated_at=2026-09-28T14:26:10.157Z revision=1 current_location_json={"legado_position_v1":{"ci":199,"co":500}}
I S12Seed: SEED_ASSERTED book=测试 TXT state=READING percent=99.4 marker=legado_v1
```

受控输入与上一轮**逐字节一致**：正文 5 000 002 B、SHA-256 `eb85f521…`（与上轮报告值相同），
ZIP 已随证据归档（`seed/s12-controlled-import.zip`）。

`CraIsolatedTestRunner` 只替换 Application、**不重定向 `filesDir`**（源码已核），
所以 seed 写的正文与 DB 行对生产 App 可见 —— 这是后续 UI 链路成立的前提。

---

## 2. 逐步 sqlite3 原始查询输出

> 全部为设备端 `su 0 sqlite3` 直读，非测试框架断言。
> 原始文件：`db/01-after-import.txt` … `db/05-cleanup.txt`。

### 2a 导入后（`db/01-after-import.txt`）

```
book_id               = 00000000-0000-4000-8000-000000000012
progress_percent      = 99.4000015258789
last_read_at          = 2025-09-28T09:00:00Z
total_reading_time_ms = 0
completion_state      = reading
current_location_json = {"legado_position_v1":{"ci":199,"co":500}}
payload               =
revision              = 1
device_id             =
updated_at            = 2026-09-28T14:26:10.157Z
completed_at          =            ← 空：估算进度不生成完结时间
deleted_at            =

books: title=测试 TXT  author=测试  format=txt  size=5000002
       content_status=available
       local_content_path=/data/user/0/com.creationreadingassistant.qahost/files/books/00000000-0000-4000-8000-000000000012/test_s12_isolated.txt

全库行数：books=34  reading_progress=17  book_content=21   （基线 33/16/21，仅 +1/+1/+0）
WAL / SHM：均已不存在（连接干净关闭，无未落盘日志）
```

`rawEstimate=99.5%` 经 `approximatePercent` 钳到 **99.4**（`≤99.4` 满足）；
`completion_state=reading` + `completed_at` 为空 → **未被静默标记完结**；
marker 原样保留 `legado_position_v1{ci:199,co:500}`。

### 2a-UI 书架与筛选（`ui/02-shelf.*`、`ui/03-*`、`ui/04-*`）

```
全部 34 | 在读 4 | 未开始 21 | 已完成 9 | 搁置
测试 TXT        →  测试 · 第200章 · 已读 99%
在读筛选(4 本)   → 含「测试 TXT」
已完成筛选       → 不含「测试 TXT」（出现 0 次）
```

### 2b 打开阅读器 —— **已加载**且有提示（`ui/05b-reader-open-now.xml`、`ui/07a-reopen-dump1.xml`）

加载完成标志（两个都必须在）：

```
[View] desc=阅读正文已就绪
[View] desc=分页正文已就绪
```

提示文本（与已加载正文**同帧**出现，因此不是加载画面误判）：

```
开源阅读导入的位置暂未能换算（本书目录不可用或章节越界），已按近似位置打开；开始阅读后将记录本书进度
```

同一帧还显示：`测试 TXT`、`TXT · 全文 · 99%`、页脚 `全文 / 99.4%`。
约 2 s 后该提示淡出（`ui/07b-reopen-dump2.txt` 已无此文本）。

### 2c 不移动直接退出（`db/02-no-move-exit.txt`）

| 字段 | 退出前 | 退出后 | 变? |
|------|--------|--------|-----|
| `completion_state` | reading | reading | — |
| `progress_percent` | 99.4000015258789 | 99.4000015258789 | — |
| `updated_at` | 2026-09-28T14:26:10.157Z | 2026-09-28T14:26:10.157Z | **未变** |
| `completed_at` | 空 | 空 | — |
| `revision` | 1 | 1 | — |
| `current_location_json` | `{"legado_position_v1":{"ci":199,"co":500}}` | 同 | **未变** |

书架副标题仍为 `测试 · 第200章 · 已读 99%`（`ui/06-after-back.*`）→ 未触发保存。

### 2d 重开 → 提示再次可见

`ui/07a-reopen-dump1.xml`：`阅读正文已就绪` + 上述提示文本同帧 → marker 保留使提示重新武装。

### 2e 翻页（`ui/08a-*` → `ui/08b-*`）

```
翻页前首行：性测试正文，不含真实书名与真…
翻页后首行：实内容，仅用于验证阅读进度边…
```

### 2f 移动后退出（`db/03-after-move-exit.txt`）

```
completion_state      = reading
progress_percent      = 99.4003753662109          ← 由实际位置重算
updated_at            = 2026-09-28T14:30:03.289Z  ← 本次真的写了
completed_at          =                            ← 仍为空，未完结
revision              = 1
current_location_json = {"offset":1681586,"locator_v2":{"v":2,"offset":1681586,"ci":0,"co":1681586}}
```

书架副标题由 `第200章` 变为 `第1章`（`ui/09-after-move-exit-shelf.*`）→ marker 已转原生 locator。

> **可复现性：** `progress_percent=99.4003753662109` 与 `offset=1681586` 与上一轮报告
> 记录**逐位相同**，说明该转换在相同受控输入下是确定性的。

### 2g 再重开 → 加载完成后无提示（`ui/10-reopen-dump{1..5}.*`）

任务明确要求：加载画面「没看到提示」不能当作无提示证明。本轮按**多点采样**取证：

| 采样 | 距点击 | `阅读正文已就绪` | 提示出现 |
|------|--------|------------------|----------|
| dump1 | ≈1.5 s | 1 | **0** |
| dump2 | ≈4 s | 1 | **0** |
| dump3 | ≈8 s | 1 | **0** |
| dump4 | ≈13 s | 1 | **0** |
| dump5 | ≈18 s | 1 | **0** |

每次采样都确认正文已加载，因此既排除「尚未出现」（首次采样已在加载完成后、
提示通常出现的窗口内），也排除「已淡出」（提示约 2 s 淡出，dump1/dump2 会截到）。
稳定帧 `ui/10-reopen-no-prompt.xml`：正文从「实内容…」开始、页脚 `99.4%`、**无任何提示文本**。

退出后 DB（`db/04-reopen-no-prompt.txt`）：

```
completion_state      = reading
progress_percent      = 99.4003753662109
updated_at            = 2026-09-28T14:30:53.356Z
completed_at          =
revision              = 1
current_location_json = {"offset":1681586,"locator_v2":{"v":2,"offset":1681586,"ci":0,"co":1681586}}
```

> **观察登记（非缺陷）：** 此步未移动却仍写了行 —— `updated_at` 由 `…14:30:03.289Z`
> 变为 `…14:30:53.356Z`，而 `offset` / `percent` / marker **完全不变**。
> 这与 2c 阶段「marker 未转换 → 不保存」属不同分支：一旦转成原生 locator，
> 打开阅读器即会重写该行（内容幂等）。上一轮报告未覆盖此步，故在此如实登记。

---

## 3. 前后逐文件哈希清单与设置哈希

| 项 | 基线（`state/pre-qahost-state.txt`） | 链路后（`state/post-chain-…`） | 清理后（`state/post-cleanup-…`） |
|----|--------------------------------------|-------------------------------|----------------------------------|
| `books` / `reading_progress` / `book_content` / `inspirations` | 33 / 16 / 21 / 0 | 34 / 17 / 21 / 0 | **33 / 16 / 21 / 0** ✓ |
| `files/books` 文件数 | 36 | 37 | **36** ✓ |
| `files/books` 逐文件哈希 | 36 项 | 仅**新增 1 项**（隔离书 `eb85f521…`），36 项既有文件**全部不变** | **36 项与基线逐字节一致** ✓ |
| `shared_prefs` 逐文件哈希 | 7 项 | 7 项**全部不变** | **7 项一致** ✓ |
| `datastore` 逐文件哈希 | 6 项 | 6 项**全部不变** | **6 项一致** ✓ |
| 主 DB 文件大小 | 1 138 335 744 | — | **1 138 335 744**（与基线同） |
| 主 DB SHA-256 | `d938c7b8…c71214b` | — | `7bd076c6…8499a28`（**不同**，见 §5.1） |
| WAL / SHM | WAL 0 B（空）/ SHM 32768 B | 不存在（连接干净关闭） | checkpoint 后不存在；冷启动复核后回到 **WAL 0 B（空）/ SHM 32768 B** —— 与基线形态一致 |

对账脚本输出：`state/pre-vs-cleanup-diff.txt`（三项 PASS）。

> WAL 为空（SHA-256 `e3b0c442…`，即 0 字节）说明没有未落盘的写入；
> SHM 32768 B 是 WAL 模式下的共享内存索引，大小与基线相同、内容哈希随进程变化属正常。
> 冷启动复核后的状态：
> `-rw-rw---- … 1138335744 creation_reading_assistant_native`
> `-rw-rw---- …      32768 creation_reading_assistant_native-shm`
> `-rw-rw---- …          0 creation_reading_assistant_native-wal`

---

## 4. 清理与设备状态恢复

### 4a 隔离数据清理（`db/05-cleanup.txt`）

```
删除前：books=1 progress=1 content=0
DELETE FROM reading_progress / book_content / books WHERE …000012
删除后：books=0 progress=0 content=0
PRAGMA wal_checkpoint(TRUNCATE) → 0|0|0
全库行数回到 33 / 16 / 21
files/books/…000012/ 已删除 → 文件数 36
外部 files/s12-seed/ 与 files/s14-residue/ 已删除（目录现为空）
```

清理后冷启动复核（`ui/11-post-cleanup-shelf.*`）：`全部 33 | 在读 3 | 未开始 21 | 已完成 9`，
「测试 TXT」出现 **0** 次 → UI 与清理后的库一致。

### 4b 设备参数

| 项 | 原值 | 验收中 | 恢复后 |
|----|------|--------|--------|
| `wm density` | 280（无 override） | override 420 | **280**（`wm density reset`）✓ |
| `stay_on_while_plugged_in` | 0 | 7 | **0** ✓ |
| `ui_night_mode` | 2 | 2（未改） | **2** ✓ |
| `user_rotation` / `accelerometer_rotation` | 0 / 1 | 未改 | **0 / 1** ✓ |

设备侧临时安全网备份（`/data/local/tmp/qahost-safety-backup`，204 MB）已删除。

---

## 5. 如实登记

### 5.1 主 DB 不能逐字节还原 —— 只报行级 + 文件级

- 主 DB **文件大小**回到基线 1 138 335 744 B，但 **SHA-256 变了**
  （`d938c7b8…` → `7bd076c6…`）。原因是 SQLite 删除行后页被标记可复用、
  文件内部布局改变，即使逻辑内容一致，字节也不可能还原。
- 因此本报告**只声称**：行数还原（33/16/21/0）、`files/books` 36 个既有文件
  逐字节未变、`shared_prefs` 7 项与 `datastore` 6 项逐字节未变。
  **不写「完全恢复」**。
- 本轮**未**逐行核对既有 33 本书的每一列；只核对了计数与文件哈希。
  若需逐行比对，应另开一轮在导入前 dump 全表。

### 5.2 未覆盖的范围

- 本轮只走「单章 TXT + 越界 Legado marker」这一条分支；
  EPUB 书、多章目录可用的换算分支未重采。
- 2g 的「无提示」结论只覆盖本次构建与本次数据；提示的重新武装条件
  （marker 保留）已由 2c→2d 反证，但未做跨版本回归。
- 上一轮的原始终端记录确认**未留存**（终端为空、E 盘原目录仅 23 个文件），
  故本报告是**重采**而非对上一轮的复算；两轮之间除构建哈希不同外，
  受控输入（正文 SHA、ZIP 结构、隔离书 ID）保持一致，可作为交叉印证。

---

## 6. 证据清单

```
s12/
  db/01-after-import.txt        导入后 sqlite3 原始输出 + 行数 + WAL/SHM
  db/02-no-move-exit.txt        不移动退出 前/后 逐字段
  db/03-after-move-exit.txt     移动退出后（locator_v2）
  db/04-reopen-no-prompt.txt    重开无提示后退出
  db/05-cleanup.txt             清理全过程原始输出
  logs/01-instrument-seed.log   seed 测试 stdout（OK 1 test）
  logs/02-logcat-seed.log       生产导入日志原文
  seed/S12FinishedImportDeviceSeedTest.kt   一次性 seed 源码副本（仓库内已删除）
  seed/SEED-MANIFEST.txt        源码/输入/APK 哈希
  seed/s12-controlled-import.zip            受控 Legado ZIP
  seed/test_s12_isolated.txt                隔离正文（5 000 002 B）
  state/pre-qahost-state.txt                基线
  state/post-chain-qahost-state.txt         链路后
  state/post-cleanup-qahost-state.txt       清理后
  state/pre-vs-cleanup-diff.txt             对账（3 项 PASS）
  ui/01…11-*.png / *.xml / *.txt            截图、UI XML、可读文本
  ui/05a-reader-open-t{1,2,3,5,8}s.png      打开过程连拍（区分加载画面与已加载）
  ui/dump_text.py                           XML→文本提取脚本
```

## 7. 仓库改动

- 新增后删除：`android/app/src/androidTest/.../feature/sync/S12FinishedImportDeviceSeedTest.kt`
- `git status` 无被跟踪文件删除；未 commit / push / reset / checkout。
