# READER-PAGES-v1：阅读附页实施合同

日期：2026-11-08（香港）。Root已决定设计和范围；用户手动转交ZCode执行。状态：READY_FOR_IMPLEMENTATION；本文不是已实现或已验收声明。

## 1. 必须交付的结果

将阅读器的目录、完整设置、外观、灵感编辑四个入口，从下拉可关闭的长底部弹层改成三种完整页面体验：索引附页、正文预览调节页、摘录旁写编辑页。SETTINGS与THEME共用阅读外观设计语言，但保留不同入口与已有设置能力。不把全项目弹层一起重写。

特色来自真实书籍上下文、稳定的纸面结构和可预测返回；禁止以玻璃模糊、巨大圆角卡片、夸张3D翻书或装饰动画替代这四个实际交付。

沿用项目真实数据、字体/纸色、深浅主题与MotionTokens。GitHub参考仅用于信息结构，不复制外部导航库和业务：
- https://github.com/mihonapp/mihon/blob/main/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsMainScreen.kt
- https://github.com/Ashinch/ReadYou/blob/main/app/src/main/java/me/ash/reader/ui/page/settings/SettingsPage.kt
- 调研文档：`docs/coordination/2026-11-08-page-presentation-github-research.md`。

## 2. 开工输入与所有权

活动仓库：`D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919`。产品源码主载体为本轮实核D工作树；E历史candidate只读引用，不整体回灌、不以旧APK覆盖D产物。先读用户级/仓库AGENTS及current，记录HEAD、相关diff、当前APK与下列文件SHA；不清理WIP。

最近核对APK为52,812,949B、SHA256 `d9c5adcf364244365549eecb8b8df6f8be50b480e16599dfb97de6eaad352545`。它只是核对线索，若盘上已变，先核明确切输入/owner并记录，不退回旧包。UIX-CLOSE-v1旧问题及证据核清另按原合同，不把本设计实现算作旧语义修复或覆盖旧结论。

