# S04 qahost 沙箱包 MuMu 阅读验收（非破坏性）

- 日期：2026-09-25
- 设备：MuMu 实例 1，ADB serial `127.0.0.1:16416`（仅后台 ADB，全程未动主机鼠标/窗口/焦点）
- 包名：`com.creationreadingassistant.qahost`（**未重装、未卸载、未清数据**）
- 书库：qahost 沙箱内既有匿名副本，21/22 本；`sample-003.epub` 导入解析拒绝为**预期**，未重试
- 性质：**模拟器功能验收**。MuMu 当前横屏（SurfaceOrientation=1），本报告只主张模拟器功能结果，
  **不主张竖屏 UI、不主张真机通过**（AGENTS.md 的真机约束另行适用）
- 证据文件：全部保存在主机临时目录（不入仓库），设备端临时 dump 已清理（`/sdcard/qa_*` 剩 0 项）

---

## 1. 结论摘要

| # | 验收项 | 结果 |
|---|---|---|
| 1 | APK 身份与安装态 | **PASS** |
| 2 | TXT（sample-006）打开/翻页/目录/进度保持 | **PASS** |
| 3 | EPUB（sample-001）打开/翻页/目录跳转/进度保持 | **PASS** |
| 4 | MD（sample-011/012）打开/滚动/目录/进度保持 | **PASS**（样本极小，保持精度受限于样本，见 §6） |
| 5 | 明显错误反馈（sample-003 解析拒绝） | **PASS** |
| 6 | 稳定性（崩溃/ANR） | **PASS**（0 FATAL / 0 ANR） |
| 7 | MD 细粒度滚动位精度、竖屏布局、真机表现 | **未覆盖**（见 §8） |

全程零数据破坏操作：未点击任何清理/删除/重置入口，未长按书卡，未触碰设置页危险项。

---

## 2. APK 身份与安装态

| 项 | 值 |
|---|---|
| host 包 | `pm path` → `/data/app/~~kxuVDfxaGGAmUhOOWy03lw==/com.creationreadingassistant.qahost-DXiwOb5xV4wPduNf23JDrQ==/base.apk` |
| 安装包 SHA-256（设备侧 `sha256sum`） | `08c287642a2cb26cfff8fa8862e845015c0318e63bb429cc496578c2e0d75de7` |
| 当前构建产物 `android/app/build/outputs/apk/qahost/app-qahost.apk` SHA-256 | `08c287642a2cb26cfff8fa8862e845015c0318e63bb429cc496578c2e0d75de7` |
| aapt badging（本地产物，与安装包同哈希故同内容） | `package: name='com.creationreadingassistant.qahost' versionCode='3' versionName='0.5.1'`，minSdk 24 / targetSdk 35 |
| 设备 `dumpsys package` | `versionCode=3 minSdk=24 targetSdk=35 / versionName=0.5.1` |
| androidTest 包 `com.creationreadingassistant.qahost.test` | **未安装**（`pm path` 非零退出）；阅读验收不需要 instrumentation，如实记录 |

**版本闭合说明**：安装包与当前构建产物逐字节一致。该哈希与同日早间
`docs/coordination/legacy-stages/2026-09/2026-09-25-s04-qahost-apk-refresh.md` 记录的 `064F5334…` 不同——
那之后 S04 反馈实现与残留保护改动已合入并重新打包（当前产物 mtime 2026-09-25 20:48）。
本验收对应的是**当前**构建输入；不与早间报告的哈希做闭合主张。

启动方式：`am start -n com.creationreadingassistant.qahost/com.creationreadingassistant.MainActivity`
（`cmd package resolve-activity --brief` 解析），启动后 `mCurrentFocus` 为该 Activity。

---

## 3. 样本清单（只读查询沙箱库取得，仅中性编号）

`run-as … sqlite3 databases/creation_reading_assistant_native`（只读 SELECT）：
在库 21 本 = EPUB 4（sample-001/002/004/005）+ TXT 14（006-010、013-020、022）+ MD 3（011/012/021）。
`sample-003.epub` 不在库（导入拒绝，预期）。

