# Android S03-1 真实书库全量搜索索引重建离线对账与证据收口报告

- **日期**：2026-09-23
- **验证范围**：Android S03-1 搜索索引全量重建（SearchIndexRepository）离线数据与证据最终收口
- **工作仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支**：`codex/recover-worktree-20260920`
- **设备 Serial**：`c49ac6cf`（真实物理手机，本轮全程**零连接、零操作、零 adb、零重建**）
- **参考上游输入**：
  - [真机验收原始报告](2026-09-23-s03-1-real-library-device-acceptance.md)
  - [Codex 回读复核报告](2026-09-23-s03-1-real-library-codex-review.md)
- **证据目录**：[`docs/qa/evidence/2026-09-23-s03-1-real-library/`](../evidence/2026-09-23-s03-1-real-library)

---

## 一、执行边界与铁律说明

本轮为纯离线证据收口与数据深度对账，严格遵循以下铁律边界：
1. **零设备介入**：全程不连接、不调用手机，未执行任何 `adb` 命令，未触发二次索引重建。
2. **零源码修改**：未修改产品源码与构建配置，未动用 `git reset`、`clean` 或格式化。
3. **离线对账**：数据库解包与校验在仓库外的私有临时目录进行；`full_reconcile.py` 对解包副本使用普通 `sqlite3.connect` 并仅执行查询及完整性检查，并非 `file:...mode=ro`。原始备份 tar 未被脚本写入。
4. **隐私完全脱敏**：本报告及公开证据目录中**严禁输出任何真实书名、正文原句、设备绝对私有路径或个人标识**；所有截图引用一律收口至 `redacted/` 目录下的去隐私化副本，原始截图与备份 tar 归为用户本地私有资产，不纳入暂存或提交。

---

## 二、数据库离线完整性与 Schema 对账

基于私有临时目录中提取的重建前（Pre）与重建后（Post）真实数据库副本，进行 SQLite 引擎级底层体检与 Room 版本核对：

| 校验维度 | 重建前基线（Pre） | 重建后产物（Post） | 差异与对账结论 |
|:---|:---|:---|:---|
| **SQLite quick_check** | `ok` | `ok` | **PASS**（B-tree 结构完好无损坏） |
| **SQLite integrity_check** | `ok` | `ok` | **PASS**（全库物理与逻辑完整性一致） |
| **foreign_key_check** | `0` 条违规（空集合） | `0` 条违规（空集合） | **PASS**（无孤立外键或级联遗漏） |
| **user_version (Room Schema)** | `15` | `15` | **PASS**（数据库架构版本严格一致） |
| **主库物理文件大小** | 1,128,742,912 字节 | 1,098,870,784 字节 | 释放约 29.87 MB 空间（B-tree 重平衡与碎片整理） |

---

## 三、非索引业务表逐行/全字段深度对账

为回应 Codex 复核中关于“仅比较行数不足以证明非索引数据无损”的审慎关注，本轮编写专用离线比对工具（`full_reconcile.py`），对所有核心非索引业务表执行了**逐行全字段序列化 SHA-256 摘要比对**：

| 表名 (Table Name) | Pre 行数 | Post 行数 | Pre 全表逐行哈希 / Post 全表逐行哈希 | 一致性裁决 |
|:---|:---:|:---:|:---|:---:|
| `books` | 11 | 11 | `53d57f88c11136c5...` / `53d57f88c11136c5...` | **100% IDENTICAL** |
| `reading_progress` | 7 | 7 | `528089da56ddc515...` / `528089da56ddc515...` | **100% IDENTICAL** |
| `reading_sessions` | 90 | 90 | `62ea808a7ea9bd4d...` / `62ea808a7ea9bd4d...` | **100% IDENTICAL** |
| `chapter_reads` | 8 | 8 | `71470db0cd09f75a...` / `71470db0cd09f75a...` | **100% IDENTICAL** |
| `book_content` | 10 | 10 | `90ce2a3e38cfee32...` / `90ce2a3e38cfee32...` | **100% IDENTICAL** |
| `book_files` | 11 | 11 | `068874cb406a4964...` / `068874cb406a4964...` | **100% IDENTICAL** |
| `highlights` | 16 | 16 | `f019cb9152f87009...` / `f019cb9152f87009...` | **100% IDENTICAL** |
| `notes` | 3 | 3 | `86b7827ec123f344...` / `86b7827ec123f344...` | **100% IDENTICAL** |
| `reader_anchor_cache` | 4 | 4 | `ec922dd21e0e4d56...` / `ec922dd21e0e4d56...` | **100% IDENTICAL** |
| `reader_text_corrections` | 1 | 1 | `8556758c3a13693f...` / `8556758c3a13693f...` | **100% IDENTICAL** |
| `library_source_refs` | 11 | 11 | `7786363b03bc934e...` / `7786363b03bc934e...` | **100% IDENTICAL** |
| `android_metadata` | 1 | 1 | `547fed23cf7b0364...` / `547fed23cf7b0364...` | **100% IDENTICAL** |
| `room_master_table` | 1 | 1 | `b941fe3b30f90a9b...` / `b941fe3b30f90a9b...` | **100% IDENTICAL** |
| *其他未用业务表（11张）* | 0 | 0 | `4f53cda18c2baa0c...` / `4f53cda18c2baa0c...` | **100% IDENTICAL** |
| `reader_page_index` | 41 | 44 | `3fa6fecc94f4f80f...` / `292cd40775a0c1cc...` | **新增 3 行（合理解释）** |

