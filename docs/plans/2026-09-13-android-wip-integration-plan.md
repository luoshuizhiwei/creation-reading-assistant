# Android/native 未提交 WIP 集成与提交路线（P0）

> 日期：2026-09-13
>
> 范围：仅 `android/` 与本文件关联的 Android 文档；不触及 Electron。
>
> 基线：`main` / `6cd29670e54c3fc2915d876896ecbaae8d000aa4`。
>
> 本文是分组与集成计划，不授权 stage、commit、push、reset、checkout 或清理既有工作区。

## 1. 现状与已关闭边界

工作区有 208 条并行 WIP 状态记录，不能按文件名或行数直接推断作者和功能归属。对 `HEAD` 的 Android 审阅清单中有 98 条已跟踪改动路径、85 条未跟踪路径；二者来自不同 Git 视图，只可作为盘点线索，不能据此把任一组直接提交。

应用内书籍目录 R3 已由 WorkBuddy 在真机完成 A–E 独立验收。它应当作为一个完整的未来提交切片，而非混入 Reader、首页或 Profile 的重构。R3 验收不等同于安装脚本通过；也没有判定私有副本保留策略正确或错误。

## 2. 集成前的硬规则

1. 先为每个候选切片生成 `HEAD` 对比清单、未跟踪清单和行为说明；未知作者/范围的文件保持原样。
2. `AppDatabase`、Room schema、迁移测试、导航路由、共享设置模型和 `ShelfImporter` 是串行 seam；一个集成者一次只处理一个 seam 集合。
3. 不把格式化或 CRLF/LF 转换混入功能提交。目前至少 `HomeContinueSheet.kt`、`HomeUiState.kt`、`HomeMetricsSection.kt`、`ReaderAiExplainSheet.kt`、`ImportSheets.kt`、`ShelfSharedComponents.kt`、`HomeViewModel.kt`、`SearchViewModel.kt` 与 `android/scripts/install_with_confirm.ps1` 有行尾转换提示，必须在各自功能差异中逐项确认。
4. 一个切片只有在其最小测试、编译以及审阅均完成后才可进入候选提交；全部切片冻结后才串行运行全量 JVM、lint、APK 与需要的真机验收。
5. 所有真机验收仍交 WorkBuddy。若安装脚本失败，先保留脚本失败证据；仅确认目标 serial 后可用 `adb -s <serial> install -r` 继续功能测试，并明确记为回退。

## 3. 候选切片与文件所有权边界

| 顺序 | 切片 | 应成组的主要区域 | 关键 seam / 不能拆开的原因 | 当前状态 |
|---|---|---|---|---|
| 0 | 本计划与 R3 状态同步 | `docs/handoff/`、R3 路线文档、本计划 | 只记录已验证事实；不触发产品行为 | 本轮完成，未提交 |
| 1 | R3 书籍目录、来源索引与增量更新 | `feature/library/`、`LibraryBrowser*`、`ShelfImporter.kt`、`LibrarySourceRef*`、`AppDatabase.kt`、v13→v14 schema/迁移测试及对应单测 | 导入时落来源、DB schema、浏览判定、扫描上限必须一致 | 已验收；完整只读审阅已完成（[`2026-09-13-android-r3-slice1-review.md`](2026-09-13-android-r3-slice1-review.md)），待集成者按其 hunk 清单拆分后独立提交 |
| 2 | 阅读器选区动作、字典、规则/替换与设置 | `feature/dictionary/`、`feature/reader/`、reader sheets、Reader/ViewModel 及其测试、阅读设置存储 | 选区能力、可用性、规则持久化和 UI 文案跨层；不得拆成“UI 有入口、后端未接线” | 未进行本轮验收 |
| 3 | 首页、档案页、灵感页的组件拆分与交互 | `ui/screen/home*`、`homearchive/`、`profile/`、`inspiration/` 及专属 ViewModel/测试 | 大文件拆分可能改变状态归属和导航；按页面纵切，不与 Reader/R3 混入 | 收口报告已出（`reports/slice3-home-profile-inspiration-integration.md`），待集成者审阅后提交 |
| 4 | 书架详情、导入面板与共用 Shelf UI | `ui/screen/shelf/`（排除切片 1 已归属文件）、书架专属 ViewModel/测试 | 与切片 1 共用 `ShelfImporter`，切片 1 先稳定后再接 UI 重构 | 收口报告已出（`reports/slice4-shelf-ui-integration.md`，含 `ImportSourceSheet.kt` 等导入面板拆分），待集成者审阅后提交 |
| 5 | 共享数据、设置、搜索、同步与安全配置 | `data/settings/`、`data/repository/`、`data/local/`（切片 1 除外）、`feature/search/`、`feature/sync/`、`data/security/` | 有跨页面消费者；先建立调用图和迁移影响再分出最小独立提交 | 只读调用图审计已完成（[`2026-09-13-android-shared-seams-audit.md`](2026-09-13-android-shared-seams-audit.md)）：`data.settings` 主体归属 Reader（25/50 消费者），须并入切片 2 而非独立成片；仍禁止直接提交 |
| 6 | 安装脚本可靠性 | `android/scripts/install_with_confirm.ps1` 及只为它服务的测试/文档 | 已在正常 PowerShell（显式恢复默认 `PATHEXT`）复现并修复，见本文件 §6a | **2026-09-13 已修复并真机复现通过，待独立提交** |

## 4. 推荐的串行集成顺序

