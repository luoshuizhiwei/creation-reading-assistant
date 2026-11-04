# S12 结果：未换算 Legado marker 的 UI 提示与保存保护 — 2026-09-28

> **Codex 最终集成补记（晚于下方首轮及第二轮记录）：** `temporaryInspection` 是会随导航模式改变的 Boolean；两处 500ms 保存 `LaunchedEffect` 原键未包含它，临时查阅切换后协程可能继续使用旧值。Codex 在 `ReaderProgressEffects.kt` 将它加入滚动、分页保存 effect 的 key，并加入 TXT 滚动恢复 effect 的 key；未改状态机或其他文件。新鲜定向 JVM 重新编译生产代码，GuardTest 13/13 + SourcePositionTest 14/14 = **27/27，EXIT 0**，原始日志/XML及 SHA 在 `E:\creation-reading-assistant\codex-s12-integrator-20260928\`。此补记只关闭代码/JVM 接线复核；MuMu 导入→提示→不移动退出→重开 marker 保留→移动后原生进度保存仍需端到端验证。

> **Codex 2026-09-28 集成复核：有待修正的接线风险。** E 盘 run2 XML 汇总为 29/29、EXIT 0，提示字段已消费、退出守卫已接线；接受这两项代码事实及纯状态机 JVM 结果。实际 `ReaderProgressEffects.kt` 两处防抖 collect 调用 `observe` 时，仅在 initial pending/off<0 传 null，**没有在 `temporaryInspection` 为 true 时传 null**，与本报告 §2、§4 的临时查阅主张及测试夹具不一致；临时查阅位置变化可能提前解除守卫。另，`observe` 只在 500ms debounce 后调用，若初次定位后用户在首次 collect 前移动，第一位置会被当作锚点而非主动移动，直到第二次移动才可能保存。需在同一窗口补齐生产接线与判别性测试；在此前 S12 保存保护只记“部分实现/JVM 状态机通过”，不记端到端关闭。无需重复现有 29 项，修复后定向验证相关新增/受影响项。

- 仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`｜分支 `codex/recover-worktree-20260920`｜本轮仅 JVM 定向 Gradle，**未运行 MuMu/ADB/真机**，未进原仓库。
- 授权文件使用情况：`ReaderScreen.kt`（1 处 hunk）、`ReaderProgressEffects.kt`（守卫 + 接线）、新建 `ReaderLegadoMarkerSaveGuardTest.kt`。**`ReaderProgressActions.kt` 零改动**（其 git ` M` 为前轮 S07-F2.4 既有未提交改动，本轮 hunk 与其无关）；`feature/sync/`、`LocalZipBackup`、`JsonBridge`、窗口 2 文件、`ReaderSessionEffects`/`ReaderScaffold`/`ReaderLayerBuilders`（所有权交叉文件，见 §7）全部未触碰。未 commit/push/reset/checkout。
- 任务来源：`docs/coordination/reviews/2026-09/2026-09-28-stage-b-exit-review.md` Codex 裁决——「`unconvertedLegadoPosition` 只有生产字段定义/赋值，无 UI 消费，且 500ms 后保存仍可能把未换算 marker 覆盖为 offset 0。S12 部分完成，不关单」。

## 1. 接线链（本轮建立）

```
ReaderDocumentLoader（上一轮，未再改）
  └─ ReaderLoadedContent.Text.unconvertedLegadoPosition  （marker 存在且本次打开未能换算时非 null）
       ├─【提示】ReaderProgressEffects：文档就绪后一次性 nav.showNotice（首开提示，见 §2 文案）
       └─【保存保护】LegadoMarkerSaveGuard（进程内单书注册表，本文件定义）
            ├─ 武装/解除 effect：seam 非 null → arm；seam null（换算成功/原生进度/换书）→ disarm
            ├─ 模式切换 effect：滚动↔分页切换 → resetAnchor（重锚，不解除）
            ├─ 滚动 500ms 防抖 collect：observe(bid, 恢复落点/用户位置) → 抑制或放行   ReaderProgressEffects:226-241
            ├─ 分页 500ms 防抖 collect：同语义（位置身份 = 全书偏移）                  ReaderProgressEffects:296-303
            └─ ReaderScreen.persistOnLeave（退出/暂停/离开落库唯一汇聚点）：
                 if (!temporaryInspection && !LegadoMarkerSaveGuard.isArmed(bid)) nav.persistCurrentProgress()
```

所有普通保存派发点穷举（`grep ReaderAction.SaveProgress(|SaveEpubProgress(`）：滚动防抖（Effects:261）、分页防抖（Effects:305/313）、退出落库（Actions:291/336，仅经 persistOnLeave 闭包到达）。三处全部被守卫覆盖，无旁路。

## 2. 两个需求的落地语义

