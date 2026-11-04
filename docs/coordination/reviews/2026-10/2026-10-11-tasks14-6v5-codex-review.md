# 任务14-v1与6-v5核查及同任务继续

核对：2026-10-11。任务编号/版本：14-v1、6-v5。交付状态与下一步：14有候选/双APK/三轮运行但REPORT缺失，最新18 PASS/1 FAIL/12 NOT_RUN，未完成验收与完整恢复交接；继续原14-v1 owner，禁止重派第二设备owner。6-v5材料已交付，新12路径限定核验成立，完整65目标绑定待6-v6返修。两条独立线并行：14单失败连续诊断/窄修/验收/恢复；6-v6不可变12输入材料目标重放，无构建设备。

## 输入指纹、所有权与改动

14候选仍999项，相对12-v2生产改ReaderProgressEffects、ReaderScrollPosition，测试改TxtShortClampContentReadinessTest。当前tree e2f9746c75f974875432851f28f25866b599b55ec925552e80df765c54c25ce5。双APK宿主bb393d560e14cbe2822c02010b375a1e0d67f478d9c104a732a702b3ad40d8a3，测试a710d511796e34b983dd44e0b603be01cfd7eeaf87f64e70feeaefe4d0898ee6。最终44验收tree仍6e98833f357a9b8e8031e7f2efa85a5c2cda1bfaac613223496381b82022fda1。

14原freeze使用外部13-v2工具SHA1ac860...，未使用指定Root补正5b4dbf...。Root已用强版独立verify真实运行并得到PASS/exit0，当前源码、清单、APK身份/签名/target、原快照、真实执行日志、命令/cwd等实读通过；不因工具版本问题重复构建。下一修改/诊断构建必须采用Root版并用新路径，不覆盖失败run。

6-v5证据21/21 SHA/size匹配。53复用补丁自身53/53匹配旧INDEX；旧目标SHA 30为空（缺证，不是已证不同），23有效里20匹配12-v2、3不同：ProfileRoute、SearchScreen、ProfileViewModel。12新补丁只读验证通过，但不证明旧53重放目标。supersedes含省略号，缺可解析locator。65路径集合计数成立，字节完整性未退出，不直接重做全部补丁。

## 环境、执行与结果

HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存空。14 run_t14v1_final/device2/device3均18/1/12，同一clamp20失败；Root未重跑设备/Gradle。独立审查实际失败raw为短末项重进offset0，前序S=3840/bodyLength3840、unit1末项；harvest确认DB offset/percent归零。UI XML正文就绪不能代替layout/loader/pending/DAO写入时间线，尚不能区分恢复未到、过早清pending、后续保存覆盖。下一只针对该步骤采集连续事件链、提出可证伪假设后修，不盲跑完整31。

6独立pwsh默认只读核验通过；原40检查、12补丁重放与7安全断言按交付日志限定，未重复运行带写入的套件。旧53目标按原INDEX比较的上述限制准确保留；下一在自有E夹具实算完整65，每项目标绑定实际字节，先判仅元数据旧还是补丁内容旧。

## 原始证据

[Root核验](<E:/creation-reading-assistant/tasks14-6v5-codex-review-20261011/verification.json>)、runs-summary.json、freeze-verify.log与[next-input-binding](<E:/creation-reading-assistant/tasks14-6v5-codex-review-20261011/next-input-binding.json>)。14各runs summary/raw/harvest/restore、6-v5 evidence与INDEX保留原件。初次Root调用沿用旧CLI参数位置产生usage error，已按现有子命令参数位置修正，最终真实verify PASS；前次调用错误不算产品门禁失败。

## 恢复与副作用

14最新restore.json声称ok/verified true，含卸载前取证、sandbox包/数据absent和remote清理；没有在此摘要里完整关闭保护原包持久文件/DB/WAL/SHM/原APK/设置和约定MuMu状态。Root未设备复核这批全部保护项。当前MuMuNxDevice/Headless进程存在，最终REPORT缺失，不能称完整恢复已退出。Gradle daemon进程存在不单独证明任务仍构建。Root只读查看与强版freeze verify，无设备写入/构建/Git操作。

## 未覆盖项与调整原因

DEVICE_ACCEPTED=false；X清零、Back专属完成归因、队列排空、初始new save、实体手机/MIUI及正式签名发布未验，全产品分母未核。14连续三轮同失败而缺连续诊断，改为首失败事件链→单步复现→定向修复→必要后续/最终矩阵，不换新owner、不重复已有普通/J1成功项。6只补材料完整目标绑定，不写14可变候选、不重复充分路径安全测试，不等待长轮询。用户只粘贴，执行者自主收尾；无D产品/Git/发布或私有库授权。
