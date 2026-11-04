# 125-v1 ZCode GLM5.3优先免UI接续

用户最新明确“继续自动化，优先使用zcode的glm5.3模型，如果对它的工作成果不满意可以要求它进行复检”。此指示覆盖旧ZCode队列停止及必须Claude两个既有会话的执行路由；不覆盖免UI、资产保护和设备批次门禁。Root继续协调、验原件、决定接续。GLM-5.3为首选，不替换成Flash冒充；失败不买额度/换账号/供应商。不合格交付要给同owner具体缺陷、证据和复检成功标准，不重复整套充分验证。

## 本机实际核查

- 安装D:/develop/AI Agent/ZCode/ZCode.exe，Windows签名Valid，签名者北京智谱华章科技股份有限公司；开始菜单快捷方式定位得到，不通过UI启动。
- 自带官方CLI D:/develop/AI Agent/ZCode/resources/glm/zcode.cjs，--help输出0.17.0，Node D:/develop/Nodejs/nodejs/node.exe v24.22.1。
- 官方源码说明支持node bundle的--prompt/--cwd/--mode/--json和--resume；https://github.com/zai-org/ZCode/blob/main/apps/zcode-cli/docs/usage/01-quickstart.md 与03-config.md已本轮核对。
- CLI项目探测目录E:/creation-reading-assistant/task125-zcode-headless-v1/probe，仅含本次配置及必要内置catalog副本。zcode.json关闭memory/use、hooks、plugins、skills、mcp、subagent；不启用--memory-bench，不读取Claude/DSH/.codex/memories。
- 首次--prompt /model启动前报内置provider路径打包错误。未改安装；用发行包支持的ZCODE_BUILTIN_PROVIDER_CONFIG_FILE指定本次catalog副本，ZCODE_BUILTIN_PROVIDER_BUNDLED_CONFIG_FILE指向安装包catalog。路径错误已越过。
- 模型选择仍阻断：/model、GLM5.3无工具ready测试以及/model指定ID，都在模型请求前返回 `Error: Select a model before sending a message` / exit1。没有GLM模型成功响应，不能称免UI派发已通。
- 使用本次不含密钥的personal-provider.json声明defaultModelSelection，先按旧历史Start Plan、再按发行包确有GLM-5.3映射的account:bigmodel-individual-coding-plan核对，仍同错误。该配置仅本次临时文件，不改用户全局模型/账号/认证。没有读取或输出凭据，没有登录/购买/换账号。
- 发行包catalog确认支持GLM-5.3（非Flash）Coding Plan；不等于用户CLI当前已绑定可用选择。未认定额度耗尽或登录失效，当前只证模型选择初始化失败。

## 后续最小推进与停止条件

最新接续：用户要求立即开始且首选GLM-5.3-Flash。已读本机发行bundle的独立账号构建逻辑ejn/njn：仅枚举access.mode=individual-coding-plan，并要求官方credential store中的account-provider identity及认证；桌面Start Plan不在该独立加载通道中。此为当前发行代码限制，不推断账号过期或额度耗尽。官方内置catalog的Start Plan当前支持Flash但不支持GLM5.3，已与之前模型选择错误分开记录。

已执行官方 `node zcode.cjs login bigmodel --no-browser`，终端session_id=60523，返回官方BigModel授权URL并等待用户完成。没有自动操作认证页面、读取/搬运凭据或购买。URL临时state不写入文档。后续只轮询该现有命令结果，不能启动第二登录/循环开URL。用户完成后官方CLI自己保存认证，再用本次personal-provider选择account:bigmodel-individual-coding-plan/GLM-5.3-Flash做无工具ready测试；账号无Coding Plan/模型不可用则如实报告，不买套餐或换账号。当前仍无Android设备动作。

恢复Codex heartbeat，优先ZCode GLM-5.3。先解决CLI可用模型选择，必须一条无工具输出实际成功且回执确认provider/model，才能派设备任务。下一不可重复同参数盲试或把help成功当模型调用成功；可核对官方版本对应的配置/选择入口，或需用户在ZCode中确认GLM-5.3已连接可选。不能通过GUI代发提示词、读私有DB、模拟内部API、自动化登录/验证码、升级重装或起持久服务。

125仅路由验证，不授权ADB/新设备批/源码修改。待模型调用成功，将尚未提交的124-r4改为当前唯一ZCode owner的明确新任务文档并重新核输入；r4原文不能直接冒充给旧B已派发。旧Claude工作已交r3且停止，不并发同设备。旧boot与10-25预算不可自动沿用，先fresh只读身份及资产门禁，未知保留。

## Android当前证据

r3 ready55792207a91638957e50f4675afcd23037cd4deeade25f27a10d0a440498ef6b，REPORT9589B/7ec008861c0a0b0374c8c94cd80450b52173dbcafa0a5720511342d1265ef647已Root核。7原件1136620B与12证据匹配，当前Room/WM/DE保全、一次受控停止、前后字节同；Room逻辑未变，WM计数差异未归因但旧新原字节齐备。原test/71私有820外部/Download179/settings/night/VM有原件支持，仅当时有效。123资产仍KEPT_BLOCKED，无卸载/新业务。r4文档4fef7011…未提交，用户停止UI后不得补投。
