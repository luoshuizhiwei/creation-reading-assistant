# E盘占用流程治理

## 任务编号/版本

CRA-STORAGE-GOVERNANCE-20261018-v1，2026-10-18，Root集成。流程整改交付，不改变产品验收状态。

## 输入指纹

本轮直接读取用户/仓库约定、vault入口/current，以及10月16日清理计划/A批交付、59/60和61/62核验；仅补读63的实际冻结、驱动和旧盘点中一个精确缓存子目录。无全盘/全历史扫描。

原63资产保持：gate SHA `5b4dbfe577234eeb895d39a0da2cdb1976debd271af4e7c0d3a39d2a35141e56`，config `745a4a034724c47ff1fb2415aa5cd1d93ec3a368c86c16179a29e1b5d75822c3`，freeze `31f0f8594bd2f096c3d3e3534c4254e9c8878c66ca464f3e403196968e0090dc`，build driver `11534fe219908a78acee289342d6ef1ba00f6978d891e79e5b49375e376a3b20`。严格复核沿原记录检查prebuild/build-record、1034产品输入、65验收输入和APK；不重造来源证据。

## 所有权与改动

Root唯一写入以下入口，独立子代理只读核对冻结语义。预检未见项目Java/Gradle/Python任务；已有ADB server、MuMu管理器、Claude/Codex进程保留，不由“无构建进程”推断缓存可删。仓库大量既有WIP保持；写前取当前文件SHA/时间，不与Git基线差异混算本轮改动。

| 实际入口 | 本轮改动 |
|---|---|
| `.codex/agents/android-worker.toml` | 实施者任务包直接要求最小差量，禁止完整工程/缓存/APK副本，编译交唯一owner |
| `.codex/agents/android-verifier.toml` | 验收者优先原冻结复用；失败交Root，无自动重建/复制；同SHA冲突保留日志交合同接缝 |
| `scripts/android-reuse-freeze.py` | 新只读调用入口：核Root接收的gate/config/freeze/driver指纹，只执行原gate的严格verify；不支持wrap、构建、复制、设备操作 |
| `docs/coordination/android-task-dispatch-template.md` | 外部完整提示词模板：必填大目录/预计字节/复用路径/owner/锁；同一REPORT报告实际体积、分类保留和停止条件 |
| `AGENTS.md` / current页首 | 将实际角色和派发入口连通，记录唯一工作区及保护边界 |

没有修改历史63驱动、共享gate、冻结、runner/工具绑定、Android产品或桌面代码。历史脚本仍为历史证据，不再作为未变化输入的下一批启动入口。

## 环境

当前唯一完整Android构建工作区：`E:\creation-reading-assistant\task59-common-candidate-v1\candidate\android`。59曾完整复制1033源码约19MiB、总目录报告约689MiB，偏差已有[59/60核验](../../reviews/2026-10/2026-10-18-tasks59-60-root-review.md)记录；62新增一个测试并复用59，63也复用此处，不能重开59或再复制工程。

实际Gradle home：`D:\develop\env\gradle`，JDK `D:\develop\Java\jdk-17.0.14`，SDK `D:\develop\Android\Sdk`。集成/完整构建唯一owner为Root指定执行者，当前Codex Root；锁 `E:\creation-reading-assistant\locks\android-gradle-exclusive.lock`。调用新只读入口期间同样由owner排除并发写入；该入口本身不接管owner或授予构建/设备权限。

明确保留的 `E:\creation-reading-assistant\txt-persist-codex-completion-20261006\gradle-home` 与当前D缓存角色不同，仍受保护，不因大小、年龄或空闲删除。本轮没有新建E工作区、Gradle home、构建树或APK目录；派发预计增量为数十KiB D文本文件，无新增大目录。

## 执行与结果

空间来源有三类，证据口径分别保留：

1. 历史反复创建Gradle home/构建树：10月16日盘点和A批10项实际清理释放32.97GiB已交付。本轮不重做。用户提供的最新284.3GiB/475目录、约50环境及37.03GiB build-cache是最近盘点口径，本轮未全量刷新，不再包装成新测量。
2. 59全源码复制：偏差成立，但62/63已正确复用59；不删除既有候选、不再复制。角色入口和模板此前缺少具体复用路由，本次补入。
3. 63 `tools/task63_build_driver.py` 实际使用`--rerun-tasks`并`shutil.copyfile`双APK至freeze/apk，真实构建221.85秒；本次冻结副本总61,537,874字节。这是当时真实发生的成本，不是本轮新增/可删除收益。旧驱动不改，不再用于输入未变的复用批次。

