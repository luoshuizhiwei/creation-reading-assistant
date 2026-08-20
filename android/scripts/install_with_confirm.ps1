param([string]$Apk = 'd:\develop\Code\Codex\creation-reading-assistant\android\app\build\outputs\apk\benchmarkRelease\app-benchmarkRelease.apk')
$apk = $Apk
$proc = Start-Process -FilePath 'adb' -ArgumentList "-s c49ac6cf install -r -t `"$apk`"" -PassThru -RedirectStandardOutput "$PSScriptRoot\install_out.txt" -RedirectStandardError "$PSScriptRoot\install_err.txt"
$tapped = $false
for ($i = 0; $i -lt 60 -and -not $proc.HasExited; $i++) {
    Start-Sleep -Milliseconds 700
    $focus = adb -s c49ac6cf shell "dumpsys window | grep mCurrentFocus"
    if ($focus -match 'AdbInstallActivity|PackageInstaller') {
        Start-Sleep -Milliseconds 500
        adb -s c49ac6cf shell "uiautomator dump /sdcard/ui_install.xml" | Out-Null
        adb -s c49ac6cf pull /sdcard/ui_install.xml "$PSScriptRoot\ui_install.xml" | Out-Null
        $xml = [System.IO.File]::ReadAllText("$PSScriptRoot\ui_install.xml", [System.Text.Encoding]::UTF8)
        # 勾选"拒绝后下次安装不再提示"复选框（若有），减少后续弹窗
        $cb = [regex]::Match($xml, 'class="android\.widget\.CheckBox"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
        if ($cb.Success) {
            $cx = ([int]$cb.Groups[1].Value + [int]$cb.Groups[3].Value) / 2
            $cy = ([int]$cb.Groups[2].Value + [int]$cb.Groups[4].Value) / 2
            Write-Host "TAP checkbox at $cx,$cy"
            adb -s c49ac6cf shell "input tap $([int]$cx) $([int]$cy)"
            Start-Sleep -Milliseconds 400
        }
        $m = [regex]::Matches($xml, 'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
        foreach ($node in $m) {
            $txt = $node.Groups[1].Value
            if ($txt -match '继续安装') {
                $x = ([int]$node.Groups[2].Value + [int]$node.Groups[4].Value) / 2
                $y = ([int]$node.Groups[3].Value + [int]$node.Groups[5].Value) / 2
                Write-Host "TAP button '$txt' at $x,$y"
                adb -s c49ac6cf shell "input tap $([int]$x) $([int]$y)"
                $tapped = $true
                break
            }
        }
        if (-not $tapped) { Write-Host "dialog visible but no confirm button found; dumping nodes:"; $m | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ } }
    }
}
$proc.WaitForExit()
Write-Host "exit=$($proc.ExitCode) tapped=$tapped"
Get-Content "$PSScriptRoot\install_out.txt" -ErrorAction SilentlyContinue
Get-Content "$PSScriptRoot\install_err.txt" -ErrorAction SilentlyContinue
