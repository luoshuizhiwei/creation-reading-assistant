# CRA-READ-NOTE-JOURNEY-ACCEPT-v1：可达三格式笔记冷读回源 + 仅移除正文 MuMu 验收（2026-10-03）

## 任务编号/版本

`CRA-READ-NOTE-JOURNEY-ACCEPT-v1` / v1，2026-10-03。

- **执行应用/模型**：Claude Code（桌面 Code 标签页）；实际路由标识 `hw726brokbuddy AI/global:deepseek-v4.1-flash`
- **执行标识**：`CRA-READ-NOTE-JOURNEY-ACCEPT-v1-20261003-1100`
- **角色**：本任务唯一构建、测试驱动与 MuMu 操作者
- **状态**：**已交付待 Codex 核查**

## 输入指纹

| 项 | 值 |
|---|---|
| 工作仓库 / HEAD（预期=实际） | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` / `e39a78da9c62f02614207cffb36969a32492eaea` |
| 暂存区 | 空（本批前后一致） |
| 只读产品输入 | `E:\creation-reading-assistant\next-round-integration-ready-v1\android`（883 文件） |
| `final-manifest.json` SHA-256 | `a0240a395c20251f480378ed07ba901c0c0061c79cd98ea4b43985b229ed491d`（scope 25，25/25 载荷 SHA 全等） |
| 上一批核查 | `docs/coordination/reviews/2026-10/2026-10-03-next-rollback-v1-mumu-v2-reader-v2-codex-review.md`（指明本任务为「新 UID 执行可达三格式笔记冷读回源与正文移除有限旅程」） |
| 只读预检 | `docs/coordination/dispatches/2026-10/2026-10-03-next-read-note-return-precheck-v2.md`（删除语义三分、笔记页不可达边界） |

**占用核对（开工前）**：`CRA-INTEGRATION-MUMU-ACCEPT-v2`（ZCode/GLM）已于 10:03 交付报告并释放设备；`CRA-RESELECT-ROLLBACK-FIX-v1` 为零设备任务；`next-round-v2-review-20261003` 为只读复核。设备上无运行中 instrumentation、无应用进程、`forward`/`reverse` 均为空。**两个 adb serial（`127.0.0.1:16416` 与 `emulator-5556`）实为同一台 MuMu**（build fingerprint、uptime、`nemu*` 服务与监听进程 PID 完全一致，唯一监听者为 `MuMuVMMHeadless.exe`），本批统一使用 `127.0.0.1:16416`。

## 所有权与改动

- **唯一工作目录**：`E:\creation-reading-assistant\next-round-read-note-journey-accept-v1`
- **唯一仓库报告**：本文件
- **本任务副本内改动（未回灌冻结输入）**：
  1. `app/build.gradle.kts` qahost build type 的 `applicationIdSuffix`：`.qahost` → **`.qahostreadingv1`**（本批隔离后缀）
  2. **新增** `app/src/androidTest/java/com/creationreadingassistant/acceptance/ReadNoteJourneyAcceptanceTest.kt`（夹具与断言驱动，本批唯一新增仪器类）
  3. 新增 `ui.py`（真实 UI 执行器）、`rj.py`（仪器运行器）
- **未改**：产品源码、既有测试、runner、依赖、冻结输入、`current.md`、TODO、其他窗口目录、私有记忆库
- **未执行**：stage / commit / push / reset / checkout；未混入并行 rollback-v2 的产品改动
- 旧独立笔记页**未恢复入口**、未删死代码；跨书/临时查阅旅程**未在本批关闭**

## 环境

- Windows 11 / Git Bash；JDK 17.0.14；Android SDK build-tools 34.0.0；Python 3.13；独立任务缓存（复制完成后才启动 Gradle，全程 `--offline`）
- **设备**：MuMu 模拟器（判定依据为宿主进程 + TCP 对应 + guest 特征，**不凭型号字符串**；该机 model/manufacturer 被模拟为 `SM-S9280`/`Samsung`）。手机宽度竖屏：物理 1080×1920，测试期 `wm density 480`（≈360dp），**收尾已 `wm density reset` 回 280**
- **runner 与真实链路**：输入 runner 为 `com.creationreadingassistant.testing.CraIsolatedTestRunner`，其隔离分支仅在 targetPackage **精确等于** `com.creationreadingassistant.qahost` 时生效；本后缀不匹配 ⇒ 走透传分支。设备侧实证每次断言证据中 `applicationClass = com.creationreadingassistant.App`，即**清单声明的生产 Application（Hilt/Room/DataStore 真实链路）**
- **冻结双 APK**：

| APK | 包名 | 字节 | SHA-256 |
|---|---|---:|---|
| 宿主 | `com.creationreadingassistant.qahostreadingv1` | 51,371,708 | `7f3da32f4bdfc4b71fc020e800e06f00432870555277b7d2e5833ef45a7b3fdc` |
| androidTest | `com.creationreadingassistant.qahostreadingv1.test` | 9,171,667 | `b46ce1af6e58adb7d48986cb63a3c50ba9d496cb555ca60ff0ff329ed067f947` |

  安装后设备回读 SHA 与冻结件一致；`apksigner verify` 两包 exit 0；证书 `05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1`。宿主 manifest `application android:name="com.creationreadingassistant.App"`；androidTest `instrumentation android:name="com.creationreadingassistant.testing.CraIsolatedTestRunner"`、`targetPackage="com.creationreadingassistant.qahostreadingv1"`（**按 manifest 实际 runner 核对**）。

### 本批实测到的工具边界（如实记录，影响执行方式）

Compose 仪器测试环境（`createAndroidComposeRule`）内，**`performTouchInput { longClick(...) }` 与 `Instrumentation.sendPointerSync(...)`（含 `SOURCE_TOUCHSCREEN`）均无法让本阅读器的 readOnly `BasicTextField` + 自定义 tap 门控产生选区**：30 个候选落点全部尝试、双向握手确认触摸已送达，Compose 语义树仍观察不到「高亮」工具条；而**设备侧真实触摸（`adb shell input swipe x y x y 1200`）在同样坐标下立即产生选区**。

⇒ 本批据此把**全部 UI 动作（选区 / 保存 / 查看 / 回源）交给设备侧执行者用 uiautomator 完成**，仪器驱动只负责**夹具准备与真实持久数据断言**，不用替身链路冒充 UI 验收。这是执行方式的选择，**不是产品缺陷**。

## 执行与结果

### 7 项验收合同

| 合同 | 结果 | 关键证据 |
|---|---|---|
| **J1 TXT**：真实阅读选区→保存高亮→杀进程冷启动→书籍详情查看→书内笔记面板点击回源 | **PASS** | `ui-runs/j-txt-*.xml`、`screens/j-txt-*.png`、`device-evidence/rnj_10_txtAssertSaved.json`、`rnj_11_txtColdReadback.json` |
| **J2 EPUB**：同一完整旅程，核章节与具体位置 | **PASS** | `ui-runs/j2-epub-*.xml`、`rnj_20_epubAssertSaved.json`、`rnj_21_epubColdReadback.json` |
| **J3 Markdown**：同一完整旅程，核实际来源偏移 | **PASS** | `ui-runs/j3*-md-*.xml`、`rnj_30_mdAssertSaved.json`、`rnj_31_mdColdReadback.json` |
| **M1 TXT 仅移除正文** | **PASS** | `rnj_40_txtRemoveBodyOnly.json`、`rnj_43_txtAssertAfterRemove.json`、`ui-runs/m1-txt-*.xml` |
| **M2 EPUB 仅移除正文** | **PASS** | `rnj_41_epubRemoveBodyOnly.json`、`rnj_44_epubAssertAfterRemove.json`、`ui-runs/m2-epub-detail.xml` |
| **M3 Markdown 仅移除正文** | **PASS** | `rnj_42_mdRemoveBodyOnly.json`、`rnj_45_mdAssertAfterRemove.json`、`ui-runs/m3-md-detail.xml` |
| **R1 环境完整恢复** | **PASS** | `device-baseline/settings-before.txt` vs `settings-after.txt`、`qahost-files-sha-before/after.txt`、`existing-apks-sha-before/after.txt` |

### J1 / J2 / J3 实际驱动路径（全部真实 UI）

1. 打开 App → 底栏「书架」→ 点书进入阅读器
2. **长按正文标记句**（真实触摸）→ 选区工具条出现（高亮 / 浏览器 / 复制 / 更多）
3. 点「高亮」→ 颜色行出现（`选择高亮颜色`：yellow/red/green/blue/purple）→ 点黄色 → 界面提示「已高亮」
4. **杀进程冷启动**（`am force-stop` 后另起一次 `am instrument`，新 PID）→ 只读回既有持久数据
5. 书架长按该书 →「书籍详情与管理」→ 滚动到「书签与笔记」区块，**摘录可见**
6. 进书 → 翻页离开标记句（确认标记句**不可见**）→ 阅读器「更多」→「笔记与标注」→ 点该条「跳转」→ **标记句重新可见**

**逐格式保存与冷启动读回（真实 Room 持久数据）**：

| 格式 | 摘录 | locator（来源身份/位置） | 章节 | 冷启动 | 回源到达 |
|---|---|---|---|---|---|
| TXT | `中性测试标记句甲甲甲` | `{"v":2,"offset":0,"ci":0,"co":0,"fp":"110be3b38e1e07b5"}` | 全文 | PASS（PID 15509→15628，值一致） | 翻页至 **52.2%** 后跳转回 **0.0%**，标记句可见 |
| EPUB | `中性测试标记句丙丙丙` | `{"v":2,"offset":0,"ci":0,"co":0,"fp":"2187fe88ec6c79a1"}` | **第 1 章** | PASS（PID 17769→17887，值一致） | 翻页离开后跳转回 **第 1 章 / 0.0%**，标记句可见 |
| Markdown | `中性测试标记句乙乙乙` | `{"v":2,"offset":0,"ci":0,"co":0,"fp":"64186fd4f469a3fd"}` | 开篇 | PASS（PID 19220→19334，值一致） | 翻页离开后跳转回 **0.0%**，标记句可见 |

- **摘录与个人批注**：本批保存的是**高亮**（`excerpt` = 高亮正文，`annotation`/`note` = 个人批注，二者在数据与 UI 上分区；`AnnotationModels` 两处 `toAnnotationEntry` 已由只读预检确认）。书籍详情页以「高亮 · 0%」为标题、摘录为副标题展示。
- **来源身份**：三种格式的 locator 均带 `v:2` 版本与 `fp` 内容指纹；EPUB 的 `ci`（chapterIndex）/`co`（charOffset）为**章内坐标**，TXT/MD 为 `offset`（全书偏移），与只读预检的「EPUB 用 forEpub、TXT/MD 共用 forPlain」结论一致。**J3 的偏移由 MD 自身实测得出，未由 TXT 推定。**
- **回源到达**：以「翻页离开后标记句不可见 → 跳转后标记句重新可见 + 进度指示回到 locator 指向的位置」双重判定，**不只看 ReaderRoute 被打开**。

### M1 / M2 / M3 实际驱动路径（仅移除正文）

移除动作调用**产品入口** `BookRepository.clearBookCache`（生产实现，非夹具直写库；不改软删除/物理删除合同）。随后：

| 格式 | 书行 | content_status | 正文指针 | 活跃高亮 | 正文文件 | 详情摘录 | 打开时失败解释 |
|---|---|---|---|---|---|---|---|
| TXT | 仍 active（1） | `missing` | `local_content_path`/`local_uri` 均清空 | 1（保留） | 已删除 | **可见** | `《测试 TXT 中性甲》正文未在本机，暂时无法打开。请检查文件状态或重新导入/下载正文。` |
| EPUB | 仍 active（1） | `missing` | 清空 | 1（保留） | 已删除 | **可见** | `《测试 EPUB》正文未在本机，暂时无法打开。请检查文件状态或重新导入/下载正文。` |
| Markdown | 仍 active（1） | `missing` | 清空 | 1（保留） | 已删除 | **可见** | `《测试 Markdown 中性乙》正文未在本机，暂时无法打开。请检查文件状态或重新导入/下载正文。` |

书架三本均显示「正文未在本机」状态标签。**这与软删除/物理删书是两个不同合同**：本批只做「仅移除正文」，未改变现有删除/撤销合同。

### 运行计数

- **仪器运行**：19 PASS（含 3 次冷启动独立进程）+ 11 FAIL（**全部为调试期 UI 驱动尝试，已保留原件**）+ 10 份 `*-texts.txt` 诊断输出
- **UI 层级 XML**：98 份；**截图**：108 张（1080×1920 竖屏）
- **设备侧 JSON 证据**：17 份（`device-evidence/rnj_*.json`）
- 未重跑输入未变的旧 153 条 JVM；本批只编译本驱动并做相关验证

## 原始证据

根目录：`E:\creation-reading-assistant\next-round-read-note-journey-accept-v1\`

| 路径 | 内容 |
|---|---|
| `device-runs/*.txt` | 仪器原始输出（命令、exit、OK/FAILURES、异常栈）。每次运行独立文件名，**失败原件全部保留未删** |
| `device-evidence/rnj_*.json` | 设备侧 JSON 证据（17 份）：保存后 / 冷启动读回 / 移除正文 / 移除后复核 / 最终状态 |
| `device-evidence/*.xml` | 关键步骤的 UI 层级原件（含详情页摘录、笔记面板、失败提示） |
| `ui-runs/*.xml`、`ui-runs/*-texts.txt` | 真实 UI 每一步的层级与文本清单 |
| `screens/*.png` | 108 张截图（1080×1920 竖屏，实测尺寸） |
| `freeze/apk/*`、`freeze/meta/*` | 冻结双 APK、badging / manifest / signature 原输出 |
| `freeze/device-run-index.json` | 逐次运行索引与 SHA |
| `logs/` | 13 次构建日志（含失败与迭代） |
| `device-baseline/` | 前后对账：包、APK SHA、53 个持久文件 SHA、8 项设置、mappings |
| `ui.py`、`rj.py`、`android/…/ReadNoteJourneyAcceptanceTest.kt` | 本批执行器、运行器与唯一新增仪器类 |

> 证据为 **JSON / UI XML / PNG**；instrumentation 结果 XML 由 `am instrument` 侧产出，两者不混称。测试书均为「测试 TXT / 测试 EPUB / 测试 Markdown」中性名称。

## 未覆盖项

- **7/7 只代表本批有限旅程**，不代表全阅读能力或全产品。
- **普通书内回源 ≠ 临时查阅**：本批只验普通回源（书内笔记面板「跳转」）；**跨书/临时查阅旅程未在本批关闭**（其 UI 入口仍不可达，属保留边界）。
- 旧独立笔记页**未恢复入口**，其不可达状态是保留边界，本批不以代码注释取消需求。
- **未验**：实体手机/MIUI、TalkBack、R8、正式签名/升级、WebDAV、正式书库、AI/续读卡/索引工程。
- 高亮**颜色**只选了 yellow；其余颜色未逐一验证（颜色行存在性已由 UI 层级证据覆盖）。
- **个人批注（note 字段）**未在本批通过 UI 录入（保存的是高亮）；「添加批注」入口在选区「更多」菜单中存在，但本批未执行该动作。
- 三格式各只保存 1 条高亮；未覆盖多条、跨章、软删除/撤销。
- `M` 项的正文移除走产品仓储入口而非「书架 → 批量选择 → 清缓存」的 UI 路径（该 UI 路径在 G1-v3 已有验收）。

## 恢复与副作用

| 项 | 结果 |
|---|---|
| 本批新增宿主/测试包 | 已卸载（`Success` ×2）；`/data/user/0/…qahostreadingv1` 与 `/sdcard/Android/data/…qahostreadingv1` 确认**不存在** |
| 本批测试夹具 | 随卸载一并消失（三本书行/正文文件/高亮均在沙箱包私有目录） |
| 原宿主/测试 APK | 三个既有包 APK SHA **3/3 与开工基线相同** |
| 受保护持久数据 | `.qahost` 的 files / databases(含 WAL/SHM) / shared_prefs **53/53 SHA-256 完全一致** |
| 系统设置 | 1080×1920 / 280dpi / rotation 0 / accelerometer_rotation 1 / timeout 60000 / stay_on 0 / night=yes / 无代理 —— **8/8 与开工基线逐字节相同** |
| ADB 映射 | 本批未创建任何 `forward`/`reverse`；收尾两者均为空，与基线一致 |
| 额外状态 | 沙箱包在 AndroidKeyStore 的主密钥别名与 `androidx.work.workdb` 随卸载消失；无遗留 |

**需要如实登记的副作用（已恢复）**：开工实测夜间模式为 `yes`，批中变为 `no` —— 这是生产 `App.onCreate` 的 `applyApplicationNightMode` **跨进程持久修改系统级设置**所致（该行为在仓库既有文档中已登记为已知副作用）。收尾已 `cmd uimode night yes` 恢复并回读确认，8/8 设置与基线一致。**未发现误定位或持久数据污染**；未触发停止条件。

## 交付状态与下一步

**已交付待 Codex 核查。** 7/7 合同通过：可达三格式（TXT/EPUB/MD）的「真实阅读选区 → 保存高亮 → 杀进程冷启动 → 书籍详情查看 → 书内笔记面板点击回源」完整旅程，以及三格式「仅移除正文」后书行/笔记仍 active、详情摘录仍可查看、打开正文有明确失败解释；环境完整恢复。

**建议下一步（由 Codex 决定）**：

1. 独立核查本批原始证据，重点：三格式 locator 的字段差异（EPUB `ci/co` vs TXT/MD `offset`）、冷启动 PID 变化与值一致性、回源的「离开→回来」双重判定、M 项的 53/53 与 8/8 对账。
2. 若核查通过，本旅程的**可达子链**即可关闭；**跨书/临时查阅**与**旧独立笔记页去留**仍是独立待决项（见只读预检 v2 的裁决点）。
3. 未开启新功能、未追加全量历史测试、未提交、未推送、未启动下一任务。

**百分比**：本批合同 **7/7 = 100%**；这是**有限旅程**的完成率，不等于全阅读能力或全产品完成率。全产品完整分母仍未核定。
