# 79-v4-BC Root核验（2026-10-21）

任务编号/版本：79-v4-BC / Root。Root范围为交付与原始证据只读核验、D交接；没有编译、设备命令或外部派发。

## 输入指纹、所有权与改动
共享Material源码41870a3a6ffd011af2a854da06896b31c01c7d0ed7316dfbcc9c732cac7e588a实际匹配ready。host53804439B/SHA ceb60dd1a24455efbfcbcac4143a058b2d30e45b6f42659653ec7034925ae79a、test9832275B/SHA 8f60122689649d631e200dd2bde3f0ce12faadac62b2c1a1fb5e264b0f972f53核同。patch实际SHA db72b8eb0d36dcfb1302afbd3e5097e6cceda273b889f98b42d9ab08dc9f6337核同。v3旧test单份保全d75bf600…828f实际SHA核同。四冻结输入原件及本attempt input-bytes快照逐项核同。未重跑未变JVM/绑定闭包。

## 环境、执行与结果、原始证据
integration/v4/logs/assemble-test-v4.log有BUILD SUCCESSFUL，build-result-v4 exit0。唯一v4 attempt20261021-104327-a36df5，baseline-result原始错误为焦点com.hypergryph.arknights/com.u8.sdk.U8UnityContext。protected-baseline-initial原dumpsys同时显示mCurrentFocus、mFocusedApp及visible=true任务指向该游戏，支持当时游戏真实前台，并非仅旧历史文本。未推断谁在操作或有权打断。device_actions记录install_attempted=false/phase_started=false，probe和四业务旅程全部NOT_RUN；业务分母四旅程0PASS/0FAIL/4NOT_RUN，probe另列，不混成五业务旅程。前置基线BLOCKED，不能声称夹具真实执行已通过。

## 恢复与副作用
Root直接比原基线与recovery/protected-original-final：protected_files/external_files/db_groups/apk_pair/settings/uri_permissions/pkg_states均相同；final-protection-result71私有/820外部、diffs空，五恢复回执ok。没有新安装仪器。前台状态不同：基线游戏，结束app.lawnchair/app.lawnchair.LawnchairLauncher；所以接收以上保护数据/设置合同范围恢复，但不称全部初态恢复，不能说未影响使用。Root未擅自重新打开游戏。实例保持运行结论引用本run原回执，不是本轮Root实时设备探测。

## 未覆盖项、交付状态与下一步
夹具修复与构建交付，运行时验证仍未运行，真实选择器/保存/URI/四旅程未验。报告关于v3 patch差异系中间调试稿的原因只属执行者陈述，未补独立原件证明，不据此改历史。79目录实测19499781B（包含旧v3 APK单份保全及已有A/B/v3/v4，非纯v4新增），不删除资产。自动化未改，保持PAUSED。
已向用户提出本次设备占用的具体选择：允许切桌面验收但保留游戏进程/不关重启，或保持设备不动等待空闲。此前允许使用实例不能证明此次游戏占用可被打断；等该选择后再设备。离线可复用当前已匹配APK与充分日志，禁止因下一编号再构建。后续唯一owner为必要新attempt台账/入口参数生成最小受绑定差量，保留旧冻结原字节，不覆写同批重试。无新的真实缺口不制造并行审核。所有权、身份、恢复合同不明停止依赖操作。全产品分母未知，无实体手机/MIUI结论。
