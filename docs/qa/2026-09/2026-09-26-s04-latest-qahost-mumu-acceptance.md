# S04 最新 qahost APK 的 MuMu 独立验证报告

- 日期：2026-09-26
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（分支 `codex/recover-worktree-20260920`）
- 设备：MuMu 实例 1，serial `127.0.0.1:16416`；全部设备命令显式该 serial，真实手机 `c49ac6cf` 仅出现在 `adb devices` 列表中、**未执行任何针对它的命令**
- 待测 APK（Codex 集成者交付）：`E:\creation-reading-assistant\codex-s04-build-20260925\project-builds\app\outputs\apk\qahost\app-qahost.apk`
- 性质：**MuMu 模拟器验证**。本报告不主张真机验收、不主张竖屏 UI 表现
- 证据文件（UI dump / 截图）保存在 `E:\creation-reading-assistant\codex-s04-build-20260925\temp\s04-acc\`，**未放入仓库**；设备端 `/sdcard/qa_*` 临时文件已清理（剩 0 项）

---

## 1. 结论摘要

| # | 验收项 | 结果 |
|---|---|---|
| 1 | APK 身份核对（哈希 + 包名） | **PASS** |
| 2 | 安装（正式入口先试 → 授权 FALLBACK）+ 安装后哈希回读 | **PASS**（FALLBACK 标记，未冒充 CONFIRM_LOOP） |
| 3 | qahost 既有数据保留（21 本 + 进度行） | **PASS** |
| 4 | 来源更新失败原因可见、可读（入口 B：确认刷新 → Snackbar） | **PASS** |
| 5 | 重新定位失败反馈可见、可读（入口 A：格式不匹配 + 残留拒绝两条） | **PASS** |
| 6 | 刷新成功路径与沙箱恢复（非成功路径冒充） | **PASS** |
| 7 | 代表性 TXT / EPUB 打开、位置保持（新二进制复查） | **PASS** |
| 8 | 崩溃 / ANR | **PASS**（应用相关 0；3 条 FATAL 均为模拟器系统进程，已甄别） |
| 9 | 部分删除 / ClearedWithRemains 注入、MD 复查、竖屏、真机 | **未覆盖**（§8） |

---

## 2. APK 身份核对

| 项 | 值 |
|---|---|
| 本地 APK SHA-256（实算） | `24525998f69ab41a7fb1f6edea1e26783d0c4f45c922d87c4a575b0d95d0794b` |
| 预期 SHA-256（Codex 交付值） | `24525998F69AB41A7FB1F6EDEA1E26783D0C4F45C922D87C4A575B0D95D0794B`（忽略大小写后一致） |
| aapt 包名 | `com.creationreadingassistant.qahost`，versionCode 3，versionName 0.5.1，minSdk 24 / targetSdk 35 |
| 构建时间 | 2026-09-26 01:14（晚于同日早间 `064F…` 与 20:48 `08c287…` 两版） |

**命令与退出码**：

```
sha256sum E:\…\app-qahost.apk                     → 与预期一致（exit 0）
aapt dump badging E:\…\app-qahost.apk | grep -E   → exit 0
```

---

## 3. 安装（含 FALLBACK 证据链）与数据保留

### 3.1 安装前状态

```
adb -s 127.0.0.1:16416 shell pm path com.creationreadingassistant.qahost
  → /data/app/~~kxuVDfxaGGAmUhOOWy03lw==/…/base.apk
adb -s 127.0.0.1:16416 shell sha256sum <上述路径>
  → 08c287642a2cb26cfff8fa8862e845015c0318e63bb429cc496578c2e0d75de7   ← 旧包（20:48 版）
```

证明安装前设备上是**旧包**，本次验证对象是**新包**。

### 3.2 正式入口先试（install_with_confirm.ps1）

```
powershell.exe -NoProfile -ExecutionPolicy Bypass -File android/scripts/install_with_confirm.ps1 \
  -Serial 127.0.0.1:16416 \
  -Apk "E:\creation-reading-assistant\codex-s04-build-20260925\project-builds\app\outputs\apk\qahost\app-qahost.apk" \
  -PackageName com.creationreadingassistant.qahost -TimeoutSeconds 90
```

输出证据（关键行）：

```
FALLBACK: confirm loop did not complete (installer UI never appeared (MIUI security popup or
wrong VIEW handler); UI dumps parsed with errors: 0 (other UI action errors: 0)); trying direct adb install.
RESULT: FALLBACK_DIRECT_INSTALL (installed via direct adb install; NOT a confirm-loop PASS)
```

- MuMu 上不存在 MIUI 安装器，确认循环 90 秒未出现安装 UI（失败证据已留：90 秒 UI dump 拉取记录 + 解析错误计数 0）；
- 脚本随后执行**用户已授权**的直接 ADB 回退，结果标记 `RESULT: FALLBACK_DIRECT_INSTALL`；
- **按纪律该次安装只能标注为 FALLBACK，不标注 CONFIRM_LOOP PASS**；
- 说明：脚本的 shell 退出码因报告采集时经管道掩蔽未单独捕获，以上文 RESULT 标记行为准（脚本内该路径为 `exit 2`）。

### 3.3 安装后回读

```
adb -s 127.0.0.1:16416 shell pm path com.creationreadingassistant.qahost
  → /data/app/~~i8dhjvsUb3xJafmjXMHiVw==/…/base.apk        （安装目录已更换）