**需求 1（首开提示）**：文档就绪（`!isLoading && error == null`）且 seam 非 null 时，经 `showNotice` 提示一次（每书一次 latch）：

> 开源阅读导入的位置暂未能换算（本书目录不可用或章节越界），已按近似位置打开；开始阅读后将记录本书进度

准确性与真实行为一一对应：无法换算 = 目录兜底「全文」单章或章号越界（上一轮的门控结果）；当前落点 = 近似位置（导入百分比驱动恢复或书首）——不谎称「已恢复到原位置」；「开始阅读后」对应需求 2 的解除语义。

**需求 2（保存保护）**：`LegadoMarkerSaveGuardSession` 状态机（纯 JVM）：
- 武装后，第一次**可定位**快照锚定（即初始定位完成后的恢复落点——恢复前的起点因 pending 传 null 不会被锚定，程序化恢复滚动因此不被误判为用户移动）；
- 锚点上的一切自动保存（恢复后 500ms 防抖的 offset≈0 落库正是被覆盖场景）持续被抑制；
- 位置偏离锚点（滚动/翻页/跳章/搜索跳转，含用户主动开启的自动翻页与 TTS 推进）→ 解除 → 本次起按正常原生进度保存；
- `null` 位置（初始定位未完成 / **临时查阅进行中**）→ 不锚定、不解除、维持抑制——临时查阅里的导航不是普通阅读位置变化，返回后仍在锚点则 marker 继续保留；
- 滚动↔分页切换改变位置身份量纲 → `resetAnchor` 重锚而非解除（切换本身不是用户移动）；
- 仅关闭提示不触碰守卫（提示无任何解除副作用）。

**零退化保证**：seam 只在「marker 存在且换算失败」时非 null（上一轮定义）。换算成功、原生 locator、EPUB、无导入位置的书 → 守卫从未武装 → `observe` 返回 null → 三处保存点走**逐字不变**的既有路径；`canPersistNormalReadingProgress`/`canPersistLegacyScrollPosition` 函数本体零改动。

## 3. 改动文件（2 生产 + 1 新测试）

| 文件 | 本轮 hunk |
|---|---|
| `ui/screen/reader/ReaderProgressEffects.kt` | ①`LegadoMarkerSaveGuardSession` + `LegadoMarkerSaveGuard`（判定状态机 + 单书注册表，纯 JVM）；②seam 读取 + arm/disarm effect + resetAnchor effect + 首开提示 effect；③滚动/分页两处防抖 collect 头部插入守卫判定（未武装书路径逐字不变）；④新增 4 个 compose runtime 导入 + `ReaderLoadedContent` 导入 |
| `ui/screen/reader/ReaderScreen.kt` | 仅 `persistOnLeave` 闭包：追加 `&& !LegadoMarkerSaveGuard.isArmed(bid)`（该闭包是退出/暂停/离开落库的唯一汇聚点，ReaderScaffold:319 与 SessionEffects:260 的 onPersistProgress 全部经它） |
| `app/src/test/.../ui/screen/reader/ReaderLegadoMarkerSaveGuardTest.kt`（新建） | 9 个判定语义用例（见 §4） |

注：三个授权文件在工作区均携带**前轮/他人既有未提交改动**（如 ReaderProgressActions 的 S07-F2.4、ReaderScreen 的纸张动效），本轮 hunk 与其互不重叠（diff 逐块核对）。

## 4. 定向测试证据

命令（`android/` 下，两轮同过滤器）：

```bash
./gradlew.bat :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderLegadoMarkerSaveGuardTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderSourcePositionTest" \
  --tests "com.creationreadingassistant.ui.viewmodel.ReaderDocumentLoaderStreamingLegadoTest"
```

| 轮次 | 退出码 | 结果 |
|---|---|---|
| run1 | 1 | 29 项，**1 失败**：临时查阅用例（`observe(t, null)` 在已锚定会话上被误判为位置变化而解除）——**状态机真实缺陷**，被新增用例抓住并修复（null 显式语义：不锚定、不解除、维持抑制） |
| run2 | **0** | **29 tests / 0 failures / 0 errors / 0 skipped**（`BUILD SUCCESSFUL in 40s`） |

run2 逐类：ReaderLegadoMarkerSaveGuardTest 9/9、ReaderSourcePositionTest 14/14（既有门控函数回归）、ReaderDocumentLoaderStreamingLegadoTest 6/6（上一轮 seam 回归）。

用例 → 需求映射：

