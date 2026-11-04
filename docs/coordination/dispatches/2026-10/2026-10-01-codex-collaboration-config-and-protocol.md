# Codex协作配置与协议交付（2026-10-01）

## 任务编号/版本

MEM-COLLAB-v1。来源：用户“好，先按你的想法来，结果给我看看”。本轮仅配置/指令/工作流/只读检查脚本，未实施产品返修或外部派发。

## 输入指纹

恢复仓库HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空。精确修改清单和前后哈希见 [changes](<E:/codex-collaboration-20261001/changes.json>)、[修改前备份manifest](<E:/codex-collaboration-20261001/backup-manifest.json>)。输入为已整理AGENTS/交接与两仓库既有config/四角色，不采用共享脏树作为产品候选。

## 所有权与改动

- 用户级及两个项目默认子智能体：GPT-6 Luna/medium；侦察与验证角色Luna/medium，实现GPT-6.1 Sol/medium，关键审查GPT-6.1 Sol/high。模型允许按风险及实际可用性调整；存量对话角色可能仍为旧快照。
- 两仓库8角色设备规则引用活动current的当次决定，不固定手机/MuMu；精简重复读取，验证者允许只写指定报告，保护正式数据与他人修改。
- 用户AGENTS明确总指挥维护状态；现有 [协作工作流](<D:/Application/文档/Obsidian/Codex/agent/项目指挥-完成后进度汇报.md>) 包含单框任务合同、九字段报告、状态转换、通信前置与证据复用。
- current任务表采用任务编号/版本、所有者、依赖、状态；项目页/TODO同步J2已交付待核。仅登记失败与哈希不一致，不改外部报告、不关单。
- 新增 [只读协作检查](<D:/Application/文档/Obsidian/Codex/scripts/collaboration_check.py>)；只读显式活跃入口与相关角色，按需检查新报告，不遍历历史，不自动归档、改文件或启动自动任务。

## 环境

Windows，Python3.13标准库。配置语义参考 [官方子智能体文档](https://learn.chatgpt.com/docs/agent-configuration/subagents)，默认模型及角色设置分开；未安装工具、重启应用或操作MuMu。

## 执行与结果

- 11份TOML解析通过；18份备份SHA通过，用户config新增agents层可逆校验通过。
- 静态检查活跃入口20文件/111链接；包含本报告为21文件/117链接，均为0错误0提醒。8项针对性自检和2项负例集成检查通过（缺链接、坏TOML能报错）。
- 两仓库3377个非docs/.codex/AGENTS资产路径前后相同；HEAD/index未变；其他项目TODO区原文一致。
- J2新增报告已只读查到：原日志确有4 tests/3 failures，报告所列宿主/测试APK哈希均不匹配本轮实算freeze。验收保持待核/输入对账阻塞，下一产品任务核实际被验输入和恢复证据。

## 原始证据

[本轮核查JSON](<E:/codex-collaboration-20261001/verification.json>)；[配置增补回退记录](<E:/codex-collaboration-20261001/global-config-rollback.json>)。设备原证据沿用J2路径，本轮没有重新运行测试。

## 未覆盖项

本轮静态验证不证明新会话实际加载模型/角色。需要未来正常新对话确认其工具角色与模型列表；未强制中断现有应用/外部任务。外部通信能力、运行状态、额度未核，不能宣称已自动直连。J2完整验收尚未复核/关闭。

## 恢复与副作用

既有文件完整备份在E盘；用户级config只增补agents层，未复制provider或凭证。回退先核对应前后SHA：普通文档逐文件恢复；用户config仅移除回退记录中的新增后缀，保留其他内容。无设备数据/设置/产品源码变更，无Git提交或push。

## 交付状态与下一步

本轮配置/协议/脚本交付并完成静态验收；运行时加载验证暂未完成。以后修改相关指令时执行：

````powershell
& 'D:\develop\Python\Python313\python.exe' 'D:\Application\文档\Obsidian\Codex\scripts\collaboration_check.py' --repo 'D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919' --mirror 'D:\develop\Code\Codex\creation-reading-assistant' --self-test --json
````

新协议报告可额外传 `--report <报告绝对路径>`；其他项目可传 `--project <项目页名称>` 与其 `--repo`，无需复制脚本。旧报告不强制补格式。检查成功只证明静态合同，不能代替产品验收。
