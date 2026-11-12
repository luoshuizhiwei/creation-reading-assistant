# ZCode读取执行词：READER-PAGES-v1

日期：2026-11-08。实施者ZCode，单owner完成实现、必要编译修复、验证与交接。用户手动发送本入口，不代表启用自动化。实施模型由ZCode实际配置记录，不虚称切换。

```text
你接任任务 READER-PAGES-v1，按Root已定方案直接修改原生Android代码并完成验证。

活动仓库：D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919

先读仓库AGENTS.md、用户级AGENTS.md、docs/handoff/current.md，然后完整读取：
docs/coordination/2026-11-08-reader-pages-v1-implementation.md

该合同已明确容器、页面布局、动画、文件所有权、7项工作顺序、业务接缝、设备边界和交付规则。它是本次实现要求，不需要再给用户一组设计选项或等待选风格。普通技术接线由你直接解决。

必须完成四入口的正式生产接线：
1. TOC变为书内索引附页，保留目录/书签/笔记及原有规则管理和typed回源能力。
2. THEME变为真实正文预览+分类调节区；SETTINGS变为完整满高分类页，保留本书/全局作用范围、即时生效和来源返回。
3. INSPIRATION变为摘录旁写完整编辑页，保护草稿；保存必须等待真实落库，失败留稿可重试，同一次草稿使用稳定id。

沿现有ReaderSheet状态与阅读会话，用限定全屏Dialog容器承载以上入口，不新建第二套reader/NavHost/ViewModel；其他短sheet不全局替换。必须实现合同中的返回/键盘优先级、系统栏与insets、180/140ms轻量过渡、减少动态效果和窄屏布局。

先读当前源码和WIP，记录输入，不复原、不覆盖他人修改。已通过的UIX按钮结构保持原样。本批是新的页面体验改造，不宣称旧UIX全部历史或全产品完成。先自行集中验证四入口，再进行一批设备验收，避免每个控件一轮交接。最多一次集中返修，不能通过放宽断言凑PASS。

交付到E:/AgentWork/CRA/reader-pages-v1/，按合同提供实际diff、源码和APK指纹、原rc/log/XML、截图/录像、恢复原证及未验项。写current/state前核指纹并合并，仅同步本批。全程不commit/push/发布、不清理既有资产、不写私有记忆，不恢复自动化。不要只交静态稿或继续生成让用户反复转发的修复词。

最终总结末行：
这是任务 READER-PAGES-v1。
```
