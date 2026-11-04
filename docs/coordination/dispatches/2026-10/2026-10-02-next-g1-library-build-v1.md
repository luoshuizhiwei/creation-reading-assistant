# G1-build-v1 — 独立书库 UI 验收包与最小夹具准备报告

- 任务编号/版本：**G1-build-v1**（依据 Codex-review-N1v3-Library-v1 的并行安排：从 J1-v3 建新 UID 书库 UI 包，允许 qahost 后缀单 hunk 与一个新增仪器类）。
- 开始时间：2026-10-02T10:50+08:00；应用/模型：ZCode / GLM（account:zai-start-plan）；零设备操作、零 ADB。
- 输入指纹：HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（全程未变）；父候选 `next-round-joint-j1-v3\android`，其 `freeze/six-file-sha256.txt` **重算 6/6 MATCH**。与 N1-v4 修复独立，未读取/集成 N1 修复内容；D 盘产品树未触碰。

## 1. 所有权与改动

新候选 `E:\creation-reading-assistant\next-round-g1-library-build-v1\android`（876 文件，自 J1-v3 已核源码派生）。改动恰为合同范围（`freeze/evidence/tree-diff-v3.txt`：875 vs 876，changed=1 + only-in-G1=1，无其他差异）：

| 文件 | 类型 | SHA-256 |
|---|---|---|
| `app/build.gradle.kts` | 唯一 hunk（-1/+1）：qahost `applicationIdSuffix` `.qahost` → `.qahostlibrary` | 450dec2253553b397c917de5d9a3c212dcf2468d3b0ad24404d0344e82a46c84 |
| `app/src/androidTest/.../feature/library/LibraryLifecycleAcceptanceTest.kt` | 新增仪器类 | 8f7552cd47f32e5acd6a64daa4ea9dd5f6866a33c2a65cb47b63a67b88565b33 |

其余产品/测试/依赖/runner/manifest 源文件保持原字节；补丁 `freeze/evidence/g1-single-hunk.patch`。runner 未更换、未加依赖——现有 `CraIsolatedTestRunner` 支持所需夹具，**无接缝请求**。

## 2. 环境与构建

- JDK17 `D:\develop\Java\jdk-17.0.14`；缓存 `E:\creation-reading-assistant\next-round-joint-j1-v2\_cache\gradle-home`（复用现有 E 盘依赖；占用检查：本轮独占使用，无其他窗口并发写入）。
- 构建：`:app:assembleQahost` + `:app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost` → **EXIT=0**（`freeze/evidence/build-g1.log`）。未跑全量 JVM/Lint/Release、未调 ADB。

## 3. 执行与结果（身份与合同核验）

| 项 | 实测值 |
|---|---|
| 宿主 applicationId | **`com.creationreadingassistant.qahostlibrary`**（aapt badging；versionCode 3 / 0.5.1） |
| 测试 APK 包名 | `com.creationreadingassistant.qahostlibrary.test` |
| targetPackage / runner | `com.creationreadingassistant.qahostlibrary` / `com.creationreadingassistant.testing.CraIsolatedTestRunner` |
| provider authorities | FileProvider → `com.creationreadingassistant.qahostlibrary.fileprovider`；androidx-startup → `….qahostlibrary.androidx-startup`；mlkit → `….qahostlibrary.mlkitinitprovider`——全部随新 applicationId 命名空间，与 qahost/生产无冲突 |
| 签名 | 双 APK apksigner verify exit 0；CN=Android Debug；证书 SHA-256 `05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1`（与既有验收批次同证书） |
| 宿主 APK | `freeze/apk/app-qahost.apk` SHA `4bf670ab4006c6d60a7e18d8d31d0856d55755d6b8ff6994b3f41edc3b272531` |
| 测试 APK | `freeze/apk/app-qahost-androidTest.apk` SHA `70b04ecf3628caeedc5cb2d14a89628d23ab8927665efcdf3cd30121bcd95c8f` |
| UID 合同 | 新 applicationId = 独立 UID = 独立私有库/文件/KeyStore 命名空间；**设备上是否预先不存在由下一设备窗口实查**，本轮零 ADB 不声称已确认 |

## 4. 新增仪器类：夹具与未来验收范围