冻结合同判断：旧gate第708–721行把“构建前已存在且SHA相同”独立判FAIL；即使真实构建成功、mtime在窗口内也不豁免，因此“同SHA证明没构建”这个前提不成立。但63旧复用探针还缺真实build-record且mtime早于快照，拒绝它有合理依据，不能只凭报告判门禁整体错误。63成功冻结的prebuild记录两份暂存APK不存在，不是“同SHA实建接受”的证据。

本次最小兼容修正落在调用流程：**输入/环境/验收范围相同，引用已接收的原PASS冻结，执行原verify，不为新任务编号再snapshot/wrap。** 既有构建命令/cwd、退出码、日志SHA、原时间窗、package/runner/target/签名、双树/manifest和恢复合同全部保留。原gate的同SHA规则未改；若未来实际改变输入并真实构建后遇同SHA拒绝，必须交新版本冻结合同接缝，不能删产物、改mtime、伪造记录或强制重建制造不同SHA。该场景的新版门禁实现不在本次已完成项中。

验证仅覆盖本次入口：原63严格verify **PASS、exit0**；定向回归最终 **6/6、exit0**（指纹变化、非完整PASS、错误driver、严格核验失败无fallback、成功复用）。回归含Mock调用边界，不是新构建/设备结果；实物verify使用原生产gate/aapt2/apksigner及原资产，无Mock替代。脚本只输出原冻结指针，无新freeze/ready/监控或收据体系。

复核命令（运行时以Root接收指纹为准，不代表后续设备/构建许可）：

```powershell
& D:\develop\Python\Python313\python.exe -B scripts/android-reuse-freeze.py `
 --gate E:\creation-reading-assistant\freeze-provenance-task13-root-v2-20261011\tools\cra_freeze_gate.py `
 --gate-sha 5b4dbfe577234eeb895d39a0da2cdb1976debd271af4e7c0d3a39d2a35141e56 `
 --config E:\creation-reading-assistant\task63-common-device-entry-v1\freeze\attempt1\gate-config.json `
 --config-sha 745a4a034724c47ff1fb2415aa5cd1d93ec3a368c86c16179a29e1b5d75822c3 `
 --freeze E:\creation-reading-assistant\task63-common-device-entry-v1\freeze\attempt1\freeze.json `
 --freeze-sha 31f0f8594bd2f096c3d3e3534c4254e9c8878c66ca464f3e403196968e0090dc `
 --build-script E:\creation-reading-assistant\task63-common-device-entry-v1\tools\task63_build_driver.py `
 --build-script-sha 11534fe219908a78acee289342d6ef1ba00f6978d891e79e5b49375e376a3b20 `
 --evidence-cwd E:\creation-reading-assistant\task63-common-device-entry-v1
