# desktop — 全局卡片库 v9→v10 迁移预检

状态：**四项产品决策、M1-A–M1-G 与真实打包 Electron 验收均已完成；阶段 1 已关闭**
日期：2026-09-10（2026-09-11 更新）
适用产品线：Electron 桌面端（`electron/`、`src/`）

## 1. 已锁定的迁移输入

`v9-baseline-fixture.ts` 已通过公开 workspace Interface 种入并重开验证两项目数据：项目私有卡片和自定义类型、别名、标签、字段、修订、关系、场景任务卡引用、批注引用，以及 A/B 各自真实附件。M0.1–M0.3 进一步锁定：

- A 项目包不会包含 B 的元数据或附件字节；
- A/B 附件在资源扫描中均为零问题；
- 备份→恢复后 SQLite、附件字节、资源记录与 v9 完整性仍可读。

因此 v10 迁移契约有一份可复用的、会失败的 v9 基线，不能以手写 SQL fixture 或内存状态替代。

## 2. 已确认的实现 seam

| 范围 | 当前事实 | v10 影响 |
|---|---|---|
| SQLite | M1-A 已将类型、关系类型、卡片与卡片关系改为全局所有权，保留的 nullable `project_id` 只记录旧来源且不再外键级联；`project_card_links` 是项目投影真源 | 早期开发版 v10 的残留项目外键会在先备份后自动修复；M1-E 以附加表承载全局卡片资源，不改写旧项目附件 |
| 公共 Interface | M1-B 已把 `CardSummary.projectId` / `CardRelation.projectId` 改为 nullable 全局语义，增加 `linkedProjectIds` / `usageCount`，并统一全局 list/create 与 link/unlink | 页面只能通过同一 workspace/service/store Interface 使用全局资产，不能自行拼装跨项目数据 |
| 场景与批注 | `planningJson` 内保存卡片 ID；`annotations.card_id` 保存卡片 ID | 稳定卡片 ID 必须原样保留；不一致引用只能列入迁移报告，不能静默删除 |
| 附件 | 旧项目附件保留 `resources/<projectId>/...`；M1-E 新增 `global_card_resources` 与 `resources/cards/<cardId>/...`，封面由唯一 `cover` 角色约束 | 项目解除关联或删除不会删除全局资产；M1-F 通过持久 GC 队列在卡片到期后删除物理文件，失败可重试 |
| 项目包 | 当前包以一个 `projectId` 导出；M1-E 已包含当前项目关联卡片的全局资源快照并排除无关资产 | M1-G 已实现普通/加密包稳定 ID 预检、逐卡冲突选择、引用/资源重映射与可见结果；数据库事务会再次校验 |
| 备份 | workspace 正常关闭会执行 WAL checkpoint；备份模块对 SQLite 与文件逐项哈希，恢复前做 staging、SQLite 校验与检查点 | v10 迁移应先排空写入、关闭/checkpoint，再调用此深 Module；不得复制活跃数据库文件 |

当前内置类型已经准确为 8 个，迁移的安全默认是原样保留其稳定 `kind`：`character`（角色）、`location`（地点）、`organization`（组织）、`item`（物品）、`worldRule`（世界规则）、`plotEvent`（情节事件）、`foreshadow`（伏笔线索）、`reference`（资料）。

M1-F 已将 v10 的 `card.delete` 收敛为全局 30 天软删除：保存全部原项目关联后移除 `project_card_links`，卡片在全局和项目投影中隐藏，但关系、场景任务卡、批注和资源仍保留。恢复会重建仍存在的原项目关联；到期后才永久清理引用与资源。v9 测试模式继续保留旧项目私有兼容分支。

## 3. 建议的深 Module

实现时新增一个唯一的迁移 Module，Interface 保持窄：

```ts
migrateV9GlobalCards({ workspaceDirectory, backupRoot }): MigrationReport
```

它负责关闭前置校验、调用备份、`BEGIN IMMEDIATE` 内的数据变换、引用校验、`PRAGMA user_version = 10` 与失败回滚；调用者不接触表名、ID 映射或资源文件路径。其 Interface 必须返回可见的计数、未迁移/不一致引用、备份位置和是否激活，形成足够的 Depth 与 Locality。

