# A/B/C 工件 v2 核查

## 任务编号/版本

CRA-D2-DEVICE-ARTIFACTS-REPAIR-v2、CRA-J1-REAL-READER-ARTIFACTS-REPAIR-v2、CRA-JOINT-RECOVERY-ARTIFACTS-REPAIR-v2。2026-10-08，三份报告已交付3/3，工件验收0/3；不代表已执行设备失败。设备仍NOT_RUN。

## 输入指纹

Root证据：`E:/creation-reading-assistant/abc-artifacts-codex-review-20261008/fingerprints.json`。
原v1 Android987/987、acceptance24/24内容保持，无源码漂移。
上轮提示词将不同JSON封装清单混淆：freeze/candidate-android-sha256.json实际SHA为fe00810694919e9d80e3661b154814afd681d44239743e2c52943bdeaed76081；candidate/freeze/source-sha256.json为728dff4b05e7660e7939888b323d1a677e07e3bed4db9b6db199157c0ff0d243。同987条内容，不把包装差异算源码漂移；下一提示使用准确路径及SHA。

- A清单4/4代码匹配，manifest SHA 81a2224f3baad752402246df6414507eed4935cd4e8806962de9b047fe9d8431。
- B清单10项中9匹配；清单记录自身旧SHA形成自引用漂移。实际manifest SHA d32c6a56b6a0b58534af253b80326327d00e4a3d5a3b1e3785d357f81c36d910。
- C2258证据项全部匹配；manifest SHA 1a79d659df9b9e2f292b837053fc4652556b67bc0c64ebf23d7f7814f9d0a3f2。
- A/B/C报告SHA分别72c3ce8305658939a6fc71a8385d1e82825bcab307ba1521a969f53a96a32986、ceb97b4bb747fe26bd778f14a378d7624f80aa1fc0387dccc76d61c8813eaaf6、bc21d3f00837e01795f28d8c114affaf946b9dde1b0fefc607ad8b668307836b。

## 所有权与改动

A/B/C各有独立E根；原件保留，下一v3只修分配工件，不改产品。公共合流/冻结/设备仍C唯一owner且当前等待。Root只核查与维护本报告/current。D HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空。

## Standards

模型为执行者自报：A/C Antigravity Gemini 3.8 Flash (High)，B记录Gemini/DeepSeek无法确定单一实际模型；不把上轮建议模型当实际执行模型。GLM5.2仍不可用。新轮建议A GLM5.3Flash、B/C DeepSeek，但按当前可用性记录事实，不切节点。上轮编译后置门禁调整：A/B需独立E定向仪器Kotlin编译，原产品JVM不重跑；C需真实producer/consumer格式及失败路径离线接缝。

## Spec

### A：保留真实数据库路径断言、七场景重开和真实EPUB API，修五项

1. D2PersistenceContract.kt:135–139调用不存在DAO：content getById应对照getByBook；file getByBookId应对照getByBook；note/highlight没有getByBook。不得改生产DAO迁就测试。
2. preferencesDataStore delegate缓存首次实例，每方法换Context/root无法隔离且旧root被删。需实际文件归属与生命周期/单方法进程合同，不能反射重置或删宿主DataStore。
3. evidenceList只有add，未输出；toReadableMap未完整序列化所有字段。before/after/reopened完整行实际落盘/结果输出，失败保全，证据不能随root删除。
4. SAME/refresh只核部分允许变化与保持字段；补完整差分、非默认种子及来源实际size/modified/hash。
5. Kotlin ZIP生成器未固定entry时间，seed与selected重建跨时间窗MD5不同；复制同冻结seed或固定元信息，验证实际Kotlin字节而非Python另造ZIP。

### B：真实App/Hilt方向保留，编译和导航链尚未成立

Fixtures非suspend直接调用suspend DAO；LocatorCodec.progressFromLocator不存在，SearchTermDao只有upsertAll没有upsert；Journey缺JSONObject import。实体字段应按真实CatalogEntities核对，不能把实际存在字段误列缺陷。

