# Root 联合候选：修复、构建与限定 MuMu 验收

## 任务编号/版本

CRA-JOINT-FINAL-v3；2026-10-04 用户要求继续，由 Codex Root 集成与独占设备验证。此报告替代上轮“交付3/3、验收0/3”的当前状态；旧失败报告和工件保留。

## 输入指纹

- 恢复仓库 HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区为空，结束再次只读核实。
- [最终 E 候选](<E:/creation-reading-assistant/next-round-joint-final-v3>)：sync-md-join-v1 的886输入文件，加回滚v4完整九路径依赖和仪器测试v2三路径；不只拷 delta。原树与合流清单见 [base-sha](<E:/creation-reading-assistant/next-round-joint-final-v3/freeze/base-sha.json>)、[integrated-inputs](<E:/creation-reading-assistant/next-round-joint-final-v3/freeze/integrated-inputs.json>)。
- 仅 E 候选 qahost 后缀单hunk改为 `.qahostjoint20261004v3`，生产 App 透传。D 产品源码、原仓库、HOLD、桌面资产、正式书库均未写入；未暂存、提交或push。
- 冻结宿主 SHA256 `2a1473b47c26998398a57b1fa34a55c5e30aaff302a024493136e4ad0711a990`；测试 `98fc9cf4e44fb9e1b0437a18e82559bfe21fabe2b721897ff170b7325979c0d7`。第二批真实安装读回与这对冻结 SHA 相同。

## 所有权与改动

三项独立修复经 Root 核查后接受限定源码/离线范围：

1. [回滚v4](../../coordination/dispatches/2026-10/2026-10-04-next-rollback-wip-reconcile-v4.md)：普通parse完整生命周期持有同一交换闸；闸内重新判断复用；replacement调用显式闸内parse避免非重入锁死。新增两条有界故障/受控并发反例，149/149绿，原147用例保留；fresh-source、完整backup、复制验证及主异常/suppressed保护保留。
2. [仪器v2](../../coordination/dispatches/2026-10/2026-10-04-next-joint-acceptance-tests-v2.md)：解压真实ZIP chapter校验V2字节；按真实表名/列清理并保留异常。MD、SE4交付原字节保留，编译和Root独立ZIP/SQLite合同通过。
3. [驱动v4](../../coordination/dispatches/2026-10/2026-10-04-next-joint-mumu-driver-v4-package-probe.md)：raw/sidecar/真实退出与run_id、配置指纹严格绑定，恢复必须有明确未占用基线。实际MuMu的空输出pm-path rc1只在成功精确包列表证明不存在时接受；邻近包、诊断输出、未知结果仍拒绝。Root独立55/55离线通过。

Root独占最终构建、两批设备操作、真实UI和文档。Root UI辅助模块的自动路线不作为独立自动验收能力通过；最终MD批采用Root逐步观察XML/截图后操作，callback等待Root证据文件，仪器断言读取真实数据。

## 环境

JDK17、SDK35、Gradle8.9离线；E盘候选与日志，复用已空闲的任务Gradle缓存。MuMu明确serial `127.0.0.1:16416`，别名emulator-5556为同VM，设备伪报型号不作为实体手机证据。1080×1920竖屏、density280无override；本轮未改系统设置。此密度下书架为宽布局，不宣称手机窄布局验收。

## 执行与结果

