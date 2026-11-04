# N1 — 本地备份恢复过程真实进程中断验收报告

- 任务：补齐本地备份恢复（`LocalZipBackup.importZip`）过程中**真实进程中断**证据；发现稳定可复现缺陷时做最小修复。
- 基线：`e39a78da9c62f02614207cffb36969a32492eaea`（E 阶段 final-v2 闭包报告明确「真 process-kill 与整包原子性」未测，本报告补齐该项）。
- 隔离候选：`E:\creation-reading-assistant\next-round-n1\android`（`git archive` 导出 872 文件，非共享工作树源码）。
- 设备：MuMu 模拟器（用户本轮明确授权，无实体机），serial `127.0.0.1:16416`，全部命令显式 `-s`；测试数据全部为中性合成记录（测试TXT甲/乙、测试EPUB丙/丁）。
- 结论先行：**6 个真实 kill -9 中断场景全部 PASS，无稳定可复现缺陷；未做产品行为修复**，仅按既有接缝先例新增了一个测试同步点接缝与两个专用测试文件。恢复中断后的整包**不是原子的**（与代码注释声明的 S14 设计一致），由「再次重试同一 ZIP 收敛」兜底——本报告逐边界实证了该声明。

## 1. 实际恢复流程与中断边界（代码事实）

`importZip`（基线 e39a78da 的 `LocalZipBackup.kt:118`）顺序：

| # | 阶段 | 中断边界 |
|---|------|----------|
| 0 | `recoverCrashResidues`（按归属日志补完上次被杀现场） | K0 |
| 1 | 解压到 `cacheDir/zip-restore-<ts>` + 校验 `backup.json`（ZipSlip 防护） | K1 |
| 2 | `jsonBridge.importFromString`（Room 单事务，含 13 类实体合并） | K2（事务中） |
| 3 | `copyBookFiles`：逐文件 staging → 长度校验 → `renameTo` 原子替换；版本感知跳过本地较新文件 | K3 / K4 / K5 |
| 4 | 完成，finally 清理临时目录 | — |

DB 先提交、文件复制在后，**整包不是原子的**（`LocalZipBackup.kt` 类注释 §2 明示）；失败中间态由「再次导入同一 ZIP 收敛」兜底（DB 合并幂等、文件全量重写自愈半写入）。

## 2. 方法

### 2.1 测试同步点（所有权内最小接缝）

- **新增接缝**：`LocalZipBackup.restorePhaseHook: ((String) -> Unit)?`（instance 级，生产恒 `null`，每边界一次空判）。仿照既有 `fileCopyOperation` 接缝先例（`internal var` + KDoc「仅供测试注入」），调用点 5 处：after-crash-residue-recovery / after-unzip-before-db / after-db-commit-before-files / `before-file:<rel>` / after-files-complete。补丁：`acceptance/evidence/n1-restorePhaseHook.patch`（+11 行，无删改既有行）。
- **既有接缝**：`fileCopyOperation` 用于 K5（staging 写入 16 字节后阻塞 = 真实半写入）。
- **DB 事务中同步点**：不触碰 `JsonBridge`（非所有权）；改用测试自建 Room 实例的 `setQueryCallback`（直接执行器），在备份内 6000 条合成标签的第 1500 条语句处阻塞——该回调在事务线程内同步执行，**事务已开启、先前语句未提交**。
- 接缝只允许测试阻塞等待外部 kill，不注入故障；JVM 回归 `LocalZipBackupPhaseHookTest`（2 用例）锁定「生产默认 null + 阶段序列契约」。

### 2.2 kill 协议与 PID 证据

每个场景由 E 盘驱动 `driver_kill.py` 编排：reset → 造旧数据+备份包 → 清阶段日志 → 后台启动 hold 插桩 → 轮询阶段日志出现 `hold:` 标记 → 从日志取 `pid=` 并交叉核对 `pidof` → `kill -9 <pid>` → `/proc/<pid>` 变 GONE + `pidof` 不再返回旧 PID → 拉取 DB(db+wal+shm)/books/阶段日志/ZIP → dump → `am start` 重启应用 → 再 dump → 重试导入 → 逐项对账。全部命令与退出码在 `acceptance/logs/driver-commands.log`。

### 2.3 数据设计（对账轴）

