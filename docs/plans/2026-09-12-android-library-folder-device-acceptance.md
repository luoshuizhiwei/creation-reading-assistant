# 应用内书籍目录与智能识别 —— 真机验收 Runbook

> 日期：2026-09-12。适用：`android/`（独立原生 Android）。
> 依据：[`../2026-09-12-android-library-folder-smart-recognition-roadmap.md`](../2026-09-12-android-library-folder-smart-recognition-roadmap.md) §11.2、§11.3；
> 交付状态见 [`../android-parallel-delivery-2026-09-09/reports/workbuddy-r4-library-folder.md`](../android-parallel-delivery-2026-09-09/reports/workbuddy-r4-library-folder.md)。
> 本文是**执行手册**，不是验收结论。未执行的条目不得写成 PASS。

## 0. 前置条件

- [ ] 开发侧门禁全绿：`compileDebugKotlin` / `testDebugUnitTest` / `compileDebugAndroidTestKotlin` / `lintDebug`。
- [ ] 若含第 3 组改动：Room 新版本**必须有** `android/app/schemas/com.creationreadingassistant.data.local.AppDatabase/<version>.json`
      快照，且 `AppDatabaseMigrationTest` 已覆盖旧→新迁移（真机或 instrumentation）。
- [ ] **已取得用户的设备时段**（真机验收独占设备，夜间/长测需先问）；未取得则本文只做准备，不执行。
- [ ] 工作区无他人正在写入的 Kotlin 文件：`find android/app/src -name "*.kt" -newermt "-3 minutes"` 应为空。

## 1. 设备与命令约定

| 项 | 值 |
|---|---|
| 设备 serial | `c49ac6cf`（每次先 `adb devices` 复核，不符就停下报告） |
| adb 路径 | `D:/develop/Android/Sdk/platform-tools/` |
| 模拟器 | **禁用 MuMu**；只用已连接真机 |
| 命令形式 | 所有设备命令显式 `-s c49ac6cf` |

## 2. 构建与安装

```bash
cd android
GRADLE_USER_HOME=D:/develop/env/gradle JAVA_HOME=D:/develop/Java/jdk-17.0.14 \
  "$JAVA_HOME/bin/java" -Dorg.gradle.appname=gradlew \
  -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
  :app:assembleDebug --no-daemon
```

安装顺序（方案 §11.2 要求）：

1. 优先 `android/scripts/install_with_confirm.ps1`；
2. 脚本实际超时或被 MIUI 阻断时，**先保存失败证据**（截图/日志），再允许 `adb -s c49ac6cf install -r <apk>` 继续功能验收；
3. 直接 ADB 只是功能测试回退，**不得写成「安装脚本 PASS」**。

