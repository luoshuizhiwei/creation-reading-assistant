#Requires -Version 5.1
<#
install_with_confirm.ps1 的无设备契约回归（device-free contract regression）。

覆盖范围
  A. 静态契约：禁用写法（2>$null / Read-Host / pause / settings put / pm grant / su）、
     成功与回退的 RESULT 文案与退出码、adb 必须显式 -s、UI dump 必须按 UTF-8 解码、
     临时文件必须是 GUID 精确目标。
  B. 行为契约：在 %TEMP% 里放一个假 adb（把成功摘要写到 STDERR，真实复现 adb 的行为），
     用真实脚本跑四种情形 —— 确认循环成功 / 超时且禁用回退 / direct install 回退 /
     UI XML 解析失败可见，逐一断言退出码与 RESULT 文案。

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

function Invoke-ContractScenario {
    param(
        [string]$Name,
        [string]$UiXml,
        [int]$PmPath = 0,
        [int]$InstallOk = 0,
        [int]$TimeoutSeconds = 3,
        [switch]$NoDirectInstall
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
    }
    $env:PATH = "$shim;$env:PATH"
    $env:CRA_FAKE_ADB_LOG = $logFile
    $env:CRA_FAKE_UI_XML = $uiFile
    $env:CRA_FAKE_PM_PATH = "$PmPath"
    $env:CRA_FAKE_INSTALL_OK = "$InstallOk"

    $psExe = Join-Path $PSHOME 'powershell.exe'
    $argList = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $ScriptPath,
        '-Serial', 'fake-serial-0001', '-Apk', $apk, '-TimeoutSeconds', "$TimeoutSeconds")
    # 裸 `-NoDirectInstall`：经 -File 传入时参数是字符串，PS 5.1 不接受
    # `-NoDirectInstall:$true` 这种带值写法（无法把 "true" 转成 SwitchParameter）。
    if ($NoDirectInstall) { $argList += '-NoDirectInstall' }

    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & $psExe @argList > $outFile 2>&1
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    $output = (Get-Content -LiteralPath $outFile -Raw -ErrorAction SilentlyContinue)
    if ($null -eq $output) { $output = '' }
    $adbLog = if (Test-Path -LiteralPath $logFile) { (Get-Content -LiteralPath $logFile -Raw) } else { '' }

    $env:PATH = $saved.Path
    $env:CRA_FAKE_ADB_LOG = $saved.Log
    $env:CRA_FAKE_UI_XML = $saved.Ui
    $env:CRA_FAKE_PM_PATH = $saved.Pm
    $env:CRA_FAKE_INSTALL_OK = $saved.Install
    Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue

    [void]$dumps.Add("### scenario=$Name exit=$exitCode`n--- argv ---`n$($argList -join ' ')`n--- script output ---`n$output`n--- fake adb log ---`n$adbLog")

    [pscustomobject]@{
        Name     = $Name
        ExitCode = $exitCode
        Output   = $output
        AdbLog   = $adbLog
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
