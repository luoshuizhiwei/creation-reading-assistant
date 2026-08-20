param([int]$Minutes = 45)
# 监视 MIUI USB 安装确认弹窗并自动点击"继续安装"（复测期间临时使用）
$deadline = (Get-Date).AddMinutes($Minutes)
$count = 0
Write-Host "watcher started, deadline=$deadline"
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Milliseconds 800
    $focus = adb -s c49ac6cf shell "dumpsys window | grep mCurrentFocus"
    if ($focus -match 'AdbInstallActivity|PackageInstaller') {
        Start-Sleep -Milliseconds 600
        adb -s c49ac6cf shell "uiautomator dump /sdcard/ui_install.xml" | Out-Null
        adb -s c49ac6cf pull /sdcard/ui_install.xml "$PSScriptRoot\ui_install.xml" | Out-Null
        $xml = [System.IO.File]::ReadAllText("$PSScriptRoot\ui_install.xml", [System.Text.Encoding]::UTF8)
        $m = [regex]::Matches($xml, 'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
        foreach ($node in $m) {
            $txt = $node.Groups[1].Value
            if ($txt -match '继续安装') {
                $x = ([int]$node.Groups[2].Value + [int]$node.Groups[4].Value) / 2
                $y = ([int]$node.Groups[3].Value + [int]$node.Groups[5].Value) / 2
                Write-Host "[$(Get-Date -f HH:mm:ss)] TAP confirm at $x,$y"
                adb -s c49ac6cf shell "input tap $([int]$x) $([int]$y)"
                $count++
                break
            }
        }
    }
}
Write-Host "watcher done, taps=$count"
