# RDS-v1 剩余任务队列与执行合同（2026-11-05）

> 后续排期已由 [RDS-NEXT-v2 八项任务](2026-11-05-rds-next-replan-v2.md) 替代。Z1–Z6 已有交付，禁止从头重跑；本文件保留作原验收合同。

状态：`READY_FOR_MANUAL_DISPATCH`。用户手动把两份提示词分别交给 Trae 与 ZCode；本文件不重新启动自动化，不授权 commit、push、PR、发布或删除既有资产。

## 现态与裁决边界

- RDS-v1 真机走查已执行到 P13。ZCode 的 `E:/creation-reading-assistant/rds-v1-real-device-smoke/REPORT.md` 已更新为终稿 v2，v1 原件保存于 `REPORT-v1-archived.md`；`ISSUES.md` 已记录 Trae 清单的逐项采纳。
- 当前终稿的有限裁决是 `EXECUTED_COMPLETE`，含义仅为 P0–P13 走查流程执行完毕。它有明确覆盖缺口：S1=0、S2=3、S3=10，以及 P6.2/P6.4/P6.5、P8 拔耳机、P9.3/P9.4、P11.3/P11.4、P12 主体、全局搜索等 NOT_RUN。不得解释为产品验收通过。
- 已确认的 S2：RDS-101（MD 位置恢复归零，两次复现）；候选 S2：RDS-102（书内搜索命中不跳转）、RDS-103（TTS 进度和控制联动）。RDS-103 的“跨书串扰”已撤回，不得继续作为缺陷。
- 测试身份：`c49ac6cf` / bootId `7fdcf9dc-ed4a-46cb-aa77-634a2bde7c2a` / APK `c396ebe1…` / 53,899,310B。新批须重新探测 identity，不沿用旧 runId 或 bootId 作为当前事实。
- 原证、原红、REPORT-v1-archived、Trae 旁站日志与 `backup-pre-uninstall/` 全部保留。P13 设置已恢复；`/sdcard/Download` 的 5 个 9 月旧测试文件仍在，清理不在本队列授权内。

## Owner 与依赖

- **ZCode**：唯一 Android 源码、Gradle、APK、手机及修复批 owner。所有源码修改、构建和设备命令由 ZCode 串行执行。
- **Trae**：唯一独立旁站与复核 owner。只读检查产物、设备状态和原始证据；不改源码、不执行构建、不与 ZCode 同时写设备；复核结论写自己的日志。
- **Root**：current、state 与最终裁决 owner。两项交付都完成并复核后，Root 才更新全局状态并给出限定接受范围。
- 顺序：Z1 → Z2 → Z3 → Z4 → Z5 → Z6；T1 与 Z1 可并行，T2 跟在 Z2 后，T3 跟在 Z3 后，T4 跟在 Z4 后，T5 跟在 Z5/Z6 后。Trae 的复核只对已经落盘的 ready/REPORT/原证做一次，输入指纹未变不重复核验。

## ZCode 剩余任务（六项）

### Z1 — 冻结 RDS-v1 交付与待修基线

输入：`REPORT.md` v2、`REPORT-v1-archived.md`、`ISSUES.md`、`E:/creation-reading-assistant/coordination/RDS-v1-ZCODE-ISSUES.md`、Trae 观察日志、evidence/screenshots/logcat。

动作：核 v2 是否仍有互相矛盾的裁决、错误计数、把 NOT_RUN 写成 PASS 的条目、误写的 `stay_awake=7`、Download “已清空”措辞和隐私面过宽的 SAF dump；把遗留缺陷写成追加件，保留 v1/v2 原字节。列三条 S2 的复现输入、期望、实际、原始证据路径及不明确之处。检查 `ISSUES.md` 证据树里“8 问题”与实际 3+10 条的措辞冲突。

交付：`E:/creation-reading-assistant/rds-v1-real-device-smoke/REPAIR-BASELINE.md` 与 `ready/Z1.json`，含上述文件 SHA256、设备与 APK 身份、逐项检查结论。ready 只是交付信号。

验收：每项有文件与证据指针；原文失败证据可定位；不覆盖 v1/v2；没有把已有缺陷写为通过。失败即停并写具体差异。

