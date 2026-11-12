# RDS-NEXT-v2 Root 进展核验（2026-11-06）

任务：RDS-ROOT-REVIEW-20261106-v1。结论：PARTIAL_ACCEPTANCE_REQUIRES_SEARCH_TTS_AND_DATA_CLOSURE。本轮按用户“再看看情况”核实际 diff、APK SHA、已有测试 XML/原输出、原设备 XML/logcat、备份 ZIP 和 Trae 02:25 增量复核。未操作手机、未构建/重跑测试、未发送外部消息或恢复监控。

## 实核结果

- 当前实际 APK：`E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android/app/build/outputs/apk/debug/app-debug.apk`，53,900,372B，Root 复算 SHA256 `f684d8cae38996fcb688723287abd750f4dc1f252d450e50cb20e3cb2c00ae47`，与 NEXT-v2 的最终版本一致。原 c396ebe1 字节未保全的限制继续披露，不能由相同大小推断旧包身份。
- 实际改动：D 树 PagedTxtReaderHost.kt 增 ON_RESUME 重锚及生产判定函数；新增 PagedTxtReaderResumeReanchorDecisionTest.kt。读取已有 targeted-tests-result/assemble-result 与日志均 rc=0/BUILD SUCCESSFUL。candidate 测试 XML：ResumeReanchorDecision 6 项、PagedPositionReport 6 项，均 failures=errors=0。该测试是决策层回归，不证明所有生命周期调度。
- 同输入设备原证：`evidence/R3/r3-p1.xml`、after-home、home35s、reopen、cold-reopen 均显示测试 MD 的 4.4%；home-p0 显示 0.0%；TXT home 为 16.2%。`r3-logcat-full.log` 可见 absoluteOffset=192 持久化、savedPlainOffset=192、open target=192 及新 PID 重开链。原失败 offset=192 被清零记录保留。**限定接受该手机、该构建、这些输入的 MD 修复复验，不再要求重做同一证据。**
- installed SHA 对照与设备最终恢复沿用实施记录和 Trae 相应核验；本轮未连接设备重新采集。日志无 FATAL 的结论只限已捕获窗口。
- ZIP 实读 `evidence/R3/cra-backup-r4.zip`：3 个条目（backup.json、MD/TXT 正文）；books=2、progress=2、highlights=1、notes=0、inspirations=0。支持“有正文的 ZIP 导出及版本感知合并路径已验证”的有限结论；不能称笔记/独立灵感恢复已验，不能以保留本地较新数据代替缺失数据/正文真正恢复的对照。
- Trae 最新实质复核是 02:25；之后无变化日志不是新增验收。ZCode 名为 TRAEREVIEW 的 ready 是 ZCode 的执行回执，不是 Trae 独立结论。

## 八项现态（替代旧的未派发状态）

| 卡 | Root 当前判断 | 剩余工作 |
|---|---|---|
| R1 源码/构建/APK绑定 | 本次最终构建限定闭合；旧字节缺失限制保留 | 保持最终实际路径/版本清楚，禁止再覆盖唯一接受包后只留 SHA |
| R2 MD修复与定向回归 | 定向交付已核，有限接受 | 不宣称6项门控测试覆盖所有生命周期；对新风险才加验证 |
| R3 同输入手机复验 | 本轮范围接受 | 不重跑已充分的 home/back/冷重启/书首负例/TXT 对照 |
| R4 数据恢复 | 部分完成 | 补非空笔记/独立灵感及缺失数据/正文恢复对照，优先隔离测试库；不 pm clear、不碰真实书库 |
| R5 删除源书后保全 | 对象混用，需按对象补证 | 分别列书内 notes/highlights 和独立 inspirations，验证实际 inspiration 保留与来源失效提示 |
| R6 关键交互 | 未闭合，搜索新红、TTS未成功启动 | 改诊断方法后查事件链、导航和播放初始化；不再按坐标盲点 |
| R7 功能范围裁决 | 部分裁决见下 | 其余S3逐条按Android承诺归类，不直接当新开发清单 |
| R8 最终复核/退出 | 尚不能通过 | 业务缺口及下述文档/ready一致性完成后一次增量复核 |

## Root 现在裁定的语义

1. **删除整本资料**：现有 BookRepository.applyDeletionLocked 连带软删书内 notes/highlights，与“删除整本资料”的明示范围一致，不把此行为直接判为数据丢失缺陷。独立灵感是另一实体，代码不在该删除链内；InspirationEntities 的 SET_NULL 是硬删除外键策略，不能把它套给书内高亮或软删除。原 Z5 灵感保留与 R5 高亮消失不能互相证伪，撤回“两个结果必然冲突”的说法。R5 仍需对独立灵感给最终证据。
2. **MD显示页首百分比**：允许该分页策略按页首显示，但不得借此解释实际 offset 被0覆盖或无依据的可见位置倒退。本次按192原输入已通过的结果沿用，12.8→8.4不是精确恢复的单独凭证。
3. **TTS入口**：控制放在通知栏本身不构成缺陷；点击听书必须有明确运行/失败反馈，可用的暂停/继续入口须真的有效。通知权限不足是否导致启动失败须用权限查询和初始化/回调原证定位，不能由日志没命中 POST_NOTIFICATIONS 推断 granted=false。`onSynthesizeText` 零命中只证明本日志无该标签，不独立证明所有引擎无音频输出。
4. **搜索新红**：重开为 RDS-102 间歇功能失败待因果定位，撤回“未复现因此已关闭”的结论；已有成功与失败记录并存。原点击控件/入口和时序需核清，再决定产品还是操作工具原因，不预设Compose丢事件。

## 下一轮具体收口

- ZCode先定位搜索：同一测试标记固定输入，确认控件 bounds/点击事件是否到达生产 onClick、locator解析结果、导航事件是否发出及前台最终页面。保留首次失败，命中真实根因才最小修复并定向回归；不再增加一组随机坐标。
- 同一设备owner串行定位TTS：记录实际通知权限、听书请求/服务初始化/引擎回调/媒体状态及UI，验证真实暂停/继续。普通权限交互已有零接触授权，自行用脚本完成；权限确实无法处理则留具体原证，不要求用户碰手机。
- 数据恢复及独立灵感补证按R4/R5做，不从头重跑全部冒烟。R7其余需求分类可由Trae先只读收集，与设备工作独立。
- 文档一次性纠正：REPAIR-DIAGNOSIS/IMPLEMENTATION 原错误降级与 DESIGN_SCOPE 段须明确作废/指向最新；不得继续称它们已同步。`ready/Z6.json.finalPackage.sha256` 仍为旧 `9258ba…`，Root 实算总包为 `826196ea98dca0ef18c71e28ab82787e2478fa420475059e1f2ec7dde4d3c6b5`；下一次最终交付一次性刷新真实指纹与状态字段，历史放历史字段，不仅靠addendum覆盖。
- 保留报告归档失误与SAF原证改写的追溯限制。脱敏副本保留中性测试素材名允许，无须为措辞全替换测试名。
- Trae只核变化一次，缺结果则给出具体待办并结束；不再循环追加“无变化”记录。无法发送就一次落盘说明，不反复找无权限通道。

current/state由Root本轮同步到已交付、部分接受、仍有缺陷的真实状态。保持自动化暂停和用户手动分发；没有发送新任务。本阶段尚未退出，不能宣称全产品完成。