1. 切片 1 manifest 已建立于 `docs/plans/2026-09-13-android-r3-slice1-integration-manifest.md`，并已核对 Room v13→v14、`ShelfImporter`、来源扫描和 Library Browser 的调用闭环。后续提交前仍须按其中的 v12→v13 共享 schema 顺序处理。完整只读审阅与 hunk 级拆分清单见 `docs/plans/2026-09-13-android-r3-slice1-review.md`（注意：`ShelfViewModel.kt` 当前改动属 L1 动态视图、`ImportSourceSheet.kt` 属切片 4，均不进 R3 提交）。
2. 冻结切片 1 后，按切片 2 的“数据/能力 → ViewModel → Reader sheet → UI 测试”顺序审阅。Reader 共享宿主不得与其他页面拆分并行修改。
3. 切片 3 按首页、Profile、灵感三个独立纵切继续；有共用 state/model 时交回集成者。
4. 切片 4 在 `ShelfImporter` 已冻结后开始，避免 R3 导入链和 UI 拆分交叉覆盖。
5. 切片 5 必须先输出调用方表，再按数据迁移、设置、搜索/同步的实际依赖切分；不接受“大杂烩基础设施提交”。
6. 切片 6 已在 2026-09-13 完成根因定位与最小修复（见 §6a）。验收必须区分 `SCRIPT_EXITCODE=0` + `RESULT: CONFIRM_LOOP`（自动安装路径成功）与 direct-ADB 功能回退（仅记为 FALLBACK，不得写成脚本 PASS）。
7. 所有候选切片完成代码审阅和定向验证后，再由单一集成者串行跑全量门禁；需要真机行为证据的切片交给 WorkBuddy。

## 5. 每个切片的最低证据包

```powershell
# 只读清单；在仓库根执行
git diff --name-only HEAD -- android
git ls-files --others --exclude-standard -- android
git diff --check -- android/<本切片路径>

# Android 门禁；仅在切片冻结并且不与其他构建抢占时执行
Set-Location android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```

提交前还必须包含：变更目的、文件归属、调用链/迁移影响、定向测试输出、未覆盖项，以及与其他 WIP 无重叠的证据。不能以“全量构建绿”替代这些归属核对。

## 6. 独立产品决策，不混入当前提交

| 议题 | 已确认事实 | 尚需决定/审计 |
|---|---|---|
| 私有正文副本回收 | 书架删除目前是 DB 软删除；“移除正文”才删除 `files/books/` 内容 | 是否需要回收站、保留期、空间统计、恢复入口和批量清理；先审计现有契约和用户可见语义 |
| `library_source_refs` 备份导出 | R3 审阅（2026-09-13）确认：`JsonBridge` 的备份导出/导入清单未包含该表，备份恢复后「精确已入架 / 内容有更新」判定退化为弱判定，导入基线无法完整重建 | 是否将来源引用纳入备份（备份完整性既有契约要求新增实体必须扩展 JsonBridge）；或显式接受降级并记录 |
| ~~自动安装脚本~~ | **2026-09-13 已定位并修复（见 §6a）。根因是脚本自身两个缺陷，与 `PATHEXT` 无关** | 是否补一个不依赖真机的脚本回归用例；失败证据（`$xmlParseErrors`、原始 adb 输出）保留到什么粒度 |

## 6a. 安装脚本根因与修复（2026-09-13，切片 6）

**两个独立缺陷，均已修复并在 `c49ac6cf` 复现通过：**

1. **PowerShell 5.1 STDERR 提升。** 脚本第 14 行 `$ErrorActionPreference='Stop'`；`adb push` 把成功摘要
   （`1 file pushed, 0 skipped. 38.2 MB/s…`）写到 **STDERR**。Stop 下原生命令的 STDERR 被提升为终止性
   ErrorRecord，其消息就是 adb 的*成功*文本，于是 `Invoke-Adb` 抛错 → `catch` → direct-install 回退 → `exit 2`。
   ⇒ 在 Stop 脚本里“为了静音加 `2>$null`”是反向操作；正确做法是执行期间临时降为 `Continue`，只看 `$LASTEXITCODE`。
2. **UI dump 编码损坏。** `adb exec-out cat` 返回原始 UTF-8，PowerShell 按控制台代码页（zh-CN=CP936）解码，
   中文乱码并吞掉属性闭合引号 → `[xml]` 每轮抛 `"com.miui.packageinstaller" 是一个意外标记`，被空 `catch {}`
   静默吞掉 → 确认循环永远“看不见”安装器 UI → 超时回退。
   ⇒ 改用 `adb pull` + `Get-Content -Raw -Encoding UTF8` 显式解码；解析失败计数 `$xmlParseErrors` 暴露到失败原因。

**修复方式：** 新增 `Invoke-AdbNative`（执行原生 `adb`，内部临时 `Continue`，返回 `@{ExitCode;Output}`），
`Invoke-Adb` 改为只按 `$ExitCode` 判定；所有裸 `& adb` 调用点（push/pull/shell/install/am start/get-state/pm path/
finally 清理）统一改走该函数。未吞异常、未强制 `exit 0`、未删除失败检查、未要求手动点击、未改设备安全设置。

**判定口径（写报告时不得违反）：** 只有 `SCRIPT_EXITCODE=0` 且 `RESULT: CONFIRM_LOOP` 才是自动安装路径成功；
`RESULT: FALLBACK_DIRECT_INSTALL (…NOT a confirm-loop PASS)` 必须按回退记录，不得写成脚本 PASS。

## 7. 本轮完成定义

P0 的完成是：R3 状态不再显示“待验收”，并且未来集成者能按本计划识别 seam、顺序和风险。P0 不表示 208 条 WIP 已审阅、可提交或已验收。