### Z2 — 先补候选问题的判别证据，再决定修复

输入：Z1 的基线、RDS-102/103 证据、P7/P8 原始 XML/logcat、D 仓库当前 Android 源码。

动作：对 RDS-102 区分“点击坐标不在热区/测试操作误差”与“生产点击回调或导航确实失效”：记录实际点击控件边界与命中位置，检查 `SearchScreen` 的命中项 onClick/导航调用，必要时用同一台设备做一次只读 UI 定位与最小复现。对 RDS-103 做不少于 60 秒的 TTS 观察，并明确介质音量、段落边界、播放状态、进度回写及通知控制路径；若合同原设计只要求音频播放而不要求进度联动，指出设计依据，不擅自改语义。

交付：`REPAIR-DIAGNOSIS.md`、`ready/Z2.json`、新的原始 logcat/XML/截图、对应源码路径与行号、每项 `CONFIRMED_DEFECT` 或 `OBSERVATION_LIMIT` 或 `DESIGN_SCOPE` 判定。

验收：结论能由原 rc/UI/logcat 与源码调用链双向支持；不以单次点击或 12 秒观察定级；不为凑结果放宽断言。确认是真实产品缺陷才进入 Z3。

### Z3 — 修复已确认缺陷并写定向回归

输入：Z2 判为 `CONFIRMED_DEFECT` 的条目，以及两次复现成立的 RDS-101。

动作：每缺陷只改必需源码，修根因；为 MD 位置保存/回放、搜索命中导航、TTS 联动分别补最小且真实调用生产路径的 JVM 或仪器测试。RDS-101 覆盖 home 与 back 两种退出，对照 TXT/EPUB 的既有保存路径。禁止用测试替身或字符串检查冒充行为验证；禁止通过改断言、阈值或默认值让测试变绿。

交付：D 仓库中互斥修改文件、实际 patch、编译/定向测试原 rc 与 stdout、红→绿证据、`REPAIR-IMPLEMENTATION.md`、`ready/Z3.json`。

验收：修复前后使用相同复现输入；每个已确认缺陷有至少一项失败后成功的原始测试记录；产品 APK 指纹须重新计算并记录；不得声称修复候选问题或 NOT_RUN 项。无缺陷经 Z2 确认时保留 RDS-101 修复并写明另两项未修原因。

### Z4 — 同一真机上的复现批与恢复

输入：Z3 实际构建出的新 APK、三本中性测试书、P0.3 基线、RDS-v1 合同；新 runId、fresh `adb devices`/bootId/user/当前焦点/包与设置基线。

动作：只复测 Z3 已修条目及其直接回归边界；保留原红对照。使用 `adb -s <本轮 serial>`，所有点击由脚本或 ADB 模拟，用户不碰手机；保持供电与唤醒，记录 `stay_on_while_plugged_in` 原值。逐条留下 stdout、UI XML、截图、分段 logcat 和时间戳。批次结束恢复本轮测试数据、APK、设置、旋转、媒体音量与设备身份，输出最终 identity 比对；不清理 5 个旧测试文件。

交付：`REPAIR-DEVICE-VERIFICATION.md`、`ready/Z4.json`、按 runId 分目录的原证、业务判定与恢复判定分开写。

验收：新 APK 的磁盘/安装/证据 SHA 三方一致；每个修复项有同输入 red→green 或明确 red→仍红；恢复不绿时不得写业务通过；0 项 instrumentation 不算通过。同项连续三次失败保留原红并停止该方法。

### Z5 — 补齐原清单的可执行覆盖缺口

输入：RDS-v1 合同、终稿 v2 的 NOT_RUN 清单、Z4 恢复后的设备。

动作：按安全顺序补 P6.2 摘录生成与精确回源、P6.4 删除测试书后的摘录保全、P6.5 笔记导出、P9.3 亮度/护眼、P9.4 设置搜索、P11.3 手势/三键导航、P11.4 连续操作流畅度、P12 本地 ZIP 备份恢复演练、全局搜索命中跳转、P8 耳机拔出（可脚本断连才执行）。每项按原合同给出 PASS/FAIL/BLOCKED/NOT_RUN/NOT_APPLICABLE 与证据；只使用测试书和测试数据，不配真实 WebDAV 账号，不触碰正式书库。

