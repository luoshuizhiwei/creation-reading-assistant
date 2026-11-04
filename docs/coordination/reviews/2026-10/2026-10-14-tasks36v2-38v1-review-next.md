# 任务36-v2/38-v1实核与定向返修

## 任务编号/版本
2026-10-14 06:25香港时间核查。两项已交付，主机闭环均返修；下一36-v3/38-v2互斥并行，Root只核查规划，不设备、不构建、不自主实施。

## 输入指纹
Root实核36-v1源码manifest1019项全部匹配，原双APK及source/acceptance/freeze SHA未漂移。完整输入SHA与新交付文件SHA见E核查verification.json。36-v2只有runtime主机增量，38-v1只有host/配置/离线材料；不把任务报告自述当Android重新验证。

## 所有权与改动
Root仅自有E核验与全局交接文档。下一36-v3所有权为调度/采集/映射/消费者；38-v2为准入/基线/备份/恢复/安全操作编排。均仅自有E新版本目录，原交付只读，禁止修改Android、公共输入、原APK或彼此模块。新工具冻结等核验后唯一owner合流再做；本轮不重复Lint/打包。

## 环境
Windows/Python3.13，未调用实际ADB或设备；所有device_session外部命令均传MockDevice。D HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存空。当前外部可用模型菜单未实查，不推断反代wrapper等于真实底层模型。

## 执行与结果
36原自测13反例实际exit0，但未覆盖独立反例。Root实际调用：旧producer run+unknown页面+无marker通过；阶段逆序后重排stage_index/nanos通过；临时入口行改写后跨章恢复通过；同方法两个不同UUID新文件及错误回执绑定通过；虚构B类/方法通过。实际C生产格式含“无法打开这本书”和“preview_semantics_present=false”却被拒：消费者把字符串含preview当显示预览，同时不识别真实错误文案。consume读取method目录而真实生产者为producerUUID目录，映射函数不读取文件不返回映射且未接到consume。invoke-mock直接写合成JSON，plan是文字步骤，没有可注入命令层的真实执行/采集实现。

38实际独立mock：原APK清空、settings/mappings/uri权限/device state变化、创建物无清理后读证仍返回restored=true，只执行库存读取；没有恢复动作。admission仅admission_ok=true且缺run/serial/保护清单的基线获得admission=true。空DB成员且末次inventory失败仍forensics=true（忽略ok_after和错误列表）。备份无本地pull/size回读、不拒既有stage；库存只包私有目录，未覆盖配置触碰根。device-run成功只打印交由owner按JSON执行，尚无完整执行编排。

38原测试保留原日志18/18；Root原样载入并迁移测试输出先得16/18，其中2项因audit路径不在allowlist而提前拒绝，不能称产品失败。随后仅给自有audit根加入测试allowlist、临时目录强制E隔离重新执行，结果见38-selftest-isolated.log及verification.json，测试fixture未经产品修改。关键缺陷来自上述独立反例，不以旧18/18推断恢复完整。

## 原始证据
[独立验证与SHA](<E:/creation-reading-assistant/tasks36v2-38v1-codex-review-20261014/verification.json>)；[36原自测重跑](<E:/creation-reading-assistant/tasks36v2-38v1-codex-review-20261014/36-selftest.log>)；[核查脚本](<E:/creation-reading-assistant/tasks36v2-38v1-codex-review-20261014/verify.py>)；[38隔离重跑脚本](<E:/creation-reading-assistant/tasks36v2-38v1-codex-review-20261014/rerun38.py>)。原任务REPORT保留只读，不覆盖执行者报告。

## 未覆盖项
设备全部NOT_RUN，D/F NOT_PROVEN，X/实体手机/MIUI/发布不扩大；全产品分母未知。冻结产物有效不代表新主机工具已冻结或设备就绪。

## 恢复与副作用
未实际设备连接/卸载/安装/设置修改；mock argv不等于真实执行。D产品/Git不变，无正式库/原损坏仓库/桌面HOLD/私库访问。核查初次38测试沿用原脚本系统temp目录，后续强制E，不擅自删除其资产。无子代理、外部消息或定时任务。

## 交付状态与下一步
交付2/2，完整主机闭环验收0/2；保留已有正确阶段名、12列跨章冻结、严格consumer引用、路径缺失拒绝和首次基线不覆盖。36-v3补实际命令适配/harvest/真实schema与身份消费；38-v2补实际基线与恢复操作编排，拒绝空基线及失败库存。两线可立即并行。建议36-v3 DeepSeek Pro（若菜单无Pro，用实际可用DeepSeek并提高推理档），38-v2 GLM5.3；替代均为对方或可用Gemini，GLM5.2不可假定可用，不用GPT/Claude外派。DeepSeek粗估各60–100分钟。不增加填充审计任务；Root核查通过才合流与新工具冻结，再限定MuMu。
