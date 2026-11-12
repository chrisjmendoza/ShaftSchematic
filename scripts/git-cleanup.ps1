<#
.SYNOPSIS
  Prunes branches and worktrees whose work is already merged into main.

.DESCRIPTION
  Lists (and with -Apply, removes):
    1. extra worktree folders checked out on a branch fully merged into main,
    2. local branches fully merged into main,
    3. branches on origin fully merged into origin/main (only with -Remote).

  Without -Apply nothing is changed: the script prints what it would do.

  It never forces anything. A worktree with uncommitted or untracked files is left alone,
  `git branch -d` refuses a branch that is not merged, and main, the branch you are on and
  any branch still checked out in a kept worktree are skipped. Anything unmerged is listed
  at the end and never touched.

.EXAMPLE
  .\scripts\git-cleanup.ps1                 # dry run
  .\scripts\git-cleanup.ps1 -Apply          # prune merged worktrees + local branches
  .\scripts\git-cleanup.ps1 -Apply -Remote  # ...and merged branches on origin
#>
param(
    [switch]$Apply,
    [switch]$Remote,
    [string]$Main = 'main'
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Error 'git is not available on PATH.'
}

function Write-Section([string]$Title) {
    Write-Host "`n== $Title ==" -ForegroundColor Cyan
}

# git output as trimmed, non-empty lines; an empty array when the command prints nothing.
function Get-GitLines([string[]]$GitParams) {
    $out = & git $GitParams 2>$null
    if (-not $?) { return @() }
    return @($out | ForEach-Object { "$_".Trim() } | Where-Object { $_ })
}

$repoRoot = (Get-GitLines @('rev-parse', '--show-toplevel') | Select-Object -First 1)
if (-not $repoRoot) { Write-Error 'Not inside a git repository.' }
Set-Location $repoRoot

$mode = if ($Apply) { 'APPLY' } else { 'DRY RUN (nothing is changed; add -Apply to prune)' }
Write-Host "git-cleanup: $mode" -ForegroundColor Yellow

Write-Section 'Fetch'
& git fetch --prune --quiet
Write-Host 'origin fetched; stale remote-tracking refs pruned.'

$current = (Get-GitLines @('branch', '--show-current') | Select-Object -First 1)
$merged = Get-GitLines @('branch', '--merged', $Main, '--format=%(refname:short)') |
    Where-Object { $_ -ne $Main }
$unmerged = Get-GitLines @('branch', '--no-merged', $Main, '--format=%(refname:short)')

# --- 1. Worktrees on merged branches ------------------------------------------------------
Write-Section 'Worktrees'
$worktrees = @()
$entry = $null
foreach ($line in (Get-GitLines @('worktree', 'list', '--porcelain'))) {
    if ($line -like 'worktree *') {
        $entry = [pscustomobject]@{ Path = $line.Substring(9); Branch = $null }
        $worktrees += $entry
    } elseif ($line -like 'branch refs/heads/*' -and $entry) {
        $entry.Branch = $line.Substring(18)
    }
}
$mainTree = $worktrees | Select-Object -First 1
$keptBranches = @($mainTree.Branch)
foreach ($wt in ($worktrees | Select-Object -Skip 1)) {
    $isMerged = $wt.Branch -and ($merged -contains $wt.Branch)
    $dirty = @(& git -C $wt.Path status --porcelain 2>$null).Count -gt 0
    if (-not $isMerged) {
        Write-Host "keep    $($wt.Path)  [$($wt.Branch)] - not merged into $Main"
        $keptBranches += $wt.Branch
    } elseif ($dirty) {
        Write-Host "keep    $($wt.Path)  [$($wt.Branch)] - has uncommitted or untracked files" -ForegroundColor Yellow
        $keptBranches += $wt.Branch
    } elseif ($Apply) {
        & git worktree remove $wt.Path
        if ($?) { Write-Host "removed $($wt.Path)  [$($wt.Branch)]" -ForegroundColor Green }
        else { Write-Host "FAILED  $($wt.Path)" -ForegroundColor Red; $keptBranches += $wt.Branch }
    } else {
        Write-Host "would remove $($wt.Path)  [$($wt.Branch)]"
    }
}
if ($worktrees.Count -le 1) { Write-Host 'No extra worktrees.' }
if ($Apply) { & git worktree prune }

# --- 2. Local branches merged into main ---------------------------------------------------
Write-Section "Local branches merged into $Main"
$count = 0
foreach ($b in $merged) {
    if ($b -eq $current) { Write-Host "skip    $b - you are on it"; continue }
    if ($keptBranches -contains $b) { Write-Host "skip    $b - checked out in a kept worktree"; continue }
    $count++
    if ($Apply) {
        & git branch -d $b | Out-Null
        if ($?) { Write-Host "deleted $b" -ForegroundColor Green } else { Write-Host "FAILED  $b" -ForegroundColor Red }
    } else {
        Write-Host "would delete $b"
    }
}
if ($count -eq 0) { Write-Host 'None.' }

# --- 3. Branches on origin merged into origin/main ----------------------------------------
Write-Section "Branches on origin merged into origin/$Main"
$remoteMerged = Get-GitLines @('branch', '-r', '--merged', "origin/$Main", '--format=%(refname:short)') |
    Where-Object { $_ -like 'origin/*' -and $_ -ne "origin/$Main" -and $_ -ne 'origin/HEAD' -and $_ -ne 'origin' } |
    ForEach-Object { $_.Substring(7) }
if (-not $remoteMerged) {
    Write-Host 'None.'
} elseif (-not $Remote) {
    Write-Host "$(@($remoteMerged).Count) merged branch(es) on origin; add -Remote to include them:"
    $remoteMerged | ForEach-Object { Write-Host "  $_" }
} else {
    foreach ($b in $remoteMerged) {
        if ($Apply) {
            & git push origin --delete $b --quiet
            if ($?) { Write-Host "deleted origin/$b" -ForegroundColor Green } else { Write-Host "FAILED  origin/$b" -ForegroundColor Red }
        } else {
            Write-Host "would delete origin/$b"
        }
    }
}

# --- Unmerged work: reported, never touched -----------------------------------------------
Write-Section "Not merged into $Main (left alone)"
if ($unmerged) { $unmerged | ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow } } else { Write-Host 'None.' }

$stashes = @(Get-GitLines @('stash', 'list'))
if ($stashes.Count -gt 0) {
    Write-Section 'Stashes (left alone)'
    $stashes | ForEach-Object { Write-Host "  $_" }
}
