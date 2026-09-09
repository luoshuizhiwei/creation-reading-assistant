# 发给 WorkBuddy：R1 独立验收（只验不修）

工作目录 `D:/develop/Code/Codex/creation-reading-assistant`。先完整读本目录README/COMMON和三方reports。
只在Codex明确交付冻结集成快照后启动完整验收；三方仍在开发或缺报告时说明依赖，不能验旧APK后宣布新功能PASS。
不修改产品源码/测试/脚本，不stage/commit/push/reset/clean/stash。报告写 `docs/plans/android-parallel-delivery-2026-09-09/reports/workbuddy-r1.md`。

## 证据基线

记录branch/HEAD、所有权内diff、未跟踪产品文件；核对APK来自该快照，记录SHA-256和安装结果。既有测试输出仅是参考，缺证据时重跑缺失门禁，不重复有可信当前证据的昂贵步骤。
测试命令在android目录：`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin`；必要仪器测试另构建安装test APK。记录命令、退出码、tests/failures/errors/skipped及Lint条目。

## 设备安装授权（必须遵守）

先adb devices核对真实serial；仅真实手机，不用MuMu，每条命令 `adb -s <serial>`。
先 `android/scripts/install_with_confirm.ps1 -Serial <serial> -Apk <absolute-apk>`。
**用户已明确授权：脚本超时或MIUI拦截后，保留失败日志，允许 `adb -s <serial> install -r <absolute-apk>` 回退；测试APK需要时 `install -r -t`。**
不要因为脚本失败就放弃功能验收，也不能将回退写成脚本PASS。不要求用户手动点MIUI，不改安全设置，不卸载、不清数据。
设备只由你占用；记录设置和阅读位置，结束恢复。用中性测试书名；测试创建的数据标识明确，删除只针对自己创建的资料。删除/撤销场景使用独立中性fixture书，不能拿用户原书做破坏性测试。

## 验收矩阵

### A. N1统一笔记
- 高亮/批注/书签在“我的”和书内入口一致；筛选/清除筛选/空态正确。
- 编辑、改色、删除、撤销、批量导出、取消导出；原文与个人批注分开。
- 精确回到选中记录；无locator旧记录有合理降级；重启仍可管理。
- 章节顺序与目录一致，长文本/大字体/深浅色/横屏无截断。

### B. D1删除恢复
- 导入中性fixture，添加分类/标签/书单/已读记录/笔记/高亮；事先软删除一条记录。
- 删除→撤销后核对本次影响全部恢复，事先删除项不复活；不能只看封面。
- 批量、连续删除、重复撤销、窗口过期；必要数据库核对只读且报告不包含正文/真实书名。
- 不支持跨重启撤销时界面不继续给出有效承诺。

### C. Reader
- TXT分页兼容关闭进入此前失败滚动路径：字面规则预览命中→保存→正文实际显示替换。
- 滚离返回、重进、进程重启仍显示；禁用还原，长度变更后搜索/选区/高亮/TTS定位正常。
- 小TXT/流式TXT及超限降级；旧分页TXT/EPUB回归，Markdown不越过能力边界。
- 普通汉字、大字号、窄窗口、数字/英文/破折号/省略号行尾；段落末行自然、不拆原子单元、不裁字。
- 模式切换/旋转/恢复不把阅读位置重置开篇；无FATAL/ANR。

结果逐项PASS/FAIL/BLOCKED，并列未覆盖项、截图/录屏、设备副作用和恢复结果。预览命中、入口可点、JVM通过都不能代替正文实际替换证据。
若Codex预约的是日志诊断轮，仅收集同一失败路径的ScrollReplaceTrace与截图，归还设备时段，不擅自扩展完整矩阵。
