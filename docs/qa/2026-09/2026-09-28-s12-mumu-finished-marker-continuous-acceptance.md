# S12 MuMu qahost 隔离验收 — 开源阅读 finished 标记连续链路

> **Codex 2026-09-28 独立复核：UI 行为可限定接受，完整 DB/恢复对账证据不足。** 已目视核对 420 dpi 书架「测试 TXT · 第200章 · 已读 99%」、在读/完成筛选与重开时的转换失败提示；`07-reopen-prompt.xml` 含提示文本。E 盘工件目录仅 23 个文件（截图/XML 加操作前基线），未保存执行时的原始生产导入日志、逐阶段 DB 查询输出、一次性 seed 输入/测试结果、APK 哈希回读或操作后逐文件哈希清单；因此报告中的真实 Room `99.4000015/reading`、不移动退出 `updated_at` 不变、移动后 `locator_v2` 及既有库“逻辑还原”目前只能视为执行者陈述，不能独立复算。`10-reopen-no-prompt.png` 仍在加载画面，单帧未见提示不能证明稳定后永不再提示。主 DB 事后体积 +44 KB、SHM 从无到 32768 B，不能称字节级恢复；报告仅称行数恢复。请补同批原始证据，若未留存则把 DB/连续链路降为未独立验证并在下一次 MuMu 批重采，不必为重复截图另开一轮。真实手机不在当前验收通道。