交付：`COVERAGE-GAP-VERIFICATION.md`、`ready/Z5.json`、每项原始证据。

验收：未执行的项明确写原因，禁用截图存在替代行为判定；数据安全项有恢复前后对照；发生 S1 即停该项、保存证据并继续安全的独立项。

### Z6 — 修复批交付收口

输入：Z1–Z5 全部 ready、报告、原证、Trae T1–T5 复核结论。

动作：汇总每条问题的最终状态、修复文件、测试、设备结果、恢复结果、未覆盖项与残余风险；纠正 REPORT/ISSUES 中受新结果影响的事实，保留旧版原件；给 Root 一份限定接受建议，明确真机范围、模拟器历史范围、原红保留范围。

交付：`E:/creation-reading-assistant/rds-v1-real-device-smoke/FINAL-ROOT-PACKAGE.md`、`ready/Z6.json`、清单与 SHA256；停下等 Root 裁决，不自行修下一轮。

验收：报告不出现“全产品完成”或无分母百分比；没有把旧 V0/MuMu 证据升级为真机通过；current/state 由 Root 更新，ZCode 不写。

## Trae 剩余任务（五项）

### T1 — 复核 Z1 冻结件

只读核 v1/v2 原件、ISSUES、Trae 原清单、Z1 的 SHA 和逐项结论；输出是否有裁决冲突、计数错误、证据缺失或隐私面过宽，写入 `E:/creation-reading-assistant/coordination/rds-v1-trae-observation.md` 的新章节 `T1`。不发设备命令，不改 ZCode 文件。

### T2 — 复核 Z2 判别证据

只读核 RDS-102 的控件边界/点击/导航证据、RDS-103 的 ≥60 秒观察和播放状态证据，以及对应源码调用链；对每个候选分别写“证据支持 / 证据不足 / 属设计范围”，标出任何从现象跳到根因的推断，写 `T2` 章节。

### T3 — 复核 Z3 代码与红绿证据

只读核实际 diff、定向测试源码、原 rc/stdout、APK 指纹和失败证据；确认测试调用生产路径，没有放宽断言或隐藏异常；逐缺陷写 `T3` 结论。

### T4 — 旁站 Z4 真机复现批

每 5 分钟只读观察新证据和 ADB 状态；记录 fresh identity、APK 安装指纹、修复项复现、logcat FATAL/ANR、基线恢复及最终 identity。用户不碰手机；Trae 不改设备、不抢 ZCode owner。若再次安全锁屏且常规唤醒无法恢复，记录 BLOCKED 和受影响项，不索取凭据。写 `T4` 章节。

### T5 — 复核 Z5/Z6 总交付并交 Root

逐项核对 NOT_RUN 补测证据、业务与恢复判定、隐私裁剪、新旧报告差异、S1/S2/S3 计数、残余风险和限定接受建议；写 `T5` 章节及最终三方差异表。把复核结论整理成一条消息，确认是 ZCode 的正确会话后点“立即”发送；无法确认目标会话则落盘并报告发送失败。Trae 不裁决最终通过，不写 current/state。

## 通用停止条件与资产边界

- 任一 ready 未出现时，只核已落盘交付；不进入桌面查进度、不发“继续”、不重贴提示词、不盲重试。输入 processed 指纹未变不重复核验。
- ZCode 认证或权限请求、模型不可用、工作区冲突、owner 争用、设备断连两次、电量低于 15% 或需新卸载授权时，停对应操作并报告。
- 禁止 `pm clear`、恢复出厂、盲恢复、正式书库/个人文件读写、Git 暂存提交 push PR 发布、资产删移、D 产品回灌、私有记忆写入。旧 205 红、RDS-v1 原红和 v1 报告保留。
- 用户 stop/Esc 后停止新增输入。`android-zcode` 自动化保持暂停；手动交接不等于恢复自动化。
- 只有 Root 在 Z1–Z6 与 T1–T5 完成后，才可更新 current/state 并发布有限裁决；文档整理、测试运行或交付 ready 本身都不构成验收通过。