adb -s 127.0.0.1:16416 shell sha256sum <新路径>
  → 24525998f69ab41a7fb1f6edea1e26783d0c4f45c922d87c4a575b0d95d0794b   ← 新包 ✓
```

### 3.4 数据保留（install -r 不清数据）

| 项 | 安装后实查 |
|---|---|
| 在库书籍数（`books where deleted_at is null`） | **21** |
| 进度行（抽查） | sample-006.txt 0.6155 / sample-001.epub 50.5042 / sample-012.md 50.3896 —— 与安装前完全一致 |

---

## 4. 来源更新失败原因：可见、可读（入口 B）

### 4.1 验证前置（沙箱注入与还原，全程披露）

qahost 沙箱的 21 条来源引用系**播种写入**：`root_id` 全为空、qahost 无持久 SAF 授权、
`library_root` 未配置 → 来源更新检测对全部引用静默跳过（设计为「宁可漏报」），
下拉刷新恒报「书架已是最新」（已实测并截图）。为使入口 B 端到端可达，做了三项注入，
**每一项均记录并在测后还原或声明**：

| 注入 | 目的 | 还原 |
|---|---|---|
| `touch /sdcard/Download/cra-s04-samples-20260925/sample-002.epub`（仅 mtime，内容/大小不变） | 触发「来源已更新」检测 | 未还原 mtime（内容零变化；见 §6 成功路径后基线自洽） |
| `run-as … touch files/books/epub/epub_mva894.backup`（0 B 残留标记，sample-002 的 EPUB 残留守卫位） | 触发残留守卫拒绝 | **已删除**（测后 `ls | grep backup` = 0） |
| `update library_source_refs set root_id='primary:Download/cra-s04-samples-20260925' where book_id='epub_mva894'`（与真实文件位置一致，树授权已存在） | 使检测器可 stat 该文件 | **已还原为 null**（测后回查确认） |

另：为让检测器拿到 treeUri，通过应用自身 UI（我的书籍目录 → 设置书籍目录 → 授权）持久化了
`library_root`（`content://…/tree/primary%3ADownload%2Fcra-s04-samples-20260925`）与系统树授权——
这是应用正常流程产生的合法配置，保留。

### 4.2 检测命中与确认弹窗

下拉刷新后（uiautomator dump 原文）：

```
同步源文件更新？
检测到 1 本书（阶段A隔离测试EPUB）的源文件已被修改，是否重新同步正文？
同步不会主动清除已有阅读记录；但正文更新后章节会重新划分，阅读位置也可能随之变化。
[取消] [重新同步]
```

（书名「阶段A隔离测试EPUB」为项目标准中性测试名。）

### 4.3 失败 Snackbar（本验收核心证据）

点「重新同步」约 1 秒后 dump，Snackbar 原文：

```
已同步 0 本书的源文件更新；1 本刷新失败：阶段A隔离测试EPUB
（原因：检测到未完成的替换残留，本次替换已停止；残留备份已保留，重复重试不会自动恢复该备份）
```

逐项核对：

| 要求 | 证据 |
|---|---|
| 失败原因可见、可读 | 原因内嵌于汇总，一句读完 |
| 「本次已停止」 | ✅ 明示 |
| 「反复重试无效」 | ✅ 「重复重试不会自动恢复该备份」 |
| 不引导删书 / 清备份 | ✅ 无「删除/清空/清理/重新下载」字样 |
| 不宣称能自动恢复 | ✅ 仅否定式「不会自动恢复」 |
| 不误报成功 | ✅ 「已同步 0 本」如实 |
| 不泄露书籍内容 | ✅ 仅书名（书卡既有信息）+ 失败原因，无正文 |

**PASS**。该文案与实现报告
`docs/coordination/legacy-stages/2026-09/2026-09-25-s04-replacement-feedback-implementation.md` 的
`buildSourceRefreshSummary` 设计逐字对应。

---

## 5. 重新定位失败反馈：可见、可读（入口 A，两条独立证据）

### 5.1 格式不匹配（sample-001 / UI 名「测试EPUB」）

长按书卡 → 动作面板 → 「重新定位文件」→ SAF 选取器选 `sample-006.txt`（原书为 EPUB）：

```
修复失败：格式不一致：原书为 EPUB，所选为 TXT
```

失败原因明确；选文件失败于解析前置检查，书籍零改动。

### 5.2 残留守卫拒绝（sample-002，注入残留期间）

