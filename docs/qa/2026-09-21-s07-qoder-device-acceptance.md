# S07 真机验收报告 — Qoder 独立执行（2026-09-21）

> 验收 Agent：Qoder（用户明确指定本轮由 Qoder 代替 WorkBuddy 执行真机验收）
> 验收仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`；验收全程 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> 性质：**只验收，不修复**。未修改任何 Android 产品源码、测试源码或项目配置；未 commit / push / stage / reset / checkout / clean / stash；未全仓格式化。
> 证据目录：`docs/qa/evidence/2026-09-21-s07-qoder-device/`（47 张截图 + 夹具生成脚本）
> 状态：**等待 Codex 主任务复核**

---

## 1. 验收范围与排除项

### 已执行范围

任务书 §5–§10 的 A/B/C/D 四组矩阵、跨书与 force reload、TTS→滚动→dispose→消费者快照、浏览器与词典真实动作、小屏/横屏/大字体/触摸/无障碍结构、多高亮 + TTS 性能体感。

### 明确排除项

| 排除项 | 原因 |
|---|---|
| MuMu 或任何模拟器 | 任务禁止；本轮全程只使用序列号 `c49ac6cf` 的真实手机 |
| 在用户真实藏书上做纠错/高亮/批注/规则测试 | 任务 §4.2 禁止；书架原有 5 本均为用户真实藏书，本轮一律只读且不记录书名 |
| 卸载/停用/禁用系统浏览器或词典以制造 `NO_HANDLER` | 任务 §8 禁止；相关子项标 NOT TESTED |
| 启用 TalkBack | 任务 §9.6 禁止擅自改变用户长期无障碍设置；只做结构检查 |
| 修改 MIUI 安全设置 / 默认应用 | 任务 §2.11 禁止 |
| 发送 AI 请求（章节摘要 / 问答 / 灵感生成 / AI 助读） | 任务 §4.6 限制；只验证摘录预填状态 |
| 修改系统动画、开发者选项、性能模式 | 任务 §10.5 禁止；gfxinfo 只读 |
| 领取 S08–S20 | 未领取 |

### 关于本轮不采信的数字

Zcode F4.1 与 Qoder F4.1 复核中的 JVM 数字（29 类 / 366 tests、126 类 / 1081 tests 等）**不作为本次真机任何一项的 PASS 依据**，只在对应子项标 NOT TESTED 时作为"已有静态/ JVM 覆盖"的指路引用。

---

## 2. 设备和系统信息

`adb devices -l`（原始输出，未裁剪）：

```
List of devices attached
c49ac6cf               device product:diting model:22081212C device:diting transport_id:1
```

| 项 | 值 | 取证命令 |
|---|---|---|
| 序列号 | `c49ac6cf` | `adb devices -l` |
| 状态 | `device`（在线、已授权） | 同上 |
| 真机判定依据 | `product:diting` `model:22081212C`（Redmi 实体机型；`genymobile`/`google_sdk`/`emulator` 等特征均不存在） | 同上 + `getprop` |
| 品牌 / 型号 / 设备代号 | Redmi / 22081212C / diting | `getprop ro.product.brand\|model\|device` |
| Android 版本 / SDK | Android 15 / SDK 35 | `getprop ro.build.version.release\|sdk` |
| ROM | HyperOS `OS3.0.3.0.VLFCNXM`，`ro.miui.ui.version.name = V816` | `getprop` |
| 屏幕 | 物理 1220×2712，密度 480dpi（≈3.0×，约 407×904 dp） | `wm size` / `wm density` |
| 系统字体缩放 | 1.0 | `settings get system font_scale` |
| 无障碍服务 | 未启用 TalkBack；已启用为 李跳跳 / Select to Speak（系统）/ GKD / MIUI 截屏 | `settings get secure enabled_accessibility_services` |

后续所有设备命令均显式带 `-s c49ac6cf`。

---

## 3. APK 信息

| 项 | 值 |
|---|---|
| 绝对路径 | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\android\app\build\outputs\apk\debug\app-debug.apk` |
| 文件大小 | 51,584,724 字节 |
| mtime | 2026-09-21 02:16:46 +0800 |
| SHA256 | `cb4b921e0db8566f1a595983f2a02b68fbfd252e443444da3fa019a57320663f` |
| applicationId | `com.creationreadingassistant` |
| versionName / versionCode | `0.5.1` / `3`（`output-metadata.json`） |
| 安装前设备上 `lastUpdateTime` | 2026-09-19 22:58:04 |
| 安装后设备上 `lastUpdateTime` | 2026-09-21 02:17:43 |

---

## 4. 构建证据

### 4.1 被丢弃的第一次尝试（假退出码，必须记录）

```
cmd.exe /c "gradlew.bat :app:assembleDebug --console=plain"   → 报告 EXIT=0，耗时 0 秒
```

该结果**不可信并已丢弃**：MSYS 会把 `/c` 当路径转换掉，`cmd` 退成交互式空输入，返回假 exit 0，日志里只有 Windows 版本横幅，没有任何 Gradle 任务。判定依据：0 秒不可能完成构建 + 日志无 `BUILD SUCCESSFUL`。

### 4.2 真实构建

| 项 | 值 |
|---|---|
| 目录 | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\android` |
| 命令 | `./gradlew.bat :app:assembleDebug --console=plain` |
| 开始 / 结束 | 2026-09-21 02:15:45 → 02:16:49 |
| 结果 | `BUILD SUCCESSFUL in 1m 4s` |
| 退出码 | `0`（`REAL_BUILD_EXIT=0`，写入同一日志文件，未经 `cmd.exe /c`） |
| 任务统计 | `41 actionable tasks: 15 executed, 5 from cache, 21 up-to-4ate`（原文 `up-to-date`） |

日志中 `15 executed` 说明本次确有真实编译，不是纯缓存命中。构建过程未修改任何源码。

---

## 5. 安装脚本及安装证据

### 5.1 先读脚本真实接口（未猜参数）

`android/scripts/install_with_confirm.ps1` 参数：`-Apk`、`-Serial`（默认 `c49ac6cf`）、`-PackageName`、`-TestApk`、`-NoDirectInstall`、`-TimeoutSeconds`。退出码语义（脚本自身定义）：`0` = MIUI 确认循环成功；`2` = 直接 `adb install` 回退（**不是** confirm-loop PASS）；`1` = 失败。

### 5.2 实际执行

```
MSYS_NO_PATHCONV=1 powershell.exe -NoProfile -ExecutionPolicy Bypass -File "scripts/install_with_confirm.ps1" \
  -Apk "D:\...\android\app\build\outputs\apk\debug\app-debug.apk" -Serial c49ac6cf -TimeoutSeconds 120