View.findNavController从Compose根获取controller与生产rememberNavController没有View绑定不匹配。直接生成route/nav.navigate亦未实际执行搜索结果click。需真实UI生产入口及可观察ReaderRoute加载，不能声称helper调用等于搜索UI。

Fixtures:100先建目录，112清理删除，116写文件前未重建，正常启动会失败。Activity rule在@Before之前启动，引导设定来不及；写onboarding_completed而生产读取app_prefs/onboarded_v1。固定ID/目录、LIKE批量删除与吞错清理没有run归属。

完整进度证据须包括返回后及落点，不能只核书籍marker；assertExactEquals实际只比较12列中的4列，返回后没有新位移/合法保存推进断言。需区分普通A离开时合法最终保存和随后的临时冻结；失败场景waitForIdle后立即Back不能证明异步加载失败，未确定性触发时降为pending取消并如实未覆盖。方法清单区分纯route逻辑与真实旅程，加入同书不同locator真实落点。manifest排除自身。

### C：离线20绿不能覆盖实际接缝，设备前阻断

forward三元组方向成立；reverse真实输出首列(reverse)并非设备serial，现过滤会忽略本设备映射，需按命令类型解析并绑定-s设备。

run_restore用out_path.parent.parent查ledger；真实run目录/restore.json会误取上级。真实callback账为字符串数组，新清理器只接受dict entries，两端不通。纯内存反例显示近似OTHER-TASK/run-extra也被接受，需严格task/run/step归属及producer→consumer测试。

取证门未强制force-stop成功、数据库ls失败可视作空；纯内存反例force_stop非零+ls非零仍ok=True、零DB备份。须真实静止、全部约定持久文件、APK实际备份与失败停止。补正：当前代码已遍历枚举项，不按.db后缀过滤；不将无扩展名库遗漏当现有缺陷。当前缺口是枚举失败放行及未核拉回SHA/尺寸完整性。

联合生成器只接受list而实际A/B methods为带methods对象，缺fixture/source_manifest等prepare_binding所需字段，TXT task_contract_ref不被checker消费，新task_id使子合同不匹配。旧APK常量不能绑定新测试APK；validator需消费实际新冻结，未绑定/无入口拒绝。

protected清单漏旧qahost.test，不能证明原APK对；正式包当前递归data_manifest与正式书库不读边界不符。正式包仅允许批准安装身份元数据，原隔离qahost数据摘要按已许可范围，原宿主/测试包成对记录安装或缺席状态。hostVM约定状态仍待具体入口。

旧provenance漂移原因只有SHA差异，不能断言已证写入竞态；修生成顺序可采纳为防护，原因仍未证。

## 环境

Root本机读取/SHA和三位独立只读审查。未构建、未设备。C反例纯内存FakeAdb，不调用adb；静态符号错误未冒充已运行编译。

## 执行与结果

指纹如上。读取外部原日志，C原20专项、46合成、18严格字段绿保留原范围；A/B静态关键词绿保留为工件文本检查，不算编译或行为通过。复用197 JVM与原APK身份；不为交接全量重跑。

## 原始证据

原A/B/C REPORT、logs、manifest，以及Root fingerprints.json和独立审查结果。需要v3增加真实Kotlin编译原日志、实际生成的机器证据检查、真实配置/账本接缝与负向失败门。

## 恢复与副作用

无设备操作；无设备恢复PASS。原件保留，Root仅E核查文件及D交接文档写入。没有产品回灌或Git操作授权。

## 未覆盖项

合流仪器编译、新测试APK冻结、限定MuMu、真实TXT/D2/J1旅程及完整恢复未完成。X整机中断仍未证；正式书库/桌面/发布范围不变。全产品分母未核。

## 交付状态与下一步

三份v2报告交付3/3，验收0/3。A/B/C独立v3定点返修可并行；A/B各自定向仪器编译，不构建APK，C仅离线。Root核通过后才给唯一owner合流/新冻结提示词；新冻结再核后才给MuMu。保留目标、产品与充分证据，调整工件验证门，避免关键词假绿反复交付。
