# 恢复仓库 AGENTS.md

先读 [当前交接入口](docs/handoff/current.md)。长期协作规则继承 [用户级 AGENTS](<C:/Users/23254/.codex/AGENTS.md>)；跨项目记忆入口为 [Codex 项目页](<D:/Application/文档/Obsidian/Codex/projects/创作阅读助手.md>)。

换窗接手见 [接手说明](docs/handoff/next-codex.md)与[后续任务执行手册](docs/handoff/continuation-playbook.md)。后续每项工作开工前先落完整任务合同，写明输入、所有权、步骤、权限、验证、交付与停止条件；用户指定的接任协调者可承担Root核验/排期职责，不依赖原会话在线或额度。每轮收尾更新current/state及下一任务文档，不能只在聊天留接续说明。历史任务和交接快照只按需定位，不整份加载，也不从旧授权恢复自动化、设备批次或Git操作。

DSH 侧另有独立记忆库（[DSH 项目页](<D:/Application/文档/Obsidian/DeepSeek-Harness/projects/创作阅读助手.md>)），只供 DSH 使用；其他智能体不读不写，需要时只引用链接，不复制正文。

## 工程与资产边界

- `android/` 是 Kotlin/Compose/Room 原生 Android；`src/` 与 `electron/` 是独立桌面产品线，不共享运行时代码。`docs/` 为共享文档，旧 Capacitor 见 `archives/frozen-mobile/FROZEN.md`。
- 保护既有 WIP、桌面资产、HOLD 与历史证据；不 reset/checkout、全仓格式化或 renormalize，不为清空状态删文件。当前精确操作授权以交接入口为准，未经明确授权不 commit/push/发布。
- 文件所有权互斥；`package.json`、lockfile、公共类型、根文档和公共构建配置由单一集成者维护。并行实现只跑定向测试；合流后完整门禁和设备批次由各自唯一所有者串行执行。
- 构建优先使用 E 盘隔离候选与任务缓存；不要把 D 源码直接改为跨盘 buildDir 后假定 KSP 可用。输入与验收证据已匹配时复用，不为了交接全量复跑。

### E盘占用与最小交付（2026-10-16起）

- 实际派发入口使用 [任务模板](docs/coordination/android-task-dispatch-template.md)；`.codex/agents/android-worker.toml`、`android-verifier.toml` 同步约束差量交付和既有冻结复用。模板存储预算不得缺省；本批实际空间与分类收尾写入同一REPORT，不新增收据体系。

- 外部任务默认交付最小patch、改动/新增源码、基线及前后SHA、REPORT和必要原始日志/XML；不得按任务或版本默认复制完整工程、整套历史证据、备份、APK对或依赖缓存。
- 复制前估算新增体积并检查可用空间；已有任务直接接续，不因新增存储要求重启或另建同一任务。只读基线用绝对路径和指纹引用。
- `build/`、`.gradle/`、`.kotlin/`、`node_modules/`、Gradle用户缓存、旧cache/mirror和二进制产物不得随源码复制。依赖复用现有一套，不擅自迁移或清空共享缓存。
- 完整Android编译集中到Root指定的单一构建工作区，由唯一owner串行执行；并行执行者只写互斥差量，可做不需复制工程的定向验证。需要完整编译但尚无owner/工作区时如实列为待Root验证，不复制工程来绕过。
- 磁盘盘点仅列精确路径、内容、体积、保留依据和删除后果；源码/WIP、DB/WAL/SHM、恢复备份、最终冻结APK、唯一原始证据与配置不得按“旧版本/大文件”直接清理。已有资产删除、移动另按用户确认的精确批次执行；不自动删除以腾空间。

## 常用命令（按任务选择）

- Android 在 `android/`：`./gradlew :app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug`；Release 的签名/变体合同由当轮候选配置和任务定义决定。
- 桌面在根：`npm run dev`、`npm run build`、`npm run dist:dir`。当前 Android 任务不自动扩展到桌面构建或发布。
- 架构按需读 `docs/architecture/native-android-reader.md`；候选需求按需读 `docs/plans/legado-feature-backlog.md`；不把历史 backlog 当当前任务授权。

## Android 设备验证

- 当前恢复项目的设备类型、许可与恢复要求见 [交接入口的设备决定](docs/handoff/current.md#设备与权限决定)。不要把旧“仅真机”记忆覆盖当前用户决定；MuMu 验收必须称模拟器验收。
- 执行设备任务先 `adb devices`，命令全部 `adb -s <本轮核实 serial>`；测试结束恢复本轮数据/APK对/密度/旋转/保活等约定基线。记录只用中性书名，不授权访问或改写正式书库。
