# A01 真实手机定向验收报告 — Qoder

> 验收 Agent：**Qoder**（独立复核与验收方，不修产品代码）
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 开工与收工 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> 上游开发报告：`docs/coordination/agent-results/agent-result-a01-zcode.md`
> 我的代码轴复核：`docs/coordination/reviews/undated/agent-review-a01-qoder.md`（判定：代码轴 PASS，建议进入真机）
> 本报告：`docs/qa/2026-09/2026-09-21-a01-qoder-device-acceptance.md`
> 证据目录：`docs/qa/evidence/2026-09-21-a01-qoder-device-acceptance/`
> 判定：**真机轴 PASS**

---

## 0. 结论速览

| 旅程 | 结果 |
|---|---|
| **A 已有高亮** | **PASS** —— 空态消失，3 条高亮全部列出，点击跳到**章内 40%** 的真实位置（不是章首），无类型不匹配提示 |
| **B 普通笔记** | **PASS** —— 目录笔记 Tab 与完整「笔记与标注」Sheet 两处都能看到普通笔记；两处点击都按 **NOTE** 定位（若仍借用 BOOKMARK，resolver 会返回 null 并提示"无法定位…类型不匹配"，实测 0 次） |
| **C 书签隔离** | **PASS** —— 书签只出现在「书签」Tab，「笔记」Tab 内该书签标题出现 **0 次**；书签点击按 BOOKMARK 定位（第6章 83% → 第1章 0%）；本轮临时创建的书签**已删除并回读确认** |
| **D 真正空态** | **PASS** —— 高亮/笔记/书签全为 0 的中性夹具仍显示"暂无划线与笔记"，空态未被误伤 |
| 补充观察 | 混排可见、图标色调可辨、长文本截断合理、列表回收无崩溃 |
| 稳定性 | 无 FATAL、无 ANR、无 Duplicate key；crash buffer 为空；进程未异常重启 |
| 设置与权限恢复 | **完成**（含一项我未主动修改却漂移的权限，见 §6.1） |
| 用户数据 | **未触碰用户真实书籍**；未发生任何导入；标注总量与测试前逐项相等 |

**A01 判定：真机轴 PASS。** 结合代码轴 PASS，建议 Codex 关闭 A01。

---

## 1. 边界遵守

| 约束 | 结果 |
|---|---|
| 只用真实手机，禁止模拟器 | 全程 `adb -s c49ac6cf`；`adb devices -l` 见 §2 |
| 开始时重新确认设备 | 已执行，未沿用历史值 |
| 不打开/修改/删除用户真实书籍与标注 | 未做。本轮只打开 4 本我此前生成的中性夹具（测试 EPUB / 测试 TXT / 测试 Markdown B / 长章测试 TXT）；`books` 表 live 计数 11 → 11 未变 |
| 只用中性测试夹具 | 是 |
| 报告/截图只用中性名称 | 全文只用 测试 EPUB / 测试 TXT / 测试 Markdown 等；`t-txt-s07` 等为夹具中性代号。**书架页含用户真实书名，因此我未保存任何书架截图**（只在 `/tmp` 留了用于定位坐标的 dump，不入库） |
| 只删除本轮自己创建的临时书签 | 只删了 1 条（`kind=bookmark`，created 2026-09-21T10:22:14Z，deleted 10:38:51Z），删除前经二次确认对话框；**未删除或修改任何测试前已存在的高亮/笔记/书签** |
| 不直接写设备数据库 | 未写。DB 全程 `run-as cat` 复制到主机后以 `mode=ro` URI 只读查询 |
| 不修改产品/测试源码、不 commit/push/stage/stash/reset/checkout/clean/全仓格式化 | 未做。收工 `git diff --cached` = 0、`git stash list` = 0、HEAD `5aed593` |
| 不改 MIUI 安全设置、不要求用户手动点击 | 未改；安装由脚本自动确认循环完成 |
| 不领取 D-2 / S08–S20 / 其他缺陷 | 未领取 |

---

## 2. 设备与测试前状态

```
$ adb devices -l
List of devices attached
c49ac6cf    device product:diting model:22081212C device:diting transport_id:1
```