- 构建成功，20个受影响JVM套件 **303/303**，零失败、错误、跳过；宿主和仪器APK均构建成功。[构建日志](<E:/creation-reading-assistant/next-round-joint-final-v3/logs/joint-build.log>)、[固定XML](<E:/creation-reading-assistant/next-round-joint-final-v3/freeze/jvm-xml>)及 [机械核验](<E:/creation-reading-assistant/next-round-joint-final-v3/freeze/build-verification.json>)。
- [第一批 core](<E:/creation-reading-assistant/next-round-joint-final-v3/device-core/summary.json>)：EPUB同URI读取新V2/不可读源保旧副本2项，SE4延迟消费/不重复跳转/外部source优先3项，**5/5**普通仪器方法通过，恢复通过。第一批驱动执行过安装SHA检查，但未另存完整postinstall原输出；第二批同对APK的安装读回原输出已保存，不能冒充第一批单独留存。
- [最终MD批 r4](<E:/creation-reading-assistant/next-round-joint-final-v3/device-md-r4/summary.json>)：**7/7**方法通过；00夹具和90终态不计功能，01/10/11/20/30为 **5/5功能行为**。生产4个H1章节起点0/377/730/1107；目标第2章index1，实际UI整段选择→高亮→yellow，落盘offset605；冷读记录一致；真实TOC切第3章并退出保存offset730/index2；目录→笔记→唯一“跳转”回第2章，黄色标记可见，保存offset378/index1。真实 [DB断言JSON](<E:/creation-reading-assistant/next-round-joint-final-v3/device-md-r4/db-results>)、[UI XML/截图](<E:/creation-reading-assistant/next-round-joint-final-v3/device-md-r4/ui>)、[ADB安装/恢复原输出](<E:/creation-reading-assistant/next-round-joint-final-v3/device-md-r4/adb-readback.jsonl>)。
- MD限定为**上下滚动模式**，通过真实阅读设置仅改测试书；没有直接写DB制造高亮、进度或PASS。locator ci=0为既有plain合同，章节身份由chapter_title/全局区间证明。回源offset378为实际视口顶端，未宣称marker605像素贴顶。
- 早期MD批 device-md、r2、r3均保留NOT PASS及恢复证据：前两批工具栏取证/点击顺序错误；r3已点第2章却仍见第1章分页正文，不能把它改写为绿。r4滚动模式通过不覆盖分页问题。

## 原始证据

构建、XML、原始仪器日志、逐项记录、UI与安装读回入口均见上文，成功与失败批次分别保留，各批 config-used.json 与原run manifest的配置SHA一致。

## 恢复与副作用

[Root最终独立核验](<E:/creation-reading-assistant/next-round-joint-final-v3/freeze/root-final-device-verification.json>)再次从设备回读，与第一批开始前基线对账：原qahost **71/71文件SHA相同**（含DB/WAL等持久文件），原仪器数据清单0/0相同，原宿主/仪器 **2/2 APK路径及SHA相同**；七系统项、forward/reverse相同。新任务host/test包和内部data目录均不存在；external两目录与本任务命名的临时UI XML均不存在。未读取密钥明文。Root实际查看回源截图，正文与黄色marker正向竖屏；最终62张UI PNG全部1080×1920。

## 未覆盖项与阻断

- **新发现待定位：MD默认左右翻页下，TOC点击第2章时toolbar显示第2章但正文/底栏仍第1章。** 当前仅证明此现象与截图，未定位根因，也未宣称滚动设置是分页修复。[失败批r3](<E:/creation-reading-assistant/next-round-joint-final-v3/device-md-r3>)应作为下一任务输入。
- 独立只读审查已排除“MD固定取EPUB空章节表”的错误前提：ReaderScreenDerivedState 对MD采用自身chapters起点，真实夹具第2章起点377；source的二分查找与controller装载逻辑可处理377。尚缺运行时证据区分：TOC请求未写377、消费时被其他open覆盖、实际source身份不一致。下一只在E调试候选记录请求→消费→open→applyChapter的章号/偏移/身份，不记录正文；先确认接缝，再修，不能按猜测直接替换source/cache。
- EPUB设备使用file URI；SAF/content URI重新授权链未新增覆盖。全局EPUB parse串行闸的性能影响未测量。
- 不含实体手机/MIUI、手机窄布局、全产品验收、永久D集成、签名发布或Git提交。历史设备/旧APK的通过不自动绑定此新对。

## 交付状态与下一步

返修交付限定核验 **100%（3/3）**；联合受影响JVM **100%（303/303）**；本轮限定MuMu行为 **100%（10/10：EPUB2＋SE4 3＋MD滚动5）**，方法12/12及恢复独立成立。**全模式联合退出仍未满足**，不能写全产品100%；全产品分母仍未定。

里程碑复盘：保留“阅读→书内标注→回源”的产品主线、已验同步与回滚；不重复已满足且输入未变的回归。下一先窄定位MD分页章号/正文接缝，源码分析可独立于候选清单核对，但公共接缝修复及设备批次仍单owner。分页闭合后再决定永久精确集成清单及当前操作授权，不继承旧提交许可。跨书/临时查阅与方向最终理解仍保留，暂不扩展新功能。
