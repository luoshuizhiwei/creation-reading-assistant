````text
# 任务200-r1：纠正启发式正文判定、损坏patch及不实验证
原200同owner，仅外部源码差量继续，预计15min；不写candidate/Gradle/设备/ADB/恢复/globalstate。Root已核200-v1不能接受，不集成：
1. after1089d277的isReaderBodyText = text.contains('\n') || text.length>=50不是正文身份边界。单行短正文会漏；长书名/章节/多行chrome会误入。这是按文本形状筛选，不能声称精准或全部强合同保持。去除50阈值和换行启发式，不能为复现绿而丢合法节点。
2. 实际git apply --numstat原patch2972Bb180610056df607cfa52c12b8a9db13e9ded9e41c326570e45aa17c2034f60eb报error: corrupt patch at line20。verify_task200.verify_patch仅打印hash/长度，直接return biDirectionalOk=True，根本没apply。保留原件和错误声称，不把它当双向通过。
3. verify_199_reproduction手工raw_199_nodes为“测试TXT书/这是前置文本内容”，与199原stdout的“测试TXT甲/这是用于填充段落甲前置文本的内容”不同；marker1手填模拟，T1/T2也是模拟。只能称手工样例，不能称199真实12节点实证。REPORT关于第一章唯一目标的数学唯一性过强，fixture原填充短语也引错。纠正事实，不再复制推测当原文。

只读实际候选基线LIFO28183Ba4bcca37582a69fff9dbcd751e270497e55c3c614d467740b62ac9555988071d与fixture0aea，保留原target/12列/两源/普通重开/正负断言/默认pagedon等全部合同。Root已看到生产feature/reader/pager/PagedReaderPageSurface.kt:479-507 renderFrame只向isCurrent PageLayer暴露accessibleText，非当前为null；515及568的外层正文容器有contentDescription="分页正文已就绪"。继续核PageLayer实际语义、其与header/footer的树层级，优先使用已经存在且能证明的正文容器祖先/子节点关系及unmerged/merged语义选择，只收真实当前正文；不得凭长度/换行/坐标猜。若现有语义不足以唯一识别，准确报告缺口和最小必要产品语义接口建议，先停待Root裁决，不擅自改产品源码或加平行框架。

最少源码+准确验证闭环：证明短单行正文也保留、长/多行chrome仍排除、邻页非当前不入、T1/T2正文和S0 marker不会因过滤为空假绿。只做与真实接缝有关的离线证据；未Compose执行就明确NOT_RUN，不用Python自写筛选模拟当运行验证。原199stdout只读绝对路径按原task199ready链接，引用须准确，并区分观察和推断。
有效标准unified diff，不能手写错hunk计数；用git diff --no-index或正确生成器生成，实际git apply --check和正反应用到本任务小临时单文件目录比较before/after全SHA，保留原rc。允许复制必要单before/after源码作验证，禁止复制工程。脚本必须实际检查失败退出，不硬编码true；括号计数不是Kotlin编译通过。

唯一新根E:/creation-reading-assistant/task200-lifo-body-evidence-source-v1/recheck-r1/，ready=根ready.json；旧200所有原件不覆盖删移。交最小after/patch/REPORT/pins(无自引用)/必要真实原rc日志，source/patch/report/ready<=128KiB，raw<=64KiB，实际分类和时间。无合法编译环境就明确待Root集成编译，不扩任务。禁止candidate/Gradle/APK/设备/hostconsumer改动、业务重跑、Git暂存提交push发布、删除移动证据、云正式库私库sessionDB/.codex/memories/自动记忆。用户stopEsc权限所有权冲突停对应工作。
末尾独立一行：这是任务200-r1。
````
