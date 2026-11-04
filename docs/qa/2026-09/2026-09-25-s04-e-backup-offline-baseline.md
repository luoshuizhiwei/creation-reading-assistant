# S04 E 盘备份离线内容基线（2026-09-25）

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 性质：纯离线工作。本轮未运行 adb、Gradle connected、安装、instrumentation；未启动/停止手机应用；未触碰原始仓库（`creation-reading-assistant`）、真机书库、`verification-copy/`、`formal-app-data.tar` 与备份目录 `README.md`；未 commit / push / stage / reset / checkout / clean。
- 结论先行：**基线已生成并通过全部检查，本轮无 BLOCKED 项。** 该基线属于"对账基线"档，不构成真机测试放行（验机前独立审查 §0 的 BLOCKED 状态不变）。

## 1. 输入与所有权

| 项 | 路径 | 状态 |
|---|---|---|
| 输入归档（只读） | `E:\creation-reading-assistant-backups\s04-qahost-2026-09-25\formal-app-data.tar` | 未修改（实测仅读取） |
| 本轮新增 | `android/scripts/s04_private_archive_manifest.py` | 基线/比较工具 |
| 本轮新增 | `android/scripts/s04_private_archive_manifest_tests.py` | 定向测试（21 例） |
| 本轮新增 | `docs/qa/2026-09/2026-09-25-s04-e-backup-offline-baseline.md` | 本报告 |
| 本轮新增 | `E:\creation-reading-assistant-backups\s04-qahost-2026-09-25\baseline-manifest.json` | 私有内容级基线（仓库外） |

## 2. 归档核验结果

预期值（任务指定）：SHA-256 `9DFF292C806F33EA17659BE99072E01B72C82891E2394CED2D6E6E870DB8D29C`，大小 2,294,109,184 字节。

| 检查 | 结果 |
|---|---|
| 独立 `sha256sum` 实测 | `9dff292c806f33ea17659be99072e01b72c82891e2394ced2d6e6e870db8d29c` = 预期值，通过 |
| 归档大小 | 2,294,109,184 字节 = 预期值，通过 |
| 成员结构（`tar -tf`/`-tvf` 聚合预检） | 84 个成员；普通文件 54、目录 30；无符号/硬链接/设备成员；无重复成员名 |
| 四根白名单（databases/、files/、shared_prefs/、no_backup/） | 恰好四根，无绝对路径、无 `..`，通过 |
| 端部完整性（工具校验） | 结束标记后零填充、末尾 1024 字节全零，通过 |

## 3. 基线生成结果

命令（仓库根执行，Python 3.13.2）：

```
python android/scripts/s04_private_archive_manifest.py manifest \
  --archive "E:\creation-reading-assistant-backups\s04-qahost-2026-09-25\formal-app-data.tar" \
  --output "E:\creation-reading-assistant-backups\s04-qahost-2026-09-25\baseline-manifest.json" \
  --expect-sha256 9DFF292C806F33EA17659BE99072E01B72C82891E2394CED2D6E6E870DB8D29C \
  --expect-size 2294109184 \
  --expect-workdb-in-no-backup
```

实际输出摘要（**退出码 0**）：

- 归档：2,294,109,184 字节，SHA-256 同上。
- 成员：84（目录 30 + 普通文件 54）；各根文件数：databases=5、files=39、shared_prefs=7、no_backup=3。
- 文件内容字节合计：2,294,048,721。
- WorkManager workdb 位置核对：`WORKDB_CHECK: ok (main=1 wal=1 shm=1)` —— 主库及 `-wal`/`-shm` 三件均在 `no_backup/` 下。
- 基线文件：`baseline-manifest.json`（13,310 字节），自身 SHA-256 `FE42381E4515A02F3ABA528066B0C07E1469DEE65FDD26639A94BED0D659D352`（完整性锚；若该文件被改动，与此值不符即不可信。自第 2 轮加固起，该值成为 compare 模式的强制信任锚，见 §8）。

### WorkManager workdb 位置发现（重要）

`androidx.work.workdb`、`androidx.work.workdb-wal`、`androidx.work.workdb-shm` 实际位于 `no_backup/`（与备份 README 一致）。此前 `docs/code-review/2026-09/2026-09-25-s04-qahost-predevice-review.md` §5-D-13 写的 `databases/androidx.work.workdb` 与归档实际不符；按任务要求**未改动旧审查文档**，此处仅记录差异，后续 workdb 对账应以 `no_backup/` 为准。

