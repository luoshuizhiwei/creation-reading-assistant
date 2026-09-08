# 创作阅读助手 · 主题系统视觉方案（实施稿 · 设计方向已冻结）

> **状态说明**：架构条件**通过**；视觉规范**已通过**；**已实施并通过真机验收**（2026-07-28，小米真机 c49ac6cf，§9.1 截图矩阵与专项核对完成，截图存 `artifacts/screenshots/`）；**设计方向已冻结，仅可微调尺寸 Token**（书签 8×9→6×7、间距等），**不再调整整体设计方向**。
> **代码映射说明（2026-09-08）**：本文 D 节的具体文件/符号是 2026-07 实施快照，不是当前源码清单；
> 后续演进中未接线的 `ThemeSwitchButton`、`BookmarkIndicator` 等已作为死代码删除。视觉原则继续有效，
> 当前实现位置以源码和 `docs/handoff/current.md` 为准，禁止据本文恢复已删除结构。
>
> **范围与基调**：外壳收敛为单套「清屏骨架（冷调低彩度纸白底 / 大留白 / 统一圆角 / 清晰层级）+ 墨笺书签特征（靛青主色 + 唯一书签形选中态）」，仅浅 / 深双模；阅读器独立 `ReaderPaperPalette`（白纸 / 暖纸 / 护眼 / 夜读 4 档）与外壳解耦；Apple 删除、清新并入默认并做兼容映射，旧用户配置不失效。
>
> **外观 vs 纸张（关键解耦）**：应用**外观** 仅 3 态（跟随系统 / 浅色 / 深色）；阅读器**纸张** 独立 5 态（跟随外观 / 白纸 / 暖纸 / 护眼 / 夜读）。两者互不干扰。

---

## 1. 设计主张

- **外壳（应用框架）= 一套设计系统**：删除 Apple（仿 iOS + 最差对比度）、清新合并进默认外观。默认外观 = 清屏结构骨架 + 墨笺唯一品牌特征（书签形选中态）。
- **不采用**：大面积暖米色、全局宋体、宣纸纹理、印章、毛笔线等成套仿古表达。
- **冷调、低彩度纸白**：外壳页面底由暖米改为带极轻冷灰 / 蓝灰倾向的纸白；深态背景去暖，改为冷近黑。外壳点缀色**只用靛青 primary**，不再使用赭石 secondary。
- **阅读器纸张独立且 chrome 跟随 paper**：4 档纸张与外壳浅 / 深正交；进入阅读器后，正文、顶栏、底栏、进度条、系统栏**共同跟随所选 paper 的明暗**，禁止出现「浅色顶栏 + 夜读正文」的明暗断层。
- **字体**：正文 / 按钮 / 表单 / 导航 / 统计数字 / 空状态 / 普通区块标题一律用易读黑体（Sans）；宋体（Serif）**仅用于页面级标题与摘录 / 题记**，不当全局 UI 字体、不用于统计大数。

---

## 2. 单套外壳 Token（浅 + 深）

### 2.1 颜色（冷调低彩度纸白）

| Token | 浅色 Light | 深色 Dark | 说明 / 相对旧版变化 |
|---|---|---|---|
| background（页面底） | `#F2F4F7` | `#121316` | 旧 `#F4F1EA`(暖米)→`#F2F4F7`：**色相由暖黄转冷蓝灰，彩度更低**；深旧 `#15140F`→`#121316`：**去暖，转冷近黑** |
| surface（卡片面） | `#FFFFFF` | `#1B1D22` | 深态比底亮一档，靠明度差分层 |
| surfaceVariant（次级面） | `#EAEDEF` | `#25282E` | 区块内次级面 / 输入底 |
| primary（主色·靛青） | `#3D5A80` | `#8AA6D8` | 墨笺靛青；深态提亮保对比 |
| onPrimary | `#FFFFFF` | `#121316` | |
| onSurface（主文字） | `#1B1E23` | `#E8EAEE` | 冷近黑 / 冷浅 |
| onSurfaceVariant（次文字） | `#5A606A` | `#9AA0AA` | 浅落 surface≈6.33:1、落 background≈5.75:1；深落 surface≈6.41:1，均≥4.5:1 |
| outline（实边 / 图标） | `#C3C8D0` | `#3A3E45` | 冷灰 |
| outlineVariant（发丝线） | `#E2E5EA` | `#2A2D33` | 浅色分离主要靠它；深态冷灰发丝 |
| error | `#C2413B` | `#E57373` | 冷调红 |
| scrim | `#000000` | `#000000` | 弹层遮罩（运行时带 alpha） |
| primaryContainer | `#DCE4EE` | `#26354A` | 选中指示底 / 进度 / 筛选 chip 选中底（**替代原赭石 secondaryContainer**） |
| secondary | — | — | **已移除，不作为外壳 token** |

### 2.2 层级策略（浅深两态信息层级一致，非反相）

| 层级 | 浅色 | 深色 |
|---|---|---|
| 页面底 | `#F2F4F7` 最暗一档冷纸白 | `#121316` 最暗档冷近黑 |
| 卡片 | `surface #FFFFFF` 比底亮 + `outlineVariant` 发丝线 + `Modifier.shadow(2.dp, RoundedCornerShape(14.dp))` | `surface #1B1D22` 比底亮一档明度（深色投影不可见，靠明度台阶 + 发丝线分离） |
| 浮层（Sheet / Dialog / 菜单） | `#FFFFFF` + `Modifier.shadow(6.dp, RoundedCornerShape(20.dp))` | `#1B1D22` 再亮一档 + 发丝线 |
| 导航栏 | 与页面底同色 + 顶部 1dp `outlineVariant` 发丝线 | 同 |
| 系统栏 | 同页面底（沉浸） | 同 |

---

## 3. 组件规则（圆角 / 间距 / 边框 / 阴影）—— 真实 Compose 落地

> 直接给单套 `ComponentSpec` 字段建议值，data class 不新增字段；`useSquircle=false`（删 Apple 方圆形），`glassEnabled/glassBlur=false`（玻璃系统休眠）。**删除抽象的 `ambient` / `cardElevationAmbient` 参数，改为可真实落地的 elevation + BorderStroke 确定值。**

### 3.1 ComponentSpec 字段表（修订）

| 字段 | 值 | 说明 |
|---|---|---|
| cardRadius | `14.dp` | 旧 14/22/12 → 一律 14 |
| sheetRadius | `20.dp` | 弹层大圆角（旧 18/26/20 → 20） |
| pillRadius | `999.dp` | 胶囊全圆 |
| listItemRadius | `10.dp` | 列表项 / 小卡（旧 10/16/10 → 10） |
| useSquircle | `false` | 删 Apple iOS 方圆 |
| pressScale | `0.96f` | 弹性按压 |
| cardContainer | `CardContainer.Lowest` | 白卡浮于冷底（深态 `surfaceContainerLowest=#1B1D22` 亮于底） |
| contentPadding | `18.dp` | 卡片内边距统一 |
| sectionGap | `18.dp` | 区块间距（旧 16/22/18 → 18） |
| listItemGap | `12.dp` | 列表项间距统一 |
| borderWidth | `1.dp` | 统一发丝边（删 Apple 0 边框） |
| borderSubtle | `true` | 用 `outlineVariant` 更淡发丝线 |
| dividerThickness | `1.dp` | 删 Apple 0.5dp，统一 1dp |

### 3.2 组件落地规则（Jetpack Compose 确定值）

- **卡片 Card**
  - `containerColor = surface`
  - `border = BorderStroke(1.dp, outlineVariant)`
  - 阴影：`Modifier.shadow(elevation = 2.dp, shape = RoundedCornerShape(14.dp), ambientColor = Color(0x14000000), spotColor = Color(0x1F000000))`
  - 深色态：`elevation = 0.dp`（阴影不可见），靠 `containerColor = surface` 比 `background` 亮一档 + `BorderStroke` 分离。
