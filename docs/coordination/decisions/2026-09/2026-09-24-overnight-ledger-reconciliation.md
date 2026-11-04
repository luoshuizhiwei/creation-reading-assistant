# 夜间总账对账（2026-09-24，窗口 D · 仅文档）

- 工作区：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`，HEAD `5aed5939600e491843e3c44453921bd6273a233c`，staged **0** 项。
- 本轮性质：**只做协调文档**。未改任何源码或测试、未运行 Gradle / ADB / 手机 / 系统或设备设置、未做 `reset` / `checkout` / `clean` / `stash` / `add` / `commit` / `push`。
- 写入范围（仅两处）：本文件 + 在[当前任务账本](2026-09-22-current-task-ledger.md)末尾**追加**一节带日期的裁决（未删除、未覆盖任何历史条目）。
- 观测时刻：本文所有工作树事实取自 **2026-09-24 02:5x–03:0x** 的一次只读快照。窗口 C 当时仍在活动（见 C-1），**这些数字可能在你读到时就已变化**；每条都附了可自查的指针。

## 1. 对照表：总账旧状态 → 证据 → 正确状态 → 仍缺什么

### S04（EPUB 来源替换哈希语义）

| 面 | 内容 |
|---|---|
| 总账旧状态 | 尾部最后一节停在 **stage-p**：`2026-09-23-s04-epub-book-hash-codex-review.md` 裁「暂不放行真机，S04 仍 PARTIAL」，点名两条 P2。另 §3 父切片表 S04 行仍写「**已关闭**（当前代码与 JVM 范围）… fresh JVM 58/58」——**同一文件内自相矛盾**。 |
| 当前证据 | 报告 `agent-stage-q-s04-epub-hash-failure-correction.md`（mtime 09-23 21:36）针对该两条 P2 修正。工作树核对：`feature/reader/EpubRepository.kt:329-343` 现为 `internal fun computeFileHash`、`require(len > 0L)`、**已无 `runCatching(...).getOrNull()`**；`EpubRepository.kt` 为 ` M`。测试 `EpubRepositoryReplaceTest.kt`（`??` 未跟踪）现有 **5** 个 `@Test`，含 stage-p 要求的「后置失败逐字节回滚」与「0 字节必须抛错」两条。 |
| 正确状态 | **S04 = PARTIAL；stage-q 为「代码/JVM 候选，待 Codex 复核」**。父项不得因 stage-q 而关单；§3 表 S04 行的「已关闭」登记为过期措辞。 |
| 仍缺 | ①**不存在 stage-q 的 Codex 复核**（全仓最后一份 S04 Codex 回读针对 stage-p）；②red（`tests="5" failures="1"`）/green（5/5、退出码 0）与 `ShelfImporterTest` 36/36 的 XML **全部留档在仓库外私有目录**，工作树内不可回读；③`ShelfImporterTest` 现在是 **39** 个 `@Test`（`grep -c @Test` 实测），stage-q 的「36/36 零回归」不覆盖当前源；④EPUB 隔离真机复验未做；⑤断电/强杀级物理原子性、真实 Room 事务未覆盖（报告 §5 自陈）。 |

### S06-⑤/⑥ C · 动画期连点队列

| 面 | 内容 |
|---|---|
| 总账旧状态 | 总账 §3 S06 行 = 「待核实」；§4 未登记 S06-⑤/⑥。C 侧最新两份：建档报告 `agent-next-c-s06-rapid-page-turn.md`（09-23 22:30，自陈未编译未跑）与诊断报告 `2026-09-24-c-high-speed-test-diagnosis.md`（01:04，称「`PageTurner.kt` 一字未动，mtime 09-23 22:19」）。 |
| 当前证据 | **两者都已过期**：`PageTurner.kt` mtime **09-24 02:57**、`git diff` **+312/−89**；报告所记 `PENDING_TURN_CAPACITY`、`enqueueTurnRequest` 在当前源码中**不存在**，取而代之的是**新文件** `feature/reader/pager/PendingTurnQueue.kt`（`??`）与 `PageTurner.kt:119-123 / 354-453` 的 `enqueue/poll/requeueFront/clear` + `turnEpoch` + `TurnGate:867`。**只读快照 03:01:37**：新建 `PendingTurnQueueTest` 的定向 JVM 结果 `tests="11" failures="2" errors="0"`（XML sha256 前缀 `e5c59f403b7e9e42`，失败类型 `java.lang.AssertionError`）。 |
| 正确状态 | **并行进行中 / 报告与源码不一致 / 待 Codex 复核 —— 不得关单**。这是窗口 D 的只读观测快照，不是对 C 代码的裁决。 |
| 仍缺 | ①C 自己的完成报告（当前源码没有任何报告描述）；②**门禁链已全部失效**：`compileDebugAndroidTestKotlin`（01:01:38 退出码 0）、test APK 打包（01:51，`716728d6…`，1,512,197 B）、`app-debug.apk`（09-23 22:58，`acab95ba…`，52,482,008 B）全部早于 02:57；当前源码能否编译**未知**；③5 项 instrumentation 用例 **0 completed / 0 failed**；④R6「切换翻页效果即作废积压」合同待 Codex 裁决；⑤同帧双点被上游 `externalTurnRequest` 单槽合并的问题仍在。 |

### S06-⑦ + D · CraSlider RTL

| 面 | 内容 |
|---|---|
| 总账旧状态 | 09-23 真机报告曾记 D = **PASS（镜像）**；`2026-09-23-bcd-evidence-correction.md` §4 撤回为 **PARTIAL**；`2026-09-24-d-evidence-alignment.md` 进一步把「JVM 8/8 不得记作真机 RTL PASS」写入口径 4 处。 |
| 当前证据 | JVM：`CraSliderTest` 现 **8** 个 `@Test`（我 09-24 增加 2 条镜像/边界不变式；01:01 定向运行退出码 0、`tests="8" failures="0"`）。**但该 XML 已被 C 的 03:01 定向运行覆盖，工作树内当前只剩 `PendingTurnQueueTest` 一份 XML** ⇒ 8/8 现为报告口径。androidTest：`CraSliderRtlRenderTest.kt`（4 条，sha256 `5017af8d…`，与 cd 验收报告 §1.1 逐位一致）；真机结果 = **1 项 BLOCKED + 3 项未运行**，0 项取得结果。 |
| 正确状态 | **D 真机 RTL：0/4 取得结果；既非通过也非失败。** JVM 侧 8/8 只证明几何镜像不变式与画布包含性，不证明渲染。旧截图继续按「未镜像的反证」登记。 |
| 仍缺 | ①SR-ENV-1 三选一未裁定（换真机 / CI / 明确接受本机不可验并改 JVM 替代门禁），`2026-09-24-compose-test-host-investigation.md` §0 =「**暂无可信绕过方案**」（4 候选中 A/B/C 与失败路径机制同源，D=Robolectric 不满足「真实手机」）⇒ **9 项目前没有关单路径**；②H6：`debug.force_rtl` 为何未生效仍未证实（09-23 全程只有 `settings put/get`，**从未 `getprop`**、从未取过 `ldrtl/ldltr` 配置输出）；③M3 对 `track` 槽左右留白对称性（拇指-填充交界贴合）未取证；④RTL 下滑动语义仍未实测（09-23 只有 tap）。 |

### S05-④ B · 导入后「查看」定位

| 面 | 内容 |
|---|---|
| 总账旧状态 | 09-23 报告 B = PASS → 更正为 **PARTIAL**；总账 §3 S05 行仍为「待核实」，未登记 S05-④ 实现。 |
| 当前证据 | 实现报告 `agent-next-b-s05-import-focus.md` 自陈「**本文所有测试结论均为设计意图，不是执行结果**」；工作树：`ShelfImporter.kt`、`ShelfRoute.kt`、`ShelfScreen.kt`、`ShelfImporterTest.kt` 均 ` M`，`ShelfImportFocusTargetTest.kt` 未跟踪且为 **15** 个 `@Test`（实测）。统一构建门禁 `agent-next-bcd-build-gate.md`（09-23 23:00）记：lint 退出码 0（0 error / 9 warning）、`ShelfImporterTest` **39/39**、`ShelfImportFocusTargetTest` **15/15**、`CraSliderTest` 6/6（已过期）、「60/60 已运行并通过」。 |
| 正确状态 | **S05-④：代码 + JVM 门禁已过；真机 B-1/B-2 仍 PARTIAL。** JVM 通过不得写成真机验收 PASS。 |
| 仍缺 | ①B-1 原始记录 15:20:36–15:21:35 全程**没有点击「查看」**（`tap 1073 2410` 缺失）⇒ 定位可用性未验证，现存证据无可补，需重新真机执行；②B-2 的 fallback 与「MD 本就按最近导入排第一」两种解释在截图/dump 上**不可区分** ⇒ 是否触发不可判定，需要设计能区分两者的证据（例如让候选列表首个 id 与视觉落点可对照的可观察信号）；③600 ms 上界、`delay` 真机行为、部分可见滚动落点均未测；④门禁记 39/39 而 B 报告只说「追加 3 条」，**是谁在何时跑的、XML 在哪**需由门禁方留证。 |

### 统一门禁与真机通道（跨 B/C/D）

| 面 | 内容 |
|---|---|
| 总账旧状态 | 无。门禁报告是 09-23 23:00 的独立文档，总账未收录其结论。 |
| 当前证据 | 门禁三门禁退出码 0；但主 APK mtime 09-23 22:58、test APK 09-24 01:51，**均早于 02:57 的 PageTurner 变更**。真机侧：对照组 `AppDatabaseMigrationTest` **OK (23 tests)、1.125 s、退出码 0**（同设备、同会话、同 test APK、同 runner，且 `am instrument` 带 `-r`）；D-1 `started` 无 `finished`、外部 180 s 超时退出码 **124**、原始输出 414 B；本轮**首次取得自身实证**（未截断 logcat 27 MB、`01:54:44`）：`MIUILOG- Permission Denied Activity`、`Abort background activity starts from 10949`、`result code=102`，前台仍 `com.miui.home`。 |
| 正确状态 | 门禁结论**只对 ≤09-23 22:58 的产品源码成立**；「可以交付后续真机验收」需限定为「非 Compose 用例可交付」。Compose instrumentation 阻塞**本轮已实证**，归因边界限于「宿主 Activity 启动失败 + 180 s 无完成结果」。 |
| 仍缺 | ①PageTurner 收敛后**一次统一重建**（lint + assembleDebug + assembleDebugAndroidTest + 全量 JVM）；②SR-ENV-1 裁定；③如需整批真机跑，`stay_on_while_plugged_in`（原值 `3`）需显式授权（SR-ENV-2）；④「是否有本轮未观测到的产品缺陷」——因 0 结果，**全部目标行为仍未验证**。 |

## 2. 交叉冲突登记（不改写任何一方，只登记）

1. **测试计数**：`CraSliderTest` 6（建档/门禁）↔ **8**（当前源、01:01 运行）；`ShelfImporterTest` 36（stage-q）↔ **39**（当前源、门禁）。以当前源 `@Test` 计数为准，旧数字标注所属批次。
2. **test APK 哈希**：门禁 `C061AB5B…`（1,498,298 B，09-23 22:59）↔ cd 验收 `716728d6…`（1,512,197 B，09-24 01:51）。后者更新，含 D 的 4 条（javap 已复核）。
3. **「MIUI 是根因」的口径演进**：09-22 有原始日志（跨轮）→ 09-23 属**跨轮类比**（`logcat -d -t 100` 只回显 adbd）→ 09-24 取得本轮自身实证。三份文档口径不同并不矛盾，但引用时必须写清是哪一轮的证据。
4. **就地改写的未跟踪文件**：`docs/qa/2026-09/2026-09-23-bcd-portrait-device-acceptance.md` 与 `evidence/…/screenshot-manifest.md` 都是 `??`（未跟踪），就地更正**没有 git 历史可回读**；唯一基线是更正报告 §0.2 记录的仓库外原文哈希。后续再改这两个文件必须继续「先保藏哈希再改」。
5. **设备身份**：历史记 `22011211C`，09-24 实测 `22081212C`（`ro.hardware=qcom`、`ro.kernel.qemu` 空 ⇒ 真机判定不受影响）。登记为 SR-DOC-1，本轮不核实、不推断。
6. **文档署名**：`2026-09-24-d-evidence-alignment.md` 标题署名「窗口 B」却处理 D 的证据；`WorkBuddy` 署名已加「非活跃进程」注记。建议后续报告统一「窗口 + 日期 + 场景号」命名，避免引用歧义。

## 3. 下一轮：可并行 / 必须串行

**可并行（互不重叠文件，不需要设备）**

| 项 | 前置 | 证据文件 | 文件所有权 |
|---|---|---|---|
| P1 Codex 复核 stage-q（要求把 red/green XML 落进仓库或重跑；并把回归口径改为 `ShelfImporterTest` 39） | 无 | `agent-stage-q-…-correction.md`、`EpubRepository.kt:329-343`、`EpubRepositoryReplaceTest.kt` | Codex |
| P2 B 设计「fallback 可区分证据」方案（先设计后执行，不动源码） | 无 | `2026-09-24-b-evidence-alignment.md` §B-2、`agent-next-b-s05-import-focus.md` | B（文档）+ Codex 裁定 |
| P3 SR-DOC-1 设备型号核实（只读比对既有证据，不接设备） | 无 | cd 验收 §1.2、09-23 门禁与验收记录 | Codex |
| P4 隐私/工件遗留清单化（`dump3–6.xml`、归档文档两处历史路径、`/sdcard` 通配清理不可界定） | 无 | `2026-09-23-bcd-evidence-correction.md` §1.3、§2.4、§7 | Codex 决策（删除/脱敏/保留） |
| P5 **C 自己**收敛队列并补写报告 | 不与 P1–P4 争文件 | 当前 `PageTurner.kt` / `PendingTurnQueue.kt` / 两个测试文件 | C |

**必须串行（每一步都要等上一步的产物）**

1. **S1**：C 队列收敛并交付报告 →（在此之前任何门禁结论都只对旧源码有效）。
2. **S2**：由唯一构建 owner 做统一重建 + 全量 JVM：`lintDebug`、`assembleDebug`、`assembleDebugAndroidTest`、全量 `testDebugUnitTest`；**当场登记每个 XML 的路径 + 哈希**（避免本轮再次发生 XML 被覆盖）。
3. **S3**：Codex 裁定 SR-ENV-1（换真机 / CI / 接受本机不可验）→ 决定 C 的 5 项 + D 的 4 项共 9 项的运行路径。
4. **S4**：真机竖屏验收按新 test APK 重取（含 D-1 的宿主阻塞是否复现、B-1/B-2 的可区分证据、EPUB 隔离书复验）。
5. **S5**：以上全部收敛后才谈提交；提交由集成者串行，且不得夹带其他窗口的在途改动。

## 4. 所有权冲突与风险

- **热点文件**：`PageTurner.kt`（C，02:57 活跃）+ 新 `PendingTurnQueue.kt`（C）——任何重编译/门禁必须等其收敛；`ShelfImporter.kt`（B，22:28）被 stage-q 当作回归对象，**两者对同一文件有不同版本号认知**（36 vs 39）；`AndroidManifest.xml` 为他窗未提交改动（+5/−1：TTS `<queries>` 与新 `configChanges`），引用其内容判断 RTL 时必须标明「工作树未提交」。
- **共享构建目录**：`android/app/build/` 为单一物理目录，**定向 `--tests` 运行会覆盖 `test-results/testDebugUnitTest/` 的 XML**（本轮实测：01:01 的 CraSliderTest 8/8 XML 已被 03:01 的运行清掉）。留证运行必须当场复制/哈希，或改用独立 worktree。
- **未解决风险（登记，不处置）**：①9 项真机结果为 0 且无绕过路径；②stage-q 未经复核；③当前 PageTurner 源码未经任何编译验证（**已知在 03:01 快照时其 JVM 队列测试有 2 项红**）；④RTL 生效机制未证实；⑤隐私工件仍在工作树内未处置；⑥门禁/哈希记录与源码时间线错配可能再次发生。

## 5. 措辞纪律（本轮据此把守）

- 口头「完成」、`exit 0` 的编译、更早批次的 JVM 通过、其他批次的测试通过，**都不得**被转写成某场景的正式验收 PASS。
- 未发起 = 「未运行」；发起无结果 = 「BLOCKED」；两者都不得写成失败或通过。
- 真机 RTL 相关截图一律记作「未取得镜像证据」，`debug.force_rtl=1` 期间的旧图不得复用为本轮 RTL 凭据。
- 本轮不裁决 C 的在途实现，只登记快照观测与「待 C 报告 + 待 Codex 复核」。

## 6. 本轮边界声明

只写两份文档：本文件与总账末尾的追加分节。未修改任何 `.kt` / `.xml` / manifest / Gradle / 脚本；未运行 Gradle、ADB、模拟器、真机；未触碰用户书库、设备设置、系统语言。HEAD 仍 `5aed593`，staged 0，无 `reset` / `checkout` / `clean` / `stash` / `commit` / `push`。文中不含真实书名、私有目录或日志原文；设备侧原始大日志保持在仓库外，仅引用其脱敏摘要与时间戳。
