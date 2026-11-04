# B/C/D 批次真机验收报告 —— 只读证据补证与安全审计

- **审计日期**：2026-09-24
- **审计对象**：
  - [`docs/qa/2026-09/2026-09-23-bcd-portrait-device-acceptance.md`](2026-09-23-bcd-portrait-device-acceptance.md)（原报告，已就地加注更正）
  - [`docs/qa/evidence/2026-09-23-bcd-portrait/commands.log`](../evidence/2026-09-23-bcd-portrait/commands.log)（命令摘要，已就地脱敏 + 加注）
  - [`docs/qa/evidence/2026-09-23-bcd-portrait/screenshot-manifest.md`](../evidence/2026-09-23-bcd-portrait/screenshot-manifest.md)（截图清单，已加批注）
- **被审对象仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`

## 0. 审计边界（先声明做了什么、没做什么）

**本轮只读，未做任何下列动作**：未运行 `adb`、未连接或操作手机、未重装 APK、未重测、
未删除或移动任何文件、未修改任何产品/测试源码、未执行
`git reset / checkout / clean / stash / add / commit / push`。
审计过程中只新增了「脱敏副本」与本报告两类文件。

**本轮实际采用的手段**：读取仓库内文本证据；读取**仓库外的原始截图**并做 SHA-256 复算与像素差分；
读取**本轮真实执行记录**（见下）；读取设备端原始 `uiautomator` dump；对仓库做只读字符串排查。

### 0.1 关键发现：本轮真实执行记录不在仓库内

原报告与两份证据文件是**结论性文本**，不含执行过程。本轮验收的真实过程记录位于仓库外：

| 记录 | 路径 | 说明 |
|---|---|---|
| 执行会话全文 | `C:\Users\23254\.gemini\antigravity\brain\c68db6da-5b7c-4480-b401-44cf18c961f0\.system_generated\logs\transcript_full.jsonl` | 2030 步，2026-09-23 14:47→23:40；B/C/D 轮为第 1526–2029 步 |
| 单条命令原始输出 | 同目录 `\.system_generated\tasks\task-<N>.log` | 每条命令一个文件，**未经润色** |
| 原始截图（8 张） | `C:\Users\23254\.gemini\antigravity\scratch\cra-device-backup-20260923\evidence\` | 与清单同源 |
| 设备端原始 dump | 同目录 `current_dump.xml`（23:37，RTL 态）、`shelf_dump.xml`（23:17）、`sheet_dump.xml`（23:18） | 可直接核对节点 bounds |

> 审计方法说明：结论**只以原始记录与原图为依据**；会话文本中 agent 自己的 `thinking`
> 只在「它能作为当时的现场观察」时引用，不作为事实来源。

### 0.2 审计前基线（脱敏/更正前的原文已完整保留在仓库外）

脱敏与更正**之前**，三份文本文件已原样复制到仓库外私有目录
`C:\Users\23254\.gemini\antigravity\scratch\cra-bcd-originals-20260924\`（复制，未移动、未删除原件）：

| 文件 | 审计前 SHA-256（基线） |
|---|---|
| `2026-09-23-bcd-portrait-device-acceptance.md` | `7f0b015861f821b920cfc7ddf231fa09d38d9c59dbbe271f61d627a8823ddb0c` |
| `screenshot-manifest.md` | `5962b7b6eecddb79a582e740b4056e282f59aadf992d593fdaa3d52695c3c570` |
| `commands.log` | `1fa79d66f47d4675c66ab09ea1ac5ba688e29c5e4d6fe2fccbcb283a01092345` |

---

## 1. 设备图片清理范围

### 1.1 原始证据：存在（命令与执行结果），缺失（清理前文件列表）

- **命令确实执行。** 原始执行记录 `2026-09-23T15:38:39Z`（= `23:38:39 +08:00`）第 2006 步：
  真实调用 `adb -s c49ac6cf shell "rm -f /sdcard/*.png /sdcard/window_dump.xml"`，
  对应任务 `task-2007.log` 记录 **exit code 0，stdout/stderr 均为空**。
- **清理前文件列表：不存在，且无法补取。** 全程**没有**执行过 `ls /sdcard/*.png`、
  `find /sdcard`、`ls /sdcard` 之类的列举命令（全会话检索 `/sdcard` 的列举命令，
  仅命中更早轮次的 `ls /sdcard/Download/ | grep cra-r3`，与本轮无关）。
  agent 在第 2004 步的 `thinking` 里写「设备上可用截图列表已整理」，但**那只是它自己写过的文件名清单，不是设备回读**。

### 1.2 可独立核对的事实

1. 通配符作用域为 **`/sdcard` 根目录、非递归、仅 `*.png`**，同批删除 `/sdcard/window_dump.xml`。
2. 本轮会话记录中**写到 `/sdcard` 根目录的 PNG 共 10 张**，比清单多 2 张：
   - 已 pull 且列入清单的 8 张：`b3_snackbar` / `b3_not_in_filter` / `b3_all_no_stale_scroll` /
     `b2_pair_snackbar` / `b2_fallback_success` / `d_ltr_craslider` / `d_rtl_craslider` / `d_rtl_reader_chrome`。
   - **从未 pull、未列入清单的 2 张**：`b2_snackbar.png`、`b2_tap_view.png`
     （15:29:46 / 15:29:57 产生，属 B-2 **首次尝试**——当时结果提示「成功 1 本，重复 1 本」，
     因 `test-b-multi-beta.epub` 与既有书 `content_hash` 相同被判重复，遂重做）。这 2 张随本次通配一并消失。
3. 更早轮次也往 `/sdcard` 根目录写过 PNG：S03-1 轮的 `01_storage_pre_rebuild.png` … `07_search_hit_jump.png`
   —— 这 7 张**各自在 pull 之后已单独 `rm` 删除**（第 203/209/227/335/344/358/360 步），
   理论不在本次通配命中范围；S04 轮的 `s04_screen_03/04/05*.png` **未见删除记录**。
4. 同一根目录下的 `s05_shelf.xml`、`s05_import_menu.xml`、`ui_dump.xml` 未被匹配（非 `.png`），
   本次通配只删了 `window_dump.xml`。

### 1.3 仍无法确认的部分

**无法确认是否影响非测试 PNG。** 通配符对 `/sdcard` 根目录下**任何** PNG 一视同仁，
既包括本轮自建测试图，也可能包括更早遗留图、以及其他应用/用户自己放在根目录的 PNG。
由于不存在清理前列表，**不得**由清理后目录为空反推「原来只有测试图」。
仅能确认「本轮自建 PNG 中至少有 10 张落在匹配范围内」，**匹配总数不可知**。
现存日志已全部保留，未补造任何记录；本轮**未再触碰设备**去补救。

### 1.4 修订后判定

- 本项本身不是原报告的验收项，但原报告「数据安全防护」一段的**间接结论（只用到测试文件）**证据不足。
- **判定：`PARTIAL`** —— 命令存在、影响范围不可界定。
  建议后续设备验收把「清理前 `ls` 输出」列为强制留证项。

---

## 2. 隐私

### 2.1 原始证据：存在（泄露本身与原始文件都确凿）

- 原报告第 1.2 节与 `commands.log` 第 63 行写入了**真实书库的私有父路径**
  （真实用户名 + 真实藏书目录名），违反「只使用中性名称」约定。**各 1 处。**
- `screenshot-manifest.md` 中**未**出现该路径（已逐字检索确认），无需脱敏。

### 2.2 已执行的处理

1. **先在仓库外保留原始证据**：三份文本的审计前原文已复制到
   `…\scratch\cra-bcd-originals-20260924\`（哈希见 §0.2）。
2. **仓库内脱敏（就地改写，未删除证据文件）**：
   - 原报告第 1.2 节：`/sdcard/<真实父路径>/cra-s04-isolated-test/b_focus/` → `/sdcard/<隔离测试目录>/cra-s04-isolated-test/b_focus/`，并在同处加「审计脱敏」注记。
   - `commands.log` 第 63 行：同一路径改写，并加「审计脱敏」注记说明原文位置。
3. **未把任何含隐私的原图或备份加入仓库、提交或上传。**

### 2.3 截图本身的隐私核查（逐张目视 + 复核）

8 张原图**均未出现真实书名，也未出现任何文件系统路径**。图中可见的书名全部是
仓库测试夹具与隔离测试自制件（`test-b-*`、`t-txt-s07`、`t-md-*`、`测试 EPUB` 等）。
因此**未对图片做像素遮盖**——没有需要遮盖的内容；`redacted/` 下副本与原件**逐位相同**（哈希见 §3.3）。
另需说明：`b3_not_in_filter.png` 显示「共 14 本书籍 · 当前显示 3 本」，
**书量数字属于对私人书库规模的间接披露**（非书名、非路径），已在原报告同处标注，待总指挥裁定是否一并中性化。

### 2.4 同类泄露的其余扫查结果

| 位置 | 内容 | 性质 | 本次处置 |
|---|---|---|---|
| 仓库根 `dump3.xml` / `dump4.xml` / `dump5.xml` / `dump6.xml`（未跟踪，17:48–17:50，**S04 轮遗留**） | `text="<真实藏书目录名>"`（导入页「我的书籍目录」行） | 同一类私有目录名泄露 | **未改动**（非本轮产物、属他轮原始证据）；已记录，待裁定 |
| `docs/archive/mobile-legacy/*.md`（2 处，已提交历史） | `/sdcard/<真实父路径>/reads/起点` | 同一类私有路径，属历史归档 | **未改动**；建议后续单独出脱敏提交 |
| `docs/handoff/current.md`、`docs/research/2026-09-15-*.md` 中的「刺猬猫」 | 指公开小说平台名，非私有路径 | 不构成泄露 | 无需处置 |
| Obsidian Codex 记忆库 | 未出现该私有路径 | — | 无需处置 |

**本项判定：`PARTIAL`** —— 本轮两处已彻底脱敏且原文已保藏；但仓库内仍存在**同一类**的
他轮/历史泄露（`dump3-6.xml`、归档文档），本轮按「不改动他轮原始证据」原则未动，需总指挥决策。

---

## 3. B 场景的证据等级

### 3.1 原始证据：截图全部存在且哈希一致；B-1 的关键动作缺失

**8 张原图全部找到**（仓库外私有暂存区），SHA-256 **8/8 与清单逐位一致**：

| 截图 | 清单声明 SHA-256 | 实测 SHA-256 | 判定 |
|---|---|---|---|
| `b3_snackbar.png` | `5C40E2C7…6A8547` | `5c40e2c74543d288abe1818597190b8c19998616073976565aa6c58eb46a8547` | 一致 |
| `b3_not_in_filter.png` | `DCE3CE02…C7D06C` | `dce3ce0288d21f6f00f5d6c6a94f1741d86ae69c220e68986341d971b2c7d06c` | 一致 |
| `b3_all_no_stale_scroll.png` | `CAE8A784…7190C0` | `cae8a7845fadd3afc824b0dd3ced59694afa547f969ed4e24e637068607190c0` | 一致 |
| `b2_pair_snackbar.png` | `1687E1BF…46158C` | `1687e1bf6aa6be173dbc72e77bb64c01f68217433f66093d38967d4a2246158c` | 一致 |
| `b2_fallback_success.png` | `711497B2…2F9E37` | `711497b2ebc481f0aef92321823acc309f835e72bcf5bed9a7e26605a32f9e37` | 一致 |
| `d_ltr_craslider.png` | `E4B9436C…AD759A` | `e4b9436c427d9a12e646b891d8c0ef5f73fdd7da8f4c4fc27651ed2063ad759a` | 一致 |
| `d_rtl_craslider.png` | `315E6A1E…76FDA13A` | `315e6a1e89167fecc99122702d07147e0a67d938a07988c86f51818776fda13a` | 一致 |
| `d_rtl_reader_chrome.png` | `1C4ED56E…6A6E443C` | `1c4ed56e334cb4ad93f7afc1987435d68b1efd0d534433e7c6c8205e6a6e443c` | 一致 |

**B-1 的实际动作（原始记录 15:20:36–15:21:35）**：
`tap 400 1820`（选文件）→ `tap 986 2586`（加入书架）→ `keyevent 4`（返回）→ `uiautomator dump` 观察列表。
**全程没有 `tap 1073 2410`（「查看」按钮）**，也没有任何定位后的二次观察。

**B-2 的实际动作（15:31:35–15:31:49）**：有 `tap 1073 2410`（查看）→ 截图。此前 15:29:46 有一次**失败重做**（见 §1.2-2）。

**B-3 的实际动作（15:26:50–15:27:14）**：有 `tap 1073 2410`（查看）→ 截图 → `tap 197 433`（切回全部）→ 截图。

### 3.2 可独立核对的事实（逐图目视回读）

- **`b3_snackbar.png` 与清单描述不符（新发现）**：该图实际是**「导入书籍」页**
  （「本次导入完成 / 共解析 1 本，成功入库 1 本」、处理队列含 `test-b-none-hidden.txt`、
  `test-b-single-target.txt`）。清单 §2.1 却把它描述为「书架页切换至『已完成』Tab + Snackbar + 查看按钮」，
  它引用的 `text="已完成 3"`、`text="3 本 · 已完成 · 最近导入"`、`text="查看"` 等节点
  **不属于这一屏**（这些节点实际出现在 `b3_not_in_filter.png` 那一屏；经 bounds 换算与该图几何吻合）。
  推测成因为：`tap 986 2586 && sleep 1.5 && keyevent 4` 之后**立即截图**，返回键尚未生效。
- **`b2_pair_snackbar.png` 同样与清单描述不符**：该图实际是**「导入书籍」页**
  （「共解析 2 本，成功入库 2 本」、队列含 `test-b-pair-two.md` / `test-b-pair-one.txt` /
  `test-b-multi-beta.epub 已存在，未重复导入`）。清单 §2.4 却描述为「书架启用 Markdown 过滤 + Snackbar + 查看」，
  且其写的 `2 本 · Markdown` 与实际 `3 本 · Markdown` 也不符。
- **`b3_not_in_filter.png` 与清单相符且有实质证明力**：明确显示
  「新导入的书不在当前筛选结果里，可调整筛选后再查看」，Tab 为「已完成 3」，底部「共 14 本书籍 · 当前显示 3 本」。
  该提示文案**只有经「查看」路径才可能出现**，故 B-3 的「查看」确实被点到。
- **`b3_all_no_stale_scroll.png` 相符**：切回「全部 14」后列表在自然顶部，新书 `test-b-none-hidden` 带「新」角标居首。
- **`b2_fallback_success.png` 相符但证明力弱**：Markdown 筛选下 3 本，`test-b-pair-two`（第二本 MD）带「新」角标在首，
  且**未出现**「不在当前筛选结果里」提示。**但该图无法排除**「MD 本来就按『最近导入』排第一」这一替代解释。
- 清单 §2.2 / §2.5 引用的 `awaitFocusTarget` 收到候选列表、`onFocusHandled(false)` 触发、
  `focusNewBookIds = emptyList()` **均为内部状态**，截图与 dump 都看不到。

### 3.3 脱敏副本（供 Codex 目视回读）

已生成 `docs/qa/evidence/2026-09-23-bcd-portrait/redacted/`，含全部 8 张。
**因图中经核查不含真实书名与私有路径，副本未做像素遮盖，与原件逐位相同**，哈希与 §3.1 表内实测值一一对应：

```
5c40e2c74543d288abe1818597190b8c19998616073976565aa6c58eb46a8547  b3_snackbar.png
dce3ce0288d21f6f00f5d6c6a94f1741d86ae69c220e68986341d971b2c7d06c  b3_not_in_filter.png
cae8a7845fadd3afc824b0dd3ced59694afa547f969ed4e24e637068607190c0  b3_all_no_stale_scroll.png
1687e1bf6aa6be173dbc72e77bb64c01f68217433f66093d38967d4a2246158c  b2_pair_snackbar.png
711497b2ebc481f0aef92321823acc309f835e72bcf5bed9a7e26605a32f9e37  b2_fallback_success.png
e4b9436c427d9a12e646b891d8c0ef5f73fdd7da8f4c4fc27651ed2063ad759a  d_ltr_craslider.png
315e6a1e89167fecc99122702d07147e0a67d938a07988c86f51818776fda13a  d_rtl_craslider.png
1c4ed56e334cb4ad93f7afc1987435d68b1efd0d534433e7c6c8205e6a6e443c  d_rtl_reader_chrome.png
```

### 3.4 仍无法确认的部分

- **B-1 的「查看」定位是否可用：完全未验证。** 现有材料只能证明「新书入库后排首位且带『新』角标」。
- **B-2 的 fallback 是否被真实触发：不可由可见证据区分**（排序替代解释无法排除）；
  内部候选列表 `[txtId, mdId]`、跳过 txtId 等结论**属代码路径推断**。
- 清单中 §2.1/§2.2/§2.4/§2.5 引用的**节点 bounds 已无存活 dump 可核**
  （当次 dump 均覆写到 `current_dump.xml`，后者已被 23:37 的 dump 覆盖）。

### 3.5 修订后判定

| 场景 | 原判定 | **修订判定** | 依据 |
|---|---|---|---|
| B-1 | PASS | **PARTIAL** | 从未点击「查看」，定位路径未被实测 |
| B-2 | PASS | **PARTIAL** | 可见行为通过；原图与描述不符；fallback 为推断 |
| B-3 | PASS | **PASS（可见行为）**，内部状态为推断 | 提示文案 + 防位移残留有真实原图支持 |
| **项 2 综合** | PASS | **PARTIAL** | — |

---

## 4. D 的 RTL 矛盾

### 4.1 原始证据：两张 D 原图与一份原始 dump 全部存在

- `d_ltr_craslider.png`（23:36:12 抓取）、`d_rtl_craslider.png`（23:37:15 抓取）、
  `d_rtl_reader_chrome.png`（23:37:51 抓取）—— 哈希见 §3.1，均存在且与清单一致。
- **原始 `uiautomator` dump 存在**：`…\cra-device-backup-20260923\current_dump.xml`，
  抓取于 `2026-09-23T15:37:15Z`（即 `23:37:15`，`force_rtl=1` 生效期间），`rotation="0"`。
- RTL 开关确有下发记录：23:36:31 `settings put global debug.force_rtl 1` → 回读 `1`；
  23:38:10 恢复 `0` → 回读 `0`；两次之间各有 `am force-stop` + `am start`。

### 4.2 可独立核对的事实：**强制 RTL 下页面并未镜像**

**（a）程序化像素差分**（`d_rtl_craslider.png` vs `d_ltr_craslider.png`，同为 1220×2712）：

| 区域 | 差异像素占比 |
|---|---|
| 状态栏 `y[0,120]`（时钟 11:36→11:37、网速） | 5.64% |
| **内容区 `y[120,2400]`** | **0.0000%** |
| 底部 `y[2400,2712]` | **0.0000%** |

差异外接框为 `y[32,78] x[87,1138]`，**只在状态栏**。即：两屏的应用内容**逐像素完全相同**。

**（b）原始 dump 佐证**（`current_dump.xml`，`force_rtl=1` 期间）：

| 节点 | bounds | 位置 |
|---|---|---|
| `0 分钟 (关闭)` | `[83,1565][258,1626]` | **左** |
| `90 分钟` | `[585,1565][691,1626]` | 中 |
| `180 分钟` | `[1018,1565][1137,1626]` | **右** |
| 预设档位 `关闭 / 15 分 / 30 分 / 45 分 / 60 分 / 90 分` | x 由 125 → 1018 递增 | 左→右递增（LTR） |
| `今日进度：2 / 90 分钟` / `还差 88 分钟` | `[83,925]` / `[943,925]` | 左 / 右（LTR） |

**（c）报告与清单：哪一处写错**

- 报告 §2.3 第 2 步写「RTL 下 `0 分钟` 标签在**右**、`180 分钟` 在**左**」→ **写错**（与 a、b 均相反）。
- 清单 §2.7 写「右侧 `180 分钟`、左侧 `0 分钟`」→ **位置正确**（其引用的 bounds 与 `current_dump.xml` 逐字吻合）。
- **但清单同一节的动态描述「已走段从右侧起点向左填充、拇指紧贴已走段左边缘、未走段停点位于最左侧」同样错误**
  —— 原图中已走段在**左**、自左向右、拇指位于 90 分钟刻度处。
- 阅读器控制栏同理：清单 §2.8 写「右侧为上一章、左侧为下一章」，而原图 `d_rtl_reader_chrome.png`
  显示 `上一章` 在**左**（x≈85/1220）、`下一章` 在**右**，全书进度条自左向右；
  这与该轮 agent 自己在 LTR 时的 dump 观察（「`上一章` x=150，`下一章` x=1070」）完全一致——
  **清单把 LTR 时期的 bounds 当成 RTL 镜像结果写出，并把左右互换**。

### 4.3 脱敏副本及其哈希

同 §3.3（`d_ltr_craslider.png`、`d_rtl_craslider.png`、`d_rtl_reader_chrome.png` 三张已在
`docs/qa/evidence/2026-09-23-bcd-portrait/redacted/`），哈希见 §3.1 实测列。

### 4.4 仍无法确认的部分

- **`force_rtl=1` 为何未生效：不明。** 命令确已下发且回读为 `1`，但没有任何证据说明原因
  （MIUI 忽略该全局开关 / 应用未声明支持 RTL / 该页面对方向不敏感，三者皆有可能）。本轮不做结论。
- **RTL 几何是否真的正确：本轮未被证实。** 本轮**没有**取得任何「镜像布局」的正面证据。
- 报告 §2.3 所写「阅读器控制栏中的全书进度条同样自右向左镜像生长，滑动操作与轨道延伸无任何反向撕裂」
  中的**滑动操作**一项，本轮无对应原始动作记录（未做滑动，只做了 tap）。

### 4.5 修订后判定

**D = `PARTIAL`**：LTR 几何有真实原图支持；「强制 RTL 下严格镜像」的结论**撤回**（原图为反向证据），
「RTL 镜像几何正确」本轮**不成立**，需在**已确认 RTL 生效**的环境下重取证据。

---

## 5. C 的阻塞证据

### 5.1 原始证据：instrumentation 原始输出存在；MIUI 拦截日志**不存在**

- **代表用例确已真机发起**（原始记录 `15:10:27Z` = `23:10:27`）：
  `adb -s c49ac6cf shell am instrument -w -e class com.creationreadingassistant.feature.reader.pager.PageTurnerHighSpeedTest#singleTapAdvancesExactlyOnePage com.creationreadingassistant.test/androidx.test.runner.AndroidJUnitRunner`
- **原始输出存在**，全文（`task-1568.log`，76 字节）为：

  ```
  com.creationreadingassistant.feature.reader.pager.PageTurnerHighSpeedTest:
  ```

  仅类名，**无方法名、无 `OK (n tests)`、无 `INSTRUMENTATION_CODE`、无失败栈**。即该次运行**未产出任何测试结果**。
- **时间上限的原始记录**：`task-1570.log` 显示一次 **20 秒**计时器
  （`23:10:45` 建、`23:11:05` 触发，`Early Termination: …/task-1568`，其间 `Still waiting. Remaining time: 10s`）。
  **不存在 30 秒硬超时设置，也不存在任何强制终止（kill）记录。**
- **实际终止方式**：`23:12:37` 对两个包执行 `am force-stop`（主包 + 测试包），距发起约 **130 秒**。
- **前台状态有原始记录**：`23:12:12` 的 `dumpsys activity activities` 输出
  `topResumedActivity=ActivityRecord{… com.miui.home/.launcher.Launcher …}`。
- **MIUI 拦截日志：本轮不存在。** 本轮唯一一次日志检索（`23:11:46`）：

  ```
  adb -s c49ac6cf shell "logcat -d -t 100 | grep -iE 'creationreadingassistant|instrument|Abort|background activity'"
  ```

  原始输出（`task-1578.log`，153 字节）**只有 adbd 自己那条命令回显**：

  ```
  09-23 23:11:47.215  2151  2151 I adbd    : in ShellService: logcat -d -t 100 | grep -iE '…Abort|background activity'
  ```

  即 **`Abort background activity starts` 在本轮没有被采集到**。

### 5.2 该 MIUI 关键字的真实来源（**跨轮次**）

同一现象在**上一轮（2026-09-22）**有**独立且完整**的原始 logcat 证据：
`docs/qa/evidence/2026-09-22-s09b-s15-duration-gesture-device-acceptance/08-logcat-instrumentation-window.txt`，
其中含 `E/ActivityTaskManager( 2591): Abort background activity starts from 10949` 与 `result code=102`
多条记录（14:37–14:47）。**该证据真实有效，但它属于 09-22 轮，不属于本轮。**

因此原报告 §2.1「查看系统日志发现 MIUI 系统的安全权限策略限制（`Abort background activity starts`）」，
在本轮语境下是**把上一轮的结论当作本轮的现场观察**，属**归因越界**。

### 5.3 能支持到哪一级

| 结论层级 | 支持程度 | 依据 |
|---|---|---|
| 代表用例确已在真机发起 | **成立** | 原始命令记录 |
| 该次运行未产出任何测试结果 | **成立** | `task-1568.log` 全文 |
| 测试期间前台为 `com.miui.home`、ComponentActivity 未起前台 | **成立** | `dumpsys activity activities` 原始输出 |
| 挂起至少持续约 130 秒、以 `am force-stop` 收场 | **成立** | 命令时间线 |
| 「超时超过 30 秒」 | **不成立** | 实际为 20 秒计时器，无 30 秒上限，无强杀记录 |
| 「本轮查看系统日志发现 MIUI 拦截」 | **不成立** | 本轮 logcat 检索为空；关键字来自 09-22 轮 |
| 「C 的 5 项未运行」 | **成立** | C 批次新增 5 个 `@Test`，本轮 0 项产出结果（仅发起 1 条代表用例，亦无结果） |

### 5.4 仍无法确认的部分

- **阻塞根因仍未被本轮证据直接证明。** 可见行为（挂起、无结果、前台停在 MIUI 桌面）与
  「MIUI 拦截后台 Activity 启动」**相容**，但也与「测试自身在等待某个不出现的条件」相容。
  由于未能在阻塞发生的当下抓到 logcat，本轮**无法**把根因钉死。
- 另需注意证据强度的边界：该次检索用的是 `logcat -d -t 100`（仅末尾 100 行）且带 grep 过滤，
  即便拦截日志曾经存在，也可能已滚出窗口 —— **「没抓到」不等于「不存在」**。
- **未使用 MuMu 或任何模拟器**：成立（本轮全部设备命令均为 `adb -s c49ac6cf`）。
- **未把未运行的 5 项写成通过**：原报告未这样写（只记 BLOCKED），**此点原报告无误**。

### 5.5 修订后判定

**C = `BLOCKED`（可见行为成立）/ 归因未实证。**
可见行为层面「真机受阻、未产出结果」成立；「MIUI 拦截」这一**根因归因**本轮缺乏原始日志，
只能标注为「来自 09-22 轮次的跨轮类比，本轮未复现采集」。

---

## 6. 修订后裁决汇总

| 验收项 | 原裁决 | **修订裁决** | 原始证据 | 主要缺口 |
|---|---|---|---|---|
| 产物门禁（APK 哈希） | PASS | **PASS** | 两份 APK SHA-256 可独立重算，逐位吻合 | — |
| 项 1 (C 批次) | BLOCKED | **BLOCKED（可见行为）/ 归因未实证** | 命令、原始输出、前台 dump 均在 | 本轮无 MIUI 拦截日志；「30 秒」不实 |
| 项 2 (B 批次) | PASS | **PARTIAL** | 8/8 原图存在且哈希一致 | B-1 未点「查看」；B-2 图文不符、fallback 属推断 |
| 项 3 (D 批次) | PASS | **PARTIAL** | 3 张原图 + 原始 RTL dump 均在 | 强制 RTL **未镜像**；镜像结论撤回 |
| 设备图片清理范围 | （未列为验收项） | **PARTIAL** | 命令与退出码在 | **无清理前列表，影响范围不可界定** |
| 隐私 | （未列为验收项） | **PARTIAL** | 本轮泄露已定位并脱敏，原文已保藏 | 仓库内仍存同类的他轮/历史泄露（未动） |

**整体：`PARTIAL`。本批次不建议据原报告直接关闭。**
最小补证动作建议：① D 在**已确认 RTL 生效**的环境重取；② B-1/B-2 补做一次真实的「查看」点击定位；
③ C 的 5 项留待 CI 或不受 MIUI 管控的环境，届时保留完整 logcat（不加 `-t` 截断）。

---

## 7. 未解决事项（交总指挥）

1. **仓库内 `dump3.xml`–`dump6.xml` 含真实藏书目录名**（未跟踪，S04 轮遗留）——
   本轮按「不改动他轮原始证据」未动，请裁定是删除、脱敏还是保留。
2. **`docs/archive/mobile-legacy/` 两处历史文档含真实父路径**（已提交）——
   脱敏需另开提交，本轮未做。
3. **「用户原有 11 本书」这类书量数字**是否也要中性化，请裁定。
4. **`/sdcard` 根目录通配清理**已造成 2 张本轮中间截图（`b2_snackbar.png`、`b2_tap_view.png`）
   永久消失；建议后续验收把「清理前 `ls`」写入强制留证清单。
5. **`force_rtl` 在 MIUI 上为何未生效**值得单独立项排查（可能影响 D 的可验证性）。

---

## 8. 本轮所有原始文件均保留

- 仓库内：原报告、`commands.log`、`screenshot-manifest.md` **均未删除**（仅就地脱敏/加注）；
  证据目录新增 `redacted/`（8 张副本）。
- 仓库外：8 张原图**原样未动**；三份文本的审计前原文另存 `…\scratch\cra-bcd-originals-20260924\`。
- 未执行任何 `git` 写操作（`reset` / `checkout` / `clean` / `stash` / `add` / `commit` / `push`）。
