# 93-v1 Android轻量归类与标签导出增量派发

## 任务编号/版本、依据与现态
93-v1-A/B，2026-10-22。用户纠正：V0收口不是全项目停止，要求继续主动给下一批提示词。限定V0/92交付结论保留；本批是既有Android轻量取材主线的产品增量，不再发重复验收或说明任务。旧2026-10-15 vault TODO的联合门禁未过描述已过期，不用它重开91已收口工作；本次不修改私人/自动记忆库。

依据现行workflow-replan的轻量归类与导出、产品总规划已授权笔记/素材方向；不启用桌面、素材工作台、专题视图、继续阅读卡片、新AI模式或历史backlog增强。

## 输入指纹、环境
基线为91/92 current与共同E候选；APK22f6d8c1778136c81a9a2ed904757d2e73d2ed4d5b0eacacbf74af56dfd227ae保持，不在实施者并行写差量阶段构建。唯一完整工作区E:\creation-reading-assistant\task59-common-candidate-v1\candidate\android；Gradle home D:\develop\env\gradle；构建锁E:\creation-reading-assistant\locks\android-gradle-exclusive.lock。Root负责公共接缝/合流/构建/设备，A/B只交自身最小差量。

## 原始证据与具体缺口
两个explorer只读不同生产入口，未做产品/设备修改。

A：InspirationPayloadData现有categoryIds，InspirationViewModel保存会保留、合并会取并集，但InspirationDraft及编辑/筛选UI没有消费分类字段。现有tags预填→保存→搜索及chips已经实现，不能再派一遍。现有TaxonomyRepository.observeCategories()可复用分类目录；CategoryEntity字典与BookCategory关联表区分，不能把灵感选择写成书籍分类关系。

B：真实阅读笔记Markdown入口ReaderNotesSheet→ReaderNotesExporter.buildNotesExportMarkdown。来源/多摘录/多行引用/损坏payload失败已有实现与NotesExportTest，80-H旧成果继续采纳。现有payload.tags未导出，不是原合同已证bug；本批明确增加“既有标签随灵感记录导出”的输出约定。只输出tags，不依赖A的分类命名映射，避免制造串行依赖。分类导出另随真实需求决定，不以原始ID冒充显示名。

## 所有权与改动
A写E:\creation-reading-assistant\task93-light-organizing-v1\A内必要源码差量/patch/测试/REPORT/ready，范围Inspiration编辑器、列表/详情/Route/Screen、InspirationViewModel及对应测试。复用既有分类目录，ID写payload.categoryIds、UI显示名称；只读分类字典，不改书籍分类关系、schema、TaxonomyRepository或共享构建配置。明确未改分类保持旧值、显式清空才清空；加载/未知旧ID不静默抹除。

B写E:\creation-reading-assistant\task93-note-tag-export-v1\B内ReaderNotesExporter及NotesExportTest最小差量，必要时仅自己的导出接线文件；不得写InspirationViewModel、分类UI或共享模型。Markdown每条灵感在正文/来源之外输出标签行，去除空白/空标签、按首次出现去重、顺序稳定、多行标签不能破坏结构；不对正文/引用全局替换，不改变损坏payload失败合同。

A/B真正并行，不相互等待，不改同一公共文件或当前构建工作区。Root接收两份后一次合流，运行新增/受影响定向测试和必要构建，再按变更决定单次受影响设备范围。必要编译返修由原owner继续，不再分新任务/环境。源码/测试前后SHA齐，未合流/未编译状态如实报告，不用文本断言冒充行为验证。

## 验证、空间、恢复与副作用
A验证选择→保存→再次编辑、组合筛选、显式清空、旧未知ID保留、标签/来源/摘录/采用字段不丢；B调用真实Exporter验证有/无标签、重复/空白/多行及特殊字符、来源/摘录不变。已有充分旧用例复用，不默认全量3674或设备重跑。

每包新增预算预计≤1MiB、上限4MiB，只交必要差量/测试/原始日志。现有依赖引用，不复制整工程/缓存/APK对。完整构建和设备owner仅Root；不是永久禁止设备，A/B前段没有新APK可供有效验收，须先合流。

## 执行与结果、交付状态与下一步
本次仅完成派发前定向定位与两份提示词，尚未启动A/B实施、构建或设备，没有新恢复义务。两个增量按上述新明确范围执行，不能称旧V0有新缺陷。后续收到完成消息，Root核实际差量/定向证据再合流推进，继续主动提供下一步，阶段收口不自动等于项目停止。

## 未覆盖项、停止条件
分类目录管理/新建树形分类、跨域taxonomy重构、分类名导出、复杂素材工作台和新产品线不在本批。身份/owner/真实语义冲突、需跨公共文件或schema时报告具体接缝，不自行越界。无D产品回灌、Git/发布、外部应用消息、正式库、桌面、HOLD/私库/云或存量清理。两个自动化PAUSED；实例不关重启。全产品分母未知，既有MuMu证据不等于实体手机/MIUI。
