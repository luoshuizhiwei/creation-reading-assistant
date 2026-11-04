# J3-n1-fix-accept-v2：驱动/协议返修 + 定向门禁 + MuMu 限定验收（2026-10-02）

## 0. 任务编号 / 版本 / 执行者

- **任务编号**：`J3-n1-fix-accept-v2`
- **执行应用**：Claude Code（桌面 Code 标签页）
- **实际执行模型**：`hw726brokbuddy AI/global:deepseek-v4.1-flash`（经 ccr 路由，模型别名 `anthropic/claude-ccr-h776f726b62756464792041492f676c6f62616c3a646565707365656b2d76342e312d666c617368`）
- **角色**：本任务唯一实施者、构建者与 MuMu 所有者
- **日期**：2026-10-02

## 1. 输入指纹（全部实算核对）

| 输入 | 期望 | 实算 | 结论 |
|---|---|---|---|
| 恢复仓库 HEAD | `e39a78da9c62f02614207cffb36969a32492eaea` | `e39a78da9c62f02614207cffb36969a32492eaea` | ✓ 一致，暂存区为空 |
| 生产 `LocalZipBackup.kt` | `f141941a…b298e` | `f141941a628de19660d71875bb34faa5b4833f05f6db7d0025aba774165b298e` | ✓ |
| v7 `LocalZipBackupTest.kt` | `000b7c81…1e8f3` | `000b7c811abc1ac013d301203d52f6a4ef9c8161a641f6d76ee782573f01e8f3` | ✓ |
| J3-v1 驱动 | `3bc29768…aaa4c` | `3bc297688bf4e98a81dfa67c4180eeb9afc9fe4e906a2e9d05edd5272baaaa4c` | ✓ |

仓库分支 `codex/recover-worktree-20260920`，HEAD 与预期一致，暂存区 0 项，porcelain 489 项（与本批开始前相同，未增未减）。

## 2. 所有权与改动

**新候选**：`E:\creation-reading-assistant\next-round-j3-n1-fix-accept-v2\android`
（旧候选 `next-round-j3-n1-sandbox-build-v1` 与 v7 冻结件**只读保留，未改一字**。新目录此前不存在，无既有成果可续接。）

**全树差异（879 vs 879 文件，实算 SHA-256）**：

| 项 | 数量 | 内容 |
|---|---:|---|
| 仅 J3-v1 有 | 0 | — |
| 仅 v2 有 | 0 | — |
| **内容不同** | **2** | `app/src/androidTest/.../LocalZipRestoreAcceptanceTest.kt`（唯一驱动，`3bc29768…` → `12ac7175…`）；`app/src/test/.../LocalZipBackupTest.kt`（v7 精确复制，`4d6825b1…` → `000b7c81…`） |

**八项 N1 继承指纹**：其中 **7 项与 J3-v1 逐字节相同**（`JsonBridge.kt` `f8e455a4…`、`LocalZipBackup.kt` `f141941a…`、`LocalZipRestoreStateStore.kt` `167b881a…`、`ProfileRoute.kt` `89a793ba…`、`ProfileViewModel.kt` `124517d2…`、`JsonBridgeTest.kt` `bcc27a4d…`、`LocalZipRestoreStateStoreTest.kt` `9eba0a91…`），**仅 JVM 测试替换为 v7**。后缀单 hunk（`app/build.gradle.kts:119` `applicationIdSuffix = ".qahostn1"`，文件 SHA `d4366e3f…`）与 J3-v1 一致。

**未改**：生产算法、Profile、runner、依赖、后缀、`current.md`、项目页、TODO、原损坏仓库、共享 WIP、桌面/HOLD。未 stage / commit / push / reset / checkout / 搬移目录。未读取 Claude 独立记忆库。

## 3. 环境

