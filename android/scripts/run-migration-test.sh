#!/usr/bin/env bash
# 在已启动的 Android 模拟器上运行 Room 迁移测试（6->7 / 1->7）。
# 用 step 内重试循环吸收模拟器偶发抖动（emulator-runner 不支持 job 级 retry）。
# 注意：reactivecircus/android-emulator-runner 的 `script:` 多行块标量会被
# `sh -c` 截断（见项目 .workbuddy/memory 的 CI 纪律），故抽到独立 .sh 文件执行。
set -u

TEST_CLASS="com.creationreadingassistant.data.local.AppDatabaseMigrationTest"

for i in 1 2 3; do
  if ./gradlew :app:connectedDebugAndroidTest \
       -Pandroid.testInstrumentationRunnerArguments.class="$TEST_CLASS"; then
    exit 0
  fi
  echo "::warning::迁移测试第 $i 次失败，15s 后重试（吸收模拟器偶发抖动）"
  sleep 15
done

exit 1
