# Android 剩余 WIP 验证与原子提交审计报告

> 日期：2026-09-02
> 审计者：小元宝（洛水之蔚的 WorkBuddy 助手）
> 基线：`main` HEAD = `169577e`（`feat(android): strengthen reader navigation and scroll state`）
> 授权范围：验证并提交「已完整接线、可独立验收」的 Android WIP；不碰桌面端/`archives`/根配置；不 push；不重写历史。
>
> **后续状态（2026-09-08）**：本文是 09-02 的历史 WIP 快照。用户随后授权死代码清理，表中未接线的
> `SpineTexture.kt` 已删除；当前结论见 `docs/qa/android-structure-cleanup-2026-09-08.md`。

## 结论（TL;DR）

- **实际创建 commit：0 个。**
- 12 个候选全部未满足提交条件：
  - 11 个 Android 源/WIP **没有任何真实调用点**（未被调用的代码，按纪律不得伪装成完成功能提交）；
  - `install_with_confirm.ps1` 是完整独立的 MIUI 安装器自动化脚本，但**真机验证未通过**（已装 release 0.5.0 与 debug APK 签名冲突，MIUI 安装器卡住，脚本 90s 超时未装成），按"真实手机验证成功后才提交"原则不提交。
- 三道 Gradle 门禁在「含全部未接线 WIP 的当前工作树」上**全部通过**，证明这批 WIP 至少不破坏编译/测试/lint。
- **未 push、未改桌面端、未改 `archives/`、未重写历史、未删除任何源码。**

## 1. 基线核对

| 项 | 结果 |
|---|---|
| `git log -4` | `169577e` → `c712ab1` → `0152b8c`（桌面）→ `3b3afac` ✅ 符合预期 |
| 未提交项总数 | **12**（9 个 `??` 未跟踪 + 3 个 `M` 已修改） |
| `git rev-list --count origin/main..HEAD` | 214（本次 0 新增，历史状态未变） |

## 2. 审计方法

- 用 Grep 全仓确认每个符号的引用点。**关键坑位**：模块源码在 `android/app/src/...`，Grep 的 `glob: src/**` 相对 `android` 解析为 `android/src/**`（不存在），会漏检。修正为 `path=android/app/src` 后重测。
- 真机用 `adb devices` 确认（显式 `adb -s c49ac6cf`，禁 MuMu/模拟器）。

## 3. 三道 Gradle 门禁真实结果

| 门禁 | 命令 | 结果 |
|---|---|---|
| assembleDebug | `.\gradlew.bat :app:assembleDebug --no-daemon` | ✅ BUILD SUCCESSFUL，`app-debug.apk` 53MB（13:08） |
| testDebugUnitTest | `./gradlew.bat :app:testDebugUnitTest --no-daemon` | ✅ BUILD SUCCESSFUL in 34s |
| lintDebug | `./gradlew.bat :app:lintDebug --no-daemon` | ✅ BUILD SUCCESSFUL in 28s |

> 注：`UnusedResources` 默认 severity=warning（不挂 lintDebug），故 heatmap/*/common_*/ai_* 等未使用字符串仅 warning，不影响门禁。

## 4. 真机验证（install_with_confirm.ps1）

- `adb devices` → `c49ac6cf device`（小米，MIUI V816 / Android 15）。✅ 真机可用。
- 脚本默认 serial `c49ac6cf`，所有 `adb` 命令显式带 `-s $Serial`，只点 `com.miui.packageinstaller` 的确认控件且要求先勾选风险提示——符合任务约束。
- **验证结果：未通过。** 脚本 push APK 到真机（`/sdcard/Download/cra-codex-*.apk` 残留可证），启动 MIUI 安装器后因真机已装 `com.creationreadingassistant` 0.5.0（签名 `e63b8de7`）与 debug APK 签名不同，安装器停在签名冲突界面，脚本 selector 不处理该情况 → 90s 超时未装成，真机 CRA 仍为 0.5.0 原版。
- **清理**：脚本 finally 未执行（残留未删），已手动 `adb -s c49ac6cf shell rm -f` 清理远程 `cra-codex-*.apk` 与 `cra-install-ui-*.xml`。复查无残留。
- **结论**：ps1 不提交。需先在真机卸载旧版或统一签名，或扩充脚本处理签名冲突分支后再真机复验。

