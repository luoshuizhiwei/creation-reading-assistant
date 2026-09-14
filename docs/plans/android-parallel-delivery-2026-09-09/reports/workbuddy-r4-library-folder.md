# WorkBuddy 交付报告：应用内书籍目录、智能识别接线（第 1–2 组）

日期：2026-09-12。执行目录：主仓库 `android/`。
依据：[`docs/plans/2026-09-12-android-library-folder-smart-recognition-roadmap.md`](../../2026-09-12-android-library-folder-smart-recognition-roadmap.md)
承接：Codex 已交付的 Module 层（`LibraryRootStore`、`LibrarySource`/`SafLibrarySource`、`SmartBookRecognizer`/`SafSmartBookRecognizer`）
前置基线：`compileDebugKotlin` PASS（改动前实测，确认 Codex 交付可用）

本报告只记录真实执行结果，不把开发检查点当验收 PASS，不 commit / push，未执行任何真机命令。

## 0. 结论速览

| 项 | 结果 |
| --- | --- |
| 第 1 组：根目录授权 / 恢复 / 重新授权 | **接线完成**（新增 `LibraryBrowserViewModel` + 目录页） |
| 第 1 组：当前目录列表 / 面包屑 / 搜索 / 排序 / 刷新 | **完成**（下拉刷新降级为显式刷新按钮，见 §4） |
| 第 1 组：保留系统文件与电脑导入入口 | **完成**（入口 Sheet 改为方案 6.1 三入口） |
| 第 1 组：移除错误 PDF 宣称 | **完成**（两处导入入口的 PDF 胶囊已移除） |
| 第 2 组：智能识别最小闭环（进度 / 停止 / 分档 / 原因 / 默认勾选 / 批量入架） | **完成** |
| 第 3 组：来源索引与增量更新 | **未开始**（依赖 Room migration，方案要求等 WIP 收口） |
| `:app:compileDebugKotlin` | PASS |
| `:app:testDebugUnitTest` | 见 §5 |
| `:app:compileDebugAndroidTestKotlin` | 见 §5 |
| `:app:lintDebug` | 见 §5 |

## 1. 本轮范围

Codex 已把 Module 层做完，但**没有任何 UI 接线**：`LibraryRootStore`、`SafLibrarySource`、`SafSmartBookRecognizer` 在全仓零引用（只有各自的 JVM 测试）。因此本轮做的是方案第 1–2 组的「接线 + 页面」，并按方案 6.1 / 12 收敛导入入口。

## 2. 交付清单

### 2.1 新增文件

| 文件 | 职责 |
| --- | --- |
| `feature/library/LibrarySourceModule.kt` | Hilt 绑定 `LibrarySource` → `SafLibrarySource`（Codex 未提供绑定，缺它无法注入） |
| `ui/viewmodel/LibraryBrowserViewModel.kt` | 目录浏览与识别状态机；只读来源目录，不导入、不复制、不删除 |
| `ui/viewmodel/LibraryBrowserModels.kt` | 页面 UI 模型、分档枚举、一次性提示文案 |
| `ui/viewmodel/LibraryBrowserPolicy.kt` | 纯规则：弱重复判定、分档过滤、默认勾选口径、尺寸/时间降级文案 |
| `ui/screen/shelf/LibraryBrowserRoute.kt` | 「我的书籍目录」页面主体 |
| `ui/screen/shelf/LibraryBrowserComponents.kt` | 根目录卡 / 面包屑 / 排序行 / 目录行 / 文件行 / 多选底栏 / 识别面板 |
| `app/src/test/.../ui/viewmodel/LibraryBrowserPolicyTest.kt` | 9 条 JVM 用例 |

### 2.2 修改文件（最小侵入）

| 文件 | 改动 |
| --- | --- |
| `ui/screen/shelf/ShelfSharedComponents.kt` | 新增路由常量 `SHELF_LIBRARY_ROUTE` |
| `ui/navigation/AppNavigation.kt` | 在 `shelf-graph` 内注册 `shelf/library`，导入动作交给 graph 作用域的 `ShelfViewModel` |
| `ui/screen/shelf/ImportSourceSheet.kt` | 改为方案 6.1 的三入口（我的书籍目录 / 从系统选择文件 / 从电脑导入），移除 PDF 胶囊 |
| `ui/screen/shelf/ShelfRoute.kt` | 适配新入口：目录入口导航到 `shelf/library`，电脑导入复用既有 `DesktopBooksSheet` |
| `ui/screen/shelf/ShelfImportRoute.kt` | 新增「我的书籍目录」渠道；移除 PDF 胶囊（历史记录的 PDF 着色保留，属防御性映射） |

## 3. 关键设计决定

