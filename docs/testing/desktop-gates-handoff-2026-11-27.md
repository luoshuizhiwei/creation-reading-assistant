# desktop 门禁战役交接（BD-257 … BD-260，2026-11-27）

> 给下一个接手 desktop 普查/门禁主线的 agent。命令清单以 `AGENTS.md` 为准（本文不重复，避免第二份真源）；本文只记**状态、机制、坑、开放项、证据在哪**。
> 证据目录在仓库外：`E:\AgentWork\CRA\desktop-bd<NNN>-<slug>-v1\`（每批一个，含 `REPORT.md`、`commit-msg.txt`、`push-record.txt`、牙与门禁的原始日志）。下文只写目录名。

## 1. 现在在哪

| 项 | 值 |
|---|---|
| 仓库 | `D:\develop\Code\Codex\creation-reading-assistant` |
| 分支 | `codex/desktop-settings-20260927` |
| HEAD / origin | `3830918`（BD-260），三者一致，工作区已跟踪改动 0 |
| 机器 | Windows 10.0.26300，PowerShell 7（**本机 Bash 工具不可用**），Node v22.23.2，vitest 2.1.9，jsdom 29.1.1，@testing-library/react 16.3.2，Electron 33.4.11，i7-12800HX / 24 逻辑处理器，显示缩放 150%（AppliedDPI 144） |
| 桌面主线的活动记录 | `scratch/desktop-survey\`：`gates.md`（39 本普查账的口径 + §6 批次纪律）、`features.md`、`renderer-shell.md`、`electron-main.md`（只读调查） |

工作区里有一批未跟踪的用户资产（`dump*.xml`、`scratch/`、`.opencode/`、`.zcode/`、`docs/qa/evidence/` 等）。**不要清理、不要移动**——按项目纪律，工作区里的既有修改一律视为用户资产。

## 2. 这批战役在做什么

不是加功能，是**给 desktop 的既有 39 本普查账补上「可证伪」这一层**：每批一个可复现的缺陷或量具缺陷 → 最小修法 → 牙（故意改坏、看它咬不咬）→ 全套门禁 → 报告 → 提交推送。批次纪律见 `scratch/desktop-survey/gates.md` §6；每批的产物清单固定为：

`E:\AgentWork\CRA\desktop-bd<NNN>-<slug>-v1\` 下的 `REPORT.md` + `commit-msg.txt` + `push-record.txt` + 牙/门禁/统计的原始日志（逐字落盘，不摘要）。

## 3. 四个批次一览

| 批次 | 做了什么 | 提交 | 报告 |
|---|---|---|---|
| BD-257 | 建立 `audit:replace-plan-flow` 常驻账 + 源码账；首次跑全量 39 本普查电池，得到本机基线（34 绿 + `non-text-contrast` 环境红 + 四本账路径红） | 已推送 | `desktop-bd257-replace-flow-v1\REPORT.md` |
| BD-258 | `audit-non-text-contrast` 的判边门槛按设备像素比折算（150% 下 1px 读回 0.667px 被判成「无边框」） | `b11a956` | `desktop-bd258-nontext-dpr-v1\REPORT.md` |
| BD-259 | 四本普查账（identification / page-title / headings / heading-order）写死的 Linux 沙箱工程根改成按脚本自身位置解析；提交后在 `c012546` 上把 39 本电池整轮重跑到**全绿** | `c012546` | `desktop-bd259-portable-root-v1\REPORT.md` |
| BD-260 | 偶发用例 `project-card-link-dialog-link-focus` 的「关联失败不抢焦点」稳住：只改用例（+38/−3），产品一行未改 | `3830918` | `desktop-bd260-link-focus-flake-v1\REPORT.md` |

## 4. 门禁基线（可直接对照，2026-11-27 实测）

九条全部 exit 0（命令见 `AGENTS.md`；BD-260 那轮 10:39–10:46，共约 6 分 47 秒）：

- `build` 52 秒；`test` 186 秒（**266 文件通过 / 1 跳过；2067 用例通过 / 4 跳过**）；`verify:design-tokens` 12 秒
- 四本账：identification 39 秒、page-title 37 秒、headings 37 秒、heading-order 40 秒。它们的「扫描完成行」和「出口行」自 BD-259 起逐字稳定：
  - `IDENT_OK：…`｜`一致识别扫描完成：判定 17 屏，功能族 19 个，图案 25 张。`
  - `TITLE_OK：…`｜`页面标题扫描完成：采样 20 屏，标题 15 种。`
  - `HEADING_OK：…`｜`标题骨架扫描完成：采样 18 屏，页级标题 16 种。`
  - `HEADING_ORDER_OK：…`｜`标题序列扫描完成：采样 19 屏，其中 15 屏有 h2 及更深的层级，卡片头（.stats-card > h2）累计 10 个。`
- `audit:test-anchors` 294 个锚点（27 个文件）；`audit:script-selectors` 149 个类名
- 全量 39 本电池的最后一次完整基线跑在 **`c012546`**（BD-259 提交后，09:32:43–10:06:35，约 33.8 分钟，**39/39 exit 0**）。跑器与结果在 `desktop-bd259-portable-root-v1\`：`battery.mjs`、`battery\battery-summary.md`、`battery.DONE.txt`。最慢三本：character-keys 337 秒、focus-rings 170 秒、contrast 130 秒。
- **注意**：BD-260 只改一份用例文件，因此只重跑了「会启动 app」的那四本账（门禁里各一遍），其余 35 本没有在 `3830918` 上重跑。要一份 `3830918` 的完整电池基线，得再跑一轮（约 34 分钟）。
- `audit:contrast` 需要 Xvfb：Linux 沙箱里才跑得动，**Windows 本机跑不了**；其余 `audit:*` 在 Windows 上直接 `node scripts/audit-*.mjs` 即可（脚本按 `process.platform` 选 electron.exe）。

## 5. 接手必读：机制与坑

### 5.1 量具自己的错，比产品的错更常见

三例，都留了原始日志，别重蹈：

1. **BD-259 运行时牙第一轮 FAIL**：T3 的判据照 T1 的「死法」写（要求日志出现 `electron.launch: Failed to launch` 和 launching 行），而 root 差一级时 playwright 实际抛 `Error: Process failed to launch!`、不打 launching 行。账没错，判据错。
2. **BD-260 牙第一轮 FAIL**：T3 要求我新加的「跨帧失败」用例变红，实测它 96 遍一次没红，红的全是「对照」那条。错在判据，不在产品也不在用例；改成「『对照』至少红一遍且点名『关闭』」后整轮成立。
3. **BD-260 跑器头注写着「空载 5 遍全绿」，盘上只有 1 遍的日志**（其余遍数我的检查脚本自己中断了）。**能确认的只有 1 遍。** 报告里已更正，跑器不再改。教训：日志没落盘就等于没跑过，不要写成实测。

### 5.2 PowerShell 的坑（本机只能用 PowerShell）

- **双引号字符串里嵌复杂 `$(...)`（含 `[math]::Round`、`-split`、正则括号）会整体解析失败，整条命令不执行**。先算进变量，再用 `-f` 格式化。我在这上面浪费过两轮。
- 路径引号必须成对：`"…'` 混用会报 `The string is missing the terminator`，同样整条不执行。
- 写 UTF-8 文本统一 `[IO.File]::WriteAllText($p, $text, [Text.UTF8Encoding]::new($false))`（无 BOM、LF）。
- `Start-Job` 起 vitest **抓不到输出**；统计跑用 `repeat.mjs`（每遍单独一个进程、输出直接写文件）。
- 跑门禁用 `cmd /c "<命令> > <日志> 2>&1"` 时要处理引号剥离，把日志路径放在最后一段（`run-gate.ps1` 已是可用骨架，照抄即可）。