- Windows 11 / Git Bash；JDK 17；Android SDK build-tools 34.0.0（只读 aapt/apksigner）；Python 3.13（只读核对脚本）。
- 自有缓存：`E:\…\next-round-j3-n1-fix-accept-v2\_cache\gradle-home`（从 J3-v1 复制 24,253 文件，相对路径集合完全一致；3 个 Gradle 锁文件被占用未复制，Gradle 自建）。构建全程 `--offline`，未写他人缓存。
- 设备：MuMu 模拟器，`adb -s 127.0.0.1:16416`。

## 4. 执行与结果

### 4.1 定向 JVM 门禁（先于构建与设备）

```bash
GRADLE_USER_HOME=<自有缓存> ./gradlew.bat :app:testDebugUnitTest \
  --tests com.creationreadingassistant.feature.sync.LocalZipBackupTest \
  --tests com.creationreadingassistant.feature.sync.JsonBridgeTest \
  --tests com.creationreadingassistant.feature.sync.LocalZipRestoreStateStoreTest \
  --offline --console=plain --max-workers=1
```

**exit 0，BUILD SUCCESSFUL in 3m 10s**。原始 XML 实读：

| 类 | tests | failures | errors | skipped | XML SHA-256 |
|---|---:|---:|---:|---:|---|
| `LocalZipBackupTest` | **62** | 0 | 0 | 0 | `a78f04bee23d94f24ddce8bed3d1866d012ca17cf3f6795fd9a0075a0b45cd06` |
| `JsonBridgeTest` | 20 | 0 | 0 | 0 | `7407752e54bbd8172200a7fb87c307f446fb29a7b4f5775f9ccce8326251c5f2` |
| `LocalZipRestoreStateStoreTest` | 16 | 0 | 0 | 0 | `c6fb0b4df79c0c5886df31fbd0773dcff8c91f523efbb9a7c0909c5d64710897` |

`LocalZipBackupTest` 保留 **62 项**，与 v7 验收一致。日志：`logs/jvm-targeted-20261002-203634.log`。未重复 v5 负向对照，未跑无关全量套件。

### 4.2 构建与冻结

```bash
./gradlew.bat :app:assembleQahost :app:assembleQahostAndroidTest \
  -PcraAndroidTestBuildType=qahost --offline --console=plain --max-workers=1
```

**exit 0**（首次因驱动两处编译错误失败，见 §7，修正后 `BUILD SUCCESSFUL in 21s`）。日志：`logs/build-qahostn1-20261002-204541.log`（失败那次是 `…-204006.log`，原样保留未覆盖）。

| APK | 字节 | SHA-256 | 包名 | 签名 |
|---|---:|---|---|---|
| 宿主 | 51,338,932 | `d99fe746e5bb5f726f313c62159e16a46aba0df8eb0a76bbbb4523d211321082` | `com.creationreadingassistant.qahostn1` | v2 方案 true；证书 `05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1`；apksigner exit 0 |
| androidTest | 9,133,863 | `ab1210969860b809c208c13fc3f4ceee2c433e2bbb65925885a2253a7875c758` | `com.creationreadingassistant.qahostn1.test` | 同上证书；apksigner exit 0 |

