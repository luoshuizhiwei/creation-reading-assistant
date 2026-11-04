# S09-B 62 条 UI 自动化分诊 + 现役输入链核对（2026-09-28，只读）

- 仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`｜分支 `codex/recover-worktree-20260920`｜本轮零源码/测试/账本改动，未运行 Gradle/ADB/MuMu；唯一写入 = 本报告。
- 输入：`docs/handoff/current.md`、任务账本（S09-B 行）、[B 窗口读者风险增量](2026-09-28-stage-b-reader-risk-delta.md)、[S06 MuMu 验收](../../../qa/2026-09/2026-09-27-s06-mumu-page-turn-rtl-acceptance.md)（快翻 8/8 + 滑杆 4/4，Codex 已复核）、[9/22 联合验收报告](../../../qa/2026-09/2026-09-22-s09b-s15-duration-gesture-device-acceptance.md)（62 条定义与阻塞证据）。
- 口径更正：**「所有 Compose 测试无法运行」不再是当前事实**——MuMu + qahost + `CraIsolatedTestRunner` 通道已被 S14（2/2）与 S06（12/12）实证可用；本报告不把 62 条未运行等同于 62 个缺陷，也不重排已通过的 12 项。

## 1. 62 条的当前构成（源码级核对，用例数与 9/22 计划完全一致）

| 类（当前 FQCN） | 数 | 内容 | 现役引用（生产符号） |
|---|---|---|---|
| `ui.screen.ReaderScreenTest` | 37 | 见 §2 四组 | `TapZonePreviewCard` ← `ReaderPagingSettings.kt:95`；`LegacyEpubTapArbiter` ← `PagedEpubContent.kt:243`（均为当前可达能力） |
| `ui.screen.stats.components.DurationUnitToggleEntryTest` | 10 | 5 个时长入口 ×（唯一长按切一次 / 普通点击不切） | 统计/首页生产组件（**注意真实 FQCN 含 `stats.components`，目录在 `stats/`，F-6 未修**） |
| `ui.screen.shelf.DurationPillGestureTest` | 7 | 会话/完读时长 pill 点击与长按契约 | 书籍详情生产组件（完读 pill 真实数据缺失时不渲染 = F-2，测试用夹具） |
| `ui.screen.readinghistory.MyReadingTimelineRowGestureTest` | 8 | 阅读历史行四态归属 + 单位跟随 | 阅读历史生产组件 |

四个类均已编译进 qahost androidTest 变体（`build/tmp/kotlin-classes/qahostAndroidTest/`），四类源文件均无 Hilt 依赖（ReaderScreenTest 仅注释提及 hiltViewModel，实际渲染自足夹具）。

## 2. ReaderScreenTest 37 项分组

| 组 | 数 | 方法（摘要） | 判定 |
|---|---|---|---|
| A 纯状态断言 | 12 | loading/error/chapter 状态类、controls/sheet 状态类、`all_sheet_types_can_be_opened`、`action_types_cover_all_UI_interactions` 等 | 本可在 JVM 跑的用例放进了 instrumentation 类（分层错位，非缺陷）；随批次顺带跑 |
| B 通用渲染冒烟 | 4 | controls 文本、长标题省略、320dp 定宽 Box、双字号文本 | 自造组件，不触及生产 Reader；320dp 是固定宽度弱化版，不是真机小屏证据 |
| C 生产预览卡 | 6 | 三区等宽、五区 12%/76% 带、左手镜像、双向推进说明、TalkBack 语义 | 覆盖现役设置页预览卡；断言共享几何（注：与 B 窗口报告「预览卡第三套 24/52/24」表述冲突——当前测试期望共享几何且注释称旧比例会被抓住，以测试与源码为准，B 窗口该行需复核） |
| D 生产点击仲裁层 | 15 | 路由×3、边界 no-op×4、中央恰好一次、可用边缘恰好一次、命名 disabled 语义×5、文案随映射 | 触摸行为 + 语义树双面；断言停在 `onPrev/onNext/onToggleControls` **回调边界** |

**输入链影响面的否定结论（关键）**：D 组断言停在仲裁回调——位于 9/24 `PageTurner` 队列化的**上游**；37 条均不引用 `KeyEvent`/`ReaderHardwareKeys`——不在 9/22 重构面内。S15 三类在 stats/history 组件层，同样不受两次重构影响。**因此「62 条因输入链变化而过期」不成立**；它们覆盖的层仍然现役，值得原样跑。

## 3. 按根因分组（替代「62 个缺陷」的错误口径）

| 根因 | 覆盖 | 性质 |
|---|---|---|
| R1 通道：9/22 主包 + `AndroidJUnitRunner` 在 MIUI 真机被「后台拉起 Activity」拦截（`result code=102`，0 完成） | 全部 62 | 验证缺口；已被 MuMu qahost 通道取代，非产品缺陷 |
| R2 寻址：`DurationUnitToggleEntryTest` 目录/包不一致（F-6 未修） | 10 | 测试一致性；命令用真实 FQCN 规避，无需先改文件 |
| R3 分层错位：A+B 共 16 条不依赖设备 | 16 | 工程债；建议后续由测试 owner 迁 JVM（本窗口不动） |
| R4 陈旧覆盖缺口：`all_sheet_types_can_be_opened` 只 contains 10 个枚举，现 `ReaderSheet` 已有 12 项（新增 `RULES`/`DICTIONARY` 未断言）；`action_types_cover_all_UI_interactions` 自含 16 构造（`ScanTxtTocRule` 仍在 `ReaderModels.kt:125`，签名未变，当前可过） | 2 | 不会失败但对新入口零覆盖；登记为测试侧待办，不阻塞批次 |
| R5 真正未验证（9/22/9/24 之后）——见 §4 批次 2/3 | — | 见下 |

## 4. 交总指挥的最小 MuMu/qahost 验收批次

### 批次 1：现有 62 条原样跑（一次定向运行）

- **前置数据**：无书籍依赖（全部自足夹具）；qahost + qahost.test 在装、正式包不存在。
- **APK**：复用 09-27 gate（宿主 `a4669f85…`、测试 `6d19c244…`，`E:\creation-reading-assistant\codex-s04-gate-20260927\`）。运行前按 S06 §2 口径复核源码新鲜度——本轮已核 `android/app/src` 无 `.kt` 晚于 2026-09-27 15:47，gate APK 仍匹配当前源码（含 9/22/9/24 输入链改动）。
- **命令**（注意：通道是 qahost + CraIsolatedTestRunner，**不是** 9/22 的主包 + AndroidJUnitRunner；DurationUnitToggleEntryTest 用真实 FQCN）：
  ```bash
  adb -s <serial> shell am instrument -w -r -e class \
  "com.creationreadingassistant.ui.screen.ReaderScreenTest,com.creationreadingassistant.ui.screen.stats.components.DurationUnitToggleEntryTest,com.creationreadingassistant.ui.screen.shelf.DurationPillGestureTest,com.creationreadingassistant.ui.screen.readinghistory.MyReadingTimelineRowGestureTest" \
  com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
  ```
- **成功判据**：`numtests=62`、`OK (62 tests)`、ADB 退出码 0；逐项落盘。个别 AssertionError 按 §3 R3/R4 归类为测试侧或产品候选，不并入「未运行」。
- **失败时查**：logcat 出现 `Abort background activity starts`/`result code=102` ⇒ 通道回归，回退 SR-ENV-1 裁决；`ClassNotFoundException` ⇒ FQCN/包问题；断言红 ⇒ 保留原始栈并登记。
- **为什么不重复 S06**：本批次类清单不含 `PageTurnerHighSpeedTest`/`CraSliderRtlRenderTest`；已 PASS 的 12 项直接引用 S06 §3/§9。

### 批次 2：硬件键→队列接线（9/22 重构后**从未有任何设备证据**）

- **为什么**：`ReaderHardwareKeysTest`（JVM 9/9）只测决策纯逻辑；`MainActivity.kt:215/220`（KeyEvent）与 `:230`（`dispatchScroll`）→ `ReaderMediaButtonBridge`（媒体键桥 `triggerDirection`）→ `PagedReaderControllerEffects.kt:185`（`externalTurnRequest`→`turnRequest`）→ PageTurner 队列的**接线层零覆盖**；9/22 人工矩阵只测了点按热区，无硬件键项。
- **做法**（设备动作，无需新测试代码）：qahost 打开隔离测试 EPUB，依次 `input keyevent` 注入 `VOLUME_DOWN/VOLUME_UP`、`PAGE_UP/PAGE_DOWN`、`SPACE`、`DPAD_RIGHT/LEFT`、`MEDIA_NEXT/MEDIA_PREVIOUS`（后者注意 B 窗口登记的「媒体键桥仅分页引擎激活」激活条件），每键后 uiautomator dump 读页码/进度；对照组：阅读器外按音量键应保持系统行为。滚轮无 adb 注入通道，记 UNPROVEN。
- **成功判据**：翻页方向正确、边界静默、防抖语义与 `ReaderHardwareKeysTest` 钉住的契约一致；turnRequest 经队列排空不卡死（结合 S06 快翻已证的队列行为）。
- **失败时查**：logcat `CRA-Boot`（隔离 Application 启动）、KeyEvent 相关 tag、`CraIsolatedTestApplication` 是否正常换入。
- **为什么不重复 S06**：S06 12 项全部为 Compose 组件内注入 turnRequest，不含任何 KeyEvent/滚轮路径。

### 批次 3：点按全链 + F-1 复核（9/24 队列化后的生产渲染路径）

- **为什么**：S06 的连点用例在 harness 里直接写 `turnRequest`；9/22 人工矩阵跑在队列化**之前**的代码上。生产链「点按 → `LegacyEpubTapArbiter` 回调 → `turnRequest` 写入（`PagedReaderPageSurface.kt:428`）→ 队列 → 动画」的中间接线段无任何测试。
- **做法**：qahost 打开测试 EPUB，三区预设：动画期间（连点）**翻页热区连点 2 次 ⇒ 应推进 2 页**（旧代码=第 2 次被吞，这是 9/24 后最有牙的人工断言）；首/末章边界静默（对照 9/22 人工结果）；控制栏展开时点热区（复核 F-1：同帧收起+翻页是否有意，3 次独立观测的老候选）。
- **成功判据**：连点推进数=点按数（上限 3 内）、边界 no-op、F-1 行为记录归档供产品裁决。
- **失败时查**：uiautomator dump 进度序列、logcat 翻页相关 tag。
- **为什么不重复 S06/9/22**：S06 不经生产点按仲裁；9/22 人工矩阵无「动画期间热区连点」项且代码已变。

**明确不进批次**：同帧单槽合并（S06 §6.3 已登记，产品语义待决，现有测试无法表达）；切书清队（[09-28 切书核查](2026-09-28-s06-book-switch-queue.md)已证伪可达性，零改动，无需测试）。

## 5. 分类与最小行动归属

- **产品故障**：本轮 0 个新增。F-1（控制栏展开双动作）维持 9/22 候选体验缺陷，批次 3 复核后由 Codex 裁决。
- **验证缺口**：62 条运行结果（批次 1）；硬件键接线（批次 2）；点按全链（批次 3）；滚轮注入通道缺失记 UNPROVEN。
- **产品决定**：F-1；F-2（完读卡不显示用时是否有意——S15 范围）；媒体键桥激活条件；同帧单槽合并。
- **测试侧待办**（需测试 owner，本窗口不动）：R3 迁 JVM、R4 补 `RULES`/`DICTIONARY` 断言、F-6 目录/包归一。
- **不重复 S06 已通过 12 项**：批次类清单与其零交集；其结果继续有效。

## 6. 边界声明

本轮只新增本报告；未修改测试/产品/Gradle/账本/他人报告；未运行 Gradle/ADB/MuMu；未 commit/push/reset/checkout；报告只用「测试 EPUB」等中性名称，未抄录任何书名或隐私 XML。批次命令仅为给总指挥的建议，未执行。落盘即停，等总指挥复核。