| 项 | 值 |
|---|---|
| 序列号 | `c49ac6cf`（真实手机） |
| 机型 | `22081212C` / `product:diting` |
| 分辨率 / 密度 | `1220x2712` / `480` |

### 2.1 测试前回读

| 类别 | 键 | 测试前值 |
|---|---|---|
| 设备 | `stay_on_while_plugged_in` | `3` |
| 设备 | `accelerometer_rotation` | `1` |
| 设备 | `user_rotation` | `0` |
| 设备 | `font_scale` | `1.0` |
| 权限 | `POST_NOTIFICATIONS` | **`granted=false`** |
| 安装包 | `versionName` | `0.5.1` |
| 安装包 | `lastUpdateTime` | `2026-09-21 13:02:19`（= 上一轮 F5 APK） |
| 安装包 | `firstInstallTime` | `2026-09-19 18:38:36` |
| 安装包 | 设备上 base.apk SHA256 | `3164f41aae9616fd98d4d7ac63a74597d412429e494c1b56edfd08423881909c` |
| 全局 | `reader_mode` / `pager_engine_mode` / `epub_pager_engine_mode` / `reader_page_turn_effect` / `reader_screen_orientation` / `reader_tap_zone_mode` / `reader_tap_action_preset` | `paged` / `on` / `auto` / `none` / `system` / `three-zone` / `standard` |
| per-book | 测试 TXT `readerMode` | `paged` |
| per-book | 测试 Markdown `readerMode` | `scroll` |
| per-book | `t-txt-b-s07 fontSize` | `25.0` |
| per-book | 测试EPUB `fontSize`/`lineHeight`/`background` | `28.0`/`1.95`/`warm` |
| per-book | **override 键总数** | **6** |
| 数据 | live books / highlights / notes / bookmarks | **11 / 16 / 2 / 0** |

> **安装前设备上的 APK 不是 A01 构建**（SHA 与 A01 产物不同），因此按任务书重新构建并安装。

---

## 3. 构建与安装

### 3.1 构建

```bash
cd android && ./gradlew.bat --console=plain :app:assembleDebug
```

| 项 | 值 |
|---|---|
| 结果 | `BUILD SUCCESSFUL in 20s`，退出码 **0** |
| APK | `android/app/build/outputs/apk/debug/app-debug.apk` |
| 大小 | 52,642,429 字节 |
| **SHA256** | `01afa7baa287a5a9a085a5284344812c082c1482ec49ac2e5a38a9c5a795e477` |

### 3.2 安装

先读脚本真实接口（`param([string]$Apk, [string]$Serial, [string]$PackageName, [switch]$TestApk, [switch]$NoDirectInstall, [int]$TimeoutSeconds)`），按其真实签名调用，未猜参数：

```
powershell.exe -ExecutionPolicy Bypass -File android/scripts/install_with_confirm.ps1 \
  -Apk android/app/build/outputs/apk/debug/app-debug.apk -Serial c49ac6cf
```

| 项 | 值 |
|---|---|
| 脚本输出 | `RESULT: CONFIRM_LOOP (installed through the MIUI installer UI)` / `Installed com.creationreadingassistant successfully.` |
| 退出码 | **0** |
| 自动确认点击轨迹 | `android:id/button3` ×2 → `com.miui.packageinstaller:id/checkbox` → `com.miui.packageinstaller:id/second_button` |
| **是否使用直接 ADB 回退** | **否**（未出现超时，未被 MIUI 阻断，退出码不是 2） |
| 安装后回读 | `lastUpdateTime=2026-09-21 18:08:57`；`firstInstallTime` 仍 `2026-09-19 18:38:36`（未清数据）；设备 base.apk SHA256 = `01afa7ba…` == 构建产物 |

> 口径：**install_with_confirm.ps1 PASS（CONFIRM_LOOP，退出码 0）**。本轮没有回退，因此不存在"回退成功"的说法。

---

## 4. 夹具选择（只读 DB 查询，未构造任何数据）

查询脚本与结果：`query_fixtures.py`（只读 `mode=ro`）。

