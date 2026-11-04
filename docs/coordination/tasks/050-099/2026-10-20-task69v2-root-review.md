# 69-v1-A / 69-v2-B Root核验：源码与测试采纳，宿主和导出整链仍阻塞

## 任务编号/版本
69-v1-A、69-v2-B，2026-10-20香港时间。A和B交付均到齐，独立实施与合流部分完成，不称模拟器或整链通过。

## 输入指纹
Root实算7份合流源码（MD、策略、取材及4产品/单测文件）与ready一致。MD14808205449445820db9e0907c85665bf998451e42f1c176d95ec21bf648c46a；策略f7e651e75b33062d60721a9b0b4219fb058ea62e01f7571cd577673429cb20cd；取材76eaa9784afd71dbafc9322d7c16084926244f31ca816f0ba89ca06d38f416cf。
新test APK3336d1ca58945f8566396af096640c567f803d1eb83cc6016b5fd285d7ac00e7、host14e573ab964a122c3d4fbb83770423d70bebd57028eca26c455a442da1a282aa及保全旧test db741d1d…1b55均实算匹配。候选runner/freeze/binding按实际生产校验逐项核对，返回全ok，但存在下述来源合同缺口。

## 所有权与改动
相关task69/59 Python/Java执行进程查询为空。Root仅写E任务目录root-review-v2/checks.json、生产校验输出及本报告/current。没有修改产品、工件、旧报告或冻结。A原件保留，B合流时仅补swipe import，已有真实编译证据支持此集成修正。

## 环境
复用59唯一工作区、D:/develop/env/gradle和既有依赖。Root没有构建、设备、Git或外部应用动作，不重跑充分的定向单测或原19项runner回归。

## 执行与结果
采纳A严格delta判定和分页横向手势修复：原14项离线判定日志compile/run rc0、all_ok，合流策略SHA一致；实际设备动作仍待验证。采纳B分类ID同源产品差量、严格分类关联/无悬空断言、删除file://组件替身和target.delete、取证错误addSuppressed保留。74项ReaderViewModelTest原XML tests74 failures0 errors0 skipped0，真实编译/assemble日志BUILD SUCCESSFUL。
Root实际运行候选verify_binding、verify_freeze、audit_import_closure和预算检查，均ok。该结果仅说明现有合同所列输入匹配，不能证明产品源码已进入待安装host。

## 未覆盖项与放行阻塞
1. 产品三文件已修分类身份，但报告明确host未重建；host SHA仍为修复前已采纳旧APK14e573ab…82aa。因此待安装宿主仍是修复前工件，不能验收分类修复。assembleQahostAndroidTest成功和单测通过不能替代宿主打包。下一版需必要assembleQahost并记录来源；不是以“新旧SHA必须不同”为构建真实性规则，而是针对本次真实产品变化核实源与工件一致。
2. build-provenance只列三份androidTest源码及compile/assemble androidTest命令，未绑定本次产品源码与host构建。现有freeze仍返回ok，需最小补齐产品输入、host真实构建来源及对应工件绑定，保留源码/来源漂移拒绝。
3. MaterialGatheringJourneyTest.kt:774起仅等待DocumentsUI，出现后发送back取消；pickerOpened=false也不失败；mat_70_export仍写true，exportFileWritten=false/PENDING_DEVICE_SAF。无实际文档、URI、权限、内容读回或总入口拒绝依据。诚实标待设备值得保留，但该仪器和runner不能作为完整取材验收入口。需完成实际保存与应用URI读回，结果严格消费；文件未写/选择器未出现/取消/权限失效必须阻塞，不用旧SAF证据替代。

## 原始证据
E:/creation-reading-assistant/task69-md-material-closure-v1/A下REPORT/ready、src、verify/policy-check.json及log；B/v2下REPORT/ready、src/patch、evidence/junit/TEST-com.creationreadingassistant.ui.viewmodel.ReaderViewModelTest.xml；integration下logs/02与04/05/06真实日志、ready、evidence/build-provenance.json、candidate工具/冻结/绑定；Root实算与生产校验输出位于root-review-v2。均原路径引用。

## 恢复与副作用
本轮无设备动作，无新增恢复义务；68旧FAIL及已核71/820恢复结论不变。E仅新增小型Root JSON，没有工程/缓存/APK复制及既有资产删除迁移。automation-2/3未改，保持暂停决定。

## 交付状态与下一步
A实现/离线交付采纳并收口，不重派A。B源码及定向单测/真实androidTest构建采纳；新设备放行拒绝。69-v3-B由原B唯一集成owner一次补齐导出严格链、宿主必要打包及产品来源冻结后交付，不自动设备。已有A/分类修复及充分单测证据复用，除后续改动实际影响外不重跑。当前剩余工作共用取材仪器、执行合同和最终APK绑定，按实现→一次合流构建→冻结串行，不制造泛审查任务。全产品分母未知，不称全产品完成或实体手机/MIUI通过。