- **浮层 Sheet / Dialog**
  - `containerColor = surface`，`border = BorderStroke(1.dp, outlineVariant)`
  - 阴影：`Modifier.shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp), ambientColor = Color(0x1F000000), spotColor = Color(0x29000000))`
  - 深色态：`elevation = 0.dp` + `BorderStroke` 分离。
- **底部导航栏 NavigationBar**
  - `containerColor = background`
  - 顶部发丝线：`Divider(thickness = 1.dp, color = outlineVariant)`
  - `elevation = 0.dp`，**无阴影**（仅靠发丝线）。**保留 5 项 24dp 导航图标，书签旗标仅锚定顶部发丝线、不替代/遮挡图标（见 §6.1）**。
- **列表项 ListItem**
  - `containerColor = Color.Transparent`（卡片变体用 `surfaceVariant`）
  - 分离靠 `Divider(thickness = 1.dp, color = outlineVariant)` 或 `BorderStroke(1.dp, outlineVariant)`
  - `elevation = 0.dp`、无阴影（扁平，仅色调台阶 / 发丝线分层）。

### 3.3 阴影哲学（去「ambient」抽象词）

页面底 = 0；卡片 = `surface + 1dp 发丝线 + shadow(2.dp, 14.dp 圆角)`；浮层 = `shadow(6.dp, 20.dp 圆角) + 1dp 发丝线`；底栏 = 0（仅靠顶部发丝线）；列表项 = 0（仅靠发丝线 / 色调台阶）。深色态阴影不可见，分离改由明度台阶 + `outlineVariant` 发丝线承担。全文档不再使用「ambient」一词。

---

## 4. ReaderPaperPalette（独立，4 档，chrome 跟随 paper）

> 与外壳浅 / 深**解耦**：无论外壳浅或深，正文区按用户选的 paper 档渲染。进入阅读器后，正文、顶栏、底栏、进度条、弹层、系统栏**共同跟随所选 paper 的 `light`/`dark` 属性**（亮纸则整屏亮、夜读则整屏暗），**禁止出现「浅色顶栏 + 夜读正文」的明暗断层**。纸张与外壳浅深正交（浅外壳下可选手动夜读、深外壳下可选手动白纸），但一旦选定某 paper，阅读器内所有元素（含系统栏）统一跟随该 paper 明暗。

### 4.1 四档纸张（含 light/dark 属性与 chrome 色）

| 档（内部 key / 显示名） | 明暗 | 背景 bg | 正文 fg | 强调色 accent | outlineVariant / outline | 批注底纹（随纸 5 色，用于高亮底 @0.18） | 对比 |
|---|---|---|---|---|---|---|---|
| `white` / 白纸（默认） | light | `#FAFAFB` | `#1B1E23` | `#3D5A80` | `#E4E6EA` / `#C7CBD2` | 黄`#E6C95A` 红`#D08B7A` 绿`#7FA86B` 蓝`#6E8FC0` 紫`#A884B0` | fg/bg≈16:1 |
| `warm` / 暖纸 | light | `#F3ECDC` | `#2B231A` | `#3D5A80` | `#E6D8C4` / `#CDBBA0` | 黄`#C9A24B` 红`#B5705A` 绿`#7C8A5A` 蓝`#6E84A8` 紫`#9A7C92` | fg/bg≈11:1 |
| `green` / 护眼 | light | `#E8F0DF` | `#1F291A` | `#3F6B4F` | `#D8E4CC` / `#BCD0AC` | 黄`#C7B65A` 红`#B5705A` 绿`#6E8A55` 蓝`#6E84A8` 紫`#9A7C92` | fg/bg≈10:1 |
| `night` / 夜读 | dark | `#15171C` | `#DEE2E9` | `#8AA6D8` | `#24262C` / `#383B42` | 黄`#E6C95A` 红`#D08B7A` 绿`#8FA86B` 蓝`#8EA3D0` 紫`#C0A0C8` | fg/bg≈12:1 |

- **chrome 颜色规则（随 paper）**：顶栏 / 底栏容器色 = paper `bg`；顶栏文字图标 = paper `fg`（次级用纸面降饱和灰：浅纸 `#5C606A`、夜读 `#9AA0AA`）；进度条 = paper `accent`；系统状态栏背景 = paper `bg`、图标随 paper 明暗。
- accent 用途：正文内链接、批注引线标记色、选中句高亮底（按 §4.3 alpha 叠加，替代现行 `primary.copy(0.22f)`）。

### 4.2 chrome 跟随 paper 明暗（关键修正说明）

- 旧文档 §4 / §8 写「阅读器 chrome 随外壳浅深」——**此为错误，已修订**：chrome 不再引用外壳 `surface`，而由 paper 的 `light`/`dark` 决定。
- 正交但不撕裂：用户可在浅外壳下选夜读纸（整屏暗）、深外壳下选白纸（整屏亮）；选定后阅读器内**全部元素（顶栏、底栏、进度条、弹层、系统栏）统一跟随该 paper 明暗**，不存在外壳 / chrome 撕裂。
- 标注 / 划线随 paper 5 色 + §4.3 / §4.5（高亮底 5 色 @0.18；下划线 / 引线 5 色暗化描边，纯底色高亮与线条均不叠加书签形）。

### 4.3 批注 alpha 与对比度（两套标准，计算见附录 C）★ 第 5 点已推翻「统一 primary」

> **两套独立标准（禁止混用）**：
> - **(a) 高亮底**：验证「正文 fg vs 合成高亮底」的**文本对比度 ≥ 4.5:1（AA）**。合成底 = 随纸 5 色批注色 @ α 与 paperBg 直线混合（α=0.18）。
> - **(b) 下划线 / 批注引线**：验证「线条颜色 vs 纸张背景」的**非文本对比度 ≥ 3:1（WCAG 1.4.11）**。线条颜色 = **`annotationStrokeColors[paper][color]`**（每档纸张、每档批注色的「暗化描边色」，保留 5 色语义，见 §4.5）。
>
> **★ 关键：下划线 / 引线保留 5 色语义，不得为达标统一成单一 primary。** 上一轮曾为达标把线条统一成纸张主色 accent，本轮**推翻**该妥协——线条色按 (paper, 批注色) 计算一个足够暗的描边色，使线条 vs 纸面非文本对比度 ≥3:1，同时保留黄/红/绿/蓝/紫五套可辨识色相。浅纸面上 5 色批注色物理上明度过高（@1.0 仅 1.2–1.6:1），故线条**不拿原批注色直接画**，而用该纸面下各色的「暗化版」（向黑插值）；夜读档原批注色已高亮、对比充足则直接沿用。

| 批注类型 | 颜色来源 | 标准 | 说明 |
|---|---|---|---|
| 高亮底（文本背景高亮） | 随纸 5 色批注色 @ `0.18` | 正文对比度 ≥ 4.5:1 | 合成底浅于正文，保证可读；5 色身份保留 |
| 下划线（句下划线） | `annotationStrokeColors[paper][color]`（暗化描边·实色） | 非文本对比度 ≥ 3:1 | WCAG 1.4.11；保留 5 色语义 |
| 批注引线（引线 / 标记线） | `annotationStrokeColors[paper][color]`（暗化描边·实色） | 非文本对比度 ≥ 3:1 | 同上；与下划线共用同一套描边色 |

合成公式（sRGB 直线混合）：`C_final = α · C_src + (1−α) · C_paperBg`（逐通道，仅高亮底用 α；线条为实色描边，不叠加 alpha）。两套标准分别计算、分别列表，结论：**高亮底 4 档 × 5 色共 20 组全部 ≥ 4.5:1（AA）；下划线 / 引线 4 档 × 5 色共 20 组描边色全部 ≥ 3:1（非文本）**。脚本（`_contrast_calc.py`）与结果表见附录 C，最终落地表见 §4.5。

### 4.4 第 5 态 · 跟随外观（默认映射，不撕裂）

除上表 4 档外，阅读器提供「**跟随应用外观**」选项——浅色外壳默认映射到白纸（light）、深色外壳默认映射到夜读（dark）（仅默认）。用户手动选中白 / 暖 / 护 / 夜任一后**独立持久化**，外壳明暗切换不重置该选择；手动选纸后整屏（含 chrome）跟随该纸，不存在外壳 / chrome 撕裂。