### 核心对账结论与差异根因分析：
1. **核心阅读与书籍资产零受损**：
   - `books`（11 本书籍元数据、书名、排序、分组）100% 逐字段一致，**零增删、零改名**。
   - `reading_progress`（7 条阅读记录，包含阅读进度百分比、章节锚点偏移、更新时间戳）100% 逐字段一致，**零阅读点漂移**。
   - `reading_sessions`（90 条阅读会话时长）与 `chapter_reads`（8 条章节阅读足迹）100% 逐字段一致，**历史统计无任何回退**。
   - `book_content`（10 条书籍正文与章节解析缓存）与 `book_files`（11 份文件绑定）100% 逐字段一致。
2. **唯一差异非索引表（`reader_page_index`）分析**：
   - 该表行数由 41 行增至 44 行（新增 3 行）。
   - **发生机制**：在上一轮真机验收中，操作步骤包含了“点击全局搜索命中项，拉起阅读器跳转目标章节”（对应第 07 步验证）。阅读器加载目标章节时，底层排版引擎（`BookPaginator`）自动计算视口分页并将页码索引缓存写入 `reader_page_index`。
   - **性质定性**：属于真机阅读器正常展示时的非破坏性缓存追加，与全文搜索索引重建流程相互独立，充分证实无脏写风险。

---

## 四、搜索索引表状态与口径严格收窄

针对全文搜索索引表（`search_terms`、`search_index_coverage`、`search_index_state`），比对事实如下：

| 表名 (Table Name) | 重建前基线（Pre） | 重建后产物（Post） | 状态演进事实 |
|:---|:---|:---|:---|
| `search_terms` | 5,602,206 行（覆盖 6 本） | 5,605,851 行（覆盖 11 本） | 词条总数净增 3,645 行，覆盖书目由 6 本扩展至 11 本全部处理 |
| `search_index_coverage` | 12 行（5 full, 6 n/a, 1 partial） | 22 行（**10 full, 11 not_applicable, 1 partial**） | 详见下方口径收窄说明 |
| `search_index_state` | `built_at` = 1789828230376 | `built_at` = 1790149112279 | 状态标记完好，时间戳正常更新 |

### 关键口径严格收窄声明：
> [!WARNING]
> **绝对不能表述为“全书正文 100% 可搜索”**：
> 1. **书级处理进度 11/11**：UI 上显示的「已索引 11 / 11 本」表示书库内的 11 本书均已完成扫描与索引管线调度。
> 2. **实际覆盖率分布**：底层覆盖率记录为 **10 本 full（全量覆盖）、1 本 partial（部分覆盖）、11 条 not_applicable（display 层不适用标记）**。
> 3. **业务一致性**：在全局搜索截图（[`redacted/06_search_results_hits.png`](../evidence/2026-09-23-s03-1-real-library/redacted/06_search_results_hits.png)）中，顶部明确显示警示横幅：**「有 1 本书只索引了部分内容，正文搜索结果可能不全」**；且第四个搜索命中卡片也明确标注「仅索引部分章节」。
> 4. **对账结论**：索引重建结果与底层策略规则及 UI 提示 100% 严丝合缝，但必须如实定义为“覆盖度为 10 full + 1 partial”，不可夸大为正文全量覆盖。

---

## 五、备份边界与未覆盖项披露

### 1. 后置备份 Coverage 边界
- **重建前备份**：`cra_full_backup_pre_rebuild.tar`（1,153,464,320 字节，SHA256: `B92ACECC...`），包含 `databases/`、`files/datastore/`、`shared_prefs/` 与全部源文件。
- **重建后备份**：`post_rebuild_db.tar`（1,121,550,848 字节，Codex 复核 SHA256: `8C48ED61B923C40D19A511586F37ACAF508A8FB1245DDE4450D6FD2BFAB3EA96`），**仅包含 `databases/` 目录**。原报告所载 `8987483C...` 为记录错误。
- **边界说明**：因此，本轮“前后 100% 一致”的逐行摘要比对结论**严格限定于 SQLite 数据库体系**。关于 DataStore 偏好设置文件的比对未在离线后置备份中覆盖，不能在证据中声称文件级前后一致。

