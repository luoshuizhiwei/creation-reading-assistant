param(
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,
    [long]$TargetBytes = 6000000,
    [int]$ParagraphsPerChapter = 240
)

$ErrorActionPreference = 'Stop'

if ($TargetBytes -le 5000000) {
    throw 'TargetBytes must exceed the app streaming threshold (5,000,000 bytes).'
}
if ($ParagraphsPerChapter -le 0) {
    throw 'ParagraphsPerChapter must be positive.'
}

$resolvedParent = Split-Path -Parent $OutputPath
if ([string]::IsNullOrWhiteSpace($resolvedParent)) {
    $resolvedParent = (Get-Location).Path
}
[void](New-Item -ItemType Directory -Force -Path $resolvedParent)

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$stream = [System.IO.File]::Open($OutputPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
$writer = New-Object System.IO.StreamWriter($stream, $utf8NoBom)

try {
    $chapter = 1
    while ($stream.Position -lt $TargetBytes) {
        $writer.WriteLine("第 $chapter 章 流式分页回归")
        for ($paragraph = 1; $paragraph -le $ParagraphsPerChapter; $paragraph++) {
            $writer.WriteLine(
                "这是测试 TXT 的中性正文段落 $chapter-$paragraph。用于验证大文件章节识别、分页、搜索、替换净化、快速翻页与进度恢复；内容不对应任何真实作品。"
            )
            if (($paragraph % 240) -eq 0) {
                $writer.Flush()
                if ($stream.Position -ge $TargetBytes) { break }
            }
        }
        $writer.Flush()
        $chapter++
    }
} finally {
    $writer.Dispose()
    $stream.Dispose()
}

$file = Get-Item -LiteralPath $OutputPath
Write-Host "Generated neutral TXT fixture: $($file.FullName) ($($file.Length) bytes)"
