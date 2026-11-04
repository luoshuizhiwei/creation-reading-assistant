# 108-v1 Root核验与109方向

## 任务编号/版本、输入指纹

108-v1，ZCode官方Start Plan GLM-5.3，唯一run T108V1-20261024-122315。Root读实际REPORT c68f0f04efdae16264c8af2ce2a90cf08768906459e2bcff18f7759bea0b7e34(6242B)/ready33addc59e90fa8b1814b756fb0ad057c4a48b2c188d2985cff60491b06cea39d(4747B)。仪器a2559532(34188B)、test a5c9c117(10587749B)、产品bf9720a7(52763781B)沿已核保全。host修错误产品引用d2f18f9f→f21953bde5e3824a12e42b62672fa39ca93260563b939b191922cea764e0a4a6(19042B)，17项运行输入已冻结。

## 所有权与改动、环境

Root D文档，ZCode E源码/host/build/设备唯一owner，原Claude已停止。仪器/产品未再改，无新构建/APK复制；只改host引用及恢复补全脚本。MuMu1，ADB命令绑定本轮serial127.0.0.1:16416；不作为下一批fresh授权。唯一工作区、JDK/SDK/Gradle home沿原合同。

## 执行与结果、原始证据

唯一attempt已耗、业务129.583秒，仪器原stdout class/test/numtests1、状态1→-2、终码-1，Tests run1/Failures2。主因setUp113 require Regex("T106A-[A-Za-z0-9-]+")拒T108V1，tearDown另保留resultsDir/db未初始化异常；结果目录创建115、db118、onboarding119、seed120均在该require之后。21/23/22/合并/采用均NOT_RUN，不算产品行为反例或修复依据。106原业务FAIL保持。入场失败不批内重试，原stdout/summary/collect失败全部保持。

## 恢复与副作用

Root核原test verified/restored apkSha256/inventory/userState逐字段一致（0d18原字节）；initial/final原consumer71私有/820外部/diffs空。自动summary原collect失败/KEPT_BLOCKED/ok=false保留。后补13-recovery-completion原probe CRA_EXT_ABSENT、卸载rc0 Success/presentAfter=false、原test ok、night=no、保活rc0；卸载后独立readonly-protection-proof.json ok=true/db_groups_match=true与consumer71/820/diffs空，protected-original-final problems空，恢复原缺席产品接受。一次传kind=final-rerun2的argparse rc2用法错误原回执保留，后final独立outdir成功，未作业务重跑。

边界：complete_recovery.py的“test -d && ls || echo absent”不能当通用可靠缺席探针，存在把列举失败当缺席的代码风险，禁止后续无条件复用。本批独立setUp在一切fixture/结果写入前失败的确证，与原collect缺席/后补状态及完整保护证据共同支持无本批唯一仪器证据丢失；不将此脚本作为新恢复工具交付。保活Manager is_android_started字段波动不当实例关闭或重启证据，pid运行及保护ADB回执支持实例运行范围。

## 未覆盖项、交付状态与下一步

108接线失败/恢复接受，不是合并旅程验收。实际目录14206805B，比REPORT14200634B多6171B（收尾新增/报告计量时点差），保全test10587749B、其余3619056B；预算内，不重测build增量。1073/3、B1/1和主编译充分证据不重跑；108本次ZCode未新build，跨owner整个108此前10s/9s构建仍应区分。

方向自审：真正未闭合仍是I1/I2连续合并采用，首要阻塞是已经确证的runId接线，不改产品布局。选host兼容绑定T106A-T109V1-时间戳，作为明确109新编号/新输入/新attempt，保留仪器和实际APK字节；离线直接核Kotlin实际Regex及host参数/结果目录一致后才设备。避免一行仪器改动带来无必要重建，也不为数量造并行审查。host/设备唯一owner继续GLM-5.3；109单run取证恢复退出，若失败不批内修码重试，完成核验后继续实际目标。
