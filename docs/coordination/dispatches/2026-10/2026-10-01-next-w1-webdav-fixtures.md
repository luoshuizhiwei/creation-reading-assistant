# W1-v1 — WebDAV 可追溯测试服务与合法导入夹具报告

- 任务编号/版本：**W1-v1**（依据 Codex-review-J1v2-J2v2-v1，2026-10-01 的下一轮安排）。
- 执行者：ZCode/GLM（离线准备）；核查：Codex；**本任务零 ADB、零设备操作**。
- 工作目录：`E:\creation-reading-assistant\next-round-w1-webdav-fixtures`（新建，本轮唯一写入点）。产品输入 `next-round-joint-j1-v2\android` 与旧服务 `next-round-j2-acceptance` 全程只读；未修改产品、构建配置、测试源码、原服务/原日志、freeze 或全局交接文件；未 commit/push/reset/checkout，未改原损坏仓库。

## 1. 输入指纹

- 恢复仓库 HEAD：`e39a78da9c62f02614207cffb36969a32492eaea`。
- 产品合同只读自 J1-v2 已核候选（六文件清单与本轮实算一致）：
  - `LegadoReadingProgressImporter.parseProgress` 实际读取键：`name`、`author`、`durChapterIndex`（必填≥0）、`durChapterPos`（必填≥0）、`durChapterTime`、`durChapterTitle`、**`totalChapterNum`**（>0 → 估算百分比 min(ci/总章数×100, 99.4)）。**`totalChapters` 不是解析器字段**——旧夹具的字段错误即 J2 "导入 0 条/跳过" 的直接根因；`bookshelf.json` 必须是 JSON 数组（HapeLee Backup.backup 形态）。
  - `WebDavBackup`（R2 版）请求合同：`test()` = HEAD→2xx 成功 / 401·403 报"凭据可能不正确"（不回退）/ 405·501 → PROPFIND Depth:0→2xx；`put()` = PUT→2xx / 401·403 直接抛错（不 MKCOL 不重试）/ 404·409 → 一次 MKCOL（201/200/204/405 均视为就绪）→ 至多一次重试 PUT；`listBackups()` = PROPFIND Depth:1；Legado 备份过滤 `backup*.zip`。
  - `JsonBridge.LocalExport`：`schemaVersion` 必须 =1；信封 camelCase `updatedAt`，实体 snake_case。
  - `ProfileViewModel.testWebDav()` → `webDavManager.test()` → 读 `WebDavConfigStore.config`（SecurePrefs `webdav_config`）；**输入框编辑不自动保存**。

## 2. 所有权与改动（全部为新建工件）

| 工件 | SHA-256 |
|---|---|
| `service/controlled_webdav_w1.py`（可追溯受控服务） | 680408000a035b78a450144059880c2f5d5017e21d01975d3c16f102bfdd86ea |
| `fixtures/backup-20261001-测试 空格.zip`（确定性，双构建同 SHA） | aec6d4ff8f6114b8b82d858be193cdc527817c80b34a1372f83c879d376aaecf |
| `fixtures/cra-backup-20261001-测试 空格.json` | 4bcbeabf3594c834082ee38e58b6211612c74046ec2f58d0c8c702d0de477770 |
| `fixtures/build_fixtures.py`（含自校验与确定性时间戳） | 18b353c92262e1a956433c942bdd559275b52ca2fe0c083e6f7c3c316f8e7342 |
| `selfcheck/run_selfcheck.py` | 97a09dac3b0a902e1da53507623e6fd1e0861e7b9fbe0fd08fd8a606d6d34d4a |
| `README-operations.md`（设备批次操作说明） | 287d7950428b33e674f381c0649d23df61d416f288091cc5f405391b65cb5204 |
| 汇总 `SHA256SUMS.txt` / `fixtures/SHA256SUMS` / `fixtures/build-output.txt` / 自检结果与两个会话日志 | 见 `SHA256SUMS.txt` |

## 3. 服务（对旧服务的四项修正）

1. **回环绑定**：仅 `127.0.0.1:18081`（旧版 `0.0.0.0`；避开 DSH 占用的 18080）。
2. **日志不可覆盖**：按会话追加 `logs/sessions/<session>.jsonl`；`reset` 只重置场景与计数并在当前会话日志追加一条 `reset` 事件，**绝不删除/截断任何日志文件**（旧版 reset 会删 `webdav_requests.jsonl`）。每条记录：ts/session/case/scenario/seq/method/原始 path/unquoted_path/has_double_encoding/脱敏 headers/body_len/response_code/计数快照。
3. **凭据脱敏**：`Authorization` 等敏感头替换为 `<redacted len=N>`（旧版原样落盘）。
4. **确定性场景**与产品合同逐一对应：`head_405_propfind`、`put_409_mkcol_retry`、`auth_401`、`auth_403`、`default`（含 Depth:1 清单，内含中文+空格备份文件的百分号编码 href 与原文 displayname）；GET 直接从磁盘返回夹具字节，保证设备下载内容与本地夹具 SHA 一致。

