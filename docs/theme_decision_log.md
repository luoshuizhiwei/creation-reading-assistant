# 主题决策记录（Theme Decision Log）

- 日期：2026-08-02
- 背景：在 Codex 的未提交改动（外壳墨绿「纸墨」`#365C4A` + 全黑体 + 页面文件拆分，已验证 `BUILD SUCCESSFUL`）基础上"加强"。经 grill-me 逐分支拷问达成共识。
- 本文件为 **canonical 决策依据**，替代被 Codex `git rm` 的 842 行旧主题文档（`theme_visual_plan.md` 552 行等）。不堆回旧文档体量。

---

## 已锁定的 6 项决策

### 1. 品牌主色 — 全站统一墨绿（选 A）
- 外壳主色（浅）：`PaperInkGreen = #365C4A`；外壳主色（深）：`#8FAF9D`。
- 通过 Material 令牌全局下发：`Theme.kt` 的 `PaperInkLightColorScheme` / `PaperInkDarkColorScheme`，`primary`/`secondary`/`tertiary` 全部 = 墨绿，`AppStreak = #365C4A`。
- 全站只有这一个品牌色，无第二套可切换配色。

### 2. 字体身份 — 大标题宋体 + 其余黑体（选 A）
- 仅 `displayLarge` / `displayMedium` / `displaySmall` / `headlineLarge` 用 `FontFamily.Serif`（宋体），字重 `Normal`/`Medium`。
- 其余 `headline*` / `title*` / `body*` / `label*` 全用 `FontFamily.Default`（黑体）；大标题级用 `SemiBold` 拉开层级。
- **纪律**：宋体只用于大标题四档，不得扩散到列表 / 按钮 / 正文。

### 3. 阅读器纸面 accent — 收编进品牌（选 A）
- 白纸 / 暖纸 accent：由靛青 `#3D5A80` → 品牌墨绿 `#365C4A`（进正文不再跳色）。
- 护眼绿纸 accent：`#2E6B57`（深青绿，与品牌绿同族但压深，保证在浅绿纸 `#E0E9D6` 上选中态清晰；**不可改回 `#365C4A`，否则绿上加绿发糊**）。
- 夜读 accent：保留 `#8AA6D8`（深底蓝调对比好）。
- 取值位置：`ReaderPaperPalette.kt` 各 paper 的 `accent` 字段。

### 4. "加强"范围 — 收敛 + 全面提质（选 C）
- 先做分支 1–3 收敛，再对 5 个 Tab（首页 / 书架 / 灵感 / 统计 / 我的）+ 阅读器做一轮动效 / 组件 / 空状态 / 插画级 overhaul。
- **必须**先跑真机 / 截图走查清单，枚举粗糙点后再动手，不凭感觉改。
- 硬伤必修：深色模式无法点开首页的 bug。

### 5. 多配色框架 — 留单套（选 A）
- `AppPalette` 仅保留 `PAPER_INK("纸墨")`，框架作扩展点；不现在加第二套、不做切换器（与"单一墨绿品牌"一致）。

### 6. 设计文档 — 重建精简决策记录（选 A）
- 即本文件。不堆回被删的 552 行方案文档。

---

## 护栏（不可违反）

1. 全站唯一品牌色：墨绿 `#365C4A`（深底 `#8FAF9D`）。不得引入第二套可切换配色。
2. 护眼纸 accent 必须为 `#2E6B57`，不得改回品牌绿。
3. 宋体仅限大标题四档，不得外溢到 UI 控件 / 正文。
4. 阅读器白 / 暖纸 accent 必须 = 外壳墨绿 `#365C4A`，不得回退靛青。
5. 分支 4 选 C 已是最大范围；不得在"加强"中再开新战场（如新增配色、重写导航骨架）。

---

## 实施流程

1. **走查**：`./gradlew assembleDebug --no-daemon`（先 `--stop` 清锁）→ `adb -s c49ac6cf install -r` → 亮 / 暗双模截图，列粗糙点清单。
2. **收敛**：改 `Theme.kt`（字体 / 色）、`ReaderPaperPalette.kt`（三条 accent）。
3. **提质**：按清单逐页打磨（动效 / 组件 / 空状态 / 插画）。
4. **验证**：编译通过 + 双模真机截验 + 修深色首页 bug。
5. **提交**：Codex 改动与本次加强合并提交，附本决策依据。
