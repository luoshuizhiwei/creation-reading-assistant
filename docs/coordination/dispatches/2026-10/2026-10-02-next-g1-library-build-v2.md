# G1-build-v2 — 书库验收夹具与执行协议修复报告

- 任务编号/版本：**G1-build-v2**（依据 Codex-review-N1v4-G1build-v1 Spec.G1 两项 P2 与协议要求）。
- 执行者：ZCode / GLM（account:zai-start-plan）；零设备操作、零 ADB；与 N1-repair-v5 独立并行，未读取/集成 N1 修复。
- 候选：`E:\creation-reading-assistant\next-round-g1-library-build-v2\android`（自 G1-build-v1 派生；树差核对 **SINGLE_FILE_DIFF=True**——仅仪器类一个文件，`freeze/evidence/tree-diff-v1v2.txt`）。原候选 v1、freeze、原报告与日志只读未动。

## 1. 输入指纹

- HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（全程未变，未 commit/push/reset/checkout）。
- 父输入按 `n1v4-g1-codex-review-20261002\verification.json` 的 g1_hashes/g1_apks：宿主 `4bf670ab4006c6d60a7e18d8d31d0856d55755d6b8ff6994b3f41edc3b272531`、测试 `70b04ecf3628caeedc5cb2d14a89628d23ab8927665efcdf3cd30121bcd95c8f`（与 G1-v1 报告一致，本轮重算复核）。

## 2. 所有权与改动

**唯一改动文件**：`app/src/androidTest/java/com/creationreadingassistant/feature/library/LibraryLifecycleAcceptanceTest.kt`（SHA `7add5e80acb645d36a17b7f89ad8b6958c0e385b53f9323be7091c24422db373`；diff `freeze/evidence/g1v2-test-diff.patch`，694 行）。build.gradle.kts、runner、产品源码、依赖全部保持父输入字节；未新增依赖（v2 草案中的 UiDevice 用法因 uiautomator 未声明为 androidTest 依赖而移除——`onApplySaved` 自身关闭面板，无需 back）。

## 3. 三项修复

### 3.1 正文路径查询与创建分离（P2 夹具副作用）

- `contentPath()` 改为**纯路径构造**（不 mkdir、不 writeText）；写文件仅存在于 `seed*` 路径（`createSeedContent()`，private，仅 seed 调用）。
- `assertAfterShelve`：文件缺失 → **判 FAIL「搁置误删正文文件」**（v1 会在此把文件补回，必然假绿）；`assertAfterRemoveContent`：`!contentPath().exists()` 检查**实际缺失**（v1 会重建文件，恒为假绿）；`assertLifecycleFixtureReady`/`deleteUndoChainRealUi` 中的存在性检查同样纯读。

### 3.2 撤销协议单会话化（P2 跨仪器依赖）

`deleteUndoChainRealUi`：一次仪器调用内完成全链——

1. DAO 夹具（标注的种子写入：书 available + 正文文件 + reading 进度 + 笔记 + 保留/预删标签 + 书单）；
2. **真实 UI**：`createAndroidComposeRule<MainActivity>` 启动书架 → `book-tile-g1-life-a` 长按 → 动作面板「删除书籍」→ 确认对话框可点击「删除」（`hasText("删除") and hasClickAction()` 精确匹配）；
3. 撤销条出现（`deletion-undo-bar`）后：DAO 只读对账（书/进度/笔记同批软删、关系清理）→ **DAO 写入「删除后合法新改动」对照行（标注夹具）** → 点击真实撤销条 `deletion-undo-action`——全程处于 `DeletionUndoPolicy` 12 秒窗口内；
4. 读回恢复：书/进度（reading）/笔记恢复活跃、保留标签关系由快照恢复、**预删标签不复活且关系不重建**、删除后新改动不被覆盖。

不经删除协调器、不写库模拟用户删除/撤销；不延长撤销窗口、不持久化凭证、不改 runner。

### 3.3 L1 具体预期条件（含可见书结果）

`savedFilterUiChain` 单仪器调用、三阶段证据**独立文件名**（`l1-saved.json` / `l1-after-restart.json` / `l1-reapplied.json`，不互相覆盖）：

- 预置四态书（finished/reading/shelved/unread，标题含"筛选对照"标记）；
- UI：状态胶囊「在读」→ 打开筛选面板（`shelf-filter-summary`）→ 唯一文本框输入「G1 验收视图」→「保存」；
- **阶段 1 读回**：`statusFilter == "READING"` 且 `formatFilter == "txt"`（具体预期，非同名存在）；
- **阶段 2 重启**：`activityScenario.recreate()` 后重开面板，视图仍在且条件一致；
- **阶段 3 重新应用**：点视图 chip（`onApplySaved` 自行关闭面板）→ 网格可见书 = 仅「测试 TXT 中性乙」，三本对照书 `assertDoesNotExist`。