### 4.5 annotationStrokeColors 落地表（4 档 × 5 色，下划线 / 引线统一使用）

> ⚠️ **实现状态（2026-07-28 收口更正）：本表为设计冻结值，代码尚未实现。**
> 当前标注模型（`HighlightEntity`）仅有 `color` 字段、无标注类型字段（高亮 / 下划线 / 引线），阅读器现阶段**只支持高亮底色**一种标注形态。五色下划线 / 引线不可达，此前「已实现」的验收口径撤回；`ReaderPaperPalette.kt` 中对应的未消费 Token 已删除（不留看似已实现、实际不可达的代码）。本表保留作为未来落地依据：待标注模型新增类型字段后，按本表接入渲染路径。

> **实色描边，不叠加 alpha**。每档纸张 × 每档批注色一组描边色；蓝色 / 紫色及多数暖纸 / 绿纸批注色在原纸面已 ≥3:1，直接沿用原批注色（语义零偏差）；仅明度过高的黄色等（及浅纸黄）做暗化处理。夜读档全部沿用原批注色（深底上已高亮）。全部经 `_contrast_calc.py` 求解并验证 ≥3.1（安全余量，稳过 3:1）。

| 纸张（bg） | 黄 | 红 | 绿 | 蓝 | 紫 |
|---|---|---|---|---|---|
| white `#FAFAFB` | `#A38E40` | `#BF7F70` | `#749962` | `#6E8FC0` | `#A783AF` |
| warm `#F3ECDC` | `#A1823C` | `#B5705A` | `#7C8A5A` | `#6E84A8` | `#9A7C92` |
| green `#E8F0DF` | `#948743` | `#B5705A` | `#6E8A55` | `#6E84A8` | `#9A7C92` |
| night `#15171C` | `#E6C95A` | `#D08B7A` | `#8FA86B` | `#8EA3D0` | `#C0A0C8` |

> 说明：单元格为「描边色」；其中 white·蓝 `#6E8FC0`、warm·红/绿/蓝/紫、green·红/绿/蓝/紫、night 全 5 色均为**沿用原批注色**（原色已达标），仅 white·黄/红/绿/紫、warm·黄、green·黄 为**暗化版**（原色在浅纸面 <3:1）。详见附录 C。

---

## 5. 字体角色与字号（宋体收窄）

> 复用墨韵 `AppTypography`（CJK 字距归零 + 行高放宽），作为单套外壳字阶。**宋体（Serif）仅用于页面级标题 + 摘录 / 题记**；统计大数、空状态、普通区块标题、正文、按钮、表单、导航、caption 一律 Sans。

### 5.1 字体表（修订后）

| 角色 | 字族 | 字号(sp) | 字重 | 行高 | 备注 |
|---|---|---|---|---|---|
| 大标题（统计大数 / 空状态标题） | **Sans** | 44 / 28 | 600 | 1.20 / 1.25 | 旧标 Serif → **改为 Sans**（收窄） |
| 页标题（页面级标题） | **Serif** | 26 / 22 | 500 | 1.30 / 1.35 | 仅此保留 Serif（宋标） |
| 区块标题 | **Sans** | 19 | 600 | 1.40 | 旧标 Serif → **改为 Sans**（收窄） |
| 摘录 / 题记 | **Serif** | 18–20 | 400 | 1.50 | 仅摘录 / 题记保留 Serif |
| 正文 | Sans | 16 / 14 / 13 | 400 | 1.75 / 1.70 / 1.65 | 阅读 / 说明 |
| 按钮 | Sans | 14 | 500 | 1.45 | 字距 0.1 |
| 表单 / 输入 | Sans | 16 / 12–14 | 500 | 1.45 / 1.40 | |
| 导航 | Sans | 12 | 500 | 1.40 | 底部导航 / 标签 |
| caption | Sans | 11 | 500 | 1.40 | 辅助说明 |

### 5.2 字体落地与 APK 体积影响

- **实际字族**：Android `FontFamily.Serif`（页标题、摘录）/ `FontFamily.Default`（其余全部 Sans）。中文 Sans 走系统默认黑体回退链；中文 Serif 走系统 `serif` 回退链。
- **APK 体积结论**：**仅使用系统字族、不打包任何 TTF → APK 增量 = 0 MB**。**不打包 TTF，使用系统衬线**。

---

## 6. 书签形选中态（唯一品牌特征）

- **几何（总体）**：顶部平、底部带 V 形缺口的小旗标（页签剪影），填充 `primary`（靛青）；**内衬不再使用赭石 secondary（已移除），纯靛青填充**。
- **出现位置（唯一锚点，极度克制）**：**仅底部导航 5 项 active 指示**。
- **明确不出现**：书架封面、筛选 chip、列表 / 设置行、卡片、普通按钮、阅读器底栏——一律不叠加书签造型；选中态用字重 / 主色 / 发丝线表达，不用书签形。
- **品牌主张**：书签形是全 App 唯一的中式基因锚点；看到书签即知「被选中 / 正在读」。当前项的图标与文字**仍使用 primary 色（靛青）**，满足「不依赖形状即可识别选中态」——形状只是点缀，颜色才是主识别。

### 6.1 BookmarkIndicator 规格（第 4 点 · 重定位 + 避让，确定几何）

> **重定位**：书签**不再悬浮于导航图标上方**（旧方案探出 2–3dp），改为**锚定在底部导航栏顶部的发丝线（顶部分隔 Divider）上，向底栏内部垂下**。它**不悬浮、不探出底栏上沿**，与顶 Divider 重合后向下伸入底栏。
>
> **避让（第 4 点硬约束）**：书签**不得替代或遮挡**原来的 **24dp 导航图标**。书签锚定底栏顶部分隔发丝线（Divider），与图标**至少间隔 4dp**（书签底沿到图标顶沿 ≥4dp）。书签水平居中于当前 active tab 的图标中心，但**不参与、不覆盖**图标本体。

| 几何属性 | 确定值 |
|---|---|
| 位置 | 顶部边与底栏顶 `Divider(thickness = 1.dp, color = outlineVariant)` **完全重合**；向下伸入底栏内部，**不探出底栏上沿**；与 24dp 导航图标**垂直间隔 ≥4dp**（书签底沿到图标顶沿） |
| 宽度 | `8.dp`（先实现；水平居中于当前 active tab 图标中心，**不覆盖图标**） |
| 高度 | `9.dp`（自顶 Divider 起向下 9dp；先实现） |
| 底部造型 | **V 形缺口**（旗标 / 书签剪影）；路径（以左上角为原点，单位 dp）：`M0,0 H8 V9 L4,5 L0,9 Z` |
| 填充 | `primary`（浅态 `#3D5A80` / 深态 `#8AA6D8`） |
| 层级 | 绘制于底栏容器之上、导航项之下（不超出底栏上沿） |
| 可见性兜底 | 当前 active 项图标 + 文字已用 primary 色，书签仅为形状点缀；即使形状被遮挡仍可凭颜色识别选中态 |

- **回退尺寸（第 4 点）**：先按 **8×9dp** 实现；**真机显重则缩至 6×7dp**。工程师实现 8×9 并**预留 6×7 常量 / 开关**（如 `BOOKMARK_W/H = 8.dp/9.dp`，回退 `BOOKMARK_W_SMALL/H_SMALL = 6.dp/7.dp`，V 缺口路径对应 `M0,0 H6 V7 L3,4 L0,7 Z`）。此为**可接受回退尺寸**，仅在真机验收阶段按需切换，不改整体设计方向。
- **落地点**：仅 `BottomNavBar` 的 active 项；删 `AppNavigation.kt` 原 Apple 特判与「图标上方探出」逻辑，改为 `BookmarkIndicator` 锚定顶 Divider 向内、避让 24dp 图标的实现。
- **尺寸换算**（3x / xxhdpi）：8×9dp ≈ 24×27px（先实现）；6×7dp ≈ 18×21px（回退）。