| 需要的夹具 | 选中 | 依据（live 计数） |
|---|---|---|
| 已有高亮的测试 EPUB | **测试 EPUB**（`epub_zhrum`） | 高亮 3（第一章 码头 blue ×2、第三章 值夜 green ×1）；普通笔记 0 |
| 已有普通笔记的测试 TXT | **测试 TXT**（`t-txt-s07`） | 普通笔记 2（`kind=note`，第2章 / 第4章）**且同时有 2 条高亮** → 顺带覆盖"高亮与普通笔记共存" |
| 带书签的书 | **不存在**（全库 live bookmark = 0） | → 按任务书许可，在测试 TXT 内**通过 App UI** 创建 1 条临时书签，验收后删除 |
| 高亮与普通笔记都为 0 | **测试 Markdown B**（`t-md-b-s07`）：0/0/0；另有 `t-txt-b-s07`：0/0/0 | 空态旅程可用既有中性夹具，无需构造 |
| 长列表（回收/重复 key） | **长章测试 TXT**（`t-txt-long-s07`）：8 条高亮 | 补充观察用 |

**未通过数据库写入构造任何数据。**

---

## 5. 四条必验旅程

### 5.1 旅程 A —— 已有高亮（测试 EPUB）

| # | 步骤 | 预期 | 实测 | 判定 |
|---:|---|---|---|---|
| A1 | 打开书 → 目录与标记 → 笔记 Tab | 不再显示空态 | `暂无划线与笔记` 出现 **0 次**；`阅读选中文本添加划线…` 出现 **0 次** | **PASS** |
| A2 | 高亮条目可见 | 至少看到已有高亮 | **3 条**卡片，各带「跳转」：`第一章 码头 / 雨后的石阶泛着青色，缝隙里长出细小的苔…` ×2、`第三章 值夜 / 窗台上的陶盆裂了一道缝…` ×1 —— 与 DB 的 3 条 live 高亮**逐条对应** | **PASS** |
| A3 | 点击第 1 条高亮 | 跳回正文对应位置 | 从 `第二章 石阶 17.8%` → `第一章 码头 0.0%`（该记录 `progress_percent=0.0`） | **PASS** |
| A4 | 点击第 3 条（**章内位置**，判别性更强） | 跳到该高亮真实位置 | 从 `第一章 码头 0.0%` → **`第三章 值夜 35.9%`（表头 40%）**，与 DB `progress_percent=41.03` 一致，且该页正文含被划线的句子 → **不是"跳到章首"的假通过** | **PASS** |
| A5 | 是否跳成普通笔记/书签目标 | 不出现类型不匹配 | `无法定位` 提示出现 **0 次** | **PASS** |

证据：`screenshots/01-A-…no-empty-state.png`、`ui-dump-a5.xml`（列表）、`ui-dump-a6.xml`、`ui-dump-a7.xml`（两次跳转后位置）。

### 5.2 旅程 B —— 普通笔记（测试 TXT，同时含 2 高亮 + 2 普通笔记）

| # | 步骤 | 预期 | 实测 | 判定 |
|---:|---|---|---|---|
| B1 | 目录与标记 → 笔记 Tab | 普通笔记可见 | 列表共 **4 条**（`跳转` ×4）：2 条高亮（第6章、第2章）+ **2 条普通笔记**（标题 `柜台后的老人抬起头…`，含摘录与正文块）—— 与 DB 的 2+2 完全对应 | **PASS** |
| B2 | 点击普通笔记（笔记 Tab） | 跳回该笔记位置 | 从 **`第6章 测试章节6号 83%`** → **`第1章 测试章节1号`**（该记录 locator `ci=0, co=482`），表头随后显示 `0.0% → 6.5%`，即**按 locator 的章内偏移落点**，不是原地不动 | **PASS** |
| B3 | 是否误按书签处理 | 无"类型不匹配" | `无法定位` 出现 **0 次**（t+0.8s 与 t+2.8s 两次采样均为 0） | **PASS** |
| B4 | 打开完整「笔记与标注」Sheet | 普通笔记在「笔记 / 书签」小节 | 小节计数 **3**（2 普通笔记 + 1 当时的临时书签），高亮小节 2 条 | **PASS** |
| B5 | 在完整 Sheet 点普通笔记的「跳转」 | 仍按 **NOTE** 定位 | 从 `第1章 13.2%` → **`第1章 0.0%`**（另一条笔记 locator `co=78`），Sheet 关闭，`无法定位` **0 次** | **PASS** |

