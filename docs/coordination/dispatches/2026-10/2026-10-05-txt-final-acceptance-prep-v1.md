# TXT 最终验收工件准备：独立验收准备报告

## 任务编号/版本

CRA-TXT-FINAL-ACCEPTANCE-PREP-v1；2026-10-05。角色：独立验收工件准备负责人（不承担 TXT 生产修复）。
交付状态：**READY_FOR_BINDING / 离线验证通过**。不产生任何 J1 设备 PASS 结论。

## 输入指纹

- 恢复仓库 HEAD `e39a78da9c62f02614207cffb36969a32492eaea`；本任务只读，未改生产源码/构建/current/TODO。
- 只读参考（实算 SHA256）：
  - `UnifiedMumuAcceptV1Test.kt` 6422aed5…（旧 umu_12 假通过来源）
  - `AcceptancePredicates.kt` 97d4e066…（补强谓词）
  - `scripts/ui.py` 4e7c6227…（复用其 dump/tap/start/force-stop 原语）
  - `patched-driver/joint_driver.py` 013ca2f9…、`runner.py` a634a344…（冻结 runner，逐字节复用）
- 生产坐标事实依据（实算 SHA256）：
  - `ReaderProgressEffects.kt` a7baa349…（保存 S / 恢复 scrollToItem(unitIndexForOffset)）
  - `ReaderTextIndex.kt` a69c8714…、`ReaderScrollPosition.kt` b9cd9043…（unitIndexForOffset / currentPlainListOffset / chunkPlainText=3000）
  - `ReaderScreenDerivedState.kt` 9129e9fa…（小文件 units = buildChapterAlignedPlainUnits）
  - `PlainTextDocument.kt` 86a36dab…、`LocatorCodec.kt` cf412e59…、`LocatorBuilder.kt` f62fbe9b…

## 所有权与改动

