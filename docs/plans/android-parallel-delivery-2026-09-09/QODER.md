# 发给 Qoder：R1 书籍删除与撤销完整性

请直接实施这项任务。工作目录必须为 `D:/develop/Code/Codex/cra-android-qoder-r1`，分支 `codex/android-qoder-r1`。
完整阅读仓库AGENTS.md与 `docs/plans/android-parallel-delivery-2026-09-09/{README,COMMON}.md`。
你不是唯一开发者：Trae负责阅读笔记，Codex负责reader与共享接口。不要改他人文件，不stage/commit/push。

## 拥有的路径

以 `android/app/src/main/java/com/creationreadingassistant/` 为根：
- data/repository/BookRepository.kt
- 新增feature/library/deletion/**及对应测试
- ui/viewmodel/ShelfBookActions.kt、ShelfViewModel.kt
- ui/screen/shelf/ShelfRoute.kt、ShelfSearchRoute.kt
- ui/screen/readinghistory/MyReadingRoute.kt
- 新增res/values/strings_deletion.xml及同名locale资源

全部既有DAO/entity/AppDatabase/schema、NoteRepository、Profile文件归他人；必要改动写SEAM REQUEST。

## 先确认的缺陷

当前deleteBook清除分类/标签/书单关系和chapter_reads；restoreBook没有对称恢复，并会按bookId捞起历史已删除的笔记/高亮等。不要只修UI文案。

## 必须完成

1. 建立一次删除操作的明确作用范围。快照只包含本次操作前处于活跃状态的资料及关系；已有删除项不纳入撤销。
2. 删除与撤销具备事务一致性；恢复书籍、进度、阅读记录、笔记、高亮、分类、标签、书单、已读章节以及本次影响的必要正文状态。
3. 不覆盖操作之后的合法新修改；关系目标已删除时有明确冲突处理，不创建孤儿或重复关系。重复撤销幂等；批量删书的撤销范围与入口提示对应。
4. 第一版可以采用有明确有效期的会话撤销凭证；凭证不得全局无界积累。不承诺重启仍可撤销，重启后不显示失效撤销按钮。若必须落库，先提schema请求给Codex。
5. 书架、书架搜索、阅读历史入口一致；明确区分搁置、移除正文、删除整本资料，不能把清缓存当成完整备份或可靠恢复。
6. 移除正文/重新关联的完整设计放入报告：内容哈希匹配、文件变更时定位风险、资料保留；该扩展D2本轮不擅自改导入器，将由R2实施。

## 必要回归

含分类/标签/书单/已读章书籍删除后撤销；已有软删除高亮不复活；批量与单本；连续删除；重复撤销；失败事务；撤销期间关系变化；本次删除前无正文/无记录；窗口过期。
优先通过已有仓储/DAO完成，用真实行为测试证明问题；不能用原始SQL绕开共享所有权。
保持既有调用方兼容；开发验证预约串行。设备交WorkBuddy，COMMON明确允许脚本失败后adb install回退。
完成后写 `docs/plans/android-parallel-delivery-2026-09-09/reports/qoder-r1.md`，说明实现、测试、实际限制和接口请求，不自动开R2。
