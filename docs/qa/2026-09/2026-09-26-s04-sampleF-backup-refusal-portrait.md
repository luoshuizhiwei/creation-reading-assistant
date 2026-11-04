# S04 补充验证：MuMu 手机竖屏 · 样本 F（epub_-lk2pf7）「.backup 残留拒绝」路径

> ## ⚠️ 结论性质：MuMu 模拟器验证，不是真机验收
>
> 本轮全部操作在 MuMu 模拟器实例 1（`127.0.0.1:16416`）上完成。
> 模拟器不等于真机：文件系统语义（删除失败、文件锁）、SAF provider 行为、系统版本差异均可能不同。
> 本报告的 PASS 不可当作真机验收结论。真实手机本轮全程离线、零命令。

- **原验证日期**：2026-09-26（样本 F 首次执行）
- **复核修正日期**：2026-09-27（本次独立复核后的手机竖屏复测与报告订正）
- **被测设备**：MuMu 模拟器 `127.0.0.1:16416`
- **应用包名**：`com.creationreadingassistant.qahost`
- **数据库**：`/data/data/com.creationreadingassistant.qahost/databases/creation_reading_assistant_native`
- **证据目录**：`E:\creation-reading-assistant\s04-mumu-2026-09-26`
- **关联文档**：`docs/qa/2026-09/2026-09-26-s04-epub-image-cache-mumu-verification.md`（同一仓库的 S04 单书 EPUB 图片缓存清理 MuMu 验证报告）
- **合规性**：未改源码、未 commit / push / reset / checkout；只植入并回收自己的标记文件。

---

## 〇、本次复核修正说明（先读这一节）

独立复核指出上一版报告的手机布局结论**不成立**，本次已全部重做并订正：

| 上一版说法 | 复核结论 | 本次处理 |
|---|---|---|
| `f23-clear.png` 拍到拒绝提示，作为竖屏证据 | 该图设备密度为 **280 dpi**，UI 走的是**平板布局（左侧导航栏）**，不是手机布局 | **作废**，不再作为手机竖屏证据 |
| `f24-refusal-portrait.png` 是拒绝提示竖屏正式截图 | 该图**没有提示条**，其 XML 中也**无提示文案** | **作废**，不再引用 |
| “横屏未能复现”作为遗留项 | 横屏与本条验收无关，且会误导为“横屏也需覆盖” | **删除**该陈述 |

本次改在 `wm density 440` 下重做，先确认手机布局后再执行，得到的新证据见 §4。

---

## 一、结论

**样本 F 的「.backup 残留拒绝」路径，在 MuMu 手机竖屏布局下已复测通过。**

在 F 的图片目录植入 `.backup` 标记后执行「清缓存」，应用**拒绝了整本删除**：

- EPUB 文件 `epub_-lk2pf7.epub` **仍在**（4729 字节）
- 图片目录 `epub_-lk2pf7/img/` **仍在**（2 张原图 + 植入的标记仍存）
- `books` 行**未变**：`content_status=available`、`local_content_path` 仍指向原路径
- `book_files.deleted_at` **仍为 NULL**
- 书架总数 `28` **未变**
- 手机竖屏界面弹出明确拒绝提示（与底部导航同屏可见，见 §4）

与样本 E（无残留、正常清缓存）形成对照：E 的文件与图片目录被删除、DB 置为 `missing` / `NULL`、`deleted_at` 被打上时间戳。两条路径行为互相印证，符合设计。

---

## 二、为什么这条路径值得单独验证

清缓存的实现（`BookRepository.clearBookCache`）会**同时删除内部存储里的正文文件**，删掉后只能重新导入才能恢复。因此当书的图片目录里存在 `.backup` 残留时，应用必须先判断：这个「备份」可能是该书**仅存的正文**。若判断失误，就会把用户唯一的数据删掉。

本次验证的就是这个**保护性拒绝分支**是否真的生效。

---

## 三、手机布局确认（前置条件）

临时设 `wm density 440` 后逐项确认，全部 rc=0：

