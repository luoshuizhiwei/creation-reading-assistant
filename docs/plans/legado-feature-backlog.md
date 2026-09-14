# 向 legado 补齐功能的排序清单（backlog）

> 2026-09-09：用户新增授权的八组功能需求与执行顺序见 `android-parallel-delivery-2026-09-09/README.md`。本文其余未被新需求覆盖的条目仍是候选；不可把新授权范围扩大为整份历史 backlog。繁体显示与 TTS 定时停止已有实现，应先核对入口/覆盖；安全滚动 TXT 替换仍待修复。

> 2026-08-16 状态更新：已完成条目——内核主线 P4～P6；第一梯队 1（翻页动效，
> curl 按决策映射为 cover）、2（音量键）、3（自动翻页/滚动）、4（护眼）、6（亮度）；
> 第二梯队 8（字体导入）、9（内置 TXT 目录规则）、13（页眉页脚四槽）、
> 19（笔记/书签/灵感导出）。第 7/15 项已附核实标记。
> 其余条目仍是候选 backlog，不代表已经承诺实施。

> 2026-07-27 制定。依据：`docs/research/legado-with-md3-analysis.md`（上游 legado-with-MD3 全面分析）
> 与 `android/` 现状盘点。排序标准来自 CLAUDE.md：**是否让阅读体验更好或应用更流畅；
> 不是的排后面。**
>
> 范围约束（已定，不重复讨论）：
> - 本应用只读本地书（TXT / EPUB / MD），所有依赖书源、网络抓取的功能一律排除；
> - 不做服务端。已有局域网同步 + WebDAV 备份，够用；
> - 阅读内核自研（SIDECAR-ZH 方案），legado 代码只作参考改写，不整体引入；
> - 许可证：用户已确认仅自用不分发，GPL 义务不触发，可直接参考改写上游算法
>   （将来若分发需补来源说明与 GPL 材料）。

---

## 一、现状速览（避免重复造已有的轮子）

盘点 `android/` 后确认**已经有**的能力，backlog 里不再列：

| 领域 | 已有 |
|---|---|
| 书架 | SAF 导入 EPUB/TXT/MD、导入队列与历史、搜索、4 种排序、状态筛选条（未读/在读/已读完/可读，等价 legado 的虚拟分组）、书单/分类/标签三套组织（Room 关联表）、批量操作、文字封面生成、书籍详情（含阅读历史图表）、格式筛选、标签多选、EPUB 内嵌封面提取、导入内容哈希查重 |
| 阅读器 | paged/scroll 两模式；自研排版内核 `feature/reader/layout/`（中文禁则、两端对齐、字形簇、API 35 letterSpacing 探针）已落地 P1；TXT 章内真翻页（`pagerEngineMode` 开关，P2）；长按选句 + 选区/TTS 句高亮（P3 第一刀）；字号/行距/段距/边距/粗体；7 种背景色（含 oled-black）；亮度压暗遮罩；沉浸式/常亮/菜单自动隐藏/进度条；三区/五区点击 |
| TTS | 系统 TTS + MediaSession 通知栏控制、语速 0.5–2.0x 连续调节、音调/音量/音色、定时停止、跨会话续读、句高亮、章末自动接续（EPUB/MD）、拔耳机暂停 |
| 笔记 | 书签、笔记、高亮、灵感摘录（SelectionToolbar 五动作），Profile 与书籍详情均有汇总入口 |
| 搜索 | 书内全文搜索（与 ReaderDocument 同源、进度+取消）、全局搜索（书名/作者/标签 + 正文预览命中） |
| 统计 | 会话表 + 今日/7 日/30 日/连续天数/按书时长（`StatsRepository`） |
| 同步 | 局域网配对同步（QR 配对）、WebDAV 备份 |
| AI | 自配接口的 AI 助手（解释选段等），Key 加密存储 |

**已排定、优先于本清单一切项的主线**：`docs/architecture/native-android-reader.md` 的
P3 收尾（双把手拖拽选区）→ P4（EPUB 分页 + 翻页动效）→ P5（locator 与进度）→
P6（排版收尾 + `pagerEngineMode` 灰度默认开）。这条线本身就是「真正的左右翻页」，
是第一优先级的阅读体验，**不要被下面任何 backlog 项打断**。

