# S04 qahost 有限真机验收报告（2026-09-25）

- 日期：2026-09-25（执行窗口 16:15–16:28，单窗口串行完成）
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 性质：**有限真机验收**。仅运行 runbook §4.1 指定的两个 instrumentation 类；测试前/后各按用户本次授权短暂 `force-stop` 正式包一次以捕获 E 盘归档；未重装、未卸载、未清数据、未改系统设置、未操作正式书库、未取消/改正式 WorkManager 队列、未手动启动沙箱图标、未演练恢复。
- 授权范围：用户明确授权本轮有限验收——可在测试前/后短暂停止正式应用以在 E 盘分别捕获新的前/后归档；仅运行下列两类 qahost 沙箱 instrumentation；知悉并接受备份恢复流程尚未在真机演练的残余风险。**授权不包括**恢复演练、卸载、重装、清数据、修改原书、全量 androidTest 或修改系统安全设置。
- 判据来源（只读）：`docs/qa/2026-09/2026-09-25-s04-qahost-device-runbook.md`（第三次修订）、`docs/qa/2026-09/2026-09-25-s04-qahost-installed-readonly-preflight.md`、`docs/qa/2026-09/2026-09-25-s04-e-backup-offline-baseline.md`、`android/scripts/s04_private_archive_manifest.py`（manifest/compare）。
- 本窗口唯一仓库写入 = 本文档。私有归档、基线、原始 instrumentation 日志只写在本轮 E 盘唯一私有子目录（位于 `s04-qahost-2026-09-25` 备份根下，目录名本轮唯一）；既有 E 盘文件（13:07 旧归档、旧 `baseline-manifest.json`、`README.md`、`verification-copy/`）均只读未改、未覆盖。
- 未 commit / push / stage / reset / checkout / clean；未触碰原始仓库、MuMu、横屏、正式书籍内容、正式 WorkManager 队列或其他 agent 的文件。

---

## 0. 三类结论分开（结论先行）

| # | 结论域 | 判定 | 一句话证据 |
|---|---|---|---|
| 1 | **两类 instrumentation 测试结果** | **PASS** | `EpubParserInstrumentedTest` OK (4 tests)、退出码 0；`EpubReplaceRoomIsolatedTest` OK (6 tests)、退出码 0；0 失败 / 0 崩溃 |
| 2 | **正式包 APK 一致性（测试前 → 测试后）** | **PASS** | `pm path`、versionCode 3 / versionName 0.5.1、base.apk SHA-256 `53d0c01b…85359ac`（53,532,616 字节）三端点逐项一致，并与预检登记一致 |
| 3 | **正式私有数据在两个捕获端点是否一致** | **PASS** | 测试前/后归档 SHA-256 均为 `36D1D6DC…F15726`（2,294,109,184 字节，逐字节相同）；`compare` `RESULT: PASS`、`TOTAL_DIFFERENCES: 0`、退出码 0 |
| — | **恢复能力** | **UNVERIFIED** | 归档与基线一致只证明「数据没动」，不证明「能还原」；恢复流程从未实机演练，本轮未授权演练 |

**最终判定：PASS**（限于上述三类已验证结论）；**恢复能力 UNVERIFIED**。三类结论互不替代：安装成功 ≠ 测试通过 ≠ 前后归档一致 ≠ 备份可恢复。

---

## 1. Step 1：上机只读复测（全部 PASS）

设备 serial 现场确认为 `c49***6cf`（脱敏；与预检一致），全程逐条 `adb -s <serial>`。

