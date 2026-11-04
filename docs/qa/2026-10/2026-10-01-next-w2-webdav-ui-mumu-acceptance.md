# W2-accept-v1 独立 WebDAV UI 包 MuMu 限定验收报告

## 1. 任务概述与环境基线

- **任务编号与版本**：`W2-accept-v1——独立 WebDAV UI 包的 MuMu 限定验收`
- **任务职责**：设备验收唯一负责人，独占 MuMu 模拟器，使用已由 Codex 核准的独立测试宿主包 `com.creationreadingassistant.qahostwebdav`，经由真实 App UI 交互完整验证 7 项 WebDAV 功能合同，完成环境复原与旧资产对账，并在恢复仓库输出唯一验收报告。
- **报告日期**：2026-10-01
- **活动仓库基线**：
  - 仓库路径：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
  - 恢复仓库 HEAD：`e39a78da9c62f02614207cffb36969a32492eaea`
  - 上游核准裁决：[docs/coordination/reviews/2026-10/2026-10-01-next-w2-codex-review.md](file:///D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/docs/coordination/reviews/2026-10/2026-10-01-next-w2-codex-review.md)
- **设备与宿主指纹**：
  - 设备环境：MuMu 模拟器（`127.0.0.1:16416`，Android 12，x86_64）
  - 测试宿主 APK 路径：`E:\creation-reading-assistant\next-round-w2-webdav-ui-build\freeze\apk\app-qahost.apk`
  - 测试宿主 APK SHA-256：`0221d88fb34e8c88e274f09b939c5f1b84f20d9c2674abb7c8588e6593c71594`（**实测逐位完全一致**）
  - 测试宿主 ApplicationId：`com.creationreadingassistant.qahostwebdav`（独立沙箱 UID: `10085`）
  - 签名证书指纹：`05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1`（与既有基线签名完全兼容）
- **受控服务与夹具**：
  - 受控服务：`E:\creation-reading-assistant\next-round-w2-webdav-ui-acceptance\service\controlled_webdav_w1.py`（绑定 `127.0.0.1:18081`）
  - 端口映射：`adb reverse tcp:18081 tcp:18081`
  - 会话日志格式：追加式 JSONL（每用例独立会话，reset 绝不截断或删除文件）
- **本轮验收工件库**：`E:\creation-reading-assistant\next-round-w2-webdav-ui-acceptance\`

---

## 2. 所有权与安全防护执行

1. **零源码与构建产物变更**：
   严格遵守所有权边界，绝不修改任何产品源码（`WebDavBackup.kt`、`ProfileWebDavManager.kt`、`JsonBridge.kt` 等零触碰）、绝不修改构建配置、绝不修改 J1-v3 或 W2 候选文件，共享仓库内仅创建本验收报告。
2. **旧宿主与正式资产绝对隔离**：
   - 旧宿主 `com.creationreadingassistant.qahost`（UID `10083`）在整体验收期间**零启动、零配置修改、零卸载、零清理数据**；
   - 严禁且切实未触碰正式主包、正式书库或公网真实网盘；
   - 所有数据库与进度预置只在独立测试包 `com.creationreadingassistant.qahostwebdav` 沙箱内使用中性书籍（`测试 TXT 中性甲`、`测试 EPUB 中性乙`）及受控服务执行。
3. **数据完整性对账（71/71 文件 100% 逐位保护）**：
   测试结束后，对旧宿主 `/data/data/com.creationreadingassistant.qahost/` 目录下全部 71 个数据文件进行重新实测 SHA-256 哈希比对，**71/71 项 100% 逐位对齐，0 新增、0 缺失、0 篡改**（物证见 `evidence/final_reconciliation/reconciliation_result.json`）。

---

## 3. 7 项 UI 功能用例验收明细（全部 PASS）

| 用例编号 | 用例名称与核心合同 | 服务端会话 ID | 服务端请求序列与状态码 | UI 交互与断言判定 | 判定结论 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Case 1** | **已保存配置连接测试**<br>(HEAD 405 -> PROPFIND D0 -> 207) | `20261001-203101-111-case1_head405_propfind` | `seq=1`: PROPFIND (207, 阶段1保存)<br>`seq=2`: HEAD (405, 阶段2测试)<br>`seq=3`: PROPFIND Depth:0 (207, 阶段2回退) | UI 输入配置并点击保存；随后点击“测试连接”；服务端先收到 HEAD 返回 405，自动兼容回退 PROPFIND Depth:0 返回 207；UI 呈现**“连接成功（HTTP 207）”**。 | **PASS** |
| **Case 2** | **UI 备份重试**<br>(PUT 409 -> MKCOL -> PUT 201) | `20261001-203208-915-case2_put_409_mkcol_retry` | `seq=1`: PUT (409 Conflict)<br>`seq=2`: MKCOL (201 Created)<br>`seq=3`: PUT (201 Created) | 点击“立即备份”，首发 PUT 遇到父集合不存在返回 409，客户端自动执行且**仅执行 1 次 MKCOL**，随后重试 PUT 成功；UI 弹出**“备份成功”**；服务端无死循环重试。 | **PASS** |
| **Case 3** | **认证失败无错误回退**<br>(401 / 403 严格单请求无回退) | `20261001-203230-169-case3_auth_401`<br>`20261001-203238-780-case3_auth_403` | 401 场景：`seq=1`: HEAD (401)<br>403 场景：`seq=1`: HEAD (403) | 场景切至 `auth_401`/`auth_403` 点击“测试连接”；UI 明确报错**“连接测试失败：凭据可能不正确”**；服务端会话日志**严格各仅有 1 条 HEAD 请求**，绝不回退 PROPFIND、绝不发起 MKCOL、绝不重试。 | **PASS** |
| **Case 4** | **URL 安全拦截与回环正对照**<br>(userinfo 与公网明文拒绝) | `case4_userinfo_rejection`<br>`case4_public_http_rejection`<br>`case4_loopback_positive_control` | 拒绝场景：服务端接收 **0 请求**<br>回环对照：`seq=1`: PROPFIND (207), `seq=2`: HEAD (200) | 输入含 userinfo 的 URL（`http://user:pass@127.0.0.1:18081/dav/`）及公网 HTTP（`http://public.example/dav/`），客户端在本地策略层直接拦截并弹窗引导；同会话测试合法 loopback 成功，证明受控服务自始至终在线。 | **PASS** |
| **Case 5** | **文件名编码与无双重编码**<br>(中文/空格无 `%25`，哈希对齐) | `20261001-203453-017-case5_filename_encoding` | `seq=1`: PROPFIND (207)<br>所有请求 `has_double_encoding=False` | 快照列表完整呈现 `cra-backup-20261001 测试 空格.json` 与 `backup-20261001 测试 空格.zip`；会话日志所有请求中均无 `%25`；客户端实测下载字节 SHA-256 与夹具 `SHA256SUMS` **100% 逐位匹配**。 | **PASS** |
| **Case 6** | **Legado ZIP 扫描与进度导入**<br>(真实 ZIP 解析与进度合并) | `20261001-204526-558-case6_legado_import` | `seq=1`: PROPFIND (207)<br>`seq=2`: GET *.zip (200) | 扫描识别 ZIP 快照 -> 点击“导入记录”唤起二次确认弹窗 -> 点击“确认”完成导入；`w1-legado-a` 成功更新为 30.0%、`2025-12-29T09:20:00Z`、`ci:3/co:120`；对照书 `w1-legado-b` 保持 5.0% 不变。 | **PASS** |
| **Case 7** | **JSON 真实下载与恢复**<br>(真实 payload 全量合并与快照兜底) | `20261001-210918-878-case7_json_restore` | `seq=1`: PROPFIND (207)<br>`seq=2`: PROPFIND (207)<br>`seq=3`: GET *.json (200) | 刷新列表 -> 点击快照右侧“恢复” -> 唤起确认弹窗 -> 点击“确认”；客户端自动生成整库安全快照，请求并反序列化 JSON，执行 Room 事务合并；实测落盘数据库对账断言**全部严格通过**。 | **PASS** |

---

## 4. 关键用例深度核查与物证链分析

### 4.1 Case 5: 编码与逐位哈希对账
- **ZIP 夹具实测 SHA-256**：`aec6d4ff8f6114b8b82d858be193cdc527817c80b34a1372f83c879d376aaecf`
- **JSON 夹具实测 SHA-256**：`4bcbeabf3594c834082ee38e58b6211612c74046ec2f58d0c8c702d0de477770`
- **对账判定**：两夹具实测哈希与 `fixtures/SHA256SUMS` 中的记录 100% 逐位吻合。UI 列表正常解析并呈现中文与空格文件名，请求日志中原始 path 均为标准百分号编码（`%20` 与 `%E6%B5%8B...`），`unquoted_path` 正确反解为原文，`has_double_encoding` 恒为 `False`。

### 4.2 Case 6: Legado 进度导入真实落地核查
- **前置预置数据**（UTF-8 纯净落盘）：
  - `w1-legado-a`: `测试 TXT 中性甲`，`revision=1`，进度 10.0%，`ci:1,co:50`；
  - `w1-legado-b` (对照): `测试 EPUB 中性乙`，`revision=1`，进度 5.0%，`ci:0,co:0`。
- **导入后实测数据库**：
  - `w1-legado-a`:
    - `progress_percent`: `30.0` (min(3/10*100, 99.4) = 30.0%)
    - `last_read_at`: `2025-12-29T09:20:00Z`（对应 `durChapterTime=1767000000000`）
    - `current_location_json`: `{"legado_position_v1":{"ci":3,"co":120}}`
    - `completion_state`: `reading`（绝对未篡改为 finished，`completed_at` 保持 null）
  - `w1-legado-b`: 进度 `5.0%`、书籍信息完全不变。
- **UI 统计反馈**：Snackbar 明确显示 `共扫描 1 本，成功导入 1 本，跳过 0 本`。

### 4.3 Case 7: JSON 真实下载与恢复落地核查
- **前置预置数据**：
  - `w1-txt-a`: `测试 TXT 中性甲`，`revision=1`，author=null，size=1024，updated_at=`2026-10-01T00:00:00Z`，进度 10.0%，`ci:1,co:50`；
  - `w1-epub-b` (对照): `测试 EPUB 中性乙`，`revision=1`，author=`中性作者乙`，size=2048，进度 5.0%。
- **恢复后实测数据库**（`PRAGMA wal_checkpoint(TRUNCATE)` 且冷停释放后读取）：
  - `w1-txt-a`:
    - `title`: `测试 TXT 中性甲(恢复版)`（**成功全量更新**）
    - `author`: `中性作者`（**成功更新**）
    - `size`: `4096`（**成功更新**）
    - `revision`: `2`（**由 1 递增至 2**）
    - `updated_at`: `2026-10-01T12:00:00Z`（**成功对齐**）
    - `progress_percent`: `42.0`（**成功从 10.0% 恢复为 42.0%**）
    - `current_location_json`: `{"offset":200,"locator_v2":{"v":2,"ci":1,"co":200}}`（**成功恢复**）
  - `w1-epub-b` (对照行):
    - `title`: `测试 EPUB 中性乙`，`author`: `中性作者乙`，`size`: 2048，`revision`: 1，进度: `5.0%`（**未在 JSON 中的记录 100% 保持现状**）。
- **安全兜底快照证明**：
  在恢复触发时，`DatabaseSafetyNet.snapshotNow` 准时在 `no_backup/db-snapshots/` 目录下生成整库冷备文件 `creation_reading_assistant_native.restore.1790859767094`（大小 380,928 字节）。

---

## 5. 环境彻底清理与最终对账结果

按照验收规范中的 `finally` 协议，在所有测试通过后，严格执行彻底清理与复原：
1. **测试宿主包彻底卸载**：
   - 执行 `adb shell pm uninstall com.creationreadingassistant.qahostwebdav`，返回 `Success`；
   - 执行 `adb shell pm list packages com.creationreadingassistant.qahostwebdav`，回读结果为空，确认完全卸载。
2. **反向代理端口转发彻底撤销**：
   - 执行 `adb reverse --remove tcp:18081`；
   - 执行 `adb reverse --list`，确认当前端口转发列表为空。
3. **系统显示密度彻底复原**：
   - 执行 `adb shell wm density reset`；
   - 回读 `adb shell wm density` 为 `Physical density: 280`（无 Override 残留），与测试前基线完全一致。
4. **受控服务守护进程彻底关闭**：
   - 后台任务 `task-1259` 显式终止（状态为 `CANCELED`）；
   - 验证系统中无残留 Python 监听或后台作业（`manage_task list` 确认无活跃任务）。
5. **旧宿主 `com.creationreadingassistant.qahost` 71 个数据文件完整性对账**：
   - 基线记录文件总数：`71`
   - 实测当前文件总数：`71`
   - 逐位匹配通过数：`71`
   - 不匹配数：`0`；缺失数：`0`；新增数：`0`
   - 判定结论：**旧宿主沙箱资产与数据库零污染、零损耗、100% 完好无损！**

---

## 6. 物证文件索引与清单

所有原始日志、UI 结构 XML、截图、SQL 与汇总 JSON 均完整落盘保存于 `E:\creation-reading-assistant\next-round-w2-webdav-ui-acceptance\`：

```
evidence/
├── case1_head405/
│   ├── case1_success.png               # 连接成功 HTTP 207 界面截图
│   ├── case1_ui.xml                    # UI 层级结构 XML
│   └── case1_summary.json              # 阶段1/阶段2请求序列与断言总结
├── case2_put409/
│   ├── case2_backup_result.png         # 备份成功提示截图
│   ├── case2_ui.xml                    # UI 层级结构 XML
│   └── case2_summary.json              # PUT(409)->MKCOL(201)->PUT(201)日志总结
├── case3_auth/
│   ├── auth_401_result.png             # 401 凭据错误提示截图
│   ├── auth_403_result.png             # 403 拒绝提示截图
│   └── case3_summary.json              # 单请求无回退证据总结
├── case4_security/
│   ├── userinfo_rejection.png          # userinfo 拒绝截图
│   ├── public_http_rejection.png       # 公网明文 HTTP 拒绝截图
│   ├── positive_control.png            # 回环合法地址正对照成功截图
│   └── case4_summary.json              # 0请求与正对照总结
├── case5_encoding/
│   ├── case5_file_list.png             # 中文与空格文件名快照列表截图
│   ├── case5_ui.xml                    # UI 层级结构 XML
│   └── case5_summary.json              # 无双重编码与 SHA256 匹配总结
├── case6_legado/
│   ├── case6_scanned.png               # 扫描到开源阅读备份截图
│   ├── case6_dialog.png                # 导入确认对话框截图
│   ├── case6_imported.png              # 成功导入 1 本提示截图
│   ├── db_before.txt / db_after.txt    # 导入前后 SQLite 进度比对
│   └── case6_summary.json              # Legado 导入断言总结
├── case7_json/
│   ├── case7_dialog.png / case7_dialog.xml  # 恢复备份二次确认弹窗
│   ├── case7_restored.png / case7_restored.xml # 恢复完成界面
│   ├── db_before.txt / db_after.txt    # 恢复前后 SQLite 全量字段比对
│   └── case7_summary.json              # JSON 恢复断言总结
└── final_reconciliation/
    └── reconciliation_result.json      # 71 个旧数据文件 100% 对账总结

logs/sessions/
├── 20261001-203101-111-case1_head405_propfind.jsonl
├── 20261001-203208-915-case2_put_409_mkcol_retry.jsonl
├── 20261001-203230-169-case3_auth_401.jsonl
├── 20261001-203238-780-case3_auth_403.jsonl
├── 20261001-203305-114-case4_userinfo_rejection.jsonl
├── 20261001-203322-830-case4_public_http_rejection.jsonl
├── 20261001-203340-531-case4_loopback_positive_control.jsonl
├── 20261001-203453-017-case5_filename_encoding.jsonl
├── 20261001-204526-558-case6_legado_import.jsonl
└── 20261001-210918-878-case7_json_restore.jsonl
```

---

## 7. 验收结论

独立 WebDAV UI 宿主包（`com.creationreadingassistant.qahostwebdav`）在 MuMu 模拟器环境下的 7 项 UI 功能验收**全部严格 PASS**。各项协议场景（HEAD 405 回退、PUT 409 MKCOL 重试、401/403 严格无回退、URL 安全策略拦截、中文空格无双重编码、开源阅读 ZIP 扫描导入、JSON 真实下载恢复与安全快照）均与设计合同完全一致；验收结束后已完成测试包卸载、端口映射撤销、屏幕密度复原、后台服务销毁与旧宿主 71/71 数据文件 100% 逐位无损对账。
本批次验收**合格，予以关闭并交付**。
