# S04 MuMu qahost UI 与定向数据对账

> 2026-09-27 Codex 更正：本报告中用户从书架长按进入的是「重新定位文件」→ `ShelfImporter.repairFile`，它验证了内部正文替换、书籍行与进度，**并未验证另一入口 `refreshFromSource` 会推进 `library_source_refs` 基线**。S14 后续快照中测试书 `books.size=1700`/哈希 V5，而 `library_source_refs.size=1101`/哈希 V1；这是两入口合同待核实项，不能把下述「来源刷新」措辞外推为来源引用同步通过。原始证据保留。

- 日期：2026-09-27。用户明确改用 MuMu 继续设备验证；本结论仅适用于模拟器，不等于实体手机/MIUI 验收。
- 设备：`adb -s 127.0.0.1:16416`，Android 12 / x86_64，1080×1920。测试时临时设为 440 dpi 手机布局，结束执行 `wm density reset`，实测恢复原始 280 dpi。
- 工作区：恢复仓库。宿主为 `com.creationreadingassistant.qahost`，正式包 `com.creationreadingassistant` 在该模拟器未安装（`pm path` exit 1、无输出）。沿用前轮隔离 APK 与 8/8 instrumentation 证据，见 [前轮报告](2026-09-27-s04-mumu-qahost-instrumentation.md)。没有操作实体手机、正式书库或原仓库。
- 样本与原始证据：`E:\creation-reading-assistant\codex-s04-mumu-20260927\`。所有样本为合成「测试 EPUB S04」，来源在模拟器 `/sdcard/Download/Test-EPUB-S04.epub`。仅测试书 `epub_-5hwnnt` 被本轮增改。书架总数由 28 增至 29，收尾仍为 29；原有 qahost 数据未清除。

## 场景 A：来源刷新与进度

1. 经应用 UI 导入 V1，打开可见 `V1MARKER`；同名来源覆盖成 V2 后经「重新选择来源」刷新，打开可见 `V2MARKER`。`v1-baseline-state.json` → `v2-after-reselect-state.json`：书 ID 和内部路径不变，`updated_at` 从 `08:59:41.774Z` 变为 `09:04:56.481Z`，`content_hash` 由 `734d17ff81e5151fe2096a47523a5ad7` 变为 `630fe29c8ed14695b97e40b9872adaa8`；内部 EPUB SHA-256 与 V2 来源一致。V1/V2 都是 1101 字节，因此这一对样本未证明大小值变化。
2. 为验证非零进度，改用较长的 V3 并在阅读器翻页。`v3-progress-before-refresh-state.json` 的进度为 2.97830605506897%、`offset=486`、`locator_v2.co=486`。同名来源覆盖成 V4，再经 UI 刷新；`v4-after-refresh-state.json` 与 V3 的整条 `reading_progress` 输出逐字相同，书 ID/内部路径不变，`updated_at` 变为 `09:29:11.162Z`，MD5 变为 `3257860c7a26cae0d041bcf15386ac56`，内部 EPUB SHA-256 `57c74c9be82872ee8b0b904e2a5fc08dae424e4c070ef3ca0282143b064a285e` 与 V4 来源一致。重进阅读器可见 `V4MARKER` 并从非零位置显示正文；UI 百分比受重排影响，不宣称仍显示精确 2.978%。V3/V4 都是 1637 字节，大小变化同样未实测。
3. V4 刷新后 `.backup`、`.repairing`、`.importing` 均不存在。截图：`v1-reader.png`、`v2-reader.png`、`v4-reader-progress.png`。后者 SHA-256 `1AB307DCF3AABC9EE8289F7F7C63377FD68FF01350668FC1F39567DDBEF20800`。
4. 补做不同长度的 V4→V5：V4 为 1637 字节，V5 为 **1700 字节**，经相同 UI 入口重新选择同名来源。V5 `books.size=1700`，MD5 `cafaef499165761c7574f2e3226ee02c` 与来源相同，内部 EPUB SHA-256 `639f193924b5d2921c83bd41d900e2d644705e9e6e96d1151bb7d8d8f1e6e44b` 与来源相同；原书 ID、内部路径不变，进度 `offset=486` 保留，残留文件不存在。重开阅读器可见 `V5MARKER` 及非零正文位置；`v5-reader.png` SHA-256 `5A1DE679C9A53CAB56F68AD6B71F46D91728BED43A7E3FBF3DF4D2268D326A8B`。

### V4→V5 强停一致快照

在 V5 替换前后各执行一次仅针对 qahost 的 `am force-stop`，随后通过 `adb exec-out run-as` 将主 DB、WAL、SHM 三件按原名复制到 E 盘各自目录；本机 SQLite 打开并回放 WAL。`snapshots/before-v5/manifest.json` 与 `snapshots/after-v5/manifest.json` 均记录三件的大小和 SHA-256、时间、测试书行；两次 `PRAGMA integrity_check` 都为 `ok`，书总数均 29。主 DB SHA-256 两次同为 `1411b2bcf062390d0da6bab0eefec238f6a51ddd6307bc4be00e3cbcf8ae59fd`；WAL SHA-256 从 `e32b31980517ed38960df3cbf9582e1fc710f9a5dce03364f238410606e64d75` 变为 `6f1a346977a75fff852dd7bc6b9991e9b42da5557c0d0c28333164f06d483a3f`，说明不能只看主 DB。快照中测试书 `size` 1637→1700、MD5 更新、进度行逐字一致。快照 SQL 另对比其余 **28 条 books** 和 **14 条 reading_progress**，两类行均逐字段相同（`snapshots/v5-compare.json`，比较程序 exit 0）。这不代表整个数据库所有表无写入。

## 场景 C：备份残留拒绝

仅对上述测试书创建空的 `epub_-5hwnnt.backup`，记录 `residue-before-click-state.json`；覆盖同名来源后，通过 UI 对该书重新选择来源。正确操作的视频是 `residue-refusal-correct.mp4`（SHA-256 `392BE59D63C0B04933D0D5F5077EB5E8D43C5D45A4B402004DDED3646D5BF958`）。截图 `residue-refusal-message.png`（SHA-256 `408A35F6922DB5A40EEDC76521058099E907018DC195420289C3A0596D38B7D0`）实见「检测到未完成的替换残留」「本次替换已停止」「重复重试不会自动恢复」。较早的 `residue-refusal.mp4` 误入其他选择器目录，不作为验收证据。

`residue-before-click-state.json` 与 `residue-after-video-state.json` 机械比较：测试书的 `books` 行、`reading_progress` 行、目标 EPUB SHA-256、备份 SHA-256 四项均完全相同；`updated_at` 因此也未改变。这证明所检查的目标行与文件未改，**不证明整个数据库零写入**。之后仅删除该测试书自身 `.backup`，V3/V4 对账确认不再存在。

## 场景 D：诊断页

「我的 → 日志与诊断」可打开，显示 1 条 INFO/App「应用启动」、WARN/ERROR 均 0，未见「EPUB 回滚恢复未完成」；当前可见记录不含书名、路径、正文或异常消息。点击「导出日志」出现 Android 分享面板，但未分享给任何应用。`diagnostics-log.png` SHA-256 `A0ACBD50B632B0014D3E3D4E6311D218DB2D7E082C01A7E813387D47ED3411DF`；`diagnostics-export.png` SHA-256 `E095077BF8C1891B306BAF8B4A643996CE1EACA9B6C6EE576B69F0277284CC5E`。这只验证当前可见日志与导出入口，不验证真实回滚失败日志的内容。

## 结论与边界

- **模拟器实测通过**：qahost 隔离 instrumentation 8/8；手机布局 UI；同名来源替换后新正文/新哈希、不同长度 `size` 更新、非零进度行保留；残留备份守卫及测试书目标行/文件不变；诊断页可读与导出入口；V4→V5 强停后一致 SQLite 三件快照、完整性检查与其余书/进度行不变。设备临时录像、截图已删除，E 盘证据保留；来源测试 EPUB 保留供当前测试书读取。
- **未覆盖**：C 守卫场景未复制整库三件套，仅对测试书目标行/文件定向在线对账；可选 >32 MiB 场景 B、观察性进程中断场景 E 未运行。正式包在本模拟器未安装，不能据此证明真实正式书库前后零变化；V4→V5 的其余数据库表亦未逐表对比。
- **实体手机边界**：无真实设备测试、无 MIUI 自动安装/ROM 行为结论；后续有设备时须单独执行。当前用户授权的 MuMu 工作可继续推进，不把实体手机不可用当作模拟器工作阻塞。
- **项目进度**：全局恢复计划 A–E 仅 A 完整退出，`1/5=20%`；S04 原五关仍 `4/5=80%`，第 5 关的模拟器必做 UI 路径及 V4→V5 一致快照已通过，但原验收单的实体手机/正式书库前后对账仍未完成，不将这两项写为 PASS。