**为什么"无提示 + 位置变化"足以证明按 NOTE 解析**：`resolveAnnotationTarget` 对 `bookmark:{id}` 只匹配 `kind == 'bookmark'` 的行；这两条记录 `kind='note'`，若仍借用 BOOKMARK 类型，resolver 必返回 null → 清请求并弹"无法定位该阅读笔记，记录可能已删除或类型不匹配"。实测两处均无该提示且确实发生了定位。

证据：`ui-dump-b4.xml`、`ui-dump-b8.xml`、`ui-dump-b13.xml`、`ui-dump-b14.xml`、`ui-dump-b16.xml`、`screenshots/02-B-…`、`03-B-…`、`04-B-…`。

### 5.3 旅程 C —— 书签隔离

书签夹具不存在，按任务书许可在测试 TXT 内用 App UI 创建：`笔记与标注 → 添加当前位置书签` → DB 新增 `kind=bookmark`、`title=书签 · 第1章 测试章节1号`、`created_at=2026-09-21T10:22:14.505Z`。

| # | 步骤 | 预期 | 实测 | 判定 |
|---:|---|---|---|---|
| C1 | 「书签」Tab | 书签出现在此 | 1 条 `书签 · 第1章 测试章节1号` + 「跳转」；`暂无书签` = 0 | **PASS** |
| C2 | 点击书签「跳转」 | 按 BOOKMARK 定位 | 从 **`第6章 83%`** → **`第1章 0%`**（书签建立位置），`无法定位` **0 次** | **PASS** |
| C3 | 「笔记」Tab 对照 | 同一书签**不得**重复出现 | 笔记 Tab `跳转` = 4（2 高亮 + 2 普通笔记），`书签 · 第1章` 出现 **0 次** | **PASS** |
| C4 | 删除本轮临时书签 | 只删这一条 | 「笔记 / 书签」小节该卡片「删除」→ 二次确认框（`「书签 · 第1章 测试章节1号」将被彻底删除，且无法撤销。`）→ 确认；DB 回读 `deleted_at=2026-09-21T10:38:51.783Z` | **PASS** |
| C5 | 恢复回读 | 回到测试前状态 | **live bookmarks = 0**（测试前亦为 0）；live notes 仍 2、live highlights 仍 16（**未误删任何既有标注**） | **PASS** |

> C1 之后的「删除后书签 Tab 显示暂无书签」这一次 UI 回读**没有取到**（我的导航没能再次稳定打开 Sheet，`ui-dump` 显示 sheet=0；对应截图已改名为 `discarded-C2-navigation-missed-no-sheet.png`）。恢复确认以**只读 DB 回读**为准（live bookmark = 0），并附"删除前该 Tab 确实渲染过这条书签"的反向证据（`ui-dump-k6.xml`），不冒充 UI 回读。

证据：`ui-dump-k6.xml`（书签 Tab）、`ui-dump-k8.xml`（跳转后）、`ui-dump-k10.xml`（笔记 Tab 无书签）、`ui-dump-f5.xml`（删除二次确认）、`screenshots/05-C-…`、`06-C-…`。

### 5.4 旅程 D —— 真正空态（测试 Markdown B，0 高亮 / 0 笔记 / 0 书签）

| 项 | 实测 | 判定 |
|---|---|---|
| 笔记 Tab | `暂无划线与笔记` 出现 **1 次**、`阅读选中文本添加划线或思考，灵感随时沉淀。` 出现 **1 次**、`跳转` **0 次** | **PASS —— 空态未被误伤** |

证据：`ui-dump-d4b.xml`、`screenshots/07-D-true-empty-state-still-shown.png`。

---

## 6. 补充观察

