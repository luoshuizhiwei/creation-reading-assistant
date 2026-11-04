# D-2-B 真机验收报告 — Qoder（D-2-B-DEVICE-ACCEPTANCE）

> 验收 Agent：Qoder　|　声明模型：`qwen3.8Flash`
> 任务编号：D-2-B-DEVICE-ACCEPTANCE（阅读设置作用域说明的真实手机验收，只验收不修复）
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`　固定基线：`origin/main = 5aed5939600e491843e3c44453921bd6273a233c`
> 验收开始 `git rev-parse --short HEAD` = `5aed593`，结束仍为 `5aed593`；`git diff --cached` = 0；`git stash list` = 0
> 前置报告：`agent-result-d02b-zcode.md`、`agent-review-d02b-qoder.md`、`agent-result-d02b-f1-zcode.md`、`docs/qa/2026-09/2026-09-21-d02a-qoder-device-acceptance.md`
> 证据目录：`docs/qa/evidence/2026-09-22-d02b-qoder-device-acceptance/`
> 验收窗口：2026-09-22 01:26:17 ～ 02:11:50（主机时钟）
> **最终结论：PASS**

---

## 1. 最终结论

**PASS。**

任务书用例 A–G **全部通过**，判定门槛逐条满足：

| # | PASS 门槛 | 结果 | 关键证据 |
|---:|---|---|---|
| 1 | 真机 `c49ac6cf` | ✅ | `00-device-before.txt`：`ro.kernel.qemu` 为空，`adb devices -l` 全程只有这一台 |
| 2 | 五页 BOOK 文案正确 | ✅ | 10/10 页精确匹配、各 1 次、页内无其他提示（`52-…-check.txt` §A） |
| 3 | DISPLAY 使用真实控件名 | ✅ | 四个例外逐字等于行标题；六个模板槽位在场且未被写成本书例外（§D） |
| 4 | 五页 GLOBAL 文案正确 | ✅ | 五页统一为 Codex 修正版，无「影响所有书」、无承诺清除、无 `**` |
| 5 | ROOT 无页级提示 | ✅ | 7 份 ROOT dump 页级提示计数均为 0（§B） |
| 6 | 根页翻页速度说明可见 | ✅ | 「全局设置，所有书籍共用」位于翻页速度卡片内部（`09-*.png`） |
| 7 | 动画无残留或重复提示 | ✅ | 3 轮 12 份稳定态 + 7 份过渡态 dump：子页各 1 条、ROOT 各 0 条 |
| 8 | 大字号与横屏仍可读可滚动 | ✅ | 1.3 字号 2 行完整换行；横屏 DISPLAY 2 行 / GLOBAL 1 行；正文可滚动 |
| 9 | DataStore 零差异 | ✅ | 前后 `preferences_pb` SHA-256 相同（`7b4509ce…`），70 键 diff 为空 |
| 10 | 阅读位置不变 | ✅ | 测试 TXT 验收后仍「第1章 测试章节1号 / 0.0%」，与基线逐字一致 |
| 11 | 设备状态全部恢复 | ✅ | 5 项 settings + 权限 + 进程 + 作用域逐项回读 MATCH |
| 12 | 无本应用 crash/ANR | ✅ | 45.4 MB logcat：0 FATAL / 0 ANR / 0 am_crash，本进程 0 异常 |
| 13 | 无 P0/P1/P2 | ✅ | P0/P1/P2 = 0/0/0，保留 3 项 P3 |

**一处由验收工具自身造成的副作用已完全恢复并单独披露**（§11），不属于产品缺陷。

---

## 2. 设备身份与非模拟器证明

| 项 | 值 |
|---|---|
| `adb devices -l`（01:26:17 与 02:11:50 各一次） | `c49ac6cf  device product:diting model:22081212C device:diting transport_id:1` |
| 同时在线设备数 | **1**（**未出现** `127.0.0.1:*`、MuMu 或任何虚拟传输，因此不存在误操作虚拟设备的可能） |
| 品牌 / 型号 / device / product | Redmi / 22081212C / diting / missi |
| Android / SDK | 15 / 35 |
| MIUI 版本 | `OS3.0.3.0.VLFCNXM`，`AQ3A.250226.002` |
| **`ro.kernel.qemu`** | **空** → 非模拟器 |
| 屏幕 | 1220 × 2712（480dpi，约 406.7 × 904dp） |
| 包名 / 版本 | `com.creationreadingassistant`，versionName 0.5.1，versionCode 3 |
| 装机前 `lastUpdateTime` | 2026-09-21 21:26:13（**D-2-A 轮**的包，早于 D-2-B 源码改动） |
| 装机后 `lastUpdateTime` | 2026-09-22 01:28:47（本轮构建） |
| 每条设备命令 | 全部显式 `adb -s c49ac6cf …` |

**本轮验收跑在自己 01:25 构建、并证明含 D-2-B-F1 文案的 APK 上**（§4）。

---

## 3. 代码门槛（引用 Codex 结果，本轮未重跑）

任务书明确：Codex 已在 F1 完成后新鲜重跑，本轮**不再重复运行 JVM 测试**，因此我没有再次执行 Gradle 测试任务。引用值：

| 测试类 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `ReaderSettingsScopeNoticeTest` | 21 | 0 | 0 | 0 |
| `ReaderSettingsRouterTest` | 16 | 0 | 0 | 0 |
| **合计** | **37** | **0** | **0** | **0** |

`BUILD SUCCESSFUL in 44s`。

**我没有独立复算这 37 项**（任务书禁止重复运行），改用 §4 的 APK 字面量检索独立证明「被测代码确实进了装机包」，这条证据不依赖 Gradle 绿灯。

---

## 4. 构建

```
# 工作目录：android/
.\gradlew.bat --console=plain :app:assembleDebug --rerun
```

实际执行形式：`powershell.exe -NoProfile -Command "Set-Location <android>; .\gradlew.bat --console=plain :app:assembleDebug --rerun; Write-Output ('EXITCODE=' + $LASTEXITCODE)"`（Git Bash 下 `cmd /c` 会给出假退出码，见既有取证约束）。

| 项 | 值 |
|---|---|
| 退出码 | **0**（日志末行 `EXITCODE=0`） |
| 结果 | **`BUILD SUCCESSFUL in 35s`** |
| 任务统计 | `41 actionable tasks: 3 executed, 38 up-to-date`；真实执行：`dexBuilderDebug`、`mergeProjectDexDebug`、`packageDebug`（+ 请求的 `assembleDebug`） |
| APK 路径 | `android/app/build/outputs/apk/debug/app-debug.apk` |
| APK 大小 | **52,647,080 字节** |
| **APK SHA-256** | **`0316fd7243de0b94bb6eecf198b5c530127cc5104becf3a15f189766057e528f`** |
| 构建时间 | 2026-09-22 01:25:44（产物 mtime） |
| **第一条真实警告** | **无** —— `^w: ` 0 条、`^e: ` 0 条、`warning` 0 条（日志第 3 行是项目自身用 GBK 打印的 debug 签名提示，不是编译器警告） |

**`compileDebugKotlin` 报 UP-TO-DATE 的处置**：Gradle 的 `--rerun` 只强制命令行请求的任务，依赖任务保留 up-to-date 状态；Codex 的 F1 门禁已编译过同一份源码，所以此处 UP-TO-DATE 是**预期**而非「没编译」。为不依赖这个推断，我解包 21 个 dex 按 UTF-8 字节检索字面量：

```
除沉浸模式、显示进度条、自动隐藏和显示安静阅读信息外   → classes16.dex  1 次
本页修改将保存为全局默认；其他书已有的单书设置不会被清除 → classes16.dex  1 次
全局设置，所有书籍共用                                → classes16.dex  1 次
本页设置只对当前书籍生效                              → classes16.dex  1 次
本页均为全局设置，对所有书籍生效                       → classes16.dex  1 次
页眉页脚开关（F1 前的旧串）                           → 0 次
除沉浸模式、进度条、菜单隐藏（F1 前的旧句子前缀）        → 0 次
```

⇒ 装机包**含 F1 新文案且不含旧文案**，与源码工作树一致（`ReaderSettingsSheet.kt:101` 现场核对）。

---

## 5. 安装

```
powershell.exe -NoProfile -ExecutionPolicy Bypass -File android/scripts/install_with_confirm.ps1 \
  -Apk <android\app\build\outputs\apk\debug\app-debug.apk> -Serial c49ac6cf
