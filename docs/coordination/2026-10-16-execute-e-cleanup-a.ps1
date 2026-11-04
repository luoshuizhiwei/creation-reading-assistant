$ErrorActionPreference = 'Stop'
$projectRoot = 'E:\creation-reading-assistant'
$proposalPath = 'D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\coordination\2026-10-16-e-cleanup-batch-a.json'
$receiptPath = 'D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\coordination\2026-10-16-e-cleanup-a-result.json'
$approvedTargets = @(
    'E:\creation-reading-assistant\d2-relink-identity-fix-v4\_cache\gradle-home\caches',
    'E:\creation-reading-assistant\d2-relink-identity-fix-v3\_cache\gradle-home\caches',
    'E:\creation-reading-assistant\d2-relink-identity-fix-v2\_cache\gradle-home\caches',
    'E:\creation-reading-assistant\d2-relink-identity-fix-v1\_cache\gradle-home\caches',
    'E:\creation-reading-assistant\j1-crossbook-temporary-seam-v3\gradle-home\caches',
    'E:\creation-reading-assistant\j1-crossbook-temporary-entry-v1\android\gradle-home\caches',
    'E:\creation-reading-assistant\txt-reentry-fix-v2\gradle-home\caches',
    'E:\creation-reading-assistant\txt-reentry-fix-v3\gradle-home\caches',
    'E:\creation-reading-assistant\txt-reentry-fix-v4\gradle-home\caches',
    'E:\creation-reading-assistant\txt-reentry-persist-fix-v1\gradle-home\caches'
)
$proposal = Get-Content -LiteralPath $proposalPath -Raw | ConvertFrom-Json
if (@($proposal.items).Count -ne 10) { throw 'Proposal count mismatch' }
foreach ($entry in $proposal.items) {
    if ($approvedTargets -notcontains [string]$entry.path) { throw 'Unexpected proposal target' }
}
if ([IO.Path]::GetFullPath((Resolve-Path -LiteralPath $projectRoot).ProviderPath).TrimEnd('\') -ne $projectRoot) {
    throw 'Project root identity mismatch'
}
$liveProcesses = @(Get-CimInstance Win32_Process)
$liveProcessIds = @($liveProcesses | ForEach-Object { [int]$_.ProcessId })
$beforeDrive = Get-PSDrive -Name E
$results = [Collections.Generic.List[object]]::new()
$receipt = [ordered]@{
    schema = 'cra-space-cleanup-execution/1'
    authorization = '2026-10-16 user requested cleanup after the exact batch A proposal; only these ten caches subdirectories'
    proposal_sha256 = (Get-FileHash -LiteralPath $proposalPath -Algorithm SHA256).Hash.ToLowerInvariant()
    started_utc = [DateTime]::UtcNow.ToString('o')
    before_free_bytes = [long]$beforeDrive.Free
    before_used_bytes = [long]$beforeDrive.Used
    results = @()
    stopped_processes = @()
    status = 'RUNNING'
}
function Save-Receipt {
    $receipt.results = @($results.ToArray())
    $receipt | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $receiptPath -Encoding utf8
}
Save-Receipt
foreach ($target in $approvedTargets) {
    $itemResult = [ordered]@{ path = $target; status = 'PRECHECK'; error = $null }
    $results.Add($itemResult)
    try {
        if (-not (Test-Path -LiteralPath $target)) {
            $itemResult.status = 'ALREADY_ABSENT'; Save-Receipt; continue
        }
        $resolvedTarget = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $target).ProviderPath).TrimEnd('\')
        if ($resolvedTarget -ne $target -or -not $resolvedTarget.StartsWith($projectRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Resolved target differs from approved path or escapes project root'
        }
        if ([IO.Path]::GetFileName($resolvedTarget) -ne 'caches' -or [IO.Path]::GetFileName([IO.Path]::GetDirectoryName($resolvedTarget)) -ne 'gradle-home') {
            throw 'Target is not the exact approved caches child'
        }
        $ancestor = Get-Item -LiteralPath $resolvedTarget -Force
        while ($null -ne $ancestor) {
            if (($ancestor.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Reparse point in target ancestry' }
            $ancestor = $ancestor.Parent
        }
        $homePath = [IO.Path]::GetDirectoryName($resolvedTarget)
        $liveProcesses = @(Get-CimInstance Win32_Process)
        $liveProcessIds = @($liveProcesses | Where-Object { $_.Name -match '^java(w)?\.exe$' } | ForEach-Object { [int]$_.ProcessId })
        $relatedProcesses = @($liveProcesses | Where-Object {
            $_.CommandLine -and $_.CommandLine.IndexOf($homePath, [StringComparison]::OrdinalIgnoreCase) -ge 0
        })
        if ($relatedProcesses.Count -gt 0) { throw 'Active process references this private Gradle home; skip without stopping it' }
        $daemonPath = Join-Path $homePath 'daemon'
        if (Test-Path -LiteralPath $daemonPath) {
            $activeDaemonLogs = @(Get-ChildItem -LiteralPath $daemonPath -File -Recurse -Filter 'daemon-*.out.log' | Where-Object {
                $_.Name -match '^daemon-(\d+)\.out\.log$' -and $liveProcessIds -contains [int]$Matches[1]
            })
            if ($activeDaemonLogs.Count -gt 0) { throw 'Private home daemon PID remains alive; skip without stopping it' }
        }
        $reparseChildren = @(Get-ChildItem -LiteralPath $resolvedTarget -Recurse -Force -Attributes ReparsePoint)
        if ($reparseChildren.Count -gt 0) { throw 'Reparse point inside target; skip entire target' }
        $lockFiles = @(Get-ChildItem -LiteralPath $resolvedTarget -Recurse -Force -File -Filter '*.lock')
        foreach ($lockFile in $lockFiles) {
            $stream = $null
            try {
                $stream = [IO.File]::Open($lockFile.FullName, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::None)
            } finally {
                if ($null -ne $stream) { $stream.Dispose() }
            }
        }
        $preservedBefore = @(Get-ChildItem -LiteralPath $homePath -Force | Where-Object { $_.Name -ne 'caches' } | ForEach-Object { $_.Name } | Sort-Object)
        $propsPath = Join-Path $homePath 'gradle.properties'
        $propsBefore = if (Test-Path -LiteralPath $propsPath -PathType Leaf) { (Get-FileHash -LiteralPath $propsPath -Algorithm SHA256).Hash } else { $null }
        $itemResult.resolved_absolute_target = $resolvedTarget
        $itemResult.checked_lock_files = $lockFiles.Count
        $itemResult.preserved_siblings_before = $preservedBefore
        $itemResult.status = 'DELETING'
        Save-Receipt
        Write-Output ('Deleting approved old cache only: ' + $resolvedTarget)
        Remove-Item -LiteralPath $resolvedTarget -Recurse -Force -ErrorAction Stop
        if (Test-Path -LiteralPath $resolvedTarget) { throw 'Target still exists after deletion' }
        $preservedAfter = @(Get-ChildItem -LiteralPath $homePath -Force | ForEach-Object { $_.Name } | Sort-Object)
        if (@(Compare-Object $preservedBefore $preservedAfter).Count -gt 0) { throw 'Sibling inventory changed unexpectedly' }
        if ($propsBefore -and (Get-FileHash -LiteralPath $propsPath -Algorithm SHA256).Hash -ne $propsBefore) { throw 'Sibling gradle.properties changed unexpectedly' }
        $itemResult.status = 'DELETED_VERIFIED'
        $itemResult.target_absent = $true
        $itemResult.preserved_siblings_match = $true
        Write-Output ('Deleted and verified: ' + $resolvedTarget)
    } catch {
        $itemResult.error = $_.Exception.Message
        $itemResult.error_position = $_.InvocationInfo.PositionMessage
        $itemResult.status = if ($itemResult.status -eq 'DELETING') { 'DELETE_PARTIAL_OR_FAILED' } else { 'SKIPPED_PRECHECK' }
        Write-Output ('Skipped or stopped this target: ' + $target + ' | ' + $itemResult.error)
    }
    Save-Receipt
}
$afterDrive = Get-PSDrive -Name E
$receipt.after_free_bytes = [long]$afterDrive.Free
$receipt.after_used_bytes = [long]$afterDrive.Used
$receipt.observed_free_increase_bytes = [long]$afterDrive.Free - [long]$receipt.before_free_bytes
$receipt.finished_utc = [DateTime]::UtcNow.ToString('o')
$receipt.status = if (@($results | Where-Object { $_.status -notin @('DELETED_VERIFIED', 'ALREADY_ABSENT') }).Count -eq 0) { 'COMPLETED' } else { 'COMPLETED_WITH_SKIPS_OR_FAILURES' }
Save-Receipt
Write-Output ($receipt | ConvertTo-Json -Depth 4)
