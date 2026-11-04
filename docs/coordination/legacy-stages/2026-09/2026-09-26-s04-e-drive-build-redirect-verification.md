# S04 构建产物 E 盘重定向：配置与验证

- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **日期**：2026-09-26
- **约束**：用户磁盘要求——构建产物尽量不写 C 盘/D 盘；S04 的 APK、测试结果、编译中间目录与大体积临时文件优先放 `E:\creation-reading-assistant\codex-s04-build-20260925`。
- **性质**：只做重定向配置核验与定向 JVM 测试（`--offline`）。未运行任何未重定向的构建，**未构建 APK**（APK 由 Codex 集成者产出）；未触碰真实书库、E 盘备份（`E:\creation-reading-assistant-backups\` 与本目录无关）、原始仓库；无 git 操作。

## 1. 现场配置（集成者已就位，本窗口未改动）

`redirect-build.gradle`（308 字节，2026-09-25 23:47，**非本窗口创建，原样使用**）：

```groovy
def outputRoot = new File('E:/creation-reading-assistant/codex-s04-build-20260925/project-builds')
gradle.beforeProject { project ->
    def relativeName = project.path == ':' ? 'root' : project.path.substring(1).replace(':', '/')
    project.layout.buildDirectory.set(new File(outputRoot, relativeName))
}
```

目录现状：`project-builds/`（实际产物根）、`project-cache/`（`--project-cache-dir` 目标）、`temp/`（TEMP/TMP 目标）、`gradle-home/`（集成者所建，本窗口未使用未改动）、`redirect-build.gradle`。本窗口核对后删除了自己误建的空 `build/` 目录（与 `project-builds/` 布局重复，避免歧义；删除前确认为空）。

## 2. 开跑前核验：buildDir 指向 E 盘（`:app:properties`）

命令（`android/` 下；先 `./gradlew --stop` 重启守护进程，使新守护进程继承重定向后的 TEMP/TMP）：

```
export TEMP='E:\creation-reading-assistant\codex-s04-build-20260925\temp'
export TMP='E:\creation-reading-assistant\codex-s04-build-20260925\temp'
./gradlew :app:properties -I E:\creation-reading-assistant\codex-s04-build-20260925\redirect-build.gradle \
  --project-cache-dir E:\creation-reading-assistant\codex-s04-build-20260925\project-cache --offline
```

实测输出（**EXIT 0**）：

- `:app` → `buildDir: E:\creation-reading-assistant\codex-s04-build-20260925\project-builds\app`
- 根项目 → `buildDir: E:\creation-reading-assistant\codex-s04-build-20260925\project-builds\root`

`buildFile` 仍指向 D 盘仓库源码（`android\app\build.gradle.kts`）——符合预期（源码不搬，只搬产物）。

## 3. 重定向定向测试与结果位置核验

命令：同上环境变量与两个重定向参数，跑 `:app:testDebugUnitTest`（五类过滤：BookResidueSafeCleanupTest、BookRepositoryTest、ShelfBookActionsTest、ShelfViewModelTest、ShelfSourceRefreshFeedbackTest，`--offline`）。

- **实际退出码：0，BUILD SUCCESSFUL**。
- **测试结果实际位置（已逐一核对）**：`E:\creation-reading-assistant\codex-s04-build-20260925\project-builds\app\test-results\testDebugUnitTest\TEST-*.xml`（5 个 XML）——101 例全部通过（23+25+18+14+21，0 失败 0 错误）。`project-builds\app\` 下 `generated/intermediates/kotlin/outputs/reports/tmp` 全套产物均在 E 盘。
- **TEMP 重定向旁证**：`temp\` 内出现 JVM `hsperfdata_23254`——守护进程 JVM 实际使用了重定向后的 TEMP。
- **D 盘零新写入（证据）**：跑前在 `temp\` 放置时间标记 `d-write-marker-20260926`，跑后 `find android/app/build android/build android/.gradle -type f -newer <标记>` 结果为空——D 盘仓库的 build/ 与 .gradle 零新写入。

## 4. 后续统一命令模板（任何 S04 Gradle 调用）

```bash
cd <仓库>\android
export TEMP='E:\creation-reading-assistant\codex-s04-build-20260925\temp'
export TMP='E:\creation-reading-assistant\codex-s04-build-20260925\temp'
./gradlew <任务> \
  -I E:\creation-reading-assistant\codex-s04-build-20260925\redirect-build.gradle \
  --project-cache-dir E:\creation-reading-assistant\codex-s04-build-20260925\project-cache \
  --offline
```

- 每次构建/测试前先用 `:app:properties` 复核 `buildDir` 指向 E 盘；跑后核对结果实际位置。输出路径无法确认时暂停该次构建并上报，**不默认写回 D 盘仓库 build/**。
- 更换 Gradle 版本或重启机器后需先 `./gradlew --stop`（守护进程须继承重定向后的 TEMP/TMP）。

## 5. 与集成者 / B 窗口的接口

- **B 窗口不负责构建**：必须等 Codex 集成者给出 E 盘新 APK 的路径与 SHA-256 才能安装验收。集成者用本模板构建后，APK 将位于 `E:\creation-reading-assistant\codex-s04-build-20260925\project-builds\app\outputs\apk\<variant>\`。
- 本窗口本轮**未构建 APK**、未运行未重定向的全量构建；仅定向测试。

## 6. 边界与说明

1. `~/.gradle`（C 盘用户级 Gradle 缓存/发行版）不在本轮指令范围，未改动；`gradle-home/`（E 盘）为集成者所建，本窗口未启用也未改动——是否切换 `GRADLE_USER_HOME` 由集成者决定（切换会影响发行版缓存位置，需评估离线可用性）。
2. `gradle-home/wrapper` 已存在（集成者预置）；本窗口所有命令走既有 C 盘发行版缓存，无网络下载。
3. D 盘 `android/app/build` 中留有约束生效前（2026-09-25）的旧产物——属既有工作区资产，本窗口未清理（清理需用户指示）。
4. 本报告全部为构建位置核验与 JVM 测试结果，不构成真机/设备验收结论。
