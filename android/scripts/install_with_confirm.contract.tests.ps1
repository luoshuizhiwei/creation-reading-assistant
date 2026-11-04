#Requires -Version 5.1
<#
install_with_confirm.ps1 的无设备契约回归（device-free contract regression）。

覆盖范围
  A. 静态契约：禁用写法（2>$null / Read-Host / pause / settings put / pm grant / su）、
     成功与回退的 RESULT 文案与退出码、adb 必须显式 -s、UI dump 必须按 UTF-8 解码、
     临时文件必须是 GUID 精确目标、-TestApk 缺 -PackageName 或包名为空白时的快速失败
     必须先于任何 adb 调用（S19-F1 / S19-F2）。
  B. 行为契约：在 %TEMP% 里放一个假 adb（把成功摘要写到 STDERR，真实复现 adb 的行为），
     用真实脚本跑十种情形 —— 确认循环成功 / 超时且禁用回退 / direct install 回退 /
     UI XML 解析失败可见 / -TestApk 缺 -PackageName 快速失败（零 adb 调用）/
     -TestApk 带显式测试包名走确认循环 / 普通主 APK 显式主包名 / -TestApk 回退仍带 -t /
     -TestApk 显式纯空白包名快速失败 / -TestApk 显式空字符串包名快速失败，
     逐一断言退出码与 RESULT 文案。

用法
  powershell -NoProfile -ExecutionPolicy Bypass -File .\install_with_confirm.contract.tests.ps1
退出码：0 = 全部断言通过；1 = 存在失败断言。
#>
param(
    [string]$ScriptPath = (Join-Path $PSScriptRoot 'install_with_confirm.ps1')
)

# 宿主环境修正：本机 Agent 会话默认 PATHEXT=.CPL，会让 .exe/.cmd 都无法按名字解析。
# 这是宿主特征，不是被测脚本的缺陷，因此在测试宿主里显式恢复 Windows 默认值。
$env:PATHEXT = '.COM;.EXE;.BAT;.CMD;.VBS;.VBE;.JS;.JSE;.WSF;.WSH;.MSC'
$ErrorActionPreference = 'Stop'

$script:checks = 0
$failures = New-Object System.Collections.Generic.List[string]
$dumps = New-Object System.Collections.Generic.List[string]

function Assert {
    param([bool]$Condition, [string]$Name, [string]$Detail = '')
    $script:checks++
    if ($Condition) {
        Write-Host "  PASS  $Name"
    } else {
        $line = if ($Detail) { "$Name -- $Detail" } else { $Name }
        Write-Host "  FAIL  $line"
        [void]$failures.Add($line)
    }
}

# ---------------------------------------------------------------- 静态契约
Write-Host "== A. 静态契约 =="
$scriptText = Get-Content -LiteralPath $ScriptPath -Raw
$codeLines = @($scriptText -split "`r?`n" | Where-Object { $_ -notmatch '^\s*#' })
$code = $codeLines -join "`n"
$codeLineCount = $codeLines.Count

