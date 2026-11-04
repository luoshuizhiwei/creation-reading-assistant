# W2 WebDAV UI 验收 v2 — MuMu 模拟器验收报告

> 任务标识：W2-accept-v2
> 验收日期：2026-10-01
> 验收设备：MuMu 模拟器（serial 127.0.0.1:16416）
> 验收包：com.creationreadingassistant.qahostwebdav（冻结 APK，UID=10086）
> 工作目录：E:\creation-reading-assistant\next-round-w2-webdav-ui-acceptance-v2

---

## 1. 任务标识与范围

**任务**：W2-accept-v2 — 针对 W2 WebDAV UI 验收（v1）遗留的 5 个证据缺口进行定向补验，并完成环境恢复与后账。

**补验范围**（5 个缺口，对应 v1 的 Case1/3/4/5/7）：

| 缺口 | v1 问题 | v2 补验目标 |
|------|---------|-------------|
| A (Case1) | HEAD 405 后 PROPFIND Depth:0→207 缺原始响应核对 | 保存 HEAD 无响应体 + PROPFIND Depth:0 原始响应，UA=okhttp |
| B (Case3) | 401/403 时测试连接与立即备份的请求序列缺完整证据 | 401/403 各自 HEAD→认证码 + PUT→认证码，无 MKCOL，无解析异常 |
| C (Case4) | 非法 URL 拒绝缺"零服务端请求"证据 + 合法回环正对照 | userinfo/明文 HTTP 客户端拒绝 + 0 服务端请求 + 合法回环正对照 |
| D (Case5) | App GET 路径/编码/字节 SHA 缺失（v1 用 Python 下载冒充） | App 真实 GET，精确路径，单次编码，服务响应体 SHA=夹具 SHA |
| E (Case7) | 恢复快照"存在且可用"缺 SQLite 完整性与恢复前对账 | 快照 SHA + SQLite integrity ok + 恢复前空库→恢复后 1 book/1 progress |

**不在范围**：Case2（备份 PUT 201）和 Case6（PROPFIND 列目录）v1 证据完整，本轮复用不重跑。

**停止条件**：完成全部补验并恢复环境，或发现真实缺陷/数据差异时保留证据不降低断言。

---

## 2. 设备与 APK 信息

### 设备

| 项 | 值 |
|----|-----|
| 型号 | SM-S9280/e3q (MuMu 模拟器) |
| serial | 127.0.0.1:16416 |
| 验收方式 | MuMu 模拟器验收（按交接入口设备决定） |

### 冻结 APK

| 项 | 值 |
|----|-----|
| 路径 | E:/creation-reading-assistant/next-round-w2-webdav-ui-build/freeze/apk/app-qahost.apk |
| 包名 | com.creationreadingassistant.qahostwebdav |
| SHA256 | 0221d88fb34e8c88e274f09b939c5f1b84f20d9c2674abb7c8588e6593c71594 |
| 大小 | 51,306,172 bytes |
| 安装 UID | 10086（与旧 qahost UID=10083 隔离） |

### 受控 WebDAV 服务

| 项 | 值 |
|----|-----|
| 实现 | Python http.server，回环 18082 端口 |
| 场景切换 | /__control__/scenario/{default,head_405_propfind,auth_401,auth_403,...} |
| adb reverse | tcp:18082 → tcp:18082 |
| UA（App 实际 HTTP 客户端） | okhttp/4.12.0 |

### 基线对比

七项系统设置（wm_size 1080x1920, density 280, font_scale null, accel_rot 1, user_rot 0, stay_on 0, night_mode yes）与旧基线完全一致。旧包（qahost/qahost.test/local.creationReadingAssistant.mobile）在场且未触碰。

---

## 3. 测试工具与改动

### v1 → v2 工具修复

| 修复项 | v1 缺陷 | v2 修复 |
|--------|---------|---------|
| HEAD 无响应体 | 服务端 HEAD 返回了响应体，导致 okhttp 解析异常 | HEAD 严格不返回 body，4 种场景全部验证 body_bytes=0 |
| GET 精确路径 | 路径匹配宽松，错误文件名也能命中 | 精确 URL 解码匹配，错误文件名返回 404 |
| PROPFIND 关系 | href/displayname 与夹具关系不明确 | 明确 href 解码末段=displayname=夹具文件名，保存服务/夹具 SHA |
| 原始响应核对 | 缺原始字节级响应证据 | 新增 raw_probe：HEAD 无 body、keep-alive 无污染、GET 精确+404、PROPFIND 关系 |

