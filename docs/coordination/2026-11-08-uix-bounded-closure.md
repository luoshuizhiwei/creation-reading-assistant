# UIX-CLOSE-v1 有界收尾决定与 Antigravity 执行词

日期：2026-11-08。状态：第七轮已构建，尚无对应真机验收结果。本文件不接受全产品、不恢复自动化、不直接派发外部消息。

## 本轮核对结果

- 已实算 D 仓库 `android/app/build/outputs/apk/debug/app-debug.apk`，52,812,949B，SHA256 `d9c5adcf364244365549eecb8b8df6f8be50b480e16599dfb97de6eaad352545`，匹配第七轮报告。
- 已读 D 源码：`ShelfScreen.kt` 的整理入口保留 Row.clickable，箭头携带说明；`ReaderChrome.kt` 的 ReaderFooterAction 保留 Surface(onClick)，内部 IconPedestal 携带 label。第七轮修改存在；这不证明设备导出节点与动作已正确。
- 最新 `E:/creation-reading-assistant/uix-v1/UIX-V1-REACCEPTANCE-REPORT-zcode.md` 截止第六轮：报告称顶栏/scrubber、书架四钮通过；剩余书架整理入口和底栏五钮失败。上述历史设备结论本轮仅按报告引用，未重跑设备，不宣称独立实证。
- 联合 patch 当前实际为 98,237B，报告/提示词仍写 91,742B；必须纠正引用并核清 patch 的基线/内容关系，不能只改数字冒充已绑定。
- 既有 `ReaderAccessibilityLayoutTest.kt` 已包含底栏显示、Click 和48dp断言，应优先补充/运行这些真实生产组件测试，避免另造静态字符串检查体系。
- 旧交接词“一分钟抽查后将无障碍标 PASS、宣布全面收官”预设了结果，应以本文件取代该部分。当前入口旧头部为11月4日口径，不能覆盖11月8日尚未验收的UIX变更。

## 决策

先验第七轮现成包，成功即可结束，禁止为执行本合同先做第八轮修改。剩余失败按一次完整矩阵收集，最多再做一个有因果证据的修复候选，再进行一次联合验收。目标是把外部往返限制在两轮内，不以轮数上限强行PASS。第二轮仍失败时停止同法试错，交付未闭合项和已排除假设，不无限续造版本。

本合同仅收尾UIX语义/交互缺口。Antigravity为实施和交付owner；若ZCode仍为手机owner，由其串行执行设备验收。Antigravity先准备完整单次验收材料，由用户转交；本合同不授予跨应用自动发送权限。已授权真机测试范围内无需再问是否允许使用手机，开始前仍需核身份、当前owner和基线。

## 给 Antigravity 的完整执行词

```text
执行任务 UIX-CLOSE-v1。你的目标是用现成第七轮候选收尾UIX，在本合同内最多增加一个修复候选。不要继续逐个猜 modifier 写法并逐轮转给ZCode。

1. 先读仓库AGENTS.md、docs/handoff/current.md、本合同，以及 E:/creation-reading-assistant/uix-v1/UIX-V1-FIX-REPORT-qoder.md 第十节、UIX-V1-REACCEPTANCE-REPORT-zcode.md 最新段和对应原证。若已有第七轮或更新验收，先复用，不重发、不覆盖。说明实际源码工作区、构建工作区、设备owner；D/E不混用、不整树回灌。

2. 冻结现有输入。D仓库APK预期52812949B、SHA256 d9c5adcf364244365549eecb8b8df6f8be50b480e16599dfb97de6eaad352545。记录两处呈现源码及必要依赖SHA、实际diff、构建原rc/log、APK身份、patch基线与SHA。patch现值98237B，与报告91742B不符，核清实际内容和基线后更正文档。输入变化先查明owner变更，不使用旧包冒充新构建。保全旧证，只交差量；新增交付预算100MiB，不复制工程、缓存或历史证据。

3. 暂不改代码。对现成候选一次收齐验证矩阵：书架四图标按钮、书架整理入口；阅读器返回/更多、上一章/下一章；底栏目录/听书/灵感/外观/设置。返回阅读处和自动翻页控件若被本轮实际diff影响，加入相应状态验证。逐项记录真实可见名称、启用状态、焦点/语义节点、Click动作和激活后的实际结果；同一候选保留已绿控件的定向回归。章节首尾等正常禁用态单列，不当缺陷。不得只检查desc非空或点击坐标后界面变化。

4. 使用现有生产组件Compose测试补足动作和标签联合断言，优先接续ReaderAccessibilityLayoutTest及ShelfScreenComposeTest；点击须验证回调/页面结果，不用字符串搜索当行为测试。Compose合并/未合并树、系统Accessibility节点与UIAutomator各自记录，禁止凭一次dump断定TalkBack实际朗读。若UIAutomator空desc但节点可由text获得可访问名称，需核真实节点名称/动作和重复焦点，按可访问性要求裁决，不为凑XML字段反复重构。无法验证朗读时保留该限制，不擅自安装/开启第三方服务。原生节点确有无标签、无动作或重复焦点时保留失败。

5. 手机由当前唯一owner操作，Antigravity不与ZCode争用。沿用户已授权零接触真机范围执行，fresh核实际serial/model/boot、包和数据/系统设置基线，所有命令带-s。已有数据先保全，不套用历史“空机”假设；保持供电时唤醒，记录原值，结束恢复本轮真实基线。不硬写历史动画/haptic/服务列表，不擅自卸载现存含数据应用、pm clear或删除旧资产。用户停止、数据风险、认证或owner冲突停止对应动作并保全证据。模拟器结果不能代替这台真机结果。

6. 第一轮全部达标则零新增修复，直接交最终证据。若失败，一次列出全部失败，给出生产组件路径、源码行、merged/unmerged/system节点、预期/实际动作、根因与可证伪验证。只修改失败控件及必要的小范围测试；ReaderChrome.kt与ShelfScreen.kt仅限相关hunk。不得顺手改动画、主题、导航/保存/数据库，不盲删size约束，不覆盖已绿结构；clearAndSetSemantics等API按实际modifier/动作关系判断，禁止泛化为全局有害或断言某种嵌套必然错误。

7. 必要时一次完成最小修复、定向测试、编译和APK绑定，再交第二轮联合验收。单Gradle owner持项目约定锁；禁止使用构建失败后残留旧APK。第二轮仍失败，停止同方法循环，交付精确未闭合项、已排除假设与下一步改变方法的建议。不能删掉失败用例、放宽业务断言、把NOT_RUN改PASS来满足两轮目标。不创建r8/r9无限转发链。

8. 报告放 E:/creation-reading-assistant/uix-v1/closure-v1/REPORT.md，原始日志/XML/必要截图放同目录，提供每项判定、候选SHA、测试原rc、设备前后基线、恢复结果和未验项。已充分历史证据引用原路径；改动涉及的才定向回归，不重跑整个历史。无障碍达标、功能激活、动效历史结论和数据安全分别报告。所有必需项达标且恢复有证据才接受本UIX范围，不宣布全产品完成。

9. 更新本批交接与状态前核指纹、合并他人变更。若你拥有Root维护职责，定向同步current/state的UIX条目；没有则交付精确更新建议。不要重写旧全局验收数字或历史结论。无Git暂存/commit/push/PR/发布、私有记忆写入、自动化恢复或既有资产清理许可。最终总结末行写“这是任务 UIX-CLOSE-v1。”
```
