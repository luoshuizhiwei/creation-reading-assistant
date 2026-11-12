# UIX-CLOSE-v1 Root 交付复核

日期：2026-11-08。裁决：`NODE_RECOVERY_VERIFIED_BEHAVIOR_EVIDENCE_PARTIAL`。停止新增修复代码；剩余为有限证据核清，不重启第八轮产品改动。

本轮只读检查交付文件，未操作手机、未重跑测试。

## 已确认

- 实算 D 仓库 APK SHA256 `d9c5adcf364244365549eecb8b8df6f8be50b480e16599dfb97de6eaad352545`，匹配第七轮候选。
- `E:/creation-reading-assistant/uix-v1/closure-install3.log` 为安装 Success，前两次确有 INSTALL_FAILED_USER_RESTRICTED；安装日志自身不包含设备端APK哈希，不据此声称设备二进制三方独立核验。
- 已解析 `closure-v1/evidence/uidump-reader-final.xml`：顶栏返回/更多与上一章/下一章均有clickable和自身描述；底栏五个clickable父节点恢复，后代分别有目录/听书/灵感/外观/设置描述。
- 已解析 `uidump-shelf-final.xml`：整理入口clickable父节点恢复，后代有打开书架整理描述；书架四图标按钮描述存在。
- `restore-check.txt` 有动画1.0×3、reduce_motion null、触感1及四服务配置输出。

## 报告超出证据或尚缺的内容

1. 仅凭两份UIAutomator XML不能证明TalkBack朗读、合并后焦点/名称正确。报告“TalkBack可读”需降为“后代描述存在，TalkBack未运行”。不把UIAutomator树直接等同Compose未合并树。
2. 本批未提供整理入口和底栏五钮逐项实际激活结果；报告前段功能说明不能代替运行证据。关联控件“同构通过”仅是推断，不是各自设备PASS。现有Compose测试源码也不等于本次已执行，缺本批运行rc/XML。
3. 现有restore-check原文未含卸载/pm path、临时文件清理及两自动点击应用重新启用的输出；报告自述恢复尚缺对应原始记录。先找现有证据补链接，不能为了补证再次卸载或重复清理。
4. 报告页首仍写等待ZCode，与第六节完结冲突，需统一当前判定与限制。

## 唯一接续动作

由原owner先引用既有操作日志/截图/测试结果，补齐上述六个剩余控件的实际激活、设备安装身份和恢复证据；充分则直接关闭本UIX范围。没有现成行为证据时，只在已授权且安全的本轮设备/数据基线和单owner条件下补一次这六项激活检查；不全量重测，不修改产品，不安装TalkBack。缺恢复原证的项如实记录报告声称/未独立确认，现态查询不能冒充历史过程证据。

若激活成功且恢复核清，关闭为本次节点恢复和触摸交互已验，TalkBack未验限制保留；若实际激活失败，再提交具体失败证据按原合同剩余修复预算裁决，禁止凭描述拆分自动返工。