### 原始响应探针结果（raw_probe）

- **HEAD 无 body**：default(200)/head_405(405)/auth_401(401)/auth_403(403) 四场景全部 body_bytes=0 ✅
- **keep-alive 无污染**：连续 401 请求，第二次响应前缀干净（`HTTP/1.1 401 Unauthorized\r\nServer: W2-Co`）✅
- **GET 精确匹配**：正确路径→200 + SHA=夹具 SHA；错误路径（空格未编码）→404 ✅
- **PROPFIND 关系**：2 个条目，href 解码末段=displayname=夹具文件名 ✅

### 夹具文件

| 文件名 | SHA256 |
|--------|--------|
| backup-20261001-测试 空格.zip | aec6d4ff8f6114b8b82d858be193cdc527817c80b34a1372f83c879d376aaecf |
| cra-backup-20261001-测试 空格.json | 4bcbeabf3594c834082ee38e58b6211612c74046ec2f58d0c8c702d0de477770 |

---

## 4. 补验结果

### Case1 (A) — 测试连接 HEAD 405 → PROPFIND Depth:0 → 207

**状态：PASS**

请求序列：
1. 保存设置 → PROPFIND /dav/ Depth:1 → 207
2. 测试连接 → HEAD /dav/ → 405（无响应体）
3. 测试连接 → PROPFIND /dav/ Depth:0 → 207

UI 反馈："WebDAV 连接成功（HTTP 207）"
UA=okhttp/4.12.0

### Case3 (B) — 401/403 认证失败

**状态：PASS**

**401 场景**：
- 测试连接：HEAD → 401，UI "WebDAV 服务器可达，但凭据可能不正确（HTTP 401）"
- 立即备份：PUT /dav/cra-backup-latest.json → 401，UI "备份失败：WebDAV 返回 401: Unauthorized"
- 无 MKCOL，无重试，无解析异常

**403 场景**：
- 测试连接：HEAD → 403，无解析异常
- 立即备份：PUT /dav/cra-backup-latest.json → 403，UI "备份失败：WebDAV 返回 403: Forbidden"
- 无 MKCOL，无重试，无解析异常

### Case4 (C) — 非法 URL 客户端拒绝

**状态：PASS**

| URL | 结果 | 服务端请求 |
|-----|------|-----------|
| http://testuser:testpass@127.0.0.1:18082/dav/ | "备份失败：WebDAV 地址不能包含用户信息（userinfo），账号密码请在输入框填写" | 0 |
| http://example.com/dav/ | "备份失败：已拒绝请求：example.com 是公网地址，明文 HTTP 会泄露 WebDAV 账号密码与整库内容，请改用 https://。" | 0 |
| https://example.com/dav/ （对照） | 无安全拒绝（仅网络失败） | 0 |
| http://127.0.0.1:18082/dav/ （正对照） | HEAD 405 → PROPFIND → 207，"WebDAV 连接成功" | >0 |

拒绝均在客户端发生（未发出任何服务端请求），合法回环正对照通过。

### Case5 (D) — App 真实 GET，精确路径/编码/字节 SHA

**状态：PASS**

请求序列：
1. PROPFIND /dav/ → 207（列目录）
2. GET /dav/cra-backup-20261001-%E6%B5%8B%E8%AF%95%20%E7%A9%BA%E6%A0%BC.json → 200

检查：
- App 发出真实 GET ✅
- GET 返回 200 ✅
- 路径精确匹配：`/dav/cra-backup-20261001-%E6%B5%8B%E8%AF%95%20%E7%A9%BA%E6%A0%BC.json` ✅
- 无双重编码 ✅
- 服务响应体 SHA256 = `4bcbeabf...` = 夹具 SHA ✅
- UA = okhttp/4.12.0 ✅
- 哈希来源：服务端响应体（session log 中 sent_body_sha256），非 Python 下载冒充

