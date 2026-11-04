# 任务145-r5：真实三根/身份的拒错边界及真实父run_attempt离线验收

2026-10-26香港15:48，同owner同Claude global Flash，20分钟/差量512KiB raw512KiB。设备仍禁；原r4原件冻结，已充分API与模板证据复用。

````text
Root完整读r4host18615B614d6cb7f4fe07fd9a70c31abb8ed995e24a2d8c35cd9c29eb1e0f9c0542bf94与验证脚本关键，实际13绿还没覆盖真实三根/主入口，以下均实际代码所指，不泛审：

A. r4 FakeDev.find忽略传入root，对CE/DE/external都返回同一CE FILES；每次tar同一个CE归档，测试仍称三根positive。真实保全函数未要求源/path/member属于本根，三根可能错归档假绿。每根必须独立source manifest和对应tar，条目归一化严格在对应根之内；root目录本身和所有子目录也核集合/类型（目录不比磁盘size），文件核sizeSHA；拒不同根/越界/重复member/软硬链接。当前只比files不比目录且src dir size保留raw，按实际tar语义处理，不拿文件size替完整目录清单。

B. source_manifest静默跳过格式坏行/intsize非法/sha缺行，缺失/坏源不能变空清单。find rc0必须包含本root真实目录条目，零文件合法必须本root目录存在+父正对照，不能只有父存在。每个file必须恰一合法64hex SHA，不缺/重/乱序误匹配；保存完整listing与sha stdout/stderr/rc，不只解析entries。空tar(members空)现在不进if members导致跳过比较可绿，必须拒；合法空DE应有根目录tar member。严格逐根源前后清单与writer前后同身份窗口。

C. writer_identity只对部分rc核验；get-state/boot/user/serial rc非0但stdout看似正常也能通过，未核boot_id与本批fresh，expected_serial传dev.serial为自证。保全必须由父本批fresh/预检真实boot_id、serial、user注入（不可由当前值作为expected），所有命令rc/stderr均有规范，pidof只有rc0且PID或rc1空与ps能解释；ps非空有header/有效输出且无精确pkg或pkg:前缀，空/无效不是绿；before/after身份均同本批fresh。

D. 真实shell命令必须沿已证设备语法。r4 find -printf Android支持未证，不在本批设备探测；复用138实际已跑完整清单脚本的stat/find/sha口径只读源，不猜新toybox扩展。父路径有中文空格，dev.sh经adb remote shell传参不能以Pythonlist就是shellquote安全；复用父shell(dev, script)的正确转义/实际已证inventory脚本，单一命令文件内容或安全逐项quote。归档tar全root原字节，不导出其他包。

E. 主入口仍未真正验收：r4脚本尾无if __name__=="__main__": main()，报告CLI `python host145_r4.py --...`只导入并exit，不执行main。补真实CLI入口。r4 offline StubParent.run_attempt仅计数return0，没调用真实父run_attempt，不能证明其预算/attempt/fixture/collect/finally。要求最终只一项真parent.run_attempt全链离线fake：真实父模块与实际函数体，patch仅外部b.run/Dev.sh(严格真签名)/独立系统端点，不替换run_attempt/collect/recover本体，不用StubParent代替；全部路径SYNTHETIC_FAKE隔离fake budget/attempt/raw，绝无真实evidence或ADB。包含新class+runner/-eclass、noarginputs/同scope、实际collector新tuple、finally实际保全门以及原test/settings独立恢复调用记录；三根失败产品uninstall0而其他恢复按父安全门发生，成功才正常精确uninstall，源码不为测试放宽。未approve在任何文件attempt/外命令前拒绝，python脚本CLI确到main可独立用无approve负例证明。

只新增这些真实失败边界必要用例，严禁继续用被测源生成expected或把root忽略的fake当positive。最少positive三根各有不同文件/目录(含CE db/wal/shm)+空DE、wrong-root、空tar、SHA缺失、坏manifest、身份rc/boot漂移、真parent链。不刷旧13绿。独立恢复proof实现复用r4，但如果preserve抛异常，finally仍须产品保留+原test/settings安全恢复，不能异常跳过恢复。原始cause与恢复cause分列，不吞错。未完成明确BLOCKED，不假绿。

只新E:/creation-reading-assistant/task145-scroll-host-binding-v1/recheck-r5/host/inputs/最小相对r4patch/REPORT/原离线raw/ready.json。旧v1/r1/r2/r3/r4源码raw/SYNTHETIC不改不rmtree。仅离线，任何ADB/VM探测启停/安装卸载force-stop设置/hostmain真执行/真实runid预算attemptsummary/F1/设备禁止，Android源码/APK/Gradle冻结。真parent链仅所有外部命令mock且假输出scope明确隔离，不算设备执行授权。Root不旁路ADB/build，不Git云正式库私库自动记忆/删移旧资产/kill共享进程。新144APK539f44c5/f2d2982b与15rolepins复用。完成Root读实际差量和真实parent离线原log后另裁设备，最终总结末行：这是任务145-r5。
````