1. **导入仍只有一个 Module。** 目录页不持有导入状态，勾选后把 URI 列表交给 `shelf-graph` 作用域共享的 `ShelfViewModel`（即同一个 `ShelfImporter` 实例），因此导入进度、去重、批次摘要与既有导入页完全一致，不会出现第二个导入队列。这也符合方案 7.4「不要增加只转发 ShelfImporter 的浅 Module」。
2. **根目录写入前先确认持久授权。** `saveRoot` 内部先 `takePersistableUriPermission`，失败则不落库并提示重新授权——避免保存一个下次启动读不到的根目录（`LibraryRootStore` 的注释明确要求由调用方保证授权）。
3. **恢复上次目录采取保守策略。** `LibraryRootStore` 只保存一个 document ID，无法反推中间层级。实现只在「命中 root 直接子目录」时恢复并重建面包屑；更深的位置退回根目录并清除过期 ID，而不是伪造一段用户没走过的面包屑。
4. **重复判定如实标注为弱判定。** 第 3 组的 `library_source_refs` 尚未存在，App 无法证明「已在书架」。因此 UI 文案为「疑似已在书架（按文件名推测，导入时以内容为准）」，并且**不会**据此跳过导入——真正的去重仍由 `ShelfImporter` 的内容哈希负责。这一条同时对应方案 12 拒绝项「仅按文件名判断重复」。
5. **识别依据直接渲染在列表行内。** 每条候选都显示命中理由，避免方案 4.3 说的「智能识别黑盒」。
6. **默认勾选口径收敛到一个纯函数。** `selectedByDefault(decision, duplicateHint)` 同时被识别事件流与「只选推荐」按钮使用，保证「事件到达时的默认勾选」与「用户手动重算」口径一致。
7. **不新增 Room 表、不动 schema。** 第 1–2 组按方案要求零迁移。

## 4. 与方案的偏差与降级（如实记录）

| 方案要求 | 本轮实现 | 原因 |
| --- | --- | --- |
| 4.2「下拉刷新」 | 顶部显式刷新按钮 + 加载态 | 复用书架页的自定义下拉刷新会引入一套手势状态机，首期以显式动作收敛，语义等价（重新读取当前目录） |
| 4.2「已入架文件单击可打开对应书籍」 | 只显示疑似提示，不提供跳转 | 弱匹配拿不到可靠的 `bookId`，第 3 组来源索引落地后才能精确映射 |
| 4.3「内容有更新」档位 | 未实现 | 需要来源索引比较基线（第 3 组） |
| 4.4 识别结果预览页（P1） | 未实现 | 方案本身标为 P1 |
| 4.5 来源状态索引 | 未实现 | 第 3 组，且方案要求等 Room WIP 收口 |

另外，`formatColorFor` 与书架选择页中保留的 `"PDF"` 分支是**对既有数据/历史记录的防御性着色**，不是对 PDF 支持的宣称；两处导入入口的 PDF 胶囊已按方案 12 移除。

## 5. 门禁结果

（真实执行输出，`--no-daemon`，无并发写入；命令：`:app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug`）

| 任务 | 结果 |
| --- | --- |
| `:app:compileDebugKotlin` | PASS |
| `:app:testDebugUnitTest` | **PASS** — 250 套件 / 2200 tests / 0 failures / 0 errors / 0 skipped（本轮新增 9 条 `LibraryBrowserPolicyTest` 用例） |
| `:app:compileDebugAndroidTestKotlin` | **PASS** |
| `:app:lintDebug` | **PASS** — 0 errors / 4 warnings，且 4 条全部为既有告警（3 条 `ObsoleteLintCustomCheck` 来自依赖 jar，1 条 `UnusedResources` 指向 `strings_annotations.xml:37`），**无一条出自本轮文件** |
| 整轮 | `BUILD SUCCESSFUL in 9m 23s` |

测试过程中 `LibraryBrowserPolicyTest` 抓到一处真实缺陷并已修复：「疑似入架」页签原先只按弱重复提示过滤，会把**未识别**的文件也算进该档，从而诱导用户重复导入；现改为 `duplicateHint && decision != REJECTED`。

## 6. 未完成与下一步

1. **第 3 组（来源索引与增量更新）**：`library_source_refs` 表 + migration + 「已在书架 / 内容有更新」精确档位；落地前不得把弱匹配升级成事实口径。
2. **真机验收**：首次授权、取消、重启恢复、大目录截断、停止扫描不假死、删除书架项不动来源文件、来源失效后内部副本仍可读。按方案 11.2 使用已连接真机 + `adb -s <serial>`，报告只用中性书名。
3. **下拉刷新**：若要求手势级刷新，可在目录页接入与书架页同源的下拉实现。
4. **识别结果预览页**（P1）：来源目录、编码、书名/作者建议、识别依据、导入后章节结果。