---

## 7. 迁移映射（关键：不让旧配置失效）【架构 · 已通过，仅改 ThemeSwitchButton 入口】

- `resolveThemeParams()` / `componentSpecForStyle()`：`APPLE`/`WEB` 分支统一回退 DEFAULT（一行改动即可消除 Apple / 清新视觉）。`VisualStyleProvider` 的 `LocalGlassPalette` 恒 `null`。
- 删除 `AppNavigation.kt` 与 `OnboardingOverlay.kt` 中 `style == VisualStyle.APPLE` 两处特判，导航栏统一 `NavigationBarItemDefaults.colors()` + M3 填充图标 + 书签 active 指示。
- **持久化前向安全**（防未来落盘）：`VisualStyle.fromStored(name)` 对 `APPLE`/`WEB`/null/未知一律回退 DEFAULT。
- **阅读器纸张 7→4 档**（扩展 `SettingsStore.migrateReaderBg`）：`white→白纸`；`warm`/`warm-yellow→暖纸`；`green`/`green-bean→护眼`；`night`/`oled-black→夜读`（夜读 bg 取 `#15171C` 冷深灰护眼）。
- **ThemeSwitchButton（第 1 点 · 仅 3 态）**：删 `APPLE`/`WEB` 分支，**仅留 3 态：跟随系统 / 浅色 / 深色**（色点统一 `#3D5A80`）；仅控制应用**外观**（浅 / 深 / 跟随系统），**不含任何纸张入口**；纸张选择是阅读器独立设置项（放阅读器设置页或阅读器内独立弹层），与外观开关**解耦**。统一用 M3 填充图标，弃 iOS 线性图标特判。**无「默认」第 4 态**。

---

## 8. 四页面视觉方案（同内容、同尺寸；外壳切换只换主色 / 纸张明度，结构不变）

- **首页（第 1 点 · 减卡片化；第 3 点 · 仅继续阅读用卡片）**：
  - **「继续阅读」是唯一主卡片**：沿用 §3 卡片容器规则（`surface` + `BorderStroke(1.dp, outlineVariant)` + `shadow(2.dp, 14.dp)`），其余区块均不套卡片。
  - **两个统计数字（阅读时长 / 藏书数）合并为一个无边框区域**：两项之间用 **1dp `outlineVariant` 竖向细线（hairline divider）** 分隔；**无卡片、无边框、无阴影**，只消费颜色 / 排版 / 间距 Token。
  - **「今日灵感」改为留白区 + 短墨线装饰**：一条短的水平墨色细线 / 墨笔触作为分隔或点缀，**不再使用卡片**，只消费颜色 / 排版 / 间距 Token。
  - **删除首页右上角「默认」胶囊**：外观设置入口已越界到首页，统一只在个人页（§7 `ThemeSwitchButton`）。
  - 底栏 active 项书签旗标（锚定顶 Divider 向内、避让 24dp 图标，见 §6.1）。统计大数走 **Sans**（§5 收窄）。
- **书架（第 2 点）**：
  - **封面**：使用真实封面或**低饱和多色占位封面**（灰绿 / 陶土 / 石板蓝 / 藕灰 / 暖灰等互异的低彩度色，每本书不同），**禁止全部蓝色矩形**。
  - **标题不重复**：封面内只放图形 / 渐变 / 极简书名；封面**下方只放「书名 + 作者」一行**。
  - **筛选**：改为**单行横向滚动**（`HorizontalScroll` + `Row`，chip 不换行）；或把「收藏」单独分组。筛选 `SelectablePill`（选中 = `primaryContainer` 淡靛底 + 主色字，不用赭石、不用书签角标）。
  - 书卡网格（`SectionCard` 变体，封面圆角 `listItemRadius10`）。选中态仅由底栏书签旗标表达。
- **阅读器（第 3 点）**：
  - **顶栏仅三元素**：返回按钮 + 章节标题 + 一个阅读设置图标（齿轮 / 排版图标）。**移除章节标题旁的纸张状态圆点**。
  - **「跟随应用外观」「纸张颜色」「字体」全部移入底部设置面板**：从底栏上滑的 Sheet / 弹层承载，顶栏不放这些开关。
  - 正文区强制走 `ReaderPaperPalette`（4 档 + 「跟随外观」第 5 态，满版纸无卡片圆角）；**顶栏 / 底栏 / 进度条 / 弹层 / 系统栏共同跟随所选 paper 明暗**（亮纸整屏亮、夜读整屏暗，无明暗断层）。标注 / 划线：高亮底随 paper 5 色 @0.18（正文对比度 ≥4.5），下划线 / 引线用 `annotationStrokeColors` 暗化描边（非文本 ≥3，见 §4.3 / §4.5）。外壳浅 + 纸张夜读 → 整屏暗；外壳深 + 纸张白纸 → 整屏亮；选定后阅读器内无外壳 / chrome 撕裂。
- **个人页**：设置组 = `SectionCard`；开关行 = `SettingRow`（选中态用字重 / 主色表达，不用书签形）；标签 = `SelectablePill`（含外观设置入口，见 §7）；圆角间距同首页规范。书签旗标仅底栏「我的」项。**外观设置入口仅在此页（个人页）的 `ThemeSwitchButton` / `SelectablePill`，选项 = 跟随系统 / 浅色 / 深色（无「默认」第 4 态）**。

---

## 9. 下一步（实施清单见附录 D）【已实施 · 已通过真机验收（2026-07-28，c49ac6cf）】

> 验收记录：§9.1 截图矩阵已在小米真机完成（截图存 `artifacts/screenshots/`，共 20 张，均只显示中性名称，不出现任何具体书名）：EPUB 书籍显示「测试 EPUB」；TXT 书籍在应用数据中的标题为非中性具体书名，其书名区域已在两张书架截图中遮盖，故持久截图不出现该具体书名。矩阵覆盖：浅/深外壳 × 首页/书架/个人页 + 阅读器 白纸/暖纸/护眼/夜读 + 「跟随系统」外观双向切换 + 「跟随外观」纸张映射（浅→白纸、深→夜读）。验收与本轮收口修复：①ThemeSheet/ProfileScreen 纸张选项残留旧 7 色 key 且缺「跟随外观」入口，已收敛为「跟随外观/白纸/暖纸/护眼/夜读」5 态；②高亮底 5 色 × 4 纸对比度脚本复核全部 ≥9.1:1（AA 通过）；③夜读退出阅读器后系统栏正确恢复外壳明暗；④首页「最近灵感」空态改为无容器布局（留白 + 24dp 短墨线 + 提示文字），去除虚线框/卡片/边框/阴影；⑤对比度脚本 `_contrast_calc.py` 改为纯 ASCII 结果标识（`[PASS]`/`[FAIL]`）并在 GBK 控制台稳定退出 0，最小正文对比度 9.18:1，全部高于 4.5:1（下划线/引线仍为设计计算，未接入代码）。

1. 改 `resolveThemeParams` + `componentSpecForStyle` 让 APPLE/WEB 回退 DEFAULT（零风险）。
2. 物理删除 / 停引 `AppleTheme.kt` / `WebTheme.kt`。
3. 扩展 `migrateReaderBg` 完成 7→4；新增 `ReaderPaperPalette`（含 `light`/`dark` 属性与 chrome 色）替换 `ReaderScreen.paperColors()` 与 `highlightColor()`；**阅读器 chrome 改读 paper 明暗而非外壳 surface**。⚠️ §4.5 `annotationStrokeColors` **未落地**：标注模型无类型字段，现阶段仅支持高亮底色（5 色 @0.18），下划线 / 引线待模型支持后再接入（见 §4.5 实现状态注记）。
4. 新增 `BookmarkIndicator` **仅接入底部导航当前选中项**：锚定底栏顶 `Divider(1.dp, outlineVariant)`、向底栏内部垂下 8×9dp（回退 6×7dp）、底部 V 缺口（先实现路径 `M0,0 H8 V9 L4,5 L0,9 Z`，回退 `M0,0 H6 V7 L3,4 L0,7 Z`）、填充 `primary`，不悬浮、不探出底栏上沿；**保留 24dp 导航图标、与图标垂直间隔 ≥4dp、不替代/遮挡**；当前项图标 + 文字仍用 primary（形状仅为点缀）；封面 / 列表 / 筛选 chip / 卡片 / 按钮 / 阅读器底栏一律不叠加书签造型。
5. 首页（第 3 点）：**只有「继续阅读」使用 `SectionCard`**（全首页唯一卡片）；**统计区与「今日灵感」区不得重新卡片化**——不套 `SectionCard`、不套任何 Card/边框/阴影容器，只能消费颜色 / 排版 / 间距 Token（统计 = 无边框 + 1dp `outlineVariant` 竖向分隔；灵感 = 留白 + 短墨线，均非卡片）。
6. 全量回归：浅 / 深 × 四页面 × 四纸张，确认外壳切换只换主色 / 纸张明度、结构不变，且阅读器内无明暗断层。