### 3.4 保留项与 runner 合同

保留 v1 的：后缀 hunk 与包名合同（宿主 `com.creationreadingassistant.qahostlibrary`）、seed/读回分阶段结构、结果落盘机制（阶段名独立化）。**runner 合同如实声明**：CraIsolatedTestRunner 仅对精确 `.qahost` 注入裸 Application，`.qahostlibrary` **透传生产 Application**（Hilt + cra-startup 调度在本沙箱 UID 内启动）——不声称裸 Application 启动隔离；启动/设置副作用由下一设备批基线与后账核对。

## 4. 构建与证据

- 命令：`:app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost`（复用 j1-v2 独占缓存；不新增依赖）→ 过程两次编译修复（seedLifecycleRows 缺 suspend；缺 `withTimeout`/`filter` import）均留痕，**最终 EXIT=0**（`freeze/evidence/build-g1v2-test.log`）。
- 测试 APK：`freeze/apk/app-qahost-androidTest.apk` SHA `ebcf45bc533d6ba3a690857589d4f981190d304b9384b5cd44483c1b713d7744`；targetPackage `com.creationreadingassistant.qahostlibrary`；runner `CraIsolatedTestRunner`；签名 exit 0（证书 `05249d78…`）。
- 宿主：**复用 v1 冻结宿主**（源码零变化；v1 冻结包与其 build 输出重算逐位一致 `4bf670ab…`；本轮未构建宿主）。
- 未跑全量 JVM/Lint/Release（改动仅仪器类）。

## 5. 下一轮 MuMu 步骤（区分夹具 / 真实 UI / 只读断言）

基线保存：新包 `pm path com.creationreadingassistant.qahostlibrary` 预存在检查（预期不存在）；原 qahost APK 对 SHA、持久 DB/文件、系统设置、reverse 映射冻结。

| 步骤 | 类型 | 内容 | 读回 |
|---|---|---|---|
| 1 | 安装 | 安装 `freeze/apk/` 双 APK（新包首装） | aapt/pm 核对包名与 UID |
| 2 | 夹具 | `#seedLifecycleFixture`（DAO+正文文件写入） | `#assertLifecycleFixtureReady` |
| 3 | 真实 UI | 长按书卡 → 详情面板执行「搁置」 | `#assertAfterShelve`（误删即 FAIL） |
| 4 | 真实 UI | 详情面板执行「移除正文」 | `#assertAfterRemoveContent`（实际文件缺失） |
| 5 | 单会话链 | `#deleteUndoChainRealUi`（内部含夹具+真实 UI 删除/撤销+读回） | 结果 JSON `deleteUndoChainRealUi` |
| 6 | 单会话链 | `#savedFilterUiChain`（内部含真实 UI 保存/重启/重新应用） | 结果 JSON `l1-saved` / `l1-after-restart` / `l1-reapplied` |
| 7 | 恢复 | 卸载沙箱包（仅其自有 UID 数据）→ 原 qahost 对与持久数据/设置按基线逐项对账 | SHA 清单 |

证据拉取：`/data/data/com.creationreadingassistant.qahostlibrary/files/g1-results/*.json`（root）+ logcat `G1LifeAccept` + `am instrument` 原始输出。

## 6. 未覆盖项

- 四项种子/断言方法与两条单会话链**未运行**（本轮不操作设备）；Compose 选择器与 12 秒撤销窗口的实际时序属设备首跑迭代项（选择器出处已在类 KDoc 逐条列出）。
- 生产 Application 启动副作用（cra-startup 三调度、WorkManager workdb）在沙箱 UID 内的具体表现待设备基线/后账。
- D2 真实重关联、13→15 迁移、N1 恢复、新功能：不在本包范围（维持登记接缝）。

## 7. 恢复与副作用

E 盘新增 v2 候选与 freeze；缓存复用 j1-v2（无新增依赖下载）。共享仓库仅新增本报告；原 v1 候选/freeze/报告只读未动；HEAD 全程未变。

## 8. 交付状态

**BUILD_READY=true（测试 APK 冻结 + 宿主复用核验）；DEVICE_ACCEPTED=false；仪器 NOT RUN（不写设备 PASS）。** 停止，交 Codex 审核；通过后由唯一 MuMu 所有者按 §5 执行。
