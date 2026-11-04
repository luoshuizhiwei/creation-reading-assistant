# 任务142-r3：保存重试不能掩盖更新请求的失败

2026-10-26香港14:42；原owner同Claude global Flash，单一reader/Gradle。r2实际53行test patch/REPORT/raw/XML Root已读，6tests0fail0error0skip、测试97630B/cf731c0e核同；接受调度修正与六目标事实，不扩展为全产品/设备通过。

````text
只处理r1现有ioProgressSave完成回调中的真实顺序缺口：progressSaveFailure只在null时记录，progressSaveFailureSeq也只记第一次；其后任何seq大于该首次失败的成功都会clear。因此同一reader同一book依次派发seq1/2/3，按“1失败→3失败→2成功”完成，3的失败因failure非null未记其序号，2成功因2>1清空失败，await可能错误返回成功，掩盖比2更新的失败。该路径是代码推导，尚未运行，不冒充已复现。

用同scheduler+受控gate的真实SaveProgress/共用ReaderExitBoundary确定性红复现这一顺序：3失败之后仍待2，放2成功后必须保持reader/错误可观察，不导航；再只有更晚真正成功保存覆盖该失败时才允许一次导航。不得放松现有六目标、不得抄actual。反向完成顺序“更新请求先成功、旧失败后完成”的可重试裁决也明确处理，不能因完成顺序把旧失败永久卡死；若Repository实际SaveOrder已覆盖旧请求，按真实生产排序合同解释，并仅以真实成功请求证明覆盖，不能用任意其他book/格式成功清掉失败。

先根因/失败证据再局部最小修复原VM记账，不新增failure-generation/wakeup框架，不改ReaderProgressSaveOrder排序/Repository。保留首个原异常因果可观察，但不能丢更新失败的覆盖边界；避免把“某次成功”当“全部失败已解决”。不为一次性测试造工具框架。若必须超出范围准确BLOCKED。

唯一工程E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。先核VM54725B/48bcefab85842eac8f4c842781f5376d7ff51d71e1f561c43396619522c289c0与VMTest97630B/cf731c0e254929a92bb17f3843f602aeee31868992547af8d21106bf2c2dc901。只VM进度保存记账+必要定向测试；其他reader接线、Search/B草稿、host/inputs/APK/公共配置/deps均不动。原142/r1/r2/141所有红绿交付冻结，新E:/creation-reading-assistant/task142-reader-exit-save-drain-v1/recheck-r3/ready.json，最小patch相对r2、前后SHA、REPORT、必要新case红绿及最终本批目标XML/raw；原六目标+新顺序case定向验证，不全类/全JVM/lint。生产变化后一次必要compileDebugAndroidTestKotlin，无assemble/设备/APK。15分钟，差量256KiB/raw1MiB；不复制工程缓存APKs历史。

无Git暂存commit push PR发布/云/正式库/私库自动记忆/任何ADB VM探测启停安装设置hostmain attempt F1/旧资产删移压迁或其他进程处置。Root不旁路build/ADB。B集成仍等待该唯一Gradle资源，不造泛审并行。当前产品74d106b6/test28173170冻结不含修复；离线通过不设备通过。完成后等Root核，不自动后批。最终总结末行：这是任务142-r3。
````