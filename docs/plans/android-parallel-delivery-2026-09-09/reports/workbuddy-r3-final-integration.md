# WorkBuddy R3 第三轮最终集成 — 独立真机与视觉验收报告

报告时间：2026-09-14 03:00–04:10 (GMT+8)
验收角色：只验收、不修复、不改代码（本报告为唯一产出物；辅助脚本 `ui.py` 仅写入 `output/android-r3-gate/` 证据目录，未触碰产品源码）

---

## 0. 结论速览（四类结论，互不替代）

| 结论类别 | 判定 |
|---|---|
| 冻结候选开发门禁通过 | ✅ 引用任务书给定证据（259 suites / 2,283 tests / 0F0E0S；lint 0E/5W；assembleDebug EXIT 0；compileDebugAndroidTestKotlin EXIT 0；`git diff --check` EXIT 0）。本次未复跑构建。 |
| **自动安装脚本 PASS** | ✅ `RESULT: CONFIRM_LOOP` + `SCRIPT_EXITCODE=0`，经 MIUI 安装器 UI 自动点击完成安装 |
| **direct ADB FALLBACK** | ❎ 未使用（脚本一次通过，无回退） |
| **真机验收** | ⚠️ **PASS（带条件）**：核心旅程全部 PASS；**2.2 Room 迁移执行 = BLOCKED**（缺少受控旧版本 fixture）；另有若干未覆盖项与 2 个低置信疑似 UI 问题，详见 §5/§6 |

---

## 1. 受测对象

- **受测 APK 绝对路径**：`D:\develop\Code\Codex\cra-g0-gate\android\app\build\outputs\apk\debug\app-debug.apk`
- **APK SHA-256**：`0e1a80029977df00fea5ed280f43e4a201f162de17da6206d45be9781975a522`（与任务书给定值一致，安装前核验通过）
- **APK 大小**：51,092,812 bytes（一致）
- **受测 commit**：`fd7f908e19c2ee2269103333804bdd5927201fd2`（`test(android): supply context to repository persistence tests`）
- **提交链核对**（`git log` 逐个确认，均为 2026-09-14 提交）：
  - `30cf235` ReaderCorrection 数据层，v12→v13
  - `e3fafad` R3 来源索引，v13→v14
  - `5cfb84c` 安装脚本与契约测试
  - `99a8c03` Reader、词典、阅读/选区设置
  - `066e50b` Home / Profile / Inspiration 消费侧
  - `eebd59d` Shelf UI、动态视图、导入面板、路由收口
  - `fd7f908` AndroidTest context 契约补齐
- **APK 自身元数据**（output-metadata.json）：applicationId `com.creationreadingassistant`，versionCode 2，versionName 0.5.0
- **候选声明的库版本**（源码只读核对）：`APP_DATABASE_SCHEMA_VERSION = 14`（AppDatabase.kt 顶层常量）

## 2. 设备与安装

### 2.1 设备信息

- **adb devices 输出（验收起点实测）**：
  ```
  List of devices attached
  c49ac6cf               device product:diting model:22081212C device:diting transport_id:6
  ```
- **实际 serial**：`c49ac6cf`（先 `adb devices` 确认后才使用；所有命令均显式带 `-s c49ac6cf`）
- **设备型号 / Android / ROM**：Xiaomi 22081212C（Redmi K50 Ultra / diting），Android 15（SDK 35），MIUI/HyperOS V816，build `AQ3A.250226.002`，时区 Asia/Shanghai，分辨率 1220×2712 / 480dpi

### 2.2 安装脚本（唯一安装方式）

命令（在仓库根执行）：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\android\scripts\install_with_confirm.ps1 `
  -Serial c49ac6cf `
  -Apk "D:\develop\Code\Codex\cra-g0-gate\android\app\build\outputs\apk\debug\app-debug.apk"