本轮覆盖：**TXT = sample-006；EPUB = sample-001；MD = sample-011 + sample-012**。

界面导航证据：应用启动落首页（底部/侧栏 NavigationRail，横屏宽屏形态），
书架页筛选条显示「全部 21 / 在读 1 / 已读完 1 / 未开始 19」，卡片标题均为 `sample-0NN` 中性编号
（例外：sample-001 在 UI 显示为「测试EPUB」——项目标准中性测试名，按文件名搜索可命中）。

---

## 4. TXT 链路（sample-006，454 章）

| 操作 | 可核对证据 | 判定 |
|---|---|---|
| 书架点卡片打开 | uiautomator dump 出现 `sample-006`、`0.0%`、章节标签「开篇」 | PASS |
| 翻页（左→右 swipe ×2） | 控制栏标签由 `TXT · 开篇 · 0%` 变为 `TXT · 第1章 … · 0%`（章名内容不记录）；再翻进入卷页 | PASS |
| 目录 | 底栏「目录」→ 弹层 `目录与标记 · 2 / 454`，含 目录/书签/笔记 三页签、章节搜索框；列表可滚动（第 1→3 章行渲染） | PASS |
| 目录跳转 | 点第 6 章行 → 弹层关闭，控制栏 `TXT · 第6章 … · 0%`，全书 0.0% → **0.6%** | PASS |
| 退出重进 | BACK×2 回书架 → 重开 sample-006 → 直接显示 `TXT · 第6章 … · 0%`、**0.6%**，与退出前一致 | PASS |
| 库内核对（只读） | `reading_progress` 行：`progress_percent=0.6155`，`locator_v2 offset=16518`，`ci=7`（第 6 章对应索引），更新时间为本轮操作时刻 | PASS |

备注：控制栏约 2 秒自动隐藏；两次 dump 间点击底栏会落进正文（左区=上一页），
属交互时序问题而非缺陷——测试脚本改为「呼出后立即点击」后稳定复现。

---

## 5. EPUB 链路（sample-001，UI 显示名「测试EPUB」，6 章）

| 操作 | 可核对证据 | 判定 |
|---|---|---|
| 书架搜索定位 | 搜索 `sample-001` → 「找到 1 本 · 匹配文件名」→ 卡片显示「测试EPUB · 在读 9%」 | PASS |
| 打开 | 阅读器恢复至上次保存位：`第一章 … · 2.6%` | PASS |
| 翻页（swipe ×2） | 章节推进：`第一章 → 第二章`，全书 2.6% → **18.6%**；控制栏 `EPUB · 第二章 … · 25%`（章内 25%） | PASS |
| 目录 | `目录与标记 · 2 / 6 · 已读 2/6`，目录/书签/笔记页签 + 「替换净化」入口（分页 EPUB 能力入口可达）；章节行渲染（第 1–4 章可见） | PASS |
| 目录跳转 | 点第 4 章行 → `第四章 … · 50.5%` | PASS |
| 退出重进 | BACK×2 → 搜索重开 → 精确恢复 `第四章 … · 50.5%` | PASS |
| 库内核对（只读） | `progress_percent=50.5042`，`locator_v2 ci=3`（0 基 = 第四章）、`offset=4009` | PASS |

---

## 6. MD 链路（sample-011、sample-012）

| 操作 | 可核对证据 | 判定 |
|---|---|---|
| 打开（011，241B） | 渲染标题行 + 缩进段落；控制栏 `MD · … · 100%`（格式标签 MD 正确） | PASS |
| 打开（012，1060B） | 渲染标题/段落/小节标题/列表项/引用段落/代码块等 Markdown 结构 | PASS |
| 滚动（012） | 快速轻扫滚动生效：内容位移至「第二节」，章内 0% → **50%**。**注意**：慢速拖拽（≥300ms）会被判定为文本选择（出现选区把手与 复制/浏览器/字典/更多 工具栏），滚动须用快速轻扫——行为记录，非缺陷 | PASS |
| 目录 | `目录与标记 · 1 / 1`，单章文档 + 「最近浏览」区渲染正常；**跳转项 N/A**（仅 1 章） | PASS（跳转未覆盖） |
| 退出重进（012） | BACK×2 → 搜索重开 → 恢复至文档中部「第二节」：控制栏 `MD · … · 50%`、正文 50.4%；且两次独立重进的沉浸态截图**逐字节一致**（同位置渲染确定性） | PASS |
| 库内核对（只读） | `progress_percent=50.3896`，`{"offset":194,"space":"canonical","locator_v2":{…,"co":194}}` | PASS |