### 真实归档自比较（可重复性验证）

```
python android/scripts/s04_private_archive_manifest.py compare \
  --archive "…\formal-app-data.tar" \
  --baseline "…\baseline-manifest.json" \
  --expect-sha256 9DFF292C…8D29C --expect-size 2294109184
```

实际输出：`RESULT: PASS`、`TOTAL_DIFFERENCES: 0`，全部差异类别为 0，**退出码 0**。即对同一真实归档的两遍独立全量扫描（约 2.29 GB × 2）逐文件内容哈希完全一致，"两次生成可重放"在真实数据上成立。

## 4. 工具设计与定向测试

工具要点：单遍流式（tarfile `r|` 流模式 + 哈希包装读），同时完成归档级 SHA-256、逐成员安全校验与逐文件内容 SHA-256；内容级 = 相对路径 + 字节数 + SHA-256，刻意忽略 tar 元数据（mtime/mode/uid/gid）；安全规则见脚本头注（四根白名单、拒绝绝对路径/`..`/盘符/反斜杠/冒号/控制字符/非 UTF-8 名、拒绝重复成员与链接、成员读出字节数必须与头部一致、结束标记后残留与末尾 1024 字节必须全零）；输出位置强制在 git 仓库之外；stdout 永不输出成员/文件名或内容。

测试命令（仓库根执行）：

```
python android/scripts/s04_private_archive_manifest_tests.py
```

**实际结果：21 例全部通过，退出码 0**（全部基于小型合成 tar，无真实数据）。覆盖：

| 类别 | 用例（均断言退出码与输出类别） |
|---|---|
| manifest 正常路径 | 逐文件哈希已知答案（b"hello" 的 SHA-256）、归档哈希/大小、按根计数、排序、清单自身哈希 |
| 期望强制 | 正确 SHA-256（大小写不敏感）+ 大小通过；错误哈希/大小 → BLOCKED 且不写出清单 |
| 可重复性 | 同一归档两次生成，files/counts/directories/archive 字段完全一致 |
| 成员安全 | 绝对路径、`..`、重复成员、符号链接、硬链接、非法根、反斜杠、盘符、冒号、根级普通文件、控制字符、非 UTF-8 名 → 各自 BLOCKED 且输出不含成员名 |
| 结构完整 | 成员边界截断、数据中部截断、结束标记后追加垃圾 → 均 BLOCKED |
| 输出防泄漏 | 清单/证据写入 git 仓库内被拒；比较基线位于 git 仓库内被拒 |
| workdb 位置 | no_backup/ 通过；databases/（旧文档写法）被拒；workdb 缺失被拒 |
| compare | 自比较 PASS；同大小内容改动、新增、删除、WAL 改动、prefs 改动、no_backup 根缺失均检出（退出码 1，stdout 仅数量与类别、断言不含私有文件名）；被篡改基线、不安全新归档、期望哈希不符 → BLOCKED；差异证据 JSON 写入指定私有路径 |

## 5. 未覆盖边界（诚实登记）

1. **"测试后归档"比较尚未发生**：本基线的比较路径目前只在合成 tar 与真实归档自比较上验证过；真实测试后归档的比较属后续设备阶段的产物，本轮无授权执行。
2. 内容级 = 路径 + 字节数 + SHA-256；**tar 元数据差异被刻意忽略**（设计决定，不是缺陷，但意味着比较不感知权限/属主变化）。
3. 深层**空目录**的增删不参与差异（只有根目录缺失会被检出）；普通文件被同名目录替换会以"删除"呈现。
4. 工具做的是**归档结构与字节级**核验，不做 SQLite `integrity_check`、不做任何数据库逻辑解读；字节级差异一律保守标待归因（UNATTRIBUTED），**不存在"行数未变即放行"的通道**。
5. 截断恰好发生在末尾零填充区内部的归档（成员数据完整）不会被判 BLOCKED——此时所有成员均已完整解析，属可接受残余。
6. 若 tarfile 在归档中途遇到连续零块会提前停止，此时"残留字节必须全零"校验会将其判为 BLOCKED（不会静默丢成员）——真实归档不产生这种形态，已由 84 成员数与 `tar -tf` 预检吻合佐证。
7. `baseline-manifest.json` 是普通 JSON，无签名；完整性锚 = 本报告 §3 记录的其自身 SHA-256，使用前应复核。基线、证据 JSON 与归档均含隐私（书籍派生 id、prefs 凭据等），必须留在仓库外、不进入任何同步/上传渠道。
8. 本基线描述的是 E 盘归档的**内容**，不重新定义其捕获质量：恢复/捕获过程仍 UNVERIFIED（见验机前独立审查 §6），`verification-copy/` 因 WAL/SHM 可能已被调整，明确不作为基线或恢复点使用。

