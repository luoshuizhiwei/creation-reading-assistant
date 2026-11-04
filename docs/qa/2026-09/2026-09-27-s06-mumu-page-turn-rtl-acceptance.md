# S06 MuMu 设备验收：快翻 8 项 + 滑杆 4 项 + 整页 RTL 视觉

- **日期**：2026-09-27 深夜（执行窗口 23:41–00:00）
- **执行角色**：S06 设备验收执行棒（本轮唯一 MuMu 所有者；完成后交还操作所有权，等待总指挥复核）
- **工作区**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（未使用原目录；产品源码零改动）
- **验收依据**：`docs/coordination/reviews/2026-09/2026-09-27-s06-page-turn-current-review.md`（快翻 8 项 = 5 队列 + 3 像素）、`docs/coordination/reviews/2026-09/2026-09-27-s06-rtl-slider-current-review.md`（§4 设备取证方案）
- **设备**：**MuMu 模拟器**（x86_64、Android 12 镜像、自报 SM-S9280 属性伪装）。本报告全部设备结论仅表述为「模拟器」，**不构成实体手机结论**。

---

## 1. 序列号变更与身份确认（复用授权内的同一实例）

任务下发时预设 serial `127.0.0.1:16416`，执行时 `adb devices` 显示 **`emulator-5556`**（MuMu 重启后枚举名变更，16416 已不存在）。按任务书「确认**当前**序列号」条款，核实 `emulator-5556` 与此前 16416 为**同一 MuMu 实例**（`ro.product.model=SM-S9280`、manufacturer=Samsung、**abi=x86_64**、Android 12——与 S14 轮 16416 端点的属性逐项一致，见 `01b-device-identity.txt`），且两包安装状态延续（versionCode=3 / 0.5.1）。本轮全部命令显式 `adb -s emulator-5556`。

## 2. APK 复用核实（不重建、不重装）

| 项 | 结果 |
|---|---|
| 宿主 SHA-256 | `a4669f8532055656be6341444f29eaf3a972678533e5ceb351ff34e40021a638`（=`E:\creation-reading-assistant\codex-s04-gate-20260927\app-qahost-20260927-gate.apk`，与预期一致） |
| 测试包 SHA-256 | `6d19c24449d75f893c20fef947f165fd005f08733187ad1f3e5c5662c1456ec7`（同目录 `app-qahost-androidTest-20260927-gate.apk`，与预期一致） |
| 设备安装 | `com.creationreadingassistant.qahost` + `com.creationreadingassistant.qahost.test` 在装；正式包 `com.creationreadingassistant` **不存在**，全程零触碰 |
| 测试包含两目标类 | 解包 dex 二进制检索：`feature/reader/pager/PageTurnerHighSpeedTest` 命中 12、`ui/components/CraSliderRtlRenderTest` 命中 8（均为字符串级确认） |
| 源码漂移 | `android/app/src` 无文件晚于测试 APK 构建（2026-09-27 15:47） |

→ **匹配，直接运行**（`00-apk-hashes.txt`）。

## 3. 快翻 8 项 + 滑杆 4 项（一次定向运行）

命令（完整原始输出 `04-instrument-12.log`，退出码 `04-instrument-exit.txt`）：

```bash
adb -s emulator-5556 shell am instrument -w -r -e class \
"com.creationreadingassistant.feature.reader.pager.PageTurnerHighSpeedTest,com.creationreadingassistant.ui.components.CraSliderRtlRenderTest" \
com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
```

- **ADB 退出码：0**；`numtests=12`；`Time: 7.317`；**`OK (12 tests)`**；失败栈：无。
- 逐项结果（`05-per-test-results.txt`，12/12 PASS）：
  - 快翻 8：`slideHighSpeedTurnNeverShowsABlankFrame`、`coverHighSpeedTurnNeverShowsABlankFrame`、`advancingTurnNeverShowsABlankFrame`（高速空白帧×3）、`singleTapAdvancesExactlyOnePage`、`threeRapidTapsDuringAnimationAdvanceThreePages`（连续点按×2）、`oppositeTapReplacesUnstartedBacklog`（反向）、`burstBeyondCapacityIsBounded`（容量）、`tapsAtLastPageStillConsumeRequestsWithoutStalling`（末页+渲染身份）；
  - 滑杆 4：`tapNearLeftEdgeYieldsLowValueInLtr`、`tapNearLeftEdgeYieldsHighValueInRtl`（数值回调×2）、`activeTrackIsPaintedOnLeftInLtr`、`activeTrackIsPaintedOnRightInRtl`（轨道像素×2）。

