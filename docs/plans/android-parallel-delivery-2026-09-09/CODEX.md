# 发给 Codex：R1 阅读可靠性与集成

工作目录 `D:/develop/Code/Codex/creation-reading-assistant`。完整阅读本目录README/COMMON；主仓库仍有桌面改动与诊断文件，不纳入本任务。
Trae和Qoder各在独立工作树开发，按README分配文件；未经交接冻结不覆盖他们的文件。

## 本轮产品任务

1. 修复滚动TXT规则已保存但正文不替换。先读取现有ScrollReplaceTrace日志（若缺失，预约WorkBuddy诊断轮），检查真实生产分支：引擎开关off、readerMode=scroll、小TXT、流式TXT，以及组装source到正文消费/缓存更新链。
2. 不重复首轮“统一source”猜测修补；用实际失败数据验证假设。source作用域、规则指纹、组装/绑定/投影三层一致，日志不记录正文/书名/规则内容。
   本轮静态审查线索：scrollPreparedSource受txtChapters非空门控，检查未识别目录但readingUnits可读时是否直接退回原文；线索不是根因结论。
3. 保存→当前屏、滚离返回、重进、进程重启均能实际替换；禁用还原；更改长度后选区/搜索/高亮/TTS/source持久化正确。超限与不完整作用域有明确降级。
4. 收口既有行尾排版WIP：安全字距、原子数字/英文/成对标点、正常非末行对齐、大字号/窄视口；不得为强制右对齐随意拆原子单元。排版缓存版本已有改动，先核对再修改。
5. 模式切换与重启恢复不把原进度写成0；范围只修有证据的相关路径，不启动大规模引擎重写。

## 集成职责

- 全部DAO/entity/migration/schema、AppNavigation、ReaderAction、Reader宿主、共享设置与构建文件由你唯一维护。
- 接收Trae/Qoder SEAM REQUEST，给最小兼容接口，必要时带迁移测试；接口就绪通知对应agent同步明确提交，不让其整文件覆盖。
- 开发阶段定向测试；合入前冻结写入，按数据→笔记→reader顺序集成，检查所有权与冲突。
- 完整JVM、Lint、assembleDebug、compileDebugAndroidTestKotlin串行；记录真实退出码/测试数/APK SHA-256。WorkBuddy接收冻结代码SHA和工作树diff指纹，验收期间不继续改同一快照。
- 独立真机验收仍由WorkBuddy负责，允许安装脚本失败后adb install回退（见COMMON）。
- 更新本轮报告与活动文档，不把开发检查点当作PASS，不push。当前请求是准备基线和交接；执行此提示词时才启动R1产品修复。

交付 `docs/plans/android-parallel-delivery-2026-09-09/reports/codex-r1.md`，明确三方集成状态、基线差异、下一步与未完成项。