## 6. 后续使用方式（测试后归档到达时）

```
python android/scripts/s04_private_archive_manifest.py compare ^
  --archive "<测试后归档.tar>" ^
  --baseline "E:\creation-reading-assistant-backups\s04-qahost-2026-09-25\baseline-manifest.json" ^
  --expect-baseline-sha256 FE42381E4515A02F3ABA528066B0C07E1469DEE65FDD26639A94BED0D659D352 ^
  --evidence-json "<仓库外私有路径>\diff-evidence.json"
```

`--expect-baseline-sha256` 为必填信任锚（第 2 轮加固起强制）：工具在解析、信任基线 JSON 之前，先对基线文件原始字节计算 SHA-256 并与该值比对，缺参、格式错误或不符一律 BLOCKED。退出码：0=PASS（逐字节一致）；1=存在差异（stdout 只给数量与类别，差异属 UNATTRIBUTED，须人工归因后才可继续）；2=BLOCKED。仅当确需逐文件差异明细做人工归因时才传 `--evidence-json`，且必须写到仓库外私有位置。

## 7. 隐私声明

本报告、测试输出与工具 stdout 均不含真实书名、逐文件清单、数据库行或偏好值；文件名一律不出现，仅出现四根目录名与聚合计数。归档内容、逐文件清单与偏好凭据只存在于 E 盘私有文件中。

## 8. 第 2 轮：证据保护加固（2026-09-25，Codex 独立复核后）

触发来源：Codex 独立复核确认第 1 轮 21/21 定向测试通过、真实归档自比较 PASS，同时指出两个证据保护缺口——比较时基线文件本身可能被改动而工具不会察觉；manifest 重新运行会悄悄覆盖既有基线。本轮仅修改脚本、测试与本报告三个文件，**未重新生成、未覆盖 E 盘任何文件**。

### 8.1 修复内容

1. **compare 强制基线哈希锚**：新增必填参数 `--expect-baseline-sha256`。工具在解析、信任基线 JSON **之前**，先对基线文件原始字节计算 SHA-256 并与该预期比对（从同一份字节既做哈希又做解析，避免 TOCTOU）。缺参 → `EXPECTED_BASELINE_SHA256_MISSING`；格式错误 → `EXPECTED_BASELINE_SHA256_INVALID_FORMAT`；不符 → `EXPECTED_BASELINE_SHA256_MISMATCH`（只输出预期/实际哈希，不解析基线，不输出其中任何路径或内容）。三者一律 BLOCKED、非零退出，不执行 PASS 判定。长期信任锚 = §3 记录的 `FE42381E…D352`。
2. **manifest 拒绝覆盖既有输出**：输出文件已存在时默认拒绝（`MANIFEST_OUTPUT_EXISTS`），既有文件保持逐字节不变、无 `.tmp` 残留，且在扫描归档前即拒绝（快速失败）。本轮**不提供**任何可覆盖正式基线的快捷参数；如未来确需重建基线，须走新路径并另行登记授权。

### 8.2 实际命令与退出码（本轮新鲜执行）

测试（23 例，较第 1 轮 +2 个测试方法、重构 1 个；新增覆盖：基线哈希锚缺参/格式错误/错误哈希、"JSON 仍合法但内容被改动"被旧锚拒绝、manifest 重复生成被拒且旧文件哈希不变；原有同大小改动、新增/删除、WAL、prefs、no_backup 检出测试全部保留）：

```
python android/scripts/s04_private_archive_manifest_tests.py
```

实际结果：**23 例全部通过，退出码 0**（全部基于小型合成 tar）。

真实归档只读自比较（显式传入两个预期 SHA-256 与归档大小；比较模式不写任何文件）：