| 项 | 实测 | 评价 |
|---|---|---|
| 高亮与普通笔记共存 | 测试 TXT 的笔记 Tab 同时显示 2 高亮 + 2 普通笔记，顺序为**高亮在前、笔记在后**（与代码 `highlights + notes` 拼接一致） | 符合实现；无小节标题（见 §7 Spec） |
| 图标 / 色调可辨识度 | 高亮卡片 = 琥珀色**书签形**图标 + 章节名 + 摘录；普通笔记卡片 = 紫色**编辑笔记**图标 + 标题 + 摘录 + 灰底正文块 | 高亮 vs 笔记在**同一 Tab 内**可分辨。但高亮的书签形琥珀图标与「书签」卡片图标（同为书签形、同为橙调）**几乎同形**，跨 Tab 语义偏弱 |
| 长文本截断 | 高亮摘录 `maxLines=3` + 省略号；普通笔记标题 1 行省略号；正文块不截断 | 合理，卡片高度未失控 |
| 列表滚动 / 重复 key | 长章测试 TXT（8 条高亮）笔记 Tab 首屏渲染 7 条「跳转」，滚动后回收正常；`FATAL` / `Duplicate key` 命中 **0**；crash buffer 为空 | 无重复 key 崩溃。注：向下拖到列表顶会触发底部 Sheet 的 drag-to-dismiss（标准行为，非缺陷），对应截图改名 `discarded-E1-…` |
| 跨表同 raw ID | **未通过 UI 构造**（任务书不要求）；由 JVM 行为测试覆盖：`the same raw id across tables yields distinct list keys` / `…keeps its own typed target` / `a plain note target never resolves against the highlight table` | 按任务书处理 |

### 6.1 本轮我自己造成的两处偏差（如实记录）

1. **`POST_NOTIFICATIONS` 在未由我授权的情况下从 `false` 变成 `true`。** 我在收工回读时才发现（测试前基线 `false`）。本轮我**没有执行过 `pm grant`**，也没有主动点击「听书」。最可能的解释是某次盲点落在一个系统运行时权限弹窗的"允许"上（我的坐标链有多次落点偏移，见 §8），但我**没有证据确认弹窗出现过，也没有确认是应用主动申请的**，因此只登记为"原因未确定的权限漂移"。处置：`pm revoke` → 回读 **`granted=false`**（与测试前一致）。
2. **一次盲点误开了系统文件选择器**（`com.google.android.documentsui/.picker.PickActivity`）。我**没有在选择器里选任何文件**，用返回键 + `force-stop` 退出，并按 HOME 回到桌面。核验：`books` 表 live 计数 **11 → 11 未变**，未发生任何导入。

两处都属我的操作方式（盲发坐标）带来的风险，不是产品缺陷；但第 1 条值得工程侧关注：**如果该弹窗确由应用触发，说明存在一条我未预期的通知权限请求路径**，建议 Codex 决定是否单独立项排查（本轮任务书禁止我扩大范围，我没有继续深挖）。

---

## 7. 稳定性与数据完整性

| 检查 | 结果 |
|---|---|
| FATAL / ANR | `logcat -b crash -d` **空**；主 buffer 中 `FATAL EXCEPTION` / `Duplicate key` / `ANR in com.creationreadingassistant` 命中 **0** |
| 进程 | 全程存活（pid 12118 稳定）；唯一一次进程重启发生在 **18:08:57 重装 APK 时**（`SmartPower … died->visible`，属安装杀进程，非崩溃） |
| 跨书跳转 | 未出现：每次跳转后表头书名与章节均与目标记录一致（`EPUB · 第三章 值夜`、`TXT · 第1章 …`） |
| 数据丢失 | live highlights 16 → 16、live notes 2 → 2、live books 11 → 11；只新增又删除了我自己的 1 条临时书签（软删，`deleted_at` 已置） |
| 用户真实书籍 | 未打开、未修改、未删除 |
| 阅读进度 | 测试 TXT / 测试 EPUB 的进度因跳转与翻页而变化（夹具自有状态，属预期；未回滚，回滚需写库，任务书禁止） |

---

## 8. 方法学说明（为什么有些次尝试失败）

阅读器控制栏约 **2–2.5s 自动隐藏**，而跨 Bash 调用的往返延迟本身就有数秒；把 `tap 中心` 与 `tap 控件` 分在两次调用里，第二次必然落点到正文（正文会翻页或选块）。本轮所有成功旅程都改用**单条 `adb shell "input tap …; sleep 0.6; input tap …"` 链**，并另存一个先验证控制栏可见再点的小工具（`chrome_tap.sh`）。

