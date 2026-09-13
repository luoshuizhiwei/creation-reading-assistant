param(
    [string]$Apk = '',
    [string]$Serial = 'c49ac6cf',
    [string]$PackageName = 'com.creationreadingassistant',
    # 测试 APK 须加 -t 才能安装；普通 debug/release APK 不需要。
    [switch]$TestApk,
    # 用户已授权（R3-WORKBUDDY.md「安装授权」）：确认循环超时/被 MIUI 拦截后，
    # 允许直接 `adb install -r` 回退继续验收，但回退成功**不得写成脚本 PASS**——
    # 本脚本用退出码 2 与 RESULT 标记行区分。如需强制只走确认循环，传 -NoDirectInstall。
    [switch]$NoDirectInstall,
    [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Apk)) {
    $Apk = Join-Path $PSScriptRoot '..\app\build\outputs\apk\debug\app-debug.apk'
}
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$remoteApk = "/sdcard/Download/cra-codex-$([guid]::NewGuid().ToString('N')).apk"
$remoteUi = "/sdcard/cra-install-ui-$([guid]::NewGuid().ToString('N')).xml"
# Local scratch copy of the UI dump; see the UTF-8 note at the read site below.
$localUi = Join-Path $env:TEMP ("cra-install-ui-{0}.xml" -f ([guid]::NewGuid().ToString('N')))

function Invoke-AdbNative {
    # No param block on purpose: the automatic $args variable is the only reliable way in
    # PS 5.1 to forward an arbitrary adb command line without a positional-binding error
    # ("找不到接受实际参数...的位置形式参数").
    # PowerShell 5.1 quirk: with $ErrorActionPreference='Stop' (set at the top of this
    # script), a native command's STDERR is promoted to a *terminating* ErrorRecord as
    # soon as the call is piped or its stderr is redirected. adb writes its progress and
    # summary lines ("1 file pushed, 0 skipped. 38.2 MB/s ...") to STDERR, so the old
    # `Invoke-Adb push ... | Out-Host` aborted the confirm loop carrying adb's own
    # *success* text as the exception message, which then fell through to the direct
    # install fallback and reported the script as failed.
    #
    # Fix: run the native call under 'Continue' so STDERR can never terminate, keep it
    # visible as evidence, and judge success solely by $LASTEXITCODE.
    $previousEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & adb -s $Serial @args
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousEap
    }
    [pscustomobject]@{ ExitCode = $code; Output = $output }
}

function Invoke-Adb {
    $result = Invoke-AdbNative @args
    if ($result.ExitCode -ne 0) {
        throw "adb failed ($($result.ExitCode)): $($args -join ' ')"
    }
    $result.Output
}