## 4. 夹具要点（前置/预期全文见 README-operations.md §4）

- **Legado ZIP**（中文名+空格，匹配 `backup*.zip` 过滤）：单条 `bookshelf.json` 记录
  `测试 TXT 中性甲/中性作者, durChapterIndex=3, durChapterPos=120, durChapterTime=1767000000000, durChapterTitle=第四章, totalChapterNum=10`。
  预置前置：隔离库中唯一匹配的 txt 书行（revision 1）+ 既有 Legado 标记进度 `{"legado_position_v1":{"ci":1,"co":50}}`（reading，无 completed_at）+ 本地独有对照书 `测试 EPUB 中性乙`。
  导入后预期：**Report imported=1（导入 0 条/全部跳过不得记为成功）**、percent=30.0、last_read_at=`2025-12-29T05:20:00Z`、location=`{"legado_position_v1":{"ci":3,"co":120}}`、completion_state 保持 reading、completed_at 保持 null、对照书原样不变。
- **JSON 恢复夹具**（schemaVersion=1）：`w1-txt-a` books+progress 信封（revision 2）。
  合并条件：远端 revision 2 > 本地 1 → 接受（本地 ≥2 则拒绝保持）；恢复后标题/作者/size/revision/updated_at 与进度（42.0/reading/location v2）按 payload 全量替换；**不应变化**：JSON 外的行（`w1-epub-b` 及其进度）、books 数量只增不减、版本不符整体拒绝零写入。已注明：JSON 恢复是实体全量替换（payload 未带 `local_content_path` 时该列置空），与 LAN 同步合并的"本机事实保留"不同——JsonBridge 既有合同，如需对齐另立任务。
- 夹具自校验：ZIP 可解包、键与解析器一致、JSON 可解析、schemaVersion 与信封键齐全；ZIP 固定时间戳（默认写当前时间曾导致两次构建 SHA 不同，已修正并双构建验证逐字节一致）。

## 5. 离线自检（真实 HTTP，回环，零 ADB）

`selfcheck/run_selfcheck.py` → `selfcheck-result.txt`：**27/27 PASS，退出码 0**。证明：

- 会话 A 顺序 HEAD→405(+Allow) → PROPFIND D0→207；会话 B 顺序 PUT→409 → MKCOL→201 → PUT→201；
- 401/403 全方法确定性拒绝；
- reset 前后同一会话日志追加保留（`['request','reset','request']`），两个会话文件互不覆盖、session 字段不串档；
- 中文+空格请求以原始（百分号编码）path 与 unquoted_path 同时记录、双重编码请求被 `has_double_encoding` 标记；
- GET 夹具字节与本地文件 SHA 一致；`Authorization` 原文（`dXNlcjpwYXNz`）不出现在任何日志文件。

**边界**：离线自检只证明服务与夹具准备成立；产品侧"401/403 不回退""公网拒绝"等行为必须由设备端请求日志缺席 + UI 证据证明（README §5 给出证据组合要求），本报告不写任何 App/MuMu 验收通过。

## 6. UI 输入验收路径与沙箱条件（README §5/§6 摘要）

- `testWebDav()` 读**已保存**配置：验收必须先显式保存（保存不发请求），再点测试并以服务端收到请求为判定；并做反向对照（保存 9999 端口 → 仅改输入框不保存 → 请求仍打向 9999）证明"编辑层 ≠ 配置层"。
- 公网 HTTP 拒绝的证据组合 = 产品 UI 拒绝提示 + 当前会话服务日志 0 请求 + 同会话对照请求证明服务在线；"本地服务器没收到请求"单独不作证明。
- 设备沙箱：同签名覆盖安装（严禁 uninstall/pm clear，旧 KeyStore 状态未证明）；先冷备 APK 对/持久数据/系统设置；书库只含中性测试书与精确预置进度，**不得自动用原书库做测试**；无法保证书库隔离时停止点=不开展 UI 导入；J2-v3（四项仪器，配置/身份 mock）与 UI 导入验收不可混批。

## 7. 未覆盖项

- 设备端一切行为（请求序列、UI 文案、导入/恢复 DB 对账、恢复协议执行）——待 Codex 核查与设备批次。
- `LegadoProgressMarker`/`LocatorCodec` 与真机阅读器打开导入位置的端到端换算（属设备验收范围）。
- 产品侧任何缺陷（如发现走 SEAM REQUEST）；本轮未改产品。

## 8. 交付状态与下一步

- **W1-v1 交付：READY（服务+夹具+离线自检）**；App/MuMu 验收 = NOT RUN（按边界不写通过）。
- 下一步：Codex 核查（重点：字段合同对应、reset 不可覆盖语义、脱敏、UI 已存配置路径）；核查通过后由设备窗口按 README-operations.md 执行 WebDAV UI/导入验收，会话化日志随用例归档。
