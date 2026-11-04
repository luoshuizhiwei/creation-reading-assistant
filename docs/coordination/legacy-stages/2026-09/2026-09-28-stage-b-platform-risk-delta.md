# 阶段 B — S14–S20 当前风险增量核对（2026-09-28，只读）

- 仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`｜分支 `codex/recover-worktree-20260920`｜本轮零源码/测试/账本/handoff 改动，未运行 Gradle/ADB/MuMu；唯一写入 = 本报告。
- 输入：`docs/handoff/current.md`、[任务账本](../../decisions/2026-09/2026-09-22-current-task-ledger.md)（含 09-27 增量）、[收口计划](../../../plans/android/2026-09-22-android-recovery-convergence-plan.md)、[S14 MuMu Room 验收](../../../qa/2026-09/2026-09-27-s14-mumu-room-rollback-acceptance.md)（含 Codex §8 复核）等既有证据；仅对旧结论冲突/证据缺口做当前源码定向核对。
- 口径：不重复已有测试；历史数字不当作本轮结果；「0 项 instrumentation 不是通过证据」。

## 1. 逐切片一行结论

| ID | 现役用户能力 | 代码达到 | 验证达到 | 仍缺证据 | 故障 vs 可延期 | 阻挡恢复候选 | 下一项最小行动 |
|---|---|---|---|---|---|---|---|
| S14 | 备份导出/导入、WebDAV、LAN 配对、Legado 进度导入 | 已收口：`feature/sync/` 七个生产文件齐全，`JsonBridge.kt` 含 `library_source_refs`/`reader_text_rules`/`reader_text_corrections` 三表打包，另有 `LocalZipBackup.kt`+JVM 测试 | 定向 JVM（WebDavBackupTest/JsonBridgeTest 等，09-23 账本 41/41）；MuMu qahost `LocalExportRoomRollbackTest` **2/2**（09-27，仅内存 Room 事务断言） | 真实 WebDAV 网络、正式持久库导入/回滚、跨设备 SAF URI、实体机 ROM（MuMu 报告 §5 明列；qahost 1.14GB 库仅为容量观察，未诊断） | 无现役已知产品故障；上述为验证缺口 | 不阻挡（数据安全范围，验证缺口需排期而非返工） | 设备窗口在 qahost/隔离包上补一条真实网络或持久库导入路径的最小仪器证据，Codex 复核 |
| S15 | 时长单位切换、完成态 pill、滑杆 | 代码轴在：`DurationPrefs.kt`/`DurationDisplay.kt`/`CraSlider.kt`；JVM `DurationPrefsTest`/`DurationUnitTest`/`CraSliderTest`（当前 8 @Test）+ androidTest `DurationUnitToggleEntryTest`(10)/`DurationPillGestureTest`(7)/`CraSliderRtlRenderTest` | JVM 定向（CraSliderTest 8/8 的 XML 已被 09-24 03:01 定向运行覆盖，仅报告口径）；D 人工设备报告 PARTIAL（09-22）；androidTest 三类 **0 运行结果** | >=24h 显示、完读 pill、TalkBack 播报、F-2 完读卡 vs 详情页时长取数口径 | F-2 是口径待决（调查项），非确证故障；其余为验证缺口 | 不阻挡 | 复用已打通的 MuMu qahost 通道跑三类 duration/slider instrumentation，先于 F-2 口径裁决 |
| S16 | 首页/灵感/统计/历史/搜索页 | 页面齐全：`ui/screen/` 下 home/homearchive/inspiration/profile/readinghistory/search/shelf/stats | JVM 结构自检（Inspiration/StatsFileStructureTest）+ androidTest Compose 两类存在；08-22 真机 5/5+4/4 属旧 APK 历史证据 | 当前未提交工作树状态的页面级设备验证；与 S03/S05/S15/S17 混合 hunk 的归属归类 | 无新增已知故障（heatmap 等历史修复已在源码） | 不阻挡 | 只做 hunk 归类登记（S03/S05/S15/S17 各归其主），不单开验收轮 |
| S17 | 应用壳/导航/主题/公共组件 | `App.kt`（Hilt+早挂异常处理器）、`MainActivity.kt:227` `dispatchGenericMotionEvent`（09-22 滚轮修复仍在）、`AppNavigation.kt`/`AdaptiveNavigationPolicy.kt` 齐全 | 编译/Lint 门禁随各轮共享构建通过；主题链无独立验收 | `SettingsStore.kt:383` `else -> MODE_NIGHT_AUTO` 仍在（账本 S06 行登记的旧结论冲突未消）；启动主题全链无定向断言 | 启动主题映射是**待产品裁决**，非确证故障 | 不阻挡代码，但公共组件被多方依赖，收口必须先行 | Codex 对「跟随系统→AUTO」语义裁决；裁决后由 S17 独占集成者补一条 JVM 定向断言 |
| S18 | 启动/滚动性能基线 | 资产在：`app/src/benchmark`（BenchmarkSeedActivity）、`benchmarkRelease`、`release/generated/baselineProfiles`、JVM `ChapterPaginatorBenchmark` | 历史 benchmark/profile 产物（快照登记 benchmark 2+profile 1）不可与当前混判；当前环境不可用，保持未验证（约定不改用 MuMu） | 可复现环境上的宏基准运行结果 | 无故障；属环境阻塞 | 不阻挡 | 维持未验证登记；仅在 D 阶段统一验收时有环境再跑，不为其单开排期 |
| S19 | 安装脚本与诊断入口 | 正式脚本在：`android/scripts/install_with_confirm.ps1`+contract tests（F2 历史 84 项契约）、`generate_large_txt_fixture.ps1`、`run-migration-test.sh`；诊断脚本（dump_ui.py/tap_usb_install_confirm.py 等）并存 | F2 守卫为历史通过证据；脚本无本轮复跑（未运行 Gradle/ADB，遵守边界） | 仓库根 **32 个** `dump*.xml`/`window_dump.xml` + `dump_secrets.xml`/`dump_secrets2.xml` 去向未定（D 窗口已登记 dump 系列含真实藏书目录名） | 临时 XML 是**资产处置决策**，不是产品故障；正式脚本保留 | 不阻挡构建，但敏感残留阻挡「干净工作树」目标 | Codex 独占决定 quarantine/删除/迁移清单；不自动删除 |
| S20 | （非用户能力）工作区资产治理 | 桌面端未提交改动仍在（`src/**` 约 10+ 文件、`scripts/*.mjs`、`scripts/visual-evidence/*`、`.codex/config.toml`）；`archives/frozen-mobile` 完好 | 已分类保全（阶段 A 快照），未执行删除；本轮确认残留与登记一致 | Android 提交不夹带桌面/Agent 资产的边界守卫尚无自动检查 | 资产处置决策，非产品故障 | 不阻挡 | 随 S19 一并由 Codex 出处置清单；提交阶段加一条「Android 提交不含 src/electron」人工核对项 |

## 2. 交总指挥立即排期（≤3，含建议所有权）

1. **S14 设备证据扩面**（数据安全范围，唯一带「验证缺口跨设备数据语义」的切片）：在 qahost 隔离包上补真实 WebDAV 或正式持久库导入的最小仪器用例。建议所有权：设备验收窗口（现役 A，用户已授权 MuMu）执行，Codex 复核；不得在用户真实书库上触发。
2. **仓库根 dump XML 残留处置**（32 个 + `dump_secrets*.xml`，已知含真实藏书目录名，隐私敏感且去向未定）：建议所有权：Codex 独占（S19/S20 范围），先列 quarantine 清单再决定删除/迁移，未经授权不自动清理。
3. **S17 启动主题 `else -> MODE_NIGHT_AUTO` 裁决**（旧账本登记的未消冲突，且 S17 公共依赖不得被任一窗口擅自修改）：建议所有权：Codex 裁决语义 → S17 独占集成者实现+JVM 定向断言；其余窗口只读。

## 3. 边界声明

- 本轮未运行任何构建/设备命令；所有「已运行」表述均引自账本/qa 报告的可回读证据，未重新执行。
- S14 的 2/2 仅代表内存 Room 事务断言（MuMu 报告 §5/§8 收窄口径），不写为完整备份/网络/跨设备恢复通过。
- 未修改窗口 A 的阅读器文件、窗口 B 的报告、共享账本与 handoff；S17 公共组件零改动。