### 9.1 验收与微调（第 6 点 · 实施节奏）

> 代码实施后**先构建 APK，再真机验收**；根据真机效果**仅微调尺寸 Token**（如书签 8×9→6×7），**不再调整整体设计方向**。

- **构建**：`./gradlew :app:assembleDebug`（或 `assembleRelease`）。
- **真机安装（小米设备）**：优先 `adb install -r -t app-debug.apk` 覆盖安装（USB 安装开关已开启时可直装，保留数据）；仅当系统阻止 USB 安装或签名不一致时暂停并报告，不得卸载旧应用或清除数据。
- **截图核对清单**（浅 / 深 × 四页面 × 四纸张）：
  - 四页面：首页 / 书架 / 阅读器 / 个人页；
  - 两外观：浅色 / 深色（含「跟随系统」在真机明暗下各验一次）；
  - 四纸张（阅读器内）：白纸 / 暖纸 / 护眼 / 夜读（含「跟随外观」映射一次）。
  - 重点核对：①底栏书签不遮挡 24dp 图标、间隔 ≥4dp；②统计区/灵感区非卡片（首页只有「继续阅读」一张卡）；③阅读器 chrome 整屏跟随 paper 无断层；④高亮底 5 色文字可读（正文对比度 ≥4.5:1）。~~批注 5 色下划线/引线~~ **不在本轮验收范围**——标注模型无类型字段、未实现（见 §4.5 实现状态注记），撤回原「可辨识且达 ≥3:1」验收项。
- **微调范围**：仅限尺寸 Token（书签 8×9↔6×7、间距、圆角），颜色 / 字号 / 圆角档位 / 书签几何形态 / 描边暗化色均已冻结，不可改动。

---

# 附录 A：本轮 6 条硬约束变更清单（逐条对应用户「进入代码实施」前的最终修订）

> 说明：前序「7 点修订」「本轮 5 点」已**有条件通过**并保留，本附录列**进入实施前追加的 6 条硬约束**；落实后设计方向冻结，仅可真机微调尺寸。

| 约束 | 改了什么 | 关键新值 |
|---|---|---|
| **约束 1 · 应用外观只留 3 态** | `ThemeSwitchButton` 删「默认」第 4 态，仅留 跟随系统 / 浅色 / 深色；删 APPLE/WEB 分支 | 外观 = {跟随系统, 浅色, 深色}，无「默认」 |
| **约束 2 · 阅读器纸张保留 5 态** | 纸张独立保留 5 态（跟随外观 / 白纸 / 暖纸 / 护眼 / 夜读），与外观 3 态解耦 | 外观 3 态 × 纸张 5 态，互不干扰 |
| **约束 3 · 首页仅「继续阅读」用 SectionCard** | §9 第 5 步修正：仅「继续阅读」用 `SectionCard`；统计区 / 今日灵感区**不得卡片化**，只消费颜色/排版/间距 Token | 统计=无边框+1dp 竖向分隔；灵感=留白+短墨线；均非卡片 |
| **约束 4 · BookmarkIndicator 几何与避让** | 书签不替代/遮挡 24dp 图标、≥4dp 间隔；先 8×9，回退 6×7；V 缺口路径两档 | 锚定顶 Divider、`M0,0 H8 V9 L4,5 L0,9 Z`（回退 `…H6 V7 L3,4 L0,7 Z`）；填充 primary |
| **约束 5 · 批注下划线/引线保留 5 色语义（推翻统一 primary）** | 下划线/引线改用 `annotationStrokeColors`（4 档×5 色暗化描边），非文本 ≥3:1；高亮底 5 色 @0.18 ≥4.5 保留 | 暗化描边表见 §4.5；附录 C 两表全过。⚠️ **实现状态**：仅高亮底已落地；下划线/引线因标注模型无类型字段**未实现**（见 §4.5 注记） |
| **约束 6 · 实施与验收节奏** | §9 末尾补「验收与微调」：先构建 APK → 真机浅/深 × 四页截图验收 → 仅微调尺寸 Token | 构建命令 / `adb push` 小米手动装 / 截图清单（浅深×四页×四纸） |

---

# 附录 B：8 张视觉稿令牌表（供主理人渲染 · 精确值）

> 通用换算：以 3x（xxhdpi）密度近似，**1dp ≈ 3px**。圆角：卡片 14dp≈42px、弹层 20dp≈60px、列表项/小卡 10dp≈30px、胶囊 999dp。间距：区块 18dp≈54px、列表项 12dp≈36px、卡片内边距 18dp≈54px。**书签旗标（BookmarkIndicator）：先实现 宽 8dp≈24px、高 9dp≈27px，锚定底部导航顶发丝线、向底栏内部垂下、底部 V 缺口（路径 `M0,0 H8 V9 L4,5 L0,9 Z`）、填充 primary 靛青；真机显重回退 宽 6dp≈18px、高 7dp≈21px（路径 `M0,0 H6 V7 L3,4 L0,7 Z`）；不悬浮、不探出底栏上沿；与 24dp 导航图标垂直间隔 ≥4dp（书签底沿到图标顶沿），不替代/遮挡图标；当前项图标文字仍 primary。** 底部导航 5 项：**首页 / 书架 / 灵感 / 书签 / 我的**（active 项书签旗标锚定顶发丝线向内垂下，其余项无）。

### B1 · 首页（浅 / Light）

| 维度 | 值 |
|---|---|
| 页面底 background | `#F2F4F7` |
| 卡片面 surface（仅「继续阅读」主卡） | `#FFFFFF`；容器 = `surface + BorderStroke(1.dp, outlineVariant) + shadow(2.dp, 14.dp)`，其余区块无卡片 |
| 主文字 onSurface | `#1B1E23` |
| 次文字 onSurfaceVariant | `#5A606A` |
| 主色 primary | `#3D5A80` |
| 边框 / 发丝线 outlineVariant | `#E2E5EA`（实边 outline `#C3C8D0`）；统计区竖向分隔细线 = 1dp `outlineVariant`；灵感区短墨线 = 1dp `onSurface` 水平细线 |
| 圆角 | 卡片 14dp / 列表项 10dp / 胶囊 999dp |
| 关键间距 | 区块 18dp / 列表项 12dp / 卡片内边距 18dp |
| 文本字族·字号 | 页标题 Serif 26sp(500)；统计大数 Sans 44sp(600)；区块标题/正文/按钮/导航 Sans（见 §5.1） |
| 统计区（无边框） | 阅读时长 / 藏书数两项合并为一个无边框区域，两项之间 1dp `outlineVariant` 竖向 hairline 分隔；无卡片、无阴影 |
| 今日灵感区 | 留白 + 一条短的水平墨色细线（≈1dp `onSurface`，长度约 40–56dp）作点缀，**非卡片** |
| **首页右上角** | **无「默认」胶囊**（外观设置入口已移除，统一只在个人页） |
| 底部导航 | 5 项；active「首页」书签旗标锚定底栏顶发丝线、向内垂下 8×9dp（回退 6×7dp）、与 24dp 图标间隔 ≥4dp、V 缺口、填充 primary（图标文字仍 primary） |

