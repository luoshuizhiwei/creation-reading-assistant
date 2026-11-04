# Android 外部 Agent 审查任务看板

> 2026-09-22 计划审查更新：后续调度以 [恢复仓库收口计划](../../../plans/android/2026-09-22-android-recovery-convergence-plan.md) 为准。
> 本文以下为历史任务/证据，顶部模式、模型安排、HOLD/READY 和旧横屏要求不得直接用作新任务授权。
> 当前状态见 [2026-09-22 统一任务账本](../../decisions/2026-09/2026-09-22-current-task-ledger.md)：快照/分类已完成，下一轮 B/C/D 仅做只读对账，实现与清理尚未恢复。
> 旧 B 的“首次点击只收菜单、第二次才翻页”提示词已废止。后续手机验收仅竖屏。

> 总指挥与最终结论 owner：Codex 主任务  
> 执行 Agent：Zcode、Qoder  
> 恢复仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`  
> 工作分支：`codex/recover-worktree-20260920`  
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`  
> 当前模式：只读审查，不实施产品修复

## 1. 指挥原则

1. Codex 主任务负责：决定切片顺序、划定边界、复核证据、合并结论、维护正式报告与 Obsidian 记录。
2. Zcode/Qoder 负责：范围明确的代码阅读、证据收集、定向测试和候选问题清单。
3. 一次只放行一个产品切片。当前切片未被 Codex 复核前，不自动领取下一片。
4. 两个 Agent 不编辑同一文件；各自只写自己的 `agent-input-*` 报告。
5. 不允许外部 Agent 宣称“切片完成”“可以发布”或替代 WorkBuddy 最终真机验收。

## 2. 全局安全边界

- 只使用恢复仓库。原始仓库 `D:\develop\Code\Codex\creation-reading-assistant` 只保留为证据，不写入。
- 工作区既有修改全部视为用户资产；不得 reset、checkout、clean、stash、全仓格式化或还原文件。
- 不 stage、不 commit、不 push、不创建分支或 worktree。
- 不修改 Android 产品源码、资源、Gradle、测试源码、正式报告、产品清单或 Obsidian。
- 不使用 MuMu，不安装 APK，不操作真实手机或用户书籍数据。
- 不读取或修改 `D:\Application\文档\Obsidian\Claude`。
- 查看未提交差异使用 `git diff origin/main -- <paths>`，不用三点式 diff。
- Zcode 不运行 Gradle；Qoder 只允许运行当前切片的定向 JVM/编译检查。
- 两个 Agent 同时运行时，只有 Qoder 可以占用 Gradle，避免共享缓存冲突。
- 报告必须区分：已确认事实、静态推断、历史证据、当前未验证项。
- 如果发现 P0 数据破坏、隐私泄漏或无法继续的仓库异常，立即停止并只报告阻塞。

## 3. 模型安排

| Agent | 默认模型 | 用途 |
|---|---|---|
| Zcode | `deepseek v4.1Flash` | 快速代码链路、调用关系、文件/hunk 边界、跨格式差异 |
| Qoder | `qwen3.8Flash` | 独立缺陷审查、测试质量、定向 JVM/编译证据 |

Zcode 的 `hy4` / `hy3` 暂不使用。只有 `deepseek v4.1Flash` 明确无法读取项目或输出严重不完整时，才由用户告知 Codex 后重新分配，外部 Agent 不自行换模型。

## 4. 当前放行：第 1 轮 S07

### Zcode — S07-Z：选区全链路地图

状态：`REVIEWED`（报告已完成并由 Codex 复核）

任务：从正文长按开始，分别追踪 TXT、Markdown、EPUB 的选区识别、source/display 坐标、手柄拖动、工具栏展示、动作分发、保存高亮/笔记/替换规则、翻页/滚动/切章后的清理。明确跨页选择不支持时的真实降级行为，并列出 S07 精确文件/hunk 边界与混入的其它切片。

重点检查：

- 选区是否会在换页/换章/文档重载后泄漏；
- 替换净化后的 display 范围是否能无损映射回 source；
- 流式大 TXT、小 TXT、Markdown、EPUB 是否使用一致坐标；
- 复制、浏览器、字典、更多、外部文字工具各自的真实处理方；
- UI 有入口但没有生产接线的半成品；
- 共享文件为什么不能整文件集成。

禁止运行 Gradle。

唯一允许写入：

`docs/code-review/undated/agent-input-s07-zcode.md`

报告必须包含：范围、三种格式链路、动作矩阵、坐标风险、状态清理、精确边界、事实/推断/未验证项、给 Codex 的复核清单。每个问题附文件与符号或行号。

### Qoder — S07-Q：独立缺陷与测试审查

状态：`REVIEWED`（报告已完成并由 Codex 复核）

任务：独立审查 S07 的用户行为与测试证据，不照抄代码注释或历史报告。输出必须严格分为 `Standards` 与 `Spec` 两轴，严重度使用 P0/P1/P2。

重点检查：

- 小屏、横屏和字体放大时五个主要动作是否拥挤/截断；
- TalkBack、触摸目标、焦点顺序、contentDescription 和手柄语义；
- 无浏览器、无字典、无 PROCESS_TEXT handler 时的回退；
- 复制成功提示是否与真实剪贴板写入一致；
- 翻页、滚动、切章、关闭面板后是否清除选择；
- 保存高亮/笔记是否可能使用错误章节或 offset；
- 替换净化和跨页边界是否产生半截记录；
- 测试是否只验证纯函数而没有验证真实用户旅程。

可运行少量定向 JVM/编译检查。报告完整记录命令、退出码、测试类、tests/failures/errors/skipped 和未覆盖项。

唯一允许写入：

`docs/code-review/undated/agent-input-s07-qoder.md`

报告末尾写：Standards 数量及最严重项、Spec 数量及最严重项、是否运行测试、是否改动报告以外文件。

### Codex 复核结果