function Get-NodeCenter {
    param([System.Xml.XmlElement]$Node)
    if ($Node.bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') { return $null }
    [pscustomobject]@{
        X = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
        Y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    }
}

function Invoke-InstallerNode {
    param([System.Xml.XmlElement[]]$Nodes)

    # Only click exact controls owned by the MIUI package installer.
    $candidates = @(
        @{ Id = 'android:id/button2' },
        @{ Id = 'android:id/button3' },
        @{ Id = 'com.miui.packageinstaller:id/checkbox' },
        @{ Id = 'com.miui.packageinstaller:id/second_button' }
    )

    foreach ($candidate in $candidates) {
        $node = $Nodes | Where-Object {
            $_.package -eq 'com.miui.packageinstaller' -and
            $_.'resource-id' -eq $candidate.Id -and
            $_.enabled -eq 'true'
        } | Select-Object -First 1
        if ($null -eq $node) { continue }
        if ($candidate.Id -eq 'com.miui.packageinstaller:id/checkbox' -and $node.checked -eq 'true') {
            continue
        }

        # Require the risk acknowledgement before allowing the final action.
        if ($candidate.Id -eq 'com.miui.packageinstaller:id/second_button') {
            $checkbox = $Nodes | Where-Object {
                $_.'resource-id' -eq 'com.miui.packageinstaller:id/checkbox'
            } | Select-Object -First 1
            if ($null -ne $checkbox -and $checkbox.checked -ne 'true') { continue }
        }

        $center = Get-NodeCenter $node
        if ($null -eq $center) { continue }
        Write-Host "TAP installer control '$($candidate.Id)' at $($center.X),$($center.Y)"
        Invoke-Adb shell input tap $center.X $center.Y | Out-Null
        return $true
    }
    return $false
}

# Direct `adb install` fallback: on HyperOS/MIUI the confirmation loop can be blocked
# by security popups the automation cannot drive; the user explicitly authorized this
# fallback for acceptance sessions. Exit code 2 + RESULT marker keep it distinguishable
# from a confirm-loop PASS.
function Invoke-DirectInstall {
    param([string]$Reason)
    if ($NoDirectInstall) {
        Write-Host "RESULT: FAILED ($Reason; direct install disabled by -NoDirectInstall)"
        return $false
    }
    Write-Host "FALLBACK: confirm loop did not complete ($Reason); trying direct adb install."
    $installArgs = @('install', '-r')
    if ($TestApk) { $installArgs += '-t' }
    $installArgs += $apkPath
    $installResult = Invoke-AdbNative @installArgs
    if ($installResult.ExitCode -eq 0) {
        $verify = ((Invoke-AdbNative shell pm path $PackageName).Output -join "`n")
        if ($verify -match '^package:') {
            Write-Host "RESULT: FALLBACK_DIRECT_INSTALL (installed via direct adb install; NOT a confirm-loop PASS)"
            exit 2
        }
    }
    Write-Host "RESULT: FAILED (direct adb install exit=$($installResult.ExitCode) or package not present)"
    return $false
}

try {
    # Device discipline: `adb devices` is the authoritative inventory, and every adb call
    # below is bound to $Serial with `-s`. The inventory is printed as evidence and only
    # hard-fails when it was parsed and $Serial is missing from it; an unparsable
    # inventory falls through to the get-state probe instead of blocking on a text detail.
    $deviceEntries = 0
    $serialListed = $false
    foreach ($line in @((Invoke-AdbNative devices).Output)) {
        if ($line -match '^(\S+)\s+(\S+)\s*$') {
            $deviceEntries++
            if ($Matches[1] -eq $Serial -and $Matches[2] -eq 'device') { $serialListed = $true }
        }
    }
    Write-Host "adb devices entries=$deviceEntries; serial '$Serial' listed as device: $serialListed"
    if ($deviceEntries -gt 0 -and -not $serialListed) {
        throw "Serial '$Serial' is not reported as 'device' by adb devices."
    }

    # Do NOT redirect stderr with `2>$null` here: under $ErrorActionPreference='Stop'
    # (set above) PowerShell 5.1 turns a native command's redirected stderr into a
    # *terminating* error, which would abort before the availability check below and
    # leave the "device is not available" message unreachable. Letting stderr through
    # keeps adb's own failure text visible as evidence.
    $stateProbe = Invoke-AdbNative get-state
    $deviceState = (($stateProbe.Output -join "`n")).Trim()
    if ($stateProbe.ExitCode -ne 0 -or $deviceState -ne 'device') {
        throw "Android device '$Serial' is not available."
    }

    Invoke-Adb push $apkPath $remoteApk | Out-Host
    # Some MIUI devices register a third-party browser as the default VIEW handler for APK files.
    # Start the system installer explicitly so the confirmation loop below observes the same UI
    # it is designed to drive, rather than waiting on an unrelated app's installation surface.
    $amStartExit = 0
    $amStart = Invoke-AdbNative shell am start `
        -n com.miui.packageinstaller/com.miui.packageInstaller.InstallStart `
        -a android.intent.action.INSTALL_PACKAGE `
        -d "file://$remoteApk" `
        -t application/vnd.android.package-archive
    if ($amStart.ExitCode -ne 0) { $amStartExit = $amStart.ExitCode }

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $sawInstallerUi = $false
    $xmlParseErrors = 0
    $uiActionErrors = 0
    $xmlParseErrorSample = $null
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 600

        $dump = Invoke-AdbNative shell uiautomator dump $remoteUi
        if ($dump.ExitCode -ne 0) { continue }
        # `adb exec-out cat` hands back raw UTF-8 bytes, but PowerShell decodes a native
        # command's stdout using the console code page (CP936 on zh-CN Windows). The UI
        # dump is full of Chinese text, so every node came back mojibake and some byte
        # sequences even swallowed the closing quote of an attribute value, producing
        # '"com.miui.packageinstaller" is an unexpected token' from [xml] and silently
        # tripping the catch below on every iteration -- the confirm loop went blind and
        # always reported "installer UI never appeared".
        # Pull the file and decode it explicitly as UTF-8 instead.
        [void](Invoke-AdbNative pull $remoteUi $localUi)
        if (-not (Test-Path -LiteralPath $localUi)) { continue }
        $xmlText = Get-Content -LiteralPath $localUi -Raw -Encoding UTF8
        Remove-Item -LiteralPath $localUi -Force -ErrorAction SilentlyContinue
        if ([string]::IsNullOrWhiteSpace($xmlText)) { continue }

        # Parsing is isolated from the rest of the loop body so that the counter below
        # cannot be inflated by unrelated failures (e.g. a rejected `input tap`) and
        # mistaken for an encoding regression.
        try {
            [xml]$ui = $xmlText
        } catch {
            $xmlParseErrors++
            if (-not $xmlParseErrorSample) { $xmlParseErrorSample = $_.Exception.Message }
            continue
        }
        try {
            $nodes = @($ui.SelectNodes('//node'))
            $completed = $nodes | Where-Object {
                $_.package -eq 'com.miui.packageinstaller' -and
                $_.'resource-id' -eq 'com.miui.packageinstaller:id/start_button' -and
                $_.enabled -eq 'true'
            } | Select-Object -First 1
            if ($null -ne $completed) {
                $pmProbe = Invoke-AdbNative shell pm path $PackageName
                $packagePath = ($pmProbe.Output -join "`n")
                if ($pmProbe.ExitCode -eq 0 -and $packagePath -match '^package:') {
                    $center = Get-NodeCenter $completed
                    if ($null -ne $center) {
                        Invoke-Adb shell input tap $center.X $center.Y | Out-Null
                    }
                    Write-Host "RESULT: CONFIRM_LOOP (installed through the MIUI installer UI)"
                    Write-Host "Installed $PackageName successfully."
                    exit 0
                }
            }
            if ($nodes | Where-Object { $_.package -eq 'com.miui.packageinstaller' }) {
                $sawInstallerUi = $true
            }
            [void](Invoke-InstallerNode $nodes)
        } catch {
            # Anything that is not XML parsing (e.g. a rejected `input tap`) is counted
            # separately, so an adb-level failure can never be reported as an encoding
            # regression -- and vice versa.
            $uiActionErrors++
        }
    }

    # Parse failures stay visible: count in every failure reason plus the first message,
    # so a decoding regression can no longer hide behind "installer UI never appeared".
    $diagnostics = "UI dumps parsed with errors: $xmlParseErrors (other UI action errors: $uiActionErrors)"
    if ($xmlParseErrorSample) { Write-Host "XML_PARSE_ERROR_SAMPLE: $xmlParseErrorSample" }
    $reason = if ($amStartExit -ne 0) {
        "am start exit=$amStartExit (installer never launched); $diagnostics"
    } elseif (-not $sawInstallerUi) {
        "installer UI never appeared (MIUI security popup or wrong VIEW handler); $diagnostics"
    } else {
        "timeout after ${TimeoutSeconds}s without reaching the install-complete state; $diagnostics"
    }
    [void](Invoke-DirectInstall -Reason $reason)
    exit 1
} catch {
    # `-ErrorAction Continue` is load-bearing: with $ErrorActionPreference='Stop' a bare
    # `Write-Error` is itself terminating, so the catch block would abort here and the
    # authorized transport-level fallback below would never run.
    Write-Error $_ -ErrorAction Continue
    # Transport-level failures (device offline, push failed) also get the authorized
    # direct-install chance before giving up.
    try {
        $stateProbe2 = Invoke-AdbNative get-state
        $deviceState = (($stateProbe2.Output -join "`n")).Trim()
        if ($stateProbe2.ExitCode -eq 0 -and $deviceState -eq 'device') {
            [void](Invoke-DirectInstall -Reason "exception: $($_.Exception.Message)")
        }
    } catch { }
    exit 1
} finally {
    # Cleanup must never mask the outcome computed above. With `2>$null` this call's
    # stderr would become terminating under $ErrorActionPreference='Stop' and replace
    # the script's own exit code (0/1/2) with an adb transport error, destroying the
    # very failure evidence this script exists to produce.
    [void](Invoke-AdbNative shell rm -f $remoteApk $remoteUi)
    if ($localUi -and (Test-Path -LiteralPath $localUi)) {
        Remove-Item -LiteralPath $localUi -Force -ErrorAction SilentlyContinue
    }
}