旧数据：甲（rev2 本地较新，`local_content_path` 指向本地文件）、乙（rev1）、丙（本地独有 EPUB）；进度乙 10%、丙 20%。
备份包 `pk-backup-new.zip`（生产 `ZipOutputStream` 直构，`backup.json` 为真实 `LocalExport` schema）：甲 rev1（较旧→必须整本跳过）、乙 rev2（较新→接受）、丁 EPUB rev1（新增）、乙进度 42%、6000 合成标签（拉长事务）。
期望终态：甲行 rev2+甲本地字节（保护）、乙行 rev2+乙备份字节、丙原样、丁新增；无 journal 残留。

## 3. 场景结果（6/6 PASS）

| 场景（kill 边界） | 旧 PID | kill 后实证状态 | 重试收敛 |
|---|---|---|---|
| K0 crash-residue 恢复后（尚未解压） | 28972 | DB/文件全旧态，无任何变更，无孤儿临时目录 | ✅ |
| K1 解压+校验后、DB 前 | 29466 | DB/文件全旧态；**cache 留 1 个孤儿 `zip-restore-*` 目录**（finding F1） | ✅ |
| K2 **DB 事务中**（第 1500 条标签语句处） | 29921 | 阶段日志实证 300→1500 条语句在飞；kill 后 DB **整旧**（1500 条未提交标签全部回滚），`integrity_check=ok`，文件未动 | ✅ |
| K3 DB 提交后、文件复制前 | 30383 | DB **整新**（乙 rev2/丁/6000 标签/进度 42%）；文件全旧（乙旧字节、丁缺失）= 设计内非原子间隙（finding F3） | ✅ |
| K4 `before-file:epub/pk-d.epub`（丁复制前） | 30843 | DB 整新；丁文件缺失、乙仍旧字节（walk 序先到丁目录）、甲/丙保护字节完好 | ✅ |
| K5 staging 写入 16 字节后（真实半写入） | 32231 | DB 整新；`epub/pk-d.epub.restore-<nano>.staging` 恰 16 字节 torn 残留；丁目标文件未存在（未被触碰）；**无归属日志**（POSIX 上 `renameTo` 原子替换既有目标，journal 路径在设备上不触发） | ✅（torn staging 按设计保留，见 F2） |

每个场景均验证：kill 后备份 ZIP 逐字节不变（SHA 与导入前一致）、`am start` 重启成功且 post-restart 状态与 post-kill 完全一致（crash-consistent 稳定）、重试后 DB/文件达到期望终态、无 `*.restore-backup-*` 残留。

> K5 有两次先行失败记录（summary.json 保留）：一次为测试侧 hold lambda 未写出 16 字节头（staging 0 字节）、一次为驱动 `adb pull` 目录嵌套混入陈旧证据；均为测试工具问题，最终 PASS 记录为准。测试 lambda 已改为写出 16 字节后阻塞。

## 4. 验收判据逐项

| 判据 | 结果 |
|---|---|
| 旧数据完整保留 | ✅ 全场景：丙（本地独有）行+文件原样；甲（本地较新）行 rev2+文件字节在任何 kill 后都未被备份旧字节覆盖 |
| 新数据已提交则完整 | ✅ K2 证明未提交即整旧（WAL 回滚）；K3/K4/K5 证明已提交则 DB 侧整新无半行 |
| DB 引用文件存在且正确 | ✅ 收敛后四书文件 SHA 逐一等于期望（甲=本地新、乙=备份新、丙=本地、丁=备份）；**kill 后未重试时**丁文件缺失/乙文件旧字节属设计内非原子间隙（F3），重试即自愈 |
| 失败后 backup 仍完整 | ✅ 全场景 kill 后 ZIP SHA 不变，条目集合与逐文件 SHA 不变，重试可直接复用 |
| 再次启动 / 重试可收敛 | ✅ 重启应用稳定（新 PID、状态不变）；重试一次后 DB+文件达到终态、journal 空竭。注：**仅重启不重试不触发 `recoverCrashResidues`**（该方法只在导入/导出入口运行），文件段中间态会保留到下一次导入/导出 |

## 5. 发现（无稳定可复现产品缺陷；以下为行为事实与设计权衡）