### 5.3 jsdom 的 rAF：开帧会迟到几百毫秒（BD-260 的根因）

`Dialog.tsx:109–120`（BD-127）打开时请求**一帧** rAF，回调里把焦点移到首个可聚焦元素（「关闭」按钮）。实测（`desktop-bd260-link-focus-flake-v1\diag-4-idle.log`、`repeat-diagload\`）：

- 这一帧不是渲染后 16 毫秒到：自用例开跑 **334 / 415 毫秒（空载两次）、393–494 毫秒（6 路争用）**才执行；
- 它和用例的「列表就绪」只差 **1.3–5.6 毫秒**（18 条争用轨迹里 17 条），先后随负载翻转；
- 翻过来的那条（`run-03-6.log`）：列表就绪 357ms → 手动聚焦 373ms → 开帧 421ms → `waitFor` 返回时焦点在「关闭」上。

**推论（已写进报告）**：任何「在 `Dialog` 里手动 `focus()` 然后立刻断言 `document.activeElement`」的用例，都可能在负载下被开帧抢走焦点。写法固定为：列表/内容就绪后 `await flushFrames()`（连等两帧）再布防，参考 `src/features/creation/cards/__tests__/card-type-editor-delete-focus.test.tsx:51`。

### 5.4 普查账的工程根必须可移植

`scripts/audit-*.mjs` 的 `const root = …` 一律按脚本自身位置解析（`path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..")`），**不要写死 `/data/creation-reading-assistant`**。写死的症状很误导：报的是 `electron.launch: Failed to launch: Error: spawn C:\WINDOWS\system32\cmd.exe ENOENT`，看着像可执行文件缺失，其实是账把 cwd 设成了不存在的目录（BD-259）。