```

| 项 | 值 |
|---|---|
| 时间 | 2026-09-21 02:17:17 → 02:17:44 |
| **退出码** | **0** |
| 结果标记行 | `RESULT: CONFIRM_LOOP (installed through the MIUI installer UI)` + `Installed com.creationreadingassistant successfully.` |
| 设备盘点 | `adb devices entries=1; serial 'c49ac6cf' listed as device: True` |
| 脚本自动点掉的 MIUI 控件 | `android:id/button3` ×2 → `com.miui.packageinstaller:id/checkbox` → `com.miui.packageinstaller:id/second_button` → `start_button` |

**结论：安装脚本真实 PASS（确认循环路径）。未使用直接 `adb install` 回退，因此不存在"把回退写成脚本 PASS"的风险；本轮也不声称 MIUI 自动确认问题被"解决"——它在这一次运行里工作正常。**

未使用任何会清除应用数据的安装参数；未要求用户手动点弹窗；未修改 MIUI 安全设置。

---

## 6. stay-awake 保存与恢复

| 项 | 修改前 | 临时值 | 恢复后 |
|---|---|---|---|
| `global stay_on_while_plugged_in` | **`3`**（AC+USB 常亮，用户本来就开着） | 未修改 | `3` |

取证：`adb -s c49ac6cf shell settings get global stay_on_while_plugged_in` 在会话开始（02:14）与结束（04:20 段）各取一次，均为 `3`。

**重要**：原值不是 `0`。若按常见做法"收尾置 0"反而会破坏用户既有设置，因此本轮全程未触碰该键。

---

## 7. 测试 TXT（矩阵 A）

### 7.0 测试夹具与前置

书架原有 5 本全部是用户真实藏书，**没有可安全使用的中性测试副本**，因此本轮自建中性夹具并导入（详见 §16）：

| 内部名 | 报告名 | 用途 |
|---|---|---|
| `t-txt-s07.txt`（7.5 KB，6 章） | 测试 TXT | 分页 + 滚动选区链 |
| `t-txt-long-s07.txt`（113 KB，4 长章） | 测试 TXT（长章） | 多 ReadingUnit / 性能 |
| `t-txt-b-s07.txt`（4.4 KB，3 章） | 测试 TXT B | 跨书隔离 |

夹具正文为程序生成的无意义中文段落，不对应任何真实作品；含一条跨章重复段落与一条唯一锚点句，用于串章判定。

### 7.1 应用内基线设置（改动前）

| 项 | 基线值 | 取证 |
|---|---|---|
| 阅读模式 | 左右翻页（分页） | 阅读器无障碍节点 `分页正文已就绪`；设置面板截图 `03-reading-mode-baseline.png` |
| 字号 | 25 号 | `00-settings-sheet-top.png` |
| TXT 分页兼容模式 | 强制 | `01-pager-engine-baseline.png` |
| EPUB 分页兼容模式 | 自动 | 同上 |
| 设置作用域 | 只改本书（带 ✓） | `02-scope-toggle.png` |

### 7.2 分页模式逐项结果

| # | 子项 | 判定 | 操作 → 预期 → 实际 | 证据 | 产生数据 |
|---|---|---|---|---|---|
| A.1 | 长按产生真实选区 | **PASS** | 长按正文行中心 → 出现选区 → 整句被选中，出现自绘首/尾手柄 | `10-txt-paged-selection.png` | 否 |
| A.2 | 选中文字与正文一致 | **PASS** | 选区文本 `窗台上的陶盆裂了一道缝，她用蜡补住，第二天又被雨冲开。` 与屏幕正文逐字一致 | 同上 + 无障碍摘录节点 | 否 |
| A.3 | 工具栏可见、动作不重复不缺失 | **PASS** | 主栏 5 项：复制/浏览器/替换/字典/更多；更多内 5 项：高亮/添加批注/书内搜索/记为灵感/取消选择。无重复、无缺项 | `10-txt-paged-selection.png` | 否 |
| A.4 | 拖动手柄范围合理 | **PASS** | 尾柄前拖 → 摘录追加 `\r\n\r\n雨后的石`；首柄后拖 → 摘录前扩到上一整句。无跳章、无负坐标、无错字 | 两次拖动后的摘录节点文本 | 否 |
| A.5 | 复制内容与提示一致 | **PASS** | 点复制 → 提示 `已复制`；随后在批注输入框用 `KEYCODE_PASTE` 真实粘出 `柜台后的老人抬起头，摘下眼镜，说今天要的东西已经没有了。`，与选区完全一致（证明剪贴板确实写入且内容正确） | 粘贴后 EditText 节点值 | 否 |
| A.6 | 高亮保存后立即回显 | **PASS** | 高亮→黄色 → 提示 `已高亮`，正文该句立即出现黄底 | `11-txt-highlight-saved.png` | **是**（1 条高亮） |
| A.7 | 关闭重开后高亮仍在正确位置 | **PASS** | 返回键退出阅读页 → 首页 → 重新打开同一书 → 该句仍带黄底，位置未漂移 | `12-txt-highlight-after-reopen.png` | 否 |
| A.8 | 批注打开后摘录保持打开时内容 | **PASS（部分，见 §12）** | 批注面板打开后摘录固定为选中整句；在 TTS 朗读推进、原 ReadingUnit 回收期间保持不变（详见 §12） | `23-tts-playing.png` 系列 | **是**（1 条笔记） |
| A.9 | 正常翻页后旧选区工具栏消失 | **PASS** | 有选区时左滑翻页 → 进度 16.9%→23.4%，工具栏节点数 4→0 | 两次 dump 计数 | 否 |
| A.10 | 切章后旧选区工具栏消失 | **PASS（含方法学说明）** | 分页下中央轻点会先消费掉选区（见 A.12），因此"带活选区直接跳章"在 UI 上不可达。改为跨章连续翻页验证：切到第2章后工具栏为 0，且第1章的高亮未出现在第2章同句上 | 翻页后 dump + `19/20/21/22` 系列 | 否 |
| A.11 | 滚动到新 ReadingUnit、旧单元回收后旧选区不挂到新单元 | **PASS** | 在第1章选中 → 连续下滑 24 次至第6章（第1章单元确已回收，无障碍树只剩第6章节点）→ 新单元正文**无**任何残留高亮底色或手柄；此时保存高亮，落点仍在**第1章原选句**，第6章同句不受影响 | `18/19/20/21/22` 系列截图 | **是**（第1章 1 条高亮） |
| A.12 | 中央点击/取消选择后状态收敛 | **PASS** | 轻点正文 → 摘录节点消失、工具栏消失、色板节点数 0、无残留旧坐标行为 | A.12 前后 dump 对比 | 否 |
| A.13 | 书内搜索使用当前选中文字 | **PASS** | 更多→书内搜索 → 搜索框预填选中整句，返回 `共 8 处结果 · 最多显示前 80 条`，带上一处/下一处 | dump 节点 | 否 |
| A.14 | 分页与滚动测试后恢复原设置 | **部分完成** | 滚动模式覆盖仍留在测试书上，见 §16.3 | — | 是（本书 readerMode 覆盖） |

### 7.3 滚动模式（上下滚动，同一本测试 TXT）

将本书阅读模式临时改为"上下滚动"（只改本书），滚动模式下正文为 `BasicTextField` 分支。

| 子项 | 判定 | 实际 |
|---|---|---|
| 滚动模式长按选区 | **PASS** | 长按 → 平台先弹自己的"复制/全选"浮层，应用工具栏随后出现，选区被收敛为词/句片段（`码头的绳索被海水泡得发白`） |
| 滚动模式手柄拖动 | **PASS** | 拖动尾柄 → 摘录从 `…又划掉。` 扩到 `…又划掉。\r\n\r\n雨后的石`，单调、无跳章 |
| 滚动模式跨 5 章后保存高亮落点 | **PASS** | 见 A.11 |
| 滚动模式工具栏在宿主单元回收后自动收起 | **FAIL → 记为 P3 缺陷 D-3** | 第1章单元已被回收（视图停在第6章、无障碍树无第1章节点）后，工具栏与第1章摘录仍常驻；从该工具栏保存高亮写到了屏幕上不存在的选区（落点仍正确，未造成数据错误） |

**方法学更正（重要）**：最初两次在滚动模式采样时（等待 1.5–2s）报告"应用工具栏不出现"，后续在 0.5s 与 4s 采样均正常出现。该现象**不可复现**，判定为我的采样时序问题，**不记为缺陷**。同理，分页模式最初"长按无反应"也是我的坐标落在段落间隙所致（见 D-7），不是选区功能失效。

---

## 8. 测试 Markdown（矩阵 B）

产品契约：**整块选择**，不是精细拖动选词。夹具 `t-md-s07.md`（含标题/段落/列表/引用/代码块/图片/GFM 表格/分隔线）与 `t-md-b-s07.md`（含与前者完全相同的一句话，用于跨书串位判定）。

| # | 子项 | 判定 | 操作 → 预期 → 实际 | 证据 |
|---|---|---|---|---|
| B.0 | 分页模式下的选择能力 | **明确降级（记录）** | 分页 Markdown 正文轻点只切换控制栏，**不产生选区**。整块选择在代码上位于 Markdown **滚动**分支（`ReaderContentHostMarkdownBranch.kt:168` 起，`ReaderMarkdownRender.kt:251-275` 只在 `blockGlobalOffset>=0` 时挂 `clickable`）。因此本组测试在"上下滚动"下执行 | dump 对比 |
| B.1 | 正文块整块选择、工具栏文字正确 | **PASS** | 轻点段落块 → 摘录 = 该块完整文本 `这是测试 Markdown 的第一段正文，用于验证整块选择、高亮回显与定位链。`；工具栏为 复制/浏览器/字典/更多 | dump |
| B.1b | 工具栏不含"替换"槽 | **PASS（符合契约）** | Markdown 属 `NON_SOURCE_COORDINATES` → 不可用 → 工具栏摘掉替换槽、更多菜单也无纠错入口 | dump |
| B.2 | 图片/分隔线不生成伪文字或伪坐标 | **FAIL（缺陷 D-1，P2）** | 轻点图片块 → 摘录 = **alt 文本 `示意图`**，工具栏弹出；继续 高亮→红色 → 提示 `已高亮`，**该伪文字被写入持久高亮并在重开后仍回显**。分隔线本身正常（渲染为细线，不产生节点文字） | `51-md-image-pseudo-selection.png`、`52-md-image-highlight.png`、`53-md-highlights-after-reopen.png` |
| B.3 | 表格不得产生 `[Table]` 等伪选区 | **PASS** | 轻点表格单元格（`表格行一`）→ 工具栏节点数 0，无 `[Table]` 伪文字、无伪坐标。表格单元格无障碍节点 `clickable=false` | dump |
| B.4 | 表格是否只支持高亮回显而不支持选择 | **表格当前既不可选也未验证高亮回显** → 记为**明确降级**，不判为精细选词 PASS | 单元格不可点；未对其单独做高亮回显 | — |
| B.5 | 高亮保存后立即显示 | **PASS** | 轻点段落块→高亮→黄 → 提示 `已高亮` | dump |
| B.6 | 关闭重开后高亮仍在正确块 | **PASS** | 退出阅读页→重开→滚动到第2节：黄底精确覆盖 `重复段落：山那边的钟声每天黄昏响三次，一次比一次轻，从未变过。`；红底覆盖 `示意图`（即 D-1 的伪高亮也持久化，反证该记录确实落库） | `53-md-highlights-after-reopen.png` |
| B.7 | 相邻章节/文档重复文字不得串章 | **PASS** | 打开另一本含完全相同句子的测试 Markdown（`t-md-b-s07`，分页模式）→ 该句**无**任何高亮底色 | `54-md-b-no-crossbook-highlight.png` |
| B.8 | 页面/章节变化后旧选区清除 | **PASS** | 滚动视图内轻点正文会重新整块选中（见 D-5），但退出重进后无残留选区/工具栏 | dump |
| B.9 | 带 locator 的记录重开后定位正确 | **NOT TESTED** | 未能在本轮把 Markdown 笔记/灵感的"点击→跳转到正确块"跑完（导航采样成本）。同一快照→locator 机制已在 TXT/EPUB 上以高亮落点方式验证为正确 | — |
| B.10 | 不得通过调试手段写入 display offset | **PASS（未发生）** | 全程只用 UI 手势与 `input`/`screencap`/`uiautomator`/`dumpsys` 只读命令；未写 offset、未改数据库 | — |
| B.11 | 图片/表格/分隔线真实观感截图 | **PASS** | `53-md-highlights-after-reopen.png` 含三者：代码块底色、表格按行排布（无网格线）、分隔线细线、图片仅显示 alt 文本 | 同左 |

**夹具局限（如实说明）**：图片用相对路径 `t-img-s07.png` 且已推到同目录，但应用未渲染出图像，只显示 alt 文本。无法据此区分"应用不支持 SAF 相对图片路径"还是"我的夹具路径不对"，因此**图片渲染本身记为 NOT TESTED**；D-1 只针对"alt 文本被当作可选正文并写入高亮"这一条，与图像能否渲染无关。

---

## 9. 分页测试 EPUB（矩阵 C，本轮最高优先级）

夹具 `t-epub-s07.epub`：按 `EpubParser.kt` 契约手工构造（`META-INF/container.xml` + OPF manifest/spine + NCX + 6 个 xhtml 章），`dc:title` 即"测试EPUB"，因此书架与阅读器标题都是中性名。打开后无障碍节点为 `分页正文已就绪` → 确认走分页引擎。

| # | 子项 | 判定 | 操作 → 预期 → 实际 | 证据 |
|---|---|---|---|---|
| C.1 | 长按选区和工具栏正常 | **PASS** | 长按正文 → 选中整句 `巷口的路灯在清晨五点准时熄灭，把最后一小段影子还给墙面。`，工具栏 5 项齐全 | dump |
| C.2 | 高亮保存后立即显示 | **PASS** | 第3章选中 `窗台上的陶盆裂了一道缝，她用蜡补住，第二天又被雨冲开。` → 高亮→绿 → 提示 `已高亮`，该句立即出现绿底 | `31-epub-highlight-immediate.png` |
| C.3 | 关闭重开后高亮回到正确文字 | **PASS** | 退出阅读页→书架→重开测试EPUB→回第3章：绿底精确回到同一句；同句在**第2章**的出现**没有**被高亮 | `32/33/34-epub-*` 三张 |
| C.4 | 单处纠错完整链 | **PASS（本轮最关键结论）** | 见下方 9.1 逐步记录 | `35-epub-correction-persisted-ch1.png` + dump |
| C.5 | UI 显示可纠错时保存不得提示"格式不支持" | **PASS** | 表单可用 → 保存 → 提示为 `已保存单处纠错；正文将重新分页并应用`，规则条目状态 `生效中`。**全程未出现"当前书籍格式不支持"** | dump 节点 |
| C.6 | 失败时不得先出现成功提示 | **PASS（正向路径下）** | 成功路径只有一条成功提示。真正的"拒绝先行成功提示"负向路径需 legacy 分支能发起命令，而 legacy 分支入口已被摘除（见 §10），故该负向只能由 JVM 覆盖 | — |
| C.7 | 保存后选区/工具栏/规则表单状态收敛 | **PASS** | 保存后表单关闭并直接落到"规则管理/替换净化"页，展示该条规则与 `撤销纠错` 动作；正文工具栏不再挂旧选区 | dump |
| C.8 | 翻页、切章后旧选区清除 | **PASS** | 左滑翻页后工具栏节点 4→0；用目录跳章后无残留选区 | dump |
| C.9 | 验收后删除本轮测试规则并确认正文恢复 | **部分完成** | 点 `撤销纠错` → 提示 `操作成功`，状态转 `已撤销 · 正文保留原文`，正文恢复原句（已确认）。**但该条规则仍留在规则列表，UI 只提供"恢复纠错"，未提供删除入口** → 作为遗留项报告（§16.2） | dump + 正文节点 |
| C.10 | 不得在用户真实 EPUB 上执行本项 | **PASS** | 全部纠错操作只在 `测试EPUB` 上执行；用户真实 EPUB 未被打开做写入 | — |

### 9.1 单处纠错逐步记录

| 步 | 操作 | 实际 |
|---|---|---|
| 1 | 在第1章长按选中 `巷口的路灯在清晨五点准时熄灭，把最后一小段影子还给墙面。` | 选区与正文逐字一致 |
| 2 | 工具栏 `替换` | 打开"新增单处纠错"表单，默认 tab = 单处纠错（另有 本书替换 / 全局规则） |
| 3 | 表单显示 `替换目标` = 选中原文；`替换为` 输入 `testfixA`（临时切搜狗输入法输入 ASCII，已还原，见 §16.1） | 预览 `命中 1 处`，`前：`原句 / `后：testfixA` |
| 4 | `保存单处纠错` | 提示 `已保存单处纠错；正文将重新分页并应用`；规则状态 `生效中` |
| 5 | 确认正文变化 | 第1章正文首段变为 `testfixA`，其后段落原样 |
| 6 | 关闭阅读页 → 重开测试EPUB | 位置恢复 |
| 7 | 用目录回到第1章 | 正文仍显示 `testfixA` → **纠错重开仍生效** |
| 8 | 确认不影响其他相同文字 | 该句在夹具中于第1章出现 2 次、第2–6章各 1–2 次。实查：第3章首屏该句**原样未变**；书内搜索该句得 `共 9 处结果`，与夹具中该句的真实总出现次数（第1章2 + 第2章1 + 第3章2 + 第4章1 + 第5章2 + 第6章1 = 9）完全吻合 → **只改了 1 处，无串章、无多改** |
| 9 | 撤销 | `操作成功` → `已撤销 · 正文保留原文`，正文恢复 |

**补充观察（非缺陷）**：书内搜索按**原文**索引 —— 搜 `testfixA` 得"未找到匹配结果"。对"纠错"用例（按原始错字检索）这是合理设计，但意味着纠错生效后搜索命中片段与跳转后的正文文字不一致，记为设计问题供产品裁决。

---

## 10. legacy / 非分页测试 EPUB（矩阵 D）

**前置条件已找到并成功进入**（未用分页 EPUB 结果代替）：该路径需要 `阅读模式 = 左右翻页` 且 `EPUB 分页兼容模式 = 关闭`（`ReaderPagerEngineState.kt:151-160` + `ReaderContentHost.kt:174-179`）。把本书的 EPUB 分页兼容模式临时切到"关闭"后进入 `PagedEpubView`；再把本书阅读模式切到"上下滚动"后进入带**整块选择**的 EPUB 滚动视图（`ReaderContentHostEpubBranch.kt:315` 的 `.clickable`）。

| # | 子项 | 判定 | 实际 |
|---|---|---|---|
| D.0a | legacy 分页 EPUB（`PagedEpubView`） | **可进入，但正文不可选** | 正文渲染正常（`41-legacy-epub-render.png`）；长按无选区，轻点只切换控制栏。此视图不挂整块选择 |
| D.0b | legacy 分页 EPUB 正文不进入无障碍树 | **缺陷 D-4（P3）** | 该视图 dump 只有 2 个节点：`content-desc="阅读正文已就绪"` 与 `下一章`，**正文文字完全不在无障碍树中** |
| D.1 | 整块选择行为 | **PASS** | 切到滚动视图后轻点段落块 → 摘录 = 该块完整文本 `雨后的石阶泛着青色，缝隙里长出细小的苔，踩上去有些滑。`，逐字一致 |
| D.1b | 工具栏动作集合 | **PASS（符合契约）** | 只有 复制/浏览器/字典/更多（4 项，**无"替换"槽**）；更多菜单 5 项中**无纠错入口** |
| D.2 | 持久高亮在正确内容块回显 | **PASS** | 高亮→蓝 → 该块整块上底色（`45-legacy-epub-highlight.png`）；重开后仍回显 |
| D.3 | 重复文字不得导致高亮显示到错误位置 | **PASS（同章）/ PARTIAL（跨章）** | 同章其余 13 个块均未上底色，只有被点的块有；跨章同文块（第2章亦有该句）因该视图对合成快速滑动无响应未能翻到，**跨章那一例记 PARTIAL** |
| D.4 | 单处纠错入口应不可用，或直接命令被明确拒绝 | **PASS（入口不可用）** | 工具栏无"替换"、更多菜单无纠错项 → 无法从 UI 发起该命令。能力被 `effectiveReplacementAvailability` 遮蔽为 `PAGER_ENGINE_DISABLED`（`ReaderPagerEngineState.kt:70-78, 269-271`） |
| D.5 | 拒绝只能显示一条准确提示 | **NOT TESTED** | 入口不可用导致无法从真机发起被拒绝的命令；"只有一条提示"的断言只有 JVM 覆盖（`ReaderSingleCorrectionCapabilityTest` 的 `NotAnchorable` 分支） |
| D.6 | 拒绝后不得产生纠错记录 | **PASS（间接）** | 该路径下规则列表未新增任何条目（撤销分页 EPUB 的测试规则后，legacy 路径全程未产生第二条规则） |
| D.7 | 页面/章节/阅读单元变化后旧选区清除 | **PASS（含 D-5 缺陷）** | `更多→取消选择` 可正常收敛（工具栏节点 4→0）。但**轻点正文无法收起工具栏**：每次轻点都会重新整块选中该块（`ReaderContentHostEpubBranch.kt:315` 的 clickable 吞掉中央轻点，仅坐标不可靠时才回落 `onToggleControls`）→ 缺陷 **D-5（P3）** |

---

## 11. 跨书与 force reload（矩阵 F）

| # | 子项 | 判定 | 操作 → 实际 |
|---|---|---|---|
| F.1 | 在测试书 A 建立选区并保持工具栏可见 | **PASS** | 测试 TXT（滚动）第1章长按 → 摘录 `解开来里面还是湿的`，工具栏 4 项可见 |
| F.2 | 不手动取消，返回书架并打开测试书 B | **PASS（含路径说明）** | 按返回键 → 书架 → 打开 测试 TXT B。**如实记录实际操作路径**：返回键本身先在阅读页内消费了选区（工具栏消失），随后才退出到书架；因此"带活工具栏跨书"这一最刁钻形态在 UI 上不可直接构造 |
| F.3 | B 不显示 A 的选区工具栏 / 不显示 A 的文字 | **PASS** | B 打开后工具栏节点数 0；B 正文为自身内容 `第1章 另一本测试书1号 / 另一本测试书的段落，只出现在这里：乙-1-…`，阅读器顶栏标题 `t-txt-b-s07`、`TXT · 第1章 另一本测试书1号 · 0%` |
| F.4a | 批注不带入 A 的摘录 | **PASS（同机制，见 F.4c 强证）** | 在 B 中无选区时无法打开批注（批注入口挂在选区工具栏上）；改为验证"新书不会自己冒出旧书摘录"——B 中所有消费者均为空 |
| F.4b | 规则不带入 A 的摘录 | **PARTIAL** | B 的规则页未新增任何条目；但未在"活选区跨书"形态下直接发起保存 |
| F.4c | 灵感不带入 A 的摘录 | **PASS（最强证据）** | 在 B 中打开"记录灵感"：标题预填 `阅读灵感：t-txt-b-s07`，标签默认书名 = `t-txt-b-s07`，**没有任何来自 A 的摘录文字**。这正是 F4.1 修复要守住的"新书不得重新捕获旧书选区"的用户可见面 |
| F.4d | AI 只检查预填摘录，不发送请求 | **PASS（未发送）** | 在测试EPUB 中曾误开 `AI 阅读伴读` 面板，只观察到 `已选入 344 字正文` 的预填状态，**未点击任何生成按钮**，无网络请求发出 |
| F.5 | 不会把 A 的坐标配 B 的 bookId 保存 | **PASS（只读核对）** | 用 `run-as` 只读读取 `app_settings.preferences_pb`：本轮产生的全部覆盖键为 `book_reader_override_d5bd07ef…_readerMode`、`book_reader_override_9a85d098…_readerMode`、`book_reader_override_epub_zhrum_readerMode`、`book_reader_override_202a634c…_fontSize`，其 bookId 全部是本轮新建测试书（与验收前 `files/books/` 目录清单逐一比对，用户 5 本书的 bookId 均未出现） |
| F.6 | force reload（错误页重试）清选区/快照/消费者残留 | **NOT TESTED** | 本轮未能安全地把任一测试书推入"文档加载失败 → 错误页 → 重试"状态：需要破坏测试书文件或数据库，任务 §4.4 禁止修改书籍数据。该项只有 JVM 覆盖（`a force reload rebuilds the document so it clears the selection too`） |
| F.7 | 正常同书无操作重开不应被误判成跨书残留 | **PASS** | 多次"退出阅读页→重开同一本书"（测试 TXT / 测试EPUB / 测试 Markdown）均正确恢复进度与高亮，未出现选区丢失误报，也未出现凭空多出的选区工具栏 |

---

## 12. TTS → 滚动 → ReadingUnit dispose → 消费者快照（矩阵 G）

使用滚动模式测试 TXT。**先记录一个前置发现**：听书依赖 `POST_NOTIFICATIONS`；拒绝该权限时点"听书"静默无反应、无任何解释文案（记为 **D-6，P3**）。为完成本项临时授予该权限，收尾已撤销（§16.1）。

| 步 | 操作 | 实际 |
|---|---|---|
| 1 | 打开听书 | 底部控制条进入朗读态：`第1章 …` / `1.0x` / 上一段 / **暂停**（即正在朗读）/ 下一段 / 朗读设置 / 关闭朗读。系统侧证据：`com.xiaomi.mibrain.speech` 的 `AudioTrack … state:started` |
| 2 | 建立正文选区 | 摘录 `，晾衣绳上的衬衫鼓起来`（第1章） |
| 3 | 打开不产生网络费用的消费者（添加批注） | 面板显示 `摘录：，晾衣绳上的衬衫鼓起来`；用 `KEYCODE_PASTE` 在正文框写入草稿 |
| 4 | 让 TTS 推进到程序化滚动、原 ReadingUnit 回收 | 等待 102s 后：摘录与草稿**逐字未变**；保存笔记后关闭面板，正文已自动推进到 **第2章/第3章**，无障碍树中**只剩第2、3章节点**（第1章单元确已 dispose）；再开控制栏时朗读已到 **第4章 · 50%** |
| 5 | 五项断言 | 见下表 |

| 子项 | 判定 | 依据 |
|---|---|---|
| 正文活动选区可按设计失效 | **PASS** | 第1章单元回收后正文无残留高亮/手柄 |
| 已打开消费者中的摘录不变 | **PASS** | T+102s 摘录仍为第1章那句，未跳到新 ReadingUnit |
| 批注/规则草稿不重置 | **PASS（批注）/ NOT TESTED（规则）** | 批注草稿粘贴文本 102s 后原样保留；**规则消费者未能在朗读态下跑完**（见下方阻碍） |
| locator 不变为空 | **PARTIAL** | 保存成功（`已保存笔记`），但"点笔记跳回第1章正确位置"未能跑完（见阻碍）；同一快照→locator 通路已在 A.11 / C.3 以高亮落点方式证明为正确且非空 |
| 摘录不跳到新 ReadingUnit | **PASS** | 全程摘录始终是第1章那句 |

**实际阻碍（如实记录，不编造通过）**：

1. 朗读控制条与选区工具栏共用同一个底部槽位（`ReaderChrome.kt:112` → `ReaderInteractionLayer.kt:312` 的 `ReaderBottomChromeMode.TTS`）。建立选区后朗读条被工具栏替换，因此"TTS 条 + 工具栏"不能同时可见——但**朗读本身继续推进**，所以本项核心（朗读导致 dispose 时消费者快照保持）仍可验证并已验证。
2. 从阅读器 `更多 → 笔记与标注` 打开笔记列表以做 locator 跳转，多次点击被自动隐藏的控制栏吞掉（该阅读器控制栏约 2–3s 自动收起，跨工具调用的秒级延迟使点击落到正文），未能在预算内稳定完成。
3. 规则消费者在朗读态下未覆盖。

**因此 §12 整体判定：PARTIAL**（批注快照链真机通过；规则快照与 locator 跳转未覆盖）。

---

## 13. 浏览器和词典真实动作（矩阵 H）

| # | 子项 | 判定 | 实际与证据 |
|---|---|---|---|
| H.1 | 浏览器成功：launcher 真正接受后才提示，不得提前"正在打开" | **PASS** | 选中文字 → 点 `浏览器`。T+0.4s 采样：应用窗口内**无**任何"正在打开…"文案。系统证据：`09-21 04:07:05.290 SmartPower: Miui monitor launch : com.microsoft.emmx/…ChromeTabbedActivity launchFrom:com.creationreadingassistant`，随后 `mCurrentFocus` 变为 Edge，Edge 实际渲染出网页（含"打开App"等页面节点）→ launcher 确实接受，浏览器真实打开 |
| H.2 | 系统文字工具/词典成功路径 | **NOT TESTED** | 工具栏 `字典` 在本机构建上打开的是**应用内"词典"面板**，内容为查询文本 + `尚未安装离线词库` + StarDict 词库导入说明（无离线词库、无在线/系统词典按钮）。因此"已调用系统文字工具"这条提示未被真机触发。指路：JVM `ReaderSelectionExternalActionsTest`（18 项提示矩阵） |
| H.3 | 系统词典不可用 + Web fallback 可用（只一条最终提示，明确说明已用在线词典） | **NOT TESTED** | 无法在不卸载/不停用系统应用的前提下制造该组合（任务 §8 禁止）。指路：同上 JVM 矩阵 `OPENED_WEB_FALLBACK` 分支 |
| H.4 | 在线词典成功路径 | **NOT TESTED** | 该入口在本机 UI 上未暴露（见 H.2） |
| H.5 | 空选区：不调用外部应用，只提示"请先选择要查询的文字" | **NOT TESTED（UI 不可达）** | 外部动作入口只存在于选区工具栏内，无选区时工具栏本身不出现，因此"空选区点动作"在真机上无法构造。指路：JVM `NOTICE_BLANK_SELECTION` 断言 |
| H.6 | 设备自然存在 NO_HANDLER 的失败文案 | **NOT TESTED** | 本机有可用浏览器（Edge）与输入法文字工具，不存在自然 NO_HANDLER；不得为制造失败而卸载/禁用。指路：JVM 穷举 `when`（无 `else`）+ 提示矩阵 |
| H.7 | 不得先显示"正在打开" | **PASS（在唯一可测的成功路径上）** | H.1 的 0.4s 采样未出现任何前置文案 |

---

## 14. 小屏 / 横屏 / 大字体 / 触摸 / 无障碍（矩阵 I）

设备为 6.67" 1220×2712，本轮无真·小屏设备可换，"小屏"以**横屏（有效高度 1220px ≈ 407dp）+ 大字号**作为等效挤压条件。

| # | 子项 | 判定 | 实际 | 证据 |
|---|---|---|---|---|
| I.1 | 竖屏工具栏 | **PASS** | 5 个动作等距分布，图标+文字两行，无重叠、无截断、未超出屏幕；色板 5 色可点（节点宽 138px ≈ 46dp，触摸目标不显小） | `10-txt-paged-selection.png` |
| I.2 | 横屏工具栏与更多菜单 | **PASS** | 旋转后 5 个动作全部可见、无重叠、文案未截断到不可理解；摘录条完整显示整句。轻微观感问题：选区尾柄与页脚进度区在右下重叠一瞬（不构成遮挡关键动作） | `60-landscape-toolbar.png` |
| I.3 | 应用内字号临时调大 | **PASS** | 25 → 33 号（点 8 次 `A +`），正文仍可读，行距按比例放大 | `61/62` |
| I.4 | 大字号下工具栏 | **PASS** | 33 号下 5 个动作仍无重叠/无截断、未超屏；色板与按钮仍可点击；手柄未遮挡任何动作按钮 | `62-large-font-selection-toolbar.png` |
| I.5 | TalkBack 焦点顺序 / 朗读名称 / 角色 / 手柄语义 | **NOT TESTED（设备未启用，禁止擅自开启）** | 见 I.6 结构检查 | — |
| I.6 | **结构检查**（非 TalkBack） | **PASS，含 2 项发现** | 正面：色板有规范 `content-desc`（`高亮颜色 yellow/red/green/blue/purple`）、底部动作有 `复制/浏览器/替换/字典/更多` 文本标签、工作表有 `关闭工作表`/`关闭当前工作表`、翻页区有 `上一章/下一章`、笔记/灵感面板有语义标题。**发现 1（D-4）**：legacy 分页 EPUB 正文完全不在无障碍树内。**发现 2**：Compose 侧大量可点节点在 uiautomator 中报 `clickable=false`（例如底部动作、目录项），屏幕阅读器用户可能无法通过"可点击"语义发现这些操作 | 全部 dump |
| I.7 | 恢复屏幕方向与应用字号 | **完成** | `accelerometer_rotation` 1→0→**1**，`user_rotation` 0→1→**0**；`wm size` 仍 1220×2712；字号 33→**25**（在测试 TXT B 上验证回读值） | §16.1 |

---

## 15. 多高亮 + TTS 性能体感（矩阵 J）

| 子项 | 判定 | 实际 |
|---|---|---|
| J.1 三格式各建多条高亮 | **PASS** | 测试 TXT 2 条（第1章/第2章各 1，均黄色）、测试 Markdown 2 条（黄 + 红，红即 D-1 伪高亮）、测试EPUB 2 条（绿 + 蓝） |
| J.2 较长章节快速滚动 | **PASS（操作完成）** | 在 legacy EPUB 滚动视图与滚动 TXT 中连续快速 fling |
| J.3 同时开 TTS 滚过多条高亮 | **PARTIAL** | TTS 朗读态下正文自动推进过第1→第4章，未观察到触摸失灵或卡死；但"朗读 + 手动高速滚动 + 多条高亮"三者同时的窗口未完整覆盖（见 §12 阻碍 1） |
| J.4 搜索高亮 / 持久高亮 / TTS 当前句是否可区分 | **PASS** | 持久高亮用纸张高亮色底（黄/绿/蓝/红），TTS 当前句用另一套浅色底，选区用灰蓝底 + 自绘手柄，三者视觉上可区分；未发现高亮闪烁、错位或串章 |
| J.5 叠加后正文是否仍可阅读 | **PASS** | 单条高亮叠加下正文清晰可读 |
| J.6 只读 `dumpsys gfxinfo` | **已采集，结论需复核（D-7）** | 见下表 |
| J.7 未修改系统动画/开发者选项/性能模式 | **PASS** | 仅执行 `dumpsys gfxinfo <pid> reset` 与只读 dump |

**gfxinfo 实测（legacy/非分页 EPUB 滚动视图，含 2 条持久高亮，30 次 120ms 合成 fling）**：

```
Total frames rendered: 423
Janky frames: 407 (96.22%)
Janky frames (legacy): 421 (99.53%)
50th percentile: 40ms   90th: 46ms   95th: 48ms   99th: 53ms
50th gpu percentile: 5ms  90th gpu: 5ms  95th gpu: 5ms  99th gpu: 5ms
```

**解读与保留（必须一起看）**：GPU 侧稳定 5ms 而 CPU 侧中位 40ms，说明瓶颈在**主线程/组合期**，不是绘制或 GPU —— 与 F4.1 报告遗留的"`addStyle` 额外遍历代价未测"方向一致。但这是**合成手势风暴**下的数字，本轮**没有在同一视图、同一手势下跑"零高亮"对照组**，因此不能把这 96.22% 全部归因于高亮叠加。人工体感上未出现卡死或触摸失灵。判定：**PARTIAL，需复测**（缺陷 D-7，P2，附明确的方法学缺口）。

---

## 16. 测试数据清理与设备设置恢复

### 16.1 设备与应用设置：修改前 / 临时值 / 恢复后

| 设置 | 修改前 | 临时值 | 恢复后 | 状态 |
|---|---|---|---|---|
| `global stay_on_while_plugged_in` | `3` | 未改 | `3` | ✅ 未触碰 |
| `system accelerometer_rotation` | `1` | `0` | `1` | ✅ |
| `system user_rotation` | `0` | `1`（横屏） | `0` | ✅ |
| `wm size` / `wm density` | 1220×2712 / 480 | 未改 | 1220×2712 / 480 | ✅ |
| `secure default_input_method` | `com.tencent.wetype/.plugin.hld.WxHldService` | `com.sohu.inputmethod.sogou.xiaomi/.SogouIME`（为输入替换文本） | `com.tencent.wetype/.plugin.hld.WxHldService` | ✅ |
| `POST_NOTIFICATIONS`（本应用） | `granted=false` | `granted=true`（为跑 TTS） | `granted=false`（`pm revoke`，退出码 0） | ✅ 权限位已回退；差异：原值带 `USER_SET` 标志，revoke 后该标志消失，功能等价（均为拒绝） |
| 应用内字号（测试 TXT B） | 25 号（继承全局） | 33 号 | **25 号**（已回读确认） | ✅ |
| 应用内全局 `reader_mode` | `paged` | 未改（只改本书） | `paged` | ✅ 未被污染 |
| 应用内全局 `pager_engine_mode`（TXT） | `on` | 未改 | `on` | ✅ 未被污染 |
| **应用内全局 `epub_pager_engine_mode`（EPUB）** | **`auto`** | **`off`**（在"只改本书"作用域下改的） | **`auto`**（已回读 DataStore 确认） | ✅ 已恢复，但**过程本身是缺陷 D-2** |
| 测试书 per-book `readerMode=scroll` | 无 | 3 本测试书被设为上下滚动 | **未恢复** | ⚠️ 见 16.3 |

### 16.2 本轮产生的测试数据（逐项列出，含可否清理）

| 类别 | 内容 | 处置 |
|---|---|---|
| 设备文件 | 应用已授权的 SAF 书库根目录下新建的 `cra-s07-test/` 子目录中 7 个文件（`t-txt-s07.txt`、`t-txt-b-s07.txt`、`t-txt-long-s07.txt`、`t-md-s07.md`、`t-md-b-s07.md`、`t-epub-s07.epub`、`t-img-s07.png`）。完整路径含用户个人目录名，按隐私约定不在本报告记录 | **保留未删**（新建子目录，未改动任何用户既有书籍文件） |
| 书架 | 6 本测试书：`t-txt-s07`、`t-txt-long-s07`、`t-txt-b-s07`、`t-md-s07`、`t-md-b-s07`、`测试EPUB` | **保留未删** — 任务只授权清理高亮/批注/纠错规则，未授权删书；删书会连带影响进度记录，交由用户/Codex 决定 |
| 高亮 | 测试 TXT 2 条；测试 Markdown 2 条（其中 1 条为 D-1 的伪高亮）；测试EPUB 2 条 | **保留** — 均可准确识别（只在测试书上产生），但删除需逐本逐条定位，成本高；随测试书一并移除即可 |
| 笔记 | 测试 TXT 1 条（第2章，摘录+粘贴草稿） | **保留** |
| 纠错规则 | 测试EPUB 1 条 `testfixA`，状态 **已撤销 · 正文保留原文**，正文已确认恢复 | **正文已恢复；规则记录无法删除**（UI 只提供"恢复纠错"，无删除入口）→ 遗留项 |
| 灵感 | 无（只打开面板查看预填，未保存、未发送 AI） | — |
| 阅读进度 | 6 本测试书各有进度；用户真实藏书进度未被写入（未打开做写操作） | — |

### 16.3 未恢复项（明确列出，不掩盖）

`app_settings.preferences_pb` 中残留 3 个 per-book 覆盖：
`book_reader_override_d5bd07ef-9fb8-4469-9a21-335bef60ba63_readerMode=scroll`、
`book_reader_override_9a85d098-381e-4935-b84c-b2d4eb78b575_readerMode=scroll`、
`book_reader_override_epub_zhrum_readerMode=scroll`。

**影响面已核实为零外溢**：这三个 bookId 与另一处 `fontSize` 覆盖的 bookId，全部不在验收开始前 `files/books/` 的目录清单（用户 5 本书：`1c617f67…`、`d310ad01…`、`f37e5943…`、`f5389b88…`、`fe4c6a9d…` + `epub`）内，即**只作用于本轮新建的测试书**；全局 `reader_mode` 仍为 `paged`，未被改动。

**未恢复原因**：该阅读器控制栏约 2–3s 自动隐藏，跨工具调用的秒级延迟使"打开设置→翻页与按键→左右翻页"链路反复落空；在剩余时间预算内未能稳定完成。**恢复方式**：打开对应测试书 → 阅读设置（只改本书）→ 翻页与按键 → 阅读模式选"左右翻页"；或直接删除该测试书，覆盖随之消失。

---

## 17. 缺陷清单

### P0：0 项

### P1：0 项

**F4.1 声称修复的三项在真机上均未复现**：① 分页 EPUB 单处纠错被 VM 误拒 → 已修好（§9.1 全链通过，无"格式不支持"）；② 切书旧选区可被重新捕获 → 未复现（§11 灵感/正文/工具栏三项隔离全绿）；③ 外部动作提示不真实 → 未复现（§13 H.1 无前置"正在打开"）。

### P2：4 项

| ID | 缺陷 | 复现步骤 | 证据 | 严重度理由 |
|---|---|---|---|---|
| **D-1** | **测试 Markdown 图片块轻点生成"伪文字选区"，并可写入持久高亮** | ①打开测试 Markdown（阅读模式=上下滚动）②轻点图片块 ③摘录显示 **alt 文本 `示意图`**，工具栏弹出 ④更多→高亮→红 → 提示 `已高亮`，红底落在图片块上 ⑤关闭重开，该伪高亮仍回显 | `51-md-image-pseudo-selection.png`、`52-md-image-highlight.png`、`53-md-highlights-after-reopen.png` | 直接违反矩阵 B.2"图片、分隔线等不可选节点不生成伪文字或伪坐标"；且伪记录会持久化。未造成串章/写错书，故 P2 非 P1。代码位置：`ReaderMarkdownRender.kt:251-275`（`clickable` 挂载条件未排除 `DocBlock.Image`） |
| **D-2** | **"只改本书"作用域下修改 EPUB 分页兼容模式，实际写入全局默认** | ①测试EPUB → 阅读设置（面板显示 ✓ 只改本书）②高级与偏好设置 → 排版引擎兼容 ③EPUB 分页兼容模式：自动 → 关闭 ④只读读取 `app_settings.preferences_pb`：全局键 `epub_pager_engine_mode = off`，且**无**任何 `book_reader_override_*_epubPagerEngineMode` 键 | DataStore 前后两次只读解析（`auto`→`off`→恢复 `auto`） | 根因已定位：`ReaderOverride.kt:14-34` 的 `ReaderOverrideKey` 枚举**不含** `pagerEngineMode` / `epubPagerEngineMode`，即这两项结构上只能全局；但设置面板仍在"只改本书"作用域下暴露该控件，用户以为只影响一本书，实际改变**所有 EPUB**（含用户真实藏书）的排版路径。可一键恢复、无数据损坏 → P2 |
| **D-3** | **滚动 TXT：拥有选区的 ReadingUnit 被回收后，选区工具栏与摘录仍常驻** | ①测试 TXT（上下滚动）第1章长按选区 ②连续下滑 24 次至第6章（第1章单元已 dispose，无障碍树只剩第6章节点）③工具栏与第1章摘录仍在 ④从该工具栏保存高亮 → 写入屏幕上不存在的选区（落点仍正确） | `18/19/20/21/22` 截图序列 | 与代码显式意图相反：`ReaderContentHostPlainTextBranch.kt:350-370` 的注释写明"单元离开组合时…必须请求清除——**否则工具栏会继续引用一段不可见的文字，此时保存高亮/笔记会落到屏幕上不存在的选区**"，`DisposableEffect` 的清除在真机上未生效。未产生错误数据 → P2/P3 边界，按契约违背取 P2 |
| **D-7** | **legacy EPUB 滚动视图快速滚动帧率严重劣化（需复测）** | ①测试EPUB：EPUB 分页兼容=关闭、阅读模式=上下滚动（含 2 条持久高亮）②`dumpsys gfxinfo <pid> reset` ③30 次 120ms 合成 fling ④`dumpsys gfxinfo <pid>` | `Janky 407/423 = 96.22%`，`50th 40ms / 90th 46ms / 99th 53ms`，GPU 各分位恒 `5ms` | CPU 侧瓶颈、GPU 空闲，方向与 F4.1 遗留的 `addStyle` 遍历代价一致。**但本轮未做"同视图同手势、零高亮"对照组，也未跑 TTS 同开**，因此不能把该数字全部归因于高亮；体感上无卡死/失灵。取 P2 并要求复测，而非 P1 |

### P3：5 项

| ID | 缺陷 | 复现 | 证据 |
|---|---|---|---|
| **D-4** | legacy **分页** EPUB（`PagedEpubView`）正文完全不在无障碍树中，屏幕阅读器用户读不到正文 | EPUB 分页兼容=关闭 + 阅读模式=左右翻页 → `uiautomator dump` 只有 `阅读正文已就绪` 与 `下一章` 两个节点，无任何正文文本 | `41-legacy-epub-render.png` + 该视图 dump（14→2 节点）对比滚动视图（逐块带文本且 `clickable=true`） |
| **D-5** | legacy EPUB 滚动视图中**无法用轻点收起选区工具栏**：每次轻点正文都会重新整块选中该块，唯一出口是 `更多→取消选择` | 轻点任一正文块 → 工具栏出现；再次轻点另一块 → 摘录换成新块；工具栏不消失 | §10 D.7；代码 `ReaderContentHostEpubBranch.kt:315`（`clickable` 无条件消费，仅坐标不可靠时才回落 `onToggleControls`） |
| **D-6** | 拒绝 `POST_NOTIFICATIONS` 后点"听书"**静默无反应**，无任何解释文案 | 权限设为拒绝 → 底部控制栏点 听书 → 只弹一次 MIUI 授权框；选"拒绝且不再询问"后再点 听书 → 无朗读、无提示、无引导 | 连续 3 次点击后 `com.xiaomi.mibrain.speech` 无 `state:started`，界面无变化 |
| **D-8** | 分页正文中长按落在**段落间隙**时不产生选区，也无任何反馈 | 测试 TXT 分页模式，长按坐标落在两段之间（设备 y≈844）→ 无选区、无工具栏、无提示；改到行内 y≈975 立即成功 | 首次尝试的 3 次空 dump + 定位后成功对比 |
| **D-9** | 书内搜索按**原文**索引：纠错生效后，搜替换后的文本得"未找到匹配结果"，搜索命中片段与跳转后正文不一致 | 保存单处纠错（原文→`testfixA`）后，书内搜索 `testfixA` → `未找到匹配结果`；搜原句 → `共 9 处结果`（含已被替换的 1 处） | §9.1 步 8 与补充观察 |

### 另有 1 项非缺陷但需产品裁决

底部槽位复用导致**朗读控制条与选区工具栏不能同时可见**（§12 阻碍 1）。这是设计选择，但它使"TTS + 选区 + 消费者"三态链路在真机上难以同时保持，直接影响 S07 矩阵 G 的可测性。

---

## 18. 未覆盖项

| 项 | 判定 | 缺少的前置 / 原因 | 已有 JVM 覆盖指路 |
|---|---|---|---|
| 空选区外部动作提示（H.5） | NOT TESTED | 外部动作入口只存在于选区工具栏内，无选区时工具栏不出现，真机不可构造 | `ReaderSelectionExternalActionsTest` 的 `NOTICE_BLANK_SELECTION` |
| 系统文字工具成功提示（H.2） | NOT TESTED | 本机 `字典` 入口指向应用内离线词库面板（无离线词库），未走 `PROCESS_TEXT` | 同上 |
| 在线词典成功/失败（H.3/H.4） | NOT TESTED | 该入口未在本机 UI 暴露 | 同上 |
| 各类 `NO_HANDLER` 失败文案（H.6） | NOT TESTED | 本机有可用浏览器与文字工具；禁止卸载/停用系统应用来制造失败 | 同上（穷举 `when`，无 `else`） |
| TalkBack 焦点顺序 / 朗读名称 / 角色 / 手柄语义（I.5） | NOT TESTED | 设备未启用 TalkBack；禁止擅自改变用户长期无障碍设置。**已按任务要求做结构检查并只报"结构检查"，不写 TalkBack 真机 PASS** | — |
| force reload（错误页重试）清选区/快照/消费者残留（F.6） | NOT TESTED | 需把测试书推入加载失败态（破坏文件或改库），任务 §4.4 禁止 | `a force reload rebuilds the document so it clears the selection too` |
| 规则消费者在 TTS+dispose 下的快照（G） | NOT TESTED | 朗读态下底部槽位被工具栏/朗读条互斥占用，控制栏自动隐藏使链路难以稳定 | `ReaderSelectionSnapshotTest` 24 项 |
| 笔记/灵感 locator 跳转回正确位置（B.9 / G） | NOT TESTED | `更多→笔记与标注` 多次点击被自动隐藏的控制栏吞掉，未在预算内完成 | `MarkdownBlockSelectionLocatorTest` 10 项 |
| legacy EPUB 跨章重复块高亮不串位（D.3 跨章例） | PARTIAL | 该视图对合成快速滑动无位移响应，未翻到第2章同文块 | — |
| legacy EPUB 拒绝命令的"只有一条准确提示"（D.5） | NOT TESTED | 入口被摘除，无法从真机发起被拒绝的纠错命令 | `ReaderSingleCorrectionCapabilityTest` 场景 3/4 |
| 真·小屏设备布局（I 的"小屏"） | NOT TESTED | 只有一台 6.67" 真机；已用横屏 + 33 号字做等效挤压 | — |
|  androidTest 插桩测试 | 未运行 | 本轮为真机验收，未跑插桩；且既有记录显示 MIUI 会拦 androidTest 包安装 | — |
| `:app:lintDebug` | 未运行 | 本轮任务书只要求 `assembleDebug` | — |

---

## 19. 总结判定

# **PARTIAL**

### 未触发任何 FAIL 判据

| FAIL 判据 | 是否发生 | 依据 |
|---|---|---|
| 崩溃 | 否 | 全程无 ANR / 无进程重启（`pidof` 稳定，`dumpsys window` 焦点正常） |
| 数据写错书 | 否 | §11 F.5 逐 bookId 只读核对，全部落在本轮测试书上 |
| 旧书选区进入新书 | 否 | §11 F.3/F.4c：新书无 A 工具栏、无 A 文字、灵感不带 A 摘录 |
| 坐标串章 | 否 | §7 A.11（跨 5 章后仍落第1章）、§9 C.3/C.4（第2/3章同句不受影响）、§8 B.7（跨文档同句无高亮） |
| 分页 EPUB 纠错错误落库或无法保存 | 否 | §9.1 九步全链通过，`命中 1 处`→`生效中`→正文变→重开仍生效→只改 1 处→撤销后恢复 |
| 三种核心格式选区/保存/重开链失败 | 否 | TXT（分页+滚动）、Markdown（整块）、EPUB（分页 + legacy）三链的选区→保存→重开回显全部通过 |

### 但不能判 PASS，原因有四

1. 存在 **4 项 P2**（判 PASS 要求"没有 P0/P1/P2"）。其中 **D-1 直接是矩阵 B.2 核心必测项的失败**，**D-2 是"只改本书"作用域契约被打破并波及全部 EPUB**。
2. **§16.3 有未恢复项**：3 个 per-book `readerMode` 覆盖仍留在测试书上（影响面已核实不外溢到用户书籍与全局默认）。
3. **矩阵 G 只到 PARTIAL**：规则消费者快照与 locator 跳转未覆盖。
4. **矩阵 J 的帧率结论方法学不完整**（无零高亮对照组），D-7 需复测。

### 与开发侧证据的关系

本轮**证实**了 Zcode F4.1 的三项修复在真实设备上确实生效、且分页 EPUB 单处纠错这条此前"从未跑过真机"的链路完整可用；本轮**没有**把任何 JVM 数字当作真机 PASS 依据，也**没有**声称发布完成。

---

## 20. 是否建议 S07 进入下一阶段

**有条件建议：可以进入下一阶段，但 D-1 与 D-2 必须在 S07 关闭前修掉并复验，D-7 必须补一次带对照组的复测。**

理由：

- S07 的核心目标——三种格式的选区→保存→重开链、跨书隔离、分页 EPUB 单处纠错、legacy 路径的纠错入口摘除——已在真机上通过，且未发现任何 P0/P1，未发现数据正确性问题。这些是 S07 的验收主体，主体成立。
- D-1（Markdown 图片伪选区+伪高亮）属 S07-B 矩阵内的明确契约违反，修复面很小（`ReaderMarkdownRender.kt:251-275` 的 `clickable` 挂载条件排除非文本块即可），不应带着它关闭 S07。
- D-2（分页兼容模式越出"只改本书"作用域）不属 S07 选区范围，建议**单独登记给 Codex 裁决归属**（它同时影响所有 EPUB 用户，优先级不低，但归属可能是设置子系统而非 S07）。
- D-3、D-5、D-6、D-8、D-9 与 4 项 P3 可进 S07 缺陷池随后续切片处理，不阻断。
- 未覆盖项（H 组 5 项、TalkBack、force reload、G 的规则消费者、B.9/G 的 locator 跳转）需要在 S07 关闭前明确"接受为未覆盖"还是"补测"，不能默认视为通过。

**本报告不写"发布完成"。S07 队列的 S08–S20 保持 HOLD，本轮未领取任何下一片，未修改任何产品源码。**

---

## 附：命令与退出码索引

| # | 命令 | 退出码 | 关键输出 |
|---|---|---|---|
| 1 | `adb devices -l` | 0 | `c49ac6cf device product:diting model:22081212C` |
| 2 | `cmd.exe /c "gradlew.bat :app:assembleDebug"` | 0（**假**，已丢弃） | 仅 Windows 版本横幅，0 秒 |
| 3 | `./gradlew.bat :app:assembleDebug --console=plain` | 0 | `BUILD SUCCESSFUL in 1m 4s` |
| 4 | `sha256sum app-debug.apk` | 0 | `cb4b921e…20663f` |
| 5 | `powershell -File scripts/install_with_confirm.ps1 -Apk … -Serial c49ac6cf -TimeoutSeconds 120` | **0** | `RESULT: CONFIRM_LOOP` |
| 6 | `settings get global stay_on_while_plugged_in`（前/后） | 0 | `3` / `3` |
| 7 | `pm grant …POST_NOTIFICATIONS` / `pm revoke …POST_NOTIFICATIONS` | 0 / 0 | `granted=true` → `granted=false` |
| 8 | `ime set com.tencent.wetype/…` | 0 | `Input method … selected for user #0` |
| 9 | `dumpsys gfxinfo <pid>` | 0 | Janky 96.22%，50th 40ms，GPU 5ms |
| 10 | `run-as … cat app_settings.preferences_pb`（只读，3 次） | 0 | `epub_pager_engine_mode`: `auto`→`off`→`auto` |
| 11 | `dumpsys audio` / `dumpsys window` / `uiautomator dump` / `screencap` | 0 | 只读取证 |
| 12 | `git rev-parse HEAD` | 0 | `5aed5939600e491843e3c44453921bd6273a233c`（未推进） |

未执行：`commit` / `push` / `stage` / `reset` / `checkout` / `clean` / `stash` / 全仓格式化 / 任何产品源码或配置修改 / 任何清除应用数据的命令。
