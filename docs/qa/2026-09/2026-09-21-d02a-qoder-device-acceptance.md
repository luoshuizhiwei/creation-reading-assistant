# D-2-A 真机功能验收报告 — Qoder（D-2-A-DEVICE）

> 验收 Agent：Qoder　|　声明模型：`qwen3.8Flash`
> 任务编号：D-2-A-DEVICE（真实 Android 手机独立功能验收，只验收不修复）
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`　固定基线：`origin/main = 5aed5939600e491843e3c44453921bd6273a233c`
> 验收开始 `git rev-parse --short HEAD` = `5aed593`，结束仍为 `5aed593`
> 前置报告：`agent-investigation-d02-zcode.md` / `agent-result-d02a-zcode.md` / `agent-review-d02a-qoder.md`
> 证据目录：`docs/qa/evidence/2026-09-21-d02a-qoder-device-acceptance/`
> 验收窗口：2026-09-21 21:21:30 ～ 22:00:36
> **最终结论：PASS**（功能验收）。恢复结论为**分层**表述，见 §1 与文末 §17「D-2-A-RESTORE 收尾复核」。

---

## 1. 最终结论

**PASS。**

任务书 A–I 九组用例**全部通过**。

**恢复结论按层区分（原文曾写"全部状态恢复到测试前基线并由字节级差异证明"，该表述过强，已纠正）：**

| 层面 | 结论 | 证据 |
|---|---|---|
| 全局设置 + 本书覆盖键 | **完全恢复**，70 项键值零差异 | `90-` vs `91-datastore-kv-*.json`（diff 为空），并在 RESTORE 轮再次复核 |
| 设备设置与权限 | **完全恢复**（5 项 + `POST_NOTIFICATIONS=false` + 进程 force-stop） | `09-` / `10-final-state-reread.txt` / `restore-final-device-state.txt` |
| 测试 Markdown 阅读位置 | **恢复/保持起始位置 0%** | `restore-markdown-position.png` |
| 测试 TXT 阅读位置 | **已恢复至第一章起始页 0%，且重启后仍为 0%**（原报告时点为残留 13.2%/12.1%，见 §15 与文末 RESTORE 小节） | `restore-txt-at-zero.png`、`restore-txt-reopen-zero.png` |
| 测试 EPUB 阅读位置 | **逻辑章页已恢复**；进度百分比残留 **1.5 个百分点**的引擎坐标重映射差异（35.9% → 34.4%），**不是字节级/百分比完全一致** | `ui-74-epub-pos-restored.txt` 与 `ui-r4-epub-reopened.txt` 逐行相同 |

未发现 P0/P1/P2；发现 4 项 P3（其中 1 项属验收工具副作用，1 项属既有问题，2 项属 UI 表达）。

| 判定门槛（任务书 PASS 条件） | 结果 |
|---|---|
| EPUB、TXT/Markdown 两类引擎都能按书覆盖 | ✅ 两个不同 bookId 各写入一个覆盖键，实测 |
| 覆盖重启后保留 | ✅ force-stop + 重启后仍生效（A9） |
| 其他书不受影响 | ✅ 同窗口另两本书有效值与渲染路径均未变（A8/B6/C） |
| 两种引擎键互不串台 | ✅ 四次 DataStore 快照交叉核对（C） |
| 大字号、护眼、默认预设均不清引擎覆盖 | ✅ 三个预设逐一验证（D） |
| 全部跟随全局能清覆盖 | ✅ 含引擎覆盖，清到 0 项（E） |
| GLOBAL 修改会写全局并清当前书覆盖 | ✅ 且只清被改动的那一个键（F） |
| 全局值和测试书原状态恢复 | ✅ **设置层面**：全 70 项 DataStore 键值集与测试前**零差异**。<br>⚠️ **阅读位置层面**：验收当时测试 TXT 残留 13.2%、测试 EPUB 存在 1.5pp 百分比重映射差异；测试 TXT 已在 **D-2-A-RESTORE** 轮通过 UI 恢复到第一章起始 0% 并重启复核，EPUB 差异作为已知重映射保留披露（见 §10.4 与文末小节） |
| 没有崩溃、ANR、数据损失 | ✅ 27.9 万行 logcat 内 0 FATAL / 0 ANR / 0 am_crash |
| 证据完整 | ✅ 26 张截图 + 81 组 UI dump + 5 份 DataStore 快照 + logcat |

**对 Codex 纠正的采纳**：H 用例的真机截图证实「页眉页脚」是用户可理解的汇总类别，
括号内 18 个类别覆盖 22 个底层设置，**不存在漏列**；我在代码复核报告中把它判为 P2 是错的，
本轮真机证据支持 Codex 的改判。残留只有两项 P3：`**全局**` 星号原样渲染、
以及「排版与显示相关的 22 项」这个定语把两类引擎兼容模式也框成了外观项。

---

## 2. 真实设备与 APK 身份

| 项 | 值 |
|---|---|
| `adb devices -l` | `c49ac6cf  device product:diting model:22081212C device:diting` |
| 现场确认序列号 | **`c49ac6cf`**（与历史一致，现场重新确认） |
| 型号 / 品牌 / 厂商 | 22081212C / Redmi / Xiaomi，`ro.build.product=missi` |
| **是否模拟器** | **否**：`ro.kernel.qemu` 为空，非 `emulator-*` 传输名。**全程未使用 MuMu** |
| Android 版本 / SDK | 15 / 35 |
| 屏幕 | 1220 x 2712 |
| 包名 | `com.creationreadingassistant`（versionName 0.5.1，versionCode 3） |
| 装机前 APK 时间戳 | `lastUpdateTime = 2026-09-21 18:08:57` |
| 装机后 APK 时间戳 | `lastUpdateTime = 2026-09-21 21:26:13` |

**关键：装机前的 APK 早于 D-2-A 源码改动（20:35），不是被测构建**；本轮验收跑在
我自己 21:23 生成、并证明含 D-2-A 代码的 APK 上。

---

## 3. 构建与安装结果

### 3.1 构建

```bash
# 工作目录：android/
./gradlew --console=plain :app:assembleDebug --rerun
```

| 项 | 值 |
|---|---|
| 退出码 | **0** |
| 构建结果 | `BUILD SUCCESSFUL in 26s`（21:23:30 → 21:23:57） |
| 任务统计 | 列出 48 个任务，**实际执行 8 个**，40 个 UP-TO-DATE |
| `compileDebugKotlin` | **UP-TO-DATE**（源码 `ReaderOverride.kt` mtime 20:35:25，上一次成功编译已覆盖它） |
| APK 路径 | `android/app/build/outputs/apk/debug/app-debug.apk` |
| APK 大小 | **53,629,843 字节** |
| APK mtime | 2026-09-21 21:23（`packageDebug`/`assembleDebug` 真实重新执行，产物为新） |
| **APK SHA-256** | **`f087d9142de96dd4b846eae5a18c3ba6d58bf5edb1f8bee38ce6a9571c0f0192`** |

**我不依赖 Gradle 的绿灯来证明"测的是 D-2-A"**：解包 21 个 dex 后按可打印字符串检索，

```
classes10.dex  'epubPagerEngineMode' = 2 次
classes10.dex  'pagerEngineMode'     = 1 次
其余 20 个 dex = 0
```

这两个字面量**只存在于 D-2-A 版本的 `ReaderOverride.kt`** ⇒ APK 确实含被测代码。
装机后再取一次设备侧哈希：

```
run-as com.creationreadingassistant sha256sum <base.apk>
  f087d9142de96dd4b846eae5a18c3ba6d58bf5edb1f8bee38ce6a9571c0f0192   ← 与本地构建逐字节一致
