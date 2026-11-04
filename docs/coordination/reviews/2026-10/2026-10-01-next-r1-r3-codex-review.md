# R1–R3 总指挥复核（2026-10-01）

## 结论

R3在构建配置/静态产物证据范围内通过；R1、R2返修，暂不合流。当前轮完整任务验收1/3=33%。已收到三份报告不等于三个任务通过，A–E历史恢复计划100%也不等于全产品完成。

HEAD仍`e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空；未写共享产品源码、未合流或提交新候选、未操作设备。校验脚本`E:\creation-reading-assistant\review-r1-r3-20261001.py`新鲜退出0，机器结果`next-round-r1-r3-codex-review-20261001\verification.json`。本轮回读原始证据并审查源码，没有重新执行Gradle或设备测试。

## Standards

### [P1] R2超出最小修复边界，扩大明文HTTP放行范围

隔离`WebDavBackup.kt:239–264,398–413`用手工字符串解析替换URI主机解析，新增单段主机、.lan/.home/.internal/.home.arpa放行，还统一改了认证字符集。任务明确保持HTTP限制/认证处理。网络策略扩展不是HEAD405/MKCOL必要修复，未给出受控兼容证据；应恢复既有主机判定合同，在可靠URL解析基础上修复反例。编码变更也需独立合同依据，否则撤回。

## Spec

### [P1] R1非空payload透传使后续本地事实仍无法push闭环

`SyncRepository.kt:477,510`在payload非空时直接return parsed。pull会保存非空远端payload；本机后续阅读/取消完成等写提升列，不保证同步更新payload。故“拉入旧progress→本地更新提升列/revision→push”仍发送旧percent/state/time/内嵌metadata，违背本轮要求的实际pull/push闭环。现有测试把非空透传当守卫，未覆盖真实生命周期。应保留未知payload键，同时按明确wire合同更新可同步事实及信封一致的metadata，并补此调用链回归。已有非空session缺filePath问题可在同一序列化函数中按真实合同处理；无有效值时明确失败/跳过原因，不伪造路径。

### [P2] R1完成时间未按合并后的状态联动

`SyncRepository.kt:270–273`只有explicitState非完成才清空completed_at。本地reading/shelved，远端省略状态但带合法completedAt时，状态仍reading/shelved却落了完成时间。应以最终合并状态决定是否允许completed_at，覆盖reading/shelved缺状态+带时间反例。缺状态不推断完成，100%不默认为finished。

### [P2] R1新Book分支仍用旧str解析显式JSON null

`SyncRepository.kt:176–190`新记录通过str构建base并立即upsert返回，绕过optString。JsonNull可产生字面字符串"null"，与报告“新记录显式null落实体默认值”不符。新旧记录用一致的安全解析规则并覆盖null/非法类型/非有限数值边界；不以JSON.stringify不会产生null作为依据（该断言不成立）。

### [P1] R2实际请求主机与安全判定主机不一致

`WebDavBackup.kt:255`用substringBefore(':')解析主机。离线反例`http://127.0.0.1:80@public.example/backups/`：安全判定得到127.0.0.1而实际URL目标是public.example，可绕过明文公网限制并发送Basic凭据/备份内容。反例仅离线解析，未发公网请求。用与OkHttp请求一致的URL解析器读取真实host，明确处理userinfo、IPv6、端口，不能把含user-info的URL文本当作目标host。文件名正则的\\s还接受内嵌tab/换行，须与控制字符拒绝合同一起补验。

## 新鲜证据回读

- R1红测XML实算16项/13失败；当前定向XML121项/0失败错误跳过。证明已有修复覆盖部分风险，不覆盖上述新反例。
- R2当前三个XML实算18+14+12=44项/0失败错误跳过；报告58包含未在当前XML目录找到的12+2项，不能认证为同轮58通过。返修后保留实际选择器、日志、XML和时间戳对应，按可追溯运行计数。
- R3归档unsigned/debugfallback/AAB哈希与报告一致，四分支日志及退出码0/0/0/1对应预期；CI失败明确来自缺签名守卫。现场apksigner验证归档debugfallback成功，v2签名、CN=Android Debug、证书SHA与报告相同。
- DEX分析文件与合并manifest证据已落盘；结论限指定测试组件排除，R8重命名后按名字搜索不是通用全类排除证明。未证明产物运行期兼容、正式签名或历史升级链，不发布。

## 下一步

只交回R1/R2两个原窗口补必要反例，R3停止。保留原报告/红绿日志，不进行第三次全量调查。修正后统一构造基线+R1+R2+签名配置候选，再做相称集成门禁；真实LAN/协议兼容仍需独立验收。不复用旧142路径提交授权。

规范轴1项（最严重：P1网络策略边界）；需求轴4项（最严重：P1主机判定/同步闭环）。
