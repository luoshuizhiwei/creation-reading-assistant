# CRA-MD-ANNOTATION-RETURN-FIX-v1 — Markdown 书内跳转静默无位移修复报告

## 任务编号/版本

- **任务编号/版本**：`CRA-MD-ANNOTATION-RETURN-FIX-v1`，执行标识 `CRA-MD-ANNOTATION-RETURN-FIX-v1-20261003-1832`，日期 2026-10-03。
- **执行者**：ZCode / GLM（account:zai-start-plan）。目标：修复 Markdown 书内高亮/笔记「跳转」静默无位移，完成回归测试与限定 MuMu 验收。
- **状态**：**已交付待 Codex 核查**；不合流、不启动后续任务。

## 输入指纹

| 项 | 值 |
|---|---|
| HEAD | `e39a78da9c62f02614207cffb36969a32492eaea`（开工/收工核验一致；未 stage/commit/push/reset/checkout） |
| 产品基线 | `E:\…\next-round-integration-ready-v1\android`（882 文件，排除生成物/local.properties） |
| `final-manifest.json` SHA | `a0240a395c20251f480378ed07ba901c0c0061c79cd98ea4b43985b229ed491d` ✓；**25/25 candidate SHA 实测一致** |
| 设备证据参考 | `next-round-read-note-journey-accept-v2`（J3 MD FAIL：locator offset=1270 冷读一致，两次点「跳转」停 8.7% 无提示无位移） |
| 新任务目录 | `E:\…\next-round-md-annotation-return-fix-v1\`（execution.json 记录开始时间/占用/范围） |

## 所有权与改动

**产品 payload（与基线树差恰 3 路径，`product-payload/tree-scope-check.txt` PRODUCT_SCOPE_OK=True，removed=0）**：

| 文件 | before | after | 说明 |
|---|---|---|---|
| `…/ui/screen/reader/ReaderProgressEffects.kt` | `7e4b4ab8…` | `95f2e20f…` | SE4 effect 分派改由计划函数穷尽消费；EPUB/plain 内部逻辑逐字保留（补丁 59 行） |
| `…/ui/screen/reader/ReaderAnnotationNavigation.kt` | （新增） | `804d9b61…` | `annotationNavigationPlan`（穷尽 sealed 分派决策）+ `applyMarkdownAnnotationPlan`（Markdown 动作面）+ `offsetFromProgress`；**生产 effect 实际调用**（非镜像） |
| `…/test/…/ReaderAnnotationNavigationTest.kt` | （新增） | `9d9d092e…`→终版 `9fd9d092…` | 10 用例分派/消费/路由回归 |

**设备工件与产品 payload 分离**（不入产品清单）：`device/` 子副本——`app/build.gradle.kts` 仅 `.qahost`→`.qahostmdfixv1` 后缀单 hunk（`4ff8b5f7…`）、新增仪器类 `MdAnnotationJumpAcceptanceTest.kt`（`2fb1fd4e…`）、`mdfix_ui.py`（`ee9b5fa0…`）。

**修复语义**：Markdown 高亮回源进入 canonical 定位通道（`pendingSourceLocatorJsonState` 移交 SE3——既有跨章延迟机制，locator 原文即偏移保留载体）；无 locator 有进度 → 按 `totalChars` 线性换算 canonical 偏移走 `jumpToMarkdownOffset`；两者皆无 → 明确 notice（禁止静默）；EPUB/plain 分支行为不变（apply 返回未消费、零回调=机器可检查的路由守卫）；TXT 空内容退化形态维持原静默行为（不扩大改动面）。

## 环境

Windows/Git Bash；JDK 17.0.14；Gradle 8.9 `--offline`；缓存复用 `next-round-joint-j1-v2\_cache\gradle-home`（E 盘，本轮独占）。设备 `127.0.0.1:16416`（MuMu；身份判定同 v2：宿主进程+guest 特征；**全程未触碰 `emulator-5556`**——所有命令显式 `-s 127.0.0.1:16416`）。

## 执行与结果

### JVM 定向（先红后绿；`freeze/evidence/` red-annotation.xml / green-annotation.xml / green-progressnav.xml）

- **红（占位实现逐字等价原行为）**：`ReaderAnnotationNavigationTest` **10 tests / 7 failures**——失败面覆盖 canonical 分派（`expected:<MarkdownSourceNavigationDeferred…> but was:<SilentClear>`）、legacy 章节形态、进度兜底换算、失败反馈、apply 消费 3 式；**3 个守卫用例**（EPUB/plain 路由与优先级）红阶段即通过（原行为本就正确，证明守卫真实有效而非陪跑）。`ReaderProgressNavigationTest`（seekToPercent 既有合同）7/0。
- **绿（修复后）**：10/0 + 7/0 = **17/17，0 failures/errors/skipped**。命令/退出码：RED_EXIT=1、GREEN_EXIT=0（red-run.log/green-run.log）。未跑全量 JVM/Lint。

### 限定 MuMu 验收（模拟器，非实体手机；`device/device-runs/` 13 份原始日志、`device/screens/` 67 截图、`device-evidence/mdfix-results/` JSON）

批次关联（`device/device-batch-association.txt`）：宿主冻结 `47ff9aa5…`（B1 安装回读逐位一致；B2/B3 仅重建测试包，宿主未变 `47ff9aa5…` 逐字节恒定）。测试包 B1 `bb6d80e0…` → B2 `62514c32…`（驱动断言目标从"标记句文本"改为"任一活跃高亮"——长按实际选中的是聚合节点中心段「中性 MD 前段 55」，非缺陷是驱动假设）→ B3 `7bc43c99…`（仪器类结果信封读取缺陷修复；FAIL 原件保留）。安装均 `Success`，运行前设备回读 SHA 与冻结件一致。

| 步骤 | 结果 | 证据 |
|---|---|---|
| 夹具 seed + ready | **PASS** | mdfix_00/01（192205/192210） |
| 真实 UI 非起点保存（长按→高亮→黄） | **PASS**：locator `{v:2,offset=1247,ci=0,co=1247,fp}`，**offset>0 非起点**；DB 查询直证 | mdfix_10（192947/193126） |
| 强停（PID 7628→空）→ 新进程冷读 | **PASS**：locator 逐字一致 + 新 PID（8221≠保存进程） | mdfix_11（193123/193129） |
| 重开书，移开到 **0.0%**（标记不可见） | dump 实证 | seq1/moved-back |
| 真实 UI「更多→笔记与标注→跳转」 | **跳转生效**：**0.0% → 68.2%、标记句可见**（修复前 J3：两次点击停 8.7%、无位移无提示） | seq2-panel/seq3-after（labels 含「中性 MD 前段 55」） |
| 退出触发保存 → DB 对账 | **PASS**：进度行 offset=1036（落在高亮所在渲染块起点）、percent=65.08（自 0 大幅回源） | mdfix_20（193504/193511） |

**如实限定**：① 长按实际选中段落（offset 1247）≠ journey v2 的 1270——同书同手法、语义等价（非起点回源），不冒充复现同一数值；② mdfix_20 的 1036 是渲染块起点的回写，UI 可见性才是"落到高亮"的直接判据（已 dump 存证）；③ 夹具为**单章 Markdown**——**跨章 MD 跳转未实测**（SE3 延迟机制的跨章能力仅其既有 TXT/EPUB 证据 + JVM deferred 移交断言支持，MD 跨章列未覆盖）；④ runner 透传生产 App（真实 Room/DataStore/cra-startup 链路），不声称裸 Application 隔离。

### 恢复与副作用（`device-baseline/` 全原件）

- 沙箱双包卸载 `Success`×2；`/data/data`、`/sdcard/Android/data` 中 `qahostmdfixv1` grep 为空；既有包仍仅 `qahost`/`qahost.test`。
- 既有三包 APK SHA 前后 diff **3/3 MATCH**；qahost 持久文件（53 路径）**逐条 SHA 前后 MATCH**（53/53）；设置（size/density/rotation/accel/stay_on/proxy/night）**前后 MATCH**；forward/reverse 收尾**空**。本任务未新建公共目录/未用映射，无需额外清理。
- **如实登记**：基线采集时误把 `cmd uimode night yes`（设置命令）当查询执行，即刻恢复 `no` 并在 `settings-before.txt` 标注；最终对账 night=no 与 journey v2 后账基线一致。KeyStore 为 UID 级私有数据随卸载消失属平台语义，**不声称直接观测零残留**。

## 原始证据

`E:\…\next-round-md-annotation-return-fix-v1\`：`execution.json`；`product-payload/`（tree-scope-check、ReaderProgressEffects.patch、新增两文件副本、before/after SHA）；`freeze/evidence/`（红/绿 JVM XML+日志、编译日志、device-tooling-sha、批次关联）；`device-freeze/`（B1/B2/B3 测试包 + 宿主 APK + sha256.txt + badging/manifest/signature）；`device/device-runs|screens|device-evidence|batch-association`；`device-baseline/`（9 件前后对账）。

## 未覆盖项

- 跨章 MD 回源设备实测（夹具单章）；JVM 仅断言 deferred 移交语义，未验证 SE3 延迟 focus 的 MD 端到端。
- 笔记（note）typed 目标回源：本轮验证 highlight 通道（同一分派计划消费，typed 差异仅目标解析在分支之前，未单列）。
- 失败反馈的用户可见文案未经设备截图（JVM 断言 notice 回调；MD「两者皆无」形态未在真机复现）。
- 旧 v1 全量 JVM/Lint/Release、N1/G1 验收、桌面/HOLD/F/实体硬件均不在本轮。

## 交付状态与下一步

**交付完成：产品 3 路径 payload + 红 10/7→绿 17/17 JVM + 限定 MuMu MD 跳转实测 PASS（0.0%→68.2% 标记可见 + DB 回源）+ 恢复对账全 MATCH。** 区分记录：源码定位=已实现并穷尽分派；JVM=通过；MuMu=MD 回源实际通过（模拟器）；跨章 MD、note 通道、失败文案可见性=未验证。
下一步：Codex 核查；合流需新的精确清单授权（本候选基于 integration-ready-v1）。