```

### 3.2 安装（仓库脚本，未用 ADB 回退）

先读脚本参数（`param(...)` 段）确认语法，再执行：

```
powershell.exe -NoProfile -ExecutionPolicy Bypass -File android/scripts/install_with_confirm.ps1 \
  -Apk "D:\...\android\app\build\outputs\apk\debug\app-debug.apk" -Serial c49ac6cf
```

| 项 | 值 |
|---|---|
| 脚本退出码 | **0** |
| 脚本最终 RESULT | **`RESULT: CONFIRM_LOOP (installed through the MIUI installer UI)`** |
| 是否自动确认成功 | **是**，依次点击 3 个安装器控件：`android:id/button3` → `com.miui.packageinstaller:id/checkbox` → `com.miui.packageinstaller:id/second_button` |
| MIUI 阻断 / 超时 | **无**（默认 `-TimeoutSeconds 90` 未触发） |
| **是否使用 ADB 直装回退** | **否**。`adb install -r` 全程未执行，因此不存在"回退冒充脚本 PASS"的问题 |

⚠️ 安装脚本的确认循环把 **POST_NOTIFICATIONS 从 `granted=false` 翻成了 `granted=true`**
（推测为 MIUI 安装页那个 checkbox）。这是**工具副作用，不是 D-2-A 产品代码问题**，
已在结束时 `pm revoke` 恢复并回读确认为 `false`。详见 §10 与 P3-3。

---

## 4. 测试前状态（全部现场读取，未使用历史值代替）

### 4.1 设备状态（`01-device-state-before.txt`，21:21:44）

| 项 | 测试前 | 说明 |
|---|---|---|
| `global/stay_on_while_plugged_in` | **3** | **本来就是 3**（他人/用户既有状态）。我全程未修改，结束仍须是 3，不是 0 |
| `system/accelerometer_rotation` | 1 | 未修改 |
| `system/user_rotation` | 0 | 未修改 |
| `system/font_scale` | 1.0 | 未修改 |
| `system/time_12_24` | 12 | 未修改（顺带记录） |
| POST_NOTIFICATIONS | **granted=false** | 未主动修改；被安装脚本翻转后已恢复 |
| 应用是否在运行 | **否**（`pidof` 空） | 冷启动验收 |

### 4.2 全局分页引擎模式（只读解析 `app_settings.preferences_pb`）

```
pager_engine_mode       = on      （TXT/Markdown）
epub_pager_engine_mode  = auto    （EPUB）
reader_mode             = paged
epub_engine_reenabled_v1 = true
```

### 4.3 三本中性测试书的覆盖与有效值（现场读取）

| 测试书（中性标签） | bookId | 测试前已有覆盖 | 引擎覆盖 | 有效引擎值 | 渲染路径 |
|---|---|---|---|---|---|
| 测试 EPUB | `epub_zhrum` | fontSize 28.0 / lineHeight 1.95 / background warm（**覆盖 3 项**） | **无** | epub = `auto` → 引擎开 | 「分页正文已就绪」 |
| 测试 TXT | `d5bd07ef-…` | readerMode paged（**覆盖 1 项**） | **无** | pager = `on` → 引擎开 | 「分页正文已就绪」 |
| 测试 Markdown | `9864d707-…`（t-md-b-s07） | **无（覆盖 0 项）** | **无** | pager = `on` → 引擎开 | 「分页正文已就绪」 |

**全库 DataStore 基线**：70 条偏好项，其中 `book_reader_override_*` 共 **6 条**，
**`_pagerEngineMode` / `_epubPagerEngineMode` 覆盖键数量 = 0** ⇒ 升级后所有旧书继续跟随全局（G 用例的前提）。

### 4.4 只读诊断手段的边界

- 仅使用 `adb exec-out run-as com.creationreadingassistant cat files/datastore/app_settings.preferences_pb`
  做**读取**，本机解析；
- **未**写入 / 编辑 / 替换任何 preferences 文件（设备侧文件 mtime 全程 16:42 未变，直到应用自己因 UI 操作写入）；
- **未**用脚本伪造任何覆盖键 —— 所有覆盖键都由真人在 UI 上点击产生；
- 设备无 `sqlite3`（`which sqlite3` 退出码 1），因此**未**查询 Room 数据库内容；
  书籍/标注数量改用 UI 与文件尺寸对照（§10）。
- **未使用仓库脚本 `android/scripts/dump_ui.py`**：该脚本会把 UI dump 写入
  `d:\develop\Code\Codex\creation-reading-assistant\...`（任务书禁止进入的原始仓库）。
  我在仓库外 `/tmp/d02a_qa/` 自建 dump/screenshot/tap 三个辅助脚本。

### 4.5 测试前数据规模

| 项 | 值 | 来源 |
|---|---|---|
| 书架总数 | 全部 11 / 在读 4 / 未开始 5 / 已完成 2 | `ui-30-bookshelf.txt` |
| 首页统计 | 累计阅读 3 小时 38 分钟 · 7 本 | `ui-00-shelf.txt` |
| 灵感 | 0（「还没有灵感，阅读时选中文字即可保存为灵感。」） | `ui-00-shelf.txt` |
| Room 主库文件 | 1,124,126,720 字节（mtime 18:36） | `03-app-storage-listing.txt` |
| -wal / -shm | 524,288 / 32,768（mtime 18:38） | 同上 |
| `reader_pager_health.xml` | `consecutive_crash_count = 0` | `04-app-storage-listing2.txt` |

高亮/笔记/书签的**逐条计数未能枚举**（无 sqlite3、且不为验收去点开每本书的笔记页），
本轮改用「未执行任何创建/删除标注的 UI 路径 + 灵感数 0→0 + 主库仅增长 1 个 4 KiB 页」三条间接证据，
登记为未覆盖项（§12 U3）。

---

## 5. A–I 每项结果

### A. EPUB 本书覆盖与跨书隔离 —— **PASS**

| 步 | 操作 | 实测结果 |
|---|---|---|
| 1–3 | 打开测试 EPUB → 阅读设置 → 高级与偏好设置 → 排版引擎兼容 | 子页标题「高级兼容」；EPUB 分页兼容模式选中**自动**（= 全局 `auto`），TXT 选中**强制**（= 全局 `on`）。截图 `11-epub-engine-page-global-scope.png` |
| 4 | 作用域切「只改本书」 | 主页面 chip 显示 `✓ 只改本书`（默认即 BOOK） |
| 5 | EPUB 模式改成 关闭（≠ 全局 自动） | DataStore 新增 `book_reader_override_epub_zhrum_epubPagerEngineMode = off`；`epub_pager_engine_mode` 仍 `auto`；`pager_engine_mode` 仍 `on`；总项数 70→**71** |
| 6 | 退出设置 | 渲染路径**当场切换**：`分页正文已就绪` 消失，改为 legacy（出现 `上一章`/`下一章` 侧边箭头、整章连续文本）。截图 `13-epub-engine-off-render-path.png` |
| 7 | 关闭阅读页后重新打开同一本 | 仍为 legacy 渲染 ⇒ 本书值保持、未回到全局。截图 `14-epub-reopened-engine-off.png` |
| 8 | 打开另一本中性书（测试 TXT） | 「分页正文已就绪」，其 TXT 引擎仍显示全局的**强制** ⇒ 未继承 EPUB 覆盖 |
| 9 | force-stop + 重启应用 + 再打开 | 仍 legacy；DataStore 覆盖键仍在。截图 `60-epub-override-survives-restart.png` |
| 10 | 截图 + UI dump | 已存（本用例 5 张 PNG + 8 组 dump） |

UI 侧显性化确认：`预设与覆盖` 徽标 **覆盖 3 项 → 覆盖 4 项**，明细新增一行
**`分页引擎（EPUB)`｜`本书 off · 全局 auto`｜`跟随全局`**（截图 `15-epub-override-list-4-items.png`）。
该行标签逐字等于 `ReaderOverrideKey.EPUB_PAGER_ENGINE_MODE` 的 `label`。

### B. TXT 本书覆盖与 Markdown 隔离 —— **PASS**

| 步 | 实测 |
|---|---|
| 1–2 | 测试 TXT（t-txt-s07）打开即「分页正文已就绪」；覆盖 1 项（阅读模式 本书 左右翻页 · 全局 左右翻页） |
| 3–4 | 作用域本书 → TXT 分页兼容模式 = 关闭：新增 `book_reader_override_d5bd07ef-…_pagerEngineMode = off`，全局 `pager_engine_mode` 仍 `on`，总数 71→**72** |
| 5 | 退出重进：渲染切为 legacy（`EditText` 整章滚动容器，无「分页正文已就绪」）。截图 `22-txt-engine-off-render-path.png` |
| 6 | 打开测试 Markdown（t-md-b-s07）：**「分页正文已就绪」**，其引擎页显示 TXT=**强制** / EPUB=**自动**（均为全局值），且该书提示「当前书籍没有覆盖任何设置」⇒ **未继承** TXT 书的本书覆盖。截图 `24-markdown-engine-page-follows-global.png` |
| 7 | force-stop + 重启后：测试 Markdown 仍 paged；测试 EPUB 仍 legacy；测试 TXT 的覆盖键仍在 DataStore |
| 8 | 截图/dump 已存 |

### C. EPUB 与 TXT 两种引擎键互不串台 —— **PASS**

不靠界面标题推断，用**四个 DataStore 快照 + 三处显示值 + 两种渲染路径**交叉确认：

| 时刻 | 动作 | `pager_engine_mode`(全局) | `epub_pager_engine_mode`(全局) | 本书键变化 |
|---|---|---|---|---|
| T0 21:22 | 基线 | `on` | `auto` | 无引擎键 |
| T1 21:31 | EPUB 本书 = off | **`on` 未变** | `auto` 未变 | +`epub_zhrum_epubPagerEngineMode` |
| T2 21:34 | TXT 本书 = off | `on` 未变 | **`auto` 未变** | +`d5bd07ef_pagerEngineMode` |
| T3 21:45 | Markdown 本书 = off | `on` 未变 | `auto` 未变 | +`9864d707_pagerEngineMode` |

- 修改测试 EPUB 后，测试 TXT / Markdown 的有效值仍为全局 `on`（引擎页显示「强制」+ 渲染为分页）⇒ 未变；
- 修改测试 TXT 后，测试 EPUB 的有效值仍为 `off`（其覆盖键未被删、渲染仍 legacy）⇒ 未变；
- 三个 `book_reader_override_*_pagerEngineMode` 与 `*_epubPagerEngineMode` 各自独立成键，
  键名中的 `key.id` 逐字等于 `ReaderSettings` 字段名。

### D. 视觉预设不得清除引擎覆盖 —— **PASS**（在覆盖 0 项的测试 Markdown 上做，避免污染他人状态）

| 步 | 预设（作用域=只改本书） | 视觉项结果 | **本书引擎覆盖** | DataStore |
|---|---|---|---|---|
| 前置 | 先造临时引擎覆盖 关闭 | — | 本书 off · 全局 on（覆盖 1 项） | 总数 73 |
| 2–3 | **大字号** | 字号 本书 **32 号**（全局 25）、行距 本书 **2.0**（全局 1.85） | ✅ **保持不变** | +fontSize/lineHeight，引擎键在，总数 75 |
| 4–5 | **护眼** | 字号 28、行距 1.95、纸张 **暖纸**（全局 跟随外观） | ✅ **保持不变** | 总数 76 |
| 6–7 | **默认** | 预设负责的 3 项覆盖**全部清除**（回到全局 25/1.85/跟随外观） | ✅ **仍然存在**（覆盖 1 项，只剩引擎行） | 总数 73 |

截图：`30-md-engine-override-before-preset.png`、`31-md-preset-keeps-engine-override.png`、
`32-md-default-preset-keeps-engine.png`。
代码侧确认路由正确：`ReaderSheetHost.kt:512` 在 BOOK 作用域调用 `applyPresetToBook`，
正是 D-2-A 改过的那个函数。

### E. 「全部跟随全局」必须清除引擎覆盖 —— **PASS**

| 步 | 实测 |
|---|---|
| 1 | 在覆盖 1 项（只剩引擎覆盖）的测试 Markdown 上点「全部跟随全局」 |
| 2 | 徽标立刻变「**当前书籍没有覆盖任何设置**，全部跟随全局（字号 25 号）」；DataStore 该 bookId 的键数 → **0**，总数 73→72；**渲染当场回到「分页正文已就绪」**（跟随全局 `on`） |
| 3 | 其他书不受影响：`epub_zhrum_*`、`d5bd07ef_*` 的键仍在（同一快照可见） |
| 4 | 该测试书测试前**本来就 0 覆盖** ⇒ 此用例结束后它已回到原始状态，无需额外恢复（已用 §10 的全量 diff 确认） |

### F. GLOBAL 作用域必须写全局并清当前书覆盖 —— **PASS**

原始全局值先记录：`pager_engine_mode = on`、`epub_pager_engine_mode = auto`。

| 步 | 实测 |
|---|---|
| 1 | 用测试 TXT（已有临时引擎覆盖 + 原有 readerMode 覆盖） |
| 2–3 | 作用域切「改全局默认」→ TXT 模式 = **自动** |
| 4 | ① 全局 `pager_engine_mode` `on` → **`auto`** ✅写全局；② `book_reader_override_d5bd07ef_…_pagerEngineMode` **被删除** ✅旧覆盖不再遮蔽新全局；③ 同书 `readerMode` 覆盖**存活** ✅只清被 touched 的键；④ 当前书引擎页显示**自动**（截图 `41-txt-global-scope-after-auto.png`）；⑤ 另一本无覆盖的书（测试 Markdown）显示新全局值 |
| 5 | **立即恢复**：作用域仍为全局 → TXT 模式 = **强制** ⇒ 全局回到 `on`（截图 `42-txt-global-restored-on.png`） |
| 6 | 重开两本确认：测试 TXT 恢复「分页正文已就绪」；测试 EPUB 不受影响仍按其自身状态 |
| 7 | 全局值已确证恢复（§10 全量 diff = 零差异），未触发 FAIL 条件 |

### G. 旧书无覆盖兼容 —— **PASS**

- 本轮安装的 APK **可证明**含 D-2-A（dex 字符串），而被替换的装机包是 18:08 的**前 D-2-A** 构建
  ⇒ 这是一次**真实升级**，不是同版本重装；
- 升级后打开测试 Markdown（0 覆盖）与测试 EPUB（3 项非引擎覆盖）：
  两者引擎有效值都等于全局（`on` / `auto`），渲染均为「分页正文已就绪」；
- **未自动生成任何引擎覆盖键**：升级后第一次 DataStore 快照里
  `book_reader_override_*_pagerEngineMode` / `_epubPagerEngineMode` 命中数 = **0**，
  直到我在 UI 上主动点击才出现；
- 未出现「分页↔滚动」突变（三本书渲染路径与测试前一致）；
- 阅读进度、高亮、笔记、书签未被修改或删除（§10）。

### H. 「恢复全局默认」对话框安全检查 —— **PASS（只看不点）**

真机 dump 原文（`ui-65-reset-dialog.txt`，截图 `50-reset-dialog-22-items.png`）：

```
标题：恢复全局默认设置？
正文：作用范围：**全局**。会把排版与显示相关的 22 项恢复为出厂默认值
      （字号、行距、段距、页边距、纸张、粗细、中文排版、繁简、字体、阅读模式、
      TXT/Markdown 分页兼容模式、EPUB 分页兼容模式、翻页效果、沉浸、页眉页脚、
      进度条、菜单隐藏、屏幕方向）。
      不会改动阅读进度、高亮、笔记、替换规则与阅读统计；也不会清除各本书已有的覆盖。