| 项 | 回读结果 | rc |
|---|---|---|
| `wm size` | `Physical size: 1080x1920` | 0 |
| `wm density` | `Physical density: 280` / `Override density: 440` | 0 |
| `settings get system user_rotation` | `0` | 0 |
| `dumpsys input \| SurfaceOrientation` | `SurfaceOrientation: 0` | 0 |
| `dumpsys power \| mWakefulness` | `mWakefulness=Awake` | 0 |

布局形态：`g02-phone-layout.png` 显示**底部手机导航**（书架 / 书库 / 书城 / 统计 / 我的，bounds `[0,1744][1080,1920]`），**无左侧导航栏** —— 确认已进入手机布局，而非上一版的平板布局。

---

## 四、执行与证据

### 4.1 操作序列

1. 书架页 → 右上角菜单 →「批量选择」（`g07-menu2.xml` 中该项 bounds `[884,415][1036,471]`）
2. 进入选择态，显示「已选 0 本」（`g08-batch`）
3. 点选 F 卡片 →「已选 1 本」（`g09-selectF`）
4. 底部 dock 横向滚动露出「清缓存」（`g11-dock-swipe2.xml`：文字 bounds `[649,1618][748,1668]`，可点击容器 `[538,1577][776,1709]`，**点击容器中心 `(657,1643)`**）
5. 立即抓取 `g12-refusal-portrait.png` + `g12-refusal-portrait.xml`

> **坐标坑**（本次已避开）：dock 中「清缓存」的文字节点中心点击会落到相邻「删除」区。必须点**可点击父容器**中心。上一版按文字中心点击的问题即源于此。

### 4.2 拒绝提示（手机竖屏，与底部导航同屏）

`g12-refusal-portrait.xml` 中提示条节点：

- bounds `[77,1509][1003,1692]` —— 位于底部导航 `[0,1744][1080,1920]` **之上**，两者**同屏可见**
- 文本：

```
1 本检测到未完成替换的残留备份，已整本保留：该备份可能是该书仅存的正文，未删除也未自动恢复，书架信息未改动
```

截图 `g12-refusal-portrait.png` 已目视确认：底部为手机导航（书架 / 书库 / 书城 / 统计 / 我的），导航上方即该提示条。

### 4.3 文件侧（未删除）

落盘快照的**时间顺序**是：前置快照 → 植入标记 → 触发拒绝 → 清理后快照。
两张快照**不在同一状态下**：前置快照拍于植入之前，因此不含标记。

**① 前置快照 `portrait-preclear-F.txt`（植入标记之前）—— 图片目录只有 2 张原图，不含标记**

| 文件 | 前置快照结果 | ls rc |
|---|---|---|
| `epub_-lk2pf7.epub` | 存在，4729 字节 | 0 |
| `img/-1aphk9-2.png` | 74 字节 | 0 |
| `img/ddosns-2.png` | 74 字节 | 0 |
| `img/s04-qa.backup` | **不存在**（此时尚未植入） | — |

**② 植入标记**：`run-as … sh -c 'printf S04-QA-MARKER > img/s04-qa.backup'`，`plant_rc=0`；
复查 `ls img/` 得 3 项（2 张原图 + `s04-qa.backup` 13 字节）。

**③ 清理后快照 `portrait-postclear-F.txt`（触发拒绝之后）**

| 文件 | 清理后快照结果 | ls rc |
|---|---|---|
| `epub_-lk2pf7.epub` | **仍在**，4729 字节 | 0 |
| `img/-1aphk9-2.png` | **仍在**，74 字节 | 0 |
| `img/ddosns-2.png` | **仍在**，74 字节 | 0 |
| `img/s04-qa.backup`（植入） | **仍在**，13 字节 | 0 |