- 正式报告：`docs/code-review/2026-09/2026-09-20-android-s07-selection-review.md`；
- 两名 Agent 均未修改 Android 产品文件；Qoder 定向证据为 12 类 / 100 个 JVM tests 全绿，androidTest Kotlin 编译成功；
- Qoder 先于 Zcode 最终落盘，其关于“Zcode 文件不存在/引用失真”的交叉段已过时，正式结论不采纳该段；
- Codex 确认 4 个 P1 产品问题：`edit` 死按钮、复制虚假成功、特定换页/切章路径残留旧选区与坐标、Markdown/旧 EPUB 选区能力不完整；
- S07 不进入候选；先做最小修复和状态级测试，再交 WorkBuddy 真机验收；
- S08 及后续队列继续 `HOLD`，本轮未自动放行下一片。

### S07-F1 修复轮

状态：`PASS`（Zcode 实施，Qoder 独立复核，Codex 轻量裁决）

- 已关闭：`edit` 死按钮、复制失败仍报成功；
- 验证：4 类 / 49 个 JVM tests 全绿，目标用例经 XML 核对；
- P2 后续：写侧 dormant 判据、动作分发表完整性门禁、复制接线级测试；
- 未关闭：翻页/切章旧选区与坐标残留、Markdown/旧 EPUB 选区能力；
- 报告：`agent-result-s07-f1-zcode.md`、`agent-review-s07-f1-qoder.md`。

### S07-F2 修复轮

状态：`PARTIAL PASS`（主体通过，F2.1 未闭环）

- 已完成：选区五字段统一清理；翻页、跨章、TXT 章导航、普通撤选和中央点击的状态/接线测试；
- 证据：F2 4 类 / 92 tests；Qoder 含 F1 哨兵共 6 类 / 119 tests，全绿；
- 未闭环：同章 `jumpRequest -> controller.open(j)` 不清选区；非 reveal 点按在实际提交前乐观派发 `PageTurn`；本地 `selRange` 回流缺测试；
- 报告：`agent-result-s07-f2-zcode.md`、`agent-review-s07-f2-qoder.md`。

### S07-F2.1 收口轮

状态：`PASS`

- 已关闭：同章程序化跳转旧选区、非 reveal 提前/重复 `PageTurn`、本地 `selRange` 回流测试；
- 证据：10 类 / 158 tests 全绿，patch 哈希与精确增量经 Qoder 独立复算；
- 下一步 F2.2：程序化跳转改用纯 `ClearSelection`，避免隐藏 chrome；检查滚动 ReadingUnit 身份变化；
- 报告：`agent-result-s07-f21-zcode.md`、`agent-review-s07-f21-qoder.md`。

### S07-F2.2 状态收尾

状态：`PASS`

- 已关闭：程序化跳页误隐藏 chrome；滚动 ReadingUnit 回收后的旧 VM 选区；
- 证据：13 类 / 175 tests 全绿，patch 哈希和范围独立复算；
- F2.3 待处理：弹层消费期 dispose 安全、滚动接线守卫、ownership 单一判据；
- 报告：`agent-result-s07-f22-zcode.md`、`agent-review-s07-f22-qoder.md`。

### S07-F2.3 消费安全与接线加固

状态：`FAIL`（B/C 加固有效，A 的 P1 竞态未修）

- 已完成：折叠清除与 dispose 共用同一 ownership 判据；新增 5 条滚动分支结构守卫；
- 证据：Zcode/Qoder 各自运行 14 类 / 180 tests 全绿，范围增量无夹带；
- 未关闭：TTS 程序化滚动可在笔记、规则、AI 解读、灵感仍消费选区时触发拥有者 dispose；笔记/AI/灵感丢摘录，规则页可能丢草稿并切回目录标签；
- Standards 跟进：让接线守卫截取并验证 `onDispose` 块，删除检查不可编译写法的空负向断言；
- 下一步 F2.4：保存打开时不可变的选区文字与定位快照，允许正文活动选区照常清除，消费者全程只读快照；
- 报告：`agent-result-s07-f23-zcode.md`、`agent-review-s07-f23-qoder.md`。

### S07-F2.4 消费者快照闭环

状态：`PASS`

- 已关闭：TTS/ReadingUnit dispose 期间笔记、规则、AI 解读、灵感和 AI 伴读丢失选区文字或 locator；规则页草稿/tab 被实时选区变化重建；
- 实现：打开时原子捕获四字段不可变快照，活动选区与消费者快照正交清理；关闭或切换非消费者时释放；
- 证据：Qoder 独立运行 19 类 / 263 tests 全绿；结构守卫 17、语义/行为 35，范围与哈希独立复算；
- F4 遗留：切书同 VM 释放快照 P2、消费者分类表与保存参数约束 P3、快照产品口径文档化；
- 下一步 F3：Markdown / 非分页 EPUB 选区和持久高亮能力边界；
- 报告：`agent-result-s07-f24-zcode.md`、`agent-review-s07-f24-qoder.md`。

### S07-F3 跨格式选区与持久高亮

状态：`FAIL`（多数映射基础有效，1 个 P1 阻断）

- 已完成且可保留：Markdown canonical/base 映射、重复文本隔离、半开区间、持久高亮纯投影；非分页 EPUB 整块选择真实终点；图片/分隔线和表格伪选区边界；
- 证据：Qoder 独立运行 26 类 / 397 tests 全绿，范围与 after patch 独立复算；
- P1：Markdown 起点误传 `globalOffset`，非 EPUB locator 实际读取 `rangeStart`，导致保存记录 locator 恒为 null；
- F3.1 必修：槽位修正 + 穿过 `computeLocatorJson` 的行为测试；处理 stale-chapter 假判据、非分页 EPUB 高亮回显/明确降级和 sourceEnd 对纠错入口的副作用；
- P2/P3 已写入正式报告与 Obsidian TODO，不因 F3.1 修 P1 而丢弃；
- 报告：`agent-result-s07-f3-zcode.md`、`agent-review-s07-f3-qoder.md`。

### S07-F3.1 Markdown locator 与跨格式高亮闭环

状态：`PASS`（F3 阻断 P1 已关闭，F4 继续收口 P2/P3）