| # | 检查 | 实测 | 判定 |
|---|---|---|---|
| 1 | `adb devices -l` | 唯一设备 `c49***6cf`，状态 `device`，product `diting` / model `22081212C`，无第二台/无 offline/无模拟器 | ✅ |
| 2 | 方向 | `mDisplayRotation=ROTATION_0`、bounds `0,0-1220,2712`、`port` → 竖屏 | ✅ |
| 3 | qahost 两包 | `pm list packages` 命中 `com.creationreadingassistant.qahost` 与 `…qahost.test`（另有两条 runbook 已登记的遗留包，未触碰） | ✅ |
| 4 | instrumentation | `com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner (target=com.creationreadingassistant.qahost)` | ✅ |
| 5 | UID 隔离 | 正式 `uid=10949` / host `uid=10957` / test `uid=10958`，三者互不相同 | ✅ |
| 6 | 正式包路径 | `/data/app/~~…==/com.creationreadingassistant-…==/base.apk`（单 base，无 split） | ✅ |
| 7 | 正式包版本 | versionCode 3 / versionName 0.5.1 | ✅ |
| 8 | 正式包 base.apk SHA-256 | `53d0c01b0d36dc19690dc1f62834f19fc61634a2dd394de2b23efc9da85359ac`（53,532,616 字节）= 预检登记值 | ✅ |
| 9 | `run-as` 正式包可用 | `uid=10949(u0_a949)`，退出码 0 | ✅ |
| 10 | 设备端 `sqlite3` | 不存在（退出码 1），仅登记 | ✅ |
| 11 | 本地 host APK SHA-256 | `064F5334…BC7DC054` = runbook 锚点 | ✅ |
| 12 | 本地 test APK SHA-256 | `FA48A6BC…F9A21B54` = runbook 锚点 | ✅ |
| 13 | E 盘剩余空间 | 约 340.9 GB 可用（足够容纳前/后各约 2.29 GB 归档） | ✅ |
| 14 | 旧 13:07 归档哈希 | `9DFF292C…8D29C`（2,294,109,184 字节）= 基线报告 §2，只读未改 | ✅ |
| 15 | 旧 `baseline-manifest.json` 哈希 | `FE42381E…D352` = runbook §0.1 信任锚，只读未改 | ✅ |

未重装、未打开沙箱图标。Step 1 全绿后进入 Step 2。

---

## 2. Step 2：测试前归档与私有基线

> 注意：13:07 旧归档之后正式应用已经运行过，旧归档是旧恢复点，**不**作为本轮测试前对账基线。本轮在本轮 E 盘唯一私有子目录新建测试前归档与基线。

**force-stop（用户本次授权）**：`adb -s <serial> shell am force-stop com.creationreadingassistant` → 退出码 0；`pidof com.creationreadingassistant` → 空（退出码 1），进程已退出。

**捕获命令**（二进制流式 tar，`cmd /c` 包裹避免 PowerShell 文本管道改写字节；四根同序 `databases files shared_prefs no_backup`，与 runbook §5.1 / W3 一致）：

```
cmd /c "adb -s <serial> exec-out run-as com.creationreadingassistant tar -cf - -C /data/data/com.creationreadingassistant databases files shared_prefs no_backup > <本轮E盘私有子目录>\pre-test-formal-app-data.tar"
```

| 项 | 值 |
|---|---|
| 起止 | 2026-09-25 16:15:48 → 16:17:57 |
| 退出码 | 0 |
| 大小 | 2,294,109,184 字节 |
| SHA-256 | `36D1D6DCD73AD1B825AA936D9158CE6D3824AEAC0E0824841C72E0DF38F15726` |
| 捕获后 `pidof` 正式包 | 空（退出码 1），未重启 |

**生成私有基线**（manifest，传入归档预期哈希/大小，核对 workdb 位于 `no_backup/`；输出到本轮私有子目录，不覆盖既有文件）：

```
python android/scripts/s04_private_archive_manifest.py manifest \
  --archive <本轮私有子目录>\pre-test-formal-app-data.tar \
  --output  <本轮私有子目录>\pre-test-baseline-manifest.json \
  --expect-sha256 36D1D6DC…F15726 --expect-size 2294109184 --expect-workdb-in-no-backup
```

实际输出：`RESULT: OK`、退出码 0；`TAR_MEMBERS: 84`（目录 30 / 普通文件 54）；各根文件数 databases=5 / files=39 / shared_prefs=7 / no_backup=3；`WORKDB_CHECK: ok (main=1 wal=1 shm=1)`（workdb 三件套位于 `no_backup/`）；基线清单自身 SHA-256 `A5FF57E1360DC5C03751A95862C65C592FC370207F0B45328738A4EDEE48E5AA`。

---

## 3. Step 3：两类 instrumentation 测试（均 PASS）

测试前再次核实正式应用未重启（`pidof` 空、退出码 1）且仍竖屏。仅运行下列两个类，使用已安装的 `com.creationreadingassistant.qahost.test` / `CraIsolatedTestRunner`；**未**运行 `connectedQahostAndroidTest`、`connectedDebugAndroidTest` 或任何其他类。每类完整 stdout/stderr 落本轮 E 盘私有日志，仓库只引中性摘要（"测试 EPUB"）。

### 3.1 类 1：`EpubParserInstrumentedTest`

```
adb -s <serial> shell am instrument -w -e class com.creationreadingassistant.feature.reader.EpubParserInstrumentedTest com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
```

