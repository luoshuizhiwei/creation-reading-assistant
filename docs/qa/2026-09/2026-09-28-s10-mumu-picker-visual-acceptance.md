# S10 MuMu qahost 双 HeaderFooterPicker 视觉收敛 UI 验收报告

> **Codex 2026-09-28 回读：接受 MuMu 手机尺寸竖屏限定范围。** 宿主 APK SHA 与报告一致，当前 Picker 相关源码 SHA 对应构建清单；原始截图及 UI dump 显示两入口折叠行均为「书名电量」、编辑框仍为原始 token。既有 36 个 `files/books` 文件哈希清单前后相同。原库对账仅有 checkpoint 后主 DB 事后哈希（无事后 WAL/SHM），因此本报告有关“数据库对账”的结论限于主 DB；不扩大为实体手机、正式书库或全库三件逐项零变化。

> **日期**: 2026-09-28  
> **验收范围**: qahost 隔离沙箱（`com.creationreadingassistant.qahost`）  
> **设备**: MuMu 模拟器 `emulator-5556`（Android 12 / SDK 31 / x86_64）  
> **授权与环境**: 用户已明确授权 MuMu 验证，本报告结论仅适用于模拟器，不扩大为实体手机或正式书库验收。  
> **工件目录**: `E:\creation-reading-assistant\codex-s10-s12-mumu-20260928\`  
> **代码依据**: `CustomTipDialog.kt`、`ReaderSettingsCommon.kt`、`ReaderDisplaySettings.kt`、`ReaderSubPage.kt` 及 `docs/coordination/legacy-stages/2026-09/2026-09-28-s10-picker-convergence-result.md`

---

## 1. 验收背景与核心指标

此前系统存在两份同名同签名 `HeaderFooterPicker` 实现：
- 全局设置（我的→阅读设置）使用 `ui.components.HeaderFooterPicker`，折叠行显示中文说明（如「书名 · 电量」）；
- 书内设置（阅读器→设置→显示与页眉页脚）使用 `sheets` 内本地实现，折叠行直接暴露原始 token（如「{BookName} · {BatteryPercent}」）。

本轮收敛将书内设置改用共享组件 `ui.components.HeaderFooterPicker`，并删除本地重复实现。本轮视觉验收指标：
1. **手机尺寸竖屏布局**（wm density 420）；
2. **同一模板**在两处折叠行均显示中文说明而非原始 `{Token}`；
3. **点开编辑框时**，token 仍原样显示并完全可编辑；
4. 仅操作隔离测试书，截图及 UI dump 如含其他书名需去识别化。

---

## 2. 构建与包核验

| 项目 | 哈希 / 结果 |
|---|---|
| 构建产物 | `assembleQahost` 产物 `app-qahost.apk`，`assembleQahostAndroidTest` 产物 `app-qahost-androidTest.apk` |
| 构建退出码 | `EXIT:0`，`BUILD SUCCESSFUL in 1m 17s`（原始日志：`04-build.log`） |
| 宿主 APK SHA-256 | `60949CECC9BB40AAB597EC82B550D09BF2A70AD57F234B90E1090036401E40B8` |
| 安装核验 | `Performing Streamed Install` -> `Success`，退出码 0 |
| 设备端包 SHA-256 | `/data/app/.../com.creationreadingassistant.qahost.../base.apk` 哈希 `60949cecc9bb40aab597ec82b550d09bf2a70ad57f234b90e1090036401e40b8`（与本地完全一致） |

---

## 3. 设备状态与手机布局切换

| 维度 | 事前基线 | 验收期配置 | 事后恢复 | 状态 |
|---|---|---|---|---|
| wm size | `1080x1920` | `1080x1920` | `Physical size: 1080x1920` | PASS |
| wm density | `280`（默认平板） | `420`（手机竖屏底部导航） | `Physical density: 280`（`wm density reset`） | PASS |
| 旋转模式 | 竖屏（user_rotation=0） | 竖屏（user_rotation=0） | `0` | PASS |

设备恢复回读记录见 `16-device-restore.txt`。

---

## 4. 逐项视觉验收执行与实测证据

选用统一测试模板：
- **原始模板 Token**: `{BookName}{BatteryPercent}`
- **预期中文说明**: `书名电量`

---

### 4.1 入口 1：「我的 → 阅读设置」全局折叠行与编辑框

- **路径**: 首页底部 Tab「我的」(`[944,1859][1000,1901]`) ->「阅读设置」(`[158,702][310,760]`) ->「夜读」Tab / 安静阅读信息 -> 槽位列表。
- **默认折叠行实测**:
  - `页眉左侧` 默认显示为 `书名`（而非 `{BookName}`）
  - `页脚左侧` 默认显示为 `章节名`（而非 `{ChapterName}`）
  - `页脚右侧` 默认显示为 `总进度%`（而非 `{ProgressPercent}`）
  - 证据: `screen_reader_settings_night.png`、`dump_reader_settings_night.xml`
- **编辑框交互与保存测试**:
  - 点击「页眉左侧」，弹出编辑对话框；
  - 对话框输入框展示原始 token: `{BookName}`，下方「可用变量」包含快捷标签「书名」「章节名」「当前时间」「电量」；
  - 证据: `screen_dialog_global.png`、`dump_dialog_global.xml`；
  - 点击「电量」快捷标签，输入框追加为 `{BookName}{BatteryPercent}`，预览实时显示「测试书籍85%」；
  - 证据: `screen_dialog_inserted.png`、`dump_dialog_inserted.xml`；
  - 点击「确定」保存。
- **保存后折叠行回显**:
  - UI 节点文本: `页眉左侧` 对应折叠行文本显示为 `书名电量`（坐标 `[781,1120][905,1166]`）；
  - 确认显示中文说明，未暴露 `{BookName}{BatteryPercent}` 原始 token；
  - 证据: `screen_reader_settings_saved.png`、`dump_reader_settings_saved.xml`。
- **结论**: **PASS**

---

### 4.2 入口 2：隔离测试书内「显示与页眉页脚」折叠行与编辑框

- **路径**: 书架点击隔离测试书「测试 TXT」 -> 点击屏幕中央呼出阅读菜单 -> 点击底栏「设置」(`[895,1825][951,1867]`) -> 向上滑动点击「显示与页眉页脚」(`[173,1537][425,1595]`) -> 向下滑动查看页眉页脚槽位。
- **隔离测试书内折叠行实测**:
  - UI 节点文本:
    ```
    页眉左侧 : [79,1691][223,1749]
    书名电量 : [808,1697][932,1743]
    页眉中间 : [79,1796][223,1854]
    无 : [901,1802][932,1848]
    页眉右侧 : [79,1901][223,1920]
    无 : [901,1907][932,1920]
    ```
  - 同一模板 `{BookName}{BatteryPercent}` 在书内设置折叠行上**同样正确显示中文说明「书名电量」**！
  - 彻底消除了收敛前书内暴露原始 token `{BookName}{BatteryPercent}` 的分叉缺陷；
  - 证据: `screen_inbook_display_scrolled.png`、`dump_inbook_display_scrolled.xml`。
- **书内编辑框点开测试**:
  - 点击书内「页眉左侧」折叠行；
  - 弹出自定义页眉左侧编辑对话框；
  - 编辑框内展示原始 token: `{BookName}{BatteryPercent}`（坐标 `[183,657][897,868]`）；
  - 效果预览为「测试书籍85%」，可用变量标签齐全，token 处于完全可编辑状态；
  - 证据: `screen_inbook_dialog.png`、`dump_inbook_dialog.xml`。
- **结论**: **PASS**

---

## 5. 对账与去识别化审计

- **测试数据隔离**: 全程仅在新建的中性「测试 TXT」上操作，未打开、未修改 qahost 既有 29 本书；
- **数据库对账**: 测试结束后恢复原库，主 DB SHA-256（`d7c45e04b129e85b1a6cd1cfed68e84f143629242241f6f1eb3b44a263e2d45f`）前后完全一致；既有 36 个书籍文件哈希 0 差异；
- **去识别化审计**: 测试日志与报告中无任何既有书籍敏感书名泄露。

---

## 6. S10 验收结论汇总

| 验收项 | 状态 | 备注 |
|---|---|---|
| 手机尺寸竖屏布局验证 | **PASS** | wm density 420，底部导航栏与底栏设置面板布局完整 |
| 全局设置折叠行中文说明回显 | **PASS** | 「我的→阅读设置」折叠行显示「书名电量」，非原始 token |
| 书内设置折叠行中文说明回显 | **PASS** | 隔离测试书内显示设置折叠行显示「书名电量」，两入口一致 |
| 全局编辑框 token 原样展示与编辑 | **PASS** | 对话框输入框展示 `{BookName}`，支持追加与编辑 |
| 书内编辑框 token 原样展示与编辑 | **PASS** | 对话框输入框展示 `{BookName}{BatteryPercent}`，原样可编辑 |
| 仅操作隔离测试书与去识别化 | **PASS** | 仅操作「测试 TXT」，既有书籍未触碰并已对账 |
| 设备基线参数恢复与回读 | **PASS** | wm density reset 回读 280，参数完全恢复原状 |
