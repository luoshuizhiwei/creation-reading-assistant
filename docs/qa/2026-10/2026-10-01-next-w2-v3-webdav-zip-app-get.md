# W2-accept-v3: WebDAV 严格服务下 ZIP 精确路径 App GET 限定验收报告

> **任务编号/版本**：W2-accept-v3  
> **验收目标**：定向补齐严格 WebDAV 服务上的 ZIP 精确路径 App GET 证据，并完成本次环境恢复与后账对账；不重跑已通过的六类 WebDAV、JSON GET、Room 或全量测试。  
> **报告路径**：`docs/qa/2026-10/2026-10-01-next-w2-v3-webdav-zip-app-get.md`  
> **执行日期**：2026-10-01 / 2026-10-02  
> **执行者**：本轮唯一 MuMu 设备操作者（Antigravity / Gemini 2.5 Pro）  
> **设备限定**：MuMu 模拟器（serial `127.0.0.1:16416`，显式指定；非实体手机实测）  

---

## 一、任务编号/版本

- **任务编号**：`W2-accept-v3`
- **版本**：`v3`
- **响应依据**：[`docs/coordination/reviews/2026-10/2026-10-01-next-w2v2-d1-codex-review.md`](file:///D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/docs/coordination/reviews/2026-10/2026-10-01-next-w2v2-d1-codex-review.md) §W2 指出的唯一剩余缺口——Case 5 中 ZIP 精确路径此前仅由电脑 Python 探针获取，缺乏 App 原生端（okhttp UA）发出的真实 GET 请求证据。
- **任务范围**：严格限定于复用冻结 APK，在独立目录部署受控 WebDAV 服务与夹具，通过 App 真实 UI 路径触发“从开源阅读导入记录”，捕获并核验 App 发出的真实 ZIP GET 请求与 200 响应，完成严格 404 对照实测，并完成设备环境完全恢复。

---

## 二、输入指纹

1. **活动仓库基线**：
   - HEAD Commit：`e39a78da9c62f02614207cffb36969a32492eaea`（暂存区空，产品源码未改动）
2. **复用冻结 APK**：
   - 路径：`E:\creation-reading-assistant\next-round-w2-webdav-ui-build\freeze\apk\app-qahost.apk`
   - 文件大小：`51,306,172` 字节
   - SHA-256：`0221d88fb34e8c88e274f09b939c5f1b84f20d9c2674abb7c8588e6593c71594`
   - 包名：`com.creationreadingassistant.qahostwebdav`
   - 实装 UID：`10087`（与原宿主应用 UID `10083` 严格沙箱隔离）
3. **受控 ZIP 夹具**：
   - 文件名：`backup-20261001-测试 空格.zip`
   - 路径：`E:\creation-reading-assistant\next-round-w2-webdav-ui-acceptance-v3\fixtures\backup-20261001-测试 空格.zip`
   - 文件大小：`266` 字节
   - SHA-256：`aec6d4ff8f6114b8b82d858be193cdc527817c80b34a1372f83c879d376aaecf`
4. **前序审查与基线输入**：
   - Codex 总指挥审查结论：[`docs/coordination/reviews/2026-10/2026-10-01-next-w2v2-d1-codex-review.md`](file:///D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/docs/coordination/reviews/2026-10/2026-10-01-next-w2v2-d1-codex-review.md)
   - W2-v2 验收报告：[`docs/qa/2026-10/2026-10-01-next-w2-v2-webdav-ui-mumu-acceptance.md`](file:///D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/docs/qa/2026-10/2026-10-01-next-w2-v2-webdav-ui-mumu-acceptance.md)

---

## 三、所有权与改动

- **所有权独占目录**：`E:\creation-reading-assistant\next-round-w2-webdav-ui-acceptance-v3\`
  - 存放自有 WebDAV 服务副本、夹具副本、自动化驱动脚本、会话日志、线路 raw dump、UI 截图与前后账数据。
- **自存任务凭证**：`E:\creation-reading-assistant\next-round-w2-webdav-ui-acceptance-v3\execution.json`（记录任务编号、版本、执行标识、模型应用、cwd/HEAD、时间与最终 COMPLETED 状态，无凭据泄露）。
- **仓库文件所有权**：本报告为唯一可写共享文档，未修改任何其他历史报告、测试工件、公共账本、产品源码或 HOLD 配置。
- **排他性执行**：本任务为当前唯一操作 MuMu 模拟器的执行者，其他并发任务未操作设备。

---

## 四、环境

- **操作系统**：Windows 11
- **执行环境**：Python 3.13
- **验收设备**：MuMu 模拟器（`127.0.0.1:16416`，全部 ADB 命令显式指定 `-s 127.0.0.1:16416`）
- **设备配置**：1080×1920，density 280，夜间模式开启（night=yes），accelerometer_rotation=1，user_rotation=0，stay_on=0
- **本地服务**：`controlled_webdav_w3.py` 监听回环地址 `127.0.0.1:18082`（仅绑定 loopback，不对公网开放）
- **端口转发**：`adb -s 127.0.0.1:16416 reverse tcp:18082 tcp:18082`（自建且事后已精准撤销）

---

## 五、执行与结果

### 1. 真实 UI 操作路径
1. 启动临时包 `com.creationreadingassistant.qahostwebdav`，进入主界面；
2. 导航至“我的” -> “WebDAV 设置”页面；
3. 输入回环服务器配置 `http://127.0.0.1:18082/dav/`，凭据 `testuser / testpass`，点击“保存 WebDAV 设置”；
4. 页面向上滑动至“从开源阅读导入记录”卡片区域；
5. 点击**「扫描开源阅读备份」**按钮（触发 App 发起 `PROPFIND /dav/ Depth:1`）；
6. 服务端返回包含受控夹具的 WebDAV XML 响应；App 成功解析并在列表中展示 `backup-20261001-测试 空格.zip`；
7. 定位并点击该条目右侧对应的**「导入记录」**按钮；
8. 触发 App 调用 `ProfileWebDavManager.importLegadoBackup` -> `WebDavBackup.getBytes`，向服务端发出真实下载请求；
9. 界面弹出导入确认对话框，点击“确认”，App 正常完成读取与进度比对，UI 保持稳定。

### 2. App 真实 ZIP GET 核心证据核验

在全新追加式会话 `20261001-235856-523-w2_v3_zip_app_get` 中完整捕获：

| 校验项 | 规范与预期 | 实际回读证据 | 判定 |
| :--- | :--- | :--- | :---: |
| **请求方法** | `GET` | `GET` (Seq=3) | **PASS** |
| **实际请求路径** | `/dav/backup-20261001-%E6%B5%8B%E8%AF%95%20%E7%A9%BA%E6%A0%BC.zip` | `/dav/backup-20261001-%E6%B5%8B%E8%AF%95%20%E7%A9%BA%E6%A0%BC.zip` | **PASS** |
| **URL 解码路径** | `/dav/backup-20261001-测试 空格.zip` | `/dav/backup-20261001-测试 空格.zip` | **PASS** |
| **双重编码检测** | 不含 `%25`，一次标准 UTF-8 URL 编码 | `has_double_encoding = False` | **PASS** |
| **HTTP 响应码** | `200 OK` | `response_code = 200` | **PASS** |
| **客户端身份** | 必须为 App 真实客户端（包含 okhttp） | `User-Agent: okhttp/4.12.0`（非 Python/curl 探针） | **PASS** |
| **服务端发送字节** | 266 字节 | `sent_body_len = 266` 字节 | **PASS** |
| **服务发送 SHA-256** | `aec6d4ff8f6114b8b82d858be193cdc527817c80b34a1372f83c879d376aaecf` | `sent_body_sha256 = aec6d4ff8f6114b8b82d858be193cdc527817c80b34a1372f83c879d376aaecf` | **PASS** |

### 3. 错误文件名严格 404 对照实测
- **请求路径**：`GET /dav/backup-20261001-%E6%B5%8B%E8%AF%95%20%E9%94%99%E8%AF%AF.zip` (Seq=4)
- **实测响应**：`HTTP/1.1 404 Not Found`（记录于 `0004-GET.http`）
- **判定**：服务端保持严格文件名解码匹配，不存在前缀模糊或通配放宽。

---

## 六、原始证据

所有证据工件均持久保存在 `E:\creation-reading-assistant\next-round-w2-webdav-ui-acceptance-v3\`：

1. **会话日志（追加式 JSONL，未截断）**：
   - 路径：`logs/sessions/20261001-235856-523-w2_v3_zip_app_get.jsonl`
   - 包含 Seq 1~2 PROPFIND、Seq 3 App 真实 ZIP GET 及 Seq 4 错误路径 404 探测。
2. **线路原始报文 Dump（Raw HTTP Dump）**：
   - `logs/raw/20261001-235856-523-w2_v3_zip_app_get/0003-GET.http`：状态行 `HTTP/1.1 200 OK`、响应头与 266 字节二进制 Body，SHA-256 确核为 `aec6d4ff8f6114b8b82d858be193cdc527817c80b34a1372f83c879d376aaecf`；
   - `logs/raw/20261001-235856-523-w2_v3_zip_app_get/0004-GET.http`：状态行 `HTTP/1.1 404 Not Found`。
3. **UI 截图与界面 XML Dump**：
   - `evidence/01_webdav_config_input.png` / `.xml`：WebDAV 输入页面；
   - `evidence/02_after_save_config.png` / `.xml`：保存设置后状态；
   - `evidence/03_scrolled_to_legado.png` / `.xml`：滑动至开源阅读导入区域；
   - `evidence/04_after_scan_legado.png` / `.xml`：扫描后成功展示 `backup-20261001-测试 空格.zip`；
   - `evidence/05_after_click_import.png` / `.xml`：点击“导入记录”；
   - `evidence/06_after_confirm_dialog.png` / `.xml`：二次确认弹窗；
   - `evidence/07_final_import_result.png` / `.xml`：最终导入完成界面。
4. **结构化比对凭证**：
   - `evidence/w3_zip_app_get_evidence.json`：汇总记录了请求路径、UA、哈希与 404 状态。

---

## 七、未覆盖项

1. **实体手机与特定厂商 ROM（如 MIUI/HyperOS）实测**：本轮依据用户授权在 MuMu 模拟器上执行限定验证，结论不直接外推到物理真机；
2. **公网商用 WebDAV 生产服务端到端联调**：本次使用遵循 WebDAV 标准的本地受控服务进行精准协议与路径编码验证，未访问真实坚果云/Nextcloud 公网账号；
3. **已通过的六类 WebDAV 场景**：Case 1（PROPFIND Depth 0 探针）、Case 2（备份 PUT 201）、Case 3（401/403 认证拒绝）、Case 4（非法 URL 客户端拦截）、Case 6（目录浏览）与 Case 7（恢复快照 integrity 与空库恢复对账）均直接复用 Codex 审查已通过的既有证据，本轮未重跑；
4. **Legado 内部阅读进度落库完整性**：受控 ZIP 夹具内的 `bookshelf.json` 进度匹配合同此前已在隔离单元测试和旧报告中通过，本轮不重测业务进度修改。

---

## 八、恢复与副作用

测试完成后立即执行了严格的环境清理与前后账对账，原始日志与执行明细保存在 `post_accounting/post_accounting.json` 与 `post_accounting/post_commands.json`：

1. **原宿主应用持久文件逐文件 SHA 对账**：
   - 检查范围：`/data/data/com.creationreadingassistant.qahost/` 全部持久文件；
   - 事前基线数量：`71` 项；
   - 事后后账数量：`71` 项；
   - 对账结果：**新增 0、缺失 0、修改 0（100% 逐文件一致）**。
2. **原宿主与测试 APK 对账**：
   - 原宿主 APK SHA：`e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a`（UID 10083，完全匹配）；
   - 原测试 APK SHA：`fdbd019011088505fa37d71565259a0d76f6927407af69148379e2ef7c27038d`（UID 10084，完全匹配）。
3. **系统设置无污染**：
   - 七项系统设置（wm size 1080x1920、density 280、font_scale null、accel 1、user_rot 0、stay_on 0、night yes）在事前与事后对比中**差异项为 0**。
4. **网络端口与转发规则清理**：
   - 自建的 `tcp:18082` reverse 规则已通过 `adb -s 127.0.0.1:16416 reverse --remove tcp:18082` 精确撤销；
   - 事后 `adb reverse --list` 与 `adb forward --list` 均恢复为空，未误触他人转发规则。
5. **临时测试包与驻留进程清理**：
   - 临时包 `com.creationreadingassistant.qahostwebdav` 已执行 `pm uninstall` 彻底移除（`pm list` 确核无残留）；
   - 设备端临时 dump 文件（`/data/local/tmp/dump_*.xml`）已清空；
   - 本地 Python WebDAV 服务（PID 40312）已完全停止，`18082` 端口已彻底释放；设备端无任何相关驻留进程。

---

## 九、交付状态与下一步

- **交付状态**：**已交付待 Codex 核查**（严格遵守合同，不自行声明关闭 M1）。
- **下一步**：交由总指挥 / Codex 独立核查本轮捕获的 ZIP 精确路径 App GET 原始会话日志、线路 raw dump、UI 截图及事后 71 项持久资产恢复后账。
