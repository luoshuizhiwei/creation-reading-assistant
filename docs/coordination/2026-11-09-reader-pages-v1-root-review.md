# READER-PAGES-v1 Root复核与唯一集中修正

## 2026-11-10 最终复核

裁决 `ACCEPTED_MUMU_SCOPE_CLOSED`，替代下文待修裁决。已核实际保存锁定源码及组件测试断言。最终APK对 `98b629ea…` / `4b0177c9…` 与交付匹配，20个完整源码SHA全部匹配。最终构建日志成功，110份JVM XML合计984项，失败、错误、跳过均为0。MuMu手动instrument记录支持保存保护3项、长标题展开1项、动效2项。Root复用交付原证完成复核。

真机安装尝试被HyperOS拒绝，后续测试限定MuMu serial。真机验收、真实IME推顶、实际横屏及TalkBack仍未运行。修正轮恢复记录按本轮起点对账，font_scale为1.0→1.0。首轮null→1.0的存储值差异仍属历史限制。关闭实施及已验范围，未验设备场景按后续专项验收记录。

## 2026-11-09 首轮复核与修正要求

日期：2026-11-09。裁决：`IMPLEMENTATION_REVIEWED_LIMITED_EMULATOR_EVIDENCE_ONE_SAVE_FIX_REQUIRED`。四入口布局/接线有限接受，不接受全部7项完成或真机验收已通过。此为本合同的一次集中修正，不新增设计任务。

## 已实际核验

Root读取当前源码、ready、PLAN/REPORT相关内容、构建原log、JVM XML、恢复记录及3张截图，没有执行设备操作或重跑构建。

- debug APK实算 `7849269f5038a73b43a33949cc7aa87f24a471ae3630491b15600cf577e0059e`；androidTest APK实算 `c0c3683ab32ca7eabc5d997d5926595ce93b5b4f4a4291c5b5e5afa00dd07cd9`，匹配ready。
- `src-sha/after-sha256.txt`登记的源码SHA前缀与当前文件全部匹配；该清单仅保留16位前缀，不声称它是完整SHA256清单。
- 当前 `android/app/build/test-results/testDebugUnitTest/` 下按reader包及ReaderViewModelTest筛选，110份XML合计984 tests、0 failures/errors；交付tests目录只保留3份（9+7+85=101项），984合计由盘上原始结果补核，非仅照抄报告。
- final-gate.log为BUILD SUCCESSFUL。报告明确androidTest只编译未运行，不把JVM源码字符串守卫当Compose行为验收。
- 当前生产分派确有TOC/THEME/SETTINGS/INSPIRATION全屏Dialog，其他弹层保留。ReaderPageDialog保持原reader owner；Theme/Settings作用范围沿旧callback；灵感使用Saved/Failed(seq/requestId)完成信号后才关闭。
- 已查看 `device/shots/17-theme-r2-preview.png`、`24-toc-220.png`、`28-insp-ime-r3.png`，可见真实预览、索引列表与编辑页；最后一张未出现软件键盘，不能支持IME推顶验证。截图证明布局状态，不证明动画连续性。

## 必修项 P1：保存期间可继续编辑，成功清稿会丢后续输入

路径：`android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderInspirationSheet.kt`。

源码证据：236–243行点击保存把当前draft值传给onSave，只用awaitingSave禁用保存按钮；353/369行标题/正文onValueChange仍直接更新草稿，其他分类/标签也未统一锁定；181–187行匹配Saved后无条件clearDraft并关闭。只要实际落库有延迟，就存在“提交A→继续输入B→A成功→B被清空”的确定性执行路径。本轮未在设备复现，不声称已观察数据丢失。

Root决定采用最小交互锁定方案，无需实现草稿版本/多请求队列：

1. awaitingSave期间，标题、正文、标签、分类、新建分类/标签与放弃草稿均不可修改/执行。文本可以保持可读、滚动；选择readOnly或enabled时兼顾无障碍状态。保存onClick内部也检查在途状态，避免重组前重入。
2. 保存期间系统Back先收键盘，此后保留当前编辑页并给出“正在保存”状态；可见返回同样不能打开“放弃修改”并清草稿。已打开的放弃确认在提交状态变化后也不能越过该守卫。不要在途派发另一份相同请求，不伪装撤销已经开始的落库。
3. 成功沿现有匹配id回调关闭，失败解锁全部编辑并保留原稿，重试复用原entity id；取消不发布成功。不要以固定延迟或吞异常实现。
4. 用可控延迟保存（测试fake repository或注入callback，不能给生产加sleep）验证：提交前内容、保存中尝试编辑/连点/返回、成功与失败两条结果、失败后修改重试同id。测试必须观察实际组件状态/回调，源码contains不能证明修复。优先新增小范围Compose测试并实际运行；确有环境阻碍则明确缺口，不能用VM单测替代页面锁定验收。

## 本次集中修正同时完成的有限验证

- 在授权且空闲的设备上补真实IME出现时的编辑/返回/保存可达；仅adb input text且无键盘不算。
- 补横屏实际宽高变化与长章节标题展开；缩小dp视口不等于横屏。未能验证如实保留，不继续泛化PASS。
- 录一段四入口打开/调节/返回，核180/140ms动效可取消与减弱动画分支。当前证据树未提供连续录像，不能仅截图接受动画效果。
- 原三页设计和既有顶栏/scrubber不再重写；只对受修改的灵感页做必要回归，其余充分证据复用。真机用户占用则不抢屏，模拟器结果继续标模拟器。

## 文档与恢复口径校正

- 报告“7项全部完成”改为已实现、已运行范围与待验项分别列，直到必要缺口补齐。
- `baseline-restore-evidence.md`为整理后的记录，未附八设置全量前后原始命令输出；font_scale基线null、收尾1.0属于行为等价但存储值不同，不能写逐字一致或“无未恢复”。补现有原始记录链接；没有则保留恢复证据有限的说明。不要为了补证盲重装/卸载或额外启动设备。
- 补交完整64位源码SHA256，复用现有984原XML定位；不为归档额外重跑充分测试。
- 所有修改继续沿原READER-PAGES-v1所有权与保护规则。ZCode完成这一次集中修正后交原目录REPORT/ready/diff/测试原证与新APK身份，写current/state前核指纹合并，不commit/push，不自动发外部消息。

## 给ZCode的短执行指令

读取本文件并执行READER-PAGES-v1唯一集中修正。保持四入口既定设计，不另开一轮美化。先修保存中继续输入/放弃导致草稿被旧Saved清空的窗口，补可控延迟行为测试与合同有限未验项；复用充分证据，修正恢复和验收口径。最终按实际已验范围收尾，不以源码守卫/截图替代组件行为/动画验证。最后一行写“这是任务 READER-PAGES-v1 集中修正。”
