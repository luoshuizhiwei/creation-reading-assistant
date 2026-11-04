# N1–N3 总指挥回读与下一轮安排（2026-10-01）

## 当前结论

基线 HEAD仍为`e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空。N1/N2产品候选仍在E盘隔离目录，未合流、未提交、未push；上一轮142路径提交授权不自动扩展到这些新候选。

本轮3/3报告已回读；按完整任务验收计数为1/3=33%：N1限定完成，N2构建产物部分确认但报告证据需补正，N3计数确认但语义分类需勘误。这是本轮任务计数，不是全产品百分比。A–E历史恢复计划仍100%；全产品包含可选范围/跨端，尚无批准且可核算的总分母。

## N1：真实进程中断限定证据成立，原子性不成立

- 新鲜汇总`E:\creation-reading-assistant\next-round-review-20261001.py`退出0；结果`next-round-codex-review-20261001\verification.json`。
- 回读6个最终场景，均有kill退出0、旧PID GONE、新PID不同、ZIP SHA不变及重试收敛记录。2次K5工具失败保留。定向XML实算36 tests/0 failure/error/skip；未再次执行设备破坏测试。
- K3/K4/K5明确存在DB已更新但文件尚未更新/缺失；重启不会自动修好，需再次导入。整包原子性判FAIL/不支持；不能以类注释已有声明或6个重试测试通过消除用户可见恢复风险。
- F1孤儿解压缓存、F2 torn staging进入后续备份为实际残留风险，列入后续改进，不能写“零风险”或全恢复安全已通过。无证据不得按后缀批量删除未知用户文件。
- 现场发现原测试APK未恢复（pm list只有qahost）。Codex从N1冷备安装original-test.apk成功，随后现场重新核对宿主/测试APK完整SHA、51持久文件逐项SHA、density280/night no/stay0/rotation0/accelerometer1均等于本轮N1基线。此处夜间no来自N1测试前快照，不沿用上一轮yes。
- N1测试接缝和两份测试文件仍未集成。hook默认null可作为候选；设备驱动及原始证据须与接缝一起可追溯保存。

## N2：确认未签名Release APK/AAB，拒绝扩大为可正式发布

- 当前unsigned APK SHA `af49cc390ffa7d2d1ee4acf97bc2c6797c62a506ba64be52bd618656a7809c7d`、AAB SHA `acabc324946cf6f0947ee410aa67b4d045f84757654544b51e2332807bcf7a71`与报告一致；文件大小亦一致。
- 当前aapt确认主包名、versionCode3/versionName0.5.1、min24/target35；apksigner实际返回DOES NOT VERIFY/Missing META-INF/MANIFEST.MF，符合未签名APK边界。
- 隔离build.gradle.kts存在默认unsigned+显式debug fallback改动。当前app-release.apk不存在，无法按报告中的SHA复核其debug签名证据；现存build-release.log仅首次构建日志，后续构建应保留对应日志/产物。
- 报告写基线提交说明chore(recovery)…与实际fix(android): integrate verified recovery candidate and reader state safety不符，须勘误。
- aapt list是ZIP条目，不能据此证明DEX中不存在测试类。凭据属性识别不证明真实签名/历史升级成功。历史0.1.x是否同一原生包/证书需实际证据，不根据旧Capacitor历史推导。
- “唯一阻塞为签名”“Release-Ready/100%达标”证据不足；R8包运行期兼容性、升级链及仍待修复运行缺陷未被本构建证明。暂不tag、发布或上传，也不为报告中的商店规范时效性表述背书。

## N3：两项修复方向有代码依据，180行分类不是总功能分母

- CSV新鲜实算180个唯一ID，类别45/123/2/6/4与报告一致，仅确认算术。
- HEAD SyncRepository.applyBook读取local却新建BookEntity upsert，未携带本机路径/状态等字段；applyProgress/Session同样新建实体，省略提升列。实体默认值使已接受远端记录覆盖本地读取事实的风险成立。当前未做红/绿复现，不能宣称整库所有书必然即时丢失。
- 会话/进度提升列不等于全是本机私有列；修复须区分远端载荷显式给值、缺键、null及本地事实，不能一律local.copy导致远端更新永远不生效。
- HEAD WebDavBackup.test使用HEAD，405直接失败；已交付PROPFIND列表和Legado ZIP应保留。错误应写“HEAD探测不兼容”，不能称“PROPFIND被405拦截”。MKCOL/重试属于另行可验证行为，不自动引入全量网络重写。
- SyncOptions是功能扩展，本轮只安排数据缺陷最小修复；不需要先让用户裁决才能修本地列覆盖。ADDED_DESC保持现有排序，旧候选保留，未授权删除或永久舍弃。
- “工作树0 diff即完全吸收”不是语义证据；必须核对HEAD符号/调用路径与需求。S08不写源文件仍有offset映射/撤销风险，不能宣称数据零风险；尚未授权一份只读报告自行永久裁掉S3b。
- 真kill已经在MuMu可测，不能再全部归为真机硬件阻塞。真实硬件仍未测；整包原子性为实证不支持而不是未测。

## 下一批互斥任务

1. R1 GLM：SyncRepository本地事实与远端提升列合并修复，限定该文件及新增专项回归，隔离E盘。
2. R2 DeepSeek：WebDAV HEAD405兼容及PUT缺目录必要恢复，限定WebDavBackup及新增专项回归；保留Legado接口/已交付合同。
3. R3 原N2窗口：保留真实Release产物与日志、核实DEX隔离和签名分支，补正N2报告。禁止设备操作，避免与R1/R2并发共享构建目录/缓存。

N3报告勘误由上述总指挥结论先行覆盖，不重跑180项全量调查。待R1/R2完成后总指挥统一合流到新隔离候选，按变化范围门禁；新提交需要新的精确范围，不复用上一轮授权。