```
python android/scripts/s04_private_archive_manifest.py compare \
  --archive "E:\creation-reading-assistant-backups\s04-qahost-2026-09-25\formal-app-data.tar" \
  --baseline "E:\creation-reading-assistant-backups\s04-qahost-2026-09-25\baseline-manifest.json" \
  --expect-baseline-sha256 FE42381E4515A02F3ABA528066B0C07E1469DEE65FDD26639A94BED0D659D352 \
  --expect-sha256 9DFF292C806F33EA17659BE99072E01B72C82891E2394CED2D6E6E870DB8D29C \
  --expect-size 2294109184
```

实际结果：基线哈希锚校验通过 → `RESULT: PASS`、`TOTAL_DIFFERENCES: 0`、全部差异类别为 0，**退出码 0**；比较后复核基线文件哈希仍为 `FE42381E…D352`，`formal-app-data.tar`、`README.md`、`verification-copy/` 均未被触碰，E 盘无新增或改写文件。

### 8.3 第 2 轮新增未覆盖边界

1. 基线哈希锚由调用方在命令行提供，工具只能验证"基线文件与预期值一致"，无法验证调用者提供的预期值本身是否正确（例如从登记处抄错）——锚的正确性依赖本报告 §3 记录值与操作纪律，这也是 `baseline-manifest.json` 的哈希必须在本报告登记的原因。
2. `EXPECTED_BASELINE_SHA256_MISMATCH` 时工具不解析基线 JSON（哈希先行），因此该场景下不存在部分比较输出；但"哈希一致而语义错误"的基线只能由深层结构校验（schema、逐条目字段、计数一致性）与人工复核兜底，深层校验不解读业务语义。
3. manifest 拒绝覆盖是"默认拒绝、无参数绕过"的设计决定，不是可配置项；重复生成基线的合法途径只有"新路径 + 重新登记"，本报告不预授权任何重建。
4. 第 1 轮 §5 的全部边界继续有效；§4 记录的是第 1 轮工具状态，以本节为最新防线描述。

## 9. 第 3 轮：最终落盘竞态加固（2026-09-25，Codex 复核后）

触发来源：Codex 复核指出 TOCTOU 竞态——manifest 在扫描前用 `output.exists()` 判断"不存在"，但扫描后由 `write_private_json` 用 `os.replace()` 落盘。若另一进程恰在扫描期间（可能数 GB、耗时较长）创建同名基线，`os.replace` 会把它静默覆盖。本轮仅修改脚本、测试与本报告三个文件，**未重新生成、未覆盖 E 盘任何文件**。

### 9.1 修复内容

1. **最终写入改为"独占占位 + 原子发布"**：`write_private_json` 在写临时文件之前，先用 `os.open(target, O_CREAT | O_EXCL | O_WRONLY)` 独占占位。若目标已存在（含扫描期间被另一进程创建），立即 BLOCKED（`<ROLE>_OUTPUT_EXISTS`），既有文件逐字节不变，且不产生任何临时文件；占位成功后再把完整内容写入同名 `.tmp` 并以 `os.replace()` 原子发布。发布前任何异常都会回滚占位目标与临时文件，避免留下半成品。目标与临时文件均以 `0o600` 创建（POSIX 生效；Windows 忽略 mode），使最终发布的私有文件不再继承宽松默认权限（否则占位时的 `0o600` 会被 `os.replace` 换成临时文件的宽松权限）。
2. **扫描前 `output.exists()` 检查保留**：作为"扫描前快速失败"（避免为数 GB 归档白扫），但不再是唯一防线；扫描后的独占占位是权威防线。
3. 加固位于公共落盘函数，因此 `--evidence-json` 证据文件同样获得"绝不覆盖既有文件"的保证：目标证据文件已存在时 BLOCKED（`EVIDENCE_OUTPUT_EXISTS`），需换新路径。注意此时 `cmd_compare` 已在写证据之前打印了 `RESULT: DIFF`，故 stdout 会同时出现 `RESULT: DIFF` 与随后的 `RESULT: BLOCKED`（退出码以 2 为准，BLOCKED 优先）；如需差异证据请始终使用新的仓库外私有路径。

### 9.2 新增确定性竞态测试