- **F1 孤儿临时目录**：进程在解压后被杀时，`cacheDir/zip-restore-<ts>` 残留且无清理路径（每次导入只清理自己的临时目录，K1 实证残留 1 个）。后果仅为缓存垃圾累积，无数据损坏。可选改进：导入入口/应用启动时清理陈旧 `zip-restore-*` 目录。
- **F2 无证据 torn staging 会被保留并进入后续备份**：POSIX 上 journal 机制不参与正常替换，kill 于 staging 写入中留下的 `*.staging` 属「无归属证据残留」，按 S14 设计保留原样、**导出时照常打包**（K5 收敛后 books/ 内仍存 16 字节 staging，未来导出会包含它）。这是 `LocalZipBackup.kt` 注释明确声明的权衡（宁可保留不可凭文件名误删）；实际后果是备份包内多一个无 DB 行的孤立小文件，恢复端也按普通文件报告。可作为后续改进候选（如导入成功收尾时按「本次导入开始后才新出现的严格格式文件」清理——仍需规避命名学误判）。
- **F3 整包非原子（设计内）**：DB 提交与文件复制之间的 kill 留下「DB=新、文件=旧」斜偏，直到用户重试；期间丁的行会引用尚不存在的文件。与类注释声明一致，本报告将其从「未验证声明」升级为「已实证的边界行为」。
- **F4 仅重启不收敛**：`recoverCrashResidues` 挂在导入/导出入口而非应用启动；重启只保证 crash-consistent，收敛靠重试。与文档一致，如实记录。

## 6. 变更与修复范围

| 文件 | 变更 | SHA-256（前 12 位） |
|---|---|---|
| `android/.../feature/sync/LocalZipBackup.kt` | +11 行：`restorePhaseHook` 接缝 + 5 个边界回调（生产 null 恒定） | 56c1c45486ca（基线 ab78a16e8e34） |
| `androidTest/.../LocalZipBackupProcessKillTest.kt` | 新增专用仪器测试（reset/prepare/hold×2/dump/converge） | 2a2442efe82d |
| `test/.../LocalZipBackupPhaseHookTest.kt` | 新增 JVM 接缝契约回归（2 用例） | af1c8dad5180 |

- 定向 JVM 回归：`LocalZipBackupTest` 34/34 + `LocalZipBackupPhaseHookTest` 2/2（qahost 变体，`--offline`）。未复跑上一轮全量 2628 JVM——本次唯一产品文件变更为 null 默认接缝，无新风险面。
- APK（本轮测试用，基线构建 host `19383e8ccbdb` / test `91a0b3817d2f`；加接缝后 host `79b7518915dd`、test（最终）`67fffbba86ce`）。
- 风险评估：接缝生产路径零行为差异（一次空判）；若上游合流 `LocalZipBackup.kt` 冲突，仅新增成员与 5 行 `?.invoke`，易手工合入。

## 7. 未测项（如实列出）

- 实体手机与 TalkBack 路径（本轮授权仅 MuMu）；SAF UI 手动选包入口的端到端（测试直接驱动 `importZip`，与 UI 共用同一生产函数）。
- WebDAV / 局域网同步路径的中断行为（非本任务范围）。
- 恢复期间并发读写库（另一进程同时改库）下的中断；数 GB 级超大型书库的窗口时长线性外推。
- Windows JVM 上 journal 保护路径的中断（既有 JVM 测试已覆盖换入语义；设备上该路径仅在 `renameTo` 失败时触发，本轮未构造）。

## 8. 环境恢复与逐项对账

1. 卸载本轮 host/test APK → 重装原宿主 APK → 冷备数据推回 + `chown`（新 uid 10081）+ `restorecon -R`。
2. **持久文件**：51/51 与测试前 `before-manifest.txt` 逐条 SHA 一致（`logs/restore-verify.py.log`）。
3. **APK**：设备上 `base.apk` SHA `e82904cc9c13…4ef8a` == 冷备 `original-host.apk` ✅。
4. **系统设置**：density 280、夜间未强制、`stay_on_while_plugged_in=0`、`accelerometer_rotation=1`、`user_rotation=0`，与基线一致（`logs/settings-after-restore.txt`）。
5. 测试残留（`pk_restore_root/`、`pk_restore_processkill_test.db*`）随卸载清除，已确认不存在。

## 9. 证据索引（`E:\creation-reading-assistant\next-round-n1\acceptance\`）

- `scenarios/summary.json` + `scenarios/<K0..K5>/`：阶段日志（hold 标记与 pid）、DB(db+wal+shm) 拉取副本、`db-state-postkill/converged.json`、`files-postkill/converged.json`、`pk-state-dump-*.json`、`pk-converge-result.json`、kill 前后备份 ZIP 及清单。
- `logs/driver-commands.log`（全部 adb 命令+退出码）、`logs/driver-run.log`、`logs/driver-k5-rerun2.log`、`logs/build-with-seam.log`（36/36 JVM）。
- `evidence/n1-restorePhaseHook.patch`、`evidence/LocalZipBackup.baseline.kt`、`backup/before-manifest.txt`、`backup/data-cold/`、`backup/original-host.apk`、`logs/settings-after-restore.txt`、`logs/restore-verify.py.log`。