即便这样，仍有 3 次导航没打开 Sheet（对应 3 张 `discarded-*` 截图与 `ui-dump` 里 `sheet:0`）—— 这些**我都保留并如实改名**，不拿它们当通过证据。§6.1 的两处偏差同样是这套盲点方法的风险产物。

---

## 9. 设置与权限恢复（逐项回读）

| 键 | 测试前 | **收工回读** | 判定 |
|---|---|---|---|
| `POST_NOTIFICATIONS` | `granted=false` | **`granted=false`**（`pm revoke`，退出码 0） | ✅ 已恢复（期间曾漂移到 true，见 §6.1） |
| `stay_on_while_plugged_in` | `3` | `3` | ✅ **全程未修改**（未临时改保持唤醒） |
| `accelerometer_rotation` | `1` | `1` | ✅ |
| `user_rotation` | `0` | `0` | ✅ |
| `font_scale` | `1.0` | `1.0` | ✅ |
| 全局 `reader_mode` | `paged` | `paged` | ✅ |
| 全局 `pager_engine_mode` | `on` | `on` | ✅ |
| 全局 `epub_pager_engine_mode` | `auto` | `auto` | ✅ |
| 全局 `reader_page_turn_effect` | `none` | `none` | ✅ |
| 全局 `reader_screen_orientation` | `system` | `system` | ✅ |
| 全局 `reader_tap_zone_mode` | `three-zone` | `three-zone` | ✅ |
| 全局 `reader_tap_action_preset` | `standard` | `standard` | ✅ |
| 测试 TXT `readerMode` | `paged` | `paged` | ✅ 未变（本轮全程在分页分支验收，无需改模式） |
| 测试 Markdown `readerMode` | `scroll` | `scroll` | ✅ 未动 |
| `t-txt-b-s07 fontSize` | `25.0` | `25.0` | ✅ 未动 |
| 测试EPUB `fontSize`/`lineHeight`/`background` | `28.0`/`1.95`/`warm` | 同 | ✅ 未动 |
| per-book override 键总数 | 6 | **6** | ✅ 无新增、无丢失 |
| live books / highlights / notes / bookmarks | 11 / 16 / 2 / 0 | **11 / 16 / 2 / 0** | ✅ 数据面完全回到测试前 |
| 前台 | — | `com.miui.home/.launcher.Launcher`（应用已 `force-stop`，误开的 documentsui 已关闭） | ✅ 干净收尾 |

---

## 10. 缺陷清单

**P0 / P1 / P2：0 项。**

| ID | 级别 | 项 | 归属 |
|---|---|---|---|
| A01-D1 | P3 | 高亮卡片使用**书签形琥珀图标**，与「书签」卡片图标同形同调，跨 Tab 语义偏弱（同一 Tab 内不歧义） | A01 引入（`HighlightMicroIsland`） |
| A01-D2 | P3 | 笔记 Tab 无小节标题，高亮段与笔记段直接拼接；用户只能靠图标分辨"划线/笔记" | A01 有意取舍（代码轴已登记 Spec-P3-①，真机确认可读性可接受但不显眼） |
| A01-D3 | P3 | 普通笔记卡片把**同一句话最多重复呈现三次**（标题行 + 摘录行 + 正文块内容几乎相同，见 `screenshots/02-B-…` 第 3、4 张卡片） | **非 A01 引入**（`NoteMicroIsland` 未改），本轮真机才暴露；建议单独立项 |
| A01-D4 | P3 | `POST_NOTIFICATIONS` 出现一次原因未定的 false→true 漂移（已恢复）。若确由应用触发，说明存在未预期的通知权限请求路径 | 原因未确定，不归因 A01 |

**SEAM REQUEST（`ReaderRulesSheetTest.kt` 补 2 处实参）：接受**，判定依据见代码轴报告 §8 —— 该文件对基线的 diff 只有 +7 行、全是补参与注释、零断言改动。

---

## 11. 最终判定

**真机轴 PASS。** 逐条对照任务书"真机通过标准"：