- Markdown 生产参数改为具名结构，canonical 起点进入 `rangeStart`，`globalOffset=-1`；行为测试穿过真实 locator 编解码，并证明旧槽位会失败；
- Markdown stale-chapter 假判据已移除，非分页 EPUB 已保存高亮按真实区间回显，未使用文本猜测；
- Qoder 独立运行 30 类 / 342 tests 全绿，缺失类 0，范围污染 0；
- 新确认 P2：非分页 EPUB 单处纠错目前只由 UI replacement capability 门控，ViewModel `SaveSingleCorrection` 命令边界缺少纵深防御；
- F4 同时处理 `charCount=0` 安全降级、同 VM 切书快照释放、消费者分类与动作接线门禁、提示真实性及剩余证据/文案问题；
- 报告：`agent-result-s07-f31-zcode.md`、`agent-review-s07-f31-qoder.md`。

### S07-F4 最后开发收口

状态：`FAIL`（多数 P2/P3 已闭合，但新增/保留两个阻断状态风险）

- Qoder 独立运行 23 类 / 311 tests 全绿且全部命中；测试绿灯不覆盖事实源错误；
- P1：VM 按文档类型把所有 EPUB 判为不可纠错，误伤分页引擎已判 `Available` 的 EPUB，形成 UI 允许、保存拒绝；
- P1/高风险状态缺口：切书只释放快照却保留活动文字与坐标，旧选区可在新书中被再次捕获；
- P2：外部浏览器/词典仍先报“正在打开”再失败，且词典失败借用浏览器文案，F4 账本误记为已完成；
- F4.1 只修以上三项与行为钉子，恢复同文件集 before/after 证据；S08–S20 继续 HOLD；
- 报告：`agent-result-s07-f4-zcode.md`、`agent-review-s07-f4-qoder.md`。

### S07-F4.1 三项阻断修复

状态：`PASS`（开发门槛关闭，转 WorkBuddy 真机验收）

- UI 与 VM 共用实时 `pagerEngine.replacementAvailability`，分页 EPUB 放行、legacy EPUB/Markdown 安全拒绝；
- 切书/force 重载完整清活动选区、快照和消费者 UI，旧书数据不可在新书重新捕获；
- 外部浏览器/词典只在最终结果后显示一条准确提示，dormant `edit` 写侧拒绝；
- Qoder 独立运行 29 类 / 366 tests 全绿，行为 331、结构守卫 35，缺失类 0，androidTest 编译成功；
- 无 P0/P1/P2；仅余 Standards 5 项 P3、Spec 2 项 P3及真机未验证项；
- 下一步：WorkBuddy 真实手机验收，禁止 MuMu；S08–S20 继续 HOLD；
- 报告：`agent-result-s07-f41-zcode.md`、`agent-review-s07-f41-qoder.md`。

### S07-QA1 Qoder 真实手机验收

状态：`PARTIAL`（核心链通过，4 个 P2 中 2 个属于 S07）

- 设备：`c49ac6cf` / Android 15；APK SHA256 `cb4b921e0db8566f1a595983f2a02b68fbfd252e443444da3fa019a57320663f`；
- 构建退出码 0；安装脚本退出码 0、`CONFIRM_LOOP`，未使用 ADB 回退；
- 通过：分页 EPUB 单处纠错完整链、跨书隔离、三格式保存→重开、主要布局与设置恢复；
- S07 P2：Markdown 图片 alt 文本可形成伪选区/伪高亮；滚动 TXT 单元回收后旧工具栏与摘录仍常驻；
- 独立 P2：本书作用域下 EPUB 分页模式写全局；legacy EPUB 性能需零高亮对照复测；
- P3：legacy EPUB 无障碍树/取消选择、TTS 权限静默、段落间隙长按、纠错后搜索索引等 5 项；
- 下一步：S07-F5 修两个选区 P2，然后 Qoder 只复验相关真机用例并补性能对照；
- 报告：`docs/qa/2026-09-21-s07-qoder-device-acceptance.md`。

### S07-F5 图片伪选区与滚动选区失效修复

状态：`PARTIAL`（修复和主路径复验通过，TTS 自动滚动分支待补）

- Zcode 仅修改 2 个产品文件并新增 3 个定向测试文件：Markdown 含图片单元不再生成整块选区或投影伪高亮；滚动 TXT 在选区 owner 离开可见范围后由列表宿主统一清活动选区；
- Qoder 独立代码/JVM 轴判定 PASS，并在真实手机 `c49ac6cf` 确认 D-1、D-3 用户手势与搜索程序化跳转均关闭；
- Codex 新鲜复跑 F5 三类 44 tests：0 failure / 0 error / 0 skipped，构建退出码 0；关键截图与报告结论一致；
- TTS 未能启动，故“朗读推动 owner 离开可见范围”仍是 NOT TESTED，S07 不关闭；下一步只由 Qoder 补该单项，允许临时授权通知权限并在收尾恢复；
- D-7 的高 jank 数字已被同脚本系统设置控制组证明为 shell 手势注入 + gfxinfo 测量伪影，不再保留为产品 P2；字面对照 B 组未完成，但不再用该方法作性能结论；
- 独立保留 P2：D-2 设置作用域；N-1 书内已有未删除高亮但“目录与标记→笔记”显示为空。两者都不得混入 TTS 补验；
- 报告：`agent-result-s07-f5-zcode.md`、`agent-review-s07-f5-qoder.md`、`docs/qa/2026-09/2026-09-21-s07-f5-qoder-device-retest.md`。

## 5. 后续待分配队列

以下只是总任务列表，不代表已经授权开始。只有 Codex 将对应行改为 `READY` 或明确发出下一轮指令后才能执行。

