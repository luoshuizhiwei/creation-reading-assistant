# 两项v2收尾：Codex核查

## 任务编号/版本
CRA-RESTORE-MANIFEST-V2-CODEX-REVIEW-v1，2026-10-05。验收对象：恢复收尾v2与集成清单v2。

## 输入指纹
恢复仓库HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存空。分页候选与final-v3均为已有冻结输入，没有新构建。本报告引用[上一轮核查](2026-10-04-md-paged-and-manifest-codex-review.md)，未变功能证据复用。

## 所有权与改动
Root只读核查与文档收尾；补本报告、current、TODO与skill日志。未修改源码、原报告、原工件、Git或设备设置。独立只读审查机械清单；Root审恢复与关键语义差异。

## 环境
Windows；MuMu `127.0.0.1:16416`。只读查询夜间及成功pm list；不是实体手机验收。未构建或重跑功能。

## 执行与结果
### Standards
恢复操作符合窄授权：唯一写入night yes，原证据与源码保留。五件恢复输入SHA全部匹配报告。HEAD/暂存未变化。
### Spec
**恢复收尾可采纳**：最早diag基线yes；新前账no，精确写入yes退出0，后账yes。七设置与diag基线完全相同，原宿主71文件完整集合一致且前后不变，原APK2/2一致。Root当前查询night仍yes，成功pm list未列分页包。原批失败状态保留，111JVM/四分页+一滚动5/5复用。旧恢复缺口已补，不追查跨任务历史切换作为本次阻塞。

**清单语义结论不可直接用于集成**：原报告与其自己diff/实际源文件矛盾。
1. ProfileRoute.kt：候选final-v3包含ZipRestoreInterruptedBanner、zipRestoreState及refresh调用；D没有。报告反写成D独有。cand2dw显示删除候选块，非新增D块。所谓“把D横幅叠回候选”的任务没有事实基础。
2. ProfileViewModel.kt：候选含_zipRestoreState/refreshZipRestoreState及两处刷新；D没有。报告同样读反。不能按其真合并建议执行。
3. ReaderProgressActions.kt：真实D独有变动不仅atChapterEnd与txtScrollChapterTargetOffset，还包括persistCurrentProgress的plainListState/readingUnits参数、livePlainOffset实时TXT保存、chapterForLocator反推ci。候选有markdownVisibleSourceOffset及MD canonical保存分支。报告漏了这些接缝，称改动在不同函数不能证明可组合；直接取任一版本会丢另一侧行为。
4. ReaderProgressEffects.kt：报告建议采候选却仍称D门控需人工确认，未形成终局决策。全树12项计数、测试名集合包含或同名函数不能代替逐hunk语义。报告“7单向+4合并”的文字与列举8单向+4项也不一致。

独立机械核查已完成：CSV980行分类闭合，181no-op当前双侧SHA逐行匹配；4CRLF-only当前SHA/行尾匹配；49个顶层日志实存集与证据一致且未进入清单；7个final-v3输入SHA匹配，当前Android M198/D3/未跟踪93。六个依赖在v1原CSV已同值，v2仅重分类，当前双侧SHA相同，不是新漂移。机械清单可采纳。不得将“分析表12行已写”计为12项语义验收通过。

## 原始证据
- [恢复v2报告](../../dispatches/2026-10/2026-10-04-next-md-paged-restore-close-v2.md)、[清单v2报告](../../dispatches/2026-10/2026-10-04-next-joint-integration-manifest-v2.md)，保留原文。
- `E:/creation-reading-assistant/next-round-md-paged-restore-close-v2/evidence/`：readback_before/after、restore_night_mode；原始命令退出/输出在JSON中。
- `E:/creation-reading-assistant/next-round-joint-integration-manifest-v2/evidence/diffs/`：ProfileRoute、ProfileViewModel、ReaderProgressActions、ReaderProgressEffects的cand2dw。Root并已实读final-v3与D对应文件核归属。

## 恢复与副作用
恢复窗口已释放MuMu；Root只读。原数据/APK/七设置账匹配，没有源码集成、安装或Git操作。收尾日志的grep||echo仅作辅助；包缺失由Root成功pm list空结果补核，不用单一pm path rc1证明。

## 未覆盖项
未全量复审其余八个语义项，当前关键反例足以拒绝直接合并。手机窄布局、实体设备/MIUI及发布未新增覆盖。机械清单并不授权源码应用。

## 交付状态与下一步
本轮整项验收 **1/2（50%，分母仅两项收尾）**：恢复已验，清单语义待修。分页限定功能与恢复均闭合，不重新派分页定位/功能验收。
下一对清单只修真正决策错误：重新明确diff方向，纠正Profile两处，补ReaderProgressActions全部接缝及调用方/测试依赖，落实Effects门控判断；已有机械结论复用。该工作可与不改源码的精确测试/调用方审查并行，公共候选合流由单一owner随后执行。禁止回灌D、整树覆盖或Git操作。全产品分母未定。
