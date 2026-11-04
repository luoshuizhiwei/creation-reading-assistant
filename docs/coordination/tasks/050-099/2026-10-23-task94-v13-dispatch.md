# 94-v13派发总览（2026-10-23 香港时间14:36后）

| 编号 | 执行端/模型 | 作用 | 并行/等待 |
|---|---|---|---|
| 94-v13 | 当前Claude会话及当前模型不变；确证限额才由Root转ZCode | 闭合实际探测通道与证据保全分阶段恢复，再一次剩余验收 | 同owner闭环；恢复合同→必要构建/绑定→单设备依赖串行；Root只维护协调记录，不抢写 |

````text
继续创作阅读助手原生Android，任务94-v13。用户已授权Root自动交接，保持当前模型，限额保全进度后报告，不自行切提供商。先核owner/进程/锁，若有人在执行不重复启动。

读取有效AGENTS、Codex项目页和D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/docs/handoff/current.md页首；直接读取E:/creation-reading-assistant/task94-organizing-export-integration-v1/v12/REPORT.md、ready.json及恢复合同，不扫历史。Root核同REPORT/patch/当前仪器/产品/test/保全aebc/宿主7项指纹；原test APK/完整数据inventory SHA/用户状态、保护71/820/DB组、产品缺席及保活原始证据核同。v12 summary仍有export-cleanup错误，不能称无错误恢复。实际运行42bf APK原字节缺失如实保留，不重建冒充。

当前产品1102346485a9e47b7b12885310f678e56b5c86d36082834fbec8732906ffc30c，test22c6c738750d5a6c233744751457e8cb0cd5ed0952c3cbaa95bad561894ae380（10526983B），仪器cec7b588c47f796c5221b2051d9158e0b832ed0d62fc1c91fdf243700c5b39d3，宿主6b4f76a9f57a76bd51d50de9c1e3aaa385c777447b62e89d0a4149fdea3991c4。这些离线修正未上设备，不直接旧inputs重跑。

先方向自审并解决两个剩余接缝，随后同owner闭环，不再按每个脚本报错拆批：
1. v12要求先验证实际通道却仍主run后才确证executeShellCommand字符串不解释shell。本批不要再次把“改Rw API且编译通过”当实际生产入口已验证。核Rw两个文件描述符的读写/关闭/EOF顺序、真实su和stdin脚本、明确开始/rc标记；通过实际生产适配做窄只读验证，证明现有目录可列、已存在目标拒绝、探测失败/空输出拒绝、中文/空格/引号目标正确匹配。不创建删除未知资产，不开额外业务主run；若必须安装测试包，先按有效合同保全并恢复，不能绕恢复门禁。确有无法验证的限制如实阻塞，不拿宿主ADB模拟冒充设备UiAutomation。
2. Root确认v12宿主finally约303-319行：collect/export-cleanup即使失败仍无条件卸载产品。所谓三态仅在cleanup函数中，并未控制后续可能销毁未拉取唯一证据的产品卸载；这尚未满足v12要求。修真实入口：未创建且初始/最终证明成立可恢复产品入场缺席；已创建且证据保全后可精确清理再恢复；collect失败、URI/证据未保全、未知归属必须保留依赖证据的产品状态并明确BLOCKED，原test代码/完整数据/用户状态和独立安全恢复继续。不能恢复全跳过，也不能无条件卸载。保留原业务/取证/清理/恢复错误。用实际finally入口受控模拟覆盖collect失败、部分回执、未知新增、正常未创建、已保全成功，证明禁止路径没有卸载/删除请求且独立原test恢复仍执行。不是只测未调用helper或字符串断言。

Root已查官方API确证另一处确定缺陷：executeShellCommandRw返回fds[0]=stdout读取端、fds[1]=stdin写入端，见https://developer.android.com/reference/android/app/UiAutomation#executeShellCommandRw(java.lang.String)。当前runRootShellScript却AutoCloseOutputStream(fds[0])和AutoCloseInputStream(fds[1])，方向反了！先修此处再验证真实通道，不能直接复用22c6主run。编译绿不证明FD方向正确。

核对整个剩余链：真实导出UI/provider根与StorageVolume不同字段域、canonical目录、真实默认文件名、保存前缺席、返回URI、失败前及时保全原字节/receipt、内容断言、精确清理、清空持久化、取消不写入。只改确定仪器/宿主缺口，不猜产品缺陷、不加sleep/重试或通用框架。通过分类/搜索/AND/回源及69项复用，输入未变产品不重建；仪器确改才必要test构建。冻结真实动态依赖后新绑定。

你唯一拥有本批E差量/仪器/宿主/构建/设备和交付，Root拥有D协调记录/current，交付通知Root更新D，避免抢写。唯一工作区E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android；Gradle home D:/develop/env/gradle，JDK D:/develop/Java/jdk-17.0.14，SDK D:/develop/Android/Sdk，锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock。输出E:/creation-reading-assistant/task94-organizing-export-integration-v1/v13，存在先核归属不覆盖旧交付。证据差量8MiB、既有build增量预计256MiB；覆盖当前唯一22c6测试APK前若无同SHA副本先保全必要单份10526983B，实际运行件也须在任何后续构建前保全。报告实际空间/保留理由，不复制工程缓存历史APK对，不清理迁移存量。

前置已验证且恢复合同闭合后，执行本批最多一次主run/attempt（包括入场失败），失败停业务并取证恢复，禁止同批修正再试。若前置未闭合，不消耗主run碰运气，交付明确阻塞。允许使用/必要启动项目隔离MuMu1，授权游戏可关闭，全程不关闭/重启/杀VM/操作共享ADB，结束保持运行。ADB D:/Application/NetEase/MuMuPlayer/nx_main/adb.exe，fresh serial/boot/owner/前台/预算，全部命令绑serial；阶段预算加至少1200秒恢复预留。原71私有/820外部、DB/WAL/SHM、原test代码/完整数据/用户状态、原宿主APK/设置、strict night=no均不放宽。

验收真实导出返回URI原字节的标签/正文/摘录/来源，明确清空同精确行分类且来源保留，取消同一行payload/更新见证不变。精确清理仅确证本轮新建、初始缺席、URI路径绑定、E原字节sizeSHA及设备当前字节一致的单资产，之后复核不存在；未知不删、不递归、探测错误不算不存在。

交付最小patch/变更源码前后SHA/真实绑定/REPORT/ready/必要原始日志XML，旧FAIL与原始错误不改写。分别报告实现、离线、模拟器及恢复；不要把未知或事后修改叫闭合，MuMu不代表实体手机/MIUI。最终方向自审、已完成/未完成/下一步；无Git暂存提交push发布、D产品回灌、桌面正式书库HOLD私库云部署/存量资产清理，不自行创建自动化或发外部消息。Root接续核验。最终最下面单独写：这是任务94-v13。
````
