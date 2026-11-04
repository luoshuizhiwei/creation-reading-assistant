# 101-v1删除与索引提交竞态

| 编号 | 时间香港 | 当前/推荐模型 | 作用 | 依赖/并行 |
|---|---|---|---|---|
| 101-v1 | 2026-10-24 02:35 | workbuddy/cn:deepseek-v4.1-flash 1M不变；复杂数据一致性由同owner闭环 | 删除后旧索引提交的并发复现与最小修复 | 删除事务/索引写入门控相互依赖，Claude单owner；不制造旁支审查 |

````text
开始101-v1：只修既有S1正文索引与书籍删除交错可能写回孤儿terms/coverage的问题，实施→必要编译修正→定向验证→交付连续闭环。先读有效AGENTS/current及docs/coordination/tasks/050-099/2026-10-24-task99-integration-root-review.md，已加载未变复用。Root维护D协调/current，你只写E唯一workspace及本任务交付。保持现模型，确证quota保全报Root转ZCode，网络错不当额度；严禁Claude/Cowork/Claude-Code/DSH/.codex/memories读取写入（含自动记忆），不建自动化不发外部消息。

已核复用：99词典before真实行为红6中5AssertionError→after六VM+九EmptyNotice15/15原XML及源码SHA/新产品3e2ef0eab53e43e2d16b6e887c4e7edc88aa0b022b5a2beee049fb1227955d1d(52763889B)构建核同未设备。98r2实际函数23f54279的Root反例已拒绝/11离线通过接受，不再扩解析工具。96 TXT/最小EPUB与P1数据合同84JVM+生产Host路由充分，不重复确认式设备批。

101原explorer仅只读定位如下代码交错，尚未实际测试复现，不能提前称设备缺陷：SearchIndexRepository.ensureIndexedIncremental 471-474取得activeFlow.first活跃快照；processChunked 1015-1023继续处理快照BookEntity。BookRepository.applyDeletionLocked 245-254软删除并清索引，未使该书索引任务失效。runIfCurrentGeneration 282-298只核全库generation，书删不推进代次；旧写点仍可提交terms/coverage。全重建575-580才清orphan，searchContent不join activebook。单书indexSingleBook/reindexBookById 513-516/646-658 generation=null也须兼容。现有SearchIndexRepositoryRebuildConcurrencyTest 187-275/559-603只覆盖全库代次，BookRepositorySearchIndexTest96-116仅验删除调用清理，不证明交错安全。D2 relink旧内容错误尚未确证，不能顺手扩为另一个产品缺陷。

先在当前实现/直接测试核这些事实，建立真实Repository定向门闩并发测试：旧任务已取活跃书快照并暂停于索引提交前→执行真实删除/清索引路径→放行旧任务，断言两索引表无deleted book行。测试需走生产路径，不复制policy，不仅verify delete调用或静态字符串；若真实Room JVM现有测试能力能承载直接复用，不能为测试新建框架/依赖。保留可审行为红/原日志exitcode/XML，不用编译红冒行为红。若当前实现已保证安全且原假设不成立，直接给证据推翻，不制造修复。

证实后最小实现提交/删除一致性：防全库快照与单书在途旧任务在删除清理之后写回。优先现有共享事务/每书有效性门控，必须考虑check与commit之间的竞态，不能只在开头查一次活跃状态或写前非原子查询。避免每删一本就全库重建/全局取消放大；不引入通用任务框架。文件范围SearchIndexRepository.kt、BookRepository.kt及必要直接相关现有DAO事务接缝/定向测试；DAO或构造接缝确实需要才最小改并说明，保护既有WIP。保持全库generation重建并发取消语义、单书显式重建、正常活跃书可检索和删除原事务安全。不修改词典/TTS/设置/桌面。

同owner跑新增删除交错+相关全库重建并发/BookRepositorySearchIndex定向测试及必要main编译，不跑全库/161/旧98设备/99/97充分矩阵。测试绑定实际输入SHA/原红绿XML，失败修本问题必要hunk直到闭环。必要一次产品候选构建交付实际实现；当前产品3e2ef0ea52763889B覆盖前先找同SHA原字节保全，无则只保全必要单份；旧17bb在98/v2-integration/preserved已保全不复制，test23a2618e已保全不重建。此批无ADB/设备操作，不用Mock或JVM称实际数据库设备/实体手机通过；若只能测局部，准确列剩余事务验证边界。

唯一workspace E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android；Gradle D:/develop/env/gradle，JDK D:/develop/Java/jdk-17.0.14，SDK D:/develop/Android/Sdk，锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock，你是唯一构建owner。输出E:/creation-reading-assistant/task101-index-deletion-race-v1，最小源码/patch/日志XML原证据预算4MiB，必要build预计256MiB，若无既有3e2ef0ea保全则必要单份52763889B另列。复制前估空间，报告实际字节/保留理由/build增量条件；不复制工程/build/.gradle/.kotlin/缓存/依赖/历史证据/APK对，不删移动压缩迁移存量。

交付源最小patch/前后SHA/实际输入产物清单/红绿原日志exitcode/XML/REPORT/ready，区分实现/离线/JVM/构建/设备未验、恢复无新操作；无Git暂存提交pushPR发布、D回灌、正式库/HOLD/云部署/关闭其他进程。Root核后继续已有实际未闭合目标，不把单批完成当项目停止。最终最末单独写：这是任务101-v1。
````