---

## 二、第一梯队：直接改善阅读体验（内核 P4–P6 完成后立刻做）

### 1. 翻页动效参数对齐 legado（并入内核 P4，不是独立项）

- **已完成（随 P4 接线）**：none/fade/slide/cover 四档生效；curl 按决策映射为
  cover 并改文案（`SettingsStore.pageTurnEffect`）。
- **对阅读体验**：高。翻页手感是「像不像一个真正的阅读 App」的第一观感。
- **抄什么**：legado 三个不起眼但决定手感的参数——动画时长按位移比例算
  （`speed × |dx| / 屏宽`，短距离不显慢）；淡入淡出松手阈值 10% 即翻；
  反向拖回即取消翻页。动画本身用 Compose `graphicsLayer` + `Animatable` 重写，
  别搬 View 代码。
- **工作量**：P4 计划内（约 700 行），额外 0 天。
- **依赖模块**：`feature/reader/pager/`（TxtPagedController、PagedTxtReaderHost）、
  `SettingsStore.pageTurnEffect`。

### 2. 音量键翻页

- **是什么**：按音量上/下键翻上一页/下一页；朗读播放时音量键是否仍翻页做成开关。
- **对阅读体验**：高。单手躺着看书的刚需，legado 用户迁移过来第一个找的就是它。
- **抄什么**：legado 的按键防抖区分——按键取首（立即响应，600ms 内不重复）。
- **工作量**：1–2 天（MainActivity 拦 KeyEvent 转发给阅读器 + 两个设置开关）。
- **依赖模块**：`MainActivity`、`ui/screen/reader/ReaderScreen.kt` 手势层、`SettingsStore`。

### 3. 自动翻页 / 自动滚动

- **已完成**：paged 模式逐渐揭开（AutoRevealProgress）+ scroll 模式匀速滚动
  （AutoScrollAccumulator 亚像素累积防偏慢），速度 1–10 档工具栏内联调节，
  会话态启停（重进书不擅自继续）。
- **对阅读体验**：中高。长时阅读场景的常用功能。
- **抄什么**：AutoPager 的帧累积算法——用 double 累积每帧滚动余数、攒够 1px 才消费，
  否则速度会因取整偏慢；非滚动模式用 clip 逐渐揭开下一页并画一条分界线。
- **工作量**：2–3 天。
- **依赖模块**：`feature/reader/pager/`（须在 P4 之后，动画基建就绪）、
  ReaderScreen 菜单、`SettingsStore` 新增速度项。

### 4. 护眼色温滤镜 + 按时段自动开启

- **是什么**：给屏幕叠一层暖色温（不是现在的压暗遮罩），可设「22:00–07:00 自动开」。
- **对阅读体验**：中高。夜读人群刚需；现有 brightness 遮罩只压暗不改色温。
- **抄什么**：legado `EyeProtection.kt` 本来就是 Compose 实现——三条二次多项式
  拟合 2596K–5500K 色温出 ColorMatrix，跨零点时段判定逻辑也现成。系数是物理拟合
  的事实数据，可直接用。
- **工作量**：1–2 天。
- **依赖模块**：ReaderScreen 根层绘制、`SettingsStore`。
- **备料已就绪**：设计稿（含算好的色温系数表、Compose 实现、定时判定、验收标准）
  见 `docs/plans/backlog-04-eye-care-filter-design.md`，实施时照单做。

### 5. 章节流式排版（第一页先出）

- **是什么**：打开大章节时不等整章排完，排出第一页就先显示，剩下的页在后台继续排；
  没排完时页码显示「~N」。
- **对阅读体验**：高（属于流畅度）。这是"打开书快不快"的决定因素。
  **先测再做**：如果真机实测当前 `ChapterPaginator` 排 50 万字典型章节 < 200ms，
  此项降级为不做。
- **抄什么**：结构不抄代码——排版跑后台、每页产出即推送（Channel 增量出页）、
  循环里密集检查取消、消费端包 30s 超时防挂死。
