# 69-v3-B Root核验：新宿主与来源采纳，导出适配需窄修

## 任务编号/版本
69-v3-B，2026-10-20香港时间。实现/真实构建部分采纳，设备未执行，导出适配不放行。

## 输入指纹
Root实际SHA核同：host efd4a879c1c2c264254e88312a76b38bba29547d15b1ff9ad076bb5370809776（52882843字节）；test46b3a68a6595dc15714347949555e79cd21c71da804895caeac6ed2b55e7b3d1（10264325字节）；保全旧test3336d1ca…00e7。
入口1b7964b9e80f3d1a233b908217d953edb4f4c4b6a286fc286900ada59bf59496，freeze5b68616019b3b14031153613319f573b56ea312c7c3e0efdc28b95a9508c4143，binding a24fb71c2ca35a72cf113932dcb3ec1ac02e94d7fe70b4294d3dbcb5a971f532。工具/源码指纹由生产校验实算，未复制整套资产。

## 所有权与改动
相关task69/59 Python/Java任务查询为空；Root仅只读原交付、实际源码/工件，写root-review-v3校验JSON和本报告/current。未改B源码、工作区、工件或旧冻结。下一窄修仍由原B唯一owner负责，不重派A。

## 环境
复用59唯一工作区与D:/develop/env/gradle；Root无构建、设备、Git或外部消息。原A14项及分类74项输入未改，继续复用。

## 执行与结果
host真实assembleQahost日志BUILD SUCCESSFUL、test修正后compile/assemble日志BUILD SUCCESSFUL；host包内构造函数摘录含CreateCategory(String,String)，支持产品修复已打包。实际APK匹配，旧宿主问题采纳收口。
候选verify_binding、verify_freeze、audit_import_closure及预算生产校验Root实跑全部ok。原离线反例产品源码漂移、host不符、构建来源失败均exit2/设备调用0；happy path仅离线。新增产品/配置来源消费采纳，不重复修这部分。

## 未覆盖项与具体返修
1. MaterialGatheringJourneyTest.kt:432起tapDocumentsUiSave使用/data/local/tmp/mat-export-ui-${System.currentTimeMillis()}.xml，直接uiautomator dump写入、cat后rm -f。没有初始不存在或独占创建证明；毫秒命名不证明归属，既存同名对象可能被覆盖/删除。须取消该公共暂存写删，优先现有Accessibility树查保存按钮；确需文件则独占创建确证的新资产并保留，不按时间戳直接赋予清理许可。本轮只有代码检查，无设备文件被实际删除。
2. 导出点击保存后按MediaStore.Downloads前缀和ID差集找文档，再拼content://media/...，没有捕获真实CreateDocument返回URI，也未明确固定DocumentsUI提供者/目标目录。当前代码可能因提供者或可见性差异无法发现文档，且不能据此证明实际授权URI可读。需以真实生产结果URI做应用读回及权限记录；MediaStore差集可作辅助，不作为默认路由/权限来源假设。具体运行行为仍未设备验证，不推断必然失败。
3. 导出失败的资产线索只在内容全部断言后recordAsset。URI/ID已可见时即应落盘精确身份和保留策略，避免读取失败后新外部文档没有归属证据；不新增删除权限。
4. URI读取捕获异常后throw AssertionError("...$it")未附cause，丢失原异常链。直接保留原异常或包装附cause，并保留取证错误suppressed。
实现已有保存/内容严格断言，相比v2进展采纳；以上只修真实适配，不回退file://、预写文档、Mock或放松失败门禁。

## 原始证据
E:/creation-reading-assistant/task69-md-material-closure-v1/B/v3下REPORT/ready/src；integration/v3下ready/STATUS、logs/01-assembleQahost-host.log和03-compile-assemble-test.log、evidence/dex/host-constructor-proof.json、counterexamples.json、candidate真实工具与冻结；Root输出root-review-v3/checks.json及00-*生产校验结果。原路径引用，旧FAIL保留。

## 恢复与副作用
本轮未启动设备，无新恢复义务。下一导出文档保留须精确记录为新增资产，不声称外部目录零增量或整盘回滚。68已核保护面恢复及63有限链证据不重跑。Root仅新增数KiB JSON及必要D交接，无工程/缓存/APK复制或既有资产删除迁移；automation-2/3未改，维持PAUSED决定。

## 交付状态与下一步
69-v4-B原owner只修导出适配四项，再做必要androidTest编译/打包和对应新冻结绑定；host及产品/JVM证据直接复用，不重建已采纳宿主、不全量复验。公共同一文件/工件绑定串行有直接依赖，不制造其他并行任务。完成后Root核验形成单次设备方案，不自动设备。全产品分母未知，不报整体百分比，不称实体手机/MIUI通过。
