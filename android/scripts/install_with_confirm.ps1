param(
    [string]$Apk = '',
    [string]$Serial = 'c49ac6cf',
    [string]$PackageName = 'com.creationreadingassistant'
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Apk)) {
    $Apk = Join-Path $PSScriptRoot '..\app\build\outputs\apk\debug\app-debug.apk'
}
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$remoteApk = "/sdcard/Download/cra-codex-$([guid]::NewGuid().ToString('N')).apk"
$remoteUi = "/sdcard/cra-install-ui-$([guid]::NewGuid().ToString('N')).xml"

function Invoke-Adb {
    & adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed ($LASTEXITCODE): $($args -join ' ')"
    }
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

try {
    $deviceState = (& adb -s $Serial get-state 2>$null).Trim()
    if ($LASTEXITCODE -ne 0 -or $deviceState -ne 'device') {
        throw "Android device '$Serial' is not available."
    }

    Invoke-Adb push $apkPath $remoteApk | Out-Host
    # Some MIUI devices register a third-party browser as the default VIEW handler for APK files.
    # Start the system installer explicitly so the confirmation loop below observes the same UI
    # it is designed to drive, rather than waiting on an unrelated app's installation surface.
    Invoke-Adb shell am start `
        -n com.miui.packageinstaller/com.miui.packageInstaller.InstallStart `
        -a android.intent.action.INSTALL_PACKAGE `
        -d "file://$remoteApk" `
        -t application/vnd.android.package-archive | Out-Host

    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 600

        & adb -s $Serial shell uiautomator dump $remoteUi 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) { continue }
        $xmlText = (& adb -s $Serial exec-out cat $remoteUi 2>$null) -join "`n"
        if ([string]::IsNullOrWhiteSpace($xmlText)) { continue }

        try {
            [xml]$ui = $xmlText
            $nodes = @($ui.SelectNodes('//node'))
            $completed = $nodes | Where-Object {
                $_.package -eq 'com.miui.packageinstaller' -and
                $_.'resource-id' -eq 'com.miui.packageinstaller:id/start_button' -and
                $_.enabled -eq 'true'
            } | Select-Object -First 1
            if ($null -ne $completed) {
                $packagePath = (& adb -s $Serial shell pm path $PackageName 2>$null) -join "`n"
                if ($LASTEXITCODE -eq 0 -and $packagePath -match '^package:') {
                    $center = Get-NodeCenter $completed
                    if ($null -ne $center) {
                        Invoke-Adb shell input tap $center.X $center.Y | Out-Null
                    }
                    Write-Host "Installed $PackageName successfully."
                    exit 0
                }
            }
            [void](Invoke-InstallerNode $nodes)
        } catch {
            # The UI XML can be incomplete while the installer changes pages.
        }
    }

    throw "Timed out waiting for MIUI package installer to install $PackageName."
} catch {
    Write-Error $_
    exit 1
} finally {
    & adb -s $Serial shell rm -f $remoteApk $remoteUi 2>$null | Out-Null
}