Assert ($codeLineCount -gt 0) 'A0 脚本可读取且存在可执行行' "codeLines=$codeLineCount"
Assert ($code -notmatch '2>\$null') 'A1 可执行代码中无 2>$null（不会把 adb STDERR 提升为终止错误）'
Assert ($code -notmatch 'Read-Host') 'A2 无 Read-Host（不要求人工输入）'
Assert ($code -notmatch '\bpause\b') 'A3 无 pause'
Assert ($code -notmatch 'settings\s+put') 'A4 无 settings put（不改设备安全设置）'
Assert ($code -notmatch 'pm\s+grant') 'A5 无 pm grant'
Assert ($code -notmatch '(^|[^\w-])su\s') 'A6 无 su 提权'
Assert ($code -notmatch 'exec-out') 'A7 已弃用 exec-out cat 的 CP936 解码路径'
Assert ($code -match 'Get-Content\s+-LiteralPath\s+\$localUi\s+-Raw\s+-Encoding\s+UTF8') 'A8 UI dump 显式按 UTF-8 解码'
Assert (($code -match '\$ErrorActionPreference\s*=\s*''Continue''') -and ($code -match '\$LASTEXITCODE')) 'A9 原生 adb 调用降到 Continue 且只按 $LASTEXITCODE 判定'
$rawAdbCalls = @($codeLines | Where-Object { $_ -match '& adb' })
Assert ($rawAdbCalls.Count -eq 1) 'A10 只有一个裸 adb 调用点（统一走 Invoke-AdbNative）' "count=$($rawAdbCalls.Count)"
Assert ($rawAdbCalls[0] -match '& adb -s \$Serial') 'A11 该调用点显式绑定 -s $Serial'
Assert ($code -match 'RESULT: CONFIRM_LOOP') 'A12 成功路径有 RESULT: CONFIRM_LOOP 标记'
Assert ($code -match 'RESULT: FALLBACK_DIRECT_INSTALL') 'A13 回退路径有 RESULT: FALLBACK_DIRECT_INSTALL 标记'
Assert ($code -match 'NOT a confirm-loop PASS') 'A14 回退文案显式声明不是确认循环 PASS'
Assert ($code -match 'exit 2') 'A15 回退用 exit 2 与成功/失败区分'
Assert (($code -split "`n" | Where-Object { $_ -match '\[guid\]::NewGuid\(\)' }).Count -ge 3) 'A16 远端 APK / 远端 XML / 本地临时文件均为 GUID 命名'
Assert (($code -split "`n" | Where-Object { $_ -match 'Remove-Item' -and $_ -notmatch '\$localUi' }).Count -eq 0) 'A17 Remove-Item 只针对本次脚本创建的 $localUi'
Assert ($code -notmatch 'Remove-Item[^\r\n]*\*') 'A18 删除操作不使用通配符'
# 回归护栏：无 BOM 的 UTF-8 脚本在 Windows PowerShell 5.1 下按 ANSI(CP936) 解码，
# 实测会导致 -NoDirectInstall / -TestApk 这两个 [switch] 参数被静默丢弃
# （表现为 -NoDirectInstall 无效、仍走 direct install 回退）。
$firstBytes = [System.IO.File]::ReadAllBytes($ScriptPath)
$hasBom = ($firstBytes.Length -ge 3 -and $firstBytes[0] -eq 0xEF -and $firstBytes[1] -eq 0xBB -and $firstBytes[2] -eq 0xBF)
$nonAscii = @($scriptText.ToCharArray() | Where-Object { [int]$_ -gt 127 }).Count
Assert (($nonAscii -eq 0) -or $hasBom) 'A19 含非 ASCII 时必须带 UTF-8 BOM（否则 PS 5.1 静默丢弃 switch 参数）' "bom=$hasBom nonAscii=$nonAscii"

# -TestApk 缺 -PackageName 的快速失败（S19-F1）。旧实现里 -TestApk 只加 `install -t`，
# 默认 PackageName 仍是主应用：调用方装 androidTest APK 却验 `pm path <主包>`，
# 主应用已装时确认循环会据此报 PASS —— 虚假成功。判定必须用 $PSBoundParameters
# 而不是比较 $PackageName，否则"显式传了主包名"与"没传"无法区分。
Assert ($code -match '\$PSBoundParameters\.ContainsKey\(''PackageName''\)') 'A20 用 $PSBoundParameters 判定调用方是否显式传入 -PackageName'
# S19-F2：ContainsKey 只回答「参数在不在」，不回答「值能不能用」。调用方显式传
# '' 或 '   ' 时 ContainsKey 为 true，仅靠存在性判断会让空包名一路走到 adb。
# IsNullOrWhiteSpace 用一个谓词同时覆盖 null / 空串 / 纯空白三种无效值。
Assert ($code -match '\[string\]::IsNullOrWhiteSpace\(\$PackageName\)') 'A20b 用 [string]::IsNullOrWhiteSpace 判定 -PackageName 值是否可用'
Assert ($code -match '\$testPackageNameMissing\s*=') 'A20c 无效包名判定收敛到单一变量'
Assert ($code -match '-not\s+\$packageNameExplicit\s+-or') 'A20d 无效包名判定同时覆盖「省略」与「传了无效值」两条路径'
Assert ($code -match '\$TestApk\s+-and\s+\$testPackageNameMissing') 'A21 守卫条件为 -TestApk 且包名无效（省略或空白）'
Assert ($code -match 'RESULT: FAILED[^\r\n]*-TestApk') 'A22 缺参失败输出含 RESULT: FAILED 且点名 -TestApk'
Assert ($code -notmatch '\.test''?\s*\)?\s*\+|\+\s*''\.test''') 'A23 不得用固定 .test 后缀拼接推导测试包名'

