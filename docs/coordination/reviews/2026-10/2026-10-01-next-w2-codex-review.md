# W2交付核查、构建里程碑复盘与续执行（2026-10-01）

## 任务编号/版本

Codex-review-W2-v1；来源：用户“它们都完成了”。本轮W2两个交付中构建1/2成立；设备报告与UI证据未找到，不能判W2整体完成。构建窗口报告自称CodeArts，实际模型未现场核查，不把原分配ZCode/GLM写成已证事实。

## 输入指纹

- 恢复仓库HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空。
- J1-v3与W2各875源码，唯一变化为app/build.gradle.kts中的qahost后缀；字节单次替换精确相等，875项冻结SHA全部实算匹配。
- W2 APK SHA `0221d88fb34e8c88e274f09b939c5f1b84f20d9c2674abb7c8588e6593c71594`，包名`com.creationreadingassistant.qahostwebdav`，与[READY](<E:/creation-reading-assistant/next-round-w2-webdav-ui-build/READY.json>)一致。

## 所有权与改动

Codex仅写本报告、current/项目页/TODO/总规划现态、skill记录及E审计工件。外部候选、READY、设备基线及脚本保持原件，不修改产品、暂存或提交。[入口回退副本](<E:/creation-reading-assistant/w2-codex-review-20261001/before>)。

## 环境

Windows；Python哈希/清单核对，SDK34 aapt2、Java17/apksigner对冻结APK现场检查。零ADB/设备操作，不重跑构建、JVM、Room或服务自检。资料检查仅限两个W2目录及约定报告位置，不搜索书库正文。

## 执行与结果

### Standards

构建交付通过：源码树875/875同集合，仅一个允许hunk；真实App/MainActivity、三provider authority及权限集合符合派生包合同。最终Manifest仅新包名归一化后与父APK完全相等。apksigner verify现场exit 0、证书SHA与父合同一致。构建日志BUILD SUCCESSFUL，归档exit 0；本轮未重复构建。报告所称Gradle cache“只读”不作为事实采用，Gradle通常会维护缓存；不为该措辞另开报告返修。

### Spec

W2-build-v1满足独立宿主构建合同。W2-accept-v1未满足可核查交付：恢复仓库约定报告不存在；其E目录evidence和logs文件数均为0。只有冷停基线、服务/夹具副本和两个准备脚本。不能从原APK/基线或BUILD_READY推导七UI项通过，也不能据文件缺失断言窗口绝对未执行；实际运行状态未通过界面确认。

准备物核查：旧qahost71文件的记录SHA与J2-v4恢复终点相同；旧APK对、七系统设置及空reverse/forward有记录。服务、ZIP、JSON三SHA与W1-v2一致。它们仅支持前置准备，不是本批测试后恢复证据。

## 原始证据

- [构建窗口报告](../../dispatches/2026-10/2026-10-01-next-w2-build-webdav-ui-sandbox.md)、[源码清单](<E:/creation-reading-assistant/next-round-w2-webdav-ui-build/freeze/source-sha256.csv>)、[冻结APK](<E:/creation-reading-assistant/next-round-w2-webdav-ui-build/freeze/apk/app-qahost.apk>)。
- [Codex独立核查JSON](<E:/creation-reading-assistant/w2-codex-review-20261001/verification.json>)、[现场Manifest](<E:/creation-reading-assistant/w2-codex-review-20261001/apk-manifest.txt>)、[现场签名](<E:/creation-reading-assistant/w2-codex-review-20261001/apk-signature.txt>)。
- [设备窗口已有准备目录](<E:/creation-reading-assistant/next-round-w2-webdav-ui-acceptance>)、[前置摘要](<E:/creation-reading-assistant/next-round-w2-webdav-ui-acceptance/baseline/pre_test_summary.json>)。
- [产品总规划](../../../plans/2026-10-01-product-roadmap-realignment.md)、[当前执行入口](../../../handoff/current.md)。

## 未覆盖项

七WebDAV UI/请求/有效导入及本批恢复未获得执行证据；旧KeyStore、正式书库、实体硬件/MIUI、实际桌面LAN、N1中断安全、R8/签名升级/F保持原边界。构建与APK校验不替代运行期验收。

## 恢复与副作用

本轮Codex零设备操作；未改原仓库或Claude库，未改外部工件。保留设备原准备基线，不重新采集覆盖它。TODO无关项目和skill历史原文保持。新UI包是否已存在、服务是否已运行、设备是否仍被占用须由原设备owner续执行前核查，不能盲目再次安装/启动。

## 交付状态与下一步

### 构建里程碑复盘

- 保留：整体目标及W2七用例合同；新包隔离方案成立，不再派构建窗口重建同一包。
- 调整：前置准备和最终交付必须分别回报。等待READY只能记WAITING/部分交付，不能让“完成了”被理解为完整验收；依赖现在已被Codex核准，设备窗口接续原任务，不重做准备。
- 关键路径：设备运行状态核查→续完W2证据/恢复→Codex M1联合里程碑复盘→候选收口→N1数据安全。当前M1仍未退出，不提前提交。
- 可并行：空出的构建窗口做M0-v1定向需求/当前版本/证据映射，唯一写自己的E CSV与仓库报告；不改产品、公共账本、设备或HOLD。先去重已交付旅程，再给必要验收缺口和可核算分母；180资产行、122HOLD、测试数均不得代替产品分母。
- 进度：W2两个交付1/2=50%；联合构建+完整MuMu仍1/2=50%；Room既有4/4=100%，A–E恢复5/5=100%。七UI项目前无可核查通过证据，不判七项产品失败；全产品分母仍待M0归属。
- 此次只给用户续执行与并行任务提示词，未向外部应用发送消息或启动自动任务。
