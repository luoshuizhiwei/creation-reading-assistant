# 61/62交付核验与共同候选收口

## 任务编号/版本

61-v1、62-v1，2026-10-18香港时间。按用户明确要求收敛核验：核差量与已有原始证据，只对当前共同候选固化时核一次实物，不重复全量测试或另搭验证框架。

## 输入指纹

[Root接收清单](<E:/creation-reading-assistant/task62-common-test-apk-v1/root-review/ready.json>)保存原61/62报告、ready、关键证据SHA及共同候选身份。候选位于既有59工作区：1033→1034文件，恰好新增Root-r1的`HighlightInspirationPersistenceAcceptTest.kt`，SHA `74c90e04822593db13b917991144ac1cc3f4630e53d47b7106c996b3be099f6d`，既有文件无改删。完整内容清单digest `aba75da1fea19665591e3ca93ea7eee04d52ce1d4aa5035c116f47ccd1adab15`。

## 所有权与改动

61零生产/runner差量，只交启动合同；62唯一工作区集成/构建。Root只读核验并写接收清单和交接，无新增子代理或产品修改。现场未见相关活动构建/执行进程；原交付和旧FAIL不改。

## 环境

沿用Windows/JDK17/Gradle8.9与现有依赖、单一59工作区。Root不启动Gradle、编译器、设备或ADB。

## 执行与结果

- 61结论KEEP_CURRENT采纳：19个候选输入指纹匹配；manifest指向生产App，MainActivity为AndroidEntryPoint，已有6个真实Activity测试。Hilt实际依赖字节码证据支持保留生产Application透传，不能把当前包换成裸Application隔离；无需为包名差异修改runner。
- 62原始Gradle命令`:app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost --console=plain`，exit0，日志BUILD SUCCESSFUL，实际完成KSP/Kotlin/dex/打包。本轮不重跑。
- 新test APK SHA `18868c2f8b2adf1a55d276faa547634d39caefd0fce218aca3280a208846044a`，9,707,366字节，与真实构建输出匹配。已有manifest/签名/dex工具日志与实际APK核同：target `.qahostj1persistv2`、runner为CraIsolatedTestRunner、signer `05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1`；Root另直接确认APK dex中存在新测试类。
- host复用59原产物，SHA `49610255de1e2930102e95da9193f19a9e5164f60ffca3151359a87632186978`。因生产/单测/配置未变，复用3671项JVM、Lint与宿主构建；旧56保护使用62已交证据，不再次计算整套92工具/全部旧资产。
- 62交付时“等待61”符合当时现场；61随后已交且无代码差量，Root已解除此依赖，不再为此重派或重新编译。原62报告状态保留，由Root接收清单给出当前结论。

## 原始证据

61的`inputs/input-sha.json`、`evidence/hilt-activity-component-check.txt`及manifest证据；62的`evidence/source-manifest-entry|merged.json`、`evidence/gradle/01-assemble-qahost-androidtest.{json,log}`、manifest/signature/dex日志；Root接收清单绑定上述证据并保存新增APK与源码身份。未新增一套测试夹具、运行时转换或全量编译流程。

## 未覆盖项

4个Room仪器用例未运行，模拟器/UI/进程冷启动/新整链未验收。Root接收清单固定共同构建候选身份，不伪装为既有设备工具已通过的`cra-freeze-gate/2`记录；设备执行前仍需兼容的共同冻结/入口绑定。不能直接给旧56-v13绑定换APK路径就启动。

61/62方案提及的原71/820及qahost安装前absent都是历史/方案条件，未在本轮实查设备，不采作当前事实；设备阶段须fresh核基线和恢复合同，包括60保留目录。全产品分母未知。

## 恢复与副作用

本轮Root构建0、设备/ADB0，复用有效证据。原交付、旧冻结/APK/工具不改，无D产品回灌/Git/发布/外部消息/既有资产删除迁移。两个自动化实读PAUSED，未修改。用户关于适度核验、成块交付与提示词总览表的要求已按允许机制写入memory增补note，未覆盖自动生成索引。

## 交付状态与下一步

61启动合同与62测试APK构建交付采纳，共同候选已包含Root-r1，等待61的依赖已解除。下一步沿现有62产物收口设备使用的共同冻结和入口绑定，复用宿主与测试APK，不重新开发或重跑不变门禁；之后由用户决定单次设备批次。不得以编译/打包成功称Room或整链通过，不自动恢复automation-2/3。
