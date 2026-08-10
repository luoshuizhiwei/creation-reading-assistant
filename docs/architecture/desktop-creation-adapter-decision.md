# 桌面端创作工作台 — 存储与编辑器 adapter 决策记录

> 状态：spike 阶段决策记录（不进入业务实现）。
> 本文件只记录 adapter 技术选型与门禁，不描述任何业务功能实现。

## 1. 结论

第一阶段桌面端创作工作台采用：

- 存储：**better-sqlite3 12.4.1**
- 编辑器：**Tiptap 3.29.2**（`@tiptap/core` / `@tiptap/pm` / `@tiptap/react` 同版本）

锁定运行时基准：

- **Electron 33.4.11**
- **Node 20.18.3**
- **ABI 130**

上述 adapter 依赖和 Electron 运行时基准均已写入 `package.json`（精确版本、无 `^`/`~` 前缀），并有 `scripts/spikes/` 下的运行时证据（详见第 5 节门禁）。本决策**不进入业务实现**，业务代码不得直接依赖本文件中的 spike 脚本或临时结构。

## 2. 为什么淘汰 better-sqlite3 13.x

better-sqlite3 12.4.1 是本阶段与锁定运行时（Electron 33.4.11 / Node 20.18.3 / ABI 130）匹配并经过 runtime smoke 验证的版本。当前 13.0.3 的 package metadata 要求 Node `>=22`，与 Electron 33 内置 Node 20.18.3 不兼容；它也未在本阶段对 ABI 130 完成原生构建与运行验证，因此不得直接采用。未来若升级 Electron 和 better-sqlite3，必须按第 6 节重新做完整门禁。

## 3. 为什么淘汰 sql.js

sql.js 是 WASM 实现的 SQLite，与本阶段选定的原生 better-sqlite3 在加载形态、文件句柄语义与事务完整性验证路径上不一致。存储 seam 需要原生 SQLite 事务语义（见 `verify:creation-adapter-spike` 中的 `rollbackVerified` 证据），且原生模块已在锁定 ABI 下验证可用，因此不引入第二套存储引擎。同样不虚构性能数据。

## 4. 为什么淘汰 Lexical

第一阶段正文编辑器需要严格的 schema 约束（仅允许固定的正文节点/标记，拒绝表格等非正文结构），并需要 transaction position mapping 支撑后续非正文引用锚点和批注重定位。Tiptap 3.29.2（基于 ProseMirror schema）可直接以 schema 驱动完成该约束并已在 spike 中验证（`roundTrip` / `unsupportedContentRejected`）。Lexical 同样是成熟且重视 IME、可访问性与性能的编辑器框架，但本项目若采用它，需要另外定义节点校验和锚点映射策略；本轮不同时引入两套编辑器框架。该取舍不代表 Lexical 质量较低。

## 5. 原生构建、打包与运行时门禁

### 5.1 native rebuild 与 postinstall

`package.json` 中 `postinstall` 为 `electron-builder install-app-deps`，会在安装后按当前 Electron ABI 重新构建原生模块。better-sqlite3 是原生模块，必须按 Electron 33.4.11 的 ABI 130 构建，不能直接使用当前系统 Node 24 安装出的 ABI 137 binary。

### 5.2 asarUnpack

`package.json` 的 `build.asarUnpack` 已包含 `node_modules/better-sqlite3/**`。better-sqlite3 的原生 binding 与预编译产物必须从 asar 中解包，否则打包后运行时无法加载。

### 5.3 Windows 开发 / dir 打包 / runtime smoke 门禁

门禁脚本：`npm run verify:creation-adapter-spike`（`scripts/verify-creation-adapter-spike.mjs`）。它校验：

1. 依赖精确版本：`better-sqlite3 12.4.1`、`@tiptap/core` / `@tiptap/pm` / `@tiptap/react` 3.29.2，以及 Electron 33.4.11；
2. `postinstall` 与 `asarUnpack` 配置存在；
3. 使用 Electron 二进制验证实际运行时为 Electron 33.4.11 / Node 20.18.3 / ABI 130，再运行 SQLite spike（`scripts/spikes/creation-sqlite-spike.cjs`），证明 WAL、外键、事务回滚、FTS 回滚清理与规模数据完整性；
4. 使用宿主 Node 运行编辑器 schema spike（`scripts/spikes/creation-editor-spike.mjs`），证明严格 schema 往返与不支持内容拒绝；
5. 本决策文档包含必要原样文本。

