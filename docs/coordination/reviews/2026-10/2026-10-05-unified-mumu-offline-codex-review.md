# 统一候选设备与离线交付：Codex核查

## 任务编号/版本
CRA-UNIFIED-MUMU-OFFLINE-CODEX-REVIEW-v1；2026-10-05。两窗口已经交付并停止，设备批次未通过。

## 输入指纹
HEAD e39a78da9c62f02614207cffb36969a32492eaea、暂存区空。本轮重算979项设备候选源码全部匹配freeze；相对已核978输入仅app/build.gradle.kts与一个新增仪器类。双APK实算匹配：host b5594784190a3276678fcd58f8e615e605a2504e7f9ef25e2b88d4eab5ca54e4；test e4d0e3ee893aff3204f4f2f8974c2840a06ce02be09bdbe3141c1ba478083e14。

## 所有权与改动
Codex写本报告/current/TODO与独立E证据。未修改产品或历史外部报告。MuMu确认无本批任务进程后，只读包状态并恢复本批基线night yes；没有安装、运行新旅程或写原书库。

## 环境
Windows/Python3.13；MuMu实读serial127.0.0.1:16416；emulator-5556为同设备别名。离线复核TEMP在E盘。

## 执行与结果
### 设备判定补正
- J1 FAIL：原始退出保存offset2553，重进后0；截图正文回到段落1。两轮870/2553记录均保留。保存到DB成立不等于实时位置准确性完整证明；恢复消费链为待验证定位方向，不是已确认根因。
- J2限定行为PASS：原始canonical保存1894、重进读1859，截图段落50。保留同屏恢复偏差35的范围，不宣称逐字符精准还原。
- J3限定行为PASS：冷启截图工具栏/正文/底栏均第2章；DB2277落实读第二章范围2277..4538。原报告写621..4538与实际JSON不符，以原始JSON为准。并不扩大章内locator一致性结论。
- J4限定行为PASS：真实高亮与笔记跳转链截图、JSON相符，回源目标句可见。无offset键消费路径仍仅JVM证据，不冒充设备通过。
- J5合同NOT_RUN：外部仅run-as注入zip-restore-state.json，验证横幅/跨实例读取。此子范围成立，但不能把它改写为实际ZIP恢复中断通过。原4/5=80%修正为合同旅程3PASS/1FAIL/1NOT_RUN，即3/5=60%。
### 恢复缺口
- 本批新双包卸载/缺席已从原始日志和Root本轮成功包列表空输出核实。
- night基线yes、外部收尾no，Root本轮实读no后执行cmd uimode night yes并读回yes，原始命令与退出码落盘。该单项已补恢复，不表示整个批次恢复通过。
- baseline-data.txt标记“databases SHA”但正文只有ls -l；保护数据仅43个计数+少量路径，无完整逐文件哈希；原APK仅路径，不是APK字节前后核验。不能声称“字节一致/全账恢复完成”。
- 基线/data/local/tmp命令遭Git Bash路径转换为D:/develop/Git/Git/data/local/tmp，错误后grep计0，不能当目录干净证据。原基线forward/reverse及全部设置也未形成完整对应。
- 不补造开工SHA，不把事后哈希伪装成本批前账；保留历史恢复证据不足，下一批必须正确采集。
### 仪器断言缺口
umu_12_verifyJ1Reentered只require进度行存在，即使offset=0也输出pass=true；umu_31仅计算inChapter2但不require其为真。当前J1报告人工识别为FAIL没有误关，但仪器绿色不能当旅程通过。下一验收补强断言与真实UI证据。
### 离线交付
GAP-1原驱动未强制runner身份确实存在；任务目录最小补丁增加precheck/run_all门控。Root对该补丁独立运行原离线套件55/55、0失败；外部14场景原始记录全部通过。接受限定离线补丁交付，不把它当设备或新双APK绑定已通过。设备批次未采用该检查器；原980动作行本身带SHA，但其集合不能覆盖全部新输入，实际应使用完整源码清单。新增后缀/仪器类须单独冻结，不能拿原978清单直接套新979树。

## 原始证据
- [设备外部原报告](../../../qa/2026-10/2026-10-05-unified-mumu-accept-v1.md)
- [离线外部原报告](../../dispatches/2026-10/2026-10-05-unified-offline-binding-v1.md)
- [Root源码与APK核验](<E:/creation-reading-assistant/unified-mumu-v1-codex-review-20261005/source-apk-check.json>)
- [Root离线55结果](<E:/creation-reading-assistant/unified-mumu-v1-codex-review-20261005/patched-offline-results.json>)
- [夜间模式补恢复](<E:/creation-reading-assistant/unified-mumu-v1-codex-review-20261005/night-mode-restored.json>)
- [本批包缺席读回](<E:/creation-reading-assistant/unified-mumu-v1-codex-review-20261005/batch-package-absence.json>)
设备原始UI/JSON及baseline/cleanup均在unified-mumu-accept-v1/evidence，保持原件。

## 恢复与副作用
Root只补night yes，未触碰正式库/实体手机/原APK。文档改前副本保全于E本轮before；本轮无Git变更。其他恢复合同证据缺口未关闭。

## 未覆盖项
J1根因与修复、J5真实恢复中断、完整前后恢复哈希和端口设置合同；实体手机/MIUI/发布/桌面/HOLD维持边界。

## 交付状态与下一步
离线任务已验收；设备任务交付但未验收。下一可独立并行：E隔离候选TXT恢复最小定位修复（无ADB）；设备/测试owner用冻结基座补真实J5与严格恢复合同（不改生产），然后单owner绑定TXT修复做J1回归。两条任务不共用源码/设备/缓存。J2–J4原始限定行为不无条件重跑，也不冒充最终修复APK联合通过。新统一合同旅程3/5=60%、整体设备门禁未通过；全产品分母未核。
