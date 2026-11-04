# 93-v1 A/B Root接收核验与94-v1闭环安排

## 任务编号/版本、输入指纹
93-v1-A/B-Root，2026-10-22。A采纳有限源码交付与纯策略验证，分类完整闭环尚有source保留缺口；B标签导出差量及定向JVM证据采纳。两包尚未合流/Gradle编译/构建/设备验收，当前22f6 APK不含这些增量。

A根E:\creation-reading-assistant\task93-light-organizing-v1\A；B根E:\creation-reading-assistant\task93-note-tag-export-v1\B。Root逐项实算A 15个源码/测试文件（7改生产+1新增生产+2新测试+5调用点适配）候选SHA及当前共同工作区before/新增缺席全部匹配；B两文件候选及共同工作区before均匹配。A/B所有权无交集；两patch和两REPORT SHA均与ready匹配。

A patch c6259c0cc24dc80abfdf0953a9540b8ebad7174868e0e18033ab57c39ce64ad7；VM候选488400712eb030f94506f72066d45c040dab28d1defcc84fa97a978b58e2420d；Editor候选44a72344d7a66fd831242655561717a9354c0ed275b834c8547455b1e877d20d。B patch3079468d94823ace917355a28c211d80abd8fb7f6e1b19b9d5535fcf74d3dc4b；Exporter由30230ca307bf7faea3c67907b0d4545264a7084bf3fe9dfd25d1ae293c3b37ed变为9fa52ffe9ef8e01319271694dcc0c6ef40e7420cc6295b48fddbe8ea4d73049b；NotesExportTest候选bf8412dd43c0a643eb0eba1698bf60279ed267b8f1a6c3b8e9d29ce2817b9be9。

## 所有权与改动、环境
两外部owner仅写各自E差量，未写唯一构建区。Root并行安排A只读原生审查，自己核B实际生产patch/原始日志及双方SHA，未重复全部测试。未发现task93/t93活动进程。唯一工作区仍E:\creation-reading-assistant\task59-common-candidate-v1\candidate\android，Gradle home D:\develop\env\gradle，构建锁沿current；Root或下一指定唯一集成owner独占合流/完整构建/设备。

A把既有Category字典接入编辑、列表/详情、筛选，draft.categoryIds使用null=未改、空列表=明确清空；Loading/Failed与unknown旧ID不产生删除。B仅ReaderNotesExporter与NotesExportTest，已有payload.tags在正文之后/来源之前独立成行，规范空白/去重稳定顺序并只转义标签，不改正文/来源/SAF。

## 执行与结果、原始证据
Root读取A logs/kotlinc-check.log的CATEGORY_LOGIC_OK checks=14与编译证据；这仅为真实生产纯策略运行，不是13新增JUnit/真实VM/Compose路径都已执行。A新增2类13项JUnit待共同Gradle运行，5处既有测试调用适配覆盖总8个直接VM构造点，审查未发现确定漏接。

B evidence/run/03-junit.log为OK(21 tests)，run-red同21项有3失败，实际生产Exporter/实体与真实测试使用既有Kotlin/序列化编译器和JUnit运行；这是独立JVM行为证据，不是Gradle或设备验收。Root核patch中规范化只作用标签，payload损坏仍走原fail-fast；已验来源/多行引用和保存器不重派。

原始报告/ready、前后source/baseline、patch及日志保留于A/B原目录；B真实样例evidence/run/05-sample.md。现有单一共同APK22f6d8c1778136c81a9a2ed904757d2e73d2ed4d5b0eacacbf74af56dfd227ae保持，不得称分类/标签导出已进入它。

## 已确认保留缺口与最小修复
A source/app/src/main/java/com/creationreadingassistant/ui/screen/inspiration/components/InspirationEditor.kt:137-145无条件重建InspirationSourceInfo，只写bookId/title/author/location/excerpt；chapterTitle、progressPercent、locatorJson落回null。:156传给draft，VM :284以draft.source覆盖旧source。

触发：现有带定位的阅读摘录→编辑→只修改分类→保存。来源精确定位字段丢失，临时查阅退化；书籍目录未加载时书名/作者也可能被清空。重建源的旧代码已存在，不能称93新引入根因，但新增分类流程的保留要求未满足，完整采纳前必须修。

最小方向：来源输入没有实际变化时复用原完整src0；显式改来源时再按实际变更处理，不能把旧locator无条件搬到新书/新位置。保留unknown分类ID及source/excerpts/adoptions/mergedInto等非目标字段。新增实际编辑草稿构造→VM upsert的回归，不能仅对resolveDraftCategoryIds helper自证；另验证取消不写入。不得因此扩全量来源重构。

## 交付状态与下一步
下一94-v1由一个集成owner连续完成：A来源保留必要补齐→17文件差量合流→新增/受影响定向测试→必要debug构建→一次分类/标签导出受影响联合设备验证→严格恢复/报告。旧93原件不回写，94补丁/前后SHA单独交付。属于A的必要编译/测试修复在同一owner闭环，不另开版本/工程或再分“只修编译”任务。

合流前重新核真实before与owner，不能覆盖漂移/WIP。覆盖构建输出前，当前稳定22f6 APK若无既有同SHA保全则只保全一份53,895,731B；不用重复复制旧33a08或完整APK对。构建集中现有workspace/cache，记录锁与退出码及新产物来源。

设备只针对新增分类可达/保存重开/组合筛选/取消、来源保留及实际标签导出；引用原已验SAF/保护/恢复机制，不重跑旧阅读矩阵、D/F/备份。通过真实产品入口建立中性数据，不写DB制造通过。导出创建物须本run归属/保全sizeSHA并精确收尾，同一主attempt失败即停并恢复，不能现场改后第二次main。

## 恢复与副作用、空间
Root及本批A/B均无设备动作，无新恢复义务。A约603,257B，B自报104,648B，各低于1MiB预算，无工程/缓存/APK复制。现有91 V0/92自用交付结论不因新增开发反向失效，新增量验证另计。

94预先预算：源码与运行证据≤8MiB，必要稳定APK保全53,895,731B（仅无现成同SHA副本时），现有build/cache增量按实际估算≤512MiB；不以超预算自行清理存量。实际范围与保留理由写同一REPORT，不搭新收据体系。

## 未覆盖项、停止条件
93目前不能证明Gradle/Compose/Room真实分类保存或设备通过，不能把独立JVM当APK验收。分类目录新建树/跨域重构/分类名导出/复杂工作台不在本批。输入/owner冲突、来源语义不明、必要恢复失败均保全并明确阻塞；首错/恢复错并存。无D产品回灌、Git/发布、正式库、桌面、HOLD/私库、云或存量清理；两个自动化PAUSED，实例不关不重启、结束保持运行。全产品分母未知，MuMu非实体手机/MIUI。
