# 任务24-v1第二次交付缺口核查

核对：2026-10-13。用户本轮只授权核验，不给下一批提示词；随后允许子代理直接继承当前模型。Root与两位继承模型的子代理只读审查交付，Root独立执行离线冻结verify及文件哈希核对；没有构建、启动/操作设备或修改产品代码。

## 任务编号/版本

24-v1 / CRA-V0-JOINT-FREEZE-MUMU-ACCEPT-v1，第二次缺口交付。已有实质进展，采纳限定成果；完整验收未通过，V0未关闭。本轮核验完成后待命，不自动接续或派发。

## 输入指纹

- 当前源码1009项全部匹配最终manifest，SHA08e09f3965a1540e0c85d6cfdbcfc46b4afaec3ec229f8b22cc79d0bd6d798b5，tree06cfdde33a31a758abfbbd4bcae2ca28953bf638dce6a85d7408025a23bd1830。
- 验收46项，manifest SHAfc355271d74e16d5559d59be04f24ad786320d51be4228b2039b474e4b799b32，treec91e668a2cb67d52ecd6968a6d93136ad169af171e1fff7c1df8e4803dad2c62；强冻结工具SHA未变。
- host APK仍ac580c95902a9c03226bc17f25e0d713f01879b48088fe8775f517845d698c34；test APK为f704c867b9e4080179907a0b59625271f880960813ae2a00c3ecc705f765ab7f。新freeze-t24v2与旧freeze保留。
- delivery-snapshot-20261013的135个条目逐项SHA一致，旧成果保全成立。

## 所有权与改动

对Root-v2为2改/4增/0删，生产0改。对上次24为ReaderRegressionAcceptTest一改、RealSystemUiAcceptTest一增；BootMirror及J1夹具的上次窄修改保持。当前只读采纳E交付，不D回灌、暂存、提交、push、PR或发布。

## 环境

HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存空。当前只读未见MuMu/MAA/Java构建进程。执行者自报的路由标识不作为后台模型核实结果。MuMu证据只支持模拟器，不能称实体手机/MIUI通过。

## 执行与结果

1. Root独立运行现有cra_freeze_gate.py verify对freeze-t24v2：exit0/verdict PASS。逐文件源码、验收、真实构建记录、双APK/包/runner/签名身份绑定成立。build-v2.log实际assemble命令exit0；增量全部UP-TO-DATE不作新全量测试证据。
2. 3543全量JVM及之前Lint有效范围复用，未重跑。新增androidTest有编译/双APK成功；不把旧Lint说成已在新androidTest上再次执行。
3. run_t24_device5的24份runner原始记录全OK(1 test)、adb_exit0；134个harvest引用文件逐项SHA匹配，配置绑定当前双APK。24项是执行分母，不是完整用户合同通过率。
4. 前进实际完整行4014→5052→5052，offset/locator/percent一致；回读已保存7493→5804→5804且updated_at推进。新断言已收紧，实际结果可限定采纳。正文语义节点/非空bounds支持正向正文身份，但整个Text节点的bounds不能扩大为每个marker字形的精确可见坐标。
5. 普通fixture真实UI握手S/R=4465、S2=7730、退出7730、冷读7730；S2与退出updated_at分别01:13:47.915Z/01:13:59.284Z。握手readerSessionPid26966与冷读31787不同，不能将各仪器方法producer PID数直接当真实阅读会话数。
6. 短末项夹具S=3840/100%、恢复3840、新位置1781、退出1781、冷读1781；updated_at推进到01:17:41.839Z并在退出到01:18:58.377Z。初始化是fixture seed，不宣称生产初始退出保存。该测试仅SELECT current_location_json、progress_percent、updated_at、book_id四列及locator派生offset，未交12字段完整行；8165/10936/12146为仪器producer PID，未见普通链那样真实readerSessionPid/pidof/前台握手。因此短末项只采纳这些字段与冷读摘要，完整12列/真实UI冷PID合同未通过。
7. D实际triggered=false/changedObserveCount0，分类NOT_TRIGGERED。原件前后正文节点bounds由[42,0,1038,1920]变[42,0,1878,737]，说明界面边界变过，报告的“marker未变”不精确；没有旧列表坐标变化证据，不能写D安全。F为NOT_OBSERVED，未证旧闭包迟到动作，不能由本轮观测关闭。机器summary仍overall PASS并将两项列pass，分类仍错误。