- **工作量**：2–4 天。
- **依赖模块**：`feature/reader/layout/ChapterPaginator`、`feature/reader/pager/TxtPageSource`、
  `pager/PageIndexStore`（页索引边排边写）。

### 6. 亮度直控 + 滑动调亮度手势

- **是什么**：阅读菜单里的亮度条直接调系统窗口亮度（现在是 45–100 的压暗遮罩，
  最低也不够暗）；可选做屏幕左缘上下滑调亮度。
- **对阅读体验**：中。夜间最低亮度不够暗是真实痛点。
- **工作量**：1–2 天（`window.attributes.screenBrightness` + 跟随系统开关；
  遮罩保留作为额外压暗）。
- **依赖模块**：ReaderScreen 菜单、Activity window、`SettingsStore`。

### 7. 全书页码 / 剩余页数显示

- **是什么**：页脚可显示「全书第 N / 共 M 页」和「本章还剩 X 页」，
  而不只是百分比。
- **2026-08-16 核实现状**：页脚 `PAGE_NUMBER`（`PagedTxtReaderHost.resolveItemText`）
  已显示**章内**「X/Y」页码；全书维度未做。跨章预分页属于本项主体工作量，
  待第 5 项（流式排版）真机性能结论后再排。
- **对阅读体验**：中。纸书感与"还剩多少"的掌控感。
- **抄什么**：pageestimate 的思路——未排版章节用启发式估算（返回连续浮点页数，
  不取整，否则校准回归会把取整偏置算两遍）；排过的章节把精确页数写回 Room；
  排版参数哈希成签名，字号一变整表失效。
- **工作量**：3–5 天（可复用现有 `ReaderPageIndexDao` / `PageStartsCodec` 的签名机制）。
- **依赖模块**：`feature/reader/pager/PageIndexStore`、`data/local/dao/ReaderPageIndexDao`、
  PagedTxtReaderHost 页脚绘制。

---

## 三、第二梯队：向 legado 补齐的常用功能

### 8. 自定义字体导入

- **已完成**：SAF 选 .ttf/.otf 复制到 `filesDir/fonts`（单槽位），LruCache(4) +
  失效检测；字体路径已纳入排版签名（`PaintTextRuler.typefaceKeyOf`），
  换字体页索引自动失效重排。
- **对阅读体验**：中高。字体对中文阅读观感的影响仅次于排版。
- **抄什么**：content:// 与文件路径两种 Typeface 加载 + LruCache(4) 缓存 +
  「旧字体一直用到新字体就位，中途不回落系统字体」的防闪细节。
- **工作量**：2–3 天。**关键约束**：字体路径必须纳入排版签名
  （layoutSignature），换字体后页索引全部失效重排，否则页码错位。
- **依赖模块**：`feature/reader/layout/android/PaintTextRuler`、`layout/LayoutConfig`、
  `SettingsStore`、ProfileScreen/ThemeSheet 设置入口。

### 9. TXT 章节识别规则可选与自定义

- **是什么**：目录识别错了（把"第x章"识别漏了、把正文行当成标题）时，
  用户能在内置的多套规则里切换，或自己写一条正则，重新识别目录。
- **对阅读体验**：中高。目录错 = 跳章、进度、TTS 全错。
- **2026-08-16 更正**：内置多套规则（标准/纯数字等 + 组合并集）与规则管理
  Sheet、按 profile.key 重识别并保留 source 偏移已完成（见顶部注记与第 15 项
  的规则体系）；本项剩余部分仅为「自定义正则的更多内置语料校准」。
- **抄什么**：legado `TextFile` 的内置目录规则集（十几套常见中文小说正则）
  可以整理成数据直接用；「按规则重新识别且不丢当前进度（按字符偏移恢复）」的流程。
- **工作量**：2–4 天（规则选择 UI + 重识别 + 进度保持）。
- **依赖模块**：`feature/reader/doc/TxtChapterDetector`、`doc/PlainTextDocument`、
  书籍详情 Sheet（入口）。
- **备料已就绪**：规则集草案（8 条规则的正则与用例、交互流程、误报对策、
  待验证清单）见 `docs/plans/backlog-09-txt-toc-rules-design.md`。

