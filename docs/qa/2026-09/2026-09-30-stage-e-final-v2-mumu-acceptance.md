# 阶段 E：最终 V2 联合候选 MuMu 设备独立限定验收报告（2026-09-30）

- **验收角色**：阶段 E 独立验收者（独占 MuMu 设备，产品源码只读）
- **基线仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（分支 `codex/recover-worktree-20260920`，HEAD `513891d6a66a9b78e072ba759d34ed580d377ecf`，暂存区空）
- **设备性质说明**：**本验收完全限定于已授权 MuMu 模拟器（`127.0.0.1:16416`，SM-S9280，Android 12 / API 32 / x86_64），绝非物理真机实测，结论不外推实体手机**
- **唯一安装输入**：`E:\creation-reading-assistant\stage-e-final-v2-20260930\READY.json`
- **独占证据目录**：`E:\creation-reading-assistant\stage-e-final-v2-acceptance-20260930\`
- **唯一可写共享报告**：本报告（`docs/qa/2026-09/2026-09-30-stage-e-final-v2-mumu-acceptance.md`）
- **操作纪律**：不修产品源码；不触碰正式书库、实体手机、原仓库；不执行 stage/commit/push/reset/checkout；不删除或迁移目录；READY 缺失期间记录 WAITING，不安装旧候选、不称验收完成。

---

## 一、测试前设备基线与原装数据冷备存证

### 1.1 设备运行环境基线

| 参数项 | 设备现场实测值 | 校验与恢复要求 |
|---|---|---|
| **设备 Serial** | `127.0.0.1:16416` | MuMu 模拟器实例 1 独占端口（`player_state=start_finished`） |
| **设备机型 / 系统** | `SM-S9280` / Android 12 (API 32, x86_64) | 模拟器默认配置 |
| **屏幕物理密度** | `Physical density: 280` | 初始无 override；验收时临时设为 420 dpi，验收后恢复 280 |
| **系统夜间模式** | `Night mode: yes` | `cmd uimode night` 原值为 `yes`（深色），验收后必须恢复为 `yes` |
| **插电保持常亮** | `0` | `settings get global stay_on_while_plugged_in` |
| **已安装应用包** | `com.creationreadingassistant.qahost`<br>`com.creationreadingassistant.qahost.test` | **正式包 `com.creationreadingassistant` 未安装、未触碰** |

### 1.2 原装已安装 APK 哈希（比对基准）

设备现场提取 `pm path` 并执行 `sha256sum`：

| 包名 | 原始路径 | SHA-256 校验和 |
|---|---|---|
| 宿主包 `com.creationreadingassistant.qahost` | `/data/app/.../base.apk` | `E82904CC9C1315466ED5C521693F93F541560FEEF038BF074CE7F7CF8324EF8A` |
| 测试包 `com.creationreadingassistant.qahost.test` | `/data/app/.../base.apk` | `FDBD019011088505FA37D71565259A0D76F6927407AF69148379E2EF7C27038D` |

> 该两枚 APK 与历史基准完全一致，已保全至独占证据目录 `backup/`。

### 1.3 持久状态清单与整包冷备

- **持久文件清单**：设备端使用 root 权限扫描 `./databases ./files ./shared_prefs` 共 **53 个持久文件**，SHA-256 清单落盘至 `backup/before-sha256-manifest.txt`。
  - 主数据库 `creation_reading_assistant_native`：`7bd076c60763d9d9a65fa3df990bcd67f2911686cdbcbf4310d37f0588499a28`
  - `-shm`：`fd4c9fda9cd3f9ae7c962b0ddf37232294d55580e1aa165aa06129b8549389eb`
  - `-wal`：`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`（0 字节）
  - 书籍与相关配置：50 个文件在列。
- **整包冷备归档**：
  - 路径：`backup/qahost_backup_before.tar`
  - 大小：1,181,988,864 字节
  - SHA-256：`CAE16E820BCAFB1E269FAD7682F44C832581BFB986E5CCE6FA81084B388026F0`
- **自动化对账自检**：执行 `scripts/check-device.ps1`，实测输出 `SUCCESS: 53/53 persistent files match baseline perfectly (Diff=0)!`，基线完全锁定。

---

## 二、候选输入就绪状态与等待记录（WAITING）

截至当前记录时刻（2026-09-30 19:02+），目标候选状态检测如下：

| 候选单元 | 目标 READY 路径 | 检测状态 | 观察与说明 |
|---|---|---|---|
| **最终 V2 联合候选** | `E:\creation-reading-assistant\stage-e-final-v2-20260930\READY.json` | **WAITING (不存在)** | 窗口仍在进行最终联合树（包含验证修正、S05 闭包、Reader 实际入口接线与全量门禁）的构建与生成 |

> **执行纪律记录**：严格遵循“READY 未出现时不安装旧候选、不称验收完成”的纪律，当前设备未被覆盖安装任何旧产物，保持环境绝对干净，处于只读就绪态轮询等待中。

---

## 三、待执行 6 大限定验收规程（READY 到达后串行执行）

本轮验收仅使用同一份最终冻结 APK，在 **420 dpi 手机宽度竖屏**下串行验证：

1. **G6 时长单位切换与卡片手势（8 条仪器测试）**：
   - 验证透明占位退出语义树、唯一长按手势、整卡点击打开档案；
   - 受控验证 ≥24h 在 HOURS 与 DAYS 之间的同源切换及进程重启持久化；
   - TalkBack 语义断言与实际播报分别记账，服务不可用时如实记为未验。
2. **S05 完本契约一致性验证**：
   - 验证取消读完后的书架/首页/档案/统计一致性；
   - 验证高百分比+reading 状态不被误标为完成（使用受控测试书籍）。
3. **S07 阅读器核心选区与高亮**：
   - EPUB 源范围与持久高亮（`EpubBlockPersistentHighlightTest` 等）；
   - TXT 滚动选区失效与选区归属；
   - 普通笔记及 TOC typed target 跳转。
4. **S12 Legado 进度导入契约**：
   - 真实导入入口可达性；
   - 99.4% 边界处理、未移动退出保留 marker、实际移动后原生 locator 保存；
   - 记录逐阶段 DB / 日志证据。
5. **S14/R3 备份恢复安全与回滚回归（8 条回归）**：
   - 执行 `LocalZipBackupPersistentRestoreTest`（6 条）与 `LocalExportRoomRollbackTest`（2 条）；
   - 验证备份类别、来源引用/规则/纠错、版本保护与事务回滚。
6. **新增联合接缝冒烟**：
   - S06 启动主题无闪烁冷启动冒烟；
   - 来源刷新/索引冒烟。
