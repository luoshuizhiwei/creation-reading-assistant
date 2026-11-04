````text
任务141-B-r1｜2026-10-26香港11:53后｜同Claude桌面WorkBuddy global Flash｜10分钟定点修草稿，不集成/不构建
Root已读v1实际SearchScreen 3hunk与完整新instrumented测试，5交付sizeSHA核同。方向可复用，但以下实际缺口未闭，不能接设备或宣称测试ready。142原A仍唯一reader源码/Gradleowner，你继续仅交付目录差量，与其独立并行。

只修这五处具体问题，原v1保留，不重审充分部分：
1. buildBody确定性错误：queryOffset=sb.length 后 append("唯一检索词 $QUERY...")，QUERY实际位于附加前缀后，check(sb.indexOf(QUERY)==queryOffset)必失败，发生setUp已有onboarding/目录写后。请按真实token起点构造或独立查找校验，禁止抄actual自证/硬编码字符差；各25本文本唯一QUERY/marker与合法offset校验明确，定位global/chapter若不需断言不伪称已证。必要纯离线生成核查要说明是否实际Kotlin/等价模拟，不冒充instrumented运行。
2. Back控制：readerChromeBackCount>0就先Back+Thread.sleep500再无条件Back，首Back若已到search会把search也pop。去任意sleep和盲第二Back，按可观察search恢复/临时会话状态和真正chrome状态等待，首Back已返回则不得再发；如需第二Back必须先明确仍在reader且只有chrome被收起，未判明停失败。保持真实Back/合法结束会话，不能直接改协调器或导航伪造。
3. await当前runCatching(block).getOrDefault(false)以及catch(Throwable)吞所有观测异常，语义key缺失/解析错误会被伪装超时。改为直接断言/安全node计数后读取；仅预期的Compose等待超时可定向捕获且保留原始原因，其它异常交外层first_failure记录重抛。无宽catch静默默认，不降低missing snapshot/key的失败语义。
4. 实际12列证据缺失：fixture仅bookIds，back仅progressRowsUnchanged=true。须在fixture与back落盘真实bookId→12列值快照（25本完整归属）供未来host逐值比较，nullable保留JSON null/类型正确；不能用布尔代完整值。不改helper/原F1，用已有ProgressRowSnapshot公开字段最小序列化，不新框架。中性测试TXT命名使用“测试 TXT”前缀，非产品书名。
5. 首个partially visible item可能center已在clip外，节点存在/hasClickAction不代表点击可达。保持pre.topItemKey精确比较，但点击目标可从当下真实可见且点击中心位于列表可视bounds内的本轮卡选择，不必强等topItemKey；选择后严禁滚动改变已记录pre。明确targetCardKey/book归属和真实可点击中心断言，不能假定sort或index对应书，也不能增加任意兜底循环。若无法纯观察选中合适目标，定点说明停止而非放宽。

只写E:/creation-reading-assistant/task141-offline-implementation-v1/lane-b/recheck-r1/：修改后新test源码、最小相对v1修正patch及最终new-file patch/REPORT/ready。未变SearchScreen seam引用原80181fa4/37862c93即可，不复制。共享candidate/SearchScreen仍72cc0d1a不动，A正在改reader不当作本lane输入漂移。旧v1 test28055B SHA33d61f133da4d1af862a0139dd6819e88f57798d8da7ba27af1ee68c6c3ccaae原件保留。差量<=256KiB/raw<=64KiB，不复制工程/cache/APK/旧历史原件。

不Gradle/compile/assemble/JVM/lint，不ADB/VM/安装卸载/设置/主入口attempt/源码candidate修改；不修142/reader，不Git云私库自动记忆。不增加host/inputs或APK。未来main seam须新产品APK构建冻结、旧74d106b6不含接缝等已确认依赖保持。交付仍明确未编译未运行；Root读具体修正后再决定集成时序，不能自动接后批。最终单独一行这是任务141-B-r1。
````