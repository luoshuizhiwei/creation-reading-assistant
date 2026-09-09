# 发给 Trae：R1 统一阅读笔记

请直接实施这项任务。工作目录必须为 `D:/develop/Code/Codex/cra-android-trae-r1`，分支 `codex/android-trae-r1`。
先完整阅读仓库AGENTS.md及 `docs/plans/android-parallel-delivery-2026-09-09/{README,COMMON}.md`。
你不是唯一开发者：Qoder负责删除恢复，Codex负责reader与共享接口。不得修改他们拥有的文件，不stage/commit/push。

## 目标

“我的→阅读笔记”与书内笔记面板管理同一份高亮、批注、书签。用户能够找到、查看、编辑、删除、撤销和导出记录，并精确回到来源。

## 拥有的路径

以 `android/app/src/main/java/com/creationreadingassistant/` 为根：
- ui/screen/profile/ReadingNotesSubPage.kt、ProfileRoute.kt
- ui/viewmodel/ProfileViewModel.kt
- ui/screen/reader/sheets/ReaderNotesSheet.kt
- data/repository/NoteRepository.kt
- 新增feature/annotations/**及对应测试
- 新增res/values/strings_annotations.xml及同名locale资源

ReaderSheetHost、AppNavigation、DAO/entity/schema不归你；使用兼容接口并报告SEAM REQUEST。

## 必须完成

1. 追踪现有NoteEntity、HighlightEntity与kind=bookmark，构建统一条目模型，稳定ID含类型，避免不同表ID相撞。不合并数据库表。
2. “我的”同时收录高亮、批注、书签；支持类型、书籍、关键词筛选，颜色适用于高亮；空态对应真实筛选状态。
3. 条目区分原文摘录和个人批注，来源书/章节清楚；章节按实际文档顺序或可靠source位置排序，不能按中文章名字典序。
4. 复用现有编辑/删除/导出能力；删除可撤销，撤销只针对本次记录操作。改色、编辑批注时保留定位信息。
5. 打开条目提供精确来源定位；对没有locator的历史记录保留管理能力并明确降级，不伪造偏移。共享导航若不支持，通过SEAM REQUEST让Codex接线。
6. 支持选择条目批量导出Markdown，保留来源信息、原文与批注；复用现有SAF/分享流程；用户取消不产生数据修改。
7. 普通笔记与书内面板数据一致，编辑、删改后Flow刷新，重启持久化。灵感暂不重构，保留现有入口与数据。

## 验证与交付

回归：三类混合、空筛选、同ID不同类型、章节顺序、无locator、编辑保留定位、删除撤销、批量导出选中集与特殊字符。
保留现有ReaderNotesSheet调用兼容，不能留编译红状态给集成者。设备验收交WorkBuddy，COMMON已允许脚本失败后adb install回退。
完成后写 `docs/plans/android-parallel-delivery-2026-09-09/reports/trae-r1.md`，列出实际覆盖与阻塞；不要自动进入R2。