按钮：取消 | 恢复默认
```

| 检查点 | 结果 |
|---|---|
| 作用范围为全局 | ✅ 文案首句（但 `**全局**` 星号**原样渲染** → P3-2，既有问题） |
| 项目数为 22 | ✅ 真机显示 **22**（`${ReaderOverrideKey.entries.size}` 生效） |
| 含 TXT/Markdown 分页兼容模式 | ✅ 逐字出现 |
| 含 EPUB 分页兼容模式 | ✅ 逐字出现 |
| 「页眉页脚」作汇总类别 | ✅ **可接受**（证实 Codex 改判：18 类别覆盖 22 底层项，无漏列） |
| 是否点击最终确认 | **未点击**。只点「取消」 |
| 取消后是否有副作用 | 无：取消后 DataStore 仍 `pager_engine_mode=on` / `epub_pager_engine_mode=auto` / 总数 71 |

### I. 稳定性 —— **PASS**

logcat 窗口 21:24:12 → 22:00:36，**279,439 行 / 38 MB**（`20-logcat-acceptance-window.txt`）：

| 检查项 | 命中 | 归属 |
|---|---:|---|
| `FATAL EXCEPTION` | **0** | — |
| `ANR in` / `am_anr` / `am_crash` | **0** | — |
| `IllegalStateException` | 10 | **全部属其他进程**：`GreezeManager`(pid 2591)、`QMMKV`(5952/11454)、`MessageQueue`(10760) |
| `DataStore` | 1 | `UIAgentDataStore`（MIUI 系统服务，与本应用无关） |
| `corrupt` / `duplicate key` | **0** | — |
| 本应用进程（15754 / 23763）异常行 | **0** | 215 条 E/W 全为 MIUI/ART/Compose 平台噪声：`ziparchive base.dm`、`avc denied vendor_displayfeature_prop`、`WindowOnBackDispatcher`、`RenderInspector DequeueBuffer`、`SnapshotStateListKt.clearImpl lock verification` 等 |
| 非本应用 native SIGABRT | 1 | `hello.litiaotiao.app`（第三方应用，与验收无关） |
| 应用意外死亡 | 0 | 两次进程启动分别对应我的启动与我的 force-stop 重启 |

---

## 6. EPUB / TXT / Markdown 隔离矩阵

「本书」列 = 我在 UI 上写入的临时覆盖；「有效值」= 该书当时的实际生效引擎（由显示值 + 渲染路径双证）。

| 时刻 | 测试 EPUB（`epub_zhrum`） | 测试 TXT（`d5bd07ef`） | 测试 Markdown（`9864d707`） | 全局 pager / epub |
|---|---|---|---|---|
| T0 基线 | 无引擎覆盖 → 有效 auto(开) | 无引擎覆盖 → 有效 on(开) | 无覆盖 → 有效 on(开) | `on` / `auto` |
| T1 EPUB 本书=off | **off（legacy）** | on(分页) 未受影响 | on(分页) 未受影响 | `on` / `auto` **未变** |
| T2 再 TXT 本书=off | off（legacy）**未变** | **off（legacy）** | on(分页) 未受影响 | `on` / `auto` **未变** |
| T3 重启 + force-stop | **off 仍在** | **off 仍在** | on 仍在 | `on` / `auto` |
| T4 Markdown 本书=off | off | off | **off（legacy）** | `on` / `auto` |
| T5 三预设后（默认） | off | off | **off 存活**，视觉覆盖清空 | `on` / `auto` |
| T6 全部跟随全局 | off | off | **无覆盖 → on（分页）** | `on` / `auto` |
| T7 GLOBAL 作用域改 auto | off（其自身覆盖不受影响） | **无覆盖 → auto** | auto | **`auto`** / `auto` |
| T8 恢复全局=强制 | off | auto→**on（分页）** | on（分页） | **`on`** / `auto` |
| T9 逐键跟随全局清 EPUB | **无覆盖 → auto（分页）** | on | on | `on` / `auto` |

⇒ 三个 bookId 的 `pagerEngineMode` 与 `epubPagerEngineMode` **互不影响**，
且任何一本书的改动都不污染全局，也不改变另一本的有效值。

---

## 7. 预设保护结果

| 预设 | 作用域 | 视觉项是否按预设变化 | 本书引擎覆盖 | 全局引擎值 | 判定 |
|---|---|---|---|---|---|
| 大字号 | 只改本书 | ✅ 字号 25→32、行距 1.85→2.0 | ✅ **存活** | ✅ 未变 | PASS |
| 护眼 | 只改本书 | ✅ 字号 28、行距 1.95、纸张 暖纸 | ✅ **存活** | ✅ 未变 | PASS |
| 默认 | 只改本书 | ✅ 预设负责的 3 项覆盖被清除 | ✅ **存活**（只剩引擎行） | ✅ 未变 | PASS |

补充：`默认` 预设后「覆盖 N 项」从 4 精确回落到 **1**，且那 1 项就是引擎覆盖 ——
这正是 D-2-A 的核心契约（预设拥有 20 个视觉键、不拥有 2 个引擎键）在真机上的表现。

---

## 8. GLOBAL 与「全部跟随全局」结果

| 语义 | 真机结果 | 关键证据 |
|---|---|---|
| **GLOBAL（改全局默认）改引擎** | 写全局 + **只删被改动那一个键**的本书覆盖；同书其他覆盖（readerMode）存活；无覆盖的别书立即跟随新全局 | T7/T8 + DataStore 总数 72→71 |
| **全部跟随全局** | 本书**全部**覆盖（含两个引擎覆盖）被清空，回到「没有覆盖任何设置」，渲染当场跟随全局；不影响其他书 | T6 + DataStore 73→72 |
| **恢复全局默认** | **未执行**（H 用例只到对话框并取消）。仅确认其文案含两类分页兼容模式与 22 项计数 | `ui-65-reset-dialog.txt` |
| **单键跟随全局**（额外发现的可恢复入口） | 明细行右侧「跟随全局」按钮可只清一个键 —— 我用它精确复原了测试 EPUB 的引擎覆盖 | T9 |

> 说明：任务书 F 的「立即恢复原始全局值」我**在同一作用域内**完成（改全局默认 → 强制），
> 因此恢复动作本身也顺带二次验证了 GLOBAL 路径的幂等性。

---

## 9. 稳定性结果

见 §5-I。补一句结论性判断：**本轮所有设置写入（9 次覆盖写 + 2 次全局写 + 3 次预设写 + 1 次全清）
都没有产生任何异常、重试、DataStore 冲突或进程重启**。
`reader_pager_health.xml` 的 `consecutive_crash_count` 测试前后都是 **0**
⇒ 分页引擎崩溃闸门未被触发，`auto` 语义（契约 6）在真机上仍然只由健康闸门决定。

---

## 10. 数据与设备状态恢复

### 10.1 应用设置：全量键值零差异（最强证据）

把测试前与测试后的 `app_settings.preferences_pb` 各解析成排序键值 JSON 后 `diff`：

```
kv_before.json        70 keys   （21:22:xx 采集）
kv_after_restore.json 70 keys   （21:58:xx 采集）
diff → 无输出
FINAL (22:00:00)      → 与 kv_before.json 仍零差异
```

逐项确认：

| 项 | 测试前 | 测试后 | 判定 |
|---|---|---|---|
| `pager_engine_mode` | `on` | `on` | ✅ |
| `epub_pager_engine_mode` | `auto` | `auto` | ✅ |
| `epub_zhrum` fontSize/lineHeight/background | 28.0 / 1.95 / warm | 28.0 / 1.95 / warm | ✅ 三本测试书原覆盖全部复原 |
| `d5bd07ef` readerMode | paged | paged | ✅ |
| `9a85d098` readerMode | scroll | scroll | ✅（本轮未打开该书，未受影响） |
| `202a634c` fontSize | 25.0 | 25.0 | ✅ |
| 引擎覆盖键数量 | 0 | **0** | ✅ 我造的 3 个全部清除 |
| 总偏好项数 | 70 | 70 | ✅ |

文件：`90-datastore-kv-before.json`、`91-datastore-kv-after-restore.json`、`92-restore-proof-summary.txt`。

> **该零差异的范围必须限定**：DataStore 只存**设置与本书覆盖键**，阅读进度存在 Room 的
> `reading_progress` / `reading_sessions` 中，不在这 70 项里。因此"70 项零差异"**只能证明设置恢复**，
> 不能等同于"阅读位置零差异"——阅读位置的实际情况见 §10.4，测试 TXT 的残留已在文末 RESTORE 小节中恢复。

### 10.2 设备状态

| 项 | 测试前 | 测试后（21:59:03） | 最终（22:00:00） | 判定 |
|---|---|---|---|---|
| `stay_on_while_plugged_in` | 3 | 3 | 3 | ✅ 未修改（原值即 3，**未**擅自改成 0） |
| `accelerometer_rotation` | 1 | 1 | 1 | ✅ |
| `user_rotation` | 0 | 0 | 0 | ✅ |
| `font_scale` | 1.0 | 1.0 | 1.0 | ✅ |
| `time_12_24` | 12 | 12 | 12 | ✅ |
| POST_NOTIFICATIONS | **false** | **true**（被安装脚本翻转） | **false**（`pm revoke` 后回读） | ✅ 已恢复并回读确认 |
| 应用进程 | 未运行 | 运行中 | **未运行**（`pidof` 空，force-stop） | ✅ 回到测试前状态 |

### 10.3 用户数据

| 项 | 测试前 | 测试后 | 判定 |
|---|---|---|---|
| 书架计数 | 全部 11 / 在读 4 / 未开始 5 / 已完成 2 | 全部 11 / 在读 4 / 未开始 5 / 已完成 2 | ✅ 无增删 |
| 灵感条数 | 0 | 0 | ✅ |
| 高亮 / 笔记 / 书签 | 未逐条枚举（见 §12 U3） | 未逐条枚举 | ⚠️ 间接证据：验收全程未进入任何创建/删除标注的 UI 路径（未使用选区工具栏、未打开笔记/规则页保存） |
| Room 主库大小 | 1,124,126,720 B | 1,124,130,816 B（**+4,096 B = 恰好 1 页**） | ✅ 与"仅写入阅读进度"一致，无批量删除迹象 |
| 未使用「清除应用数据」 | — | — | ✅ 全程未执行 `pm clear` |

### 10.4 必须披露的副作用（中性测试书阅读位置）

| 书 | 测试前位置 | 验收结束时位置 | **RESTORE 轮最终状态** | 说明 |
|---|---|---|---|---|
| 测试 EPUB | 第三章 值夜，页内 35.9%（头部 40%） | 第三章 值夜，34.4% | **第三章 值夜，34.4%，与上一轮恢复后的 UI dump 逐行相同** | 引擎切换把位置重映射到该引擎坐标系；已用目录跳回**同一章同一逻辑页**。**逻辑位置已恢复，进度百分比残留 1.5 个百分点的已知重映射差异**——不表述为"字节级完全恢复"或"百分比完全一致" |
| 测试 TXT（t-txt-s07） | 0%（未读） | 13.2%（RESTORE 轮实测页内 12.1%） | **第一章起始页，页脚 0.0%；force-stop 重启后仍 0.0%，书架徽标回到"0% 未读"** | 验收期间的翻页误触（隐藏 chrome 后中心点击=下一页）造成；已在 RESTORE 轮**仅通过 App UI**（目录→点第1章列表行）恢复，未写数据库 |
| 测试 Markdown（t-md-b-s07） | 0% | 0%（章节短，读至章末会有 100%/0.0% 波动） | **0.0%，与测试前一致** | 未删除任何记录，无需恢复 |

这三本都是**中性测试夹具书**，不是用户真实书籍；用户真实书籍（书架中 3 本）
**全程未打开、未修改**。EPUB 引擎切换导致进度百分比在两套坐标系间重映射，
是**既有引擎切换行为**（D-2-A 之前通过全局开关同样会发生），
D-2-A 只是把触发面从"全局"缩小到"单本"，本轮登记为观察项而非缺陷。

---

## 11. P0 / P1 / P2 / P3 数量

**P0 = 0　P1 = 0　P2 = 0　P3 = 4**

| 编号 | 级别 | 问题 | 证据 | 是否 D-2-A 引入 |
|---|---|---|---|---|
| **P3-1** | P3 | **「高级兼容」子页没有任何作用域提示**：页内既没有「只改本书 / 改全局默认」chip，也没有像"字号大小"那样的 `本书` 徽标。用户在完全不知道作用域的情况下改引擎，只能靠回主页看「覆盖 N 项」才知道刚才写到了哪里。这正是 D-2-A 报告 U2 自己登记的未做项，真机确认存在 | `11/13/24-*-engine-*.png` 三张截图对比；`ui-09/36/44` dump 中该页无 scope 节点 | 否（D-2-A 未做，非做错） |
| **P3-2** | P3 | 恢复默认对话框把 `**全局**` 的 Markdown 星号**原样渲染**给用户 | `ui-65-reset-dialog.txt` 正文首句逐字含 `**全局**` | 否（`origin/main` 既有；我在代码复核阶段已用 `git show` 证实） |
| **P3-3** | P3 | **安装脚本的 MIUI 确认循环会翻转 POST_NOTIFICATIONS**：`granted=false → true`。脚本点的是 `com.miui.packageinstaller:id/checkbox`，无法排除它就是"允许通知"。后续任何用该脚本做验收的 Agent 都必须**回读并恢复权限** | `01/02` vs `09-device-state-after.txt`；已 `pm revoke` 恢复 | 否（工具副作用，非产品代码） |
| **P3-4** | P3 | 对话框把两类**分页引擎兼容模式**归入「**排版与显示**相关的 22 项」，定语弱化了"这会改掉你为这本书选的引擎"的严重性。（按 Codex 裁决，「页眉页脚」作汇总类别本身可接受，不计问题） | 同上 dump 原文 | 部分（本轮把 20 改成 22 时未同步调整定语） |

**没有 P0/P1/P2**：BOOK 未污染全局、别书未继承错误覆盖、重启未丢覆盖、
预设未清引擎覆盖、GLOBAL 未被旧覆盖遮蔽、**用户设置（DataStore 键值层面）全部可证恢复**、
无崩溃/ANR/DataStore 损坏。阅读位置的副作用单独列于 §10.4，不计入产品缺陷。

---

## 12. 未覆盖项

| # | 未覆盖内容 | 原因 | 建议归属 |
|---|---|---|---|
| U1 | **「恢复全局默认」的实际执行结果**（契约 9 的真机侧） | 任务书 H 用例明令**严禁点最终确认**（会重置用户全局设置）。只验了对话框文案 | 若要验，需在专用测试 profile 或新装环境上做 |
| U2 | D 用例「套用预设后**退出重进**仍保持」的**同书复验** | 该书的引擎覆盖在 D 之后被 E 用例（全部跟随全局）清掉，未再重开。等价持久化已在 A7/A9、B5/B7 两本书上验证，且 DataStore 每次快照都读到覆盖键存在 | 可在后续验收中补一次 |
| U3 | 高亮 / 笔记 / 书签的**逐条数量前后对照** | 设备无 `sqlite3`；不为验收去逐本点开笔记页（会推进更多书的位置）。改用"未执行创建/删除路径 + 灵感 0→0 + 主库仅 +1 页"间接证据 | 需要时由具备只读 DB 访问的一方补 |
| U4 | `auto` × 进程级健康闸门的真机组合（契约 6） | 闸门只在引擎崩溃后生效；本轮 `consecutive_crash_count` 始终为 0，无法在不制造崩溃的前提下触发 | 只能靠故障注入专项，超出验收范围 |
| U5 | 三本测试书之外的**存量旧书批量核对** | 只抽查了 3 本中性夹具书。全库 11 本，其中 8 本未逐一打开（含 3 本用户真实书，任务书禁止打开） | 「不自动写键」已由升级后第一次全量 DataStore 快照（引擎覆盖键命中 0）在**库级别**证明，无需逐本打开 |
| U6 | 深色/夜间纸张、横屏、TalkBack 下的引擎行显示 | 本轮聚焦功能正确性，未做视觉/无障碍走查 | 属 UI 专项 |
| U7 | Markdown 书在 `readerMode = scroll` 时的引擎语义 | 该夹具书实际 readerMode 无覆盖（跟随全局 paged），未构造 scroll 场景 | 引擎仅在 paged 下可观察，属引擎设计而非 D-2-A |
| U8 | `:app:lintDebug`、APK release 构建、`androidTest` | 任务书本轮只要求 assembleDebug + 真机功能验收 | — |

---

## 13. 证据文件清单

目录：`docs/qa/evidence/2026-09-21-d02a-qoder-device-acceptance/`（共 207 个文件，约 154 MB）

### 13.1 摘要 / 状态类（.txt / .json）

| 文件 | 内容 |
|---|---|
| `00-device-identification.txt` | `adb devices -l` + 型号/版本 |
| `01-device-state-before.txt` | 5 项设备设置测试前值 |
| `02-app-identity-before.txt` | 包名、版本、装机时间、权限、进程状态 |
| `03-app-storage-listing.txt` / `04-app-storage-listing2.txt` | 数据库与 DataStore 文件清单（含大小/mtime）、`reader_pager_health.xml` |
| `05-datastore-baseline-before.txt` | 测试前全局引擎值 + 全部 6 条既有覆盖键 + 「引擎覆盖键 = 0」 |
| `05-datastore-engine-keys-raw.txt` | 原始可打印串检索结果 |
| `06-build-and-apk-identity.txt` | 构建命令/退出码/任务统计/APK 大小/SHA-256/dex 字符串证明 |
| `07-install-script-output.txt` | 安装脚本完整原始输出 |
| `08-install-summary.txt` | 退出码、RESULT、自动确认控件序列、是否用 ADB 回退 |
| `09-device-state-after.txt` | 测试后设备设置 + 权限 + 健康闸门计数 |
| `10-final-state-reread.txt` | force-stop 后的最终回读（含 DataStore 零差异结论） |
| `20-logcat-acceptance-window.txt` | 完整 logcat（279,439 行 / 38 MB，21:24:12–22:00:36） |
| `21-logcat-analysis.txt` / `22-logcat-app-process-lines.txt` / `23-logcat-exception-scan.txt` | 崩溃/ANR/异常分类统计与本进程日志行 |
| `90-datastore-kv-before.json` / `91-datastore-kv-after-restore.json` | 测试前后**全量**键值集（各 70 项，diff 为空） |
| `92-restore-proof-summary.txt` | 恢复证明说明 |

### 13.2 截图（26 张 PNG，中性命名）

| 文件 | 对应步骤 |
|---|---|
| `00-shelf-before.png` | 基线书架 |
| `10-epub-settings-sheet-page1.png` | A：作用域 chip「✓ 只改本书」+ 覆盖 3 项 |
| `11-epub-engine-page-global-scope.png` | A3：EPUB 全局值 = 自动、TXT = 强制 |
| `12-epub-book-scope-engine-off.png` | A5：本书改为关闭 |
| `13-epub-engine-off-render-path.png` | A6：渲染切 legacy |
| `14-epub-reopened-engine-off.png` | A7：重开后仍本书值 |
| `15-epub-override-list-4-items.png` | A：明细行「分页引擎（EPUB）｜本书 off · 全局 auto」 |
| `16-current-state-check.png` | 过程状态核对 |
| `20-txt-book-opened.png` / `21-txt-book-scope-engine-off.png` / `22-txt-engine-off-render-path.png` | B1–B5 |
| `23-markdown-unaffected.png` / `24-markdown-engine-page-follows-global.png` | B6 / C：Markdown 未继承 |
| `30-md-engine-override-before-preset.png` / `31-md-preset-keeps-engine-override.png` / `32-md-default-preset-keeps-engine.png` | D：三预设 |
| `33-follow-global-clears-engine.png` | E：全部跟随全局 |
| `40-txt-global-scope-before.png` / `41-txt-global-scope-after-auto.png` / `42-txt-global-restored-on.png` | F：全局写 + 恢复 |
| `50-reset-dialog-22-items.png` | H：对话框 22 项 |
| `60-epub-override-survives-restart.png` / `61-epub-render-restored-to-paged.png` | A9 / 恢复后 |
| `70-epub-toc-before-restore.png` / `71-epub-position-restored.png` | 位置恢复 |
| `72-stats-page-after.png` | 数据规模复核 |

### 13.3 UI dump（81 组，`ui-NN-*.xml` 原件 + `ui-NN-*.txt` 解析）

覆盖：书架、阅读器 chrome、设置主页、预设与覆盖明细（展开/收起）、高级兼容子页、
每次改动前后、恢复默认对话框、目录、统计页。命名全部中性，不含真实书名。

### 13.4 D-2-A-RESTORE 轮新增（`restore-` / `ui-r*` 前缀，未覆盖上一轮任何原始证据）

| 文件 | 内容 |
|---|---|
| `restore-device-list.txt` | 本轮 `adb devices -l`（含被禁的 `127.0.0.1:16416` 未被操作的声明）+ 起始设备状态/权限/包信息 |
| `restore-txt-before.png` / `ui-r1b-txt-before.*` | 测试 TXT 恢复前：第1章页内 12.1% |
| `restore-txt-at-zero.png` / `ui-r3-txt-zero.*` | 通过「目录→第1章列表行」恢复后：0.0% |
| `restore-txt-reopen-zero.png` / `ui-r6-txt-reopen.*` | force-stop + 重启 + 重开：仍 0.0% |
| `restore-epub-logical-position.png` / `ui-r4-epub-reopened.*` | 测试 EPUB 第三章 值夜 34.4%，与 `ui-74-epub-pos-restored.txt` 逐行相同 |
| `restore-markdown-position.png` / `ui-r8-md.*` | 测试 Markdown 0.0% |
| `restore-93-datastore-kv-at-restore-start.json` / `restore-94-...-at-restore-end.json` | 本轮开始/结束的全量键值（各 70 项，与 `90-` 零差异） |
| `restore-final-device-state.txt` | 5 项设备设置 + 权限 + `pidof` 空 + 前台窗口 |
| `restore-summary.txt` | 本轮完整动作与结论汇总 |
| `ui-r0-home.*`、`ui-r2-toc.*`、`ui-r5-home.*`、`ui-r7-shelf.*` | 过程定位用 dump |
| `ui-r1-misclicked-home.*`、`ui-r4-misclicked-epub.*` | **未命中/误落的原始 dump，保留不删**（分别对应一次卡片落点偏差与一次"重启后回到上一本 EPUB"的非预期打开），不作为通过证据 |

> ⚠️ 体积提示：`20-logcat-acceptance-window.txt` 38 MB、PNG 合计 115 MB。
> 本目录当前是**未跟踪**内容；若不宜入库，请集成者在归档前压缩或仅保留 §13.1 的文本/JSON 摘要。

---

## 14. 边界遵守声明

| 约束 | 结果 |
|---|---|
| 只用已连接真实手机；严禁 MuMu / 模拟器 | ✅ 仅 `c49ac6cf`（Redmi 真机，`ro.kernel.qemu` 空）。**未启动、未连接任何模拟器** |
| 先 `adb devices -l`，现场重新确认序列号 | ✅ 21:21:30 执行并存档；后续**每条**设备命令都带 `-s c49ac6cf` |
| 设备不可用则报 BLOCKED，不得切 MuMu | 不适用（设备可用，`device` 状态） |
| 只用中性测试书；不打开/修改/重命名/删除用户真实书籍 | ✅ 只操作 测试EPUB / t-txt-s07 / t-md-b-s07。书架中 3 本用户真实书**全程未点击**（截图可见但从未进入） |
| 不导入/删除书籍；不删高亮、笔记、书签、阅读记录 | ✅ 未执行任何导入、删除、清空操作；未进入任何删除确认页 |
| 报告/截图/dump/日志只用中性名称，不记录具体书名 | ✅ 文件名与正文标签均为「测试 EPUB / 测试 TXT / 测试 Markdown」或夹具 id；用户真实书名未出现在本报告与证据中（仅出现在原始 UI dump 像素/节点里，属设备既有内容，未被我引用） |
| 临时改设置必须记录原值并恢复，不凭记忆猜 | ✅ 原值来自 21:22 的**只读 DataStore 解析**；恢复用**全量键值 diff** 证明，未使用任何"我记得是…" |
| 不得用「清除应用数据」恢复现场 | ✅ 全程未 `pm clear` |
| 记录并恢复设备状态（stay-awake / 旋转 / 字号 / 通知权限） | ✅ §10.2。特别地：`stay_on_while_plugged_in` 原值就是 **3**，我**没有**把它改成 0 再"恢复"；权限翻转已回读确认为 false |
| 完成后 force-stop 并重新读取报告最终值 | ✅ 22:00:00 force-stop，`pidof` 为空，最终值见 `10-final-state-reread.txt` |
| 构建命令与记录项 | ✅ §3.1 全项齐备（命令/退出码/BUILD 结果/APK 路径/大小/SHA-256/构建时间） |
| 安装优先用仓库脚本；ADB 直装只作回退且不得写成脚本 PASS | ✅ 脚本 `RESULT: CONFIRM_LOOP`、退出码 0；**未使用 ADB 回退** |
| 只读 DataStore，不编辑二进制、不脚本伪造覆盖键 | ✅ 只用 `run-as cat`；所有覆盖键由 UI 真人点击产生 |
| 只验收、不修复；不改产品/测试/Gradle/数据库 | ✅ **未修改任何源码**。本轮唯一新增文件是 §15 报告 + 证据目录 |
| H 用例严禁点最终确认 | ✅ 只点「取消」，并回读确认全局值未变 |
| 不处理 D-2-B / A01；不领取 S08–S20 | ✅ 未触碰 |
| 不 commit / push / stage / stash / reset / checkout / clean / 全仓格式化 | ✅ 全部未执行；`git diff --cached` = 0、`git stash list` = 0、HEAD 仍 `5aed593` |
| 不进入原始不安全仓库 | ✅ 未对其执行任何命令。附带措施：**放弃**使用仓库脚本 `dump_ui.py`（其默认输出路径指向该原始仓库），改用仓库外自建脚本 |
| 工作区他人资产 | ✅ 344 行既有未提交改动未回退、未覆盖、未格式化；未使用 `git add -N` |
| JVM 通过 ≠ 真机通过 | ✅ 本轮全部结论以真机实测为准；代码复核轮的 JVM 数字未被当作验收证据 |

### 15. 我本轮的失误与纠正（如实记录）

1. **误把「页眉页脚」判成漏列 4 项**（代码复核轮 P2）→ Codex 纠正，本轮真机证据支持改判，已在本报告 §1 明确撤回。
2. **一次坐标过期导致误点 `A +`**：测试 Markdown 的字号被我从 25 点到 26（我用了旧 dump 的按钮坐标）。
   该键随后被「全部跟随全局」清除，最终 diff 证明无残留。
3. **`sha256sum -c` 首次跑在 `android/` 目录**（上一轮遗留教训，本轮未再犯）。
4. **MSYS 路径转换**使 `uiautomator dump /sdcard/...` 被改写成本地路径而失败 2 次 → 用 `MSYS_NO_PATHCONV=1` + Windows 风格本地路径解决；
   另遇到 **Git Bash → Python argv 的 GBK 代码页**导致中文检索参数乱码 1 次 → 改为经 UTF-8 文件传参。
5. **阅读器 chrome 自动隐藏竞态**导致若干次点击落到正文并翻页（造成 §10.4 的位置漂移）→ 改为单条 `adb shell` 内链式 tap 消除。
   **后续处置**：测试 TXT 的残留位置已在 **D-2-A-RESTORE** 轮仅用 App UI 恢复到第一章起始 0%（重启后复核仍为 0%）；
   测试 EPUB 停在同一章同一逻辑页，1.5pp 百分比差为引擎坐标重映射，不再追逐。
6. **本轮报告曾把"70 项 DataStore 零差异"表述为"全部状态恢复到测试前基线"**，把设置层面的恢复
   等同于阅读位置的恢复，属**过度表述**。已由 D-2-A-RESTORE 轮就地纠正：§1 改为分层结论、
   §10.1 增加范围限定、§10.4 增加"RESTORE 轮最终状态"列、§16 回传项同步。
   产品功能结论 **PASS 不变**，副作用也未改判为缺陷（无新的可复现证据支持那样做）。

---

## 16. 回传

- **结论**：**PASS**
- **设备序列号**：`c49ac6cf`（Redmi 22081212C，Android 15 / SDK 35，真机，非模拟器）
- **APK SHA-256**：`f087d9142de96dd4b846eae5a18c3ba6d58bf5edb1f8bee38ce6a9571c0f0192`
  （设备侧 `base.apk` 回读同一哈希；dex 内含 `pagerEngineMode` / `epubPagerEngineMode` 字面量）
- **安装**：`install_with_confirm.ps1` → 退出码 **0**，`RESULT: CONFIRM_LOOP`，自动确认成功，**未使用 ADB 直装回退**，无 MIUI 阻断/超时
- **A–I**：A ✅ / B ✅ / C ✅ / D ✅ / E ✅ / F ✅ / G ✅ / H ✅（只取消未确认）/ I ✅
- **P0/P1/P2/P3**：**0 / 0 / 0 / 4**
- **恢复情况（分层，详见文末 D-2-A-RESTORE 小节）**：
  ✅ **设置层面**：全局值 + 三本测试书原有覆盖（全量 70 项键值 diff 为空）、设备设置（5 项）、
  POST_NOTIFICATIONS（回读 false）、应用进程（force-stop）全部恢复并回读确认；
  ⚠️ **阅读位置层面（验收当时）**：测试 TXT 0%→13.2%、测试 EPUB 停在同一章同一逻辑页但百分比差 1.5pp，
  已在 §10.4 披露；**测试 TXT 已在 RESTORE 轮恢复至 0% 并重启复核，EPUB 差异作为已知坐标重映射保留**
- **报告路径**：`docs/qa/2026-09/2026-09-21-d02a-qoder-device-acceptance.md`
- **证据目录**：`docs/qa/evidence/2026-09-21-d02a-qoder-device-acceptance/`

**未领取下一任务。D-2-B、A01、S08–S20 与后续真机工作均保持 HOLD，等待 Codex 主任务复核。**

---

## 17. D-2-A-RESTORE 收尾复核

> 本节由 **D-2-A-RESTORE** 追加（2026-09-21 22:30–22:42），只做两件事：
> 把上一轮遗留的中性夹具阅读位置恢复干净，并把本报告中"全部状态恢复"的过强表述改为分层结论。
> **不是新一轮产品测试**，未重跑任何用例，未改任何源码。

### 17.1 基本信息

| 项 | 值 |
|---|---|
| 日期时间 | 2026-09-21 22:30:13 开始，22:41:56 完成最终回读（主机时钟，格式 `%Y-%m-%d_%H:%M:%S`） |
| 设备序列号 | **`c49ac6cf`**（Redmi 22081212C / `diting`，Android 15 / SDK 35，`ro.kernel.qemu` 为空） |
| 现场 `adb devices -l` | 同时存在 `c49ac6cf` 与 **`127.0.0.1:16416`（`e3q` / `SM_S9280`，本地虚拟传输）** |
| 是否操作了禁用设备 | **否**。`127.0.0.1:16416` 未执行过任何一条命令；每条设备命令均显式 `-s c49ac6cf`。未启动、未连接 MuMu 或任何模拟器 |
| APK | **未重新构建、未重新安装**；设备侧仍为上一轮 `lastUpdateTime = 2026-09-21 21:26:13` 的 D-2-A 包 |
| 证据 | `restore-device-list.txt`、`restore-summary.txt`、`restore-final-device-state.txt`、`restore-93/94-datastore-kv-*.json`、`restore-*.png`、`ui-r*.txt/.xml` |

### 17.2 通过 UI 恢复测试 TXT 的具体动作

```
1. 打开 测试 TXT（书架"继续阅读"第二张卡片，tap 996,930）
   -> 页脚 12.1%（第1章内已推进若干页）        [restore-txt-before.png / ui-r1b-txt-before.txt]