### B2 · 首页（深 / Dark）

| 维度 | 值 |
|---|---|
| 页面底 background | `#121316` |
| 卡片面 surface（仅「继续阅读」主卡） | `#1B1D22`；容器 = `surface + BorderStroke(1.dp, outlineVariant) + shadow(0.dp)`（深态阴影不可见），其余区块无卡片 |
| 主文字 onSurface | `#E8EAEE` |
| 次文字 onSurfaceVariant | `#9AA0AA` |
| 主色 primary | `#8AA6D8` |
| 边框 / 发丝线 outlineVariant | `#2A2D33`（实边 outline `#3A3E45`）；统计区竖向分隔细线 = 1dp `outlineVariant`；灵感区短墨线 = 1dp `onSurface` 水平细线 |
| 圆角 / 间距 / 字族 | 同 B1 |
| 统计区（无边框） | 同 B1：两项合并无边框、1dp `outlineVariant` 竖向分隔；无卡片无阴影 |
| 今日灵感区 | 同 B1：留白 + 短水平墨色细线点缀，非卡片 |
| **首页右上角** | **无「默认」胶囊**（同 B1） |
| 底部导航 | 5 项；active「首页」书签旗标（靛青 `#8AA6D8`）锚定顶发丝线、向内垂下 8×9dp（回退 6×7dp）、与 24dp 图标间隔 ≥4dp、V 缺口（图标文字仍 primary） |

### B3 · 书架（浅 / Light）

| 维度 | 值 |
|---|---|
| 页面底 / 卡片面 / 文字 / 主色 / 边框 | 同 B1（`#F2F4F7` / `#FFFFFF` / `#1B1E23` `#5A606A` / `#3D5A80` / `#E2E5EA`） |
| 封面 | **低饱和多色占位封面**（每本书互异）：灰绿 `#9CAEA0` / 陶土 `#C08466` / 石板蓝 `#7E93AE` / 藕灰 `#A89AA2` / 暖灰 `#B7A98F` 等低彩度色；或用真实封面；**禁止全部蓝色矩形**；封面内不放书名文字 |
| 封面下方 | 仅「书名 + 作者」一行（书名 Sans 16sp(400) / 作者 caption Sans 11sp），**不与封面内文字重复** |
| 封面圆角 | listItemRadius 10dp |
| 筛选 chip | **单行横向滚动**（`HorizontalScroll` + `Row`，不换行）；选中 = `primaryContainer #DCE4EE` 淡靛底 + 主色字（**非赭石**），无书签角标；或「收藏」单独分组 |
| 文本字族·字号 | 区块标题 Sans 19sp(600)；书名 Sans 16sp(400)；caption Sans 11sp |
| 底部导航 | 5 项；active「书架」书签旗标锚定顶发丝线、向内垂下 8×9dp（回退 6×7dp）、与 24dp 图标间隔 ≥4dp、V 缺口、填充 primary（图标文字仍 primary） |

### B4 · 书架（深 / Dark）

| 维度 | 值 |
|---|---|
| 页面底 / 卡片面 / 文字 / 主色 / 边框 | 同 B2（`#121316` / `#1B1D22` / `#E8EAEE` `#9AA0AA` / `#8AA6D8` / `#2A2D33`） |
| 封面 | 同 B3 低饱和多色占位封面（深态下取同色系略降明度版本）；封面内不放文字 |
| 封面下方 | 同 B3：仅「书名 + 作者」一行，不与封面内重复 |
| 封面圆角 | listItemRadius 10dp |
| 筛选 chip | 同 B3 单行横滚；选中 = `primaryContainer #26354A` 淡靛底 + 主色字 |
| 底部导航 | 5 项；active「书架」书签旗标（靛青 `#8AA6D8`）锚定顶发丝线、向内垂下 8×9dp（回退 6×7dp）、与 24dp 图标间隔 ≥4dp、V 缺口（图标文字仍 primary） |

### B5 · 阅读器（浅 · 白纸 / 整屏亮）★

| 维度 | 值 |
|---|---|
| 页面底 = paper bg | `#FAFAFB` |
| 正文 fg | `#1B1E23` |
| 次文字（纸面降饱和灰） | `#5C606A` |
| 主色 accent | `#3D5A80` |
| 边框 outlineVariant | `#E4E6EA` |
| **顶栏（仅三元素）** | ① 返回按钮 ② 章节标题（**无纸张状态圆点**）③ 一个阅读设置图标；不含「跟随外观 / 纸张 / 字体」开关 |
| **底部设置面板（Sheet）** | 从底栏上滑的弹层承载：纸张颜色（4 档 + 跟随外观）、字体、跟随应用外观；顶栏不放这些 |
| 顶栏 / 底栏 / 进度条 / 系统栏 | **均跟随 paper 明暗：容器 = `#FAFAFB`、文字 = `#1B1E23`、进度 = `#3D5A80`、系统栏浅图标** |
| 圆角 / 间距 | 同外壳（卡片 14dp / 列表项 10dp；区块 18dp / 列表项 12dp） |
| 文本字族·字号 | 正文 Sans 16sp(400) 行高 1.75；章标题 Sans 19sp(600)；摘录若有 Serif 18–20sp(400) |
| 批注 | 高亮底 = 随纸 5 色 @0.18（正文对比度 ≥4.5）；下划线 / 引线 = `annotationStrokeColors` 暗化描边（非文本 ≥3，见 §4.3 / §4.5） |
| ★ 特别标注 | **顶栏 / 底栏 / 进度条 / 系统栏均跟随 paper 明暗，整屏亮，无明暗断层**；书签旗标不出现在阅读器底栏 |

### B6 · 阅读器（深 · 夜读 / 整屏暗）★

| 维度 | 值 |
|---|---|
| 页面底 = paper bg | `#15171C` |
| 正文 fg | `#DEE2E9` |
| 次文字 | `#9AA0AA` |
| 主色 accent | `#8AA6D8` |
| 边框 outlineVariant | `#24262C` |
| **顶栏（仅三元素）** | ① 返回按钮 ② 章节标题（**无纸张状态圆点**）③ 一个阅读设置图标；不含「跟随外观 / 纸张 / 字体」开关 |
| **底部设置面板（Sheet）** | 同 B5：纸张颜色 / 字体 / 跟随应用外观均在此弹层，顶栏不放 |
| 顶栏 / 底栏 / 进度条 / 系统栏 | **均跟随 paper 明暗：容器 = `#15171C`、文字 = `#DEE2E9`、进度 = `#8AA6D8`、系统栏深底浅图标** |
| 圆角 / 间距 / 字族 | 同 B5（白纸）规范 |
| 批注 | 同 B5（高亮底 5 色 @0.18；下划线 / 引线 `annotationStrokeColors` 暗化描边 ≥3） |
| ★ 特别标注 | **顶栏 / 底栏 / 进度条 / 系统栏均跟随 paper 明暗，整屏暗，无明暗断层**；即便外壳为浅，选夜读纸则整屏暗 |

### B7 · 个人页（浅 / Light）

| 维度 | 值 |
|---|---|
| 页面底 / 卡片面 / 文字 / 主色 / 边框 | 同 B1（`#F2F4F7` / `#FFFFFF` / `#1B1E23` `#5A606A` / `#3D5A80` / `#E2E5EA`） |
| 设置组 / 开关行 / 标签 | `SectionCard`；开关行选中态用字重 / 主色（无书签形）；标签 `SelectablePill`（选中 `primaryContainer #DCE4EE` + 主色字） |
| 文本字族·字号 | 页标题 Serif 26sp(500)；区块标题 Sans 19sp(600)；正文 Sans 16sp；caption Sans 11sp |
| **外观设置入口** | 仅在此页（个人页）的 `ThemeSwitchButton` / `SelectablePill`，**选项 = 跟随系统 / 浅色 / 深色（无「默认」第 4 态）** |
| 底部导航 | 5 项；active「我的」书签旗标锚定顶发丝线、向内垂下 8×9dp（回退 6×7dp）、与 24dp 图标间隔 ≥4dp、V 缺口、填充 primary（图标文字仍 primary） |

