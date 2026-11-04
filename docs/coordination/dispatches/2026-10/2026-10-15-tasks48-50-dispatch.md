# 三线修复派发

## 任务编号/版本
任务48-v1、49-v1、50-v1；2026-10-15 20:11 +08:00。提示词已准备，待用户转交，未向外部应用自动发送。

## 输入指纹
共同基线为[Root v9报告](2026-10-15-root-stage-runtime-reader-v9.md)，工作根 E:/creation-reading-assistant/root-runtime-device-20261015。freeze-v9/attempt1/freeze.json SHA b7583c782fbf4c3b86bf0f5fadf066ab51d008e14892f26030486482c90ba8b3；source manifest SHA 505c35235e2ecafc8cc248870753e2ca3c0b06e9adffc02e74debb658ab18dc5。各执行者重核相关文件SHA，不改冻结基线。

## 所有权与改动
- 48：E:/creation-reading-assistant/task48-temporary-return-freeze-v1；独立Android候选main业务路径和相应JVM测试。修A2两时间列变化，不改androidTest或严格消费者。
- 49：E:/creation-reading-assistant/task49-inner-restore-cleanup-v1；独立host/runtime恢复链及Python测试。修新增sync/webdav配置残留并保全预存文件，不改Android或验收比较合同。
- 50：E:/creation-reading-assistant/task50-remeasure-probe-repair-v1；仅ReaderClampRealViewportRemeasureProbeTest.kt及本任务新增专属测试。修异步恢复读回与可判定实验条件；main只读，发现产品问题交Root处理。
- 三线立即并行，公共文档、合流、最终构建、设备由Root唯一负责。推荐48/50用DeepSeek，49用GLM5.3Flash；Gemini3.8仅作用户侧确有入口时的替换选项，未核其供应商能力和价格。

## 环境
三线不使用设备、不启动MuMu、不构建最终APK，仅定向离线验证，所有构建/缓存/日志留E。D HEAD e39a78da9c62f02614207cffb36969a32492eaea，index空；现存Python/管理器进程命令不匹配本工作根，未终止其他进程。

## 执行与结果
已核入口、编号索引、v9报告和相关源码路径；未见48–50同名交付目录。三项尚未实施，状态待派发。

## 原始证据
各自引用v9报告中的ABC原始行、D实验及恢复失败证据；交付REPORT.md、输入/输出SHA清单、最小patch和原始定向日志，不将离线模拟标成设备证据。

## 未覆盖项
合流构建、身份绑定与限定MuMu验收等待Codex核交付；不重复充分且输入未变的短末项/SAF验证，不扩桌面、正式书库或X整机中断。

## 恢复与副作用
本轮仅维护调度文档。执行者不修改共享冻结基线、其他任务目录和全局交接；无D产品回灌、暂存、提交、push或发布授权。

## 交付状态与下一步
用户只需转交完整提示词，执行者自行定位、实施、验证和报告，无需用户搬文件或组装脚本。完成后Codex核验并决定唯一合流/设备批次。执行者最终总结最下面单独写实际“这是任务48-v1。”等编号。

阶段方向复核：保留原生Android稳定阅读、笔记回源、轻量导出方向，先收口三个真实未过边界。三线不扩功能，退出点为独立补丁与定向证据交付，非联合验收通过。
