# 任务24-v1停点核查

核对时间：2026-10-13 00:28 +08:00。用户口头完成后，Root只读检查实际文件、原始日志、测试源码及两独立审核；未构建或操作设备。

## 任务编号/版本

24-v1 / CRA-V0-JOINT-FREEZE-MUMU-ACCEPT-v1。部分实施，未完成交付、未通过同版MuMu验收；接续同一任务/目录/owner，不创建25或24-v2。

## 输入指纹

- Root-v2源1005项，manifest SHA e0de15c70487ad2fb03d18bf57660d61abd1f863c204984eeb7d105f4b4a9816，tree8785863cd53bbf58a5c549fe01aa567459d0fed993ae1e5157f0dbce8f1a7d67；复核一致。
- 24当前源1008项，manifest SHA9d86320d7efb357964ee9e22851b2e3a74843cd16e87247209219e57d16560a0，treea0b571494790821b3d0fceffbd974a4bf2b1493921fd5b56fcd4d2f1dbc9906c。
- 24验收46项，manifest SHAfc355271d74e16d5559d59be04f24ad786320d51be4228b2039b474e4b799b32，treec91e668a2cb67d52ecd6968a6d93136ad169af171e1fff7c1df8e4803dad2c62；与旧输入内容相同。

## 所有权与改动

唯一可写候选E:/creation-reading-assistant/v0-joint-mumu-task24-v1。对Root-v2为0改/3增/0删，仅EpubDispatchAcceptTest、NotesExportShareAcceptTest、ReaderRegressionAcceptTest。生产代码未变。Root只更新协调文档，自有E保存核查与更新前快照；D产品/暂存/提交/push/发布无授权。

## 环境

HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存空。本轮未发现24相关执行进程；不推断外部会话停止原因。MuMu是否被他人占用须接续前实查，不沿用旧serial。

## 执行与结果

- 首次AndroidTest编译失败；修正LocalActivityResultRegistryOwner import后compileQahostAndroidTestKotlin及Debug测试编译成功。编译不等于APK/设备执行。
- gate_check.out实际全量3543测试、1失败：SettingsStoreBootMirrorTest的system旧配置回填镜像用例。Gradle止于测试，lintDebug未执行。
- rerun_bootmirror.out单类重跑成功；当前XML13/13，不能替代全量通过。原全量失败XML被重跑覆盖，仅原日志保留，不补造原XML。
- 根因线索经源码及独立审核确认：appearance StateFlow初始themeMode也是system，first(themeMode==system)可在DataStore回填前返回。最小测试修正：种入system及非镜像amoledPureBlack=true，再等待这两个值，保留全部mirror/cold-start断言。无需改SettingsStore产品代码、固定睡眠或共享TEMP删除。
- NotesSheet测试为真实Compose入口和生产写字节，但返回file://缓存URI；分享只捕获Intent。非真实SAF/chooser UI证据。缺书用例是null字段，未验证Room删书后快照保留。
- EPUB为真实Room/EPUB的手动1,3,2仓库调用；非自然UI保存/真实并发。ReaderRegression有真实Activity/UI设计但未执行。D仅记录triggered/overwritten，JUnit OK不能作D安全结论：未触发=NOT_TRIGGERED，触发且覆盖=FAIL，触发未覆盖仅限定夹具通过。

## 原始证据

核查：[verification.json](<E:/creation-reading-assistant/task24-partial-codex-review-20261013/verification.json>)，含逐文件哈希、增量及日志SHA。原日志在24/logs：compile_check.out、compile_check2.out、gate_check.out、rerun_bootmirror.out。当前单类XML在24/candidate/android/app/build/test-results/testDebugUnitTest。

## 未覆盖项

REPORT.md、STATUS.md、强冻结记录和双APK未发现。baseline/backup/inventory/runs/accept均0文件；无MuMu执行和恢复原始证据。不得宣称从未操作过设备，也不得宣称恢复已完成。旧UI迟到回调、D、真实SAF/分享、初始位置一致性等原合同仍待补证；V0未关闭，全产品分母未核。

## 恢复与副作用

外部rerun_bootmirror.bat曾删除共享TEMP/cra-datastore-tests，越出自有E，不能将清理后通过归因于残留文件。现有记录不足以恢复或量化所删资产；不重做、不追认。接续先核其设备实际起始状态，发现既有24现场先保全并完成原恢复，不覆盖基线。任何设备失败/中断亦须恢复核账。Root当前无设备/产品副作用。

## 交付状态与下一步

保留已有3测试、编译成功和单类13通过限定证据。由24原owner读取原任务书及本核查后：修正等待条件并保留原XML；必要全量JVM一次、lint及双APK；完成强冻结与有效配置身份；真实MuMu合同和完整恢复；无论成功/失败均交REPORT/STATUS。停止重复准备任务，不重派16/23，不以11用例运行成功替代逐合同结论。单一owner原因是同源/APK/设备/恢复不可分离，没有独立未完成实施可有价值地并行。