### B8 · 个人页（深 / Dark）

| 维度 | 值 |
|---|---|
| 页面底 / 卡片面 / 文字 / 主色 / 边框 | 同 B2（`#121316` / `#1B1D22` / `#E8EAEE` `#9AA0AA` / `#8AA6D8` / `#2A2D33`） |
| 设置组 / 开关行 / 标签 | 同 B7 规范（颜色随深态换值） |
| **外观设置入口** | 同 B7：仅个人页，选项 = 跟随系统 / 浅色 / 深色（无「默认」） |
| 底部导航 | 5 项；active「我的」书签旗标（靛青 `#8AA6D8`）锚定顶发丝线、向内垂下 8×9dp（回退 6×7dp）、与 24dp 图标间隔 ≥4dp、V 缺口（图标文字仍 primary） |

---

# 附录 C：批注对比度计算脚本 + 两套结果表（第 5 点 · 保留 5 色语义）

## C.1 计算脚本（Python，遵循 WCAG 2.1，两套标准）

> 脚本文件：`docs/WorkBuddy/_contrast_calc.py`（已按第 5 点重写并重跑）。合成：sRGB 空间直线混合 `C = α·C_src + (1−α)·C_paperBg`（仅高亮底用 α）；相对亮度 `L = 0.2126·R + 0.7152·G + 0.0722·B`（通道先 sRGB→linear）；对比度 `CR = (L_lighter+0.05)/(L_darker+0.05)`。
>
> **两套独立标准（禁止混用）**：
> - **(a) 高亮底**：正文 fg 落「合成高亮底」的【文本对比度】 ≥ 4.5:1（AA）。合成底 = 随纸 5 色批注色 @ 0.18 与 paperBg 混合。
> - **(b) 下划线 / 批注引线**：线条颜色（`annotationStrokeColors[paper][color]` 暗化描边） 与 纸张背景 的【非文本对比度】 ≥ 3:1（WCAG 1.4.11）。**保留 5 色语义**：浅纸面明度过高的批注色向黑插值暗化，夜读档沿用原批注色，均保各自色相。
>
> 脚本对每 (paper, color) 二分求解一个满足 ≥3.1（安全余量）的暗化描边色，输出 §4.5 落地表。

## C.2 结果表一 · 正文对比度（高亮底，目标 ≥ 4.5:1）

> 合成底 = 随纸 5 色批注色 @ 0.18 与 paperBg；验证「正文 fg vs 合成底」。共 4 档 × 5 色 = 20 组。

| 纸张 | 黄 | 红 | 绿 | 蓝 | 紫 |
|---|---|---|---|---|---|
| white | 14.80 | 13.71 | 13.76 | 13.42 | 13.49 |
| warm | 11.67 | 10.91 | 11.02 | 10.98 | 11.02 |
| green | 11.75 | 10.70 | 10.77 | 10.79 | 10.82 |
| night | 9.18 | 10.38 | 10.25 | 10.14 | 9.94 |

**结论：20 组全部 ≥ 4.5:1（AA），全过 ✅。** 最严苛为 night 黄 9.18:1。工程落地按 `批注色.copy(alpha = 0.18)` 叠加于 paperBg 即可。

## C.3 结果表二 · 非文本对比度（下划线 / 引线，暗化描边 5 色语义，目标 ≥ 3:1，WCAG 1.4.11）

> 线条颜色 = `annotationStrokeColors[paper][color]`（§4.5 暗化描边，实色不叠加 alpha）；验证「线条 vs 纸张背景」。下划线与批注引线**共用同一套描边色**（同一颜色、同一纸面，对比度相同），故 4 档 × 5 色 = 20 组实测，覆盖 4 档 × 5 色 × 2 型 = 40 项。单元格格式：`描边色 · 实测对比度`。

| 纸张（bg） | 黄 | 红 | 绿 | 蓝 | 紫 |
|---|---|---|---|---|---|
| white `#FAFAFB` | `#A38E40`·3.10 | `#BF7F70`·3.10 | `#749962`·3.10 | `#6E8FC0`·3.17 | `#A783AF`·3.10 |
| warm `#F3ECDC` | `#A1823C`·3.10 | `#B5705A`·3.29 | `#7C8A5A`·3.16 | `#6E84A8`·3.23 | `#9A7C92`·3.15 |
| green `#E8F0DF` | `#948743`·3.10 | `#B5705A`·3.31 | `#6E8A55`·3.31 | `#6E84A8`·3.25 | `#9A7C92`·3.17 |
| night `#15171C` | `#E6C95A`·10.99 | `#D08B7A`·6.54 | `#8FA86B`·6.81 | `#8EA3D0`·7.09 | `#C0A0C8`·7.77 |

**结论：20 组（覆盖 40 项）全部 ≥ 3:1（非文本），全过 ✅。** 最小 3.10:1（white 黄/红/绿/紫，已含安全余量）。其中 white·蓝、warm·红/绿/蓝/紫、green·红/绿/蓝/紫、night 全 5 色为**沿用原批注色**（原色已达标）；white·黄/红/绿/紫、warm·黄、green·黄 为**暗化版**（原色在浅纸面 1.56–3.05:1 未达 3:1，已暗化至稳过）。高亮底仍保留 5 色批注色身份，5 色语义完整。

## C.4 总结论

| 标准 | 范围 | 目标 | 结果 |
|---|---|---|---|
| 正文对比度（高亮底） | 4 档 × 5 色 = 20 组 | ≥ 4.5:1 | **全过 ✅**（最小 9.18:1） |
| 非文本对比度（下划线 / 引线，5 色暗化描边） | 4 档 × 5 色 = 20 组（×2 型 = 40 项） | ≥ 3:1 | **全过 ✅**（最小 3.10:1） |

> 最终参数：**高亮底 alpha = 0.18 / 下划线·引线 = `annotationStrokeColors` 暗化描边（实色，不叠加 alpha）**。两套标准分别计算、分别列表，结论全部通过。

---

# 附录 D：有序实现任务清单（给工程师）

> 设计方向已冻结，以下按**实现顺序**编排；文件相对路径以 `android/` 为根。所有颜色 / 字号 / 圆角 / 阴影 / 书签几何 / 描边暗化色均已确定，工程师照单落地即可，**无需再做设计决策**。

## D.0 前置说明（务必先读）

- **零设计风险原则**：本清单只做「删除 / 回退 / 替换 / 接入」，**不引入新视觉**。任何与本文档值不符的「更好方案」均不在本次范围。
- **真 Hex 来源**：外壳 `#3D5A80`/`#8AA6D8`、纸张 4 档、批注 5 色、§4.5 `annotationStrokeColors` 均见本文档，直接抄值。
- **回退顺序**：D1 → D2 → D3（数据/配色基础）→ D4 → D5 → D6（界面三处）→ D7（验收）。D4/D5/D6 可在 D3 后并行。

## D.1 任务 T1 · 外观视觉回退 DEFAULT（删 Apple/Web 视觉分支）

- **源文件**：
  - `app/src/main/java/com/creationreadingassistant/ui/theme/ThemeProvider.kt`
  - `app/src/main/java/com/creationreadingassistant/ui/theme/ComponentSpec.kt`
- **依赖**：无（最先做）。
- **要点**：
  1. `ThemeProvider.kt:25` `resolveThemeParams()` 的 `VisualStyle.APPLE` / `VisualStyle.WEB` 分支改为与 `DEFAULT` 一致（返回 `ThemeParams()`，不覆盖外层 AppTheme）。
  2. `ThemeProvider.kt:59` `componentSpecForStyle()` 调用改为单套——`VisualStyle.APPLE`/`WEB` 均返回 `DefaultComponentSpec`（或直接删分支，仅留 `DEFAULT`）。
  3. `VisualStyleProvider:63` `LocalGlassPalette` 恒 `null`（Apple 特判已随分支删除自然失效）。
  4. `ComponentSpec.kt` 保留 `DefaultComponentSpec`（§3.1 字段值），`AppleComponentSpec`/`WebComponentSpec` 与 `SquircleShape` 引用在 T2 删除；本任务**不删文件**，只确保回退。

