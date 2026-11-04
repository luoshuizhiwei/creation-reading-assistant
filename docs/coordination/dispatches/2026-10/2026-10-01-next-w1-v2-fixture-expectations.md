# W1-v2 — WebDAV 夹具预期与操作说明更正报告

- 任务编号/版本：**W1-v2**（依据 Codex-review-J2v3-W1-v1 Spec.2/3/4/5：说明中的时间、保存阶段、合并条件与字段归因需更正）。
- 执行者：ZCode/GLM（文档与定向核对）；核查：Codex；**零设备操作、零产品修改**。
- 目录：`E:\creation-reading-assistant\next-round-w1-webdav-fixtures-v2`（独立新版本，**不覆盖 v1**；v1 及原报告/日志只读）。未 commit/push/reset/checkout。

## 1. 输入指纹

- HEAD：`e39a78da9c62f02614207cffb36969a32492eaea`。
- **服务与夹具字节未变**（不通过改数据凑预期）：`service/controlled_webdav_w1.py` SHA `680408000a035b78a450144059880c2f5d5017e21d01975d3c16f102bfdd86ea`、ZIP `aec6d4ff8f6114b8b82d858be193cdc527817c80b34a1372f83c879d376aaecf`、JSON `4bcbeabf3594c834082ee38e58b6211612c74046ec2f58d0c8c702d0de477770`——三者与 v1 逐字节一致。
- 源码事实复核（只读 J1-v2 候选）：`ProfileViewModel.saveWebDav()` 第 561-569 行——写入配置后调用 `loadWebDavBackups()`，后者发 `webDavManager.listBackups()`（PROPFIND 目录查询）；`SyncMergePolicy.shouldAcceptRemote` 第 16-23 行——远端 revision 大接受、小拒绝、**相等比较 `updatedAt` 字符串**。

## 2. 四组更正（README-operations.md + fixtures/build_fixtures.py 注释）

| # | 原说明（错误） | 更正后 |
|---|---|---|
| 1 | ZIP `durChapterTime=1767000000000` → `2025-12-29T05:20:00Z` | **`2025-12-29T09:20:00Z`**（三通道验算；夹具毫秒字节不变） |
| 2 | "保存不发请求，服务端应尚未收到任何请求"；把保存与测试混为一谈 | **分两阶段**：阶段 1 保存/刷新——`saveWebDav()` 调 `loadWebDavBackups()` 会发 **1 条 PROPFIND 目录查询**（"不是零请求"）；阶段 2 连接测试——以服务端新增 HEAD（或 405 后 PROPFIND Depth:0）为判定；反向对照（保存 9999 → 仅改输入框不保存 → 测试仍打向 9999）继续有效 |
| 3 | "本地 revision ≥ 2 一律拒绝" | **三分支**：远端>本地接受；远端<本地拒绝；**相等时比较 `updatedAt`**（远端更大才接受）；反例分支已写入验收建议 |
| 4 | 把 `totalChapters` 字段名问题认作旧 `unmatched=1` 的直接原因 | **拆开**：`totalChapterNum` 只影响**百分比估算**（缺/错键 → 0%）；`unmatched` 由 **title/author 未匹配**产生，与总章数字段无关 |

## 3. 定向核对（`verification/verify_expectations.py` → `verification/utc-and-merge-check.txt`）

**23/23 PASS，退出码 0**：

- 时间：`1767000000000 → 2025-12-29T09:20:00Z`；对照 `1760000000000 → 2025-10-09T08:53:20Z`（与 J1-v3 同源验算一致）；
- 夹具完整性（字节不变）：bookshelf 数组单条、`durChapterTime` 换算、字段名 `totalChapterNum`（且无 `totalChapters`）、估算百分比 30.0 = min(3/10×100, 99.4)、location 预期串、JSON `schemaVersion=1`、progress revision=2/percent=42.0；
- 匹配前置（与 `BookKey.normalize` 同构：trim+空白折叠+lowercase）：夹具 name/author 与预置书行同键；author 不同则 unmatched；normalize 容忍空白/大小写；**匹配与 `totalChapterNum` 无关**；
- 合并条件（与 `shouldAcceptRemote` 同构）：三分支四例（2>1 接受、2<3 拒绝、同 rev 远端新接受、同 rev 远端旧拒绝）；
- 文档一致性：README/构建脚本注释的四处更正均已落盘（脚本按字符串断言校验）。

## 4. 服务自检（复用 + 新副本复跑）

- 服务逻辑未变（SHA 逐字节一致），v1 的 27 项自检证据可直接复用；同时在 v2 内**复跑一次**：`selfcheck/selfcheck-result.txt` **27 PASS / 0 FAIL，退出码 0**，会话日志 `logs/sessions/20261001-191033-925-selfcheck-head405.jsonl`、`…-936-selfcheck-put409.jsonl`（两会话、reset 前后保留）。
- 边界不变：离线自检只证明服务与夹具准备成立；App/MuMu 行为（401/403 不回退、公网拒绝、匹配导入、JSON 恢复）仍需设备端日志与 UI 证据。

## 5. 工件 SHA（`SHA256SUMS.txt`）

```
680408000a035b78a450144059880c2f5d5017e21d01975d3c16f102bfdd86ea  service/controlled_webdav_w1.py（与 v1 同）
97a09dac3b0a902e1da53507623e6fd1e0861e7b9fbe0fd08fd8a606d6d34d4a  selfcheck/run_selfcheck.py（与 v1 同）
3ce71b01ab0a09f85df7d3bc6520f7425bc88ee521ac87c4cfae258e5aba35c8  README-operations.md（本轮更正）
fbec08a3836298feb43d2c6a9897ab38274f7ed75277b7b12d80a950ee3c4492  verification/verify_expectations.py（本轮新增）
aec6d4ff8f6114b8b82d858be193cdc527817c80b34a1372f83c879d376aaecf  fixtures/backup-20261001-测试 空格.zip（与 v1 同）
4bcbeabf3594c834082ee38e58b6211612c74046ec2f58d0c8c702d0de477770  fixtures/cra-backup-20261001-测试 空格.json（与 v1 同）
0365f3cd6bc9725a7065861acdcecc0f55028fbb6e662423f95863fd3cb4a19b  fixtures/build_fixtures.py（本轮注释更正）
```

## 6. 未覆盖项

- 设备端一切行为仍待设备批次（同 v1 边界）；本轮未改服务逻辑与夹具数据。
- 合并条件/匹配逻辑的核对是**与源码同构的独立复算**，不是对产品代码的运行验证；产品侧实际合并行为由设备/仪器证据证明。

## 7. 交付状态与下一步

- **W1-v2 交付：READY（说明更正 + 定向核对 23/23 + 自检 27/27）**；App/MuMu = NOT RUN。
- 下一步：Codex 核查四处更正的落盘；随后设备窗口按 v2 README 的分阶段记录（保存/刷新 PROPFIND ≠ 连接测试）执行 WebDAV UI 与导入验收，夹具预期以 `verification/utc-and-merge-check.txt` 为准。
