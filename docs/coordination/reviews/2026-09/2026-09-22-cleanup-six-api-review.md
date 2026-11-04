# C01：六接口清理复核

日期：2026-09-22。结论：清理不能整体关闭；应把待决能力与明确冗余分开。本次未修改这三个源码/测试文件。

## 已核对事实

- 上轮改动仅涉及 `SearchTermDao.kt`、`SearchIndexRepository.kt`、`SearchIndexRepositorySearchTest.kt`；当前删除状态已包含在 stage-a 独立快照。
- 本轮在 `android/app/src` 搜索六个符号均无残留调用；这是删后状态，不能单独证明删前没有价值。
- `git show HEAD:.../SearchIndexRepository.kt` 可恢复原 `incompleteBookIds` 完整实现，内部依赖 `coverageDao.rowsByStates`。
- `docs/handoff/current.md:1144` 和末尾待办明确将该查询记为“是否接线待 owner 决定”。这不是明确废弃决定。
- `contextFor` 仍保留；SearchCoveragePolicy 仍被仓储与 SearchViewModel 使用，本轮不动。
- 上轮已执行 SearchIndexRepositorySearchTest 22 项 + SearchCoveragePolicyTest 11 项及主 Kotlin 重新编译，结果成功；这是历史开发证据。本次没有重新运行测试或设备验收。

## 逐项决定

| 接口 | 角色 | 复核建议 | 理由 |
|---|---|---|---|
| incompleteBookIds | 尚未接入口的未完成索引查询 | 恢复待决实现 | 交接已明确登记未来接线决定，清理不应替代该决定 |
| rowsByStates | 上述查询的 DAO 依赖 | 随上项恢复 | 恢复查询必须恢复真实依赖及 fake 实现，不能留下不能编译的半恢复 |
| deleteByChapter | 不区分文本基准的旧删除接口 | 保留本次删除 | 先前完整会话搜索仅见声明、KDoc 与 fake；生产按 book/basis 删除路径仍在 |
| countTerms | 未被消费的总行数查询 | 保留本次删除 | 对应仓储包装早已移除，当前展示计数走其他查询；无新增功能改变证据 |
| rowsByTerm | 不区分文本基准的旧查询 | 保留本次删除 | 生产搜索使用 rowsByTermForBasis，避免混淆 source/display 坐标 |
| countByStates | 未被消费的覆盖率数量查询 | 保留本次删除 | 不被 incompleteBookIds 依赖，无实际查询入口 |

## 后续最小修正边界

1. 仅恢复 `SearchIndexRepository.incompleteBookIds` 原实现与说明。
2. 仅恢复 `SearchIndexCoverageDao.rowsByStates` 的查询声明。
3. 仅恢复 FakeSearchIndexCoverageDao 中对应方法；其他四项不回退。
4. 更正 SearchTermDao 顶部仍称“只有单章清理、查询下一批接入”的过时 KDoc（P3，随同文件处理）。
5. 恢复前对照快照与当前文件，不能用 HEAD 整文件覆盖，否则会丢失既有修复。
6. 修正后运行上述两类定向测试和主代码编译；androidTest 在统一编译门禁覆盖。无需为保留一个未接入接口新增手机验收。

本报告给出具体修正建议，尚未执行；在 S03 产品修复进入同文件前串行完成。当前用户可达功能未因本轮文档复核改变；不得把历史编译通过扩大成全仓功能无损证明。