全局卡片的日常 Module 则应收敛为 `list`、`link`、`unlink`、`usage`、`delete` 五类能力。项目页和全局卡片库只通过同一 Interface 读取，不拥有各自的关联/删除实现。

## 4. 已确认的四项产品输入

2026-09-10 用户继续执行后采用以下保守规则：

1. **八个内置类型**：已确认采用上节的现有八项及稳定 `kind`；不得在迁移中删除或重分类。
2. **全局删除**：采用 30 天软删除。确认后移除全部项目关联并保留可恢复快照；到期永久清理关系、引用与资源。
3. **全局附件/封面所有权**：卡片拥有自己的全局资源；项目包只携带该项目关联卡片所需的资源快照，项目目录不是共享资源所有者。
4. **`.cra` 冲突合并**：稳定 ID 相同且内容相同则关联；稳定 ID 相同但内容不同必须让用户选择保留本机、导入副本或取消；禁止按标题静默覆盖。

这些规则是后续删除、资源和项目包冲突契约的唯一实现基线。

## 5. 实施顺序与门禁

1. ~~先确认第 4 节的四项输入。~~ 已完成。
2. ~~实现 v9→v10 迁移 Module 与正常/空库/损坏引用/失败回滚/备份失败/重启契约。~~ M1-A 已完成，并补入早期 v10 结构修复和删除项目后全局资产保留；8 个迁移契约通过。主门禁为 `npm run verify:global-card-migration`。
3. ~~串行修改公共 Interface、IPC/preload、service/store，增加全局 list/create 与幂等 link/unlink。~~ M1-B 已完成：全局卡片 7/7、迁移 8/8、Vitest 795/795、build、生产依赖审计 0 与完整 `verify:beta` 通过；4 项 legacy 测试因本机 Node/Electron 原生模块 ABI 不同按既有规则跳过。
4. ~~接入应用级全局卡片库导航、列表/详情、筛选/搜索、使用项目数与全局新建 UI。~~ M1-C 已完成：全局入口、字段搜索、使用项目账册和全局新建/编辑共用同一 workspace Interface；隔离 Electron 冒烟 13/13 已覆盖真实流程。
5. ~~项目 CardsPage 改为已关联卡片视图，提供从全局库关联、受引用保护的解除关联和不删除本体的明确文案。~~ M1-D 已完成：投影以 `linkedProjectIds` 为准；选择器以无副作用的全局只读查询加载，避免覆盖当前项目 cards store；解除关联使用既有后端完整性保护并在确认框中说明不会删除全局原件。
6. ~~实现全局附件/封面所有权、项目只读投影、备份/扫描与项目包快照。~~ M1-E 已完成：资源 6/6、资源一致性 14/14、项目包 10/10、文件项目包 19/19、备份 43/43、全局库 7/7、Vitest 801/801、build、audit 0 与完整 Beta 均通过。
7. ~~实现 30 天删除恢复、影响预览与到期物理资源清理。~~ M1-F 已完成：全局库 10/10、历史 10/10、P1 生命周期 17/17、旅程 15/15、资源 6/6、资源一致性 14/14、备份 43/43、项目包文件 19/19、Vitest 803/803、build、生产依赖审计 0 与完整 Beta 均通过；另 4 项 legacy 测试按既有 ABI 条件跳过。
8. ~~实现 `.cra` 稳定 ID 同内容复用、异内容显式冲突选择与 ID 映射结果。~~ M1-G 已完成：项目包 16/16、文件层 21/21、operation 25/25、全局库 10/10、Vitest 804/804、build、生产依赖审计 0 与完整 Beta 通过；4 项 legacy 测试按既有 ABI 条件跳过。
9. ~~完成阶段 1 的真实打包 Electron 流程验收。~~ `verify:packaged-stage1` 17/17：真实 v9 双项目首次升级、迁移前备份、稳定 ID/项目关联、跨项目同步、解绑、删除恢复、普通/加密项目包冲突副本和重载持久性均通过；取消与失败恢复继续由 operation 25/25、项目包 16/16 和文件层 21/21 锁定。

阶段 1 已形成打包运行证据：`release-beta/win-unpacked/创作阅读助手.exe` 大小为 188,818,944 字节，完整 `verify:beta:release` 通过 804/804 测试、build、生产依赖审计 0 与产物检查；打包验收使用复制到临时目录的应用副本，避免便携 `data/` 污染发布目录或真实用户数据。
