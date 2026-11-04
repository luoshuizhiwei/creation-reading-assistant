````text
任务147-r3｜Antigravity同owner提交前自查闭环｜Gemini 3.8 Flash High｜12分钟｜无自动化
用户新要求：提交前你自己逐条检查是否符合任务要求，发现具体问题由同owner修复、验证后再交，不把基础自查留给Root反复拆批；只检查实际差量，不泛审、不重跑146充分suite。
基线E:/creation-reading-assistant/task147-single-mumu-batch-prep-v1/recheck-r2/，Root完整读实际模板7122B d193bf60db8cac40083ef9e968ee9a4a296164ab24dc866aea256a80a3105f9f、test9299B22e4db8817a34b34e36700a15288bf8f2b18637f61dab572795d7dc68bc377cd、原log1454Bef9a548d9a72e704236aedaf40752a8ae9d4cb82ec38501b1ecddea95163b228及REPORT，四件SHA核同。
精确device匹配、无BOM预算和CreateNew修改事实接受。但测试另写Test-ParseDevices/Mock-SaveReceipt，未实际调用交付模板函数；不能这些绿代实际函数行为。模板未设置终止错误策略，写预算失败后的行为和真实host非零退出码传递未证明；请具体核实，若成立最小修正，不照单假设。
唯一新目录E:/creation-reading-assistant/task147-single-mumu-batch-prep-v1/recheck-r3/，旧所有原件冻结。source/doc总32KiB，raw/mock总16KiB。只交最小patch/修正模板/定向原log/REPORT/ready，不复制完整旧手册或工程，不padding凑字节。
必须先定义自身验收表，检查实际交付模板：身份锁定、精确device状态、全原rc/stdout/stderr持久化、输出同scope、预算UTF8无BOM与独占、失败前不host、host退出码不被掩盖、旧资产不写删。测试直接从实际模板加载/提取其函数或真实模板体，仅外部进程端点mock；不得复制业务实现自证。用明确的假Manager/ADB/host端点闭环（未匹配抛错），保证任何真实端点无法被调用。不真实生成T141BV1 runid或真实任务scope/budget/attempt/summary，fake输出只SYNTHETIC_FAKE。
必要定向：正常流程host恰调用1；身份rc错或stderr异常/写回执或budget失败时host0；重复预算不覆盖；host退出非0必须保留并传递。仅新增风险才扩大检查。进程stdout/stderr同时重定向时检查是否可能互相等待，按本批实际需求合理处理，避免不必要框架。
如检查出其他直接影响上述模板的具体缺陷，同owner最小修正后只补相关验证再提交，不再停在“建议Root下一轮修”。自查报告必须说明实际调用对象、外部mock边界、原红绿和仍未验证项，不能仅列全绿。报告统计以最终实际文件为准，可排除ready自身并说明口径；上轮REPORT6794/实际6835差异如实纠正不回改旧件。
不改冻结146host/父/adapter/inputs、Android源码/APK/Gradle；不build。无任何真实设备/ADB/VM探测启停/安装卸载force-stop/settings/真实main或runid预算attemptsummary/instrument/F1权限；不Git云私库自动记忆、不删除移动旧资产、不创建自动化。Root只核必要实际delta/原log，准备通过不等设备授权。
最终单独末行：这是任务147-r3。
````