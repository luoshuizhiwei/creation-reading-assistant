# S04 qahost 隔离包 · 真机独立验收报告（BLOCKED 于飞行前）

- **日期**：2026-09-27
- **验收依据**：`docs/qa/2026-09/2026-09-27-s04-integration-gate-and-device-handoff.md` 修订版（v2）§4 验收单
- **授权来源**：用户本轮明确指令「按修订版 §4 独立验收 S04 的 qahost 隔离包」（对话授权，2026-09-27）
- **验收者**：独立验收会话（非构建/报告同会话的延续隔离包构建，但同环境）
- **结论先行**：**整场验收 BLOCKED 于飞行前核实**——四项前置检查中「真机设备」一项不符：当前唯一在线 adb 端点是
  `127.0.0.1:16416`（本机 localhost，x86_64 ABI 的模拟器实例），**没有任何已连接真机**。按飞行前契约
  「任一项不符就停止并报告」、`AGENTS.md`「不使用 MuMu 模拟器 / 真机验证」红线、以及用户本轮「不要把模拟器
  结果写成真机 PASS」的明示要求，**设备阶段立即停止**：未安装任何 APK、未执行任何 run-as/UI/文件操作、
  未产生任何真机或模拟器 PASS/FAIL 结论。

---

## 1. 飞行前核实（4 项）

| # | 检查项 | 结果 | 证据 |
|---|---|---|---|
| 1 | 本轮设备使用授权 | **相符** | 用户本轮对话明示授权真机验收（含「不得对正式包安装/强停/清除/卸载」约束） |
| 2 | 设备 serial（真机） | **不符 → 停止** | `adb devices -l` 仅 `127.0.0.1:16416`，无任何真机 serial（详见 §2） |
| 3 | 两份 E 盘 APK SHA-256 | **相符** | 宿主 `a4669f8532055656be6341444f29eaf3a972678533e5ceb351ff34e40021a638`、测试 `6d19c24449d75f893c20fef947f165fd005f08733187ad1f3e5c5662c1456ec7`——与 handoff v2 §2.3 记录逐字一致 |
| 4 | 宿主/测试包名（aapt 实测） | **相符** | 宿主 `package: name='com.creationreadingassistant.qahost'`（versionCode 3 / 0.5.1）；测试 APK instrumentation `CraIsolatedTestRunner`、**targetPackage `com.creationreadingassistant.qahost`** |

## 2. 设备不符详情（停止依据）

`adb devices -l` 唯一端点：

```
127.0.0.1:16416        device product:e3q model:SM_S9280 device:e3q transport_id:1
```

只读 getprop 识别（仅此 4 条，未做任何其他设备操作）：

```
ro.product.model       = SM-S9280
ro.product.manufacturer= Samsung
ro.product.cpu.abi     = x86_64
ro.build.version.release = 12
```

判定为**模拟器实例，不是真机**：

1. adb 地址为 `127.0.0.1:<port>`——本机 localhost 端点（MuMu 系模拟器的典型形态），真机应为 USB serial（如历史真机 `c49ac6cf`）或局域网 IP；
2. `x86_64` ABI——真机 SM-S9280（Galaxy S25 Ultra）为 arm64-v8a；x86_64 + 自报三星型号是模拟器/云手机属性伪装；
3. Android 12——真机 SM-S9280 出厂为 Android 15；
4. 历史真机 `c49ac6cf`（Redmi 22081212C）**未连接**。

依据：`AGENTS.md`「android/ 的 UI 和设备行为只在已连接的真实 Android 手机上验证，不使用 MuMu 模拟器」；用户本轮「不要把 JVM 通过、包名隔离或模拟器结果写成真机 PASS」。

## 3. 执行记录（全部为只读核实，无任何写入/安装/设备修改）

```bash
adb devices && adb devices -l                     # 设备清点
adb -s 127.0.0.1:16416 shell getprop …            # 仅 4 条只读属性，用于识别端点身份
sha256sum <E 盘两份 APK>                           # 哈希核对
aapt dump badging / dump xmltree <两份 APK>        # 包名核实
```

- **未**安装 qahost 宿主/测试 APK；**未**对任何包执行 install/run-as/force-stop/pm clear/uninstall；
- **未**创建或推送任何测试样本；**未**启动任何应用；**未**触碰正式应用（`com.creationreadingassistant`）的任何状态；
- 模拟器端点除 4 条 getprop 外零操作，未安装任何东西，无需也不会做回滚。

## 4. 验收结果分类

### PASS
无（未执行任何验收场景）。

### FAIL
无（未执行任何验收场景，不存在失败证据）。

### BLOCKED
- **整场真机验收 BLOCKED 于飞行前**：真机设备不可用。§4 验收单的 P1–P4、场景 A–E 全部无法开始。
- 解除条件：用户在**已授权真机**上开启 USB 调试并连接，使 `adb devices` 出现真机 serial；随后从本报告 §1 的飞行前核实起**整体重跑**（哈希与包名已就绪，预期仍相符）。

### 未覆盖（不因 BLOCKED 而声称任何一侧成立或不成立）
1. qahost 隔离包在真机上的安装、启动、沙箱空书架确认（P1–P4）；
2. `run-as com.creationreadingassistant.qahost` 在目标 ROM 的可用性；
3. WAL 三件套一致性快照法的实测（`.db`+`-wal`+`-shm` 拉取与本地回放）；
4. 场景 A 来源刷新成功路径（content_hash/size/进度保留/无残留）；
5. 场景 B 大文件 content_hash 置 NULL；
6. 场景 C 备份残留守卫拒绝（文案合同 + target/backup 逐字节不变 + DB 零写入）；
7. 场景 D 诊断页记录核对（无「回滚恢复未完成」、无敏感内容）；
8. 场景 E 替换中进程中断观察；
9. 正式应用及书库的前后只读对账（`dumpsys package`/`pm path` 基线——因设备阶段未开始，未采集）；
10. 并发替换、低存储/跨卷（本来就不在本验收单强制范围）。

## 5. 现有可引用证据（非真机，不得写成真机结论）

以下为**构建侧既有证据**，与本轮停止决定无冲突，验收重跑时无需重新构建：

- 门禁 A：S04 四类定向 JVM 83/83（XML 哈希在 handoff v2 §2.1）；
- 门禁 C：qahost 宿主/测试 APK 构建与隔离身份核实（handoff v2 §2.3，aapt 实测）；
- 本轮飞行前核实输出已归档：`E:\creation-reading-assistant\codex-s04-accept-20260927\preflight\`
  （`preflight-device.log`、`preflight-apk-hashes.txt`、`preflight-host-badging.txt`、`preflight-testapk-manifest.txt`）。

## 6. 下一步

1. 用户连接真机并授权 USB 调试 → 确认 `adb devices` 出现真机 serial；
2. 以本报告 §1 四项飞行前核实为起点整体重跑 §4 验收单（顺序：正式包只读基线 → P1 安装 → run-as/WAL 法验证 → 场景 A→B→C→D→E → 正式包后置只读对账）；
3. 重跑时沿用本报告的停止规则：run-as 或 WAL 快照法在目标 ROM 上不成立即停在该步骤标 BLOCKED，不猜测数据状态。