# 守卫必须出现在第一条 adb 调用之前：截取「无效包名判定」与「第一条裸 adb 调用」的
# 行号，前者必须更靠前。用源码文本定位（两个标记都是稳定的字面量）。
$rawLines = @($scriptText -split "`r?`n")
$guardExitLine = -1
$firstAdbCallLine = -1
for ($i = 0; $i -lt $rawLines.Count; $i++) {
    if ($guardExitLine -lt 0 -and $rawLines[$i] -match '\$TestApk\s+-and\s+\$testPackageNameMissing') { $guardExitLine = $i }
    if ($firstAdbCallLine -lt 0 -and $rawLines[$i] -match '& adb ') { $firstAdbCallLine = $i }
}
Assert ($guardExitLine -ge 0) 'A24 能在源码中定位 -TestApk 守卫' "line=$guardExitLine"
Assert ($firstAdbCallLine -ge 0) 'A25 能在源码中定位裸 adb 调用点' "line=$firstAdbCallLine"
Assert ($guardExitLine -ge 0 -and $guardExitLine -lt $firstAdbCallLine) 'A26 守卫位于第一条 adb 调用之前（缺参/空白包名时不触碰设备）' "guard=$guardExitLine adb=$firstAdbCallLine"

# ---------------------------------------------------------------- 假 adb
function New-FakeAdb {
    param([string]$Dir)
    # 关键：push / install 把成功摘要写到 STDERR —— 真实 adb 就是这么做的。
    $bat = @"
@echo off
setlocal
if defined CRA_FAKE_ADB_LOG echo %*>> "%CRA_FAKE_ADB_LOG%"
if "%3"=="get-state" (echo device& exit /b 0)
if "%3"=="devices" (
  echo List of devices attached
  echo %2`tdevice
  exit /b 0
)
if "%3"=="push" (
  echo 1 file pushed, 0 skipped. 38.2 MB/s ^(12345678 bytes in 0.312s^) 1>&2
  exit /b 0
)
if "%3"=="pull" (
  copy /y "%CRA_FAKE_UI_XML%" "%5" >nul
  exit /b 0
)
if "%3"=="install" (
  if "%CRA_FAKE_INSTALL_OK%"=="1" (
    echo Performing Streamed Install 1>&2
    echo Success 1>&2
    exit /b 0
  )
  echo Failure [INSTALL_FAILED_ABORTED] 1>&2
  exit /b 1
)
if "%3"=="shell" (
  if "%4"=="pm" (
    rem S19-F1: when an expected package is configured, reject any other one. `shell pm path X`
    rem reaches the shim as argv %3=shell %4=pm %5=path %6=X, so %6 is the queried package.
    rem A confirm loop that verifies the wrong package must fail here, not silently succeed.
    if not "%CRA_FAKE_PM_EXPECT%"=="" if not "%6"=="%CRA_FAKE_PM_EXPECT%" (
      echo Error: package %6 not found 1>&2
      exit /b 1
    )
    if "%CRA_FAKE_PM_PATH%"=="1" (
      echo package:/data/app/~~fake==/com.creationreadingassistant-fake==/base.apk
      exit /b 0
    )
    exit /b 1
  )
  if "%4"=="uiautomator" (exit /b 0)
  if "%4"=="input" (exit /b 0)
  if "%4"=="am" (echo Starting: Intent { act=android.intent.action.INSTALL_PACKAGE }& exit /b 0)
  if "%4"=="rm" (exit /b 0)
  exit /b 0
)
exit /b 0
"@
    $target = Join-Path $Dir 'adb.cmd'
    [System.IO.File]::WriteAllText($target, ($bat -replace "`r?`n", "`r`n"), [System.Text.ASCIIEncoding]::new())
}

function ConvertTo-PsSingleQuoted {
    # Wrap a value as a PowerShell single-quoted literal (doubling embedded quotes).
    # Needed because the verbatim-package-name scenarios must be launched via
    # `-Command`: `-File` hands argv to the Windows command-line parser, which DROPS an
    # empty string argument entirely ("缺少参数 PackageName" instead of binding ''),
    # so the empty-string case cannot be delivered through `-File` at all.
    param([string]$Value)
    return "'" + ($Value -replace "'", "''") + "'"
}

function Invoke-ContractScenario {
    param(
        [string]$Name,
        [string]$UiXml,
        [int]$PmPath = 0,
        [int]$InstallOk = 0,
        [int]$TimeoutSeconds = 3,
        [switch]$NoDirectInstall,
        # S19-F1: exercise the androidTest path. -PackageName is added only when given, so
        # the "omitted" case stays distinguishable from "explicitly passed the default".
        [switch]$TestApk,
        [string]$PackageName = '',
        # Expected package for `pm path`; the fake adb rejects any other name.
        [string]$PmExpect = '',
        # S19-F2: pass -PackageName VERBATIM (including '' and '   ') by launching the
        # script through `-Command` instead of `-File`. Only meaningful together with
        # -TestApk; `-File` cannot carry an empty string (see ConvertTo-PsSingleQuoted).
        [switch]$VerbatimPackageName
    )
    $work = Join-Path $env:TEMP ("cra-contract-{0}" -f [guid]::NewGuid().ToString('N'))
    $shim = Join-Path $work 'bin'
    New-Item -ItemType Directory -Path $shim -Force | Out-Null
    New-FakeAdb -Dir $shim
    $uiFile = Join-Path $work 'ui.xml'
    [System.IO.File]::WriteAllText($uiFile, $UiXml, [System.Text.UTF8Encoding]::new($false))
    $apk = Join-Path $work 'fake.apk'
    [System.IO.File]::WriteAllBytes($apk, [byte[]](0x50, 0x4B, 0x03, 0x04))
    $logFile = Join-Path $work 'adb.log'
    $outFile = Join-Path $work 'out.txt'

    $saved = @{
        Path    = $env:PATH
        Log     = $env:CRA_FAKE_ADB_LOG
        Ui      = $env:CRA_FAKE_UI_XML
        Pm      = $env:CRA_FAKE_PM_PATH
        Install = $env:CRA_FAKE_INSTALL_OK
        PmExp   = $env:CRA_FAKE_PM_EXPECT
    }
    $env:PATH = "$shim;$env:PATH"
    $env:CRA_FAKE_ADB_LOG = $logFile
    $env:CRA_FAKE_UI_XML = $uiFile
    $env:CRA_FAKE_PM_PATH = "$PmPath"
    $env:CRA_FAKE_INSTALL_OK = "$InstallOk"
    $env:CRA_FAKE_PM_EXPECT = "$PmExpect"

    $psExe = Join-Path $PSHOME 'powershell.exe'
    $argList = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $ScriptPath,
        '-Serial', 'fake-serial-0001', '-Apk', $apk, '-TimeoutSeconds', "$TimeoutSeconds")
    # 裸 `-NoDirectInstall`：经 -File 传入时参数是字符串，PS 5.1 不接受
    # `-NoDirectInstall:$true` 这种带值写法（无法把 "true" 转成 SwitchParameter）。
    if ($NoDirectInstall) { $argList += '-NoDirectInstall' }
    if ($TestApk) { $argList += '-TestApk' }
    if ($PackageName) { $argList += @('-PackageName', $PackageName) }
    if ($VerbatimPackageName) {
        # Rebuild as a -Command invocation so '' / '   ' survive argument binding.
        $argList = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command',
            "& $(ConvertTo-PsSingleQuoted $ScriptPath)" +
            " -Serial $(ConvertTo-PsSingleQuoted 'fake-serial-0001')" +
            " -Apk $(ConvertTo-PsSingleQuoted $apk)" +
            " -TimeoutSeconds $TimeoutSeconds")
        if ($NoDirectInstall) { $argList[-1] += ' -NoDirectInstall' }
        if ($TestApk) { $argList[-1] += ' -TestApk' }
        $argList[-1] += " -PackageName $(ConvertTo-PsSingleQuoted $PackageName)"
    }
    $invocation = $argList -join ' '

    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & $psExe @argList > $outFile 2>&1
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    $output = (Get-Content -LiteralPath $outFile -Raw -ErrorAction SilentlyContinue)
    if ($null -eq $output) { $output = '' }
    # Distinguish "fake adb was never invoked" (log file absent) from "invoked but wrote
    # nothing". The shim writes on its first line unconditionally, so absence == zero calls.
    $adbLogExisted = Test-Path -LiteralPath $logFile
    $adbLog = if ($adbLogExisted) { (Get-Content -LiteralPath $logFile -Raw) } else { '' }

    $env:PATH = $saved.Path
    $env:CRA_FAKE_ADB_LOG = $saved.Log
    $env:CRA_FAKE_UI_XML = $saved.Ui
    $env:CRA_FAKE_PM_PATH = $saved.Pm
    $env:CRA_FAKE_INSTALL_OK = $saved.Install
    $env:CRA_FAKE_PM_EXPECT = $saved.PmExp
    Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue

    [void]$dumps.Add("### scenario=$Name exit=$exitCode`n--- invocation ---`n$invocation`n--- script output ---`n$output`n--- fake adb log (existed=$adbLogExisted) ---`n$adbLog")

    [pscustomobject]@{
        Name          = $Name
        ExitCode      = $exitCode
        Output        = $output
        AdbLog        = $adbLog
        AdbLogExisted = $adbLogExisted
        Invocation    = $invocation
    }
}

# ---------------------------------------------------------------- UI fixture
# 中文用 UTF-8 字节构造，避免测试文件自身编码影响断言。
$cn = [System.Text.Encoding]::UTF8.GetString([byte[]](
        0xE7, 0xBB, 0xA7, 0xE7, 0xBB, 0xAD, 0xE5, 0xAE, 0x89, 0xE8, 0xA3, 0x85))   # 继续安装
$risk = [System.Text.Encoding]::UTF8.GetString([byte[]](
        0xE9, 0xA3, 0x8E, 0xE9, 0x99, 0xA9, 0xE6, 0x8F, 0x90, 0xE7, 0xA4, 0xBA))   # 风险提示

# 真机 uiautomator dump 的声明行实测为 standalone='yes'（写成 'true' 会被 .NET 拒绝，
# 与被测脚本无关），fixture 必须与真机一致才具备回归意义。
$header = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>"
$uiComplete = @"
$header
<hierarchy rotation="0">
  <node index="0" text="$risk" resource-id="com.miui.packageinstaller:id/checkbox" class="android.widget.CheckBox" package="com.miui.packageinstaller" checked="true" enabled="true" bounds="[40,900][1040,1000]" />
  <node index="1" text="$cn" resource-id="com.miui.packageinstaller:id/start_button" class="android.widget.Button" package="com.miui.packageinstaller" checked="false" enabled="true" bounds="[100,1200][980,1320]" />
</hierarchy>
"@
$uiPending = @"
$header
<hierarchy rotation="0">
  <node index="0" text="$risk" resource-id="com.miui.packageinstaller:id/checkbox" class="android.widget.CheckBox" package="com.miui.packageinstaller" checked="true" enabled="true" bounds="[40,900][1040,1000]" />
  <node index="1" text="$cn" resource-id="com.miui.packageinstaller:id/second_button" class="android.widget.Button" package="com.miui.packageinstaller" checked="false" enabled="true" bounds="[100,1200][980,1320]" />
</hierarchy>
"@
$uiBroken = @"
$header
<hierarchy rotation="0">
  <node index="0" text="$risk" resource-id=com.miui.packageinstaller:id/start_button package=com.miui.packageinstaller enabled="true" bounds="[100,1200][980,1320]" />
</hierarchy>
"@

# ---------------------------------------------------------------- 行为契约
Write-Host ""
Write-Host "== B. 行为契约（假 adb，成功摘要写 STDERR） =="

Write-Host "-- B1 确认循环成功"
$r1 = Invoke-ContractScenario -Name 'confirm-loop-success' -UiXml $uiComplete -PmPath 1
Assert ($r1.ExitCode -eq 0) 'B1.1 退出码 0' "exit=$($r1.ExitCode)"
Assert ($r1.Output -match 'RESULT: CONFIRM_LOOP') 'B1.2 RESULT: CONFIRM_LOOP'
Assert ($r1.Output -notmatch 'FALLBACK') 'B1.3 未落回退路径'
Assert ($r1.Output -notmatch 'adb failed') 'B1.4 adb 成功摘要写 STDERR 未被误判为失败'
Assert ($r1.AdbLog -match 'input tap') 'B1.5 真的驱动了安装器控件（input tap）'
Assert ($r1.AdbLog -match 'pull') 'B1.6 UI dump 走 pull + UTF-8 解码'
Assert ($r1.AdbLog -notmatch 'install -r -t') 'B1.7 普通主 APK 的安装命令不带 -t' "log=$($r1.AdbLog)"
$pmQueried1 = @([regex]::Matches($r1.AdbLog, 'pm path (\S+)') | ForEach-Object { $_.Groups[1].Value })
Assert ($pmQueried1.Count -gt 0 -and ($pmQueried1 | Where-Object { $_ -ne 'com.creationreadingassistant' }).Count -eq 0) 'B1.8 省略 -PackageName 时仍用默认主包名验证' "queried=$($pmQueried1 -join ',')"

Write-Host "-- B2 超时且 -NoDirectInstall"
$r2 = Invoke-ContractScenario -Name 'timeout-no-direct' -UiXml $uiPending -PmPath 0 -NoDirectInstall
Assert ($r2.ExitCode -eq 1) 'B2.1 退出码 1' "exit=$($r2.ExitCode)"
Assert ($r2.Output -match 'RESULT: FAILED') 'B2.2 RESULT: FAILED'
Assert ($r2.Output -match 'NoDirectInstall') 'B2.3 失败原因说明回退被禁用'
Assert ($r2.Output -notmatch 'FALLBACK_DIRECT_INSTALL') 'B2.4 未产生 direct install 回退'

Write-Host "-- B3 回退 direct install"
$r3 = Invoke-ContractScenario -Name 'fallback-direct' -UiXml $uiPending -PmPath 1 -InstallOk 1
Assert ($r3.ExitCode -eq 2) 'B3.1 退出码 2（回退专用）' "exit=$($r3.ExitCode)"
Assert ($r3.Output -match 'RESULT: FALLBACK_DIRECT_INSTALL') 'B3.2 RESULT 标记为回退'
Assert ($r3.Output -match 'NOT a confirm-loop PASS') 'B3.3 RESULT 显式声明不是确认循环 PASS'
Assert ($r3.Output -notmatch 'RESULT: CONFIRM_LOOP') 'B3.4 回退不会被写成确认循环成功'

Write-Host "-- B4 UI XML 解析失败可见"
$r4 = Invoke-ContractScenario -Name 'parse-failure' -UiXml $uiBroken -PmPath 0 -NoDirectInstall
$m = [regex]::Match($r4.Output, 'UI dumps parsed with errors:\s*(\d+)')
$parsedErrors = if ($m.Success) { [int]$m.Groups[1].Value } else { -1 }
Assert ($r4.ExitCode -eq 1) 'B4.1 退出码 1' "exit=$($r4.ExitCode)"
Assert ($parsedErrors -gt 0) 'B4.2 解析失败计数 > 0 且进入失败原因' "count=$parsedErrors"
Assert ($r4.Output -match 'XML_PARSE_ERROR_SAMPLE') 'B4.3 首条解析错误原文被打印（不再静默吞掉）'

Write-Host "-- B5 -TestApk 缺 -PackageName 快速失败（S19-F1）"
# 不传 -PackageName。主包名仍会是默认的 com.creationreadingassistant，而 fake adb 的
# CRA_FAKE_PM_PATH=1 会让主包"存在" —— 这正是旧实现的虚假成功条件。修复后脚本必须在
# 第一条 adb 之前就退出，因此 fake adb 日志必须为空。
$r5 = Invoke-ContractScenario -Name 'testapk-without-packagename' -UiXml $uiComplete -PmPath 1 -TestApk
Assert ($r5.ExitCode -eq 1) 'B5.1 退出码 1' "exit=$($r5.ExitCode)"
Assert ($r5.Output -match 'RESULT: FAILED') 'B5.2 输出含 RESULT: FAILED'
Assert ($r5.Output -match '-TestApk') 'B5.3 错误信息点名 -TestApk'
Assert ($r5.Output -match 'PackageName') 'B5.4 错误信息点名 -PackageName'
Assert ($r5.Output -notmatch 'RESULT: CONFIRM_LOOP') 'B5.5 不出现成功标记 CONFIRM_LOOP'
Assert ($r5.Output -notmatch 'FALLBACK_DIRECT_INSTALL') 'B5.6 不出现回退标记'
Assert ($r5.Output -notmatch 'Installed .* successfully') 'B5.7 不出现安装成功文案'
Assert (-not $r5.AdbLogExisted -and [string]::IsNullOrWhiteSpace($r5.AdbLog)) 'B5.8 零 adb 调用（fake adb 日志文件根本未生成）' "existed=$($r5.AdbLogExisted) log='$($r5.AdbLog)'"

Write-Host "-- B9 -TestApk 显式纯空白包名快速失败（S19-F2）"
# Codex 复核发现的 Spec P2：ContainsKey 对 '   ' 返回 true，旧守卫因此放行，
# 脚本带着空白包名走到 adb devices。本场景在无设备探针上复现过该绕过。
$r9 = Invoke-ContractScenario -Name 'testapk-whitespace-packagename' -UiXml $uiComplete -PmPath 1 -TestApk -PackageName '   ' -VerbatimPackageName
Assert ($r9.ExitCode -eq 1) 'B9.1 退出码 1' "exit=$($r9.ExitCode)"
Assert ($r9.Output -match 'RESULT: FAILED') 'B9.2 输出含 RESULT: FAILED'
Assert ($r9.Output -match '-TestApk') 'B9.3 错误信息点名 -TestApk'
Assert ($r9.Output -match '-PackageName') 'B9.4 错误信息点名 -PackageName'
Assert ($r9.Output -notmatch 'RESULT: CONFIRM_LOOP') 'B9.5 不出现成功标记 CONFIRM_LOOP'
Assert ($r9.Output -notmatch 'FALLBACK_DIRECT_INSTALL') 'B9.6 不出现回退标记'
Assert ($r9.Output -notmatch 'Installed .* successfully') 'B9.7 不出现安装成功文案'
Assert (-not $r9.AdbLogExisted -and [string]::IsNullOrWhiteSpace($r9.AdbLog)) 'B9.8 零 adb 调用（fake adb 日志文件根本未生成）' "existed=$($r9.AdbLogExisted) log='$($r9.AdbLog)'"

Write-Host "-- B10 -TestApk 显式空字符串包名快速失败（S19-F2）"
# 空串经 `-File` 会被 Windows 命令行解析器整体丢弃（表现为缺少参数），无法到达脚本，
# 因此本场景改由 -Command 投递（ConvertTo-PsSingleQuoted）。若投递不可靠，绑定阶段
# 就会报错而不是走到守卫，B10.1 会直接失败——不会伪造覆盖结论。
$r10 = Invoke-ContractScenario -Name 'testapk-empty-packagename' -UiXml $uiComplete -PmPath 1 -TestApk -PackageName '' -VerbatimPackageName
Assert ($r10.ExitCode -eq 1) 'B10.1 退出码 1' "exit=$($r10.ExitCode)"
Assert ($r10.Output -match 'RESULT: FAILED') 'B10.2 输出含 RESULT: FAILED'
Assert ($r10.Output -match '-TestApk') 'B10.3 错误信息点名 -TestApk'
Assert ($r10.Output -match '-PackageName') 'B10.4 错误信息点名 -PackageName'
Assert ($r10.Output -notmatch 'RESULT: CONFIRM_LOOP') 'B10.5 不出现成功标记 CONFIRM_LOOP'
Assert ($r10.Output -notmatch 'FALLBACK_DIRECT_INSTALL') 'B10.6 不出现回退标记'
Assert ($r10.Output -notmatch 'Installed .* successfully') 'B10.7 不出现安装成功文案'
Assert (-not $r10.AdbLogExisted -and [string]::IsNullOrWhiteSpace($r10.AdbLog)) 'B10.8 零 adb 调用（fake adb 日志文件根本未生成）' "existed=$($r10.AdbLogExisted) log='$($r10.AdbLog)'"

Write-Host "-- B6 -TestApk 带显式测试包名走确认循环（S19-F1）"
# 显式传入变体测试包名。fake adb 配置为：只有 pm path 收到的正是该包名才返回成功。
# 若脚本仍用主包名验证，fake adb 会拒绝 → 走不到 CONFIRM_LOOP。
$testPkg = 'com.creationreadingassistant.r3gate.test'
$r6 = Invoke-ContractScenario -Name 'testapk-with-packagename' -UiXml $uiComplete -PmPath 1 -TestApk -PackageName $testPkg -PmExpect $testPkg
Assert ($r6.ExitCode -eq 0) 'B6.1 退出码 0（走既有确认循环成功路径）' "exit=$($r6.ExitCode)"
Assert ($r6.Output -match 'RESULT: CONFIRM_LOOP') 'B6.2 RESULT: CONFIRM_LOOP'
Assert ($r6.Output -notmatch 'FALLBACK') 'B6.3 未落回退路径'
# 把日志里所有 `pm path <X>` 的 X 收集成集合再断言，而不是子串匹配：
# 测试包名本身以主包名为前缀（...r3gate.test），子串判据永远为真，测不出东西。
$pmQueried = @([regex]::Matches($r6.AdbLog, 'pm path (\S+)') | ForEach-Object { $_.Groups[1].Value })
Assert ($pmQueried.Count -gt 0) 'B6.4 确实发起了 pm path 验证' "count=$($pmQueried.Count)"
Assert (($pmQueried | Where-Object { $_ -ne $testPkg }).Count -eq 0) 'B6.5 所有 pm path 都用显式测试包名（未用主包名验证）' "queried=$($pmQueried -join ',')"

Write-Host "-- B7 普通主 APK 显式传主包名（向后兼容）"
# 显式传主包名：与省略时行为一致，仍走确认循环；用于确认 -PackageName 显式化本身
# 没有改变普通主 APK 的既有契约。
$mainPkg = 'com.creationreadingassistant'
$r7 = Invoke-ContractScenario -Name 'main-apk-explicit-packagename' -UiXml $uiComplete -PmPath 1 -PackageName $mainPkg -PmExpect $mainPkg
Assert ($r7.ExitCode -eq 0) 'B7.1 退出码 0' "exit=$($r7.ExitCode)"
Assert ($r7.Output -match 'RESULT: CONFIRM_LOOP') 'B7.2 RESULT: CONFIRM_LOOP'
$pmQueried7 = @([regex]::Matches($r7.AdbLog, 'pm path (\S+)') | ForEach-Object { $_.Groups[1].Value })
Assert ($pmQueried7.Count -gt 0 -and ($pmQueried7 | Where-Object { $_ -ne $mainPkg }).Count -eq 0) 'B7.3 pm path 使用显式主包名' "queried=$($pmQueried7 -join ',')"

Write-Host "-- B8 -TestApk 回退路径仍带 -t（androidTest APK 既有契约不回退）"
# 确认循环超时 → direct install 回退。androidTest APK 必须继续用 `install -r -t` 安装，
# 显式 -PackageName 的新守卫不得顺带弄丢 -t。
$r8 = Invoke-ContractScenario -Name 'testapk-fallback-keeps-t' -UiXml $uiPending -PmPath 1 -InstallOk 1 -TestApk -PackageName $testPkg -PmExpect $testPkg
Assert ($r8.ExitCode -eq 2) 'B8.1 退出码 2（回退专用）' "exit=$($r8.ExitCode)"
Assert ($r8.Output -match 'RESULT: FALLBACK_DIRECT_INSTALL') 'B8.2 RESULT 标记为回退'
Assert ($r8.AdbLog -match 'install -r -t') 'B8.3 回退安装仍带 -t' "log=$($r8.AdbLog)"

Write-Host ""
Write-Host ("断言合计: {0}，失败: {1}" -f $script:checks, $failures.Count)
if ($failures.Count -gt 0) {
    Write-Host "---- 失败明细 ----"
    foreach ($f in $failures) { Write-Host "  - $f" }
    Write-Host "---- 场景原始输出 ----"
    foreach ($d in $dumps) { Write-Host $d }
    exit 1
}
Write-Host "ALL_CONTRACT_CHECKS_PASSED"
exit 0