UI 反馈："已从 WebDAV 恢复备份"

### Case7 (E) — 恢复快照 SHA/SQLite 完整性/恢复前对账

**状态：PASS**

**恢复前快照**（App 自动创建）：
- 路径：/data/data/.../no_backup/db-snapshots/creation_reading_assistant_native.restore.1790866713928
- 大小：344,064 bytes
- SHA256：c164847326620b848e8de26d168b99e31dcd51398dc320aae6d1dfcc89efdd32
- SQLite integrity_check：ok
- user_version：15，28 表齐全
- 行数：books=0, reading_progress=0（新装空库）

**恢复后主 DB**（WAL checkpoint 后）：
- 大小：380,928 bytes
- SHA256：de0997aad6aba3784b55dafcb941d210b104dbf26a1502ff3808270d00d8f5df
- SQLite integrity_check：ok
- user_version：15，28 表齐全
- 行数：books=1, reading_progress=1

**对账**：
- books：0 → 1（新增 w1-txt-a 恢复版）
- reading_progress：0 → 1（新增 42.0% 进度）
- 恢复内容与旧 Case7 证据和夹具完全匹配：
  - id=w1-txt-a, title=测试 TXT 中性甲(恢复版), author=中性作者, size=4096, revision=2
  - progress=42.0%, location={"offset":200,"locator_v2":{"v":2,"ci":1,"co":200}}

---

## 5. 环境后账

### 系统设置（7 项，全部 MATCH 基线）

| 设置 | 实际值 | 基线值 | 结果 |
|------|--------|--------|------|
| wm_size | 1080x1920 | 1080x1920 | MATCH |
| wm_density | 280 | 280 | MATCH |
| font_scale | null | null | MATCH |
| accelerometer_rotation | 1 | 1 | MATCH |
| user_rotation | 0 | 0 | MATCH |
| stay_on_while_plugged_in | 0 | 0 | MATCH |
| night_mode | yes | yes | MATCH |

### reverse/forward

- reverse list：空（与基线一致）
- forward list：空（与基线一致）

### 旧 qahost APK 对（未受影响）

| 包 | UID | SHA256 | 与基线对比 |
|----|-----|--------|-----------|
| com.creationreadingassistant.qahost | 10083 | e82904cc... | MATCH |
| com.creationreadingassistant.qahost.test | — | fdbd0190... | MATCH |

### 清理回读

- qahostwebdav 已卸载 ✅
- 无残留 data/app 目录 ✅
- 设备临时文件已清理 ✅

### 服务停止

- PID 15732 已停止 ✅
- 端口 18082 不再监听 ✅

### 冻结 APK（磁盘上未变）

- SHA256：0221d88f... — MATCH ✅

**结论**：环境完全恢复至测试前基线，旧包与旧 APK 对未受影响。

---

## 6. 证据清单

### 工作目录

```
E:\creation-reading-assistant\next-round-w2-webdav-ui-acceptance-v2\
├── baseline\pre_test_baseline_v2.json
├── evidence\
│   ├── raw_probe\raw_probe_result.json
│   ├── case1_head405_v2\case1_v2_summary.json
│   ├── case3_auth_v2\case3_v2_summary.json
│   ├── case4_security_v2\case4_v2_summary.json
│   ├── case5_encoding_v2\case5_v2_summary.json
│   ├── case7_v2\
│   │   ├── case7_v2_summary.json
│   │   ├── case7_v2_reconciliation.json
│   │   ├── snapshot_restore.db (344,064 bytes)
│   │   └── main_after_restore.db (380,928 bytes)
│   └── post_accounting\post_accounting_v2.json
├── logs\sessions\ (20 个 session log)
├── scripts\ (run_case1_v2.py, run_case3_v2.py, run_case4_v2.py, run_case5_v2b.py, run_case7_v2.py, ui_test_utils.py)
├── service\controlled_webdav_w2.py
└── fixtures\ (backup-20261001-测试 空格.zip, cra-backup-20261001-测试 空格.json)
```

### 关键 SHA 汇总

