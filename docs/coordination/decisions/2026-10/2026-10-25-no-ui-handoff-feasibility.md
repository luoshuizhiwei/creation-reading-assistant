# 免UI交接入口核查（只读，未实现派发）

2026-10-25 香港16:03心跳后。未使用电脑控制，未调用模型，未读取Claude私有配置、凭据、会话库或自动记忆。

## 已核事实

- PATH存在C:/Users/23254/.local/bin/claude.exe，本地--version为2.1.296 (Claude Code)。
- --help明确支持-p/--print、--resume、--model、--bare、--no-session-persistence。
- 本地帮助和官方文档均表明：print是非交互入口；bare跳过hooks/自动记忆/CLAUDE.md发现，但不沿用订阅OAuth/keychain登录，需要明确API或provider认证。
- 官方来源：https://code.claude.com/docs/en/cli-reference 与 https://code.claude.com/docs/en/headless （本轮已打开核对）。

## 未证明事项 / 当前停止点

- 本地CLI能否访问用户指定WorkBuddy global/cn两种DeepSeek路由，及是否能续接桌面两个指定会话，尚无证据。
- 不把安装了CLI等同于路由/认证/指定会话可用。不能私读凭据/内部会话库来补齐。
- 未运行-p/--resume/--continue、auth status、agents或任何模型调用；这些可能读取私人状态，不能作为单纯help检查继续扩展。
- 不默认换成Anthropic模型/API计费，不购买，不启动后台服务，不擅换A/B或新开会话。
- r4完整任务文件已准备但未成功提交；不得通过UI补投。产品停止/资产保全状态不因本核查改变，仍未卸载或启动新业务。

## 结论

存在官方非交互CLI候选入口；尚未建立满足指定WorkBuddy路由、既有会话和私库边界的实际调用闭环。免UI派发仍未实现。下一需要公开支持的WorkBuddy接入/会话映射信息或用户明确调整“必须既有会话”的约束；不能绕私库限制实现。

## 香港16:08公开资料补查

- 已打开腾讯官方 https://cloud.tencent.com/document/product/1831/137014 ：CodeBuddy非交互调用有CODEBUDDY_API_KEY/BASE_URL/MODEL配置。这是CodeBuddy入口说明，不证明Claude桌面既有A/B会话或当前WorkBuddy global/cn路由可直接续接。
- 已打开DeepSeek官方 https://api-docs.deepseek.com/quick_start/agent_integrations/workbuddy/ ：方向是把DeepSeek API key接入WorkBuddy/CodeBuddy，不是把WorkBuddy当前登录额度导出给Claude CLI。
- 未找到证明当前两条指定路由及既有桌面会话可合规免UI接续的公开说明。此为本次检索结果，不等同于证明该功能不存在。
- 不采用要求模拟私有接口或读取桌面凭据的第三方桥，不下载/安装/调用它们。
- 当前需要用户提供所用接入方案的非秘密说明/官方调用入口（不需要粘贴密钥），或明确允许调整会话约束后再验证独立CLI方案。缺这些信息不继续重复搜同文档，不盲目启动模型调用。