```

## 原始证据

[原始核验日志](../../2026-10-18-storage-governance-verification.log)、[单目录只读测量](../../2026-10-18-storage-governance-cache-candidate.log)，[旧清理计划](2026-10-16-e-space-cleanup-plan.md)、[A批交付](2026-10-16-e-cleanup-a-delivery.md)、[61/62复用证据](../../reviews/2026-10/2026-10-18-tasks61-62-root-review.md)。旧FAIL与旧报告保持。

本轮入口SHA（前→后，新文件前为不存在）：

| 文件 | 改前SHA256 | 改后SHA256 |
|---|---|---|
| AGENTS.md | 8e443bd0ae37dcc777a26c8af3953c63d8c3cf25b6fe05d4bb78311454f732cd | 129fad354adebf82d4200a23b424cef4acddccaafb60526ca8da6f8c2d606a0e |
| android-worker.toml | 070c1a75c8ed0000597d715b43b73c8c3b2a6d378ee1a4a00307654251101eef | 041ab6016d7a8472bdaa42051bd1d246bbc365f866588c20093841ec63986652 |
| android-verifier.toml | 583d234e23a2151448e487892ed00bb51d688f16d6ba56759002d616212a2af9 | 6dc2d56d32273b95f84821f14ab52508fc5ff3ef71530ce44a266ef1cb06e09e |
| current.md | 2bdfaeb58ee393d58c367d19f893a8b000e905cc451acd33cfedb5a82615e98f | 29b20567fee58b2b1719748b943ff31727152e6208da274ba19107dcaf0a4f0b |
| android-reuse-freeze.py | 不存在 | cfa7f877388ea7391f3c2c3d7daf9e8f1ed4fa7835ff8bb38aaa4b9834d5de5a |
| test_android_reuse_freeze.py | 不存在 | 47ec4d82b73a28265abb1012156315cd488bd79ae272e992d6f8428f9c06a300 |
| android-task-dispatch-template.md | 不存在 | 5b79925b15e0faaada0678247d547c65c0456965bc81b537acda2bb38f294283 |

## 未覆盖项

仅列一个待确认回收候选（未删除，非执行清单）：

| 项目 | 事实与限制 |
|---|---|
| 完整路径 | `E:\creation-reading-assistant\txt-persist-merge-mumu-accept-v1\gradle-home\caches\build-cache-1` |
| 内容/占用 | Gradle构建缓存子目录，2155文件，按设备/文件ID去重逻辑大小1,421,073,925字节，约1.323GiB；未枚举其父目录、兄弟或其他home；错误0、reparse跳过0 |
| 当前使用/引用 | 本轮未见项目构建进程；当前63配置/驱动使用D缓存，当前交接和59/62核验未引用该目录。历史脚本/未来重放引用尚未全面证明；不据此批准删除 |
| 保留依据 | 尚无本轮精确删除许可；源/WIP/DB/配置/冻结APK/唯一证据及恢复资产边界不扩展 |
| 删除后果/条件 | 若后续获准，仅此缓存子目录，旧任务失去本地构建缓存命中，需重新执行任务或从获准缓存恢复；依赖解析缓存/wrapper等父/兄弟不在目标内；不能承诺离线重建一定可用 |
| 预计收益 | 逻辑上限1.323GiB；跨目录硬链接/实际分配尚未核实，实际可释放量待确认。未与父目录或37.03GiB总盘点相加 |

不提出整gradle-home、app/build、任务目录清理批次；保留的txt-persist-codex-completion home明确排除。本轮没有其他确认可执行回收批次，不将旧全量盘点改名当本轮成果。

## 恢复与副作用

本轮构建0、设备/ADB命令0、APK复制0、既有资产删除/移动/压缩/迁移0、进程停止0。新入口没有写资产或恢复权限；恢复合同原样保留。两个自动化未恢复，保持PAUSED。产品63联合FAIL及已核恢复不被本次verify PASS覆盖。无Git暂存/提交/push/发布、外部应用消息或云部署操作。

收尾分类：脚本/角色/模板为必要交付；小型原始日志为本轮唯一证据，保留；原冻结/APK/设备恢复档案为既有恢复资产，仅引用不复制。没有新生成可清理的工程、缓存或临时构建目录，因此没有以“收尾”名义删除既有文件。

实际新增体积在本报告末尾固化（仅本轮文件逻辑增量，区别磁盘可用量波动）。预检E可用97,887,354,880字节；不把其他进程引发的空间变化归因于本任务。

## 交付状态与下一步

流程入口修正及本批针对性离线验证完成；完整编译、模拟器/整链验收未运行，产品分母未知。后续派发直接使用模板，普通实施/返修交差量、复用59唯一工作区，原冻结可复用时走只读入口。产品下一步仍为63的ABC本地短路径修复，未在本存储任务实施。

回收候选等待用户另行确认；输入变化/所有权冲突/原冻结verify FAIL/新构建同SHA合同冲突时停止依赖动作，由Root形成具体修复或决策，不自动建第二环境、改历史冻结或重试设备。

本轮10个直接交付/修改文件合计正逻辑增量 **33,680字节（32.89KiB）**，全部位于D活动仓库；E新增资产0字节。未按磁盘簇分配量宣称收益。角色TOML解析通过；协作检查17文件/0错误/1条既有入口长度提醒，未为治理清空历史。Git diff-check另提示current既有EOF空行，原尾部保持。
