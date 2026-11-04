# 任务16-v4与22-v1核查及下一批

## 任务编号/版本

Codex-review-16v4-22-v1。用户“都完成了”触发核实际交付再推进。两报告均已交付；16-v4需要返修，22-v1只读调查限定采纳。新的16-v5与23-v1可并行，不重复已交付调查。

## 输入指纹

HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存空。Root共同输入1001项仍匹配manifest d4d062a807a421488adb8fa50f441cdda69c935c4d2c9fabd5c8625034b01db4、tree 5cd1db936648b0121873889358d568ee69916fb69b361415beb45d1548cde08b。

16-v4最终1004项全部SHA匹配，manifest 6046c88cacfe679f49257b6ab67a2f0fff6e54961461890a33cd1ab414ad33d4、tree f25e8dc168211d07ad1fdcba636c14eb4ca57b47d0fa9ca6fe2be19b28927680；相对Root仅BookRepository.kt生产1改/新测试3增/0删。patch SHA c09958af319996c15e6d42d976b4ea88e9296629fb1c441986cc9ade19a6a667。

22矩阵6环节绑定Root1001输入一致。报告及矩阵SHA、原始XML逐文件SHA等见[Root核查工件](<E:/creation-reading-assistant/tasks16v4-22-codex-review-20261012/verification.json>)。

## 所有权与改动

16-v4只写其E候选；本轮不吸收其生产修复，共同输入保持1001。22只写两份自有报告，无产品增量。Root维护本轮报告/current/数字索引/现行工作流，写前存完整原字节并核指纹；不回灌D产品。

## 环境

16可见模型标识解码为workbuddy AI/global:deepseek-v4.1-flash，应用回执未独立证明；22自报ZCode/new-provider/glm-5.3-flash。推荐与实际标识分开。

本轮只读发现外部MuMu/ADB进程仍存在，未操作、接管或关闭。本批后续16/23仍离线；构建用E共享Gradle排他文件锁串行，源码实现可并行。

## 执行与结果

### 16-v4：调查与回归证据可采纳，修复不合流

Root实核原XML：green_v1 100套件/877项、0fail/error/skip、机器exit0且test任务实际运行；green_v2同过滤UP-TO-DATE，不能当第二次独立执行。red_f_v2真实保存顺序类4项，2对照绿、2故障红，exit1。red_f_v1四项全红含夹具错误，不作为目标故障证据。

**阻塞验收：关键F类没跑绿。** green_v1/v2仅过滤ui.screen.reader.*、BookRepositoryTest、ReaderViewModelTest；ReaderSessionProgressSaveOrderTest位于data.repository，两目录没有其XML。因此877绿不证明F新四项修复成功。无需重跑877，只补准确目标及受新改动影响回归。

**确定新增回归：系统时钟回拨会拒绝合法新进度。** BookRepository.kt139–146/156用incoming.updated_at墙钟同进程记账。先接受10:00后校时到09:55，后续合法前进/回读都return，直到时间追回或进程重启。独立reviewer确认，必须改用真实生产派发边界的单调顺序/必要会话身份，不让数据库业务时间承担并发排序。

**范围缺口：EPUB入口未修。** EpubRepository.saveProgress仍读合并写；时间在IO入口才产生，不是原派发顺序。两种实际保存动作须覆盖，不能机械复制墙钟门。@Singleton限定同进程同实例的mutex作用成立，未把无实证跨实例推成缺陷；跨进程/其他DAO写入仍不在本轮保证内。

**D限定调查采纳，UNPROVEN保留。** 新测试用不同坐标调用真实guard/退出接缝，条件式风险成立；真实无手势布局事件可达性没证。字符串接线测试不等于UI事件。后续唯一设备owner应制造真实锚定后重测量，而非重复静止采样。报告“真机”仅引用历史MuMu，不能称实体手机。

### 22-v1：现有流程归属限定采纳

六环节已有入口与主要源码链、逻辑测试可复用，不从零开发。已有匹配执行证据仅是标明的模型/纯函数/保存/路由/导出格式层，当前Compose点击、SAF输出和完整原书删除链不因此通过；旧task15全量有已知WebDAV失败，不简称全量全绿。

真实缺口是灵感导出未独立输出payload.source/excerpts逐条来源与定位。聚合body可能已拼来源文字或摘录，不能说所有来源文字都没导出。跨书AnnotationExport只收高亮/笔记/书签，灵感不在其现有合同；23只补现有书内导出，不偷偷新增完整跨书精选导出。

归档筛选和编辑状态恢复在源码已存在，缺的是UI恢复交互证据；不作为已确认产品功能缺失。原书缺失的SET_NULL与payload快照保留是源码机制，完整实际删除读回未证就不扩大。22无需再派一轮报告返修；Root在本核查中收窄结论。

## 原始证据

- [16-v4报告](<E:/creation-reading-assistant/reader-session-risk-task16-v4/REPORT.md>)及logs/runs/red_f_v2、green_v1/green_v2、evidence源码清单、patch。
- [22-v1报告](<E:/creation-reading-assistant/material-journey-audit-task22-v1/REPORT.md>)与[journey矩阵](<E:/creation-reading-assistant/material-journey-audit-task22-v1/journey-matrix.json>)。
- [Root核查](<E:/creation-reading-assistant/tasks16v4-22-codex-review-20261012/verification.json>)；两独立只读审核结论已纳入本报告。

## 未覆盖项

F实际修复绿、EPUB保护及真正旧会话迟到回调隔离尚未成立；D真实UI可达性未证。最终共同源码/双APK及受影响MuMu尚未验。X整机清零、Back归因/队列排空/初始new save、实体硬件、实际LAN、正式签名发布等旧边界保留。局部877不是全产品比例。

## 恢复与副作用

Root不构建/重测、不写候选/设备，不修改D产品/Git，不动正式书库、原损坏仓库、桌面/HOLD/私有库/订阅网络。只写自有E核查/提示词及D协调文档，原报告保留不改。没有外部自动发送、定时任务或模型切换。

## 交付状态与下一步

本轮提供2个并行提示词：16-v5 DeepSeek，真实保存派发顺序/两repository窄修/准确F定向绿证据；23-v1 GLM5.3Flash，现有ReaderNotesExporter来源补全/定向JVM。输入独立E副本、文件互斥；Gradle拿锁串行，均无设备/最终APK。

用户本轮替换AGENTS，新增最小实现/反过度工程规则已纳入提示词：不为一次性操作建helper/工具类，不添加内部保证之外的推测性防御，不宽泛catch或静默默认。23的来源解析失败须明确反馈；必要时仅延伸ReaderNotesSheet现有导出/分享错误反馈hunk，不扩大UI功能。缺少合法可选字段与解析错误分开。

22调查退出，23是小范围实际导出改进，不是新增V0阻塞门。16-v5核过后由Root吸收有效增量，再安排唯一owner同版冻结及受影响MuMu，届时D真实重测量并入，不另制造静止采样任务。D产品回灌、stage/commit/push/PR/发布仍未授权。