## 系统界面证据边界

RealSystemUiAcceptTest源码确实经NotesSheet发起真实系统交互，未使用受控Registry。real-ui-frozen目录只有6文件：三份各102字节的JUnit成功摘要、一份452字节导出内容、两份目录列表。可以采纳“成功摘要与内容样本存在”，但不能完整核实报告所称的最终APK上系统SAF/chooser/冷读链：

- 未找到此次对应的j1_guard_evidence详细payload、实际窗口摘要/dump、调用命令/时间/退出码元数据与最终APK实装关联。
- 冷读测试仅断言文件大小；驱动所称真实文件冷读的命令、真实URI/目标关联、冷PID证据未交。452字节内容支持内容检查，不单独证明它来自此次真实SAF返回对象。
- 分享测试设计包含真实chooser和Back，但对应实际窗口内容及取消后的状态原件未保留。
- run_t24_device4的SAF失败记录属于早轮，不能借其窗口原件证明随后三份成功摘要轮。

因此完整系统UI链为NOT_VERIFIED，不等同于确认未运行或产品失败。

## 恢复与副作用

保护基础成立：完整after清单与当前/旧快照基线的受保护qahost71/71、test0/0均相同；本地DB/WAL/SHM三件与基线/最终SHA、size匹配，原APK对三方SHA一致。run5卸载前本地取证原件核对无错误，且最终受保护包仍installed、测试沙箱absent。MuMu关闭/ADB空的末记录存在。

完整恢复门仍缺证：

1. 后账所用08:58基线已是测试包installed、night=no；旧快照01:24基线为测试包absent、night=yes。final两测试包match=false。自有测试包清理不等于保护资产受损，但晚建基线不能证明回到补验开始前状态，night=no只对晚基线匹配，缺最初状态及变更/恢复链。
2. 361条路径唯一且后账记录361/361 absent，但没有/sdcard/t24-saf1.xml、t24-saf2.xml或Download导出文件。测试源码明确写两个系统dump。额外目录列表只支持列表中出现/消失，不是完整逐路径/URI清理回读原件；未证明这些遗漏对象仍残留，也不能称全部创建物恢复。
3. run5 restore created_at约09:19:53；三系统UI成功TXT写盘约09:25，晚于其恢复。只找到run5已有pre_uninstall_manifest，无系统UI成功轮后独立卸载前取证/恢复原件。mtime不是精确操作时间，但已有证据不能将早轮restore套到后轮UI。
4. final-ledger尾note仍声称无创建物账并引用不存在的compare-result.json，与实际361账冲突；总体“未证明的副作用：无”不成立。

这些是恢复证据及范围问题，不是已确认数据损坏。Root不为补证启动设备或替执行者修复。

## 原始证据

[Root核查目录](<E:/creation-reading-assistant/task24-gap-codex-review-20261013>)：freeze-verify.txt为本轮独立verify输出；verification.json含源码差量/135快照/24runner/备份/恢复口径；binding-check.json含134harvest SHA及卸载原件检查。交付原件保留在任务24的freeze-t24v2、delivery-snapshot、runs/run_t24_device5、runs/real-ui-frozen和recovery-final-ledger。不改写外部REPORT/STATUS或原始summary。

## 未覆盖项

短末项12字段完整行/真实UI冷PID、D触发、F旧UI迟到路径、系统UI完整身份/URI/冷读链、系统UI轮后卸载取证、补验最初设置基线和完整创建物账未关闭；X清零、实体手机/MIUI、正式签名/R8/发布、桌面/HOLD、实际LAN范围不变。全产品分母未知，不以执行数量代替完成率。

## 交付状态与下一步

本次核验采纳新冻结、有限阅读保存与冷读进展，不接受“所有补验与恢复全部通过”。V0未关闭。用户要求本轮仅核验，现态维护后待命，不给提示词、不自动派发或接续、不执行设备/构建/产品修复。后续由用户命令再决定范围。