```

完整输出（全文见 `output/android-r3-gate/install_script_output.log`，UTF-16LE 原样保存）：

```text
adb devices entries=1; serial 'c49ac6cf' listed as device: True
...app-debug.apk: 1 file pushed, 0 skipped. 37.6 MB/s (51092812 bytes in 1.297s)
/sdcard/cra-install-ui-5f2b4cba57a14f09937349a2c631f5af.xml: 1 file pulled ...
TAP installer control 'android:id/button3' at 351,2522
TAP installer control 'android:id/button3' at 351,2522
TAP installer control 'com.miui.packageinstaller:id/checkbox' at 610,2008
TAP installer control 'com.miui.packageinstaller:id/second_button' at 610,2356
RESULT: CONFIRM_LOOP (installed through the MIUI installer UI)
Installed com.creationreadingassistant successfully.
SCRIPT_EXITCODE=0
```

- **自动安装判定：PASS**（同时满足 `RESULT: CONFIRM_LOOP` 与 `SCRIPT_EXITCODE=0` 两项判据）
- 安装后核验：`lastUpdateTime=2026-09-14 02:57:44`，versionCode=2 / versionName=0.5.0
- **FALLBACK（direct ADB install）：未发生、未使用**
- 未要求用户手动点击 MIUI 确认，未修改 MIUI 安全设置
- 附注：安装前设备已存在同包名旧安装（firstInstallTime 2026-09-13 18:56，lastUpdateTime 2026-09-13 23:22，含用户 1 本书与阅读数据），本安装为覆盖安装（`-r` 语义由脚本内部处理），**未清除应用数据**

## 3. 验收旅程明细

### 2.1 冷启动与基础稳定性 — ✅ PASS

- 冷启动：`am start -W` → `LaunchState: COLD`，TotalTime 1277ms，Status ok
- 前后台切换（HOME → 重进）、返回键后台化（主页 BACK → moveTaskToBack，进程存活 pid 18887）、重进渲染正常
- 全程 logcat（`60-logcat-final-fullsession.txt`，118,135 行）：`FATAL EXCEPTION` 计数 **0**；`am_crash`/`am_anr` 计数 **0**；crash 缓冲区 **空**（命中的 `AndroidRuntime` 行均为 shell 侧 uiautomator 进程，uid 2000，非应用崩溃）
- 无 UI 空白、无导航死路

### 2.2 Room 迁移边界（v12→v13 / v13→v14）— ⛔ BLOCKED

**BLOCKED：缺少受控旧版本数据库迁移 fixture**

- 安装前只读取证（`adb exec-out run-as` 拉取，未写入）：设备现有库 `user_version = 14`，且已含 `reader_text_corrections`（v13 表）与 `library_source_refs`（v14 表），`PRAGMA integrity_check = ok`
- 因此覆盖安装**不会触发** v12→v13 / v13→v14 迁移执行；为制造迁移条件而清除应用数据违反本次边界（不得破坏用户数据），故不执行
- 未使用「AndroidTest 已编译」替代运行时迁移验收（cra-g0-gate 内也无现成 androidTest APK；运行 gradle 构建超出本验收授权）

**已取得的非替代性旁证（如实区分，不算迁移执行 PASS）**：

| 证据 | 内容 |
|---|---|
| 库版本一致性 | 候选声明 v14 = 设备现值 v14 |
| v14 schema 兼容性 | 覆盖安装后冷启动成功且全书架可用 → Room 打开时对全部 28 张表做 schema 哈希校验通过，说明候选 v14 期望 schema 与设备现存 v14 逐表一致 |
| 表结构形状 | 设备上两表的列/索引与提交 `30cf235`/`e3fafad` 引入的定义形状一致（证据：pre.db dump + 13.json/14.json 对照） |
| 历史旁证 | v12→v13→v14 迁移曾在 2026-09-13 23:50 前后由先前安装的构建在本机执行成功（DB 已升至 14），但那不是本冻结候选的运行时证据 |

### 2.3 R3 书籍来源与目录旅程 — ✅ PASS（含 2 项未覆盖）

fixture（隔离目录、中性命名）：`/sdcard/Download/cra-r3-gate-fixture/`
- `测试小说集/测试文本甲.txt`（139 KB，120 章，基线 MD5 `42b18d6e...`）
- `测试小说集/子目录/测试文本乙.txt`（23 KB）
- `测试大文件/测试大文件丙.txt`（4.5 MB）
- 推送注意：`adb push` 直接推送中文路径目录会挂起（实测 5 分钟无进展，已终止）；改用 ASCII 临时名推送 + 设备端 `mv` 成功。此为环境坑，非产品问题。

| 项目 | 判定 | 证据 |
|---|---|---|
| 目录授权 | PASS | 「我的书籍目录 → 更换」→ SAF 树选择器导航至 Download/cra-r3-gate-fixture → 系统授权弹窗「允许」（13–17 号截图） |
| App 内目录浏览 | PASS | 根卡片、面包屑、排序（名称/修改时间/大小）、文件夹逐级导航均正常 |
| 导入后「已入架」 | PASS | 浏览页出现「已在书架」徽标；智能识别给出「推荐导入 · 高置信 · 样本 56 章 · 正文可正常解码 · 多个可靠章节标题」；DB `library_source_refs` 正确落库（root_id=`primary:Download/cra-r3-gate-fixture`，document_id 指向源文件，size=142591，availability=available）；导入面板显示「共解析 1 本，成功入库 1 本」 |
| 来源内容更新识别 | PASS | 向源文件追加 2 章后重新识别 → 徽标变为「**内容有更新**」（26 号截图） |
| 来源失效后内部稳定副本可读 | PASS | 将源文件临时改名（mv）后从书架打开 → 「阅读正文已就绪 / 分页正文已就绪」，翻页至第 2 章内容渲染正常（27–29 号截图）；随后已恢复文件名 |
| 删除书籍不删来源原文件 | PASS | 删除《测试文本甲》前后源文件 MD5 均为 `d50f8b742241ded2290b9701b0d18b66`（删除前因追加内容已偏离初始基线，属预期） |
| 扫描取消与恢复 | 未覆盖 | 未在 UI 中遇到可注入的取消时机；未专项构造 |
| 截断/深层目录/大文件边界 | 未覆盖 | 4.5 MB 大文件已放入 fixture 但未执行专项导入；未构造 >2000 项目录 |

### 2.4 Reader、词典、阅读设置与选区设置 — ✅ PASS（含 1 项未覆盖）

| 项目 | 判定 | 证据 |
|---|---|---|
| 全局设置与本书覆盖设置 | PASS | 阅读设置面板两级结构完整：「修改作用范围（只改本书/改全局）」「本书覆盖 0 项」。A+ 增大字号 → 「本书覆盖 1 项 · 字号 本书 26 号 · 全局 25 号」；「清除本书覆盖」→ 恢复 0 项 |
| 选区动作门控 | PASS | 长按选区 → 第一屏动作「高亮 / 浏览器 / 复制 / 更多」，「更多」内为「字典 / 添加批注 / 替换 / 书内搜索 / 记为灵感 / 取消选择」；AI 解读未出现在阅读器菜单（与配置面板声称的动作集存在差异，判读为按可用性门控，未深究原因） |
| 词典无资源降级 | PASS | 点「字典」→ 打开词典面板显示「**尚未安装离线词库**」+ StarDict zip 导入引导，无崩溃 |
| 外部跳转返回后位置稳定 | PASS | 选区 →「浏览器」→ MIUI 确认「本次允许」→ Edge 打开 → 返回应用 → 仍停在第 5 章原页，正文逐字一致（41–43 号截图） |
| 替换/规则能力边界 | 未覆盖 | 「替换」入口存在；为避免写入替换规则/纠错数据未执行实际替换 |
| Markdown / legacy 路径 | — | fixture 仅 TXT，未宣称、也未验证 Markdown/legacy 支持 |
| Profile 选区设置单一真源 | PASS（见注） | Profile「选区与查词」显示「第一屏 3 个动作 · 离线词库待导入」，展开后第一屏动作集与阅读器实际工具条逐项一致（高亮·浏览器·复制），「取消选择固定保留」的约束在两侧一致；Profile「阅读设置」打开的排版面板全局字号 25 sp 与阅读器「全局 25 号」一致 |

> 注：单一真源验证采用「一致性比对」而非「破坏性改写后回写」——避免修改用户的全局选区配置。已另以「本书覆盖字号 26→清除」验证了设置→阅读器行为的实时联动。

### 2.5 Home / Profile / Inspiration — ✅ PASS

- 导航与返回：三页 + 底部导航往返、返回重进均正常
- 页面重建后状态稳定：Profile 数据（阅读时长 21 分钟、在读 2 本）与 Home 汇总一致；Home「本周新增」随导入正确 +1
- **CountUp 不重播**：书架 → 首页返回后立即连拍（countup-1~5），首个可辨识帧即显示「21 分钟 · 2 本」，未从 0 重播（countup-*-crop.png）
- 灵感页空态正常（未整理/待整理/已整理/已采用 均 0），无崩溃、无重复数据、无导航断路

### 2.6 Shelf — ✅ PASS（「保存视图」流程未覆盖）

| 项目 | 判定 | 证据 |
|---|---|---|
| 保存视图与动态视图 | PARTIAL | 动态视图（L1）筛选器齐全：阅读状态/书单筛选/分类筛选/标签筛选/排序方式（最近阅读），`ShelfSavedViewsStore.kt` 存在于候选代码中；但「保存视图」的创建入口未在 UI 中定位到，未执行保存→复用流程 |
| 来源导入面板 | PASS | ImportSourceSheet（我的书籍目录/选择文件/选择文件夹/从电脑导入）+ 导入记录（含既有历史 1 条 + 本次 1 条）同屏无重复声明、无崩溃 |
| 书籍详情页 | PARTIAL | 长按面板中「书籍详情与管理」入口存在且文案正确；详情页各区块未逐一展开验收（时间与自动化路径限制） |
| 长按书籍操作面板 | PASS | 面板含「继续阅读 / 重新定位文件（更换本地文件并保留阅读记录）/ 书籍详情与管理 / 删除书籍」（56 号截图） |
| ImportSourceSheet 与历史/桌面入口不重复声明 | PASS | 同屏共处，无重复表单、无崩溃 |
| **删除契约** | **PASS** | 确认弹窗文案：「书籍资料、阅读进度、阅读记录、笔记、高亮，以及分类、标签、书单关联和已读章节都会一并移除。灵感不会被删除…可在 12 秒内撤销」——与真实行为一致：DB 中 `books`/`reading_progress` 对该书 `deleted_at` 置位（软删），**未删除本地正文文件、未删除来源原文件（MD5 前后一致）**，用户既有书籍未受影响。文案未误称「删除私有正文」或「删除来源文件」 |

## 4. 证据文件索引

根目录：`output/android-r3-gate/`（均在仓库工作区内）

| 类别 | 文件 |
|---|---|
| 安装 | `install_script_output.log`（完整输出+退出码） |
| 冷启动/稳定性 | `01`~`05`（截图+logcat） |
| R3 来源旅程 | `10`~`29`（导入面板/SAF 授权/识别/已入架/内容有更新/来源缺失可读） |
| Reader/设置/选区/词典 | `30`~`43`（菜单/两级设置/字号覆盖/选区/词典降级/外部跳转往返） |
| Home/Profile/Inspiration | `44`~`47`、`countup-*` |
| Shelf/删除契约 | `48`~`58`（整理页/视图/长按面板/删除确认/删除后） |
| 恢复与收尾 | `59-root-restored.png`、`60-logcat-final-fullsession.txt` |
| DB 取证 | `pre.db(+wal/shm)` 迁移前库、`post1.db` 导入后库、`post2.db` 删除后库、`orig/restored-library_root.preferences_pb` |
| fixture | `fixture-src/`（本地源）、`fixture-push/`（ASCII 推送副本）、`fixture-source-md5-baseline.txt` |
| 辅助脚本 | `ui.py`（UI dump/查找/点击，仅本次验收用） |

全程未录屏（截图序列 + logcat 覆盖了所有判定点）。

## 5. 环境与数据收尾

- **stay_on_while_plugged_in**：原值 `3`；全程未修改（会话短，无需临时改动）；恢复结果 N/A（= 3，与原值一致）
- **fixture 清理**：`/sdcard/Download/cra-r3-gate-fixture/` 已整目录删除（验证 Download 下已无 cra-r3 残留）；`/sdcard/local/tmp/book_*.txt`、`/sdcard/cra-ui-dump.xml`、`/sdcard/cra-install-ui-*.xml` 已清理
- **用户根目录恢复**：验收中为测试「目录授权」临时将单一根切换为 fixture 目录，结束后已通过「更换 → luoshuizhiwei/reads → 使用此文件夹 → 允许」恢复；DataStore 比对确认 `tree_uri` 与原始值逐字节一致（`primary:luoshuizhiwei/reads`）
- **用户数据**：既有书籍《不平静的日常_作者：惰天使》及其进度/书签/来源引用未受任何影响（删除确认弹窗曾误选该书所在行，已立即取消，未执行任何操作）
- 未执行任何 `git add/commit/push/reset/checkout/clean/stash/revert`；未修改产品源码、测试、脚本、文档

## 6. 未覆盖项、疑似问题与风险

### 6.1 BLOCKED / 未覆盖

1. **Room v12→v13、v13→v14 迁移执行验证** — BLOCKED（无受控旧版本 fixture；不得清用户数据）。建议：由候选所有者在 androidTest 环境（MigrationTestHelper）出运行时证据，或提供隔离 fixture 后补验收。
2. 扫描取消与恢复、截断/深层目录（>2000 项）、大文件专项导入 — 未覆盖。
3. 替换/规则能力实际执行 — 未覆盖（入口存在）。
4. 「保存视图」创建→复用流程、书籍详情页全区块 — 未覆盖。

### 6.2 疑似问题（低置信，需所有者复现；本验收不修改）

| # | 现象 | 证据 | 建议回交 |
|---|---|---|---|
| P1 | 目录页根目录视图下，「智能识别」页签连续 3 次点击（命中 UI dump 精确 bounds 中心）不切换；进入子目录后首次多位置点击即切换成功 | `18/19/20/21/22` 号截图 + dump 坐标 | `e3fafad`（LibraryBrowser）所有者 |
| P2 | 阅读设置工作表的无障碍节点「关闭工作表」点击不关闭（BACK 可关闭）；同时根目录视图下页签偶发不响应或与 P1 同源 | `31/37` 前后 dump | `99a8c03`（Reader 设置）所有者 |
| P3 | 书架整理页 BACK 偶发需要多次才能退出（自动化时序因素无法排除） | 操作日志 | `eebd59d`（Shelf UI）所有者 |

### 6.3 观察项（非缺陷，供所有者确认设计意图）

- 书籍软删后 `library_source_refs` 行保留（availability 仍为 available）。若为支持「疑似重导/撤销」弱判定则合理；若非预期，请 `e3fafad` 所有者确认。
- 阅读器「更多」菜单未出现「AI 解读」，而 Profile 选区配置面板中它可被选入「更多」。判读为按处理器可用性门控，请确认是否符合设计。
- 依赖注入提示：恢复根目录后 DataStore 中 `last_location_document_id` 未恢复（原始值 `primary:luoshuizhiwei/reads/` 丢失，tree_uri 完整恢复）。属本次恢复操作的已知副作用，对功能影响仅为目录页初始定位回到根。

### 6.4 失败项回交

无 FAIL 级失败项。6.2 的 P1/P2/P3 按表回交对应切片所有者复现；若复现成立，修复归属分别为 `e3fafad` / `99a8c03` / `eebd59d`。

---

## 7. 验收独立性声明

- 本验收全程只使用真实设备 `c49ac6cf`（先 `adb devices` 确认），未使用 MuMu 或任何模拟器。
- 安装仅经由仓库脚本完成并取得 CONFIRM_LOOP PASS；未发生也未需要 direct ADB fallback。
- 对用户数据的全部只读取证（DB 拉取、DataStore 读取）均经 `run-as` 只读通道；未写入、未清除。
- 「冻结候选开发门禁通过」「自动安装脚本 PASS」「真机验收 PASS（带条件）」「direct ADB FALLBACK 未使用」四类结论互相独立、不可互替。
