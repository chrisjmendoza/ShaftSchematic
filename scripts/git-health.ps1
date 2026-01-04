[CmdletBinding()]
param(
    [switch]$VerboseInfo
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Error 'git is not available on PATH. Install Git for Windows or run this inside a terminal/session that has git available.'
}

function Write-Section([string]$Title) {
    Write-Host "`n== $Title ==" -ForegroundColor Cyan
}

function Invoke-GitSafe([string[]]$GitArguments) {
    try {
        if (-not $GitArguments -or $GitArguments.Count -eq 0) {
            return $null
        }
        if ($VerboseInfo) {
            Write-Host ("git " + ($GitArguments -join ' ')) -ForegroundColor DarkGray
        }
        $out = & git @GitArguments 2>$null
        $text = ($out -join "`n").Trim()
        if ($text -match '^\s*(fatal|usage):') {
            return $null
        }
        return $text
    } catch {
        if ($VerboseInfo) {
            Write-Host "git call failed" -ForegroundColor DarkGray
        }
        return $null
    }
}

# Walk up parent directories to find a .git directory/file.
function Find-GitRoot([string]$StartDir) {
    try {
        $dir = (Resolve-Path $StartDir).Path
    } catch {
        return $null
    }

    while ($true) {
        if (Test-Path (Join-Path $dir '.git')) {
            return $dir
        }

        $parent = Split-Path -Parent $dir
        if (-not $parent -or $parent -eq $dir) {
            return $null
        }
        $dir = $parent
    }
}

# Resolve repo root robustly (don’t assume the child PowerShell starts in the repo)
$repoCandidate = $null
try {
    $repoCandidate = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
} catch {
    $repoCandidate = $PSScriptRoot
}

$top = Find-GitRoot (Get-Location)
if (-not $top) { $top = Find-GitRoot $repoCandidate }
if (-not $top) { $top = Invoke-GitSafe @('rev-parse', '--show-toplevel') }
if (-not $top) { $top = Invoke-GitSafe @('-C', $repoCandidate, 'rev-parse', '--show-toplevel') }
if (-not $top) {
    Write-Error 'Not inside a git repository.'
}

$gitBaseArguments = @('-C', $top)

function GitInRepo([string[]]$GitArguments) {
    return Invoke-GitSafe ($gitBaseArguments + $GitArguments)
}

Write-Section 'Repo'
Write-Host "Root: $top"

Write-Section 'Branch'
$statusShort = GitInRepo @('status', '-sb')
$statusLine = ($statusShort -split "`r?`n")[0]

if ($VerboseInfo) {
    Write-Host "Raw status -sb:"
    Write-Host $statusShort
    Write-Host "Parsed first line: $statusLine"
}

$branch = $null
$upstreamFromStatus = $null
if ($statusLine -match '^##\s+(?<rest>.*)$') {
    $rest = $Matches['rest'].Trim()

    # Typical forms:
    #   main...origin/main [ahead 1]
    #   feature/x.y...origin/feature/x.y
    #   HEAD (no branch)
    #   (initial)
    if ($rest -match '^HEAD\b') {
        $branch = '(detached HEAD)'
    } elseif ($rest -match '^(?<b>.+?)\.\.\.(?<u>\S+)(?:\s|$)') {
        $branch = $Matches['b']
        $upstreamFromStatus = $Matches['u']
    } elseif ($rest -match '^(?<b>\S+)(?:\s|$)') {
        $branch = $Matches['b']
    }
}

if (-not $branch) { $branch = '(unknown)' }
Write-Host "Branch: $branch"

$head = GitInRepo @('rev-parse', '--short', 'HEAD')
$subject = GitInRepo @('log', '-1', '--pretty=%s')
if ($head) {
    Write-Host "HEAD:   $head  $subject"
}

Write-Section 'Upstream'
$upstream = $upstreamFromStatus
if ($upstream) {
    Write-Host "Upstream: $upstream"
    $counts = GitInRepo @('rev-list', '--left-right', '--count', "HEAD...$upstream")
    if ($counts -and ($counts -match '^\s*(\d+)\s+(\d+)\s*$')) {
        $ahead = [int]$Matches[1]
        $behind = [int]$Matches[2]
        Write-Host "Ahead:    $ahead"
        Write-Host "Behind:   $behind"
    }
} else {
    Write-Host 'Upstream: (none)'
}

Write-Section 'Working Tree'
$status = GitInRepo @('status', '--porcelain')
if ([string]::IsNullOrWhiteSpace($status)) {
    Write-Host 'Status: clean'
} else {
    $lines = $status -split "`r?`n" | Where-Object { $_ -ne '' }
    Write-Host "Status: dirty ($($lines.Count) paths)"
    if ($VerboseInfo) {
        $lines | ForEach-Object { Write-Host "  $_" }
    }
}

Write-Section 'Operations'
$gitDir = GitInRepo @('rev-parse', '--git-dir')
if (-not $gitDir) { $gitDir = '.git' }

# `git rev-parse --git-dir` is often relative to the repo root; make it absolute so
# Test-Path works even when the script is run from elsewhere.
try {
    if (-not [System.IO.Path]::IsPathRooted($gitDir)) {
        $gitDir = Join-Path $top $gitDir
    }
    $gitDir = (Resolve-Path $gitDir).Path
} catch {
    # Keep the raw value; we'll simply fail to detect in-progress operations.
}

$flags = [System.Collections.Generic.List[string]]::new()

if (Test-Path (Join-Path $gitDir 'MERGE_HEAD')) { $flags.Add('MERGE IN PROGRESS') }
if (Test-Path (Join-Path $gitDir 'REBASE_HEAD')) { $flags.Add('REBASE IN PROGRESS') }
if (Test-Path (Join-Path $gitDir 'rebase-apply')) { $flags.Add('REBASE (apply) IN PROGRESS') }
if (Test-Path (Join-Path $gitDir 'rebase-merge')) { $flags.Add('REBASE (merge) IN PROGRESS') }
if (Test-Path (Join-Path $gitDir 'CHERRY_PICK_HEAD')) { $flags.Add('CHERRY-PICK IN PROGRESS') }
if (Test-Path (Join-Path $gitDir 'REVERT_HEAD')) { $flags.Add('REVERT IN PROGRESS') }
if (Test-Path (Join-Path $gitDir 'BISECT_LOG')) { $flags.Add('BISECT IN PROGRESS') }

if ($flags.Count -eq 0) {
    Write-Host 'None'
} else {
    $flags | ForEach-Object { Write-Host "- $_" -ForegroundColor Yellow }
}

Write-Host ""