| 对象 | SHA256 |
|------|--------|
| 冻结 APK | 0221d88fb34e8c88e274f09b939c5f1b84f20d9c2674abb7c8588e6593c71594 |
| 夹具 .zip | aec6d4ff8f6114b8b82d858be193cdc527817c80b34a1372f83c879d376aaecf |
| 夹具 .json | 4bcbeabf3594c834082ee38e58b6211612c74046ec2f58d0c8c702d0de477770 |
| Case5 服务响应体 | 4bcbeabf3594c834082ee38e58b6211612c74046ec2f58d0c8c702d0de477770 |
| Case7 快照（恢复前） | c164847326620b848e8de26d168b99e31dcd51398dc320aae6d1dfcc89efdd32 |
| Case7 主 DB（恢复后） | de0997aad6aba3784b55dafcb941d210b104dbf26a1502ff3808270d00d8f5df |
| 旧 qahost APK | e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a |
| 旧 qahost.test APK | fdbd019011088505fa37d71565259a0d76f6927407af69148379e2ef7c27038d |

---

## 7. 已知限制与复用

### 复用（不重跑）

- **Case2（备份 PUT 201）**：v1 证据完整，本轮复用。
- **Case6（PROPFIND 列目录）**：v1 证据完整，本轮复用。
- **旧 Case7 恢复后书籍/进度字段**：v1 已验证恢复后 books/reading_progress 字段正确，本轮补充的是快照 SHA、SQLite 完整性和恢复前对账，恢复后字段与 v1 一致。

### 已知限制

- **MuMu 模拟器**：按交接入口设备决定，本轮为 MuMu 模拟器验收，非真机验收。
- **local.creationReadingAssistant.mobile**：设备上存在非本任务包，未触碰，不在基线内。
- **Case7 控制书**：v1 在旧 qahost 包上有控制书 w1-epub-b（恢复后不变），本轮在 qahostwebdav 新装空库上运行，恢复前无控制书。恢复后数据与 v1 恢复后字段完全匹配，控制书不变性由 v1 证据覆盖。
- **哈希来源**：Case5 的字节 SHA 来自服务端响应体（session log 中 sent_body_sha256），非 App 下载产物。这证明了 App 收到的字节与夹具一致。

---

## 8. 结论

**5 个证据缺口全部补齐，全部 PASS。**

| 缺口 | 状态 | 关键证据 |
|------|------|---------|
| A (Case1) HEAD 405→PROPFIND Depth:0→207 | PASS | HEAD 无 body，PROPFIND Depth:0→207，UA=okhttp/4.12.0 |
| B (Case3) 401/403 认证失败 | PASS | HEAD→认证码 + PUT→认证码，无 MKCOL，无解析异常 |
| C (Case4) 非法 URL 客户端拒绝 | PASS | userinfo/明文 HTTP 客户端拒绝 + 0 服务端请求 + 合法回环正对照 |
| D (Case5) App 真实 GET | PASS | 精确路径，单次编码，服务响应体 SHA=夹具 SHA，UA=okhttp |
| E (Case7) 恢复快照完整性 | PASS | 快照 SQLite integrity ok，恢复前空库→恢复后 1 book/1 progress 精确匹配 |

**环境已完全恢复至测试前基线。旧包、旧 APK 对、系统设置、reverse/forward 均未受影响。**

---

## 9. 签名与时间

| 项 | 值 |
|----|-----|
| 任务 | W2-accept-v2 |
| 验收日期 | 2026-10-01 |
| 开始时间 | ~21:40 +08:00 |
| 结束时间 | ~22:20 +08:00 |
| 设备 | MuMu 模拟器 127.0.0.1:16416 |
| 验收包 | com.creationreadingassistant.qahostwebdav (UID=10086) |
| 冻结 APK SHA256 | 0221d88fb34e8c88e274f09b939c5f1b84f20d9c2674abb7c8588e6593c71594 |
| 工作目录 | E:\creation-reading-assistant\next-round-w2-webdav-ui-acceptance-v2 |
| 报告路径 | docs/qa/2026-10/2026-10-01-next-w2-v2-webdav-ui-mumu-acceptance.md |
| 总判定 | **PASS** — 5 缺口全补齐，环境全恢复 |
