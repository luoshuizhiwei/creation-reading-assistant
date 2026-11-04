# TXT v2成果核查与Codex补齐

## 任务编号/版本

CRA-TXT-PERSIST-V2-CODEX-COMPLETION-REVIEW-v1；2026-10-06。用户要求核验A/B实际完成情况，缺项由Codex补齐。第一批源码/JVM与验收工件范围；第二批设备仍需第一批通过后绑定执行。

## 输入指纹

活动恢复仓库HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空。原产品v1清单SHA 1d287eeeb99711da84a2c3706351df5f68462dcbb6cb2a7bdf4fa705d67ba5e6；原B-v2工件清单逐项无漂移。本轮候选唯一根为本报告目录，a/android为产品候选，b为验收工件，缓存为gradle-home。原A/B候选只读保留。

## 所有权与改动

Codex负责核查、E副本与唯一串行构建。实施者分别只写a的定向测试/报告和b的验收工件；文件范围互斥。a的981项清单相对原产品v1仅TxtReentryRestoreFlowTest.kt变化；生产代码不改。981与TXT-v4的980差异是local.properties。D产品、桌面/HOLD、原损坏仓库、正式书库和其他应用私有记忆库未修改。

## Standards

原A修改gradle.properties而未列入三文件清单，768MB配置构建OOM，无最终报告和JVM XML；本轮a保留原产品配置，使用独立E缓存覆盖构建参数。原B创建物账本漏后续dump，dump与Kotlin读取端脱节；本轮补齐任务/run/step专属dump读写链和创建物原子账本。原始证据保留，未覆盖原交付。

## Spec

原A时序用例按采样次数返回落位，不能证明真实Compose时序。本轮使用同源状态与独立协程推进、冷订阅补证；真实两个快照映射不完全相同，根因未证，无猜测产品修复。原B缺新位置保存后冷读、独立退出行和完整进度行门禁；本轮补S/R/S2/退出/冷读完整行和合法EOF/零新位置映射，末项S明确为夹具输入。harvest只采JSON，检查器已改读实际scoped raw UI路径，并将宿主身份/marker作为硬门禁；混包marker及缺时间戳反例被接受。原B离线19项通过并不覆盖这些接缝缺口。

## 环境

Windows/PowerShell、JDK17、Gradle8.9、Python3.13 -B。E任务gradle-home使用2GB堆/512MB metaspace/in-process/单worker，所有构建由Codex串行执行。未调用ADB/MuMu，未构建最终APK。

## 执行与结果 / 原始证据

A：本轮定向JVM退出码0，BUILD SUCCESSFUL；新XML FlowTest13＋RestoreTest7，共20/20、0 failures/0 errors。日志a/logs/test-root-final.log SHA 71746677353c3c66acfe62983aa9e203cef978575849766ee7e6a686fdf8a5b2。摘要evidence/a-jvm-summary.json，源码核查evidence/source-review.json，精确补丁a/freeze/vs-v1.patch。未独立核定原395邻接声明。

B：原清单逐项一致、原编译日志19a553c2a752f95bbf5da291b97f1fa9d82d639e7d400bc494387a1b2da301fa核定。无ADB反例evidence/original-b-counterexamples.json证明混包marker放行、dump未push、缺时间戳放行。补齐后最终Kotlin定向编译退出0，BUILD SUCCESSFUL 1m15s；日志b/compile-copy/logs/compile-root-final.log SHA 1cf49ae60e0fd8d1d3acefb52126f964d74455ddabd1bad908e0b8d1ca5ec458，三份编译输入/交付源SHA逐项一致（evidence/b-final-compiled-source.json）。最终UI回调独立7/7、绑定检查器UI独立6/6通过，均为mock且无ADB。Root最终复跑普通离线CLI12/12、接缝10/10、短末项CLI6/6均退出0。短末项正例成立；缺R/S2/独立退出/冷读及旧S2时间戳五例各拒绝且driver_batch仍PASS。日志evidence/b-ordinary-root.log、b-seam-root.log、b-clamp-root.log；UI独立证据见ui-independent-probe.json和checker-ui-independent-probe.json。

## 恢复与副作用

本轮无设备副作用。原外部候选、失败/成功日志保持；D受保护产品路径前账在evidence/d-protected-source-before.json，工作树前账及入口副本同在evidence。D本轮仅允许协调报告/现态维护，无产品回灌、暂存、commit、push、发布。

## 未覆盖项

真实Compose帧/列表状态/pending观测、最终同APKMuMu旅程及原数据/DB/WAL/SHM/APK/设置/前台映射恢复尚未执行。X整机中断单点清零未验证。实体手机/MIUI不在范围。全产品分母未核。

## 交付状态与下一步

第一批A补证与B验收工件两项核查通过（2/2，范围限定源码/JVM/编译/离线工件）；不是设备验收2/2。原始v1不重复派发。第二批由唯一owner合流、冻结源码与双APK、绑定新UID/源码/driver/config/fixture/serial，逐项卸载前取证并完成限定MuMu及恢复前后账。当前不能宣称最终TXT问题解决或全产品100%。


## 下一批集成决定

保留产品v1作为生产候选，附本轮A补证；保留pending/marker保护，不因yield/人工采样宣布Compose设备根因已证。用本轮B替代旧验收准备工件，冻结 driver/runner/harvest原件保持一致。下一批仅设一个合流与限定MuMu owner：在独立E候选合入三个测试源，先核占用与输入指纹，冻结完整源码和双APK，再绑定新UID、config、fixture、driver/runner、实际serial与runId。逐项卸载前保存证据，最终逐项核对并恢复数据、DB/WAL/SHM、原APK对、系统设置和约定前台状态；任何身份/恢复不成立停止，不签通过。当前本轮未派发/执行第二批，不复跑未变且证据充分的旧合同，不回灌D产品。

2026-10-06 下一批派发前补正：仅 config/binding-fields.md 同步最终步骤/EOF/夹具S/专属dump合同，源码与driver未改，不重跑运行验证。B最终24项清单指纹 199ad65684e9b0895dbcd26f87a2168f4aa91c2f24573d1708138b67e281e112；新增合流与验收任务 CRA-TXT-PERSIST-MERGE-MUMU-ACCEPT-v1，用户复制提示词后由唯一owner执行，不自动外部发送。
