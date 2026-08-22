param(
    [int]$Minutes = 45,
    [string]$Serial = 'c49ac6cf'
)
# 监视 MIUI USB 安装确认弹窗并自动点击"继续安装"（复测期间临时使用）
$deadline = (Get-Date).AddMinutes($Minutes)
$count = 0
Write-Host "watcher started, deadline=$deadline"
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Milliseconds 800
    # MIUI 不同版本的安装确认 Activity 名称不稳定，不能依赖前台窗口类名。
    # 始终读取当前 UI 树，但只点击文本精确为“继续安装”的节点。
    adb -s $Serial shell "uiautomator dump /sdcard/ui_install.xml" | Out-Null
    $xml = (adb -s $Serial exec-out cat /sdcard/ui_install.xml) -join "`n"
    $m = [regex]::Matches($xml, 'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    foreach ($node in $m) {
        $txt = $node.Groups[1].Value
        if ($txt -eq '继续安装') {
            $x = ([int]$node.Groups[2].Value + [int]$node.Groups[4].Value) / 2
            $y = ([int]$node.Groups[3].Value + [int]$node.Groups[5].Value) / 2
            Write-Host "[$(Get-Date -f HH:mm:ss)] TAP confirm at $x,$y"
            adb -s $Serial shell "input tap $([int]$x) $([int]$y)" | Out-Null
            $count++
            break
        }
    }
}
Write-Host "watcher done, taps=$count"
