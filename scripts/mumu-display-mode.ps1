[CmdletBinding()]
param(
    [ValidateSet("Original", "Mobile", "Status", "Reset")]
    [string]$Mode = "Status",

    [string]$Serial = "127.0.0.1:16416",

    [string]$AdbPath,

    [int]$VmIndex = 1,

    [string]$MuMuManagerPath
)

$ErrorActionPreference = "Stop"

function Resolve-AdbPath {
    param([string]$ExplicitPath)

    if ($ExplicitPath) {
        if (-not (Test-Path -LiteralPath $ExplicitPath -PathType Leaf)) {
            throw "The specified adb executable does not exist: $ExplicitPath"
        }
        return (Resolve-Path -LiteralPath $ExplicitPath).Path
    }

    $command = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $repoRoot = Split-Path -Parent $PSScriptRoot
    $localProperties = Join-Path $repoRoot "mobile\android\local.properties"
    $sdkFromProject = $null

    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties |
            Where-Object { $_ -match '^sdk\.dir=' } |
            Select-Object -First 1

        if ($sdkLine) {
            $sdkFromProject = $sdkLine.Substring("sdk.dir=".Length).Trim()
            $sdkFromProject = $sdkFromProject -replace '\\:', ':'
            $sdkFromProject = $sdkFromProject -replace '\\\\', '\'
        }
    }

    $candidates = @(
        $(if ($sdkFromProject) { Join-Path $sdkFromProject "platform-tools\adb.exe" }),
        $(if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME "platform-tools\adb.exe" }),
        $(if ($env:ANDROID_SDK_ROOT) { Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe" }),
        $(Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"),
        "D:\develop\Android\Sdk\platform-tools\adb.exe"
    ) | Where-Object { $_ }

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw "adb.exe was not found. Install Android platform-tools or pass -AdbPath."
}

function Resolve-MuMuManagerPath {
    param([string]$ExplicitPath)

    if ($ExplicitPath) {
        if (-not (Test-Path -LiteralPath $ExplicitPath -PathType Leaf)) {
            throw "The specified MuMuManager executable does not exist: $ExplicitPath"
        }
        return (Resolve-Path -LiteralPath $ExplicitPath).Path
    }

    $runningMuMu = Get-Process MuMuNxMain -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($runningMuMu -and $runningMuMu.Path) {
        $besideMain = Join-Path (Split-Path -Parent $runningMuMu.Path) "MuMuManager.exe"
        if (Test-Path -LiteralPath $besideMain -PathType Leaf) {
            return (Resolve-Path -LiteralPath $besideMain).Path
        }
    }

    $candidates = @(
        "D:\Application\NetEase\MuMuPlayer\nx_main\MuMuManager.exe",
        "C:\Program Files\NetEase\MuMuPlayer\nx_main\MuMuManager.exe",
        "D:\Program Files\NetEase\MuMuPlayer\nx_main\MuMuManager.exe"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw "MuMuManager.exe was not found. Pass -MuMuManagerPath explicitly."
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments,
        [switch]$AllowFailure
    )

    # Windows PowerShell 5 turns native stderr into ErrorRecord objects when
    # ErrorActionPreference is Stop. MuMu normally emits "offline/closed"
    # during restart, so capture it and let the caller decide whether to retry.
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $script:ResolvedAdb @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }

    if (($exitCode -ne 0) -and -not $AllowFailure) {
        throw "adb failed with exit code ${exitCode}: $($output -join [Environment]::NewLine)"
    }

    return $output
}

function Invoke-MuMuManager {
    param([Parameter(Mandatory)][string[]]$Arguments)

    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $script:ResolvedManager @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) {
        throw "MuMuManager failed with exit code ${exitCode}: $($output -join [Environment]::NewLine)"
    }
    return $output
}

function Connect-MuMu {
    param([int]$TimeoutSeconds = 10)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        if ($Serial -match ':') {
            Invoke-Adb -Arguments @("connect", $Serial) -AllowFailure | Out-Null
        }

        $state = (Invoke-Adb -Arguments @("-s", $Serial, "get-state") -AllowFailure) -join ""
        if ($state.Trim() -eq "device") {
            $probe = (Invoke-Adb -Arguments @("-s", $Serial, "shell", "echo", "mumu-ready") -AllowFailure) -join ""
            if ($probe.Trim() -eq "mumu-ready") {
                return
            }
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)

    throw "MuMu is not connected at $Serial. Start MuMu and enable ADB debugging."
}

function Get-MuMuDisplaySettings {
    $raw = Invoke-MuMuManager -Arguments @(
        "setting", "-v", "$VmIndex",
        "-k", "resolution_mode",
        "-k", "resolution_width",
        "-k", "resolution_height",
        "-k", "resolution_dpi",
        "-k", "resolution_width.custom",
        "-k", "resolution_height.custom",
        "-k", "resolution_dpi.custom"
    )
    return (($raw -join [Environment]::NewLine) | ConvertFrom-Json)
}