### 5.5 提交与推送

- 批次号是自定的：**提交前先 `git fetch`**，并 `git grep BD-<NNN>` 确认没人占用。
- **路径限定提交**（`git commit -F commit-msg.txt -- <文件>`），只带本批文件；提交前确认暂存区为空、提交后确认已跟踪改动为空。
- 推送偶发 TLS 握手失败：**退避重试即可，不要改 git 配置**（第 1 次失败→等 10 秒→等 30 秒）。`push-record.txt` 要记远端 `ls-remote`、本地 HEAD、跟踪引用三者是否一致。
- 提交信息分节：为什么改 / 修法 / 改前红改后绿 / 牙 / 量具踩坑 / 门禁 / 线索。**只写有证据的话**——BD-258 的提交信息写了「产品现有的焦点环都是 2px / 3px」，实际 `src/features/library/epub-reader/EpubSelectionToolbar.tsx:50` 有一处 1px 的环（150% 下读回 0.667px）。提交已推送，不改写历史，勘误记在 BD-258 报告 §0/§9。

## 6. 开放项（带优先级）

1. **（首选，候选 BD-261）同形抢跑隐患排查。** 口径：`src/**/__tests__` 下同时出现 `activeElement` 与 `.focus(` 的用例文件共 37 份，其中全文不出现 `requestAnimationFrame` / `flushFrames` / `runFrames` / `nextFrame` 的 **21 份**；这 21 份里文本出现 Dialog 字样的只有 4 份：`features/creation/command/__tests__/command-palette.test.tsx`、`features/creation/editor/__tests__/card-reference-picker-keyboard.test.tsx`、`features/library/__tests__/reader-settings-drawer-focus.test.tsx`、`features/search/__tests__/search-panel.test.tsx`。整套测试 9 轮里没见它们红过；先逐份读「手动聚焦是否落在开帧之前」，再决定要不要预先加等帧。**别只看文件名就批量加 `flushFrames`**——有窗口才有抢跑，没有窗口的加了只是噪声。
2. **产品层要不要加守卫**：`Dialog.tsx:109–120` 与 `src/components/ui/use-modal-keyboard.ts:37–48`（BD-130）同形，都是开帧 rAF **无条件**聚焦、没有「焦点已在弹层内则不动」的判断。先量真实使用中能否触发（推断不能：关联弹层的行要等 IPC 才出，人手点不到开帧之前），再决定动不动产品。
3. **开帧为什么迟到 280–460 毫秒**没查根因（渲染返回 40–50ms，第一帧 334–494ms）。整套测试日志里 `environment 2415.71s` 这个数也值得一看。
4. `audit-focus-rings.mjs:224` 的 1px 环同形前提（150% 读回 0.667px 会被判成「无本体环」）仍在，没动。下一批先量清楚那一处，再决定照 BD-258 的办法折算门槛、还是把环加粗到 2px。
5. 整套 `npm test` 修后只跑了 1 轮；按修前 8 轮 2 红的频率，要几十轮才看得出差别，没做。
6. **用户口径未定**：BD-257 那次 `npm test` 基线首轮 exit 1 怎么处理，等用户表态；新 agent 不要自行发明规则。

## 7. 接手步骤（照做即可）