### 10. 阅读统计增强：日粒度 + 热力图 + 周期总览

- **是什么**：统计页显示 GitHub 贡献图式的阅读热力图、日/周/月/年维度的时长与字数
  汇总、单本书的阅读时间轴。
- **对阅读体验**：中。不改善阅读本身，但是留存与成就感功能，legado 分支的招牌。
- **抄什么**：三表模型——会话表是唯一事实源（已有 `reading_sessions`），
  日粒度聚合表和累计表都是可重算的派生；幂等写入（按四元组查重）；
  自动保存后立即用上次 endTime 重建会话避免空窗；<10s 的会话丢弃。
  热力图组件（周列 LazyRow 反向排列）可照着改写，无外部依赖。
  **别抄它的 bug**：上游把章节序号当字数存（words = durChapterIndex），
  字数统计要接排版层的真实字数。
- **工作量**：3–5 天（聚合表 + 迁移 + 热力图 + 总览 UI）。
- **依赖模块**：`data/local/entity/ReadingEntities`、`data/repository/StatsRepository`、
  `ui/screen/StatsScreen`、Room 迁移。

### 11. 主题增强三件套：应用级纯黑 / 动态取色 / 自定义主题色

- **是什么**：① 夜间模式整个 App（不只阅读背景）纯黑省电；② Android 12+ 跟随
  壁纸取色（Monet）；③ 用户选一个颜色生成整套主题（MaterialKolor）。
- **对阅读体验**：中。阅读页背景已有 7 色，这是把 App 其余界面也做体面。
- **抄什么**：分析文档 §1 的 A1–A4 迁移路径——BaseColorScheme 抽象（12 行）、
  ThemeEngine 唯一出口、AMOLED 后处理（4 行 copy surface=黑）、
  动态取色 SDK<31 回落。明确**不抄** LegadoColorScheme 52 色槽和 Miuix 双引擎。
- **工作量**：纯黑 0.5 天；动态取色 0.5 天；MaterialKolor 种子色 1 天。
- **依赖模块**：`ui/theme/Theme.kt`、`SettingsStore.AppearanceSettings`、ProfileScreen。

### 12. 简繁转换

- **是什么**：繁体书一键转简体显示（或反向），不改原文件。
- **对阅读体验**：中。对看繁体资源的用户是刚需，其余用户无感。
- **做法**：显示层做**按字等长**转换（一简对一繁），保证字符偏移不变，
  书签/搜索/TTS 三条链路才不会错位；词组级转换（OpenCC 完整模式）会变长度，不做。
- **工作量**：1–2 天（字表内置，几十 KB）。
- **依赖模块**：`feature/reader/doc/ReaderDocument`（显示映射层）、`SettingsStore`。

### 13. 页眉页脚内容可配置

- **已完成（四槽形态）**：页眉左/右 + 页脚左/右四个槽位各一个下拉
  （无/章节标题/书名/时间/电量/页码/进度%，`HeaderFooterItem` 枚举），
  未做 legado 的 12 token 自由模板；全书页码维度见第 7 项现状标记。
- **对阅读体验**：低中。个性化打磨项。
- **抄什么**：legado 的 12 个占位 token + 6 个位置（页眉/页脚 × 左中右）的模型，
  UI 简化成每个位置一个下拉即可，不必做自由模板字符串。
- **工作量**：2–3 天（电量/时间要接系统广播并按分钟刷新，注意别整页重绘）。
- **依赖模块**：`feature/reader/pager/PagedTxtReaderHost`（页眉页脚绘制）、`SettingsStore`。

### 14. 点击区域动作自定义

- **是什么**：九宫格每个格子点了干什么（翻页/菜单/切章/书签/目录/朗读暂停…）
  由用户配。现在只有三区/五区两种固定布局。
- **对阅读体验**：中。左右手习惯不同的用户（如"点左边也是下一页"）需要它。
- **抄什么**：legado 的 14 种动作枚举表；**别抄**它 `trRect 0.36f` 的笔误。
- **工作量**：2 天。
- **依赖模块**：ReaderScreen / PagedTxtReaderHost 手势层、`SettingsStore`。

