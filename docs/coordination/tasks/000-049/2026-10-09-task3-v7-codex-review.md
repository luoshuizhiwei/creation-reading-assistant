# 任务3-v7核查

核查时间：2026-10-09香港时间。报告1/1；J1整项通过0/1。全产品分母未核。

Root实算996源码SHA一致；相对8仅J1两测试与SearchScreen变化，993条保持已验合流树字节。双APK SHA一致；构建原日志exit0。26证据SHA无缺失/漂移；补取证DB/WAL/SHM本地实算与远端记录3/3一致。

主线程边界修正限定采纳：搜索点击后B marker与返回按钮可见、A完整12列冻结、B普通进度无行、Activity关闭无异常成立。首异常推进为返回按钮后等待A1超时。截图显示B且临时按钮消失，J1整项仍FAIL，页区间/返回后真实位移未执行。该轮查询索引实际受合流生产worker影响，合法坐标须以该轮推导/诊断为准，不硬沿用v6偏移8。

独立只读检查确认A退出页面后根级协调器仍应保留normalReading；返回A合同不是要求NavController仍有A页面。当前缺协调器active/normal/stack、返回route、真实route参数及OpenBook目标观测，尚不能锁定返回根因，不猜修。

设备恢复：驱动原restore拒绝仍保留，不改成自动恢复通过。shell无法直pull私有DB，owner经su专属暂存、远端/本地SHA核同后才卸载新包；保护71文件/nsmtime/SHA、原APK对/8设置恢复原证据成立。Root当前manager实读process/android均false。未再次启动模拟器或声称新的Root全71实测。

报告§十“未合入任务4/5增量”与实际源码矛盾；实算确认合流A增量保留，应理解为未用B旧树覆盖A，不接受无增量声明。device-restore-verify中的power为中间状态，最终关闭以Root当前info为准。

下一3-v8独立E返回链诊断/必要最小修复，唯一设备；9-v1独立E驱动权限安全取证返修，离线无设备/Gradle。9不得削弱pre-uninstall失败拒绝，3不得吸收未核9。最终31继续等待Root核查。8的未变64JVM复用，不重复全量。

Root证据：E:/creation-reading-assistant/task3-v7-codex-review-20261009/verification.json、evidence-verification.json（备份文件加包前缀，3份另行实算均核同）。D产品/Git/发布无授权。X整机中断、Back完成归因/pending排空/初始新save等未覆盖保留。