| 项 | 值 |
|---|---|
| 起止 | 2026-09-25 16:24:59 → 16:25:03 |
| 退出码 | 0 |
| 汇总 | `OK (4 tests)`，Time 0.244s |
| 通过 / 失败 / 崩溃 | 4 / 0 / 0 |

### 3.2 类 2：`EpubReplaceRoomIsolatedTest`

两类之间核实正式包 `pidof` 仍空（退出码 1），未重启。

```
adb -s <serial> shell am instrument -w -e class com.creationreadingassistant.feature.reader.EpubReplaceRoomIsolatedTest com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
```

| 项 | 值 |
|---|---|
| 起止 | 2026-09-25 16:25:20 → 16:25:22 |
| 退出码 | 0 |
| 汇总 | `OK (6 tests)`，Time 0.916s |
| 通过 / 失败 / 崩溃 | 6 / 0 / 0 |

窗口内未观察到正式应用重启、设备掉线、方向变化、测试进程挂死或系统级异常；未触发 runbook §7 任何停机条件（S10/S11 等均未触发）。未访问或导入用户真实书库；报告只称"测试 EPUB"。

---

## 4. Step 4：测试后归档捕获

测试结束后按用户本次授权再次 `force-stop` 正式包：`am force-stop com.creationreadingassistant` → 退出码 0；`pidof` → 空（退出码 1）。以与测试前**相同四根、同序、二进制流式**方法捕获：

```
cmd /c "adb -s <serial> exec-out run-as com.creationreadingassistant tar -cf - -C /data/data/com.creationreadingassistant databases files shared_prefs no_backup > <本轮E盘私有子目录>\post-test-formal-app-data.tar"
```

| 项 | 值 |
|---|---|
| 起止 | 2026-09-25 16:25:48 → 16:28:20 |
| 退出码 | 0 |
| 大小 | 2,294,109,184 字节 |
| SHA-256 | `36D1D6DCD73AD1B825AA936D9158CE6D3824AEAC0E0824841C72E0DF38F15726` |
| 捕获后 `pidof` 正式包 | 空（退出码 1） |

测试后归档与测试前归档 SHA-256 **逐字节相同**。

---

## 5. Step 5：严格内容级对账（PASS）

对测试前 `pre-test-baseline-manifest.json` 现场重算 SHA-256 = `A5FF57E1…E48E5AA`，作为 compare 必填信任锚：

```
python android/scripts/s04_private_archive_manifest.py compare \
  --archive  <本轮私有子目录>\post-test-formal-app-data.tar \
  --baseline <本轮私有子目录>\pre-test-baseline-manifest.json \
  --expect-baseline-sha256 A5FF57E1…E48E5AA \
  --evidence-json <本轮私有子目录>\diff-evidence.json
```

实际输出（退出码 0）：

- `RESULT: PASS`
- `TOTAL_DIFFERENCES: 0`；FILE_ADDED/REMOVED/CONTENT_CHANGED_SAME_SIZE/CONTENT_CHANGED_SIZE_CHANGED/ROOT_MISSING 全 0；AREA_DATABASES/FILES/SHARED_PREFS/NO_BACKUP/WAL_SHM 全 0
- `NEW_ARCHIVE_SHA256: 36d1d6dc…f15726` = 基线归档哈希
- 差异证据 JSON 未生成（PASS 无差异，未写 `diff-evidence.json`）

对账范围：四根内容级（相对路径 + 字节数 + SHA-256；tar 元数据刻意忽略），覆盖正式 Room 库（`databases/`）、workdb 三件套（`no_backup/`）、书库文件（`files/`）与偏好项（`shared_prefs/`）。工具不做 SQLite 解读，不存在"行数未变即放行"通道。

### 5.1 字节一致能证明什么、不能证明什么

**能证明**：在两个捕获端点时刻，归档覆盖的正式包四个私有根所含文件集合与每个文件内容（相对路径 + 字节数 + SHA-256）取值相同——即沙箱包安装与两个测试类的运行没有在这两个捕获端点留下可观测的数据改变。该结论仅限两个捕获端点的覆盖文件一致。

**不能证明**：① 恢复能力（仍 UNVERIFIED）；② 覆盖范围之外无变化（Keystore、系统设置、/sdcard、包管理器状态、JobScheduler 注册、其他应用数据均不在归档内）；③ 捕获瞬间原子一致（归档是 force-stop 后文件级冷拷，强佐证级非形式证明）；④ 窗口内瞬时变化（端点之间发生又复原的变化不可见，"端点一致"不得表述为"窗口期间从未发生任何瞬时变化"）；⑤ 权限/属主变化（tar 元数据被忽略）。