| 顺序 | 切片 | 可交给外部 Agent 的简单任务 | 暂不外包的核心决策 | 状态 |
|---:|---|---|---|---|
| 2 | S08 章节修改层 | 盘点算法、入口、保存接线与测试缺口 | 是否允许编辑原文、撤销和数据模型 | `HOLD` |
| 3 | S09 页码/硬件键/点击区 | 分拆四项能力、查发送方/处理方、测试矩阵 | 全书页码事实源与发布边界 | `HOLD` |
| 4 | S10 目录卷标题/页眉页脚 | 规则矩阵、占位符与格式覆盖盘点 | 自定义模板产品契约 | `HOLD` |
| 5 | S11 EPUB 脚注/图片/引用卡 | 检查入口可达性、共享状态与已有测试 | 是否进入首版候选 | `HOLD` |
| 6 | S12 Legado 进度导入 | TXT/Markdown/EPUB 坐标静态对照、预览与确认链 | EPUB 坐标 owner 与写入契约 | `INVESTIGATION ACCEPTED；IMPLEMENTATION HOLD` |
| 7 | S13 设置重构 | 页面/路由/搜索索引/公共组件清单 | 共享基础设施拆分与视觉验收 | `HOLD` |
| 8 | S14 同步/WebDAV | 数据资产矩阵、开关与冲突测试盘点 | 上传/下载/冲突/半恢复语义 | `HOLD` |
| 9 | S15 时长单位 | 12 个展示位查漏、持久化和长按冲突测试 | 最终交互取舍 | `READY / WINDOW D` |
| 10 | S16 顶层页面收敛 | 按用户任务归类文件与入口 | 页面级产品优先级 | `HOLD` |
| 11 | S17 应用壳/公共 UI | 资源、导航、系统栏与公共组件依赖图 | 共享 seam 集成 | `HOLD` |
| 12 | S18 Benchmark | 脚本/设备/CI 证据审计 | 性能准入阈值 | `HOLD` |
| 13 | S19 安装诊断脚本 | 区分正式脚本、诊断脚本和临时工件 | 哪些资产进入仓库 | `HOLD` |
| 14 | S20 隔离项 | 分类 Desktop、Agent、截图、XML、日志 | 删除/归档授权 | `HOLD` |

## 6. 完成与回传协议

外部 Agent 完成当前任务后，在各自对话中只需给用户返回：

- Agent 和模型；
- 报告绝对路径；
- 问题数量及最严重项；
- 测试命令与结果（如有）；
- 是否修改了报告以外文件；
- 明确说明“等待 Codex 主任务复核，未领取下一片”。

用户随后只需回到 Codex 主任务说：`Zcode 和 Qoder 的 S07 报告已完成，请复核。`

Codex 主任务读取两份报告后，会：

1. 对照真实源码复核高风险结论；
2. 去重并纠正误报；
3. 补必要的定向验证；
4. 生成正式 S07 双轴报告；
5. 更新产品切片清单和 Codex Obsidian 记忆；
6. 决定是否放行下一轮。

### S07-F5-TTS 最终补验

状态：`PASS`（S07 关闭）

- Qoder 在真实手机 `c49ac6cf` 证明 TTS 自主推进使第 4 章选区 owner 离屏时，活动选区与工具栏在同一秒自动清除；返回后不复活，新选区可正常建立；
- Codex 抽查轮询日志、TTS logcat、关键截图，并现场回读通知权限、stay-awake、旋转和字号均已恢复；测试 TXT per-book 模式由 Qoder 回读为 `paged`；
- 消费者工作表 × TTS 因 UI 互斥为 NOT TESTED，不取代核心判定；已有 F2.4 快照语义测试与此前真机证据；
- 未新增 P0/P1/P2。D-2 与 N-1 独立保留，S08–S20 不因 S07 关闭自动放行；
- 报告：`docs/qa/2026-09/2026-09-21-s07-f5-tts-qoder-device-retest.md`。

### A01 标注索引接线修复

状态：`PASS（已关闭）`

- 目标：修复“目录与标记→笔记”在书内已有高亮/普通笔记时仍显示空态，并保证高亮、普通笔记、书签使用各自 typed navigation，不串表；
- 已知边界：ReaderViewModel 已提供 `inputs.highlights` / `inputs.notes`，DAO 非根因；`ReaderSheetHost` 调用 `TocSheet` 时漏传两者，`TocSheet` 默认空列表掩盖接线错误，且 `NotesTabContent` 只接受 NoteEntity，与“划线与笔记”文案不一致；
- 禁止修改数据库/schema/DAO、D-2 设置、S08–S20；先红测，再做最小 UI/回调接线修复；
- 完成后由 Qoder 独立代码复核，再决定是否需要真机定向复验。

#### A01-Z1 Zcode 开发结果

状态：`PASS（Qoder 独立复核与真机均通过）`

- 根因确认：生产宿主漏传 highlights/notes，TocSheet 默认空列表掩盖漏接线；笔记 Tab 只能承载 NoteEntity，普通笔记回源还错误复用 BOOKMARK 类型；
- 修复使用高亮/普通笔记统一 typed 条目，书签独立；HIGHLIGHT/NOTE/BOOKMARK 三种跳转均在写入 pending target 前带类型；
- Codex 新鲜复跑 3 类 / 34 tests：0 failure / 0 error / 0 skipped；`:app:compileDebugAndroidTestKotlin` 退出码 0；
- 接受 SEAM REQUEST：`ReaderRulesSheetTest.kt` 两处只因必填参数收紧而补 `notes = emptyList()` / `onPickNote = {}`，未改断言或产品行为；
- 代表性大集合中的 1 个 `ProfileWebDavManagerTest` 失败属于既有 S14 WebDAV 环境问题，不能算 A01 绿灯，也不归因给 A01；
- Standards 3 项 P3，最严重为生产接线仍需真机证明；Spec 1 项 P3，为高亮与笔记按类型成组而非统一时间线；
- 下一步：Qoder 只读独立代码复核，并在真实手机定向验证列表展示、typed 跳转、书签隔离和空态。

#### A01-QA Qoder 独立复核与真实手机验收

状态：`PASS`