本轮主代理已执行：

- `npm run build`：exit 0；
- `electron-builder --dir --config.directories.output=release-adapter-spike`：exit 0，明确以 Electron 33.4.11 / x64 rebuild better-sqlite3；
- 使用 Electron 33 加载打包目录 `resources/app.asar.unpacked/node_modules/better-sqlite3`：成功，SQLite `3.50.4`。

`verify:creation-adapter-spike` 负责日常开发门禁；Windows `dir` 打包和打包后 native runtime smoke 属于依赖/Electron 升级时的发布门禁，不在每次快速测试中重复打包。

## 6. 锁版本升级纪律

- 本阶段依赖全部使用精确版本，禁止在 `dependencies` / `devDependencies` 中使用 `^`、`~` 或裸 `*`。
- 任何升级（包括 better-sqlite3 13.x）必须先出新的决策记录，说明动机与差异，并在锁定运行时（Electron 33.4.11 / Node 20.18.3 / ABI 130）下重新运行完整门禁，再进入业务实现。
- 不虚构性能数据：涉及性能收益/回归的论断必须由主代理在真实数据上回填实测。

## 7. 风险与回退

- ABI 不匹配：若 Electron 升级，better-sqlite3 必须重新 native rebuild；未重跑门禁前不得发布。
- asarUnpack 缺失：会导致打包后原生模块加载失败；门禁已覆盖该配置项。
- 预编译产物不可用时需本地编译工具链（如 Visual Studio Build Tools），失败会阻塞安装。
- 数据迁移风险：现有桌面端数据（如有）切换存储/编辑器前必须备份，并遵循第一阶段规格中的迁移与回退方案。
- 回退策略：任一门禁失败时，回退到上一组锁定版本并重跑门禁；不回退业务代码以绕过门禁。

## 8. 官方参考链接

- Electron 版本与发布：<https://releases.electronjs.org/>
- 使用原生 Node 模块（native modules / rebuild）：<https://www.electronjs.org/docs/latest/tutorial/using-native-node-modules>
- electron-builder 原生模块：<https://www.electron.build/native-modules.html>
- electron-builder asarUnpack 配置：<https://www.electron.build/configuration/configuration#configuration-asarUnpack>
- better-sqlite3 发布：<https://github.com/WiseLibs/better-sqlite3/releases>
- Tiptap 安装文档：<https://tiptap.dev/docs/editor/getting-started/install>
- sql.js：<https://sql.js.org/>
- Lexical：<https://lexical.dev/>

## 9. 证据与待回填

已确认的运行时证据：

- `node_modules/electron/package.json`：`33.4.11`；
- `node_modules/better-sqlite3/package.json`：`12.4.1`；
- `package.json`：`@tiptap/core` / `@tiptap/pm` / `@tiptap/react` `3.29.2`，`asarUnpack` 含 `node_modules/better-sqlite3/**`，`postinstall` 为 `electron-builder install-app-deps`；
- `scripts/spikes/electron-runtime-info.cjs`：可输出 `JSON.stringify(process.versions)`（Electron 33.4.11 / Node 20.18.3 / ABI 130 的运行时来源）。
- `scripts/spikes/creation-sqlite-spike.cjs`：本轮主代理复跑得到 1 项目、2000 场景、10000 卡片、20000 关系；事务回滚、外键、FTS5、`integrity_check` 全部通过，单次合成 spike 总耗时约 183 ms。该数字只用于本机 adapter 可行性证据，不等同于产品 P1 性能预算。
- `scripts/spikes/creation-editor-spike.mjs`：严格 schema JSON 往返、中文文本保真、非法 table 拒绝和 `sceneBreak` atom 检查全部通过。
- `scripts/spikes/electron-native-module-info.cjs`：打包后 ASAR unpack 目录的 better-sqlite3 可由 Electron 33 加载，SQLite 版本 `3.50.4`。
- 反向验证：Electron ABI 130 rebuild 后的 better-sqlite3 由系统 Node 24（ABI 137）加载时按预期失败，说明原生模块必须跟随 Electron runtime rebuild，不能把宿主 Node 的成功/失败当作 Electron 证据。

尚未进行 sql.js、Lexical 或 better-sqlite3 13.x 的同机性能对比；当前选择依据是已确认的 interface 需求、运行时兼容和可运行 spike，不宣称未测方案的性能优劣。