---

## 6. Step 6：正式主 APK 测试后复测（PASS）

| 项 | 测试前（Step 1） | 测试后（Step 6） | 判定 |
|---|---|---|---|
| `pm path com.creationreadingassistant` | `/data/app/~~…==/com.creationreadingassistant-…==/base.apk` | 同左（逐字符一致） | ✅ |
| versionCode / versionName | 3 / 0.5.1 | 3 / 0.5.1 | ✅ |
| base.apk SHA-256 | `53d0c01b…85359ac` | `53d0c01b…85359ac` | ✅ |
| base.apk 字节数 | 53,532,616 | 53,532,616 | ✅ |

测试后 `pidof com.creationreadingassistant` 仍空（退出码 1，未自动重启正式应用）；仍竖屏。**未**卸载沙箱包、**未**删除任何归档、**未**自动重启正式应用。本轮未临时改变 `stay_on_while_plugged_in`，无原值需恢复。

---

## 7. 未覆盖范围（诚实登记）

1. **恢复能力 UNVERIFIED**：未演练恢复，归档/基线一致不推及"可还原"；如需强佐证须另行单独授权一次实机恢复演练。
2. 仅运行 runbook §4.1 两个类；其余 32 个 androidTest 类未获放行，未运行 `connectedQahostAndroidTest` / `connectedDebugAndroidTest`。
3. 对账只比较两个 force-stop 后端点；窗口内瞬时变化不可见（§5.1-④）。
4. 覆盖范围限于四个私有根；Keystore、系统设置、/sdcard 共享存储、包管理器状态、JobScheduler 注册、其他应用数据不在归档内。
5. tar 元数据（mtime/mode/uid/gid）被刻意忽略，权限/属主差异不可见。
6. 工具做归档结构与字节级核验，不做 SQLite `integrity_check` 或业务语义解读。
7. 沙箱包"当前未运行"仅证明检查瞬间无进程，不证明"从未运行过"（沿用预检报告 §4 口径）。
8. 遗留包 `com.creationreadingassistant.test` 与 `local.creationReadingAssistant.mobile` 仍存在（runbook §6 非阻塞登记项），本轮未触碰。

---

## 8. 合规自证

| 动作 | 是否执行 |
|---|---|
| 只读 adb（devices/getprop/pm/dumpsys/sha256sum/stat/pidof/run-as id） | ✅ |
| `am force-stop com.creationreadingassistant`（测试前/后各一次，用户本次授权） | ✅ 共 2 次 |
| `am instrument` 仅两个指定类 | ✅ 共 2 次 |
| 二进制流式 tar 捕获前/后归档（写入本轮 E 盘私有子目录） | ✅ 共 2 次 |
| manifest 生成私有基线（本轮私有子目录，不覆盖既有文件） | ✅ |
| compare 严格对账 | ✅ |
| 重装 / 卸载 / 清数据正式包或沙箱包 | ❌ |
| `connectedQahostAndroidTest` / `connectedDebugAndroidTest` / 其他测试类 | ❌ |
| 手动启动沙箱应用图标 / 启动正式应用 / 操作正式书库 / 改正式 WorkManager 队列 | ❌ |
| 修改系统设置（含旋转、stay_on_while_plugged_in） | ❌ |
| 恢复演练 | ❌ |
| 读取数据库行、书籍内容、私人文件名、逐文件清单 | ❌ |
| 覆盖既有 E 盘文件 / 触碰原始仓库 / 他人窗口文件 | ❌ |
| commit / push / stage / reset / checkout / clean | ❌ |

本窗口唯一仓库写入 = 本文档。私有归档、基线、原始 instrumentation 日志只存在于本轮 E 盘唯一私有子目录；仓库报告只含方法、命令（serial 脱敏 `c49***6cf`）、聚合计数、哈希与退出码，不含真实书名、逐文件清单、数据库行、偏好值或原始 logcat。

---

## 9. 最终判定

- 两类 instrumentation 测试：**PASS**（4/4 + 6/6，退出码 0×2，0 失败 0 崩溃）。
- 正式包 APK 测试前/后一致：**PASS**（路径/版本/base.apk SHA-256 三端点逐项一致）。
- 正式私有数据两捕获端点一致：**PASS**（`compare` RESULT: PASS、0 差异、退出码 0；前/后归档 SHA-256 逐字节相同）。
- 恢复能力：**UNVERIFIED**（未演练）。
- 综合：**PASS**（限于上述三类已验证结论）；未触发任何停机条件；现场已保留，未自行重试/修复/回滚/清理。
