# 118-v1 只读补充：收紧实际旅程

编号：118-v1 补充1；时间：2026-10-24；模型：官方 Start Plan GLM-5.3-Flash；作用：纠正已交报告的具体歧义；关系：原owner完成当前收尾后接续，零设备构建，不另开确认任务。

````text
接续118-v1，仅修正本批只读交付的准确性，不开119、不改代码/仪器/host、不构建/设备/ADB。先完成当前回答，再处理这条补充。Root已读实际SearchScreen/SearchViewModel，发现几处方案不能直接执行，请在本目录新增CORRECTIONS.md和ready-corrections.json（合计目标10KB，整个118仍≤1MiB），保留原REPORT/ready历史原件不覆盖。不要重扫工程。

1. SearchScreen:174 LaunchedEffect(query)自动触发；:253 KeyboardActions.onSearch只addHistory。SearchVM:147同query去重、:216即使CONTENT_SEARCH_FAILED也lastSearchedQuery=query。因此不能说键盘提交执行重搜或“retry=re-search”；现无专用retry按钮，明确用户现可通过改变/清空查询重新触发。这只是现状与产品取舍，未授权新增重试功能，不自动定缺陷。
2. 原可选步骤“元数据命中卡→无可靠正文位置snackbar”不成立：普通书卡走NORMAL reader。删除此可选步骤；若无可靠定位必须实际正文hit且resolvePreciseOffset=null，现批无充分fixture不要造该覆盖。
3. 不用颜色作唯一选择器、不直接写tabState作为UI点击替代、不因all难定位就临时fallback。提出真实点击正文Tab及可用文本/语义父子关系唯一定位，若实际没有可观察语义明确未定，不造稳定selector。
4. 单书indexSingleBook:982的外层不调用tryAcquireIncrementalSweep。只核这条必要调用链/101已核写点互斥事实，纠正“单书入口在tryAcquireIncrementalSweep排队”混淆，避免把任务101扩大成已证所有竞争安全。
5. 给正确关键行号和当前必要源码完整SHA，纠正原SearchVM132-197/199等过时编号；返回范围是返回全局搜索保持query/tab/list，不能冒充从现有阅读器发起并恢复原持久位置。临时查阅不污染reading_progress若要验，应读取同一本fixture前后实际持久行；不能只用stack空代替。不实施测试。

补充只保留一条确定可执行主链与尚待关键模型/设备owner裁决的具体观测点，明确当前Flash只读准备不等于UI已验。GLM5.3额度不可用，不自行换账号/购买/用Flash启动关键设备恢复。107/111/117不复跑，冻结a64仪器/83d test/6a产品；私库含.codex/memories禁读写，D协调/current不写，不云上传、不Git、不存量操作。完成交Root，最后单独一行：这是任务118-v1。
````

实际交接：官方Start Plan GLM-5.3-Flash，完整新补充消息与工作中11秒/停止生成截图已核；原报告保留，等补充原件。