- 宿主 `versionCode=3` / `versionName=0.5.1` / minSdk 24 / targetSdk 35；`application android:name="com.creationreadingassistant.App"`。
- androidTest `instrumentation android:name="com.creationreadingassistant.testing.CraIsolatedTestRunner"`，`targetPackage="com.creationreadingassistant.qahostn1"`。
- **`.qahostn1` 透传生产 Application 的实际边界（如实登记）**：runner 的隔离分支按精确字符串 `com.creationreadingassistant.qahost` 比较，`com.creationreadingassistant.qahostn1` **不匹配**，故清单声明的生产 `com.creationreadingassistant.App` 会在新沙箱进程内真实启动——Hilt 图谱、三条 `cra-startup` 后台接线、应用级夜间模式与 `androidx.startup` 的 workdb 都会运行。换 UID 只带来独立私有数据目录，**不等于没有启动副作用**。设备侧实证：`n1_00` 后沙箱包 `databases/` 里出现了 `creation_reading_assistant_native`（+wal/shm）与 `files/profileInstalled`，均由生产启动产生，非本驱动夹具。
- **宿主 APK 与 J3-v1 冻结件非逐字节相同**（多 dex 分区/打包元数据不同），但**全部 dex 的类型描述符集合完全相同**（45,533 / 45,533，两侧独有均为 0）——生产代码未变。androidTest APK 的类型级差异恰为驱动改写范围（删除 `n1_31_importBScenario4`、`readOnlyInspection`；新增 `n1_31_armKillScenario4`、`n1_32_readOnlyAfterKillScenario4`、`n1_33_importBScenario4`、`n1_34_importBAgainScenario4`、`readOnlyAfterKill`、`readBookRow`、`Json$Default`）。冻结清单：`freeze/freeze-manifest.json`。

### 4.3 设备批（顺序执行，逐阶段）

设备确认：`adb devices` 唯一 `127.0.0.1:16416`；该端口的监听进程 PID 27632 = `MuMuVMMHeadless.exe`（结合本机 MuMu 进程与 TCP 对应确认），guest 侧 `ro.product.cpu.abi=x86_64`、`ro.build.characteristics=nosdcard`、`qemu.hw.mainkeys=1`。**未凭 model 字符串判定**——该机 model/manufacturer 被模拟为 `SM-S9280` / `Samsung`。

**安装前既有包检查（P1 合同）**：精确检查宿主与测试两个新包，**两者原先均不存在**，故继续；未覆盖、未卸载任何已有包，未走「冷备后继续」分支。

**逐场景结果（22 个方法全部 PASS，3 次 arm 被真实终止）**：

| 场景 | 方法 | 结果 |
|---|---|---|
| 0 | `n1_00_cleanupBeforeSeed` | PASS |
| 1 equal | `n1_01_seedScenario1_equal` | PASS |
| 1 | `n1_02_armKillScenario1` | **真实终止**（无 JUnit 结果，进程已消失） |
| 1 | `n1_03_readOnlyAfterKillScenario1` | PASS |
| 1 | `n1_04_retryBScenario1` / `n1_05_retryBAgainScenario1` | PASS / PASS |
| 1 | `n1_06_reselectAScenario1` | PASS |
| 2 空指纹 | `n1_10` / `n1_11_armKill` / `n1_12` / `n1_13` / `n1_14` | PASS / **真实终止** / PASS / PASS / PASS |
| 3 legacy | `n1_20` / `n1_21` / `n1_22` | PASS / PASS / PASS |
| 4 accepted | `n1_30` / `n1_31_armKill` / `n1_32` / `n1_33` / `n1_34` | PASS / **真实终止** / PASS / PASS / PASS |
| 5 strictlyNewer | `n1_40` / `n1_41` | PASS / PASS |
| 6 导出 | `n1_50_exportGateAndRealZip` | PASS |
| 7 收尾 | `n1_99_cleanupSandbox` | PASS |

**真实强停与新 PID 证据**（三次 arm 一致）：

| 场景 | 信号 runId | 强停 PID | `pidof` 校验 | 强停后 `pidof` | 新进程 PID | 结论 |
|---|---|---|---:|---|---|---|
| s1 | `s1-1790945712473-72595606318166` | 17480 | =17480 ✓ | **空** | 17551 | 真实终止 |
| s2 | `s2-1790945787106-72670239402440` | 17865 | =17865 ✓ | **空** | — | 真实终止 |
| s4 | `s4-1790945870853-72753985613639` | 18407 | =18407 ✓ | **空** | 18480 | 真实终止 |

每次强停前核对 runId、scenario、时间戳新鲜度（3.8~4.5 s）与 `pidof` 一致性，全部通过后才发 `am force-stop`。

