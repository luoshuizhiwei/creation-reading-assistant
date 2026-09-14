# Android R3 第三轮闭环记录（2026-09-14）

## 结论

第三轮中先前标为低置信或阻塞的项目已完成收口。验证只使用真实设备 `c49ac6cf` 上的隔离 debug 包 `com.creationreadingassistant.r3gate`；正式应用包、正式书库和用户书籍均未安装、卸载或写入。

本轮未提交、未推送，也没有改动 desktop/Electron。

## 修复

| 项目 | 处理 | 主工作区文件 |
|---|---|---|
| P1：目录页“智能识别”标签 | 将标签从滚动列表移至顶栏 supporting content，避免它被列表手势层吞掉 | `LibraryBrowserRoute.kt` |
| P2：阅读设置工作表关闭 | 保留 Material 的拖拽/返回行为，并在阅读器工作表把手处增加明确的可点击无障碍控件“关闭当前工作表” | `ReaderSheetHost.kt` |
| 动态视图入口 | 将书架筛选摘要接回既有 `FilterSheet`；筛选管理仍可由“书架整理”入口进入 | `ShelfScreen.kt`、`ShelfRoute.kt` |

## 真机结果

| 验证项 | 结果 | 证据摘要 |
|---|---|---|
| P1 标签切换 | PASS | 根目录已配置时，在顶栏“当前目录/智能识别”之间连续切换 5 次，五次均进入对应页面。 |
| 大目录安全边界 | PASS | 隔离目录共 2,004 个文件，目录页明确提示仅显示前 1,000 项；智能识别显示“结果已截断”，推荐 1 项、候选 499 项。 |
| 大文件可发现 | PASS | 隔离目录中的 5.0 MB TXT 在目录浏览页可见；定向导入器/SAF 扫描 JVM 测试覆盖受限读取、深度及截断策略。 |
| 扫描恢复边界 | PASS | 真机识别在该设备上于首次可截图前完成；停止/恢复的确定性分支由 `SafBookSourceScannerTest`、`SmartBookRecognizerTest` 与 `LibraryBrowserPolicyTest` 覆盖。未把 UI 自动化取树期间的 busy 状态误记为取消成功。 |
| 导入 | PASS | 隔离书库导入 1 份中性测试 TXT，结果为“共解析 1 本，成功入库 1 本”。 |
| P3 整理页返回 | PASS | 从书架进入整理页并按 BACK 返回，连续 5 次均返回同一隔离书架。 |
| 动态视图 | PASS | 选择 TXT 筛选，保存视图；点击“关闭工作表”后书架恢复“全部”；重开工作表套用已保存视图后恢复 TXT 且工作表自动关闭。 |
| P2 阅读器工作表关闭 | PASS（修复后） | 原“关闭工作表”遮罩语义节点可见但点击不关闭，已复现；新增“关闭当前工作表”控件后，在阅读设置页点击即回到正文。 |
| 单处纠错 | PASS | 选中测试正文中的一个重复词，保存单处纠错后，关闭规则页可见仅被选中的一处替换为测试值，另一处相同原文保持不变。 |

## JVM / 构建门禁

所有命令在干净隔离工作树 `D:\develop\Code\Codex\cra-g0-gate\android` 执行，应用 ID 后缀仅用于隔离真机验证。

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest --tests "*ShelfViewModelTest" --tests "*ReaderReplacementCapabilityTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*SafBookSourceScannerTest" --tests "*LibraryBrowserPolicyTest" --tests "*SmartBookRecognizerTest" --tests "*ShelfImporterTest" --tests "*BookDetailStatsPolicyTest"
```

- 编译与隔离 APK 构建：成功。
- 保存视图 / 替换能力：29 测试，0 failure，0 error。
- 扫描 / 识别 / 导入 / 书籍详情策略：55 测试，0 failure，0 error。
- 安装脚本两次均输出 `RESULT: CONFIRM_LOOP`，且 `INSTALL_SCRIPT_EXITCODE=0`；未使用 direct ADB fallback。

## 清理要求

本记录写入后必须卸载 `com.creationreadingassistant.r3gate`，删除唯一隔离设备目录 `/sdcard/Download/cra-r3-final-gate`，并核对正式包 `com.creationreadingassistant` 仍存在。原 stay-awake 值为 `3`，本轮没有修改。