`LibraryLifecycleAcceptanceTest`（4+5 方法，分阶段协议，结果落盘 `filesDir/g1-results/<阶段>.json` + logcat `G1LifeAccept`）：

**A. 删除撤销子链**：`seedLifecycleFixture`（书+进度+笔记+标签/书单关系+**此前已删除标签对照**+真实正文文件）→ `assertLifecycleFixtureReady` → UI 搁置 → `assertAfterShelve`（state=shelved、指针/文件不动）→ UI 移除正文 → `assertAfterRemoveContent`（指针清空/`content_status=missing`/文件已删/进度笔记保留）→ UI 删除整本 → `assertAfterDeleteAll`（书/进度/笔记同批软删、关系行清理、内容文件按撤销保留策略如实记录）→ `seedPostDeletionChange`（删除后合法新改动对照）→ UI 撤销 → `assertAfterUndo`（快照恢复；**预删标签不复活、其关系不重建；删除后新改动不被覆盖**）。

**B. L1 保存筛选**：`seedSavedFilterData`（finished/reading/shelved/unread 四态可辨认书 + 验收书单）→ `assertSavedFilterData` → UI 保存视图为「G1 验收视图」→ `assertSavedFilterPersisted`（读 `ShelfSavedViewsStore` DataStore，条件如实记录）→ UI 退出重进 → 再跑一次（持久化 + 条件对比）。

**边界（类 KDoc 同步声明）**：夹具写入只是准备，**不称 UI 流程已通过**；**D2 contentHash 去重 ≠ 重新关联**——不把重导入 Duplicate 写成 PASS、未新增产品实现；不重开 13→15 迁移、不用新建库查表冒充迁移（库=沙箱包内首次安装自动创建的 v15）；不访问生产 SecurePrefs/正式书库/外部服务；不关 FK、不换 runner。

## 5. 设备操作步骤（交下一唯一 MuMu 所有者；本轮零设备）

基线保存（先于一切）：新包是否预先存在（`pm path com.creationreadingassistant.qahostlibrary`，预期不存在——若存在停止并报告）、原 qahost APK 对 SHA、持久 DB/文件、系统设置、既有 adb reverse 映射。

1. 安装本对：`freeze/apk/app-qahost.apk` + `app-qahost-androidTest.apk`（同签名覆盖原则；新包首装）。
2. 生命周期 A：按 §4 顺序——每步 `am instrument -e class …LibraryLifecycleAcceptanceTest#<方法> com.creationreadingassistant.qahostlibrary.test/com.creationreadingassistant.testing.CraIsolatedTestRunner`，UI 步骤（搁置/移除正文/删除/撤销）用真实 UI 执行，中间穿插对应读回方法；证据 = `g1-results/*.json` + logcat。
3. 生命周期 B：seed → 断言 → UI 保存视图「G1 验收视图」→ 读回 → 重启应用 → 再读回（两次 conditions 一致）。
4. 恢复：卸载**新沙箱包**仅影响其自有 UID 数据（不触原 qahost/生产）；原 qahost 与持久数据/设置按冻结基线逐项 SHA 对账恢复。

## 6. 未覆盖项

- 设备端一切行为（四项种子/读回方法未运行；UI 流程未执行）；新包首装迁移（v15 自动建库）表现待设备首跑。
- D2 真实重新关联合同未闭合（源码归属缺口，本轮未新增实现）；L1 的 UI 细节（FilterSheet 保存入口交互）未在本包断言，仅 DataStore 读回。
- 未跑全量 JVM/Lint/Release（改动=androidTest 新类+配置 hunk，产品功能零变化）。

## 7. 恢复与副作用

E 盘新增：候选、`freeze/`（双 APK+补丁+tree-diff+日志+READY）、`_evidence/`；缓存复用未复制。共享仓库仅新增本报告。原候选/freeze/报告只读未动。

## 8. 交付状态与下一步

- **BUILD_READY=true（双 APK 冻结）；DEVICE_ACCEPTED=false；仪器未运行**。
- 下一步：Codex 审查本包；通过后由唯一 MuMu 所有者按 §5 执行书库 UI 生命周期与 L1 验收并完整恢复。
