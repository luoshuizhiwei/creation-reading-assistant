# 阶段 E 四窗报告复核（2026-09-29）

## 状态

四份报告及其 E 盘矩阵均已落盘，恢复计划 A–E **仍为 4/5=80%**。本轮只读核对原件与若干关键 diff，没有 stage、commit、push、reset、checkout、构建或设备操作。四窗结果推进了归属分析，但尚未形成全范围、逐提交可复现的 E 执行清单。

## 可直接采用的关键发现

- 数据窗口指出 G0 Room v15 不能只取 `AppDatabase.kt`、`DatabaseModule.kt`、`15.json` 和迁移 androidTest：`SearchTermDao.kt` 的 `@Entity(indices=...)` 确实移除了 `index_search_terms_term`，应与迁移及 schema 同一原子边界。Codex 实读 `git diff -U0`：该文件另有 DAO 方法删除、coverage 查询新增等不属同一 hunk，**不能整文件无选择地暂存**。`AppDatabaseMigrationTest.kt` 是 `androidTest`，其 diff 包含独立 import 与约 311 行新增测试；只可引用实际设备原件，不能冒称包含在 JVM 3208/3208。
- 数据、UI、reader、资产矩阵均存在且 SHA 与报告相符。数据矩阵 64 行；UI 矩阵 102 行；reader 两张矩阵分别 42、19 行；资产修正 CSV 92 行。原目录独有 Electron 3 文件和文档 3 文件继续单列保留。上一轮资产 CSV 的 `.Count` 插值错误已另存修正 CSV，旧文件未覆盖。
- UI 窗口识别 S05、smart search、grid helper 等同 hunk 交织，报告自身仍列 8 个核心文件 HOLD 和若干 profile/stats 大文件待逐 hunk 判定。reader 窗口识别 `EpubRepository.kt` 的混合依赖；S09 产品合同及 reader UI 仍 HOLD。故报告“可提交子组”只能理解为**候选**，尚无隔离编译证据。

## 不能外推的结论

- reader 报告称 `stage-e-reader-hunk-matrix.csv` 为 36 行，但 Codex `Import-Csv` 实算 **42 数据行**；其统计把不同文件数与 hunk 数混列（接手 145、已判定 36、仍 HOLD 109），不能直接与总矩阵 376 个文件项相减。后续集成必须统一计数单位。
- 资产语义报告将 65 项全判“确认覆盖”，但部分实际是**有意改变产品行为**，例如 `percent >=99.5%` 改为显式 FINISHED、时长取整/显示口径变化。它们可按已批准的产品合同接受，不能写作严格行为等价。逐项表可供处置参考，须把“等价保留”和“已验的有意替换”分开；不能据此宣称原目录所有改动均无独有价值。修正 CSV 是 92 行总表，其中 65 行是本轮待审子集，报告“65 行数据行”的表述应收窄。
- 原 Android 总矩阵仍有 180 HOLD；本轮各窗口的接手范围有交集和不同计数单位，不得把 64+102+145 简单相加当作关闭数。E 整体尚不满足退出条件。

## 最短下一步

1. **优先形成 G0 的精确隔离候选**：只含 Room v15 所需源码/schema、`SearchTermDao.kt` 指定索引 hunk、对应 androidTest hunks；在 E 盘隔离构建目录验证干净基线加候选可编译，禁止改恢复仓库的 index/工作树。构建命令、输入哈希、退出码与 XML 原件落 E 盘。此步是准备具体授权材料，不是提交。
2. Codex 将四窗矩阵按**文件/ hunk/ 验证来源**统一口径，先选择数据风险和已冻结修复候选；仍混合的 S05、reader UI、S09、其他新功能保持 HOLD。整合前不要求用户对尚可查明的技术事实做选择。
3. 完成精确候选、依赖顺序、原目录资产去向与增量备份覆盖清单后，才按收口计划 §E 展示给用户，获取 stage/commit 或目录切换的明确授权。原仓库和正式书库保持原状。