1. `git fetch origin` → `git status -sb` → 确认 HEAD 与 origin 一致、已跟踪改动为 0。
2. 读 `scratch/desktop-survey/gates.md` §6（批次纪律）与最近一份 `REPORT.md`（结构照抄）。
3. 起证据目录 `E:\AgentWork\CRA\desktop-bd<NNN>-<slug>-v1\`；把上一批的 `run-gate.ps1`、`repeat.mjs`、`teeth.mjs` 复制过去改标签（这三个骨架都在 BD-259/BD-260 目录里，可直接用）。
4. **先量现状**：不改任何文件，原样跑目标用例/账若干遍，把红/绿与耗时落 log。红基线没量到，后面全是猜。
5. 改最小面；能只改用例就不动产品，能只动一行就不动十行。
6. 牙：每档「变异（锚点必须恰好命中 1 处）→ 跑 → `finally` 逐字节还原 → 核哈希 → 复跑与 R0 同形」。统计牙（靠负载翻车的那种）要如实标注它是统计的，并给出红率。
7. 改后统计：同一负载、同样遍数，与改前对照。
8. 九条门禁（命令见 `AGENTS.md`）；动了 `scripts/`、判据或样式，再补对应电池/`audit:contrast`（后者要 Xvfb）。
9. 写 `REPORT.md`，章节照 §8。
10. 提交（路径限定）+ 推送（退避重试）+ 写 `push-record.txt`。

## 8. REPORT 的章节模子（BD-259/BD-260 都是这个骨架）

`0 结论先行` → `1 做了什么`（文件表：改了什么/规模/哈希）→ `2 基线核对` → `3 机制：量到的与没量到的`（分两张清单，推断必须标明是推断）→ `4 修法与取舍`（写「为什么不 X」，每条给出理由）→ `5 改前红 / 改后绿`（表格 + 概率意义）→ `6 牙`（每档变异、结果、判据；FAIL 轮单独一节）→ `7 门禁`（时间/exit/耗时/关键行）→ `8 提交与推送` → `9 未验证项` → `10 下一批线索` → `11 证据索引`。

## 9. 硬约束（违反即返工）

- 不 `reset` / `checkout` / `stash` / `clean`；不删既有文件；不动 `android/`、`docs/handoff/`、`package-lock.json`、`scripts/beta-check.mjs`。
- 不打包、不发布；跑真实 app 只用一次性 profile；不复制工程、不清缓存。
- 没跑过的不写「已通过」；日志没落盘就等于没跑。
- 程序读取的 UTF-8 文本用 `[IO.File]::WriteAllText` + `UTF8Encoding($false)`；LF、无 BOM。
- 测试记录只写「测试 EPUB」「测试 TXT」等中性名称，不写真实书名。

## 10. 给下一个 agent 的提示词（可直接复制）

> 你是接手 desktop 普查/门禁主线的 agent。工作仓库 `D:\develop\Code\Codex\creation-reading-assistant`，分支 `codex/desktop-settings-20260927`，当前 HEAD `3830918`（BD-260 已推送，工作区已跟踪改动应为 0）。
>
> **先读三份东西，再动手**：① `docs\testing\desktop-gates-handoff-2026-11-27.md`（交接文档，状态/机制/坑/开放项全在里面）；② `scratch\desktop-survey\gates.md` §6（批次纪律）；③ 上一批 `E:\AgentWork\CRA\desktop-bd260-link-focus-flake-v1\REPORT.md`（报告结构照它写）。
>
> **本机环境**：Windows，只能用 PowerShell（Bash 工具不可用）；Node v22.23.2；显示缩放 150%。完全访问模式，所有命令直接执行，不要请求确认。
>
> **纪律**（违反即返工）：不 `reset`/`checkout`/`stash`/`clean`；不删既有文件，工作区里未跟踪的 `dump*.xml`、`scratch/`、`.opencode/` 等是用户资产，不要清理；不动 `android/`、`docs/handoff/`、`package-lock.json`、`scripts/beta-check.mjs`；不打包不发布；跑真实 app 只用一次性 profile；没跑过的不写「已通过」，日志没落盘就等于没跑。
>
> **每批的产物**：证据目录 `E:\AgentWork\CRA\desktop-bd<NNN>-<slug>-v1\`（`REPORT.md` + `commit-msg.txt` + `push-record.txt` + 牙/门禁原始日志），然后路径限定提交 + 推送（TLS 偶发失败退避重试，别改 git 配置）。批次号自己顺延：提交前先 `git fetch`，再 `git grep BD-<NNN>` 确认没人占用。
>
> **工作方式**：先量现状（原样跑、落 log）→ 改最小面 → 牙（变异必恰好命中 1 处，`finally` 逐字节还原 + 核哈希 + 复跑与 R0 同形）→ 改后同负载统计 → 九条门禁（命令见 `AGENTS.md`）→ 写 REPORT → 提交推送。量具自己的判据错了要如实记档，不要为了让牙变绿去改判据。
>
> **第一批做什么**：按交接文档 §6 第 1 条——把 4 份「不等帧且提到 Dialog」的用例逐份读一遍，判断手动聚焦是否落在开帧之前；有窗口的先量出红率再改，没有窗口的不要动。动手前先跟我确认这一批的边界。