function Set-MuMuDisplayProfile {
    param(
        [Parameter(Mandatory)][int]$Width,
        [Parameter(Mandatory)][int]$Height,
        [Parameter(Mandatory)][int]$Density
    )

    Invoke-MuMuManager -Arguments @(
        "setting", "-v", "$VmIndex",
        "-k", "resolution_mode", "-val", "custom",
        "-k", "resolution_width.custom", "-val", "$Width",
        "-k", "resolution_height.custom", "-val", "$Height",
        "-k", "resolution_dpi.custom", "-val", "$Density"
    ) | Out-Null

    Invoke-MuMuManager -Arguments @("control", "-v", "$VmIndex", "restart") | Out-Null
    # Drop the old transport first; otherwise adb may briefly report the
    # shutting-down Android instance as "device" and fail on the next command.
    Invoke-Adb -Arguments @("disconnect", $Serial) -AllowFailure | Out-Null
    Start-Sleep -Seconds 3
    Connect-MuMu -TimeoutSeconds 45

    # Clear stale Android overrides. MuMuManager is now the source of truth.
    Invoke-Adb -Arguments @("-s", $Serial, "shell", "wm", "size", "reset") | Out-Null
    Invoke-Adb -Arguments @("-s", $Serial, "shell", "wm", "density", "reset") | Out-Null
}

function Set-RotationMode {
    param([bool]$AutoRotate)

    $autoValue = if ($AutoRotate) { "1" } else { "0" }
    Invoke-Adb -Arguments @("-s", $Serial, "shell", "settings", "put", "system", "accelerometer_rotation", $autoValue) | Out-Null
    Invoke-Adb -Arguments @("-s", $Serial, "shell", "settings", "put", "system", "user_rotation", "0") | Out-Null
    if (-not $AutoRotate) {
        Start-Sleep -Milliseconds 500
    }
}

function Get-DisplayStatus {
    $managerSettings = Get-MuMuDisplaySettings
    $size = Invoke-Adb -Arguments @("-s", $Serial, "shell", "wm", "size")
    $density = Invoke-Adb -Arguments @("-s", $Serial, "shell", "wm", "density")
    $autoRotate = (Invoke-Adb -Arguments @("-s", $Serial, "shell", "settings", "get", "system", "accelerometer_rotation")) -join ""
    $userRotation = (Invoke-Adb -Arguments @("-s", $Serial, "shell", "settings", "get", "system", "user_rotation")) -join ""

    $managerWidth = [int][double]$managerSettings.resolution_width
    $managerHeight = [int][double]$managerSettings.resolution_height
    $managerDensity = [int][double]$managerSettings.resolution_dpi
    $profile = "Custom/unknown"

    if (($managerWidth -eq 1920) -and ($managerHeight -eq 1080) -and ($managerDensity -eq 280)) {
        $profile = "Original (MuMu desktop profile)"
    }
    elseif (($managerWidth -eq 1080) -and ($managerHeight -eq 1920) -and ($managerDensity -eq 480)) {
        $profile = "Mobile (portrait test profile)"
    }

    Write-Host ""
    Write-Host "Device: $Serial" -ForegroundColor Cyan
    Write-Host "Profile: $profile" -ForegroundColor Cyan
    Write-Host "MuMu instance: ${managerWidth}x${managerHeight} @ ${managerDensity} DPI (VM $VmIndex)"
    $size | ForEach-Object { Write-Host $_ }
    $density | ForEach-Object { Write-Host $_ }
    Write-Host "Auto rotation: $($autoRotate.Trim()) (1=enabled, 0=disabled)"
    Write-Host "User rotation: $($userRotation.Trim()) (0=portrait, 1=landscape-left, 3=landscape-right)"
}

$script:ResolvedAdb = Resolve-AdbPath -ExplicitPath $AdbPath
$script:ResolvedManager = Resolve-MuMuManagerPath -ExplicitPath $MuMuManagerPath
Connect-MuMu -TimeoutSeconds 10

switch ($Mode) {
    "Original" {
        # Change the MuMu instance itself. ADB-only overrides cause letterboxing.
        Set-MuMuDisplayProfile -Width 1920 -Height 1080 -Density 280
        Set-RotationMode -AutoRotate $true
        Start-Sleep -Seconds 1
        Write-Host "Switched to the original MuMu desktop profile." -ForegroundColor Green
    }
    "Mobile" {
        # Portrait profile used to approximate a physical phone during UI testing.
        Set-MuMuDisplayProfile -Width 1080 -Height 1920 -Density 480
        Set-RotationMode -AutoRotate $false
        Start-Sleep -Seconds 1
        Write-Host "Switched to the portrait mobile test profile." -ForegroundColor Green
    }
    "Reset" {
        # Clear stale ADB overrides without changing the MuMu instance profile.
        Invoke-Adb -Arguments @("-s", $Serial, "shell", "wm", "size", "reset") | Out-Null
        Invoke-Adb -Arguments @("-s", $Serial, "shell", "wm", "density", "reset") | Out-Null
        Set-RotationMode -AutoRotate $true
        Start-Sleep -Milliseconds 800
        Write-Host "Cleared stale Android display overrides." -ForegroundColor Yellow
    }
    "Status" {
        # Read-only mode.
    }
}

Get-DisplayStatus