> **证据缺口（如实记录）**：本轮**没有**落盘一份「植入标记之后、点击清缓存之前」的独立 `ls` 原始快照；
> 植入后的存在性只有同一条 `ls` 复查输出（非落盘文件），以及拒绝发生**之后**的 `portrait-postclear-F.txt`。
> 因此「标记在点击时确实存在」是由 `g12` 的拒绝提示（提示文案明确按检测到残留备份判定）与清理后快照中标记仍存共同支持的，
> **不是**由一份植入后/点击前的落盘快照直接证明的。上面的前置表**未**被写成标记已存在。

### 4.4 DB 侧（未改动）

| 项 | 清缓存前 | 清缓存后 |
|---|---|---|
| `books.content_status` | `available` | `available`（未变 missing） |
| `books.local_content_path` | 原路径 | 原路径（未置 NULL） |
| `book_files.deleted_at` | `NULL` | `NULL`（未打时间戳） |
| 书架总数 | 28 | 28（未变） |

### 4.5 与样本 E 的对照

| 对比项 | 样本 E（无残留，正常清缓存） | 样本 F（有 `.backup` 残留，拒绝） |
|---|---|---|
| `<id>.epub` | 已删除 | **保留** |
| `<id>/img/` | 已删除 | **保留** |
| `content_status` | `missing` | `available` |
| `local_content_path` | `NULL` | 原路径 |
| `book_files.deleted_at` | `2026-09-26T15:17:20.908Z` | `NULL` |
| 提示 | 清理成功 | **残留备份，已整本保留** |
| 证据 | `portrait-preclear-E.txt`、`portrait-postclear-E.txt` | `portrait-preclear-F.txt`、`portrait-postclear-F.txt`、`g12-refusal-portrait.png/.xml` |

> 样本 E 为既有证据，本次**未重做**（E 的截图与前后文件/DB 摘要沿用原结果）。

---

## 五、清理（只清自己植入的东西）

| 动作 | 结果 | rc |
|---|---|---|
| 删除 `img/s04-qa.backup` | 成功，图片目录恢复为 2 张原图 | 0 |
| 删除后复查 `ls img/` | 仅 `-1aphk9-2.png`、`ddosns-2.png` | 0 |

**本次复测只移除新植入的标记** `img/s04-qa.backup`（rc=0），未删除设备上的任何其他文件。

关于历史临时文件：`/sdcard/Download/s04-portrait-E.epub` 与 `/sdcard/Download/s04-portrait-F.epub`
经只读 `ls` 两个精确路径核对，均为 `No such file or directory`（rc=1），**现已不存在**（前一版报告称已删除）。
本次复测未创建、未删除、未修改它们。

---

## 六、模拟器设置复位（已回读）

| 动作 / 项 | 回读结果 | rc |
|---|---|---|
| `wm density reset` | — | **0** |
| `wm density` | `Physical density: 280`（Override 已清除） | 0 |
| `wm size` | `Physical size: 1080x1920` | 0 |
| `settings get system user_rotation` | `0` | 0 |

旋转状态与进入本轮前一致（`user_rotation=0`、`accelerometer_rotation=1`、`SurfaceOrientation=0`）；屏幕 `Awake`。

---

## 七、安装态 APK 与既有书核对

| 项 | 结果 |
|---|---|
| 安装态 APK 路径 | `/data/app/~~CHga5GQ62Km_UxYypfvllw==/com.creationreadingassistant.qahost-KJFzHBeSmzTjgP6mzsUT_A==/base.apk` |
| APK SHA-256 | `15e2a9ea049995ee80288759f84c316f9664a2d41caaddca4d00d403ee54b51c`（与基线一致） |
| 书架总数 | 28（`available: 24`、`missing: 4`） |
| F 状态 | `epub_-lk2pf7` 测试样本F·竖屏 → `available`（拒绝路径，符合预期） |
| E 状态 | `epub_-m14bte` 测试样本E·竖屏 → `missing`（E 正常清缓存的结果，符合预期） |
| 一次性样本现状 | `epub_qvvw2j` 测试样本C·含图 → `missing`；`epub_rcxigq` 测试样本D·含图 → `available`；`epub_me8lux` 测试EPUB → `available`；`epub_mva894` 阶段A隔离测试EPUB → `available` |