```

| 项 | 值 |
|---|---|
| 脚本退出码 | **0** |
| 脚本最终 RESULT | **`RESULT: CONFIRM_LOOP (installed through the MIUI installer UI)`** |
| MIUI 自动确认 | **成功**，依次点击 `android:id/button3` → `android:id/button3` → `com.miui.packageinstaller:id/checkbox` → `com.miui.packageinstaller:id/second_button` |
| 超时 / 阻断 | 无（默认 `-TimeoutSeconds 90` 未触发；安装前 `mWakefulness=Awake`） |
| **是否使用 ADB 直装回退** | **否**。`adb install -r` 全程未执行，不存在「回退冒充脚本 PASS」 |
| 安装前包信息 | versionCode 3 / versionName 0.5.1 / `lastUpdateTime=2026-09-21 21:26:13` |
| 安装后包信息 | 同版本，`lastUpdateTime=2026-09-22 01:28:47`，`firstInstallTime=2026-09-19 18:38:36`（**未变成全新安装**，数据未丢） |
| 设备侧 `base.apk` SHA-256 | `0316fd7243de0b94bb6eecf198b5c530127cc5104becf3a15f189766057e528f` —— 与本地构建**逐字节一致**（`sha256sum` 与 `run-as sha256sum` 两种方式各测一次，结果相同） |
| 安装对 DataStore 的影响 | **零**：装机后 `preferences_pb` 与测试前 SHA-256 相同（`01b-install-datastore-diff.txt` 为空） |
| POST_NOTIFICATIONS | 安装前 `granted=false` → 安装后立即回读 **`granted=true`**（脚本点的 MIUI checkbox，与 D-2-A 实测一致）→ 收尾 `pm revoke` 恢复为 `false` 并回读 |

---

## 6. 用例 A：ROOT / BOOK 与根页翻页速度说明

| # | 检查点 | 结果 | 证据 |
|---:|---|---|---|
| 1 | 根页存在「只改本书 / 改全局默认」选择 | ✅ | `08-settings-root.txt` 节点 4/5；截图可见「✓ 只改本书」为选中态（描边胶囊 + 勾） |
| 2 | ROOT 不显示 `reader-settings-scope-notice` 的页级说明 | ✅ | 7 份 ROOT dump 页级提示命中数 **0**（`52-…-check.txt` §B） |
| 3 | 「翻页速度」附近可见「全局设置，所有书籍共用」 | ✅ | 未滚动：节点 29 `[90,2671][453,2712]`；滚动后：`[90,1605][453,1653]` |
| 4 | 与翻页速度形成清晰视觉关联 | ✅ | `09-root-scrolled-to-paging-speed.png`：说明位于**同一张圆角卡片内**，顺序为 翻页速度标题 + 150 ms 徽标 → 滑块（最快/最慢）→ 说明；卡片下方留白后才出现「高级与偏好设置」分组 |
| 5 | 不被相邻的翻页效果或其他项目误读 | ✅ | 说明上方是滑块行标签（`最快/最慢` y1515–1563），下方是分组标题（y1731），中间无其他控件；「翻页效果」在其上方且不同卡片 |
| 6 | 大字号下可完整显示 | ✅ | `38-largefont-root-paging-note.txt` 节点 14 `[90,1744][552,1807]`，1.3 字号下仍单行完整（宽 462、行盒 63px） |
| 7 | 未修改翻页速度实际值 | ✅ | 全程未触碰该滑块；`reader_page_turn_speed` 浮点位不变，UI 始终显示 **150 ms**，DataStore 零差异 |

---

## 7. 用例 B：BOOK 作用域五个子页面（逐字）

作用域：「✓ 只改本书」。每页均**进入第一屏即可见**说明（位于标题之下、正文之上，随页面滚动常驻），每页说明节点出现 **1 次**，页内**无其他**提示串。

| 页 | 屏幕标题 | 逐字文案 | 行盒 | 判定 |
|---|---|---|---|---|
| 一 排版与版式 | 排版 | `本页设置只对当前书籍生效` | `[102,1438][498,1486]` 48px/1 行 | ✅ |
| 二 翻页与按键 | 翻页与操作 | `除阅读模式、翻页效果和屏幕方向外，本页其他设置均为全局设置，对所有书籍生效` | `[102,1438][1172,1534]` 96px/2 行 | ✅ |
| 三 显示与页眉页脚 | 显示与页眉页脚 | `除沉浸模式、显示进度条、自动隐藏和显示安静阅读信息外，本页其他设置均为全局设置，对所有书籍生效` | `[102,1438][1172,1534]` 96px/2 行 | ✅ |
| 四 护眼与阅读提醒 | 护眼与提醒 | `本页均为全局设置，对所有书籍生效` | `[102,1438][630,1486]` 48px/1 行 | ✅ |
| 五 排版引擎兼容 | 高级兼容 | `本页设置只对当前书籍生效` | `[102,1438][498,1486]` 48px/1 行 | ✅ |

### 7.1 显示页特别检查（F1 的核心）

| 检查点 | 结果 | 证据 |
|---|---|---|
| 不再出现「页眉页脚开关」 | ✅ | 全部 45 份 UI dump 命中 **0**；该串仅出现在我自己的证据说明文件 `02-build-and-apk-identity.txt`（作为"必须不存在"的对照） |
| 四个例外与真实控件逐字对应 | ✅ | 沉浸模式（行标题 `[198,2193]`）、显示进度条（`[198,2405]`）、自动隐藏（`[186,1839]`，位于分组「菜单隐藏」之下）、显示安静阅读信息（`[198,2263]`）—— 四个都是**行标题原文**，没有借用分组名 |
| 六个页眉/页脚模板选择器不被误读为本书设置 | ✅ | `13c` dump 中 页眉左侧/中间/右侧、页脚左侧/中间/右侧 六个槽位全部在场，且**没有一个**出现在说明文案里；它们正确地留在「其余为全局」一侧 |
| 「自动隐藏」「显示安静阅读信息」是否会被当成同一控件 | ✅ 不会 | 二者分处「菜单隐藏」与「页眉页脚」两个分组，行标题各自唯一 |

### 7.2 翻页页与高级页的例外/字段核对

- 翻页页三个例外（阅读模式、翻页效果、屏幕方向）全部是该页真实行标题（`12`/`12b` dump）✅
- 高级兼容页：`TXT 分页兼容模式`、`EPUB 分页兼容模式` 两个引擎字段**仍在场**，该页说明为「只对当前书籍生效」，页内**没有任何**把引擎字段说成全局的文案 ✅（D-2-A 的 per-book 语义未被 D-2-B 回退）
- 顺带关闭 D-2-A 的 **P3-1**：「高级兼容子页没有任何作用域提示」—— 真机现已有该页说明。

---

## 8. 用例 C：GLOBAL 作用域五个子页面

返回 ROOT → 点「改全局默认」→ 截图确认「✓ 改全局默认」为选中态（`20-root-global-scope.png`）。五页逐字均为：

```
本页修改将保存为全局默认；其他书已有的单书设置不会被清除
```

| 页 | 精确匹配 | 出现次数 | 行盒 |
|---|---|---|---|
| 排版 | ✅ | 1 | `[102,1438][1026,1486]` 48px/1 行 |
| 翻页与操作 | ✅ | 1 | 同上 |
| 显示与页眉页脚 | ✅ | 1 | 同上 |
| 护眼与提醒 | ✅ | 1 | 同上 |
| 高级兼容 | ✅ | 1 | 同上 |

| 检查点 | 结果 |
|---|---|
| 没有写成「影响所有书」 | ✅ 全证据 `影响所有书` 命中 **0** |
| 没有承诺清除其他书设置 | ✅ `清除其他书` 命中 **0**；文案本身是「不会被清除」 |
| 没有 Markdown 星号 | ✅ UI dump 中 `**` 命中 **0**（唯一命中在 `50-logcat.txt` 的 `--------- beginning of main`，与 UI 无关） |
| 返回 ROOT 后页级说明消失 | ✅ `33-r*-root-after-*.txt` 页级提示计数 0 |
| 根页「全局设置，所有书籍共用」仍存在 | ✅ `20-root-global-scope.txt` 节点 29 在场 |
| 不实际修改任何设置 | ✅ 五页只做进入/截图/返回，未点击任何控件；DataStore 零差异 |
| 结束后作用域切回测试前 | ✅ 切回「只改本书」，截图 `46-scope-restored-visual.png` 显示「✓ 只改本书」 |

---

## 9. 用例 D：动画与导航稳定性

**3 轮**完整序列 `ROOT → 翻页与按键 → 返回 → 显示与页眉页脚 → 返回`（BOOK 作用域，`33-r{1,2,3}-*`），另加快速进入再返回（`34-fast-back-*`）。

| 观察项 | 结果 | 证据 |
|---|---|---|
| 页面切换时不出现两条重叠提示 | ✅ | 6 份子页 dump 各 **1** 条提示，且 `which` 列只有一个标识（翻页页=PAGING 文案、显示页=DISPLAY 文案），无跨页文案共存 |
| 返回 ROOT 后旧提示不残留 | ✅ | 6 份 ROOT dump 提示计数 **0** |
| 不出现闪烁、空白、错页文案或重复节点 | ✅ | 每份 dump 都有完整标题 + 正文节点，无空页；同一提示串在单页内出现次数恒为 1 |
| 标题与提示同步切换 | ✅ | `33-r*-paging` 标题「翻页与操作」配 PAGING 文案；`33-r*-display` 标题「显示与页眉页脚」配 DISPLAY 文案；7 份过渡窗口 dump 同样是**目标页标题 + 目标页文案** |
| 快速但正常点击不会停留在旧页面 | ✅ | `34-fast-back-trans.xml`（点入后 350ms 即点返回）与 `34-fast-back-settled.txt` 都是 ROOT、0 提示 |
| 补充：交叉淡入本身 | ⚠️ 观察项 | `35-transition-shot-2.png` 捕捉到淡入中间帧：新页标题「翻页与操作」与旧 ROOT 内容（滑块、分组入口）**同时可见**。这是 `AnimatedContent` 160ms 淡入/120ms 淡出的既有形态（D-2-B 未改动画），该帧内**没有**两条作用域说明共存。登记为 P3-2 |

**必须如实说明的采样局限**：`uiautomator dump` 单次耗时约 0.3–0.5s，截屏约 0.1–0.2s，因此 160ms 过渡窗口内的每一帧**未被穷举**；「无重复提示」的结论建立在 7 份过渡态 + 3 张瞬时截屏 + 13 份稳定态之上，而不是逐帧证明。

---

## 10. 用例 E：大字号与横屏

测试前记录（`36-case-e-before-settings.txt`）：`font_scale=1.0`、`accelerometer_rotation=1`、`user_rotation=0`。

### 10.1 `font_scale = 1.3`（竖屏）

| 检查点 | 结果 | 证据 |
|---|---|---|
| 最长的 DISPLAY 提示允许换行 | ✅ | 行盒由 1.0 时的 96px（2 行）变为 **126px（2 行 × 63px）** —— 高度随行高增长，证明是重新排版而非截断 |
| 不出现省略号 | ✅ | 截图 `39-display-largefont-portrait.png` 两行完整：「…自动隐藏和显示安静阅读信 / 息外，本页其他设置均为全局设置，对所有书籍生效」；dump 文本为完整 47 字 |
| 不被标题或正文遮挡 | ✅ | 标题 `[180,1298][1184,1392]`、提示 `[102,1438][1172,1564]`、首个分组 `[186,1642]`，三者 y 区间不重叠 |
| 正文仍能滚动 | ✅ | `40`/`41` 两次下滑后依次露出 电量显示方式 → 页眉左侧/中间/右侧 → 页脚左侧/中间/右侧，提示常驻顶部 |
| 返回按钮可点击 | ✅ | `返回` 节点 `[60,1309][132,1381]` 全程在场，后续导航均由它完成 |
| 至少一个设置项可通过滚动到达 | ✅ | 上述六个模板选择器 + 电量显示方式三档均可达 |
| 不出现零高度正文区域 | ✅ | 正文区从 y1642 延伸到屏幕底 2712（约 1070px） |
| 根页翻页速度说明完整 | ✅ | `38` 节点 14 `[90,1744][552,1807]` 单行完整 |

### 10.2 横屏（`user_rotation=1`，`accelerometer_rotation=0`，仍 1.3 字号）

| 检查点 | 结果 | 证据 |
|---|---|---|
| DISPLAY 提示完整 | ✅ | `42-display-largefont-landscape.txt` 节点 5 `[498,835][2268,961]`，宽 1770、高 126 = 2 行；截图两行无省略号 |
| GLOBAL 提示完整 | ✅ | `45-global-notice-landscape.txt` 节点 5 `[498,835][1674,898]` 单行 63px，右端 1674 < 屏宽 2712 |
| 正文可用 | ✅ | 横屏下 42 页露出 电量显示方式/三档选项，45 页露出 排版预览 + 预览正文 |
| 返回按钮可用 | ✅ | `42`/`45` 均有 `返回` 节点（`[456,706][528,778]`） |
| 零高度正文 | ✅ 未出现 | 正文区 y916→1220 |
| 观察项 | ⚠️ | 横屏 + 1.3 字号时工作表可视高度仅约 450px，ROOT 首屏只能看到 字号大小/行距比例，**翻页速度说明需下滑才可见**（`44-root-landscape-global.txt` 中该说明不在节点里）。属短屏可滚动范围，非裁切；登记为 P3-3 |

### 10.3 恢复与回读（`47-case-e-restored.txt`）

| 项 | 测试前 | 恢复后回读 | 判定 |
|---|---|---|---|
| `font_scale` | 1.0 | **1.0** | ✅ |
| `accelerometer_rotation` | 1 | **1** | ✅ |
| `user_rotation` | 0 | **0** | ✅ |
| 屏幕方向 | 竖屏 | `wm size` = 1220x2712（竖） | ✅ |
| 作用域 | 只改本书 | 「✓ 只改本书」（`46-scope-restored-visual.png`） | ✅ |

**过程中的两个真实现象**（如实记录）：第一次横屏后设备自行回到竖屏并关闭了工作表（MIUI 旋转与 App `屏幕方向=跟随系统` 共同作用，非缺陷），我重新打开工作表补齐了 GLOBAL 横屏项；两次配置变更都在 logcat 里留下 `finishDrawing of relaunch`（376ms / 604ms），均正常完成绘制。

---

## 11. 用例 G：设置与数据零变化

### 11.1 DataStore（只读 `run-as cat`，未做任何写入）

| 快照时刻 | SHA-256（前 16） | 键数 | 本书覆盖键 |
|---|---|---:|---:|
| 测试前 01:27 | `7b4509ce3ecc5f23` | 70 | 6 |
| 安装后 01:29 | `7b4509ce3ecc5f23` | 70 | 6 |
| 用例 A/B/C 后 01:44 | `7b4509ce3ecc5f23` | 70 | 6 |
| **用例 D 首轮（我的双点击）** | `397df766b21fcecf` ← 唯一偏离 | 70 | 6 |
| 亮度恢复后 | `7b4509ce3ecc5f23` | 70 | 6 |
| 用例 D 重跑 / E 之后 | `7b4509ce3ecc5f23` | 70 | 6 |
| **最终 02:08** | `7b4509ce3ecc5f23` | 70 | 6 |
| force-stop 后回读 02:11 | `7b4509ce3ecc5f23` | 70 | 6 |

`42-datastore-diff.txt` 为空 → **零差异**。解析口径：同一份自写 protobuf 解析脚本，值里保留嵌套帧字节（如 `'*\x05paged'`），因此比较比"只看语义值"更严格。

### 11.2 副作用披露（验收工具造成，非产品缺陷）

`32-brightness-side-effect-and-restoration.txt`：我的首轮用例 D 脚本对同一坐标执行了两次点击（定位脚本本身已点击一次），第二次落在「显示与页眉页脚」页的亮度 `SeekBar`（`[60,2032][1160,2104]`）上，使

```
reader_brightness              100 → 22   (varint 0x64 → 0x16)
reader_last_fixed_brightness   100 → 22
```

**恢复方式**：仅用 App UI —— 在同一滑块上做一次 `input swipe` 拖到右端点，UI 回读 **100%**，DataStore 两键回到 `18 64`，全量 diff 归零。**未**写 DataStore/Room、**未** `pm clear`、**未**重装。修正后的用例 D 重跑（单次点击版）之后 DataStore 仍与基线一致。

### 11.3 阅读位置与书籍数据

| 项 | 测试前 | 测试后 | 判定 |
|---|---|---|---|
| 测试 TXT（t-txt-s07）位置 | 第1章 测试章节1号，页脚 **0.0%**（`07-reader-before.txt`） | 第1章 测试章节1号，页脚 **0.0%**（`49-reader-after-acceptance.txt`） | ✅ 逐字一致，**无推进** |
| 阅读器顶部副标题 | `TXT · 第1章 测试章节1号 · 0%` | 同 | ✅ |
| 书架计数 | 全部 11 / 在读 4 / 未开始 5 / 已完成 2 | 全部 11 / 在读 4 / 未开始 5 / 已完成 2 | ✅ |
| 每张卡片进度 | t-txt-s07 0%、t-txt-long-s07 第3章·50%、t-txt-b-s07 第1章·26%、t-md-s07 0%、t-md-b-s07 完、测试EPUB 第3章·34% | 逐张一致 | ✅ 无新增/删除/重命名 |
| 高亮 / 笔记 / 书签 | — | — | ✅ 全程未进入选区工具栏、未打开笔记/规则页、未点任何保存或删除入口 |
| 「覆盖 N 项」 | 1 项 | 1 项（每次 ROOT dump 都是 1） | ✅ 本书覆盖未被触碰 |
| `pm clear` | — | **未执行** | ✅ |

---

## 12. 稳定性（logcat）

采集：`adb -s c49ac6cf logcat -v threadtime`，窗口 01:30:19 → 02:08:29，**330,164 行 / 45.4 MB**（`50-logcat.txt`，分析见 `50-logcat-analysis.txt`）。

| 检查项 | 命中 | 归属 |
|---|---:|---|
| `FATAL EXCEPTION` | **0** | — |
| `ANR in` / `am_anr` / `am_crash` | **0** | — |
| `SIGSEGV` / `SIGABRT` / `F/libc` | **0** | — |
| `Duplicate key` / `duplicate key` | **0** | — |
| `corrupt` | **0** | — |
| snapshot apply conflict | **0** | — |
| `IllegalStateException` | 27 | **全部属其他进程**：pid 2591(system_server) 16、28987、2506、8054、4337、31875… |
| `has died` | 86 | **0 条**提到本应用包 |
| `DataStore` | 56 | 52× `dist:crossdeviceservice:MetaDataStore`、2× `UIAgentDataStore`、2× `FeaturedDataStore`（MIUI 系统服务），**无一来自本应用** |
| `constraint` | 69 | JobInfo / AconfigFlags / PickerDbFacade（系统） |
| crash buffer（`-b crash`）中本包 | **0** | — |

**本应用进程（pid 10598）**：14,275 行；`FATAL`/`AndroidRuntime`/崩溃标记 **0**；`*Exception` / `*Error` 词元 **0**；窗口内进程启动 **1 次**（我自己 01:30 的启动，无重启）。E/W 行全为平台噪声：`StrictMode` 11,581 行（debug 构建的磁盘/网络策略日志，本仓库 debug 包既有）、`avc: denied vendor_display_prop`、`OnBackInvokedCallback`、`DequeueBuffer`、`ziparchive base.dm`、`SnapshotStateListKt.clearImpl failed lock verification`（ART 对 Compose 运行时的锁校验退化提示）等。两次 `finishDrawing of relaunch` 对应我的字号/旋转测试，均正常完成。

⇒ **无本应用 crash / ANR / DataStore 损坏 / Compose 异常 / 快照冲突**。

---

## 13. 无障碍语义静态检查（用例 F）

方法：uiautomator XML 即 accessibility tree，对 6 份代表性 dump 做节点级判定（`48-accessibility-tree-analysis.txt`）。**本轮未开启 TalkBack**。

| 检查点 | 结果 |
|---|---|
| 提示文本作为普通文字节点存在 | ✅ 全部为 `android.widget.TextView`，字符串在 `@text`（长度 12–47），`@content-desc` 为空 |
| Info 图标没有重复朗读文本 | ✅ 树中**没有任何**节点以 content-desc 承载说明文本；图标位置只有无描述的 `View` 节点，不产生朗读 |
| 同一页面没有两份相同提示 | ✅ 每页提示串出现 1 次；无重复节点 |
| 返回按钮仍有「返回」描述 | ✅ 五份子页 dump 各 1 个 `content-desc="返回"` 节点（竖屏 `[60,1309][132,1381]`、横屏 `[456,706][528,778]`）；ROOT 无返回节点（正确） |
| 根页翻页速度说明可被 a11y tree 读取 | ✅ 该说明是 `@text` 文本节点（`09` 中节点 #35，len=11），非图片、非 desc-only |
| 不可断言项 | 本机 uiautomator 不输出 `visible-to-user` / `important-for-accessibility` 属性，这两项未能验证 |

---

## 14. P0 / P1 / P2 / P3

**P0 = 0　P1 = 0　P2 = 0　P3 = 3**

| 编号 | 级别 | 问题 | 证据 | 是否 D-2-B 引入 |
|---|---|---|---|---|
| **P3-1** | P3 | 安装脚本 `install_with_confirm.ps1` 的 MIUI 确认循环仍会把 `POST_NOTIFICATIONS` 从 `false` 静默改成 `true`（点的是 `com.miui.packageinstaller:id/checkbox`）。任何用该脚本做验收的 Agent 都必须回读并 revoke | `03-install-and-permission-after.txt`（false→true）+ `40-device-after.txt`（revoke 后 false） | 否（工具副作用，与 D-2-A P3-3 同源，仍未修） |
| **P3-2** | P3 | 页面切换的 160ms 交叉淡入期间，新旧两页内容同时可见（`AnimatedContent` 既有形态）。因说明位于 `AnimatedContent` 内，理论上存在两页说明短暂共存的窗口；真机 3 轮 + 7 份过渡 dump + 3 张瞬时截屏均未出现，但**未逐帧穷举** | `35-transition-shot-2.png`、`33-r*-trans-into-*.xml` | 部分（D-2-B 把说明放进了 `AnimatedContent`，但动画本身是既有代码；与代码复核 ST-6 同源） |
| **P3-3** | P3 | 横屏 + 1.3 字号时，工作表可视高度仅约 450dp 的三分之一，ROOT 首屏看不到翻页速度卡片及其「全局设置，所有书籍共用」说明，需下滑；另在 1.0 字号竖屏首屏，该说明行盒底边等于屏幕底边（2671–2712），文字完整但无底部留白 | `44-root-landscape-global.txt`、`08-settings-root.txt` 节点 29、`08-settings-root-book-scope.png` | 部分（新增说明行使卡片变长，贴边与折叠由此显现；根因是 sheet 底部无 contentPadding） |

**不计入产品缺陷的过程记录**：§11.2 的亮度误改由我的脚本双点击造成，已恢复且 DataStore 归零差异；首轮用例 D 因把中文期望串经 argv 传给 Windows Python（GBK 代码页）而得到 13 个假 `found=0`，改用文件传参后复核为正确结果（见 §16）。

---

## 15. 未覆盖项

| # | 未覆盖内容 | 原因 | 建议归属 |
|---|---|---|---|
| U1 | **TalkBack 实际朗读**（顺序、是否逐字读图标、整句是否被读一次） | 任务书本轮明确不要求开启，避免改变用户设备状态。**不得记为 TalkBack PASS** | 无障碍专项 |
| U2 | 6 个 Compose androidTest 用例的**实际执行** | 本机 MIUI 上 `createComposeRule` 类 instrumentation 无法跑完（既有实测），且任务书未要求；真机结论来自 uiautomator + 截屏 | 需要 CI 上的模拟器/其他设备 |
| U3 | 高亮 / 笔记 / 书签的**逐条数量前后对照** | 设备无 `sqlite3`；不为验收逐本点开笔记页（会推进更多书位置）。改用「未进入任何写路径 + 书架逐卡进度一致 + 覆盖项数不变」间接证据 | 有只读 DB 访问的一方 |
| U4 | Room 主库字节数的本轮前后差 | 我在测试前未记录该值（**我的取证遗漏**）；只取到收尾值 1,124,139,008 B。等价证据见 U3 与 §11.3 | 后续验收把库大小列入基线清单 |
| U5 | 320dp 级小屏（物理小尺寸）下的排版 | 本机为 406.7dp 宽，无法构造 | 小屏专项或 instrumentation |
| U6 | 代码复核 R8/R9（改一个全局项后「覆盖 N 项」不增加、换书确认共用） | 需要真实修改设置值，任务书本轮禁止 | 后续带写入的验收轮 |
| U7 | 深色/夜间纸张与夜间主题下说明的配色对比度 | 未切换主题（会写设置） | 视觉专项 |
| U8 | `:app:lintDebug`、Release 构建、其他切片 | 不在本轮范围 | — |

---

## 16. 我的失误与纠正（如实记录）

1. **脚本双点击误改亮度**：首轮用例 D 的定位脚本已点击一次，我又补了一次 `input tap`，第二次落到亮度滑块 → `reader_brightness` 100→22。发现方式：跑完即做 DataStore 中途比对（不是靠记忆）。处置：仅用 App UI 拖回 100，DataStore 全量 diff 归零，重跑修正版用例 D。已登记为 §11.2，不算产品缺陷。
2. **中文期望串经 argv 传参导致 13 个假 `found=0`**：Windows Python 的 argv 走 GBK 代码页，中文比较恒不匹配。发现方式：改用文件传参重跑复核，得到正确结论（每子页 1 条、ROOT 0 条）。教训仍是「先自证测量方法，再读结果」。
3. **首轮用例 D 在 GLOBAL 作用域下跑**（用例 C 之后未先切回），因此那 13 份 dump 的提示是 GLOBAL 文案。已在 BOOK 作用域下重跑 3 轮，两组都保留并分别标注。
4. **未在测试前记录 Room 主库字节数**（U4）。
5. 若干次 `find.sh` 未命中导致 `input tap` 空参数报错（`NO_MATCH` 时我的 awk 仍返回一个空格）——**这些点击全部没有执行**，未产生任何副作用；后续脚本加了 `case FOUND*` 守卫。
6. 一次横屏被系统自行转回竖屏并关闭工作表，我重新打开补齐 GLOBAL 横屏项（§10.3）。

---

## 17. 设备状态恢复表

| 项 | 测试前 | 结束回读（02:11:50） | 判定 |
|---|---|---|---|
| `global/stay_on_while_plugged_in` | **3** | **3** | ✅ MATCH（原值即 3，全程未改，也未被"恢复成 0"） |
| `system/accelerometer_rotation` | 1 | **1** | ✅ MATCH |
| `system/user_rotation` | 0 | **0** | ✅ MATCH |
| `system/font_scale` | 1.0 | **1.0** | ✅ MATCH |
| `system/time_12_24` | 12 | **12** | ✅ MATCH |
| `POST_NOTIFICATIONS` | granted=**false** | granted=**false**（`pm revoke` 后回读） | ✅ 已恢复（被安装脚本翻转） |
| 设置作用域 | 只改本书 | 只改本书（截图确认） | ✅ |
| DataStore | `7b4509ce…` / 70 键 | `7b4509ce…` / 70 键 | ✅ 零差异 |
| 测试 TXT 位置 | 第1章 0.0% | 第1章 0.0% | ✅ |
| 应用进程 | 运行中（pid 16086，前台为 MIUI 桌面） | `am force-stop` 后 **`pidof` 为空** | ✅ 按任务书要求停止（与基线的"后台存活"不同，属任务书指定的收尾状态） |
| 最终前台窗口 | `com.miui.home/.launcher.Launcher` | `com.miui.home/.launcher.Launcher` | ✅ |
| `adb devices -l` | 仅 `c49ac6cf` | 仅 `c49ac6cf` | ✅ 全程无虚拟设备 |

---

## 18. 用户数据保护声明

- 全程只打开 **测试 TXT（t-txt-s07）** 一本文字稳定、位置已知的中性夹具书；测试 EPUB / 测试 Markdown / 其他夹具书与书架中的 **3 本用户真实书籍均未点开**（逐卡进度前后一致可证）。
- **未**导入、删除、重命名任何书籍；**未**创建或删除高亮、笔记、书签；**未**修改阅读规则；**未**推进阅读位置；**未**清除应用数据（无 `pm clear`）；**未**直接写数据库或 DataStore（DataStore 仅 `run-as cat` 只读采集）。
- 报告与文本证据只使用「测试 TXT / 测试 EPUB / 测试 Markdown / 夹具 id」等中性名称。**用户真实书名已从全部 .txt / .xml / .json 证据中程序化替换为〈用户书籍-已脱敏〉（6 个文件、18 处）**，用于生成替换表的临时清单文件已删除，仓库内不含这些标题。三张书架截图（`05`/`06`/`51`）的**像素**中仍含设备既有书名（未做像素遮挡），因为它们是「书架数量与逐卡进度未变」的直接证据。
- 未点击阅读器内的「关闭提示」入口（避免写入提示抑制标记）。
- 未 commit / push / stage / stash / reset / checkout / clean，未全仓格式化，未进入原始仓库 `D:\develop\Code\Codex\creation-reading-assistant`（所有命令都在恢复仓库内以绝对路径执行）；工作区 366 项他人既有改动未回退、未覆盖。
- 未修改任何源码、测试、Gradle、Manifest、Zcode/Qoder 代码报告或看板；本轮新增文件只有本报告与该证据目录。
- 未处理 S08 / S09 或其他切片，未触碰其他窗口的报告，未领取下一任务。

---

## 19. 证据索引

目录：`docs/qa/evidence/2026-09-22-d02b-qoder-device-acceptance/`（206 个文件，约 242 MB；**未覆盖 D-2-A 的任何证据**）。完整分组清单见目录内 `summary.txt`，其中含「任务书建议文件名 → 本目录实际文件名」的对应表。

| 组 | 关键文件 |
|---|---|
| 基线 | `00-device-before.txt`、`01-datastore-before.json`、`01-app_settings_before.pb`、`05-shelf-before.*`、`06-bookshelf-before.*`、`07-reader-before.*`、`07-reader-chrome.*` |
| 构建/安装 | `02-build-and-apk-identity.txt`、`02-build-gradle-raw.log`、`03-install-script-raw.log`、`03-install-and-permission-after.txt`、`01b-install-datastore-diff.txt` |
| 用例 A | `08-settings-root-book-scope.png`、`09-root-scrolled-to-paging-speed.{png,txt,xml}` |
| 用例 B | `11/12/12b/13/13b/13c/14/15-*-book-notice.{png,xml,txt}` |
| 用例 C | `20-root-global-scope.*`、`21/22/23/24/25-global-{typography,paging,display,eyecare,advanced}-notice.*` |
| 用例 D | `33-r{1,2,3}-{paging,display,root-after-*}.*`、`33-r*-trans-into-*.xml`、`34-fast-back-*`、`35-transition-shot-{1,2,3}.png`、`30-r*-*`（首轮，GLOBAL 作用域） |
| 用例 E | `36-case-e-before-settings.txt`、`37/38/39/40/41-largefont-*`、`42-display-largefont-landscape.*`、`44-root-landscape-global.*`、`45-global-notice-landscape.*`、`46-scope-restored-visual.png`、`47-case-e-restored.txt` |
| 用例 F | `48-accessibility-tree-analysis.txt` |
| 用例 G | `49-reader-after-acceptance.*`、`50-bookshelf-after.*`、`51-bookshelf-after.*`、`41-datastore-after.json`、`41-app_settings_final.pb`、`42-datastore-diff.txt`、`42-datastore-and-data-comparison.txt`、`32-brightness-side-effect-and-restoration.txt` |
| 逐字/禁用串 | `52-verbatim-and-forbidden-string-check.txt` |
| 稳定性 | `50-logcat.txt`（45.4 MB）、`50-logcat-analysis.txt`、`04-logcat-crash-buffer-before-window.txt` |
| 收尾 | `40-device-after.txt`（02:11:50）、`53-final-confirmation-readback.txt`（02:19:04 二次确认：`pidof` 空、权限 false、4 项 settings、DataStore SHA 与基线相同）、`summary.txt` |

⚠️ 体积提示：45 张 PNG 约 194 MB + logcat 45.4 MB。本目录当前为未跟踪内容，若不宜入库，请集成者在归档前压缩或仅保留文本/JSON 与报告点名的关键截图。

---

## 20. 回传摘要

| 项 | 内容 |
|---|---|
| **结论** | **D-2-B-DEVICE：PASS** |
| 设备序列号 | `c49ac6cf`（Redmi 22081212C / diting，Android 15 / SDK 35，`ro.kernel.qemu` 空 = 真机；全程唯一设备） |
| APK SHA-256 | `0316fd7243de0b94bb6eecf198b5c530127cc5104becf3a15f189766057e528f`（52,647,080 B；设备侧 `base.apk` 同一哈希；dex 含 F1 新文案、旧文案 0 命中） |
| 安装 | `install_with_confirm.ps1` → 退出码 **0**、`RESULT: CONFIRM_LOOP`，无超时/无阻断，**未使用 ADB 直装回退**；装机未改 DataStore；POST_NOTIFICATIONS 被脚本翻为 true，收尾 revoke 回 false |
| BOOK 五页 | 排版/翻页与操作/显示与页眉页脚/护眼与提醒/高级兼容 —— **5/5 逐字正确**，各 1 个提示节点、无跨页文案共存；DISPLAY 四个例外均为真实行标题，六个页眉/页脚模板槽位未被写成本书例外；「页眉页脚开关」0 命中 |
| GLOBAL 五页 | **5/5 逐字正确**（统一为 Codex 修正版）；无「影响所有书」、无清除承诺、无 `**` |
| ROOT 翻页速度说明 | ✅ 「全局设置，所有书籍共用」位于翻页速度卡片内，与滑块同容器、不与翻页效果混淆；ROOT 页级提示 0 条；1.3 字号单行完整；值仍 150 ms 未改 |
| 大字号/横屏 | ✅ 1.3 字号竖屏 2 行完整换行、无省略号、正文可滚动、返回可达；横屏 DISPLAY 2 行 / GLOBAL 1 行完整；无零高度正文；设置已回读恢复 |
| DataStore diff | **零差异**（前后 SHA-256 均为 `7b4509ce3ecc5f23…`，70 键 / 6 本书覆盖键）。中途唯一偏离由我的脚本双点击造成（亮度 100→22），已仅用 App UI 恢复并归零 |
| 阅读位置前后 | 测试 TXT 第1章 测试章节1号 **0.0% → 0.0%**（逐字一致，无推进）；书架 11/4/5/2 与逐卡进度不变 |
| P0/P1/P2/P3 | **0 / 0 / 0 / 3**（P3-1 安装脚本翻转通知权限[工具既有]、P3-2 160ms 交叉淡入内容共存窗口[动画既有，未逐帧穷举]、P3-3 横屏大字号下根页说明需下滑/竖屏贴底无留白） |
| 设备状态恢复 | 5 项 settings + 权限 + 作用域 + 进程 全部 MATCH；`pidof` 空；前台 `com.miui.home`；DataStore 与基线一致 |
| 报告路径 | `docs/qa/2026-09/2026-09-22-d02b-qoder-device-acceptance.md` |
| 证据目录 | `docs/qa/evidence/2026-09-22-d02b-qoder-device-acceptance/` |

**未领取下一任务。** S08 / S09 的只读调查由其他窗口并行进行，本轮未触碰其报告；D-2-B 之后的实现收尾（代码复核登记的 10 项 P3 测试精简与注释归因）与 S08–S20 均保持 HOLD，等待 Codex 主任务复核。