- 代码轴：定向 3 类 / 34 tests 与代表性 116 类 / 1018 tests 全绿，两个 Kotlin 编译任务真实重编译通过；无 P0/P1/P2；
- Codex 已确认 Qoder 标出的 `ReaderTocSheetTest` “阅读中”断言早于 A01 存在，不计入 A01 改动面；A01 实际保留 Standards 4 项 P3、Spec 2 项 P3；
- 真机：真实设备 `c49ac6cf`，安装脚本 `CONFIRM_LOOP` 成功且未使用直接 ADB 回退；测试 EPUB/TXT 的高亮与普通笔记可见并能按 HIGHLIGHT/NOTE 回源，书签仅在书签 Tab 且按 BOOKMARK 回源，真正空书仍显示空态；
- 无 crash/ANR/duplicate key/跨书跳转/数据丢失；临时书签已通过 UI 删除，数据计数恢复；
- Codex 目视核对 5 张关键截图，并现场回读通知权限、stay-awake、旋转、字号及应用停止状态，均已恢复；
- 非阻断体验债：高亮仍使用书签形图标，高亮与普通笔记按两段拼接且无分节标题；另有普通笔记卡片文字重复问题，后续按体验优先级单独处理；
- 报告：`docs/coordination/reviews/undated/agent-review-a01-qoder.md`、`docs/qa/2026-09/2026-09-21-a01-qoder-device-acceptance.md`。

### D-2 阅读设置作用域契约

状态：`D-2-A CLOSED；D-2-B READY（只读 UI 透明度设计）`

- 已知现象：设置页选中“本书”时修改 EPUB 分页引擎模式，实际仍会写入全局；
- 已知结构：`ReaderOverrideKey` 支持 `READER_MODE`，但没有 `pagerEngineMode` / `epubPagerEngineMode`；`ReaderSettingsRouter` 将不可覆盖项在 BOOK/GLOBAL 两种作用域下都写全局；高级设置页未向用户显式说明这些选项是全局项；
- 本轮只允许根因、影响面、测试缺口和方案比较，不改产品代码、不改数据、不做真机、不领取 S08–S20；
- 需要比较两条方案：增加 per-book override，或在“本书”作用域隐藏/禁用并明确提示为全局设置。由 Codex 根据报告决定后续实现片。
- Zcode 只读调查确认根因，并发现同一作用域误导还覆盖 26 个设备/交互字段；5 类 / 58 项现有设置测试全绿，但现有测试把“不可覆盖项在 BOOK 下照样写全局”当成契约，未覆盖用户可见语义；
- Codex 裁决分页引擎采用方案 A：`pagerEngineMode` / `epubPagerEngineMode` 支持真正的 per-book override。理由是高级页文案明确承诺“特定书籍分页异常时临时切回”，仅提示其为全局设置不能满足该场景且会造成二次返工；
- 实现必须把“全部可覆盖项”与“视觉预设负责的项”分开：大字号/护眼/默认预设不得静默重置或清除分页引擎覆盖；“全部跟随全局”仍清除所有本书覆盖；“恢复全局默认”可恢复引擎结构默认，但确认文案必须明确列出；
- 其余 26 个真正的设备/交互全局字段另立 D-2-B UI 透明度子片，本轮不得顺带扩大。

#### D-2-A Zcode 开发结果

状态：`CANDIDATE PASS（待 Qoder 独立复核）`

- 新增 TXT/Markdown 与 EPUB 两个分页引擎覆盖键，BOOK/GLOBAL 路由复用既有枚举驱动机制；未修改 Router、EditApplier、PagerEngineState 或全局键名；
- 视觉预设与全部覆盖重置已拆分：套用预设保留引擎覆盖，“全部跟随全局”清全部本书覆盖，“恢复全局默认”明确恢复两个引擎默认并更新 22 项文案；
- Zcode 报告定向 4 类 / 76 tests、设置包 19 类 / 191 tests、SettingsViewModel 8 tests 全绿，主 Kotlin 真实重编译成功；无 SEAM REQUEST；
- Codex 源码复核未见当前行为阻断，但要求 Qoder 专查 `PRESET_OWNED_KEYS = entries - ENGINE_KEYS` 的开放式分类是否会让未来非视觉覆盖键静默进入视觉预设，以及对应测试是否为循环论证；
- 本阶段只放行 Qoder 只读代码与测试复核，不做真机、不进入 D-2-B 或 S08–S20。

#### D-2-A Qoder 独立代码复核

状态：`CODE PASS（待真实手机验收）`

- Qoder 独立运行定向 4 类 / 76 tests、设置包 19 类 / 191 tests、SettingsViewModel 8 tests，均 0 failure/error/skipped；主 Kotlin `--rerun` 编译退出码 0；
- 10/10 功能契约成立，改动归因通过 `/tmp/d02a` 哈希闭环；Standards 无 P0/P1/P2，保留 5 项 P3，最严重为预设所有权使用开放式补集且同公式测试不能防未来语义漂移；
- Codex 不接受 Qoder 的文案 P2：括号内“页眉页脚”概括 `showReaderInfo` 与页眉左右/页脚左右共 5 个底层字段，因此 18 个用户类别正好覆盖 22 项，并未漏列 4 项；“排版与显示”概括略宽及 Markdown 星号原样显示降为 P3；
- Codex 最终代码轴：Standards P3×5，Spec P3×5，无 P0/P1/P2；放行真实手机定向验收，仍不放行 D-2-B 或 S08–S20。

#### D-2-A Qoder 真机验收与现场收尾

状态：`PASS / CLOSED`

- Qoder 在真实手机 `c49ac6cf` 验证 A–I：两个分页引擎可按书覆盖、重启保持、跨书隔离、预设保留、全部跟随全局清除、GLOBAL 写入与旧书兼容均通过；无 P0/P1/P2，保留 4 项 P3；
- 安装脚本退出码 0、`RESULT: CONFIRM_LOOP`，未使用 ADB 直装回退；APK 与设备侧 `base.apk` SHA-256 一致；
- 首轮验收误触使测试 TXT 从 0% 推进，RESTORE 轮仅通过 App UI 恢复至 0%，force-stop 重开仍为 0%；测试 Markdown 保持 0%；测试 EPUB 回到同章同逻辑页，保留已披露的 1.5pp 引擎坐标重映射差异；
- Codex 目视核对三张恢复截图并现场回读：四份 DataStore 快照 SHA-256 均为 `7EACA372...24B000`，stay-awake=3、旋转=1/0、fontScale=1.0、通知权限=false、应用 PID 为空；
- 报告：`docs/qa/2026-09/2026-09-21-d02a-qoder-device-acceptance.md`。下一步只放行 D-2-B 的只读 UI 透明度设计，不自动放行 S08–S20。

