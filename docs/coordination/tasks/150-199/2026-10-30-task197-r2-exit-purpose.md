````text
# 任务197-r2：区分临时入口与普通退出保存，修齐真实生产回归及最终交付

同一197-r1 owner继续负责必要产品源码、测试、candidate集成、定向Gradle和交付。唯一工作区 E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。保护无关WIP，不开第二份工程。当前无第二必要独立项；本问题生产接线/测试/构建由同owner闭环。

## 已核事实与基线
197-r1交付根 E:/creation-reading-assistant/task197-reader-entry-boundary-integration-v1/recheck-r1/。Root读取到最终ready SHA256 e2d942c6fd14bd074aadbaa005b79b1721c82e0c1694c9f80d34f27a3264dff5；如后续曾改，先报告实际新指纹，不覆盖旧红补证。
当前candidate ReaderScreen.kt SHA256 46b377c58653d68ff7a9ad49425980f6b4078887662748be5ca1b74bdaeed5aa；r1 after镜像却是590a829670b4d5b745cef29b04d3ef708c162a49bd0c8ca68826fa35d3fda309，镜像/patch还含编译前LaunchedEffect引用未声明pagedAbsOffset，而candidate已改为pagerEngine.pagedAbsOffsetState.intValue。交付须对应最终已编译源码，不接受过期patch。
ReaderScrollPosition.kt candidate及r1after同4840923d7e2bd2f2cbbce91147dabe06b3c7c1a0f808f2201f671a60c0d4435c。
产品APK53899310B SHA256 1e99c078c3a3e591ec7597383c48170ffdc8653a5f6e84cf004b7ccd8c491652；test10696613B SHA256 475ea076b63d512fa75f1863975c07924dd14e220194259c7ca37d2416792626。
r1原始XML5tests/0fail/error/skip及定向rc0、三构建原rc0/BUILD SUCCESSFUL有限接受。test-decision.log原红是compileDebugKotlin unresolved pagedAbsOffset，不能当旧实现行为失败；5项普通退出用例绕过新skip直接调用persistCurrentProgress，未覆盖实际普通退出决策。旧197的11项充分缓存边界证据复用，不默认重跑。

## 必修缺陷
r1的skipPagedExitPersistOnUnmovedSession只有anchor/current参数；ReaderScreen通用persistOnLeave中一旦paged且current==anchor就跳过。该闭包同时接入exitSaveDispatch、ReaderPlatformEffects.persistCurrentProgress及onPersistProgress。因此普通Back和生命周期无位移也跳保存，违背r1明确要求“不能为了临时入口冻结时间戳而全局禁用普通无位移退出保存”。报告称位置合同不等于保留正常退出的阅读时长/时间戳/元数据合同。
1. 沿现有ReaderExitBoundary/真实临时入口和保存闭包做最小修复，明确区分临时转换、普通退出、生命周期等实际原因；普通无位移退出继续保存合法时间/元数据。不要再用所有路径共用anchor==current就一律skip的策略。
2. 临时转换未移动须保持S0完整12列exact；真实移动后最新合法进度与阅读时长不能丢。明确S0建立和保存排空顺序，禁止临时操作后重采S0、改scroll规避、忽略时间列、放宽断言或加sleep。
3. 原保存未完成/失败/内部取消/调用scope取消不得提前导航或标成功，异常可观察；LIFO/外部Notes合同B和冻结host不改。避免新平行状态框架，仅最小必要原因传递或现有机制。
4. 页锚建立必须引用真实恢复落位后值；别把“首个有效位置必已持久化”当未经证明事实。保留分页恢复/跳章/真实移动安全；不泛扩格式。

## 定向有判别力验证
新/调整测试须调用生产实际保存决策及分派接缝，同一实现由ReaderScreen真正调用；不能测试中手写一份状态机，也不能绕过skip后直接调用底层保存声称普通退出通过。
至少覆盖相同anchor/current下：普通Back、生命周期应保存；临时入口未移动不改变S0；临时入口真实移动保存最新合法数据；相关失败/取消保护若接缝被改则定向验证。先让针对r1实际缺陷的新行为用例在r1实现下失败并保留原log/XML/rc，再修绿。编译失败不是行为红。旧充分suite不重刷，仅新增及真实影响项。
使用现有 tools/gradle_locked.py 和 android-gradle-exclusive.lock，JDK D:/develop/Java/jdk-17.0.14、SDK D:/develop/Android/Sdk、Gradle用户目录D:/develop/env/gradle。定向绿后 compileDebugAndroidTestKotlin、assembleDebug、assembleDebugAndroidTest；编译必要修复同owner完成。无源码变化且输入匹配的构建证据可以复用，不为报告重构建。

## 交付与证据诚实
唯一新交付根 E:/creation-reading-assistant/task197-reader-entry-boundary-integration-v1/recheck-r2/，唯一ready为该根ready.json。旧r1报告、日志、错误元数据和源码不覆盖。最终after和patch必须与candidate逐字节匹配且可双向检查；不为校验Git暂存/提交，不删除临时或旧证据。若需临时副本只新增最小文件并预算，不重建工程。
交真实输入/输出size/fullSHA、源码/test/patch/REPORT、必要红绿log/XML/rc、APK实际签名runner/target和目标类方法。r1 write_ready修正ready部分指纹不代表REPORT已修：旧REPORT约30KB但after两源已83380B，before实际复制80141B；Root读到的历史时间02:12–02:16/182与184错归属不可用于本轮，原197精确发时未核，不虚构。新报告基于真实时刻和互斥字节统计，完整列限制。旧APK的哈希日志不等于APK独份保全；仅在覆盖当前APK前确认必要旧件独份是否真实存在，不存在才保全一次，若已丢失如实报告不伪造。
预算35分钟自本次实际发送起，source/patch/test/REPORT/ready<=256KiB，新增文字raw<=256KiB，必要旧APK独份另列。复用旧before路径及SHA，不再次复制整套before。超额原证保留不删补绿。ready是合法JSON，完整pins不自引用，分开集成、实际测试/缓存、构建、设备NOT_RUN。

禁止任何ADB/VM/设备、main/instrument/安装/恢复/launch/reboot/connect/kill、host/collector/fixture/业务阈值改动、Git暂存提交pushPR发布、删除移动既有资产、云/正式库/私库sessionDB/.codex/memories/自动记忆。194恢复已关闭，193业务原红永久留。本轮不放行新设备批。输入漂移/所有权冲突/用户stop或Esc/安全权限时停相应新增操作并如实报告，不旁路。仅在完成真实交付后总结，末尾独立一行：这是任务197-r2。
````
