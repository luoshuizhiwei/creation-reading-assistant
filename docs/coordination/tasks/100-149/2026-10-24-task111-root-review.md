# 111-v1 Root核验与下一阶段方向

## 任务编号/版本、输入指纹

111-v1，2026-10-24。唯一run T106A-T111V1-20261024-133356。E:/creation-reading-assistant/task111-material-journey-navigation-v1。REPORT 1201d695afbadaa50a699789afe30d4c2dfdb444344aaf25a2576d875202363d（6685B）；ready f52245d50996099c528a8b62f35917bc661f7f2998e66ab3114ee5c0d402722a（4372B）。Root读取原始输出、四阶段数据、源码补丁和恢复回执，未重复执行测试。

## 所有权与改动

Root D协调/current；ZCode官方Start Plan GLM-5.3为E唯一owner。仪器 f72bda18→b62225e5b4c81a1cf40a652f13ae8df5b426aa67380acde70fc871dd858bc3c4（36293B），补丁86b73ecd0d1f255d84dbff344a32f3dfcea90c7dd88fe0e7b2045b3fa2a7b708（1252B）。新增详情出现断言→pressBack→等列表，原cardPresent和后续完整断言保留。host2434cbe0b653b9b7c337e91d2598b1e7d7db598590d14a72cf249e3df4f83381（19115B），仅本批绑定。生产零改动。

## 环境、执行与结果

task59唯一候选/原锁及JDK17/SDK/Gradle home。仅assembleDebugAndroidTest，原日志16s BUILD SUCCESSFUL/EXIT_CODE=0。实际test717530a23c54baf482a2f7c0fbdb47a3266ea1766944e6de15e95387a3ae8b67（10588807B）preserved单份Root核同；产品6a6c65af仍引用110/preserved、不构建不复制。

Root核原04-run.json：OK (1 test)，12.657秒、状态0，末尾INSTRUMENTATION_CODE=-1为runner正常结束码，不能孤立判FAIL。host144.341秒、primaryError=null/ok=true。10仪器原件collect成功。

20_merged：素材卡51b4c321-8c43-4931-879f-d800e3944364，excerptCount2、两个locator/章节原件，原A/B archived，新卡reviewing；结合实际仪器断言及全例OK，来源book/文字/mergedInto/未删除断言通过。30_adoption：真实详情文字采用，kind=text、值为本批run绑定，status=used。40_used_filter：已采用1、cardPresent=true、原A/B=false。50_reopen：payload前后均340d2bd9bdfb3e007a721e8de24acd2d4554df490409eb262f5081800d471ae9，adoptions1/excerpts2。99 lastPhase=t106a_50_reopen。接受此完整窄旅程模拟器结果，110布局修复与111导航校正形成闭环。

## 原始证据、恢复与副作用

E111 logs/01-assemble-androidtest.log及evidence/device/04-run.json、summary.json；evidence/instrument/task106a-results-T106A-T111V1-20261024-133356内20/30/40/50/99直接核。Root前次已核同原件00-original-test-verified/06-original-test-restored：apkSha256/inventory/userState逐字段相同；02-protection-initial/final rc0、consumer ok、71私有/820外部/diffs=[]/db_groups_match=true，输入未变复用。summary recoveryErrors=[]、Download179新增0、产品UNINSTALLED、blocked=[]、testPackageRestored=true。settings night=no，keepalive pid66432/serialattached/boot存在，manager android_started=false不单独推导关闭。恢复接受。

实际目录14272746B，保全test10588807B，其余3683939B；ready/报告体积14261689B差11057B为收尾时点差，不重跑。build增量未独立测。无Root设备操作/源码回灌/资产清理/Git操作。

## 未覆盖项、交付状态与下一步

本批是MuMu1模拟器、生产DAO种中性BookEntity/两素材后的真实UI与持久化旅程，不是实体手机/MIUI，也不包含阅读器实际选区产生摘录全流程。106/108/109/110原FAIL保留，不回写改绿。全产品分母未知。

方向复核：I1/I2本轮多来源合并→采用→筛选→重开目标闭合，停止此旅程确认批。既有L1动态视图仍有独立真实UI缺口：107真实DataStore三scope持久化1/1已接受，VM测试和生产FilterSheet/ShelfRoute保存/套用/删除接线存在；旧workbuddy-r3-final-integration明确未定位创建入口、保存→复用PARTIAL。Root现读FilterSheet131–194和ShelfRoute498–511，确认当前入口存在，不能把旧未定位当当前缺实现。下一112仅实际真实UI保存多维筛选→清除→套用找回→删除目标保留对照及书单语义不变，复用107离线存储耐久性，避免重复JVM或进程重启矩阵。先当前能力与保护接线审查，必要新增独占仪器，产品保持；失败完整恢复停批后Root决定修复。共享build/host/device单owner，当前没有独立且有充分价值的额外实现可并行，不造泛审查。完整实际派发见[112](2026-10-24-task112-saved-views-ui-dispatch.md)。