**关键产品行为证据**（原始 JSON，`evidence/evidence/`）：

- `readonly_s1` / `readonly_s4`：强停后书行存在、`revision=2`、正文 = 原字节（`a8c1efea…`，A 未换入）、状态 `db_committed` 且 `isComplete=false`、义务 = 准确的 A 正文 SHA `c2b0974e…`、逐文件 journal 含该 rel 且 `verified=false`、**`killedPid ≠ currentPid`**。
- `n1_33_importBScenario4`：**B 之后 Room 读回 `revision=3`**（A accepted→2，B accepted→3），冲突正文未写入、A 义务保留。
- `n1_50_exportGateAndRealZip`：场景 1 状态已清除；导出真实产出 ZIP；解包后 `backup.json` 的 id/revision 与真实 DB 行一致（revision=2）；**ZIP 正文逐字节等于恢复后的 A 字节**。
- `n1_50_blocked_s3` / `n1_50_blocked_s4`：状态未完成，导出被**精确**阻止，报错含「上次整包恢复未完成」；s3 的 legacy 义务 `12345` 未被 B 的 SHA 替换。

## 5. 原始证据

`E:\creation-reading-assistant\next-round-j3-n1-fix-accept-v2\`

| 路径 | 内容 |
|---|---|
| `device-runs/*.txt` | 27 份原始 `am instrument` 输出（含 exit、OK/FAILURES、异常栈、时间戳）。每次运行独立文件名，**未覆盖任何旧失败日志** |
| `device-runs/*-signal.json` | 3 份强停信号原件 |
| `evidence/evidence/*.json` | 25 份设备侧 JSON 证据（从 `n1-sandbox/evidence` 拉取） |
| `freeze/apk/*.apk`、`freeze/meta/*` | 双 APK、badging/manifest/signature 原输出 |
| `freeze/freeze-manifest.json`、`device-run-index.json`、`evidence-index.json` | 指纹与索引（含逐文件 SHA-256） |
| `logs/` | 定向 JVM 与两次构建的完整日志 |
| `device-baseline/` | 安装前/后的包、APK、持久文件 SHA、设置、mapping 基线 |

> 证据均为 **JSON**；instrumentation 结果 XML 由 `am instrument` 侧产出，两者不混称。

## 6. 未覆盖项（不得由本轮冒充）

- **SAF 用户界面未走**：本驱动用 `Uri.fromFile` 直接调用 `LocalZipBackup`，不经系统文件选择器，也不经「我的 → 数据与存储」的真实 UI 路径。
- **设备身份持久化未验**：`SandboxContext` 用内存 prefs 覆写，`SecurePrefs` 的真实落盘与 `cra_device_identity` 持久化不在本轮证据内。
- **实体手机 / MIUI 未验**：MuMu x86_64 模拟器不代表实体硬件、厂商 ROM 或 MIUI 启动阻塞行为。
- **整包 DB+文件原子性未验**：S14 真实 kill 与整包非原子边界保持原状。
- 本轮只覆盖 N1 恢复链的 `equal / accepted / strictlyNewer / 空指纹 / legacy 长度义务` 五类分类与其冲突保护、真实导出与状态门禁；**不代表正式签名链、升级链、桌面/HOLD 或发布**。
- 场景 5 的导出正/反门禁由 `n1_50` 一并覆盖（正向 s1、反向 s3/s4），未单独为 s5 建阻断用例——s5 导入后状态已正常清除，无阻断语义可验。

## 7. 返修记录（本轮实际修的问题）

**驱动编译期两处**（首次构建失败，已修）：

1. `org.junit.Assert.fail` 在 Kotlin 返回 `Unit` 而非 `Nothing`，`readSignal(...) ?: fail(...)` 把 `signal` 推断成 `Any`，导致 `signal["pid"]` 报 “No 'get' operator”。改为自建 `failNow(): Nothing`。
2. `readBookRow` 调用了 suspend 的 `bookDao.getById`，未标 `suspend`。

**审查报告 5 Spec / 2 Standards 的逐项落实**见 §8。

**执行期一处工具问题（非产品问题，如实登记）**：首次 `n1_02` arm 未收到强停——执行器轮询用 `adb shell cat <path>` 时，Git Bash 的 MSYS 路径转换把 `/sdcard/...` 改写成 `D:/develop/Git/Git/sdcard/...`，`cat` 报 No such file，轮询 90 s 未命中。**驱动行为正确**：等待窗口 120 s 超时后写「未终止标记」并主动 FAIL，未冒充通过。执行器改用 `MSYS_NO_PATHCONV=1` 后重跑成功。该次失败日志（`20261002-205120-arm-s1.txt`）与标记文件原样保留。

## 8. 逐项回答审查报告

### Standards

1. **P1 既存沙箱包处理** — ✅ 已落实。安装前精确检查宿主 `com.creationreadingassistant.qahostn1` 与测试 `com.creationreadingassistant.qahostn1.test`，**两者原先均不存在**才继续；未覆盖、未卸载任何已有包，未走「冷备后继续」分支。其他既有正式/历史包（`.qahost`、`.qahost.test`、`local.creationReadingAssistant.mobile`）保持基线不动，且**未要求它们未安装**。收尾只卸载本轮新增的包对。
2. **P2 转发清理越界** — ✅ 已落实。全程**未执行任何 `adb forward` / `adb reverse` 增删**，因此不存在 `--remove-all` 风险。安装前与收尾后两次 `forward --list` / `reverse --list` 均为空，完整前后清单一致（`device-baseline/forwards-before.txt`）。

### Spec

1. **P1 准备第一步必败（`sandboxRoot()` 带 `mkdirs`）** — ✅ 已修。取路径与创建目录彻底分离：`*Path()` 系列只拼 `File`、**无任何副作用**；创建只走 `ensure*()`。`n1_00` 与 `n1_99` 的删除证明**只写外部证据目录**（`n1-sandbox/evidence`，是被删沙箱树的兄弟，不在删除范围内）。证据路径与被删除的测试数据严格区分（`n1-sandbox/evidence` vs `n1-sandbox/<scenario>`）。实测 `sandboxRootExistsAfterDelete=false`、`scenarioDirsRemaining=[]`、`dbFilesRemaining=[]`，断言不再被 getter 重建破坏。
2. **P1 accepted 跨进程路径不可达** — ✅ 已修。场景 4 拆成**四段独立方法**：`n1_30_seed` → `n1_31_armKill`（等待外部真实强停）→ `n1_32_readOnlyAfterKill`（**新进程纯读**）→ `n1_33_importB` → `n1_34_importBAgain`。强停后的 B 不再留在被终止的方法后面。**真实 Room 读回确认 A `revision=2`、B `revision=3`**（`readonly_s4`、`n1_33`），accepted 分类成立。
3. **P2 旧信号/continue 误导** — ✅ 已修。每次 arm 生成**唯一 `runId`**（`<scenario>-<millis>-<nanos>`），开始前清理旧信号、旧 continue 标记、旧未终止标记；信号含 `runId`/`scenario`/`pid`/`timestampMillis`，强停前执行者逐项核对（另核 `pidof`）。`continue` 标记**只表示继续完成复制**，绝不记为 `killObserved`。**arm 正常返回或超时 = 无效中断**：写「未终止标记」并**主动 FAIL**（本轮首次 arm 即触发此路径，行为正确）。真实终止由「外部 PID 消失（强停后 `pidof` 为空）+ 原 `am instrument` 结束 + 新进程 PID 读回（17480→17551、18407→18480）+ 未终止标记不存在」共同证明。**未用 IOException 冒充真实终止**。
4. **P2 纯读弱断言及场景残留** — ✅ 已修。`requireExistingDb` 在打开 Room 前断言测试库存在，杜绝「新建空库→返回成功」。纯读阶段断言：本场景书行存在、正文为原字节、义务为准确的 A 正文 SHA、状态未完成且停在 `db_committed`、接缝对应 journal 条目存在且 `verified=false`、**新 PID ≠ 强停 PID**。**场景级隔离**：每场景独立沙箱目录（`n1-sandbox/<scenario>/{files,cache}`）与独立 Room 库（`n1_sandbox_restore_<scenario>.db`），同一场景内部跨进程状态稳定保留，**上一场景的未完成义务在结构上不可能触发下一场景的导出门禁**。收尾 `n1_99` 删除全部场景沙箱与测试库并证明。
5. **P2 导出内容未验证** — ✅ 已修。`n1_50` 改为**固定预期**、不再按现态选分支：场景 1 正向——状态必须为 `null`，导出必须成功，**解包 `backup.json` 并对照真实 DB 行检查 id/revision**，**读取 ZIP 正文断言等于恢复后的 A 字节**；场景 3/4 反向——必须被阻断且原因必须是「上次整包恢复未完成」。`assertConflictingBBlocked` 同样断言精确阻断原因，不只看日志自报。

## 9. 恢复与副作用

- **设备已恢复**。收尾顺序：先保存证据（`adb pull` 25 份 JSON）→ 执行 `n1_99` 清理本任务夹具 → 卸载本轮新增 APK 对。
- 新包及其数据目录**确认消失**：`/data/user/0/com.creationreadingassistant.qahostn1` 与 `/sdcard/Android/data/com.creationreadingassistant.qahostn1` 均 `No such file or directory`。
- 既有资产对账**逐项一致**：
  - 既有包 APK SHA 三项与安装前完全相同（`.qahost` `e82904cc…`、`.qahost.test` `fdbd0190…`、`mobile` `d647f0f7…`）；
  - `.qahost` 持久文件 **53/53 SHA-256 完全一致**（含 3 个 DB/WAL/SHM、7 个 shared_prefs、datastore 与全部书文件）；
  - 设置一致：1080x1920 / 280dpi / rotation 0 / accelerometer_rotation 1 / timeout 60000 / night=yes / 无代理；
  - forward / reverse 清单前后均为空。
- 本轮为测试临时设置 `stay_on_while_plugged_in`（原值 **0**），收尾**已恢复为 0**并回读确认。
- 未触碰真实手机或正式书库；未清理其他任务资产。
- 恢复仓库 HEAD 与暂存区与本批开始前一致（`e39a78da…`，staged 0）；唯一写入的仓库文件即本报告。
- **产品行为未出现失败**，故未触发「保全现场后按边界恢复」分支。

## 10. 交付状态与下一步

**交付状态**：候选、定向 JVM 门禁、构建冻结、MuMu 五场景 + 导出正反门禁 + 夹具清理与设备恢复**全部完成且通过**。审查报告指出的 **2 Standards / 5 Spec 全部落实并实测验证**。

**下一步（建议）**：

1. 由 Codex 独立核查本报告与本批原始证据（尤其是三次真实强停的 `pidof` 空值与新 PID 对照、`readonly_s1/s4` 的义务 SHA、`n1_50` 的 ZIP 正文比对）。
2. 若核查通过，N1/G1 完整验收的 N1 侧可从 1/2 推进；**实体手机/MIUI、SAF UI、设备身份持久化、S14 真实 kill 与整包原子性仍是明确边界**，不由本批关闭。
3. 未开启新功能、未永久回灌、未提交、未推送。

**百分比**：本批任务范围 **22/22 设备方法 + 98/98 定向 JVM 用例通过**；N1/G1 完整验收口径仍为 **1/2 = 50%**（实体手机侧未做）；全产品完整分母仍未核定，不以本批局部 100% 代替全产品进度。