| 标准 | 命中 |
|---|---|
| 已有高亮不再显示空态 | ✅ |
| 高亮点击定位正确（含章内非零偏移） | ✅ |
| 普通笔记点击定位正确 | ✅（笔记 Tab 与完整 Sheet 两处） |
| 完整 NotesSheet 的普通笔记不再走 BOOKMARK | ✅（无类型不匹配提示 + 实际定位） |
| 书签只在书签 Tab，且跳转正确 | ✅ |
| 真正无高亮/普通笔记的书仍显示空态 | ✅（旅程 D 实做，非 NOT TESTED） |
| 无崩溃 / ANR / 重复 key / 跨书跳转 / 数据丢失 | ✅ |
| 测试前设备设置全部恢复 | ✅（含漂移权限已复位） |
| 未修改用户真实书籍和标注 | ✅ |

**建议：关闭 A01。** 未写"发布完成"；未领取 D-2 / S08–S20。

---

## 12. 回传清单

| 项 | 值 |
|---|---|
| Agent | **Qoder**（不披露底层模型标识） |
| 代码轴 | **PASS**（Standards 5 项全 P3；Spec 2 项全 P3；无 P0/P1/P2） |
| 真机轴 | **PASS** |
| 独立 JVM | 定向 **3 类 / 34 tests / 0 F / 0 E / 0 S**；代表性回归 **116 类 / 1018 tests / 0 F / 0 E / 0 S**；`compileDebugKotlin` 与 `compileDebugAndroidTestKotlin` 均 `--rerun` 真实重编译通过；全部退出码 0。与对方唯一差异：`ProfileWebDavManagerTest` 我 3 次全绿 → 判 flaky，不算 A01 证据、不归因 A01 |
| 真实设备序列号 | `c49ac6cf`（`22081212C` / `diting`，1220x2712 @480dpi，非模拟器） |
| APK SHA256 | `01afa7baa287a5a9a085a5284344812c082c1482ec49ac2e5a38a9c5a795e477`（52,642,429 字节；安装后设备 base.apk 二次复核一致） |
| 安装脚本结果 | `install_with_confirm.ps1` → `RESULT: CONFIRM_LOOP`，退出码 0；**未使用直接 ADB 回退** |
| 高亮 | **PASS**（3 条可见、空态消失、点击跳至第三章 40% 章内位置） |
| 普通笔记 | **PASS**（2 条在笔记 Tab 与完整 Sheet 均可见；两处点击都按 NOTE 定位，无类型不匹配） |
| 书签 | **PASS**（只在书签 Tab、笔记 Tab 0 次重复、按 BOOKMARK 跳转；临时书签已删除并回读 live=0） |
| 空态 | **PASS**（0/0/0 夹具仍显示"暂无划线与笔记"） |
| 设置恢复 | **完成**：设备项、7 个全局阅读设置、6 个 per-book override、`POST_NOTIFICATIONS=false`、数据面 11/16/2/0 全部逐项回读一致 |
| Standards | 5 项，最严重 = **S-P3-⑤：`ReaderTocSheetTest.kt` 里有一处与 A01 主题无关的新断言（`onAllNodesWithText("阅读中").assertCountEquals(0)`）未被报告披露，且无 before 备份可判定归属，需 Codex 确认** |
| Spec | 2 项，最严重 = **SP-P3-①：笔记 Tab 无小节标题、高亮段与笔记段直接拼接**（真机确认可读性尚可但不够显眼；另有 A01-D3 笔记卡片内容重复属非 A01 的 P3） |
| 代码复核报告 | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\coordination\reviews\undated\agent-review-a01-qoder.md` |
| 真机验收报告 | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\qa\2026-09\2026-09-21-a01-qoder-device-acceptance.md` |
| 证据目录 | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\qa\evidence\2026-09-21-a01-qoder-device-acceptance\` |
| 是否修改产品/测试源码 | **否**（零 `.kt` / `.gradle` 改动；未 commit / push / stage / stash / reset / clean；HEAD 仍 `5aed593`，`git diff --cached` = 0，`git stash list` = 0） |
| 状态 | **等待 Codex 主任务复核，未领取下一片。** |