#### D-2-B Zcode 只读设计候选

状态：`DESIGN CANDIDATE（待 Qoder 独立复核，不放行实现）`

- Zcode 重新核对为 26 项（翻页 7 / 显示 9 / 护眼 10），推荐 A′：页级作用域提示 + 每项“所有书籍”标记，保持存储和路由语义不变；
- Codex 轻量交叉核对认为清单与数据语义基本可信，但“仅改 4 个产品文件”尚未成立：现有滑块、开关、亮度、页眉页脚组件没有统一行内徽标插槽，三个页面各自包装可能造成重复与布局风险；
- `ReaderThemeSheet` 只证明页级说明已有先例，不能直接证明 26 个行内徽标已有成熟模式；报告把尚未发生的亮度错误措辞风险计为 P2，也需独立纠正分级；
- 下一步只放行 Qoder 只读设计复核，重点裁决“页级说明是否已足够”、行内标记所需真实文件边界、可测试性与小屏视觉密度；仍不实现、不进真机。
- 报告：`docs/coordination/designs/agent-design-d02b-zcode.md`。

#### D-2-B Qoder 独立设计复核与 Codex 裁决

状态：`DESIGN PARTIAL；IMPLEMENTATION READY（Zcode 单文件产品实现）`

- Qoder 确认 26 项清单正确（7/9/10），但判原设计 PARTIAL：逐行徽标需要 8 个产品文件并触碰共用组件；ThemeSheet 只证明页级说明先例；原报告的假设性亮度 P2 与高级兼容 P3-1 归因均不成立；
- 新发现：根页 `pageTurnSpeed` 还有第 27 个书内编辑点；`customTip*` 显示值存在 per-book 回落但写入全局；亮度手势与自动翻页还有面板外全局写点；
- Codex 采纳“页级例外式规则句”，放弃 26 个徽标和公共组件修改；BOOK 下混排页列出本书例外、护眼页说明整页全局、排版/高级说明只改当前书；根页翻页速度追加“全局设置，所有书籍共用”；
- Codex 修正 Qoder 的 GLOBAL 文案：不能笼统写“影响所有书”，因为其他书已有 per-book override 会保留；统一写“本页修改将保存为全局默认；其他书已有的单书设置不会被清除”；
- 产品代码边界仅 `ReaderSettingsSheet.kt`；新增纯映射 JVM 测试并扩充 `ReaderSettingsSheetTest`，复跑既有 `ReaderSettingsRouterTest` 证明数据语义未变；不新增 26 项路由重复测试、不写源码正则徽标守卫。
- 报告：`docs/coordination/reviews/undated/agent-review-d02b-design-qoder.md`。

#### D-2-B Zcode 实现候选

状态：`CANDIDATE PASS（待 Qoder 独立代码复核）`

- 产品代码仅在 `ReaderSettingsSheet.kt` 增加页级规则句与根页翻页速度全局说明；新增 1 个 JVM 测试文件并扩充 1 个 Compose 测试文件，未改路由、存储、枚举或公共组件；
- Zcode 报告定向 JVM 2 类 / 36 项、附加回归 28 类 / 290 项全绿，androidTest 与主 Kotlin 编译成功；Compose 用例仅编译未执行；
- Codex 源码复核确认映射函数、ROOT 空提示、GLOBAL 修正文案和 Column+weight 布局方向符合裁决，但保留两项独立复核重点：显示页真实控件名是“显示安静阅读信息”，候选文案写“页眉页脚开关”可能无法让小白建立对应；新增 279 行 JVM 测试含源码文本扫描，需判断是否过度或脆弱；
- 全仓 diff 中同文件还包含任务开始前的既有改动，Qoder 必须按任务新增语义审查，不得把用户既有改动归因给 D-2-B；本阶段不做真机、不放行 S08–S20。
- 报告：`docs/coordination/agent-results/agent-result-d02b-zcode.md`。

#### D-2-B Qoder 独立代码复核

状态：`CODE PASS WITH P3；D-2-B-F1 REQUIRED BEFORE DEVICE`

- Qoder 独立复跑定向 JVM 2 类 / 36 项、androidTest 编译与主 Kotlin 编译均通过；确认五页作用域矩阵、数据语义、单文件边界与 Compose 高度推导成立，无 P0/P1/P2；
- Standards P3×7、Spec P3×3。Codex 采用严格产品标准，不接受“页眉页脚开关”与真实控件名不一致：该词可能把六个全局模板槽位误读成本书例外；
- F1 只需把显示页例外改为“沉浸模式、显示进度条、自动隐藏和显示安静阅读信息”，并让新增 Compose 用例在页面切换后等待 AnimatedContent 动画稳定；其余测试精简、注释归因与守卫增强均为非阻断 P3，后续清理；
- F1 由 Zcode 串行修改 `ReaderSettingsSheet.kt` 与两份已授权测试；Codex 核验后直接放行 Qoder 真机，不再增加一轮完整代码复核。
- 报告：`docs/coordination/reviews/undated/agent-review-d02b-qoder.md`。

#### D-2-B-F1 真机前收尾

状态：`PASS；DEVICE READY`

- Zcode 将显示页例外改为四个真实控件名，并给 D-2-B 新增 Compose 用例统一加入 250ms 主时钟推进与 idle 等待；产品只改 1 行文案，未扩大边界；
- Zcode 的最终报告因变异验证结束后未重跑而在磁盘留下 21 项 / 2 failures 的旧 XML；Codex 识别该证据缺口后新鲜重跑两类定向 JVM，结果 `ReaderSettingsScopeNoticeTest` 21/21、`ReaderSettingsRouterTest` 16/16，合计 37/37，0 failure/error/skipped，`BUILD SUCCESSFUL in 44s`；
- 旧文案在三个授权文件中零命中；F1 无 P0/P1/P2，放行 Qoder 真实手机最小 UI 验收。S08/S09 只读调查可继续并行，不与真机验收共享源码写权限。
- 报告：`docs/coordination/agent-results/agent-result-d02b-f1-zcode.md`。