| 分组 | PASS | FAIL | BLOCKED | UNPROVEN |
|---|---|---|---|---|
| 快翻 8 项 | **8** | 0 | 0 | 0 |
| 滑杆 4 项 | **4** | 0 | 0 | 0 |

此前「5 项 instrumentation 从未运行、0 项结果」的缺口（page-turn 审查 §2/§4）就此补上；「页号正确但显示旧帧」「末页卡队列」两条风险对应的断言面首次在设备上通过。

## 4. 整页 RTL 视觉（应用页面 LTR/RTL 对照）

对象：qahost「我的 → 阅读目标」页的 `CraSlider`（精细调节，0..180 分钟）。**全程未打开任何既有书**。

### 4.1 设置基线（恢复锚，`03-settings-baseline.txt`）

`persist.sys.locale=""`、`ro.product.locale=zh-CN`、`settings global debug.force_rtl=null`、sysprop `debug.force_rtl=false`、`stay_on_while_plugged_in=null`、`user_rotation=0`、density 280、1080x1920；应用侧目标值=**未设定（--）**。

### 4.2 LTR 基线（同轮新拍）

- `10-ltr-goal-unset.png`（未设定，拇指左端、停点右端）；
- `12-ltr-goal-low.png`：目标 **35 分钟**（≈20%），拇指 x≈250、激活段自**左**生长、停点右端；
- `14-ltr-goal-high.png`：目标 **145 分钟**（≈80%），拇指 x≈828；
- 配套 dump：`11/13-ltr-*-dump.xml`。

### 4.3 RTL 注入：两条失败通路（H6 根因实证）+ 一条成功通路

| 通路 | 操作 | 应用进程配置 witness（`dumpsys activity top` mCurrentConfig） | 判定 |
|---|---|---|---|
| 辅通路① | `settings put global debug.force_rtl 1` + 冷启动 | `[zh_CN] ldltr`（`16-rtl-witness.txt`） | **无效**——settings 键不抵达框架配置层 |
| 辅通路② | `adb root` + `setprop debug.force_rtl true` + 冷启动 | `[zh_CN] ldltr`（`16b-rtl-witness-sysprop.txt`） | **无效**——sysprop 载体同样不被读取 |
| **主通路** | root 下 `setprop persist.sys.locale ar` + 框架重启（stop/start，等效系统设置改语言的底层动作；`cmd locale` 在该镜像不存在） | **`[ar] ldrtl`**（`17-rtl-locale-witness.txt`） | **有效** |

→ 09-23 遗留之谜（「force_rtl 回读 1 但整页未镜像」）就此取得**历史缺失的第三 witness**并定因：该 Android 12 镜像的框架不读取 `debug.force_rtl` 的 settings/sysprop 两载体，唯 locale 派生方向有效。

### 4.4 三层证据链（全部达成）

1. **进程级**：`mCurrentConfig={… [ar] ldrtl …}`（`17-rtl-locale-witness.txt`）；
2. **Row 级**：导航栏 x `[940,1080]`（LTR 为 `[0,140]`，精确互补，`19-ui-rtl-home.xml`）；关闭档位 chip 镜像至右缘 `[877,656][1031,740]`（`21-ui-rtl-goal.xml`）；首页/Profile 整页目视镜像（`18-rtl-home.png`）；
3. **滑杆级**：同值对照——`22-rtl-goal-high.png`（**145 分钟**：拇指 x≈250 = LTR x≈828 的精确镜像，激活段自**右**向左生长，停点左端）与 `24-rtl-goal-low.png`（**35 分钟**：拇指 x≈828 = LTR x≈250 的镜像）；同值 LTR/RTL 截图明显非同一画面，方向符合预期。

| 整页 RTL 视觉 | PASS | FAIL | BLOCKED | UNPROVEN |
|---|---|---|---|---|
| 主项（MuMu 范围：进程 ldrtl + Row 镜像 + 滑杆同值镜像） | **1** | 0 | 0 | 0 |
| 子项（未覆盖，见 §6） | — | — | — | **3** |

## 5. 设置恢复（逐项回读，`25/26/27/28` 号工件）

