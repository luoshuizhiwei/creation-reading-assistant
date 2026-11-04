# J1 v5核查与Root两点补齐

## 任务编号/版本

CRA-J1-REAL-READER-ARTIFACTS-REPAIR-v5。本轮用户口头完成后，Root读取实际报告、源码、编译副本和原日志，独立UI reviewer只读核查。下一只续B v6退出保存/分页前置合同；A/C已验输入不重做。

## 输入指纹

E:/creation-reading-assistant/j1-real-reader-artifacts-v5/patches/manifest-sha256.json 实算 d871dc4fc940aec1c80f8022bdd71226cb7339af373c9eb7f010d103c3ead36e。交付清单9/9 SHA与大小匹配，src三Kotlin与保留candidate_build/android对应3/3一致。原编译日志766e8365eba6b3771c97879136b1010f4bb8fa3bf53349ef17c6ee034d934c77，BUILD SUCCESSFUL；报告声明exit0但未发现独立exit文件，不把其静态检查当设备证据。

Root证据：E:/creation-reading-assistant/j1-v5-codex-review-20261008/fingerprints.json。
Root新副本：E:/creation-reading-assistant/j1-v5-codex-finalization-20261008/android。
Root overlay：同根src/J1RealReaderFixtures.kt SHA ac0f1ad26ba966ab6662a0bee014b16c988c258ad7c5b5022e12018c30f1fb99；src/J1RealReaderJourneyTest.kt SHA 34140ffad3a4a0e3dae923d9444054d5c10264c09b7d6d3b9168cb62e72b27a6。
D HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空。

## 所有权与改动

Root只在新E副本修改两测试源，原v5和A/C不变。下一B v6独占J1测试、自己的方法元数据/离线检查/报告；不写产品、D2、runner、公共Gradle、C恢复驱动或D产品。Root补丁是基线overlay，不是完整冻结。

## Standards

使用当前实际资源，不安排不可用GLM5.2。推荐DeepSeek实施唯一窄任务，Codex核关键门禁。用户只发送一份提示词，执行者自主读取输入、准备副本、修复、定向验证和固定路径交付；不要求用户转交日志、选技术方案或找旧窗口。新提示词时间标在框外。

## Spec

v5可保留：真实start:len、生产SearchOffsets.parse与实际查询词offset、chapter0预览范围、真实页面入口、12列比较、位置变化断言、run全量冲突预检、finally引导恢复及诚实NOT_COVERED。

Root已补两点，独审采纳：
- 默认merged语义树卡片自身携带title/location/source，原hasAnyDescendant会超时；改为hasClickAction与自身hasText(title/location/原文)组合。
- A/B Book upsert成功立即登记ledger，再写依赖行；部分初始化失败可以精确清理已取得所有权的书籍，不扩大为未知资产删除。

剩余阻断：journey01 Back后只等首页按钮就取冻结行，不能证明异步保存完成；journey03仅前后notNull/bookId，不能声称位置保存。BookRepository.saveProgress重新生成nowIso，updated_at/last_read_at晚于Back时间也可能来自旧auto提交。Room invalidation、固定sleep、连续稳定采样同样没有退出请求身份。

Root集成决定：真实NavController仅composable局部，ReaderViewModel属于reader NavBackStackEntry，当前无公开取实例接缝；不造Activity owner VM，不扩反射/runner/生产hook去拼退出证明。B v6采用可实施的有限合同：真实UI marker与locator → 实际DAO目标位置提交（SAVE_CAUSE=UNATTRIBUTED）→ 12列临时期间冻结 → 返回真实手势合法推进；EXIT_SAVE_REQUEST_COMPLETION与全部保存排空必须NOT_COVERED。此调整关闭验收工件假绿，并不宣称原本要求的Back专属完成已证明。联合矩阵继续保留该未验项，Root核v6后决定可冻结的限定范围。

分页前置目前仅默认值假设：应核实际模式，或用明确新宿主无旧数据/无书级覆盖的合同约束并记录，交唯一冻结owner继承。手势后等待须同时满足真实位置改变及合法保存，不能仅时间推进。

## 环境

Windows/PowerShell，JDK D:/develop/Java/jdk-17.0.14，SDK D:/develop/Android/Sdk，E任务候选和既有E Gradle缓存。无设备、无最终APK构建。

## 执行与结果

Root运行 :app:compileQahostAndroidTestKotlin -PcraAndroidTestBuildType=qahost --offline --no-daemon --max-workers=1 --console=plain，缓存 E:/creation-reading-assistant/txt-persist-codex-completion-20261006/gradle-home。BUILD SUCCESSFUL in 2m3s，机器exit0。原197产品JVM输入未变、证据充分，复用不重跑。

## 原始证据

原v5源码/清单/编译log；Root fingerprints.json、overlay-sha256.json、compile.log、compile-exit.txt，保留独立E完整编译副本。原静态工具通过字符串出现就宣称退出保存/分页/selector通过，不能验证这些运行合同；下一修订其结论与负例，避免同类假绿。

## 恢复与副作用

未设备安装卸载或数据/settings变更，不产生设备恢复PASS。只新E副本、缓存/日志与D交接文档写入，无暂存/提交/push/PR/发布，不触正式书库、原损坏仓库、桌面/HOLD或私有记忆库。

## 未覆盖项

退出保存归因、分页前置闭环、联合新冻结、限定MuMu仍未通过。多层/pending旧NOT_COVERED不扩修；X整机中断、实体手机/MIUI、正式书库/桌面/发布未覆盖。全产品分母未核。

## 交付状态与下一步

限定合流输入仍2/3：A/C采纳；B有已核进展及Root两处编译通过overlay，但有限真实旅程合同与诚实覆盖矩阵待核。下一仅B v6定点自主闭环，不能重复派v5或A/C。B核通过后才由唯一owner合流/冻结准确源码与双APK及运行配置；Root核冻结后另给限定MuMu，设备不能提前启动。即使v6工件通过，Back专属保存完成仍不自动变PASS。