新增 `test_manifest_blocks_when_output_appears_during_scan`：通过包装模块的 `scan_archive`，在"扫描完成、落盘之前"向目标路径注入一个同名文件，精确命中 TOCTOU 窗口（不依赖真实并发与时序）。断言：退出码 2、`RESULT: BLOCKED`、`REASON: MANIFEST_OUTPUT_EXISTS`、无 `RESULT: OK`；且该原文件逐字节不变、无 `.tmp` 残留、其内容不出现在任何输出。

### 9.3 实际命令与退出码（本轮新鲜执行）

定向测试（24 例，较第 2 轮 +1）：

```
python android/scripts/s04_private_archive_manifest_tests.py
```

实际结果：**24 例全部通过，退出码 0**（全部基于小型合成 tar）。原有 23 例全部保留并通过，其中 compare 模式必填基线哈希锚的全部断言（缺参 / 格式错误 / 错误哈希 / 被篡改基线拒绝）不变。在 §9.1-1 的 `0o600` 权限调整后再次新鲜复跑，仍为 24/24 通过。

真实归档只读自比较（显式传入基线哈希锚与归档预期 SHA-256 / 大小；比较模式不写任何文件）：

```
python android/scripts/s04_private_archive_manifest.py compare \
  --archive "E:\creation-reading-assistant-backups\s04-qahost-2026-09-25\formal-app-data.tar" \
  --baseline "E:\creation-reading-assistant-backups\s04-qahost-2026-09-25\baseline-manifest.json" \
  --expect-baseline-sha256 FE42381E4515A02F3ABA528066B0C07E1469DEE65FDD26639A94BED0D659D352 \
  --expect-sha256 9DFF292C806F33EA17659BE99072E01B72C82891E2394CED2D6E6E870DB8D29C \
  --expect-size 2294109184
```

实际结果：`RESULT: PASS`、`TOTAL_DIFFERENCES: 0`、全部差异类别为 0，**退出码 0**。运行前后复核：`baseline-manifest.json` SHA-256 均为 `FE42381E…D352`、mtime 未变；`formal-app-data.tar` mtime 未变；`README.md` 与 `verification-copy/` 均未被触碰，E 盘无新增或改写文件。

### 9.4 第 3 轮新增未覆盖边界

1. 独占占位与原子发布保证"最终目标绝不会被覆盖"，但不阻止另一进程在扫描期间创建同名文件本身——那种情况一律 BLOCKED，需人工处理（改名重跑或人工核对）。
2. 占位成功到 `os.replace()` 之间存在极短的"空文件窗口"。该窗口内，遵守 `O_EXCL` / 不覆盖语义的写入者无法占用该名字（会收到 `FileExistsError`）；但一个**无视已存在文件、直接截断写入**的朴素写入者（例如 `open(target, "w")`，或本工具加固前的旧实现）仍可能在该窗口内截断占位文件，随后被 `os.replace()` 覆盖。这是"占位 + 改名"惯用法的固有残余窗口，对本工具的目标威胁模型（同名基线在数 GB 扫描期间出现，即发生在占位**之前**）不构成问题；若将来要求对朴素写入者也严格无覆盖，需改用 `os.link(tmp, target)`（原子 `EEXIST`）等更强原语。
3. 独占占位依赖文件系统支持 `O_EXCL`（NTFS / 常见 POSIX 文件系统均支持）；本轮的 fixture 与 E 盘只读比较均验证通过。第 1、2 轮全部边界继续有效。

### 9.5 独立复核与处置

本轮改动经独立代码复核（只读、未改文件）。复核确认：核心修复正确，不存在可静默覆盖既有目标的路径；新增竞态测试**非空转**——用加固前的旧实现重放同一场景会得到 `rc=0` 且既有文件被覆盖，即该测试对旧实现会失败。复核提出的三点已处置：

1. **权限模式失效（已修）**：占位用 `0o600`，但 `os.replace` 会换成临时文件的默认权限。现改为临时文件也以 `0o600` 创建，使发布后的私有文件在 POSIX 上真正为 `0o600`（见 §9.1-1）。
2. **证据文件复用输出不一致（已登记）**：见 §9.1-3，退出码以 2（BLOCKED）为准。
3. **"空文件窗口"表述过强（已修正）**：见 §9.4-2，仅对遵守不覆盖语义的写入者成立；对朴素截断写入者的残余窗口属惯用法固有，已如实登记。