#### S08 Zcode 只读调查与 Codex 裁决

状态：`INVESTIGATION ACCEPTED；IMPLEMENTATION HOLD`

- Zcode 确认 `feature/reader/edit/` 仅有未跟踪的 `ChapterTextDiff.kt` 与测试，生产调用方为 0；`edit` 仍由 `DORMANT_IDS` 封闭，当前没有用户可触达的章节编辑功能；
- 报告所列“批量无事务、LCS 无输入规模上限、备份不含纠错”是未来接线前的 readiness blockers，不是当前已生效的 P1 用户故障；现役产品轴仍为 P0/P1/P2 × 0；
- 报告称“单块 diff 可复用现有 `SaveSingleCorrection`”只在整个当前选区恰好等于该修改块时成立。若用户在较大选区内部只改一小段，`resolveSingleCorrectionAnchor` 会因 `draft.findText != selectedText` 拒绝，不能宣称零新写入通路；
- 不放行切片 A/B，不解冻 `edit`，不接入未跟踪 diff 引擎。S08 保持 HOLD，待明确数据模型、事务/撤销、备份、输入上限、格式范围及入口语义后另行设计；
- 报告：`docs/coordination/investigations/agent-investigation-s08-zcode.md`。窗口 B 可转入与源码写入隔离的 S19 只读调查。

#### S09 Qoder 只读调查与 Codex 裁决

状态：`INVESTIGATION ACCEPTED；S09-A-F1 IMPLEMENTATION READY`

- Qoder 对当前章页码、全书页码/进度、硬件键和点击区域四条能力链的分拆基本成立；`WholeBookPageModel` 零生产调用、三套点击热区比例和接线层测试缺口保留为后续候选，不与当前修复混做；
- Codex 交叉确认唯一读取 `ReaderPageIndexManager.position` 的书签创建路径未校验 `position.bookId == bid`，而 manager 只在分页分支更新且 `clear()` 零调用；切到另一书的滚动模式或新书首帧前创建书签，可把上一本书的绝对坐标持久化到当前书，SN-1 的当前可达 P1 成立；
- 调查报告把“无 position 的滚动书签跳不回位置”说得过重：当前跳转链在 locator 为空时会用 `progress_percent` 回退，仍可近似回读；真正的 P1 是跨书残留坐标被误写，滚动模式只有百分比回退则是精度/能力缺口；
- 首个实现片收窄为 S09-A-F1：仅让书签锚点接受当前 `bookId` 的位置，并补当前书/别书/null 三类行为测试；不接 `clear()` 生命周期、不新增滚动精确 locator、不改页码、硬件键或热区，避免把 owner 重构混入止血修复；
- 报告：`docs/coordination/investigations/agent-investigation-s09-qoder.md`。窗口 C 可实施 S09-A-F1，完成后须独立复核才能放行真机。

#### S19 Zcode 只读调查与 Codex 裁决

状态：`INVESTIGATION ACCEPTED WITH CORRECTION；IMPLEMENTATION HOLD WHILE DEVICE ACCEPTANCE RUNS`

- 正式安装入口仍为 `android/scripts/install_with_confirm.ps1`，契约测试、明确 serial、无人工确认、无安全设置修改、确认循环与直接安装回退分级等事实成立；未跟踪诊断脚本和临时 UI dump 均未被本轮删除或改写；
- Codex 将报告的 P2-3 上调为当前可达 P1：`-TestApk` 只控制 `adb install -t`，默认 `PackageName` 仍是主应用；正式文档又允许只传 `-Serial`/`-Apk` 安装 androidTest APK。主包已存在时，确认循环会用无关主包的 `pm path` 作为成功证明，不能证明目标 test package 已安装，可能制造虚假的真机验收证据；现有契约测试没有覆盖该参数组合；
- 报告中临时 UI dump 含具体测试书名且未被 ignore，作为隐私/误提交 P2 记录；不得在项目记录中复述具体名称。本轮不删除，因为删除属于破坏性清理且尚未获得独立清理授权；
- 修正计数为 P0×0、P1×1、P2×4、P3×7、readiness blocker×4。正式安装脚本加固须与契约测试成对修改，但窗口 D 正在使用安装/真机链，因此 S19-F1 暂缓，避免验收期间改变工具；
- 报告：`docs/coordination/investigations/agent-investigation-s19-zcode.md`。窗口 B 转入 S10 只读调查，不触碰安装脚本、临时工件或 `.gitignore`。

#### S09-A-F1 Qoder 实现与 Codex 独立复核

状态：`CODE PASS；S09-B IMPLEMENTATION READY`

- Qoder 仅在 `ReaderSheetHost.kt` 将分页位置统一收敛到 `bookmarkPositionFields`，只有 `position.bookId == 当前 bookId` 才生成 abs/chapter/char/locator；mismatch 与 null 均保持 `-1/0/-1/null`，既有 `progressPercent` 近似回退未变；新增 8 项生产纯函数行为测试；
- Codex 按 Standards / Spec 双轴独立复核，无阻断发现；任务外的 `ReaderSheetHost.kt` 大量差异为本片之前既有改动，不归因于 S09-A-F1；
- Codex 新鲜运行 `ReaderBookmarkPositionPolicyTest`、`TocNoteEntriesTest`、`ReaderProgressNavigationTest`，XML 合计 31/31，0 failure/error/skipped，退出码 0；随后 `:app:compileDebugKotlin --rerun` 退出码 0，唯一 warning 位于未修改的 `LibraryBrowserRoute.kt`；`git diff --check` 通过；
- 本片关闭跨书残留坐标写入 P1。保留两个非阻断 P3：尚无 UI→动作→仓储端到端测试；纯策略暂位于 `ReaderSheetHost.kt` 文件末尾。无需单独真机阻塞，可与后续 S09-B 一并做定向设备回归；
- 报告：`docs/coordination/agent-results/agent-result-s09-a-f1-qoder.md`。窗口 C 可进入 S09-B，文件集与 B/D 当前任务不重叠。

