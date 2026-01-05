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

function Invoke-GitSafe([string[]]$GitParams) {
    if (-not $GitParams -or $GitParams.Count -eq 0) {
        return $null
    }

    if ($VerboseInfo) {
        Write-Host ("git " + ($GitParams -join ' ')) -ForegroundColor DarkGray
    }

    # Passing a string[] to an external command expands to individual args in Windows PowerShell.
    $out = & git $GitParams 2>$null
    if (-not $?) {
        return $null
    }

    $text = ($out -join "`n").Trim()
    if ($text -match '^\s*(fatal|usage):') {
        return $null
    }
    return $text
}

# Walk up parent directories to find a .git directory/file.
function Get-GitRoot([string]$StartDir) {
    $resolved = Resolve-Path $StartDir -ErrorAction SilentlyContinue
    if (-not $resolved) {
        return $null
    }
    $dir = $resolved.Path

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
$repoCandidateResolved = Resolve-Path (Join-Path $PSScriptRoot '..') -ErrorAction SilentlyContinue
$repoCandidate = if ($repoCandidateResolved) { $repoCandidateResolved.Path } else { $PSScriptRoot }

$top = Get-GitRoot (Get-Location)
if (-not $top) { $top = Get-GitRoot $repoCandidate }
$revParseTopParams = 'rev-parse', '--show-toplevel'
$revParseTopFromCandidateParams = '-C', $repoCandidate, 'rev-parse', '--show-toplevel'
if (-not $top) { $top = Invoke-GitSafe -GitParams $revParseTopParams }
if (-not $top) { $top = Invoke-GitSafe -GitParams $revParseTopFromCandidateParams }
if (-not $top) {
    Write-Error 'Not inside a git repository.'
}

$gitBaseParams = '-C', $top

function GitInRepo([string[]]$GitParams) {
    return Invoke-GitSafe ($gitBaseParams + $GitParams)
}

Write-Section 'Repo'
Write-Host "Root: $top"

Write-Section 'Branch'
$statusShort = GitInRepo -GitParams 'status', '-sb'
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

$head = GitInRepo -GitParams 'rev-parse', '--short', 'HEAD'
$subject = GitInRepo -GitParams 'log', '-1', '--pretty=%s'
if ($head) {
    Write-Host "HEAD:   $head  $subject"
}

Write-Section 'Upstream'
$upstream = $upstreamFromStatus
if ($upstream) {
    Write-Host "Upstream: $upstream"
    $counts = GitInRepo -GitParams 'rev-list', '--left-right', '--count', "HEAD...$upstream"
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
$status = GitInRepo -GitParams 'status', '--porcelain'
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
$gitDir = GitInRepo -GitParams 'rev-parse', '--git-dir'
if (-not $gitDir) { $gitDir = '.git' }

# `git rev-parse --git-dir` is often relative to the repo root; make it absolute so
# Test-Path works even when the script is run from elsewhere.
if (-not [System.IO.Path]::IsPathRooted($gitDir)) {
    $gitDir = Join-Path $top $gitDir
}
$resolvedGitDir = Resolve-Path $gitDir -ErrorAction SilentlyContinue
if ($resolvedGitDir) {
    $gitDir = $resolvedGitDir.Path
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

Write-Section 'GPG (commit signing)'
$gpgSign = GitInRepo -GitParams 'config', '--get', 'commit.gpgsign'
if ($gpgSign -eq 'true') {
    Write-Host 'Commit signing: enabled'
    Write-Host 'If git commit fails with GPG/keybox errors, reboot the services and retry:'
    Write-Host '  gpgconf --kill gpg-agent ; gpgconf --kill keyboxd ; gpgconf --kill dirmngr'
    Write-Host '  gpgconf --launch gpg-agent ; gpgconf --launch keyboxd'
} elseif ([string]::IsNullOrWhiteSpace($gpgSign)) {
    Write-Host 'Commit signing: (not set in repo config)'
    Write-Host 'Tip: If your global config enables signing and gpg acts up, reboot services:'
    Write-Host '  gpgconf --kill gpg-agent ; gpgconf --kill keyboxd ; gpgconf --kill dirmngr'
    Write-Host '  gpgconf --launch gpg-agent ; gpgconf --launch keyboxd'
} else {
    Write-Host "Commit signing: $gpgSign"
}