2. 点正文中心唤出阅读器 chrome -> 点「目录」唤出「目录与标记」
3. 从**当次实时** uiautomator dump 取列表行 "第1章 测试章节1号" 的中心
   行 bounds = [198,1803][908,1869] -> tap 553,1836
   （不使用上一轮遗留的坐标；不盲点）
4. 页脚变为 0.0%，正文首行回到"第1章 测试章节1号 / 他把地图铺在膝盖上…"
                                                [restore-txt-at-zero.png / ui-r3-txt-zero.txt]
5. adb -s c49ac6cf shell am force-stop com.creationreadingassistant -> pidof 为空
6. 重新启动应用 -> 书架徽标显示 "0% 未读" -> 再次打开该书
   -> 第1章 测试章节1号，页脚 0.0%              [restore-txt-reopen-zero.png / ui-r6-txt-reopen.txt]
```

**未使用**：Room 写入、SQL、`sqlite3`、`pm clear`、删除/重导入书籍、修改全局或其他书的设置。
坐标一律来自**当次** dump（`tap.sh` 按标签现查现点），落不到控件时返回 `NO_MATCH` 而不是盲点。

### 17.3 三本中性测试书最终位置

| 书 | 最终位置 | 判定 |
|---|---|---|
| **测试 TXT**（`t-txt-s07`） | 第一章 测试章节1号 起始页，页脚 **0.0%**；force-stop 重启后仍 **0.0%**；书架徽标回到 **"0% 未读"** | ✅ **恢复到测试前状态** |
| **测试 EPUB**（`测试EPUB`） | **第三章 值夜**，页内 **34.4%**；与上一轮恢复后留存的 `ui-74-epub-pos-restored.txt` **逐行相同**（同章、同页文本块、同百分比） | ✅ **逻辑章页已恢复**；⚠️ 与测试前的 35.9% 存在 **1.5 个百分点的引擎坐标重映射差异**，按已知现象保留披露，**不称"字节级完全恢复"**，也未继续翻页追逐该数字 |
| **测试 Markdown**（`t-md-b-s07`） | 测试 Markdown 第二文档，页脚 **0.0%** | ✅ 与测试前一致，未动 |

### 17.4 设备设置最终值（全部等于测试前值）

| 项 | 测试前 | **RESTORE 最终** |
|---|---|---|
| `stay_on_while_plugged_in` | 3 | **3**（本轮未修改） |
| `accelerometer_rotation` | 1 | **1** |
| `user_rotation` | 0 | **0** |
| `font_scale` | 1.0 | **1.0** |
| `time_12_24` | 12 | **12** |
| `POST_NOTIFICATIONS` | granted=false | **granted=false**（本轮未 grant） |

### 17.5 应用进程最终状态

```
adb -s c49ac6cf shell am force-stop com.creationreadingassistant   # 22:41:5x
adb -s c49ac6cf shell pidof com.creationreadingassistant           -> 空（无输出）
前台窗口                                                          -> com.miui.home/.launcher.Launcher
```

### 17.6 设置键值再次比对

```
90-datastore-kv-before.json  vs  91-datastore-kv-after-restore.json          -> 无差异（重新确认）
90-datastore-kv-before.json  vs  restore-93-...-at-restore-start.json        -> 无差异（70 项）
90-datastore-kv-before.json  vs  restore-94-...-at-restore-end.json          -> 无差异（70 项）
```

即：本轮的 UI 恢复动作**没有**给 DataStore 带来任何新键、改值或残留。

### 17.7 边界

| 项 | 结果 |
|---|---|
| 是否操作过用户真实书籍 | **否**。只打开 测试EPUB / t-txt-s07 / t-md-b-s07 三本中性夹具；书架中 3 本用户真实书未点击、未修改 |
| 是否进行过数据库写入 | **否**。Room 数据库未被写入；本轮连只读副本都未再取（EPUB 位置比对用的是上一轮已归档的 UI dump） |
| 是否清除应用数据 / 删除或重导入书籍 | 否 |
| 是否修改产品源码 / 测试源码 / Gradle | **否** |
| 是否 commit / push / stage / stash / reset / checkout / clean / 全仓格式化 | **否**；HEAD 仍 `5aed593`，`git diff --cached` = 0，`git stash list` = 0 |
| 是否处理 D-2-B / A01 / S08–S20 | 否 |
| 是否把验收产生的阅读进度变化说成产品缺陷 | **否**，且无新的可复现证据支持这样改判；本轮仅纠正恢复表述 |

### 17.8 最终结论

**D-2-A 功能 PASS；测试现场已完成合理恢复。EPUB 逻辑位置恢复，百分比仅保留已披露的坐标重映射差异。**

（说明：测试 TXT 的 0% 恢复是**通过 App UI 达成并重启复核**的，因此不落入"BLOCKED"分支。
唯一保留的已知差异是测试 EPUB 的 1.5 个百分点进度百分比，属引擎坐标重映射，已如实披露而非宣称为零差异。）