#### S10 Zcode 只读调查与 Codex 裁决

状态：`INVESTIGATION ACCEPTED WITH SEVERITY CORRECTION；IMPLEMENTATION HOLD`

- S10-A 与 S10-B 必须分开处理的结论成立：卷标题目前是目录渲染期字符串判据，无数据模型类型；六槽模板/10 token 数据链基本存在，但只在自研分页引擎渲染，且两个 `HeaderFooterPicker` 的折叠文案实现分叉；
- Codex 不接受 SP-1 为 P1：`TxtChapterDetector` 回答“是否建立章节边界”，`isVolumeHeader` 回答“目录是否把该标题作为卷分组”，职责不同；“第一篇”作为章节而非卷分组可能是合法产品口径。该项改列产品契约/readiness blocker，不作为现役严重故障；
- 现役主要风险保留为 P2：明确卷节点在目录隐藏、但仍参与其他章节消费端的语义分叉；EPUB 层级展平；两个 Picker 回显不一致；页眉页脚接线层缺乏真实渲染测试。滚动模式不消费页眉页脚与 S09 SN-3 同源，不重复算净新增；
- 修正计数为 P0×0、P1×0、P2×6、P3×11、readiness blocker×4（新增卷分类口径裁决）。S10 暂不实施：先裁决“卷是纯分组还是可读章节”与滚动模式页眉页脚契约；
- 报告：`docs/coordination/investigations/agent-investigation-s10-zcode.md`。窗口 B 不进入 S10 实现，转去优先关闭 S19-F1 安装验包 P1。

#### D-2-B Qoder 真机验收与收口

状态：`PASS / CLOSED`

- Qoder 在真实手机 `c49ac6cf` 构建并安装含 F1 文案的 APK；`install_with_confirm.ps1` 退出码 0、`RESULT: CONFIRM_LOOP`，无超时、无 MIUI 阻断、无 ADB 直装回退，本地 APK 与设备 `base.apk` SHA-256 一致；
- 五页 BOOK 与五页 GLOBAL 文案逐字正确，ROOT 无页级提示且翻页速度卡片显示“全局设置，所有书籍共用”；DISPLAY 使用四个真实控件名，未把六个全局模板槽位误写成本书例外；
- Codex 抽查报告原始文本、安装日志、最终回读及六张关键截图：竖屏/1.3 字号/横屏均完整可读并可滚动；DataStore 前后 SHA-256 一致、测试 TXT 位置保持 0.0%，通知权限、旋转、字号、stay-awake、作用域与应用停止状态均恢复；
- 验收工具曾误触亮度滑块，已仅通过 App UI 恢复，最终 DataStore 字节级零差异；不计产品缺陷。最终 P0/P1/P2=0，保留 3 个 P3：安装器翻转通知权限、动画中间帧新旧内容共存、短横屏需下滑查看根页说明；
- D-2-B 正式关闭。242 MB 真机证据保持未跟踪，不默认纳入 Git；报告：`docs/qa/2026-09/2026-09-22-d02b-qoder-device-acceptance.md`。D 窗口空闲，S19-F1 的工具修改等待条件解除。

#### S19-F1 Zcode 实现与 Codex 独立复核

状态：`PARTIAL；S19-F2 REQUIRED`

- Zcode 已用 `$PSBoundParameters.ContainsKey('PackageName')` 阻止 `-TestApk` 省略包名时进入设备链，并补齐 fake-adb 契约、主 APK 兼容性及 COMMON 文档；改动范围符合授权；
- Codex 新鲜复跑契约测试，65/65、0 failure、退出码 0；两个 PowerShell 文件语法解析均为 0 error，`git diff --check` 通过；未运行真实 ADB；
- Standards 轴无阻断发现。Spec 轴保留 1 个 P2：显式传入仅含空白的 `-PackageName '   '` 会绕过守卫并开始调用 ADB，不符合“没有真实测试包名时必须在任何设备操作前失败”的契约；Codex 以清空 PATH 的无设备探针复现到首个 adb 调用；
- S19 原始“省略包名后误验主包”P1 已被候选代码覆盖，但本片暂不关闭。F2 仅需把 null/empty/whitespace 与未显式传参统一快速失败，补零 adb 行为测试并修正报告中的 U3/P3 结论。

#### S12 Qoder 只读调查与 Codex 裁决

状态：`INVESTIGATION ACCEPTED WITH SEVERITY CORRECTION；IMPLEMENTATION HOLD`

- Qoder 只新增 `docs/coordination/investigations/agent-investigation-s12-qoder.md`，未修改产品源码、测试、脚本或既有文档；生产链静态接线、TXT/Markdown/EPUB 格式矩阵、预览/确认缺口与测试矩阵均可复核；
- Codex 接受 3 个现役 P2：流式大 TXT 因目录为空把 Legado 标记回退到书首且无提示；导入章号百分比可经 `mergeReaderProgress` 把长篇末章静默推成 FINISHED；导入无 busy 门控且 equal 与 behind 共用“本地位置更靠后”反馈；
- Codex 不接受报告 P2-1 的可达性结论：导入 EPUB locator 的 v2 `offset` 确实把章内偏移写成全局偏移，但当前 `pendingSourceLocatorJson` 来自搜索/标注/临时路由或真实屏幕位置上报，没有生产调用把导入进度 JSON 直接注入该参数。该项并入既有 R-2“EPUB 单一写入契约”readiness blocker，不算现役 P2；
- “链路完整、可跑”只可表述为静态生产接线完整；因本轮未运行测试或设备，运行时可用性仍未证明。修正计数：P0×0、P1×0、P2×3、P3×5、边界风险×7、readiness blocker×4；S12 实现继续 HOLD，先裁决 EPUB owner、导入是否能改完成态与 dry-run 预览契约；
- 窗口 D 转入不重叠的 S15 时长单位只读调查，避免与窗口 B 的安装脚本 F2、窗口 C 的 S09-B 点击区实现发生文件冲突。