| 需求 | 用例 |
|---|---|
| 自动保存保护 | `auto save at restored anchor stays suppressed until user moves`：pending→null 不锚定；恢复落点锚定；恢复后 500ms 的落库（offset 0 覆盖场景）持续被抑制 |
| 用户移动后保存 | `user movement disarms and lets normal saves proceed`：偏离锚点 → 解除 → 本次与后续均走正常流程 |
| 退出保护 | 上述两用例的 `isArmed` 断言（武装期间退出落库被抑制，移动后放行）+ `re-arming on reopen starts a fresh anchor session`（重开重新武装） |
| 其他语义防退化 | 模式切换重锚（`mode switch re-anchors…`）、临时查阅不解保（`temporary inspection navigation never disarms…`）、原生/换算成功书永不武装（`converted or native progress books are never armed`）、书间隔离、空 bid 不武装 |
| 上一轮 seam 回归 | `ReaderDocumentLoaderStreamingLegadoTest` 6/6（守卫消费的字段行为不变） |

## 5. 测试原始日志路径

`E:\creation-reading-assistant\codex-s12-marker-ui-guard-20260928\`：

| 工件 | 内容 |
|---|---|
| `00-changed-files-git-status.txt` | 三生产文件 + 新测试的 git 状态（含前轮遗留 ` M` 的归属说明） |
| `10-targeted-jvm-run1.log` | 第一轮（EXIT=1，1 失败，缺陷定位与修复依据） |
| `11-targeted-jvm-run2.log` | 第二轮（EXIT=0，BUILD SUCCESSFUL） |
| `12-run2-xml-summary.txt` + `TEST-*.xml`（3 份） | 逐类 XML 汇总与原始结果 |

## 6. 数据兼容边界

1. **保护期间的数据形态**：marker 原样保留（`{"legado_position_v1":{…}}`），`progress_percent` 保持导入值；武装期间不发生任何进度写库。
2. **解除后的行为**：用户移动后的首次防抖/退出保存即写原生 locator（本次 collect 落的就是用户位置），marker 被覆盖——与既有「首次原生保存覆盖 marker」语义一致，此时是用户选择的位置。
3. **重启/进程死亡**：守卫是进程内会话状态，重启后丢失；但 marker 仍在库中（保护期间未被覆盖），重开时 loader 重新产出 seam → 重新武装 → 保护恢复。不存在「保护失效窗口」。
4. **换算成功的书**：seam 为 null，守卫全程不参与，所有保存路径与上一轮 EPUB 契约报告描述逐字一致（本轮回归 6/6 + 上一轮套件可随时复跑）。
5. **临时查阅（R2-J1）**：武装期间 `reportableSourceTarget` 上报与落库同步被抑制，普通阅读位置（normalReading）在用户移动前不进入协调器——与 initial-pending 期间的既有语义相同。

## 7. 所有权交叉（只报告，未修改）

为实现零跨界，本设计**刻意避开**了三个非授权文件：①`ReaderSessionEffects.kt`（ProgressEffects 的参数转发层——守卫状态经 `loadedBook` 参数直接进入 ProgressEffects，绕开加参转发）；②`ReaderScaffold.kt`（holders 袋定义处——退出守卫放在 ReaderScreen 的 persistOnLeave 而非 Scaffold）；③`ReaderLayerBuilders.kt:102`（分页路径 pending 清除点——守卫不依赖 pending 保持，恢复落点由防抖快照自行锚定）。均无需改动；若总指挥后续希望把守卫改为 holders 袋状态（替代进程内注册表），涉及 `ReaderScaffold.kt`，需另行协调。

## 8. 未运行的验证 / 未解决项

1. **未运行**：MuMu/真机/正式书库验证（本轮与上轮均为 JVM 定向证据）；设备上的「导入流式大 TXT → 首开提示可见 → 不移动即退出 → 重开 marker 仍在 → 移动后保存」端到端链路待设备通道。
2. **未运行**：Compose 效果体接线本身（两处 collect 内的守卫调用、persistOnLeave 闭包、showNotice 展示）——Compose 逻辑不可在 JVM 运行；其正确性由判定状态机测试（接线消费的全部语义）+ 编译通过 + 静态核对覆盖，不冒充运行时验证。
3. 提示文案的 Snackbar 展示时长/视觉未验证（无设备）。
4. 调查报告其余项维持登记：P2-3 导入静默 FINISHED（D-3 未裁决）、P2-4 导入中状态、「开篇」索引对齐与流式嗅探差异（上一轮报告 §5.3/5.4）。

---

本轮未 commit/push/stage/reset/checkout/clean；未清理他人未提交文件；书籍仅用「测试 TXT」等中性名称；与窗口 2/3 无文件交叉。交总指挥复核，本棒停止。

---

## 9. 集成复核修正追加（2026-09-28 第二轮，回应顶部 Codex 注记）

针对注记的两处接线风险做最小修复；文件所有权仍限 `ReaderProgressEffects.kt`、`ReaderScreen.kt`（本轮未再改）与 `ReaderLegadoMarkerSaveGuardTest.kt`。

### 9.1 风险 ①：临时查阅位置被当作普通阅读移动

- **根因**：两处 collect 只在 `initialPositionPending`（分页另加 `off < 0`）时传 null，**没有在 `temporaryInspection` 为 true 时传 null**——临时查阅里的位置变化会进入 `observe` 并提前解除守卫，返回原锚点后 marker 失去保护。
- **修复**：新增纯函数 `legadoGuardObservation(initialPositionPending, temporaryInspection, position)`（`ReaderProgressEffects.kt`，接线判定的唯一入口）：pending 或临时查阅 → null（不锚定、不解除），其余透传。两处 collect 改为只经它构造观察值。临时查阅会话的防抖保存本就被 `canPersistNormalReadingProgress` 抑制，守卫状态在查阅期间完全冻结；返回后重新落到原锚点 → 继续抑制，移动后才解除。

### 9.2 风险 ②：锚点只靠 500ms 后的首采样建立

- **根因**：`observe` 只在防抖 collect 里调用——恢复完成后、首次采样前的用户移动会被当成「锚点」捕获，首次主动滚动/翻页不保存，直到第二次移动才解除。
- **修复**：新增 `LegadoMarkerSaveGuard.anchorAt(bookId, position)`（仅对已武装且未锚定的会话生效；未武装 no-op；迟到锚点不覆盖既有锚点；null 不锚定），并在三条恢复路径即时锚定：
  1. **滚动恢复 effect**（`scrollToItem` 完成后）以真实列表状态 `(firstVisibleItemIndex, firstVisibleItemScrollOffset)` 锚定；
  2. **分页即时锚定 effect**：`snapshotFlow { pagedAbsOffsetState.intValue }` 首次 `>= 0` 上报（= 引擎恢复落点，先于任何用户翻页）即锚定；
  3. **分页→滚动切换即时锚定 effect**：跟踪 `pagerEngineOn` 转换（`previous==true && !current`），切换且初始定位已完成时立即以当前列表状态锚定；首次组合不触发（不把恢复前的 (0,0) 误锚）。
- **不解除保证不变**：模式切换仍先 `resetAnchor` 再即时重锚；程序化恢复只在完成帧锚定；提示关闭不触碰守卫。

### 9.3 判别性测试（先写后修）

`ReaderLegadoMarkerSaveGuardTest` 新增 4 项（修复前引用尚不存在的接缝函数⇒编译红灯，即「当前接线失败」的证据形态）：

| 用例 | 钉住的语义 |
|---|---|
| `temporary inspection positions are never fed to the guard and return keeps protection` | 查阅位置经接线函数必须为 null；查阅期间不解除；返回原锚点仍受保护；返回后移动才放行 |
| `user movement before first debounce sample disarms and saves` | 即时锚定后，首采样前的第一次 observe 即判为移动并放行保存 |
| `eager anchor is ignored for unarmed books and cannot overwrite an existing anchor` | 未武装 no-op；迟到锚点不覆盖既有锚点 |
| `pending snapshots never anchor even with eager anchoring available` | pending 阶段不锚定不解除；恢复完成后锚定照常生效 |

### 9.4 真实验证结果

```bash
./gradlew.bat :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderLegadoMarkerSaveGuardTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderSourcePositionTest"
```

| 轮次 | 退出码 | 结果 |
|---|---|---|
| 修复后定向 | **0** | **27 tests / 0 failures / 0 errors / 0 skipped**（`BUILD SUCCESSFUL in 35s`）：GuardTest 13/13（9 旧 + 4 新判别项）、SourcePositionTest 14/14（门控邻居回归） |

**无退化核对**：未武装书（换算成功/原生进度/EPUB）`legadoGuardObservation` 透传、`observe` 返回 null、`anchorAt` no-op——两处防抖与 `persistOnLeave` 走改动前逐字路径；自动保存/退出保存/普通原生进度三路无事实验证变化。既有 29 项未重跑（依复核注记口径），本轮受影响类全量执行。

**未运行的验证**（与首轮相同边界）：Compose 效果体接线本身不可 JVM 运行（三条即时锚定 effect 与两处 collect 的调用正确性由接缝函数测试 + 编译 + 逐行静态核对覆盖）；设备端到端（MuMu/真机）未执行。

### 9.5 本轮证据追加（同目录 `E:\creation-reading-assistant\codex-s12-marker-ui-guard-20260928\`）

| 工件 | 内容 |
|---|---|
| `20-wiring-fix-run1.log` | 修复后定向原始输出（EXIT=0） |
| `21-wiring-fix-xml-summary.txt` | 逐类 XML 汇总（27/0/0/0） |
| `wiring-fix-TEST-*.xml`（2 份） | 本轮 Gradle 原始 XML |

本轮未 commit/push/reset/checkout，未触碰他人文件与窗口 2/3 资产。修正完毕，交总指挥复核。