### 15. 替换净化规则（谨慎，排本梯队末尾）

- **是什么**：用户写正则把正文里的固定噪音（错字、奇怪符号、网站水印）替换掉。
- **2026-08-16 现状**：引擎/Room 存储/RulesSheet 管理 UI/替换预览/有界投影 seam
  （`BoundedReplaceProjector`，含 display↔source 偏移映射）已实现并有单测；
  **正文渲染未接线**（`effectiveReplace` 生产零消费，分页签名不含规则身份）。
  接线方案已核实并落档：
  [`plans/replace-rules-render-integration-design.md`](replace-rules-render-integration-design.md)
  （四片交付：装饰器 source → pager 控制器坐标翻译 → 宿主渲染翻译 → legacy/滚动路径；
  实施前须与 reader 引擎 WIP 协调文件所有权）。
- **对阅读体验**：低中。本地书没有书源广告，需求比 legado 场景弱得多。
- **风险**：替换会改变正文长度，动摇字符偏移这条命根子（书签/进度/选区全挂）。
  若做，只做**显示层等长替换**（替换成等长占位或删除并重建偏移映射表），
  或干脆等 P5 locator 体系稳定后再评估。
- **工作量**：3–4 天（偏移映射是主要成本）。
- **依赖模块**：`doc/ReaderDocument`、Room 新表、P5 的 locator 机制。

---

## 四、第三梯队：创作向与锦上添花（阅读侧饱和后再动）

### 16. 书籍知识库：人物卡 / 事件线 / 关系 / 世界观 / 大纲

- **是什么**：给每本书建人物档案、人物关系、世界观设定条目、树形大纲，
  手写为主、AI 辅助为辅。这是「创作」定位下最有价值的一块。
- **对阅读体验**：无直接改善（所以排第三梯队，尽管价值高）。
- **抄什么**：legado 分支 `BookKnowledge.kt` 的 5 表建模几乎可以整体搬——
  共同字段 `source(user/ai/import) + confidence + status + schemaVersion + evidenceJson`
  一次解决"AI 生成与手写共存、可审核、软删除、可溯源"；
  `scopeStartChapter/scopeEndChapter` 做防剧透可见范围。
  注意：上游这些表没声明外键，删书要手动清理——我们要加 CASCADE。
- **工作量**：建模落库 1–2 天；最小闭环 UI（人物卡 + 设定集）1–2 周。
- **依赖模块**：Room（新 5 表）、`ui/screen/InspirationScreen`（入口整合）、
  `data/ai/AiClient`（可选的 AI 识别人物）。

### 17. AI 非破坏性正文批注层（BookContentProcess 思路）

- **是什么**：AI 润色/改写建议不直接改原文，而是存成一条条可单独启停、
  可失效（原文变了自动作废）的"处理记录"，叠加在显示层。
- **对阅读体验**：无直接改善；是 AI 助手从"问答"走向"改稿"的地基。
- **工作量**：模型 1 天；渲染管线（原文 + 有序处理列表 → 展示文本）约 1 周。
- **依赖模块**：`data/ai/AiClient`、`doc/ReaderDocument`、Room、P5 locator。

### 18. 封面共享元素转场

- **是什么**：书架点封面 → 阅读页，封面平滑放大过去的转场动画。
- **对阅读体验**：低（纯视觉愉悦）。
- **抄什么**：统一 key 生成、路由带 coverPath 让目标页数据未加载就能参与转场、
  圆角插值防硬跳——三个思路；Navigation-Compose 下要层层透传 scope，代价比上游大。
- **工作量**：3–5 天。
- **依赖模块**：`ui/navigation/AppNavigation`、ShelfScreen、ReaderScreen。

### 19. 笔记 / 书签 / 灵感导出

- **是什么**：把一本书的书签、笔记、高亮、灵感导出成 Markdown 文件。
- **2026-08-16 已完成**：笔记 Sheet「导出」经 SAF `CreateDocument` 写 `.md`
  （书摘按章分组含批注 / 笔记 / 书签 / 灵感四节），另有「分享」走系统分享；
  Markdown 组装为纯函数 `buildNotesExportMarkdown`（`NotesExportTest` 锁定结构）。