## 5. 未提交 WIP 逐项清单（12 项）

| # | 文件 | 状态 | 未提交原因 | 所缺调用层/测试 | 建议下一步 |
|---|---|---|---|---|---|
| 1 | `ui/screen/stats/StatsPage.kt` | M | 仅新增 `HeatmapCell` 数据结构（7 行）；HeatmapSection UI 未接线，heatmap 功能未闭合 | 无 `HeatmapSection(...)` 调用点 | StatsPage 接入 HeatmapSection 渲染后，连同 #2/#10 一起提交 |
| 2 | `res/values/strings.xml` | M | 新增 6 个 `heatmap_*` 字符串，为未接线的 HeatmapSection 准备 | 无代码引用（除 #10 自身） | 随 #1/#10 heatmap 闭合提交 |
| 3 | `scripts/install_with_confirm.ps1` | M | 完整独立脚本，但真机验证未通过（签名冲突） | 真机安装成功验证 | 真实手机（已卸载旧版/同签名）复验通过后再提交 |
| 4 | `app/lint.xml` | ?? | `UnusedResources` 整体降 warning + ignore 一批预留字符串（`common_*`/`ai_*`/`dialog_*`/`empty_*`/`status_*`/`toast_*`）；抽查 0 引用确属预留，但本轮无配套功能，且整体降级属 broad 放宽 | 无本轮功能随附 | 随对应功能（AI 面板/通用词库落地）一并提交 |
| 5 | `feature/reader/pager/ReaderMediaButtonBridge.kt` | ?? | 无外部调用点 | 缺 ReaderScreen/媒体键导航层实例化 | 接媒体键→阅读器导航后提交 |
| 6 | `feature/sync/LocalZipBackup.kt` | ?? | Hilt `@Inject` 无调用方 | 缺 BackupManager/设置页调用入口 | Backup 流程接入后提交 |
| 7 | `feature/text/HanConvert.kt` | ?? | 无调用点 | 缺文本导入/显示层简繁转换入口 | 在导入或阅读层接 HanConvert 后提交 |
| 8 | `ui/components/SpineTexture.kt` | ?? | `internal`，无调用点 | 缺书架/封面调用 `SpineTexture` 的 Composable | 书脊纹理渲染接入后提交 |
| 9 | `ui/screen/reader/tts/engine/`（5 文件 + `edge/`） | ?? | 目录内自引用；`TtsEngineProvider` 注释明确"真正切到 `TtsEngine.play` 播放通道留待后续" | 缺 `TtsController` 接入 `TtsEngine.play` 的调用层 | TTS 播放通道接好后，连同 `SystemTtsEngineWrapper`/`EdgeTtsEngine` 一起提交 |
| 10 | `ui/screen/stats/components/HeatmapSection.kt` | ?? | 无调用点 | 缺 StatsPage 调用 | 随 #1/#2 一起提交 |
| 11 | `ui/screen/stats/components/YearBillHeroCard.kt` | ?? | 无调用点 | 缺 StatsPage 调用 | StatsPage 接入年度账单卡后提交 |
| 12 | `ui/theme/TonalPalettes.kt` | ?? | 无调用点 | 缺 Theme 调用入口（未来动态主题） | 动态主题接入后提交 |

> 所有 `??` 未跟踪文件均**保留不删除**，符合用户"不得为了清干净删除源码"的要求。

## 6. 合规确认

- ✅ 未 push（`origin/main..HEAD` 214 个提交仍未推，本次 0 新增）
- ✅ 未改桌面端（`src/`/`electron/`/`scripts/` 桌面部分/`docs/` 桌面 runbook 未动）
- ✅ 未改 `archives/`
- ✅ 未重写历史（无 rebase/squash/reset/checkout/clean；HEAD 仍为 `169577e`）
- ✅ `git diff --check` 无空白错误（仅 ps1 有 LF→CRLF 提示，非错误）
- ✅ 所有 12 个未提交改动原样保留
