# 210-v1：外部入口设备验证（ProfileRoute笔记胶囊→临时查阅→Back回Profile）

2026-10-31接任协调者编制。用户持续指令"接着下一个任务做"+此前设备授权延续。

## 目标

关闭覆盖矩阵D3行：在新APK上验证ProfileRoute「我的→阅读笔记→临时查看来源」真机链（外部入口→临时查阅→Back交还导航回退→回Profile）。

## 范围

- 新增androidTest：`ReaderExternalEntryProductionTest.kt`（acceptance包，沿批B production test模式）
- 验证链：fixtures种book A+notes→launch→底栏我的→notes区pill→临时查阅打开（book A note位置）→commit（active=A, LIFO=[]）→无返回按钮（hasReturnableTarget=false）→系统Back→回Profile
- 断言不含会话内还原链（外部入口active==null→pop路径，graph test已验）

## 不含

- P5清缓存失败场景（须用户加签）；D1接缝；reader内链（批B已验）；EPUB/MD

## 机制

- 复用批B全部脚本（preflight_209/prepare_inputs_209改spec-C/run_batch_209 C/postflight/recover参数化）
- APK须重建（新增test类）→新APK对pins
- 恢复：churn PRE_FAILED预期（同批A/B）→C4 recover-only收口

## 预算

写测试+构建+设备批+报告约60分钟。