- 唯一写入：`E:\creation-reading-assistant\txt-final-acceptance-prep-v1\`（新目录）与本报告。
- 未修改：任何生产源码、窗口一（TXT-v2）目录、旧候选、旧驱动、旧报告、D 源码、current/TODO、公共构建配置。
- 未运行 Gradle / ADB / 安装 / 模拟器；未 commit / push / reset / checkout；未访问正式书库或其他应用私有记忆。
- 开工记录：应用=Claude Code 桌面版（Code 标签页）；模型=deepseek-v4.1-flash（经 anthropic/claude-ccr 代理路由；environment 报告的 model id 解码即 deepseek-v4.1-flash）；时间=2026-10-05。
- **同编号任务核占用**：E 盘无 `txt-final-acceptance-prep-v1` 目录、D 盘无本报告，E 盘未发现任何 acceptance-prep 产物 → 本编号任务此前未运行，无重复写入风险。

## 环境

Windows 11 / Python 3.13.2；sha256sum 可用。未连接设备。

## 执行与结果

### 解决的验收缺口

旧 umu_12 只查进度行存在（offset=0 也 pass）；补强版 `j1ReentryAccepted` 要求退出与重进偏移逐字相等，
但生产 TXT **按阅读单元起点恢复**，逐字相等会把正确实现误判为失败。本任务从真实正文 + 生产阅读单元
映射定义**可解释的恢复合同**（见交付 `J1RecoveryContract.kt` 头注释与根 `README.md` §2）：

> 退出保存 S ∈ [U.charStart, U.charStart+U.charCount]（U=顶部可见单元）；恢复落到单元起点 U.charStart。
> 判通过：S>0 且落在**非初始单元**；重进 R>0、与 S **同一单元**、`R==单元起点` **或** `R==S`（精确恢复）；
> 重进真实 UI dump 含**恢复目标处可见段落标记**；等待期未清零；继续滚动的新位置可保存且 UI 命中。
> 逐项记录 S / 恢复目标 / R / 可见标记，互不替代。若 TXT-v2 实现精确偏移恢复，合同已可绑定更严格分支。

拒绝（含负向用例）：只查非零、任意宽松字符容差、只查逐字相等、凭截图标题/手填 PASS、直写进度行。

### 交付清单（12 个源工件 + 离线结果）

`payload/androidTest/…/J1RecoveryContract.kt`（纯合同与谓词）、`…/TxtFinalAcceptanceJ1Test.kt`（观察器 + 负向用例）、
`payload/fixture/{j1_fixture.py,fixture.txt,fixture.sha256.txt}`、`payload/APPLY.md`、
`driver/{j1_ui_callback.py,j1_evidence_check.py}`、`config/{j1-config.template.json,binding-fields.md}`、
`offline/j1_offline_tests.py`、`README.md`、`manifest/payload-sha256.txt`。SHA 见下。

### 实际旅程（待执行步骤，非现在运行）

seed 夹具 → 真实 UI 打开并滚动到非初始单元 → 退出读生产保存值 S → 等待期不清零 →
首页/书架/强停冷启动三路重进核同一合同 → 恢复后继续滚动并退出验证新位置可保存。

### 离线验证（本任务真实执行）

命令：`python offline/j1_offline_tests.py`（退出码 0）。
对象是 **Python 证据/绑定门禁**（`driver/j1_evidence_check.py`）与回调/配置逻辑；合成证据束正反例
**18/18 与期望一致**。原始输出、逐例退出码与判定见 `offline/results/`（含 `run-raw.txt`、`SUMMARY.json`）。

覆盖：清零拒绝、错误单元拒绝、单元内非起点非精确拒绝、UI 标记缺失/错误/SHA 不符拒绝、
fixture/run_id/config/APK/units/源码清单不匹配拒绝、缺恢复证据整体不通过、等待期清零拒绝、
新位置未移动拒绝；正例：合法单元起点恢复、精确偏移恢复。

### 交付 SHA256（`manifest/payload-sha256.txt` 实算）

```
9dd7101ab360…  README.md
cce00b78c411…  config/binding-fields.md
db87bd43857a…  config/j1-config.template.json
2c638e22e200…  driver/j1_evidence_check.py
878ab9432327…  driver/j1_ui_callback.py
3205f0d2de5f…  offline/j1_offline_tests.py
def5e6a7417c…  payload/APPLY.md
05083e268de6…  payload/androidTest/…/J1RecoveryContract.kt
efdee7bb8645…  payload/androidTest/…/TxtFinalAcceptanceJ1Test.kt
477fd489c59a…  payload/fixture/fixture.sha256.txt
15e6f96577c6…  payload/fixture/fixture.txt
1fc7a107d520…  payload/fixture/j1_fixture.py
```

## 原始证据

- 交付目录：`E:\creation-reading-assistant\txt-final-acceptance-prep-v1\`
- 离线原始输出：`…\offline\results\run-raw.txt`、逐例 `<case>.json`、`SUMMARY.json`
- 夹具：`…\payload\fixture\fixture.txt`（SHA 15e6f965…，10390 字符 / 4 个 3000 字符阅读单元）
- 交付清单 SHA：`…\manifest\payload-sha256.txt`

## 恢复与副作用

本任务无设备操作，无系统设置/APK/DB 改动；不触碰 protected 包与正式书库。
唯一副作用为新建 E 盘交付目录与本报告（只增不改）。旧报告/旧驱动/旧候选原件保留不动。

## 未覆盖项

- **Kotlin 源未编译、未运行（NOT_RUN）**：`J1RecoveryContract.kt` / `TxtFinalAcceptanceJ1Test.kt` 可能存在与最终候选
  API 的签名差异，需在 TXT-v2 冻结候选内编译确认；编译/执行入口见 `payload/APPLY.md`。
- 未做设备执行；未绑定 TXT-v2 冻结源码清单/双 APK/runner/serial（明确标**待绑定**）。
- 生产「布局未就绪后缺重试」缺口属窗口一（TXT-v2）职责，本任务不判断、不预判其实现。
- 未用重复实现的 Python 谓词宣称 Kotlin 测试已通过。

## 交付状态与下一步

状态：**READY_FOR_BINDING**（离线验证通过）。不写 J1 设备 PASS，不提前绑定正在变化的 TXT-v2 源码。

待 TXT-v2 冻结后由唯一集成/设备 owner：
1. 按 `payload/APPLY.md` 复制两源文件到候选并编译（qahost 变体）；
2. 填齐 `config.json`（含 `source_manifest` 冻结源码清单）→ 经 `joint_driver.run_all(before_item=j1_ui_callback.callback)` 串行执行；
3. 拉回证据 → `python driver/j1_evidence_check.py …` 得批次判定；
4. APK 身份、恢复合同、旅程行为分别判定；**恢复缺证据时不得因 J1 行为正确而判整批 PASS**；只使用本轮前后账。

准备完成，停止，等 Codex 核查后再由唯一 owner 合流构建。