## D.2 任务 T2 · 物理删除 AppleTheme.kt / WebTheme.kt 及其引用

- **源文件**：
  - `app/src/main/java/com/creationreadingassistant/ui/theme/AppleTheme.kt`（删除）
  - `app/src/main/java/com/creationreadingassistant/ui/theme/WebTheme.kt`（删除）
  - 引用方（`ThemeProvider.kt` 的 `AppleLightColorScheme`/`AppleDarkColorScheme`/`AppleTypography`/`AppleShapes`/`AppleGlassPalette`/`Web*`、`ComponentSpec.kt` 的 `SquircleShape`、`OnboardingOverlay.kt` 的 `VisualStyle.APPLE` 特判、`AppNavigation.kt` 的 `VisualStyle.APPLE` 特判——T5 一并清）需同步停引，避免编译失败。
- **依赖**：T1。
- **要点**：删除两文件后，全局搜索并清除对上述符号的 import / 引用；`VisualStyle` 枚举保留 `APPLE`/`WEB` 常量仅用于 `fromStored` 兼容回退（见 §7），**不再有对应实现**。

## D.3 任务 T3 · 阅读器纸张 7→4 + 新建 ReaderPaperPalette（含 annotationStrokeColors）

- **源文件**：
  - `app/src/main/java/com/creationreadingassistant/data/settings/SettingsStore.kt`
  - `app/src/main/java/com/creationreadingassistant/ui/theme/ReaderPaperPalette.kt`（**新建**）
  - `app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderScreen.kt`
- **依赖**：T1（palette 引用外壳 primary 即可，无需 T2 完成）。
- **要点**：
  1. `SettingsStore.kt:98` `migrateReaderBg()` 由 7 档收敛为 4 档：`null/"paper"→"warm"`、`"plain"→"white"`、`"eye"→"green"`、`"warm-yellow"→"warm"`、`"green-bean"→"green"`、`"oled-black"→"night"`（其余 white/warm/green/night 直传）。
  2. **新建 `ReaderPaperPalette.kt`**：定义 4 档 `ReaderPaperPalette`（bg/fg/accent/outlineVariant/outline/light-dark + 5 色高亮批注色 + §4.5 `annotationStrokeColors`），并提供 `paperPalette(key)` 与「跟随外观」映射（浅→white、深→night）。
  3. `ReaderScreen.kt:263` `paperColors()` 硬编码 7 档 → 改为查 `ReaderPaperPalette`；`:253` `highlightColor()` 固定亮色 → 改为按 paper 取 5 色批注色 `@0.18`。~~下划线/引线取 `annotationStrokeColors[paper][color]`~~ **本条撤回**：标注模型无类型字段，下划线/引线不可达，Token 不落代码（见 §4.5 实现状态注记）。
  4. 阅读器 chrome（顶栏/底栏/进度条/弹层/系统栏）改读 `paperPalette.light/dark` 而非外壳 surface。

## D.4 任务 T4 · ThemeSwitchButton 仅 3 态（跟随系统 / 浅色 / 深色）

- **源文件**：
  - `app/src/main/java/com/creationreadingassistant/ui/components/ThemeSwitchButton.kt`
- **依赖**：T1（视觉统一后按钮只管外观模式）。
- **要点**：
  1. 删除 `displayName()`/`accentPreview()` 中 `APPLE`/`WEB` 分支；枚举项改为 `{跟随系统, 浅色, 深色}`，色点统一 `#3D5A80`（深态 `#8AA6D8`），**无「默认」第 4 态**。
  2. 图标统一 `Icons.Filled.Palette` + `Icons.Filled.ArrowDropDown`（删 iOS 线性图标特判 `ic_palette_line`/`ic_arrow_down_line`/`ic_check_line`）。
  3. 仅控制应用外观（写 `appearance_theme_mode` = system/light/dark），不含纸张入口。

## D.5 任务 T5 · BookmarkIndicator 接入底栏（锚定顶发丝线、避让 24dp 图标）

- **源文件**：
  - `app/src/main/java/com/creationreadingassistant/ui/components/BookmarkIndicator.kt`（**新建**）
  - `app/src/main/java/com/creationreadingassistant/ui/navigation/AppNavigation.kt`
- **依赖**：T1（底栏已无 Apple 特判后可干净接入）。
- **要点**：
  1. **新建 `BookmarkIndicator.kt`**：先实现 8×9dp，`M0,0 H8 V9 L4,5 L0,9 Z`，填充 `primary`；**预留 6×7dp 常量/开关**（`M0,0 H6 V7 L3,4 L0,7 Z`）作真机回退。不悬浮、不探出底栏上沿。
  2. `AppNavigation.kt` 底栏 active 项接入 `BookmarkIndicator`：锚定顶 `HorizontalDivider(1.dp, outlineVariant)` 向内垂下；**保留 24dp 导航图标、书签与图标垂直间隔 ≥4dp、不替代/遮挡**（删 `:225`/`AppNavigation.kt:252` 的 `VisualStyle.APPLE` 两处特判与「图标上方探出」逻辑）。
  3. 当前项图标+文字仍用 `primary`，书签仅为形状点缀。

## D.6 任务 T6 · 首页仅「继续阅读」用 SectionCard，统计/灵感去卡片化

- **源文件**：
  - `app/src/main/java/com/creationreadingassistant/ui/screen/HomeScreen.kt`
  - `app/src/main/java/com/creationreadingassistant/ui/components/SharedComponents.kt`
- **依赖**：T1（消费 `LocalComponentSpec` 已稳定）。
- **要点**：
  1. 「继续阅读」区块改用消费 `SectionCard`（§3 容器规则）；统计区/今日灵感区**移除卡片容器**，只消费颜色/排版/间距 Token。
  2. 统计区 = 无边框 + 1dp `outlineVariant` 竖向 hairline 分隔（两项阅读时长/藏书数）。
  3. 今日灵感区 = 留白 + 一条 ≈1dp `onSurface` 短水平墨线（长 40–56dp），非卡片。
  4. `SharedComponents.kt` 的 `SectionCard`/`SelectablePill`/`SettingRow` 继续消费 `LocalComponentSpec`，无需改结构，仅确认首页不再对统计/灵感套卡片。

## D.7 任务 T7 · 构建与真机验收（第 6 点）

- **源文件**：无新增；验收依据 §9.1 与附录 B 八表。
- **依赖**：T1–T6 全部完成。
- **要点**：
  1. 构建：`./gradlew :app:assembleDebug`。
  2. 小米真机：`adb push app-debug.apk /sdcard/Download/` → 文件管理手动安装。
  3. 截图核对：浅/深 × 首页/书架/阅读器/个人页 × 四纸张（含跟随外观）。重点：书签不遮挡 24dp 图标且 ≥4dp；统计/灵感非卡片；阅读器 chrome 无断层；高亮底可读。（下划线/引线未实现，不在验收范围，见 §4.5 实现状态注记。）
  4. 微调**仅限尺寸 Token**（书签 8×9↔6×7、间距、圆角），颜色/字号/书签形态/描边暗化色已冻结不改。

## D.8 任务依赖图

```
T1(外观回退DEFAULT) ──► T2(删Apple/Web文件+停引)
   │
   └──► T3(纸张7→4 + ReaderPaperPalette + annotationStrokeColors) ──┐
                                                                     │
T1 ──► T4(ThemeSwitchButton 3态)                                     │
T1 ──► T5(BookmarkIndicator 接入底栏)                               │
T1 ──► T6(首页仅继续阅读用SectionCard)                              │
                                                                     │
                          T1,T2,T3,T4,T5,T6 全部完成 ──► T7(构建+真机验收)
```

> 说明：T4/T5/T6 仅依赖 T1（视觉已统一）；T3 可独立；T2 紧随 T1；T7 为终态验收。建议顺序 T1→T2→T3→(T4∥T5∥T6)→T7。