- **对阅读体验**：无；创作素材整理的常见诉求，成本低。
- **工作量**：1–2 天（SAF 写文件）。
- **依赖模块**：`data/local/dao/InspirationDao`、书籍详情 Sheet。

### 20. 手柄 / 鼠标滚轮翻页

- **是什么**：蓝牙手柄摇杆、鼠标滚轮翻页。
- **对阅读体验**：极小众。
- **抄什么**：滚轮取尾防抖 200ms、按键取首防抖 600ms 的区分。
- **工作量**：1 天。
- **依赖模块**：`MainActivity.onGenericMotionEvent`、阅读器输入分发。

### 21. 多角色朗读（默认不做）

- **是什么**：TTS 朗读时不同人物用不同音色。
- **判断**：依赖人物识别（16 号知识库）+ 云 TTS 接入 + 分段路由，是 legado 分支
  里最重的子系统之一，投入产出比对单用户 App 不成立。**除非用户点名要，不排期。**

---

## 五、明确不做（与 legado 的差异就该差在这里）

| 项 | 理由 |
|---|---|
| 书源引擎 / 换源 / 书源调试 / 订阅 / 校验 | 本应用只读本地书，整条链路不适用 |
| RSS 订阅 | 同上，与阅读+创作正交 |
| Web 服务（Ktor + Vue 前端） | 不做服务端红线；上游实现 CORS anyHost 零鉴权，本身就是安全洞；局域网同步已覆盖跨端需求 |
| 漫画阅读器 | 不在产品范围 |
| MOBI / UMD / PDF 解析 | 用户书源是 TXT/EPUB/MD；PDF 若将来有真实需求单独立项，不属于 legado 补齐 |
| 发现页 / 首页模块 | 依赖书源生态 |
| 仿真翻页（真折角 curl） | SIDECAR-ZH §8 已决策：需 mesh shader，映射为覆盖动画并改文案，不再翻案 |
| Miuix 双引擎 / LegadoColorScheme / Navigation 3 / Koin | 上游的历史包袱或独有需求，抄了是纯负担（分析文档各章"不要抄"结论） |
| 净化规则照搬 legado 的 ContentProcessor | 见 15 号：偏移安全前提下重新设计，不照搬 |

---

## 六、落地顺序建议（一句话版，2026-08-16 修订）

已完成的 1/2/3/4/6/8/9（内置规则）/13/19 不再排队。剩余按：

1. 5（章节流式排版）先真机测排版耗时再决定做不做；7（全书页码/剩余页数）随后；
2. 15（替换净化）按既有设计文档接线（见该条目链接）；
3. 10（热力图）→ 11（主题三件套）→ 12（简繁）→ 14（点击区域）视用户反馈插队；
4. 第三梯队等阅读侧饱和后，从 16（知识库）开始转向创作。

> 现行整体排期见 `plans/2026-08-16-android-followup-roadmap.md`（收口与真机验证 →
> 首次发版 → 质量门禁 → 功能深化），本清单只覆盖其中"阅读体验候选"一角。

## 七、抄 legado 时的已知坑（每次动手前过一遍）

- **words 字数 bug**：上游把章节序号当字数存，统计全错。接真实字数。
- **trRect 0.36f 笔误**：九宫格右上区域坐标写错，靠判定顺序侥幸正确。别照抄。
- **flowUnread 漏私密过滤**：两套并行 SQL 只改了一套的教训——过滤条件抽成常量。
- **API 35 letterSpacing 行为变更**：首尾字符不再计入字距。本项目已有
  `LetterSpacingProbe` 处理，改排版代码时别绕开它。
- **占位字符**：上游用汉字「袮/꧁」占位图片，正文真出现会被误替换。
  SIDECAR-ZH 已决策用 U+FFFC，遵守。
- **单文件巨兽**：上游 ReadBookViewModel 271KB 是反面教材；本项目
  `ReaderScreen.kt` 已 177KB，往里加功能前先考虑拆文件（sheet 拆出去）。