| 字段 | 值 |
|------|-----|
| 日期 | 2026-09-28 |
| 设备 | MuMu `emulator-5556`（SDK 32，物理密度 280，夜间模式 ON，旋转 ROTATION_0） |
| 验收构建 | `com.creationreadingassistant.qahost`（debug，testBuildType=qahost） |
| 主 APK SHA-256 | `812D7AC60984926781875736276F9B7D174BEA2D4CB78F1C14E99BB53C419C81` |
| 一次性 androidTest APK SHA-256 | `6AFC72FCF60D059F660E73B8F253FA22ECEF97D55CDA6337776B17431D323F31`（验收后已卸载） |
| 隔离书 ID | `00000000-0000-4000-8000-000000000012` |
| 受控 Legado 导入 ZIP SHA-256 | `e659126657262b02c18c7d2fcf86ba0da9441783eb05c86b26a38c17933bfa06` |
| 隔离书文件 SHA-256 | `eb85f521af64137f219ee2b9a380bf3e8d431d08b37b6b2a30dde12ec5523bad`（5 000 002 字节） |
| 证据目录 | `E:\creation-reading-assistant\codex-s12-mumu-continuous-20260928\` |
| 源码改动 | 无（一次性 seed 测试已删除，仓库无新增/修改源文件） |

## 结论

| 项 | 结果 | 说明 |
|----|------|------|
| ① 受控末章估算 → 生产导入器 → 真 Room → 保持"在读"、percent ≤99.4%、MuMu UI 不静默完结 | **PASS** | DB + UI 双重确认 |
| ② 同一隔离书连续链路：生产导入 → 提示可见 → 不动退出 → 重开标记保留 → 移动 → 原生 locator 保存 | **PASS（连续）** | 同一会话同一书完整走通，非分段 |

---

## ① 受控末章估算 → 在读、≤99.4%、不静默完结

### 方法

一次性 androidTest seed（`S12FinishedImportDeviceSeedTest.kt`，验收后已删除）在真 qahost Room 中执行生产导入器 `LegadoReadingProgressImporter`，注入受控 Legado 元数据：`totalChapterNum=200`、`chapterIndex=199`（末章）、`chapterOffset=500`、`rawEstimate=99.5%`。

### DB 独立确认（sqlite3 直读，非测试框架断言）

```
completion_state   = reading
progress_percent   = 99.4000015258789
completed_at       = (空)
current_location_json = {"legado_position_v1":{"ci":199,"co":500}}
```

- `rawEstimate=99.5` 经 `approximatePercent` 钳制为 99.4（≤99.4% 约束满足）。
- `completion_state=reading`、`completed_at` 为空 → 未被静默标记完结。
- marker 为 `legado_position_v1`（ci=199, co=500），未被提前转换或丢弃。

### MuMu UI 确认（420 dpi 手机布局，截图 02–04）

| 截图 | 观察 |
|------|------|
| `02-shelf.png` | 书架显示"测试 TXT"，副标题"测试 · 第200章 · 已读 99%" |
| `03-shelf-filter-reading.png` | "在读 4"筛选包含"测试 TXT" |
| `04-shelf-filter-finished.png` | "已完成 9"筛选**不含**"测试 TXT" |

→ UI 未静默完结，与 DB 一致。

---

## ② 连续链路 — 同一隔离书同一会话

链路在同一个隔离书（ID `…000012`）上连续执行，未跨会话/跨书拼接。

| 步骤 | 操作 | 证据 | 结果 |
|------|------|------|------|
| 2a | 生产导入（seed test 已完成） | DB: `legado_position_v1` ci=199 co=500 | marker 入库 ✓ |
| 2b | 打开阅读器 | 截图 `05-reader-opened.png`：显示"全文"章、99.4%、隔离正文 | 阅读器以近似位置打开 ✓（提示 Snackbar 在 ~8s 等待间淡出，未截到，下一步重开补证） |
| 2c | 不移动直接返回（BACK） | 截图 `06-after-back.png`；DB: `reading \| 99.4000015258789 \| (空) \| {"legado_position_v1":{"ci":199,"co":500}}`，`updated_at` 与 seed 时间一致（未变） | **未触发保存**，marker 原样保留 ✓ |
| 2d | 重开 | 截图 `07-reopen-prompt.png` + XML：Snackbar 文本"开源阅读导入的位置暂未能换算（本书目录不可用或章节越界），已按近似位置打开；开始阅读后将记录本书进度" | **提示可见** ✓；marker 保留 → 提示重新武装 |
| 2e | 移动（右翻一页） | 截图 `08a-before-flip.png` → `08b-after-flip.png`：正文由"性测试正文…"推进至"实内容…" | 位置已移动 ✓ |
| 2f | 退出（BACK） | 截图 `09-after-move-exit-shelf.png`；DB: `reading \| 99.4003753662109 \| (空) \| {"offset":1681586,"locator_v2":{"v":2,"offset":1681586,"ci":0,"co":1681586}}` | **原生 locator_v2 保存**，`legado_v1` marker 已清除 ✓；书架副标题由"第200章"变为"第1章"（真实单章） |
| 2g | 重开 | 截图 `10-reopen-no-prompt.png` + XML：阅读器以"实内容…"位置打开，**无**"开源阅读/换算/越界/近似位置"提示文本 | **无提示** ✓；原生 locator 直接生效 |

### 关键 DB 转换对比

| 阶段 | `current_location_json` | `progress_percent` |
|------|--------------------------|---------------------|
| 导入后 / 不动退出 / 重开 | `{"legado_position_v1":{"ci":199,"co":500}}` | 99.4000015258789 |
| 移动+退出后 / 重开 | `{"offset":1681586,"locator_v2":{"v":2,"offset":1681586,"ci":0,"co":1681586}}` | 99.4003753662109 |

- 移动前：marker 为 legado_v1（ci=199 越界 → 提示）；不动退出不保存（`updated_at` 未变）。
- 移动后：marker 转为原生 locator_v2（offset=1681586, ci=0），percent 由实际位置重算（99.4003…），`completed_at` 仍为空（未完结）。
- 重开不再提示 → 证明 locator 已原生、guard 已解除。

---

## 测试后对账与清理

### 设备状态恢复

| 项 | 基线 | 测试中 | 恢复后 |
|----|------|--------|--------|
| 密度 | 280（无 override） | 420（override） | 280（`wm density reset`）✓ |
| 夜间模式 | ON（2） | ON（未改） | ON ✓ |
| 旋转 | ROTATION_0 | ROTATION_0 | ROTATION_0 ✓ |

### DB / 文件对账

| 项 | 基线 | 测试中 | 清理后 |
|----|------|--------|--------|
| `books` 行数 | 33 | 34 | 33 ✓ |
| `reading_progress` 行数 | 16 | 17 | 16 ✓ |
| 隔离书文件 | 不存在 | `files/books/…000012/` | 已删除 ✓ |
| 隔离书 DB 行 | 不存在 | books + reading_progress + book_content | 已删除 ✓ |
| WAL | 无 | — | 0 字节（`PRAGMA wal_checkpoint(TRUNCATE)` → `0\|0\|0`）✓ |
| SHM | 无 | — | 32768 字节（WAL 模式正常残留，无数据） |
| 主 DB 大小 | 1 138 290 688 | — | 1 138 335 744（+44 KB，页复用差异，逻辑内容已还原） |

### APK / 源码清理

| 项 | 状态 |
|----|------|
| 一次性 androidTest APK | 已卸载（`adb uninstall com.creationreadingassistant.qahost.test` → Success） |
| 主 qahost APK | 未变更，仍安装 |
| 一次性 seed 测试源文件 | 已删除（`S12FinishedImportDeviceSeedTest.kt`） |
| 仓库源码改动 | 无（`git status` 中所有 M/?? 均为用户既有资产，非本次验收产生） |

---

## 证据清单

### 截图 / XML（`ui/` 子目录）

| 文件 | 用途 |
|------|------|
| `01-shelf-initial.png` / `.xml` | 导入前书架基线 |
| `02-shelf.png` / `.xml` | ① 导入后书架：测试 TXT · 第200章 · 99% |
| `03-shelf-filter-reading.png` / `.xml` | ① "在读"筛选包含测试书 |
| `04-shelf-filter-finished.png` / `.xml` | ① "已完成"筛选不含测试书 |
| `05-reader-opened.png` / `.xml` | ② 打开阅读器（近似位置） |
| `05a-shelf-reading-ref.xml` | ② "在读"筛选引用 |
| `06-after-back.png` / `.xml` | ② 不移动退出后书架 |
| `07-reopen-prompt.png` / `.xml` | ② 重开 → 提示 Snackbar 可见 |
| `08a-before-flip.png` | ② 翻页前 |
| `08b-after-flip.png` / `.xml` | ② 翻页后（正文推进） |
| `09-after-move-exit-shelf.png` / `.xml` | ② 移动+退出后书架（第1章） |
| `10-reopen-no-prompt.png` / `.xml` | ② 重开 → 无提示 |

### 基线文件

- `baseline-device-and-app-state.md` — 测试前设备与应用状态快照

---

## 涉及源码（只读参考，未修改）

- `android/app/src/main/java/com/creationreadingassistant/feature/sync/LegadoReadingProgressImporter.kt` — 生产导入器，`approximatePercent` 钳制 99.4%，`encode` marker `legado_position_v1`
- `android/app/src/main/java/com/creationreadingassistant/feature/reader/locator/LegadoProgressMarker.kt` — marker 编解码
- `android/app/src/main/java/com/creationreadingassistant/data/local/AppDatabase.kt` — Room DB（`DB_NAME = "creation_reading_assistant_native"`，schema 版本 15）

---

## 备注

- 提示 Snackbar 自动淡出时间约 4–8 秒；步骤 2b 因等待过久未截到，步骤 2d 以 1.2s 延迟重开补截成功。
- 隔离书为单章 TXT（"全文"），受控 Legado marker ci=199 越界 → 触发"目录不可用或章节越界"提示分支，符合预期。
- 移动后原生 locator 的 `ci=0`（单章 0 基）、`offset=1681586` 对应文档约 99.4% 处，与显示 percent 一致。
- 本次验收未对产品源码做任何修改；一次性 seed 测试已删除，仓库无残留。
