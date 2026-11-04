# 统一候选 MuMu 设备验收 v1（J1–J5 与恢复）

- 任务编号/版本：`CRA-UNIFIED-MUMU-ACCEPT-v1`；2026-10-05 指定，实际执行 2026-10-05T02:05–03:15+08:00，应用 ZCode / GLM（account:zai-start-plan / GLM-5.3-Flash）。角色=唯一联合构建与 MuMu 设备验收负责人。
- 输入指纹：HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（开工=收工）；暂存区前后 0 条。已核输入 `E:\creation-reading-assistant\unified-candidate-integration-v1\android`；**Codex 978 文件源码指纹（含新组合回归测试）复制后逐文件实算 978/978 匹配，零漂移**；受影响 JVM 424/424 证据按输入不变条件复用（本轮未重跑 JVM）。新增验收测试源 1 文件（见下），已重新绑定测试 APK——不与旧测试包混用。
- 所有权与改动：写入仅 `E:\creation-reading-assistant\unified-mumu-accept-v1\`（android 副本、freeze、evidence、build-logs、gradle-home、execution.json）与唯一报告 [docs/qa/2026-10/2026-10-05-unified-mumu-accept-v1.md](2026-10-05-unified-mumu-accept-v1.md)。生产 Kotlin / D 产品源码 / 原仓库 / current/TODO / 公共清单零改动；未 commit/push/reset/checkout；未触正式书库与实体手机。本轮独占 Gradle/ADB/MuMu；窗口二仅离线脚本，未等待、未采用其结果。
- 环境：Windows / Git Bash / Python 3.13；JDK 17.0.14；Gradle 8.9 `--offline`；隔离 Gradle home 3.4G（自本会话前序任务私有缓存复制，期间修复了一次不完整 wrapper dist 复制——重拷后构建成功）；MuMu serial `127.0.0.1:16416`（本轮实核，`emulator-5556` 为同 VM 别名未使用）。

## 执行与结果

### 构建与冻结

- **包后缀**：本轮唯一新隔离后缀 `.qahostunifiedv1`（仅改 `app/build.gradle.kts` 一处 applicationIdSuffix，逐项记录的差异；aapt 实读包名 `com.creationreadingassistant.qahostunifiedv1` / `.test`）。安装前实查：pm path rc=1（两包）+ `pm list packages` 精确匹配不存在 = **新 UID 双重确认缺席**；数据目录 absent。
- **双 APK**：`assembleQahost` + `assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost`，EXIT=0（[build.log](<E:/creation-reading-assistant/unified-mumu-accept-v1/build-logs/build.log>)）。宿主 SHA `b5594784…`；测试 APK 含新增夹具后重构建为 `e4d0e3ee…`（[apk-sha256.json](<E:/creation-reading-assistant/unified-mumu-accept-v1/freeze/apk-sha256.json>)）。源码冻结 [source-sha256.json](<E:/creation-reading-assistant/unified-mumu-accept-v1/freeze/source-sha256.json>)（979 文件 = 978 已核 + 新增夹具）。instrumentation 三元组（runner/target/test）安装读回核实。
- **新增验收测试源**：`acceptance/UnifiedMumuAcceptV1Test.kt`（夹具准备 + 持久数据断言，中性书名，禁直写 DB 冒充通过；职责划分沿用 mdtc/mdpc/jera 口径）。因 J2 初版 MD 章节过短无法滚动，夹具从 10 段/章扩到 60 段/章并重建测试 APK（重建后已重装、重 seed，全程同一冻结对）。

### 设备旅程结果（每条绑定同一冻结双 APK + 原始日志/UI dump/截图/DB 读回）

| 旅程 | 判定 | 关键证据 |
|---|---|---|
| **J1** TXT 滚动实时偏移退出重进 | **FAIL** | 退出保存成立：滚动模式真实滑动 8 次后退出，进度行 `{"offset":2553, locator_v2{offset:2553, ci:0, co:2553}}`（[umu_11_second.json](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/umu-results-ext/umu_11_second.json>)，29.98% 首轮 870 同成立）。**重进失败**：首页“继续阅读”与书架卡片两条路径均回 ¶1，进度行被改写为 offset 0（[umu_12_second.json](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/umu-results-ext/umu_12_second.json>) offsetAfterReenter=0；截图 [j1-reentered.png](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/ui/j1-reentered.png>) / [j1-shelf-reentry.png](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/ui/j1-shelf-reentry.png>) / [j1n-reentry-8s.png](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/ui/j1n-reentry-8s.png>)）。两轮独立复现（870 与 2553 两组数据）。logcat 无 restore 诊断输出（[j1-logcat.txt](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/raw/j1-logcat.txt>) 仅 JIT 噪声）。**最小修复范围推断（未越权修改）**：TXT 滚动恢复链 `parseStoredAbsoluteOffset→savedPlainOffset→pendingInitialPosition` 在纯文本打开路径未生效，方向为“重进时恢复落点计算/应用”而非保存侧。 |
| **J2** MD canonical 滚动退出重进 | **PASS** | 退出保存 `{"offset":1894,"space":"canonical",locator_v2{offset:1894}}`（[umu_21c.json](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/umu-results-ext/umu_21c.json>)，20.83%，非 TXT 列表诱饵值、非直写 DB）；冷重进恢复至 ¶50 区域、进度行 1859（[umu_21d.json](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/umu-results-ext/umu_21d.json>)，差值 35 字符为重进后一次防抖落库，同一屏内），截图 [j2-reentered.png](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/ui/j2-reentered.png>)。 |
| **J3** MD 分页 TOC 1→2→3→2 + 强停冷启 | **PASS** | 目录跳转后工具栏“第 2 章/25.0%”、正文第 2 章、底栏一致（[j3-after-3-2.png](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/ui/j3-after-3-2.png>)）；强停冷启后恢复“第 2 章/25.0%”，DB 保存 offset 2277 落第 2 章范围 [621..4538]（[umu_31.json](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/umu-results-ext/umu_31_verifyJ3State.json>)）；冷启读回截图 [j3-cold-reader.png](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/ui/j3-cold-reader.png>)。**r3 现象（toolbar/正文/底栏章号不一致）未复现**。 |
| **J4** EPUB 书内笔记真实回源 | **PASS** | 真实 UI：第 2 章 → 点选目标句块 → 更多 → 高亮 → 选色（截图链 j4b/j4c/j4d/j4g）。highlights 表 1 行 `ci=1, co=27, offset=649, fp=7de304c4`（[umu_41b.json](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/umu-results-ext/umu_41b.json>)）；退出重进后 经 TOC→笔记 tab→真实“跳转”按钮（[j5k-notes.xml](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/ui/j5k-notes.xml>)）回源：**marker 句在屏可见**（[j4-returned.png](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/ui/j4-returned.png>)），离开保存落 `locator_v2{ci:1}`（umu_42）。**缺省全书 offset 契约的可达路径覆盖说明**：EPUB 本旅程锚点链路经 `ci+co`（ci=1,co=27），未经过“locator_v2 无 offset 键”的消费分支——该分支由 JVM 契约测试 3 项（真实 SourceNavigationContract 派生）覆盖，设备侧不可达路径如实记录，未用直接调用冒充 UI。 |
| **J5** Profile 恢复横幅 | **PASS** | ①无中断：横幅不显示（[j5-no-banner.png](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/ui/j5-no-banner.png>)，恢复相关文本 0 条）；②真实中断流程=受控隔离夹具：经 `run-as` 写入生产状态文件 `zip-restore-state.json`（stage=started，夹具性质如实声明）后冷启动，Profile 显示“上次整包恢复未完成 / 已开始，恢复未完成（数据库状态未知）……导出已被阻止…… / 重新选择同一备份重试”（[j5-banner.png](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/ui/j5-banner.png>)，生产横幅组件+生产读取链驱动，非直改 UI）；③跨实例持久：再强停重启横幅仍在（[j5-banner-2nd-instance.png](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/ui/j5-banner-2nd-instance.png>)）；④夹具移除后横幅消失（[j5-banner-gone.png](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/ui/j5-banner-gone.png>)），生产读写闭环。**合同边界如实声明**：状态文件注入为夹具，本轮未执行“真实 zip 导入中途强杀”级中断，J5 判 PASS 限定为“横幅组件行为与状态读取链”，不冒充完整恢复流程验收。 |

**总计：4 PASS / 1 FAIL / 0 NOT_RUN。** 新统一设备旅程 4/5=80%；不与 JVM/历史批次相加。

### 恢复与副作用（对账）

- 卸载本批双包 Success×2；缺席双重确认（pm path rc=1 空 + pm list 精确匹配无）（[cleanup.txt](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/raw/cleanup.txt>)）。
- protected `qahost`/`qahost.test` 两包原位未动（路径与基线一致）；其数据 43 文件数、DB/SHM/WAL 字节与开工基线一致（[baseline-data.txt](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/raw/baseline-data.txt>) vs cleanup 段）。
- 系统设置：动画 0.5/0.5、stay_on 0、user_rotation 0、1080x1920/280 前后一致；**夜间模式基线 yes → 收尾 no**——本轮 0 条命令触碰 uimode（[night-mode-note.json](<E:/creation-reading-assistant/unified-mumu-accept-v1/evidence/raw/night-mode-note.json>)），与既往批次“翻转来源未知”历史一致，按合同如实记录为未闭合项，不伪称本批已恢复 yes。
- forward/reverse：本轮未新增，收尾为空；/data/local/tmp 无 cra_* 残留（grep 计数 0）。
- 本批测试数据仅存在于已卸载的新 UID；未见正式书库写入。

## 原始证据

`E:\creation-reading-assistant\unified-mumu-accept-v1\`：`freeze/`（双 APK + source-sha256.json 979 项 + apk-sha256.json + final-freeze.json）；`evidence/raw/`（基线/安装/各 instrument 日志/crash 检查/cleanup/夜间说明 ×22）；`evidence/ui/`（逐旅程 UI dump XML 与截图）；`evidence/umu-results-ext/`（15 份夹具/验证 JSON+截图）；`build-logs/`；`execution.json`；`umu_ui.py`（本轮驱动，已去 pycache）。

## 未覆盖项

- **J1 失败根因未定位**（本轮按任务边界只保存失败证据、完成恢复、报告最小修复范围：恢复链消费侧方向；未修生产源码）。
- J5 未执行“真实 zip 导入中途强杀”级中断；SE4 注解延迟消费的强停冷读（umu 11/30 链路）本批未重复（J4 已覆盖同链路非强停部分）。
- 夜间模式翻转机制未追查；手机窄布局/实体机/MIUI/TalkBack/R8/发布维持既有边界；SAF/content URI 重授权链未新增覆盖。
- 本轮未重跑任何历史 G1/N1/J3 场景（无失败触发增补）；424/424 JVM 证据按输入不变复用。

## 交付状态与下一步

统一候选双 APK 冻结 + 五条旅程 4 PASS / 1 FAIL（J1 TXT 重进恢复失败，两轮复现，证据完整）+ 全账恢复完成。本轮就此停止：不回灌 D、不提交 Git、不开启 J1 修复任务。**J1 为新统一候选设备级缺陷，需 Codex 裁决修复归属后另立窄任务；统一候选在 J1 修复前不得标设备验收通过。**