**既有书改动范围（限定为可证明项）**：本轮可证明的是——书架总数仍为 28、`available: 24` / `missing: 4`
的聚合计数未变，上表列出的各条目 `id` / `title` / `content_status` 未变，且目标 F 的
`content_status`、`local_content_path`、`book_files.deleted_at` 三个字段未变。
**未**逐字段核对其他表（笔记、进度、标签、搜索索引等），也**未**做既有书文件的逐字节比对，
故不声称这些未核对的内容不变。

---

## 八、已覆盖 / 未覆盖

### 已覆盖

- [x] 手机布局前置确认：1080×1920、`SurfaceOrientation=0`、**底部手机导航（无左侧导航栏）**
- [x] F 的 EPUB + 图片目录前置状态落盘（带命令退出码）
- [x] F 的 `books` / `book_files` / 总数前置状态落盘（带命令退出码）
- [x] 在 F 图片目录植入 `.backup` 标记
- [x] 手机竖屏 UI 路径：批量选择 → 选中 → 清缓存
- [x] **拒绝提示 + 底部手机导航同屏**的截图与同一时刻 XML
- [x] 清理后文件 / DB / 总数落盘对账（全部未变，标记仍存）
- [x] 与样本 E（无残留）的对照
- [x] 只回收自己植入的标记
- [x] `wm density reset` 并回读物理 280 dpi、旋转 0
- [x] 安装态 APK SHA-256 校验
- [x] 既有书**可证明范围内**的核对：总数 / 状态聚合 / 各条目 `id`·`title`·`content_status` / F 的三个 DB 字段

### 未覆盖 / 遗留

- [ ] **证据缺口**：缺一份「植入标记之后、点击清缓存之前」的落盘 `ls` 原始快照（详见 §4.3 注）。
- [ ] **既有书未全字段核对**：笔记、进度、标签、搜索索引等其他表，以及既有书文件逐字节比对，本轮均未做。
- [ ] **真机未验证**：真实手机本轮保持离线、零命令，真机上的拒绝路径未验证。
- [ ] **模拟器不等于真机**：文件系统语义（删除失败与文件锁）、SAF provider 行为、系统版本差异均可能不同，本结论不可直接外推到真机。
- [ ] 未覆盖「残留备份 + 用户手动确认恢复」的后续交互分支。
- [ ] 未做多次连续清缓存的幂等性压力验证。

---

## 九、证据文件清单（`E:\creation-reading-assistant\s04-mumu-2026-09-26`）

**手机布局与操作链**

- `g02-phone-layout.png` / `.xml` —— **手机布局确认**（440 dpi，底部导航，无左侧导航栏）
- `g07-menu2.png` / `.xml` —— 菜单中的「批量选择」项
- `g08-batch.png` / `.xml` —— 进入选择态（已选 0 本）
- `g09-selectF.png` / `.xml` —— 已选 1 本（样本 F）
- `g11-dock-swipe2.png` / `.xml` —— dock 滚动后露出「清缓存」，给出点击容器 bounds
- **`g12-refusal-portrait.png` / `.xml`** —— **拒绝提示与底部手机导航同屏的正式证据**

**样本 F 前后对账**

- `portrait-preclear-F.txt` —— 清缓存前：设备状态 / EPUB / 图片目录 / DB（带 rc）
- `portrait-postclear-F.txt` —— 清缓存后：同上（带 rc）

**样本 E（对照，既有证据，本次未重做）**

- `portrait-preclear-E.txt` / `portrait-postclear-E.txt`

**已作废的证据（上一版误用，保留但不引用）**

- `f23-clear.png` / `.xml` —— 平板布局（左侧导航栏），非手机布局
- `f24-refusal-portrait.png` / `.xml` —— 无提示条，XML 中无提示文案

**工具**

- `shot.sh` —— 截图 + `uiautomator dump` + pull
- `make-samples.py` —— 一次性含图 EPUB 生成脚本