**样本局限**：三个 MD 样本 143–1060 B，sample-011 全文不足一屏（无滚动位可测）；
sample-012 的可滚动范围也很小，进度百分比粒度（0.0%→50.4%）由样本大小决定。
MD「细粒度长文档滚动保持」标注为**未覆盖**（§8）。

---

## 7. 明显错误反馈（sample-003）

入口：书架「⋯」菜单 → 「导入记录」（共 22 条 = 21 成功 + 1 失败）。

失败行（uiautomator dump 原文摘录，仅中性字段）：

```
sample-003.epub
解析失败
0.1 KB
声明为 EPUB 但正文不是有效的 ZIP 文件
```

- 失败被**明确标注**且给出**可理解的原因**（声明类型与实际内容不符），未混入成功列表；
- 符合「解析拒绝属预期」的预期文案语义；未出现引导删书/清备份的措辞；
- 依任务要求**未重试**该样本。

PASS。

---

## 8. 未覆盖项

| # | 项 | 原因 |
|---|---|---|
| 1 | 竖屏 UI 与布局 | MuMu 当前横屏（SurfaceOrientation=1），任务限定只报模拟器功能结果 |
| 2 | 真机表现 | 禁止操作真实手机 |
| 3 | MD 长文档细粒度滚动保持 | 仓内 MD 样本最大 1060 B，可滚动范围约一屏，百分比粒度不足以区分更细位置 |
| 4 | MD 目录跳转 | 所测 MD 仅 1 个目录项，无跳转目标 |
| 5 | 听书 / 灵感 / 外观 / 设置 | 不在本轮阅读验收范围 |
| 6 | sample-004/005（7.1 MB 大 EPUB）与 454 章 TXT 的全目录遍历 | 单本代表性覆盖已满足本轮；大批量遍历留待后续 |
| 7 | androidTest instrumentation | test 包未安装；本轮为纯 UI 功能验收 |

---

## 9. 稳定性

- `logcat -d` 全量：`FATAL EXCEPTION` 计数 **0**；`ANR in com.creationreadingassistant` 计数 **0**。
- 唯一 `AndroidRuntime:E` 记录来自模拟器系统进程 `com.huawei.hwid`（HMS 框架自身
  ClassNotFoundException），与本应用无关，且时间早于本轮操作。
- 验收期间应用前后台切换、阅读器多次进出，均无崩溃。

---

## 10. 合规自证

| 动作 | 是否执行 |
|---|---|
| 全部 ADB 命令显式 `-s 127.0.0.1:16416` | ✅ |
| 仅后台 ADB（uiautomator dump / input tap/swipe/keyevent / screencap / pm / dumpsys / run-as 只读 SELECT） | ✅ |
| 操作主机鼠标 / 切窗口 / 抢焦点 / Windows UI | ❌ 未发生 |
| 重装 / 卸载 / 清数据 / 点清理删除重置入口 / 长按书卡 | ❌ |
| 重试 sample-003 | ❌ |
| 修改源码 / Gradle / 仓库文件（本报告除外） | ❌ |
| 截图或私有数据写入仓库 | ❌（证据仅存主机 TEMP） |
| 触碰真实手机 / 原始仓库 / E 盘备份 | ❌ |
| commit / push / stage / reset / checkout / clean | ❌ |

设备端自查残留：`/sdcard/qa_*` 临时 dump 已删除（剩 0 项）。