| 项 | 原值 | 恢复动作 | 回读 |
|---|---|---|---|
| 应用目标值 | 未设定（--） | 点「关闭」档位 | `未设定目标 · 点击下方档位开启` + `--`（`25-restore-goal-dump.xml`） |
| `persist.sys.locale` | 空 | `setprop ''` + 二次框架重启（首次因空值被拒、system_server 带旧值重启过一次，二次重启后生效） | 空（回退 zh-CN） |
| `settings global debug.force_rtl` | null | `settings delete`（Deleted 1 rows） | `null` |
| sysprop `debug.force_rtl` | false | `setprop … false` | `false` |
| `stay_on_while_plugged_in` / `user_rotation` / density / size | null / 0 / 280 / 1080x1920 | 未触碰 | 原值（`27-restore-final-verification.txt`） |
| adbd | 非 root | `adb unroot` | `uid=2000(shell)` |
| 应用进程配置 | `[zh_CN] ldltr` | 冷启动 | `[zh_CN] ldltr`（`28-restore-second-restart.txt`）+ 恢复后 LTR 首页截图 `29-restored-ltr-home.png`（导航栏回到左侧） |

qahost 数据未清除、未卸载；未执行 pm clear/commit/push/reset/checkout；未触碰正式包与实体手机。

## 6. MuMu 限定边界与未覆盖项

1. 本轮全部为**模拟器**证据；实体手机 ROM（含 MIUI/HyperOS 的 force_rtl 行为差异、SR-ENV-1 通道问题）不在此列；
2. 整页 RTL 的三个子项 **UNPROVEN**：①滑杆**拖动**语义（本轮仅 tap，未做 drag 方向实测）；②拇指圆心与激活段边缘的**像素级贴合**（SR-2，需断言化或更高倍目视）；③**书内 ReaderChrome 底栏进度条**的 RTL 镜像——受「不打开既有书」边界限制未执行；
3. 「同帧单槽合并」「切书不清队列」两已知缺口未变（page-turn 审查 §3.3/§3.4，属产品语义待决，不在验收范围）；
4. 快翻/滑杆 12 项均为 instrumentation 断言（Compose 测试环境），不等价于人工目视的整页视觉验收——后者已由 §4 独立补足；
5. 截图说明仅含中性名称（测试 EPUB S04、测试样本F·竖屏、sample-019、S04 Verify B、zz-s05-import-A 为 qahost 沙箱既有测试书，本轮未打开、未修改，仅在截图中入镜）。

## 7. 工件索引（`E:\creation-reading-assistant\codex-s06-mumu-20260927\`）

`00-apk-hashes.txt`、`01/01b` 设备与身份、`02` 包清单、`03` 设置基线、`04-instrument-12.log` + `04-instrument-exit.txt`（原始输出/退出码）、`05-per-test-results.txt`（12 项逐项）、`07–09/19–21/23/25` UI dumps、`10/12/14/18/22/24/29` 截图、`16/16b/17` RTL witness、`25–28` 恢复回读。APK 哈希：§2。

## 8. 结论

快翻 8 项 **PASS**、滑杆 4 项 **PASS**、整页 RTL 视觉主项 **PASS（MuMu 范围）** + 3 子项 UNPROVEN；无 FAIL/BLOCKED；设置恢复逐项回读通过。报告路径交总指挥复核，本棒停止，不自行领取下一任务。

## 9. Codex 总指挥复核（2026-09-28）

- 独立回读 `04-instrument-12.log` 与退出码：原始输出为 `OK (12 tests)`、`INSTRUMENTATION_CODE: -1`、ADB exit 0；逐项为快翻 8/8、滑杆 4/4。两包 E 盘 SHA-256 与报告一致。
- 独立目视低/高值 LTR、RTL 同轮截图：轨道与拇指确实反向，进程配置工件记录 `[ar] ldrtl`；恢复工件记录 `[zh_CN] ldltr`。当前 MuMu 回读 280 dpi、`user_rotation=0`、`accelerometer_rotation=1`、`debug.force_rtl=null`。接受 **MuMu 上组件测试与该页 RTL 方向** 的限定 PASS。
- RTL 页面截图采于 **280 dpi、`sw617dp` 的平板布局**；本轮未满足任务书的“手机布局并确认底部导航”，不能把整页视觉项扩张为手机布局 PASS。已通过的 12 项自动化无需因此重跑；后续同类手机布局回归可复用这些测试结果，只补布局相关视觉证据。
- 同帧单槽合并、切书清队、RTL 拖动/像素级贴合及书内进度条仍按 §6 未覆盖。`debug.force_rtl` 两载体无效的结论限于本次 MuMu 镜像，不能推断其他 ROM。