长按其卡片 → 「重新定位文件」→ 选取器选 `sample-003.epub`（扩展名 EPUB，格式检查通过后
在残留守卫处拒绝）：

```
修复失败：检测到未完成的替换残留（epub_mva894.backup），本次替换已停止；
旧正文若仍存在，则未被本次操作修改，残留备份已保留。重复重试不会自动恢复该备份
```

完整守卫文案（含备份文件名）原样可见；三项关键语义齐全；无破坏性引导。**PASS**。

---

## 6. 刷新成功路径与沙箱恢复（证明注入可逆、成功不冒充）

移除注入的 `.backup` 后再次下拉刷新 → 确认弹窗再次命中 → 「重新同步」：

```
Snackbar：已同步 1 本书的源文件更新
库核对：sample-002 引用 last_modified 1790340245000（08:44 基线） → 1790357167000（触碰后）
```

- 刷新**真实成功**（非把成功路径冒充失败路径）：基线推进、来源副本与源文件重新对齐；
- 沙箱最终状态：注入的 `.backup` 已删、`root_id` 已还原 null、源文件 mtime 保持触碰值
  （内容与导入时逐字节相同，基线与 mtime 自洽）；`library_root` 配置与树授权为应用正常流程产物，保留。

---

## 7. 代表性 TXT / EPUB 复查（新二进制）

| 操作 | 证据 | 判定 |
|---|---|---|
| TXT（sample-006）重开 | 直接恢复 `TXT · 第6章 … · 0%`、全书 **0.6%**（升级安装前位置）；翻页手势正常响应 | PASS |
| EPUB（sample-001）重开 | 直接恢复 `EPUB · 第四章 回信 · 50%`、全书 **50.5%**（跨安装包升级保留） | PASS |
| 目录入口 | TXT/EPUB 底栏「目录」均可达（本轮以打开+保持为主，全目录遍历沿用 09-25 验收） | PASS |

---

## 8. 稳定性（崩溃 / ANR）

`logcat -d` 全量检索：

| 检索 | 计数 | 甄别 |
|---|---|---|
| `FATAL EXCEPTION` | 3 | **全部为模拟器系统进程**：`com.huawei.hwid.core` ×2（08:40 / 10:45，HMS 框架自身 `NoClassDefFoundError`）、`com.google.android.gms` ×1（10:41）；均早于或独立于本验收操作，与本应用无关 |
| `FATAL EXCEPTION` 中属 `com.creationreadingassistant.*` | **0** | — |
| `ANR in com.creationreadingassistant` | **0** | — |

---

## 9. 未覆盖项

| # | 项 | 原因 |
|---|---|---|
| 1 | 部分删除 / `ClearedWithRemains`（清缓存遇残留拒绝链路） | 需在「清缓存」UI 路径上注入部分删除现场；本轮注入范围限定于反馈链验收（残留标记 + root_id + mtime），未执行清缓存破坏性路径。按任务要求列为未覆盖，不以普通成功路径冒充 |
| 2 | MD 三样本复查 | 本轮任务指定复查代表性 TXT/EPUB；MD 链路已于 09-25 验收（`2026-09-25-s04-qahost-mumu-reading-acceptance.md`），本轮未重复 |
| 3 | 竖屏 UI / 折叠屏 / 真机 | MuMu 横屏 + 任务禁真机 |
| 4 | 来源更新检测对**全部** 21 本的可达性 | 沙箱播种引用 root_id 为空是结构性现状（仅对被注入的 sample-002 开洞验证）；批量 root_id 修复属集成者事项 |
| 5 | androidTest instrumentation | 本轮未安装 test 包 |

---

## 10. 合规自证

| 动作 | 是否执行 |
|---|---|
| 全部设备命令显式 `-s 127.0.0.1:16416` | ✅ |
| `adb devices` 确认真实手机 `c49ac6cf` 在列但零操作 | ✅ |
| 安装前哈希核对 / 安装后回读 | ✅ |
| 保留 qahost 数据（未卸载、未清数据、未点清理/删除/重置） | ✅ |
| 正式安装入口先试，失败证据留痕后才 ADB FALLBACK | ✅ |
| 注入项全披露 + 可还原项全部还原 | ✅ |
| 报告仅中性样本编号与测试名，无真实书名/正文 | ✅ |
| 截图/私有数据入仓库 | ❌（证据在 E 盘 temp） |
| 修改产品源码 / 测试 / 构建配置 | ❌ |
| stage / commit / push / reset / checkout / clean | ❌ |
| 触碰原始仓库 / 用户书籍 / E 盘备份区（本轮仅使用用户指定的 E 盘构建目录） | ❌ |

唯一新增文件：本报告。证据目录：
`E:\creation-reading-assistant\codex-s04-build-20260925\temp\s04-acc\`
（qa_fb_entryB.png / qa_fb_entryA.png / qa_fb_entryA2.png / qa_dlg2.xml / qa_fb1.xml / qa_fb2.xml / qa_fb3.xml / qa_ok1.xml 等）。
