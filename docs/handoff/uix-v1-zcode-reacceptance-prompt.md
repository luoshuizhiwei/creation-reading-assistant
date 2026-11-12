# 给 ZCode 的 UIX-CLOSE-v1 第一轮联合验收执行提示词

> 日期：2026-11-08。状态：READY_FOR_UIX_CLOSE_V1_ROUND1。
> 本轮严格遵循《UIX-CLOSE-v1 有界收尾合同》（docs/coordination/2026-11-08-uix-bounded-closure.md）：
> 先验现成第七轮候选包（d9c5adcf），禁止随意试改；两轮内必须收官！

```text
你接任创作阅读助手原生 Android 恢复项目：执行任务 UIX-CLOSE-v1 第 1 轮联合真机验收。实体手机 c49ac6cf 仍为你独占，自动化保持暂停。未经明确授权不 commit/push/PR/发布，不删除或移动既有资产，不改 current/state，不写私有记忆库。

一、本轮被测候选包（现成第七轮，严格冻结不修改）

- 路径：android/app/build/outputs/apk/debug/app-debug.apk
- 体积：52,812,949 字节（≈50.37 MiB）
- 构建时间：2026-11-08 18:35:01
- SHA-256：d9c5adcf364244365549eecb8b8df6f8be50b480e16599dfb97de6eaad352545
- 联合补丁：E:/AgentWork/CRA/uix-v1/uix-v1-r4-joint.patch（实际物理磁盘 98,237 字节，已核清为 91,742 字符落盘编码换算，完全同源绑定）
- 完整验收报告基线：E:/AgentWork/CRA/uix-v1/closure-v1/REPORT.md

二、第七轮改动核对（解决第六轮 clearAndSetSemantics 动作误清除）

你在第 6 轮实证中确定了两个核心事实：
1. 阅读器顶栏/Scrubber（返回书架/更多/上一章/下一章）：消除 IconPedestal 中间层 Box 后已在真机上【首次全绿 PASS】！
2. clearAndSetSemantics 会误吃掉底层 Click 动作：导致书架▼变成 clickable=false，底栏 5 钮 a11y 节点蒸发。

【第七轮已精准落地】：
1. 彻底撤销 clearAndSetSemantics；
2. 书架▼ Row：保留 Row.clickable(role = Button, onClickLabel = "打开书架整理")，并将无障碍描述挂在内部下拉箭头 Icon(contentDescription = "打开书架整理") 上，确保 clickable=true 且具备点击动作；
3. 底栏 5 钮（ReaderFooterAction）：保留 Surface(onClick)，将 contentDescription = label 挂在内部图标上，确保 onClick 动作与 a11y 节点完整存在，杜绝蒸发；
4. 顶栏与 Scrubber：原样保持第 6 轮已 PASS 的单层 IconButton 写法。

三、ZCode 固定矩阵极简抽查（1~2 分钟）

在实体手机（Redmi K50 Ultra, serial c49ac6cf）上：
1. 安装候选包：
   adb -s c49ac6cf install -r android/app/build/outputs/apk/debug/app-debug.apk
2. 打开阅读器与书架抽查 UI 树：
   adb -s c49ac6cf shell uiautomator dump /data/local/tmp/uidump.xml
   adb -s c49ac6cf pull /data/local/tmp/uidump.xml .
3. 核对重点对象（其余项已在历史轮次 PASS，复用证据无需重跑）：
   - 【重点 1】书架▼：检查 clickable="true"，确认具备可访问名称与点击动作；
   - 【重点 2】底栏 5 钮（目录/听书/灵感/外观/设置）：检查 a11y 节点是否已恢复呈现，且具备点击动作；
   - 【抽查 3】顶栏与上一章/下一章：确认持续保持 PASS。
4. 恢复设备基线（动画 1.0、reduce_motion null、haptic=1、4 服务串一致、卸载应用、全清临时文件）。
5. 结果交付：
   - 将真机实测 XML 与截图写入 E:/AgentWork/CRA/uix-v1/closure-v1/；
   - 若上述重点项通过，在 E:/AgentWork/CRA/uix-v1/closure-v1/REPORT.md 中记录通过证据，宣布 UIX-CLOSE-v1 完结，不再做下一轮！
   - 若仍有失败，如实记录具体失败节点的系统属性，进入第二轮。

最终总结末单独一行写：
这是任务 UIX-CLOSE-v1。
```
