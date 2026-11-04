# 任务118-v1 Root核验

## 任务编号/版本
118-v1，只读搜索回源旅程范围及补充1。2026-10-24。Root接受接线与证据缺口梳理，不是UI验收。

## 输入指纹
唯一Android工作区 E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。
Root实核SearchViewModel.kt 13181B SHA4549c555d8e79c0987b1fc5f937168cc358e6ab2f11970991cb9cf31aec30b96；SearchScreen.kt 43228B SHA72cc0d1a6900c2b8aed423b8a3e2ff05ef22ce185c436df89420f3983625ecba。117冻结产品6a、test83d、仪器a64引用既有交付，不复制重算APK。

## 所有权与改动
ZCode官方Start Plan GLM-5.3-Flash只写E任务118四份报告；Root写D协调/current。无源码、仪器、host、配置改动；零构建设备ADB。原报告与ready保留，补充仅覆盖冲突语义。没有第二条有实际价值且不重复的独立审计任务，不为并行数量增审。

## 环境
本批离线只读，不引用旧serial为新设备许可。GLM-5.3当前界面额度用尽；Flash只读可用。117完整恢复证据复用，不再设备查询。

## 执行与结果
全局Home搜索路由search有正文hit/临时导航；Shelf的shelf/search是元数据搜索，不能混入口。SearchScreen:174查询变化自动搜索，:253键盘搜索键只记历史；SearchVM同词去重且失败也记last，没有专用重试按钮。改变或清空查询可重新触发，未据此推导新增重试功能。
正文hit点击才解析位置并以TEMPORARY导航；普通书籍卡NORMAL路径不能冒充无可靠位置snackbar负例。主链应真实点击正文Tab、唯一正文hit、真实临时阅读落点、返回全局搜索保持词/tab/list，并核同fixture持久reading_progress前后相同；stack空不能替代持久位置不污染断言。Tab语义唯一性和落点观测尚待实际仪器与设备证明，不能称现已可完全执行或通过。
生产DAO书籍加真实正文、生产单书索引入口可作为fixture层级，不能直接seed search_terms冒充索引。indexSingleBook不取得全库sweep执行权；101写点/删除互斥不扩成所有并发安全。

Root额外纠正补充的行号：当前SHA4549源码delay(300)在157，ensureActive在213，lastSearchedQuery在216，buildNotice起226；补充的160/214/222并不准确。SearchScreen正文label实际290；补充292-297为邻近hunk口径，不能当精确行号。补充未列所要求完整SHA，Root在本报告补齐上述两个实际使用源码指纹，不再为行号往返派报告任务。

## 原始证据
E:/creation-reading-assistant/task118-search-ui-journey-scope-v1/：
- REPORT.md 10924B SHAab031b2a59976331fafb0706b0c3f6adc56b6278daad407517a60ef89f1c2655。
- ready.json 6324B SHAfd5df8a243487ac8568e4a4524a7ef54b1f34a9db4521aa9f94b32ced5baa0bc。
- CORRECTIONS.md 5937B SHA6446b794a5e6ac56247f643856c6260288f7768c4164eaf7bca3b94a9f6a7bc7。
- ready-corrections.json 4934B SHA7d944a70aa4f085a85f4298e010760a9fbd807df2d3b0840afc5ceade7b33d57。
实际总28119B，非补充估算26000B；≤1MiB。仅必要文字报告，无build/APK/缓存副本。ZCode最终回答已结束，补充新件与原件指纹已核。

## 未覆盖项
97已有22JVM不替代本搜索UI链；118不新增任何测试通过声明。无真实错误重试/缺定位负例/阅读器发起再恢复原位置/实体手机覆盖。117/111/107充分证据不复跑。全产品分母未知。

## 恢复与副作用
本批无设备操作，没有新设备恢复结果；复用117 Root核验，不造新恢复收据。不Git、云上传、私库含.codex/memories、存量清理。历史night writer未知保持。

## 交付状态与下一步
118只读范围收口。方向自审：产品下一真实缺口是搜索正文回源临时查阅与持久位置保护的UI闭环；不是再写确认报告。仪器实现、唯一构建和设备数据保护应同一GLM-5.3 owner闭环；当前指定关键模型额度不可用，不擅用Flash替代，不换账号购买。没有确证新生产缺陷可让Flash做小修，也不以资源阻塞为由制造全历史审计。等待用户恢复/指定可用GLM-5.3资源或明确修改关键执行分工；不派119、不停止automation4，2/3保持PAUSED。资源变化后复核owner和必要输入，接同目标，不重复118或117。
