param(
    [string]$Apk = 'd:\develop\Code\Codex\creation-reading-assistant\android\app\build\outputs\apk\benchmarkRelease\app-benchmarkRelease.apk',
    [string]$Serial = 'c49ac6cf'
)

$outFile = Join-Path ([System.IO.Path]::GetTempPath()) "cra-install-$([guid]::NewGuid())-out.txt"
$errFile = Join-Path ([System.IO.Path]::GetTempPath()) "cra-install-$([guid]::NewGuid())-err.txt"
$proc = Start-Process -FilePath 'adb' -ArgumentList @('-s', $Serial, 'install', '-r', '-t', $Apk) `
    -WindowStyle Hidden -PassThru -RedirectStandardOutput $outFile -RedirectStandardError $errFile
$tapped = $false

try {
    for ($i = 0; $i -lt 60 -and -not $proc.HasExited; $i++) {
        Start-Sleep -Milliseconds 700
        adb -s $Serial shell "uiautomator dump /sdcard/ui_install.xml" | Out-Null
        $xml = (adb -s $Serial exec-out cat /sdcard/ui_install.xml) -join "`n"
        $nodes = [regex]::Matches($xml, 'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
        foreach ($node in $nodes) {
            if ($node.Groups[1].Value -ne '继续安装') { continue }
            $x = ([int]$node.Groups[2].Value + [int]$node.Groups[4].Value) / 2
            $y = ([int]$node.Groups[3].Value + [int]$node.Groups[5].Value) / 2
            Write-Host "TAP button '继续安装' at $x,$y"
            adb -s $Serial shell "input tap $([int]$x) $([int]$y)" | Out-Null
            $tapped = $true
            break
        }
    }
    $proc.WaitForExit()
    $exitCode = $proc.ExitCode
    Write-Host "exit=$exitCode tapped=$tapped"
    Get-Content -LiteralPath $outFile -ErrorAction SilentlyContinue
    Get-Content -LiteralPath $errFile -ErrorAction SilentlyContinue
} finally {
    Remove-Item -LiteralPath $outFile, $errFile -Force -ErrorAction SilentlyContinue
}

exit $exitCode
