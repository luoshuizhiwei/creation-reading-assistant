# 69-v1-B Root核验：部分源码交付，返修后合流

## 任务编号/版本
69-v1-B，2026-10-20香港时间。用户明确“A尚未完成，先核验B”。不重复派A，不启动合流或设备。

## 输入指纹
B候选MaterialGatheringJourneyTest.kt SHA256 5eb0024dff01228719d778ab76f9d81364067a6dc1d66302a2c5560a5af0fc98；patch 43193fbd3f53e200ff0236d3a40da0d2b350d5a26e617035d07fef41b86e6567；基线1e31f24e7e44001896185d32c24d42001ea551f2959680deb74bb3ae10207a66，Root实算三者核同。MD工作区仍aabd083a…92dc，A目录不存在；integration只有STATUS-69-v1-B.json。

## 所有权与改动
Root只读B、对应生产调用链、指定依赖AAR和当前交接；仅写task69/root-review-B/checks.json及本报告/current。未改B原交付或共享工作区。相关Python/Java活动任务查询为空，但用户已说明A未完成，不能据进程空推定A已取消或抢占其所有权。

## 环境
沿用59工作区与D:/develop/env/gradle；无新工程、构建、设备、外部派发或Git操作。已加载指令与原68恢复证据复用。

## 执行与结果
仅采纳B最小源码差量确实存在、基线未变以及分类ID不一致缺陷定位。22项原检查是源码字符串/结构检查，不能替代Kotlin编译或运行；import检查跳过androidx前缀，故“import全部可解析”超出证据。
1. 候选第9行import androidx.activity.result.ActivityResultContract路径错误。Root直接读取本机androidx.activity/activity/1.8.2依赖AAR的classes.jar，实际存在androidx/activity/result/contract/ActivityResultContract.class，不存在所导入路径。既有NotesExportShareAcceptTest也使用正确路径。编译未执行，不能写源码已可编译。
2. 分类验证只解析payloadCategoryIds，未断言本轮categoryRowId被关联、非空且无悬空；mat_30_save仍writeEvidence(true)，与“不写通过”说明矛盾。
3. ReaderLayerBuilders.kt:648生成并返回ID-A，但派发CreateCategory(name)；ReaderViewModel.kt:453再生成ID-B入库。分类关联不一致的生产缺陷定位采纳。最小修复优先只统一CreateCategory身份传递，不顺带改无本轮证据需要的CreateTag。
4. 导出通过activity.setContent单独装配NotesSheet并注入file://结果；可用于受控组件文件输出检查，不能证明真实阅读器更多/笔记与标注入口、DocumentsProvider文档权限或同批真实SAF链通过。B报告对此部分边界有说明，但“受控SAF/实际URI权限”仍须收窄。
5. target.delete()在写入前无条件请求删除已有目标；本轮随机命名不是既存文件删除许可。应按精确新文件创建和冲突拒绝处理，不删除未知碰撞文件。
6. writeFailureEvidence用两层未消费runCatching，取证JSON/截图失败会静默丢失。须保留主异常并附加取证失败，不能吞错。

## 原始证据
E:/creation-reading-assistant/task69-md-material-closure-v1/B下REPORT-69-v1-B.md、ready、src、patch、tools/verify_b_source.py、evidence/static-checks.json及integration/STATUS；Root实算及指定依赖类检查见root-review-B/checks.json。未复制APK/历史资料。

## 未覆盖项
A未交付；B尚未真实编译、合流、构建、新冻结绑定或设备验收。本轮没有任何新模拟器PASS。标签/分类与导出源码的存在不等于对应运行断言通过。

## 恢复与副作用
无设备动作与新恢复欠账；68已采纳恢复结论保持。Root仅新增数KiB JSON和必要D报告，未删除迁移既有资产；automation-2/3不更改，保持暂停决定。

## 交付状态与下一步
B部分实现交付采纳，完整功能链及合流拒绝放行。69-v2-B原owner精确返修上述点；A继续69-v1-A，不重复派发。B新增生产文件所有权限ReaderModels.kt、ReaderViewModel.kt、ReaderLayerBuilders.kt的分类ID接缝和必要定向回归，其他共享产品文件先报Root。实现时先核独占/基线，不覆盖A或他人；产品差量先交候选，不提前抢写工作区。
A交付且B返修完成后B唯一合流owner完成一次必要真实编译/构建与新来源/冻结绑定，不造静态验证框架，不自动新设备。若真实SAF权限/归属需要合同调整先形成精确方案，不能用file://组件检查替代验收。全产品分母未知，不报百分比或实体手机/MIUI通过。