ZCode是本批唯一实施、集成与交付owner。读取不限制在下列文件，但改动限于：
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderSheetHost.kt` 的容器分派/必要参数；新增同包 `ReaderPageDialog.kt`，只供本批阅读附页使用。
- `.../reader/sheets/ReaderTocSheet.kt`、`ReaderTocChapterList.kt`、`ReaderTocBookmarkList.kt`、`ReaderSettingsSheet.kt`、`ReaderSettingsCommon.kt`、`ReaderThemeSheet.kt`、`ReaderInspirationSheet.kt`，仅实现本设计所需hunk。
- `.../reader/sheets/ReaderSheetScaffold.kt`仅新增向后兼容的全屏内容能力；旧调用默认行为不变；不得全局将所有sheet改成全屏。
- `ReaderLayerBuilders.kt`、`ReaderScreen.kt`、`ui/viewmodel/ReaderModels.kt`、`ReaderViewModel.kt`仅限本批返回/预览数据传递/灵感保存完成信号接缝；不改退出保存drain、临时回源LIFO、分页/Locator、TTS主流程。
- 对应现有Compose/单元测试与必要新增定向测试；本批交接文档。

不改ReaderChrome已通过按钮结构、全局Theme/MotionTokens参数、DB/schema/Repository数据语义、书架/桌面/导出/同步。确有边界外依赖时先说明具体路径和原因，继续独立部分，不顺手重构。

公共文件/Gradle/设备各单owner。禁止在别人操作手机时安装或跑测试；不得为并行复制完整工程。构建按current的工作区与锁进行，复用缓存。自动化继续暂停，不向其他应用自动发消息，不新增外部接力。

## 3. 容器和导航方案（已定）

1. 保留ReaderSheet枚举与现有OpenSheet/CloseSheet业务入口。只将TOC、SETTINGS、THEME、INSPIRATION分派至全屏容器，其他分支继续现有ModalBottomSheet。
2. 全屏容器使用Compose Dialog，`usePlatformDefaultWidth=false`，禁止外部点击关闭，按本项目窗口辅助组件处理edge-to-edge/insets/系统栏。Dialog仅包裹现有reader owner，不新建Activity/NavHost/ViewModel/阅读引擎，不dispose底层阅读正文。只在这一个容器实现进出过渡，内容页不各套一遍动画。
3. 页面铺满可用窗口，无拖拽把手、无底部sheet圆角轮廓、无垂直拖动关闭。全屏页滑动只浏览内容，明确返回/关闭按钮可达；关闭直接走CloseSheet，不等待旧SheetState.hide动画。不得将正常页面返回走reader退出保存/出栈。
4. 内容与返回关系：有键盘时Back先收键盘；设置子页Back到设置根页；根页Back回原阅读器；编辑页有修改时触发第6节草稿规则。可见返回按钮同样不能丢草稿。不得让Dialog系统Back越过子页/草稿检查直接关闭，也不得同时触发两个BackHandler。
5. 阅读器底层章节、位置、临时查阅身份、选区快照持续由原owner管理；只预览文字，不运行第二套正文渲染器。关闭不清空原有控件状态，不重复压栈。设置引起真实重排沿用现有进度保护。

## 4. 统一视觉和动效（已定）

- 布局使用“书内页”结构：顶栏56dp、两侧20dp（紧凑宽度16dp）、内容分节间距24dp/节内12dp；列表行至少56dp，所有交互热区至少48dp。大字时高度自适应，不能为满足定值裁剪文本。
- 页面背景、正文、强调、分隔线、次要文字分别取现有ReaderPaperTheme与MaterialTheme对应token；白天/夜间/自定义纸色全部支持。不硬编码统一米黄/黑绿，不引入字体文件或第三方设计依赖。书名/章节上下文使用已有serif排版能力（存在则复用），功能文字沿现有UI字体。标题22sp，正文说明14–16sp，辅助12sp，尊重系统字体缩放。
- 每页最有识别度的结构分别为“当前章节定位标记”“真实正文预览”“可折叠来源摘录”。不要为凑特色添加编号、封面海报、悬浮玻璃球或重复卡片。
- 支持动效时，全屏页从右侧24dp位移+淡入，180ms；退出反向140ms。采用既有同量级token优先，不全局改token。分类切换120ms淡入淡出，不左右叠加整页转场；来源展开约160ms。减少动态效果或系统禁用动画时直接到最终状态。不做全屏3D/截图翻页、blur、持续阴影动画，不用动画完成回调执行保存/导航业务。
- 横屏或可用高度不足时，将预览变为可展开的一行摘要，释放控制区；不强行沿用竖屏固定比例，不做本批平板双栏工程。

## 5. 四入口的具体实现

### P1 索引附页（TOC）

顶栏“返回／书名／定位当前章节”；下面保留章节、书签、笔记三Tab。复用TocSheet的所有真实数据与typed回源callback，包括TXT规则识别/管理、读标记等既有功能入口，不能删减来制造简洁。低频规则操作放“更多”菜单。

章节列表使用LazyColumn、稳定key；当前章节以起始侧2dp强调线+“正在阅读”文字表达，不能只依赖颜色。长章节名最多默认显示两行；完整标题可通过详情或展开访问，不让序号挤占标题。首次打开定位当前章节一次，重新组合、切Tab返回不强行滚回。列表位置按bookId和Tab保存。点击章节沿原onPick，书签/笔记沿原typed目标；关闭恢复阅读器，不新增进度写入。

有既有搜索则保留；若原目录尚无搜索，本批仅增加章节标题内存过滤（不建索引、不加DB查询），清空搜索恢复原列表位置，“定位当前”需清过滤后定位。规则/笔记已有子流程保持可达，不能嵌套第二层长底部sheet。

### P2 预览调节页（THEME）与完整设置页（SETTINGS）

THEME是特色入口。顶栏返回、标题“阅读外观”、动作“完成”；“完成”表示结束调节，保持原来的即时生效语义，不伪装成一次事务提交。作用范围“仅本书／全局”保持可见，并使用原onScopeChange/applyReaderEdit路径。

上方预览约占可用内容高度三分之一，竖屏限制128–240dp。显示真实书名、当前章与已加载的当前文字片段，优先利用`ReaderSheetDocumentState.contentText`，开页时按bookId/章节捕获最多800字符的稳定只读片段；无正文则显示真实加载/空状态，禁止编造测试正文。预览只消费设置样式，不加载文件、不启动分页、不触发进度事件，不在滑块拖动中更换文字。用字号/行距/纸色等现有有效设置渲染，不声称它是逐像素分页预览。

下方分类为“字体”“排版”“纸色”，分类切换只替换控制区。复用现有设置控件/回调，避免复制一套保存逻辑；亮度保留独立紧凑调节行。每组保留最常用控制与“完整阅读设置”入口，低频设置留SETTINGS。THEME→SETTINGS属于本次附页内层，Back回THEME并保留分类/预览，而非回正文；从原“设置”入口进入则根返回正文。入口来源由本次UI状态记录，不改reader导航栈。

SETTINGS保留ROOT、TYPOGRAPHY、PAGING、DISPLAY、EYE_CARE、ADVANCED既有分类与功能，解除旧`maxHeightFraction=0.62f`约束，采用满高页面、固定标题/范围栏、独立滚动列表。返回子页→根→来源，保持稳定lambda、persistProgress-before-settings与单书覆盖/重置/预设语义。不将设置项重新汇成一张长底部表单。

### P3 摘录旁写页（INSPIRATION）

顶栏“返回／记灵感／保存”；来源区显示真实书名/章节，摘录默认最多3行，有明确展开/收起按钮，无摘录不造占位引文。标题为轻量单行/可扩展字段，正文是主要编辑区；标签/分类默认收成摘要行，点击在页内展开，避免铺满标签挤掉正文。保存固定顶栏，键盘出现可达；正文有界滚动与imePadding只在正确一层消费，不能双重抬高。

草稿包含title/body/标签/分类，按书籍和本次编辑会话保存于UI/原VM可恢复状态，至少覆盖重组与Activity配置重建；本批不新增DB草稿表、不宣称进程被杀后必然恢复。Back先收键盘；相对初始草稿有修改再离开，使用简短确认“继续编辑／放弃修改”，禁止直接丢失输入。没有修改可直接返回。

当前D源码`ReaderLayerBuilders.kt`的onSaveInspiration派发SaveInspiration后立即CloseSheet并showNotice，`ReaderViewModel.kt`当前分支为异步io保存。这里必须最小补齐保存完成信号，不能在新页面继续提前报成功：保存中禁止重复提交；落库成功后才清选区、关闭并提示；失败保留所有草稿及可见错误，允许重试；同一次草稿重试复用同一entity id，防重复写入。复用现有repository保存方法，不改schema，不吞异常；取消协程不能变成成功。只接线当前书内灵感路径，不顺手改AI保存/其他页面。若已有并行修复提供该能力，先核输入后复用其hunk，不覆盖。

## 6. 任务顺序和退出标准

全部为同一个ZCode owner闭环，不拆成多个应用往返。共7项有依赖工作：
1. 登记输入/冲突，核UIX当前证据；本批PLAN只写实际文件清单和既定方案映射，不再把设计选择交回用户。
2. 实现全屏附页容器和Back优先级，先用一个真实页面打通进出，保住reader底层会话。
3. 完成TOC索引附页和所有既有子功能入口。
4. 完成THEME预览调节页及SETTINGS满高/来源返回接缝。
5. 完成INSPIRATION编辑页及最小保存完成信号/草稿保护。
6. 一次集中编译、定向测试、实际APK身份绑定、授权设备验收、恢复与视觉自查。
7. 整理最终证据、明确完成/未完成和入口，指纹合并后同步current及state本批条目。

完成标准：四入口均真实接线，滑动内容不会拖走页面，返回/设置范围/阅读位置正确，灵感保存失败保留草稿；长内容、大字、键盘和减弱动画可用，未修改无关数据路径。不能只交静态设计稿/HTML/测试demo，不能只换ModalBottomSheet名字或高度冒充本方案。

## 7. 验证与设备

复用充分历史证据，仅为改动和接缝增加验证。至少：
- Compose行为测试：TOC点击回调和返回、设置子页/来源返回、范围更新、灵感保存中防重复/失败留草稿/重试同id；merged树检查名称+Click且实际激活，不只字符串搜索。只编译androidTest不能报运行通过。
- 本批相关JVM测试、lintDebug、assembleDebug及assembleDebugAndroidTest（实际需要的变体）；原命令/rc/log保留，构建失败不继续用残留APK。不要求无关全量历史重跑。
- 真实设备验证先核当前owner和用户已授权的设备范围，fresh serial/model/boot及原包/数据/设置。已有同范围许可不再重复询问；单设备串行，不盲用历史serial。保护原数据，不pm clear/不擅自卸载；保持供电唤醒，结束恢复本轮真实基线。未能运行写NOT_RUN及原因，不回头用老机/模拟器冒充。
- 目录200+项、长标题/空列表/书签笔记切换；实际TXT/EPUB/MD打开和关闭附页后阅读定位不漂移；临时回源返回不破坏原LIFO；设置预览不写正常进度。
- 320dp窄屏、1.5字体比例、横屏、键盘、深浅纸色、减少动态效果/系统动画关闭；截图验证布局，短录像验证进入/调节/退出与快速连续点击。禁止纯录像宣称帧率。
- 对本批触及控件和reader返回/目录/外观/灵感/设置做必要无障碍回归，标签、热区、Click与实际功能共同核验。不重写上一轮已绿顶栏/scrubber，不安装TalkBack来凑结论，未验朗读如实保留。

## 8. 交付和反返工约束

交付根：`E:/creation-reading-assistant/reader-pages-v1/`。新增预算300MiB，先查空间，复用单一工作区、缓存和输入。交付PLAN.md、REPORT.md、ready.json、最小patch、必要修改源码SHA/构建log/XML、四入口前后截图/连贯短录像、设备恢复原证。最终冻结APK只保一套，避免每轮复制工程/APK对。

ready绑定源码清单、HEAD/工作树diff、APK/测试包SHA、构建测试原rc及实际验收范围；缺证据不得写PASS。现有WIP要在diff里区分归属，不把整仓脏改动算本批成果。当前入口/状态修改前核指纹并合并，历史原红不改写。

目标为一次完整交付+最多一次集中修正。首次交设备前owner自行完成四页面全部自查与可运行验证，收齐问题后一起修，禁止每改一个按钮就让用户转发。第二次还有失败时只停止该失败范围并交根因/已排除项，不强行收官，不继续制造r3/r4微批。一般编译与已授权技术接线直接解决，不反问用户设计选择。

禁止Git暂存/commit/push/PR/发布、删除迁移既有资产、自动化恢复、私有记忆写入、云库操作。认证、owner争用、输入被他人改写或数据风险时停止对应动作，报告具体差额；用户stop/Esc立即停新增输入。最终总结末行写“这是任务 READER-PAGES-v1。”