### 2. 真实设备极端故障注入未覆盖
- 在真实手机（`c49ac6cf`）上验证的是**一次自然端到端重建闭环**（耗时 13 分 45 秒，处理 560 万+ 倒排词条，主线程无 ANR）。
- 真实设备上的极端并发场景（例如：重建进行中人为强杀 App 进程、切断电源或系统 Low-Memory Killer 介入后的恢复），以及低于 Android 11（API 30）的 SQLite 999 变量上限，**未在真实物理机上进行破坏性注入**。此类极端场景继续由 JVM 定向并发测试（`19/19` 通过）与契约测试单列保障。

---

## 六、证据脱敏与目视审查清单

所有公开证据截图均位于 [`docs/qa/evidence/2026-09-23-s03-1-real-library/redacted/`](../evidence/2026-09-23-s03-1-real-library/redacted)，原图作为本地私有测试资产保留但不提交。所有脱敏副本均已完成目视复核：

| 截图编号 | 截图文件名 | 脱敏处理方式 | 目视复核结果 |
|:---:|:---|:---|:---:|
| 01 | [`01_storage_pre_rebuild.png`](../evidence/2026-09-23-s03-1-real-library/redacted/01_storage_pre_rebuild.png) | 仅含统计指标与概览分布，无私有书名 | **合格**（原图无敏感信息） |
| 02 | [`02_rebuild_confirm_dialog.png`](../evidence/2026-09-23-s03-1-real-library/redacted/02_rebuild_confirm_dialog.png) | 重建确认对话框，无私有书名 | **合格**（原图无敏感信息） |
| 03 | [`03_rebuild_in_progress.png`](../evidence/2026-09-23-s03-1-real-library/redacted/03_rebuild_in_progress.png) | 后台构建进度条（6/11 54%），无私有书名 | **合格**（原图无敏感信息） |
| 04 | [`04_rebuild_completed_11_of_11.png`](../evidence/2026-09-23-s03-1-real-library/redacted/04_rebuild_completed_11_of_11.png) | 覆盖度已索引 11/11，无私有书名 | **合格**（原图无敏感信息） |
| 05 | [`05_home_post_rebuild.png`](../evidence/2026-09-23-s03-1-real-library/redacted/05_home_post_rebuild.png) | 封面使用平滑圆角块遮盖，标题覆盖为中性文字，已读完书架整块遮盖，彻底消除方块乱码 | **合格**（无任何字符泄露） |
| 06 | [`06_search_results_hits.png`](../evidence/2026-09-23-s03-1-real-library/redacted/06_search_results_hits.png) | 6 处命中卡片书名使用中性色块遮盖，保留章节字偏移，保留顶部「1 本书部分内容」提示 | **合格**（排版工整，无方块乱码） |
| 07 | [`07_search_hit_jump_reader.png`](../evidence/2026-09-23-s03-1-real-library/redacted/07_search_hit_jump_reader.png) | 连续视口截帧，第 6 处命中卡片书名及原标识符彻底遮盖 | **合格**（无任何标识符泄露） |

---

## 七、离线收口最终裁决

综合底层数据库逐行 SHA-256 摘要对账、Schema 校验、索引覆盖分布收窄及证据脱敏审查：

### **裁决结论：PASS WITH LIMITATIONS**

1. **核心通过项（PASS）**：
   - **真实书库自然重建路径跑通**：耗时 13 分 45 秒平稳完成 560 万+ 倒排词条索引构建，无崩溃无阻塞。
   - **核心非索引资产 100% 无损**：`books`、`reading_progress`、`reading_sessions`、`chapter_reads`、`book_content` 经逐行哈希验证为 100% 一致，阅读进度零漂移、书名零篡改。
   - **排版索引增量行为合规**：`reader_page_index` 新增 3 行系跳转触发即时排版缓存所致，机制合理。
   - **证据脱敏彻底**：7 张证据截图彻底去隐私化，原图隔离为本地私有资产。
2. **限定项与边界（LIMITATIONS）**：
   - 覆盖度口径严格限定为 **10 full + 1 partial**，并非全书 100% 正文可搜索。
   - 离线前后一致性对账覆盖范围仅限 SQLite 数据库体系，DataStore 偏好设置未在后置 tar 中覆盖。
   - 极端断电/破坏性并发故障未在真实设备注入，由 JVM 确定性测试作为补充证据单列。