MIUI 已知拦截：`adb install` 报 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user` 是「USB 安装」开关所致，
`adb_install_need_confirm` 类键值绕不过。可行手法：`uiautomator dump` 抓弹窗 → 只点 **clickable=true 的 Button**
（`继续安装`，resource-id `android:id/button2`）→ `input tap <x> <y>`。
⚠️ 匹配时必须过滤掉标题节点（含「安装」二字但不可点击），否则点中对话框底层、5 秒后自动拒绝。

## 3. 现场保护（必做）

- 长测前记录 `adb -s c49ac6cf shell settings get global stay_on_while_plugged_in`，结束**必须恢复原值**。
- 不篡改设备上用户的真实书库；不新增/删除用户文件；测试素材用完即删。
- 所有结束操作后确认 0 临时文件残留。
- **报告只用中性名称**：「测试 TXT」「测试 EPUB」「测试目录」，不记录真实书名。

## 4. 验收清单

判定列只写 `PASS / FAIL / 阻塞 / 未验`。**未验不等于 PASS。**

### 4.1 第 1–2 组（WorkBuddy 已交付）

| # | 项 | 操作 | 期望 |
|---|---|---|---|
| 1 | 首次授权 | 书架 → 添加到书架 → 我的书籍目录 → 选目录 | 目录树授权后回到 App 内浏览根目录，显示名正确 |
| 2 | 首次授权取消 | 系统选择器里取消 | 不落库；回到「尚未设置我的书籍目录」；无崩溃 |
| 3 | 重新授权 | 撤销授权/清数据后进目录页 | 显示「无法访问原书籍目录」，提供重新授权；**书架内已导入书籍不受影响** |
| 4 | 重启恢复 | 杀进程后重进 | 根目录与上次浏览目录恢复（首期为 root 直接子目录） |
| 5 | 当前目录浏览 | 逐级进入 | 目录优先排序；子目录/支持格式可见；其他文件不列入 |
| 6 | 面包屑 | 点各段 / 系统返回 | 跳转正确；当前段不可点；返回语义与面包屑一致 |
| 7 | 搜索 | 输入文件名片段 | 只过滤文件行；目录不受影响 |
| 8 | 排序 | 名称 / 修改时间 / 大小 | 立即重排并记忆（重进保持） |
| 9 | 刷新 | 点顶部刷新 | 重读当前目录；加载态可见 |
| 10 | 多选与批量 | 单击切换 / 长按 / 全选 / 反选 / 取消 | 底栏计数正确；**不出现删除/移动/重命名来源文件入口** |
| 11 | 批量加入书架 | 勾选后加入 | 跳回导入页显示进度；完成后书架出现对应书；来源原文件仍在 |
| 12 | 智能识别 | 切到「智能识别」 | 进度「已检查 X / Y」可见；停止按钮可用 |
| 13 | 停止扫描 | 扫描中点停止 | 不再新增候选；页面可返回/滚动；无 ANR |
| 14 | 大目录截断 | 用大目录（>1000 项或 >2000 文件） | 明确提示截断而非假装完整；不 OOM、不假死 |
| 15 | 推荐/重复/失败 | 测试 TXT、测试 EPUB、损坏文件、重复文件 | 分档正确；**每条都显示识别依据**；推荐默认勾选 |
| 16 | 部分成功 | 混合批量导入 | 显示「X 本成功，Y 本失败」，失败项可查看原因 |
| 17 | 删除书架项 | 删一本已导入的书 | **来源目录原文件仍存在** |
| 18 | 来源失效 | 撤销目录授权后 | 已导入书仍可阅读、搜索、TTS（内部副本是事实源） |

### 4.2 第 3 组（zcode 交付后追加）

| # | 项 | 期望 |
|---|---|---|
| 19 | 精确已入架 | 同一来源再次扫描 → 显示「已在书架」（不再是「疑似」），默认不勾选 |
| 20 | 移动/改名 | 同内容文件改名后 → 通过内容哈希识别为同一来源 |
| 21 | 内容有更新 | 来源相同但大小/时间/哈希变化 → 显示「内容有更新」 |
| 22 | 超大文件 | >32 MiB 文件用候选指纹去重；覆盖原书前仍完整校验 |
| 23 | 迁移 | 升级安装（旧版→新版）后旧数据完好，`AppDatabaseMigrationTest` 通过 |
| 24 | 无轮询 | 静置观察：不刷新页面时无周期性目录读取（可用 `dumpsys` / 日志佐证） |

### 4.3 性能门槛（方案 §11.3）

- 目录首屏**不等**完整递归扫描；
- 扫描中页面可滚动、可返回、可取消；
- 内容探测并发 ≤ 2（日志/trace 佐证，不靠肉眼）；
- 停止后不再新增候选、不再打开新输入流；
- 扫描耗时与导入耗时**分别统计**，不得把扫描完成报成导入完成。

## 5. 报告

路径：`docs/plans/android-parallel-delivery-2026-09-09/reports/workbuddy-r4-library-folder-device.md`

必须包含：设备 serial 与 Android 版本、`stay_on_while_plugged_in` 原值与恢复结果、逐条清单判定、
缺陷（附 logcat 与复现步骤）、临时文件清理确认、明确列出**未验项**。真实书名不得出现。
