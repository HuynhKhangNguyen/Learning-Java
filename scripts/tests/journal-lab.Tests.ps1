#requires -Version 7.4
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runner = Join-Path $repo 'scripts/services.ps1'

function Invoke-Case {
  param([string[]]$Arguments)
  $info = [Diagnostics.ProcessStartInfo]::new()
  $info.FileName = (Get-Command pwsh).Source
  $info.WorkingDirectory = $repo
  $info.RedirectStandardOutput = $true
  $info.RedirectStandardError = $true
  foreach ($argument in @('-NoProfile', '-File', $runner) + $Arguments) {
    $info.ArgumentList.Add($argument)
  }
  $process = [Diagnostics.Process]::Start($info)
  $stdout = $process.StandardOutput.ReadToEndAsync()
  $stderr = $process.StandardError.ReadToEndAsync()
  if (-not $process.WaitForExit(30000)) {
    $process.Kill($true)
    throw 'Journal helper case exceeded 30 seconds.'
  }
  return @{ ExitCode=$process.ExitCode; Text=$stdout.Result + $stderr.Result }
}

function Assert-Report {
  param([pscustomobject]$Report)
  if ($Report.task -ne 'R28a' -or $Report.profile -ne 'lab' -or
      $Report.status -ne 'PASS' -or $Report.exitCode -ne 0 -or
      $Report.productionQualified -ne $false) {
    throw 'Malformed CMD-06 report header is not PASS.'
  }
  if ($Report.inputManifest.Count -eq 0 -or $Report.inputManifestDigest -notmatch '^[0-9a-f]{64}$') {
    throw 'Missing/stale input manifest is not PASS.'
  }
  foreach ($service in @('identity', 'learning')) {
    $outcome = @($Report.services | Where-Object service -EQ $service)
    if ($outcome.Count -ne 1 -or $outcome[0].tests -lt 3 -or
        $outcome[0].failures -ne 0 -or $outcome[0].skipped -ne 0 -or
        $outcome[0].artifactHash -notmatch '^[0-9a-f]{64}$' -or
        $outcome[0].reportHash -notmatch '^[0-9a-f]{64}$') {
      throw "Required $service suite/hash gate is not PASS."
    }
  }
  if (-not $Report.cleanup.childProcessesExited -or
      -not $Report.cleanup.rootsOwnedByTempFixtures -or $Report.limitations.Count -eq 0) {
    throw 'Cleanup/limitation gate is not PASS.'
  }
}

$latest = Get-ChildItem (Join-Path $repo 'artifacts/quality/R28a') -Filter report.json -Recurse |
  Where-Object { (Get-Content $_.FullName -Raw | ConvertFrom-Json).profile -eq 'lab' } |
  Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $latest) { throw 'Current CMD-06 lab report is missing.' }
$report = Get-Content $latest.FullName -Raw | ConvertFrom-Json
Assert-Report $report

$malformed = $report.PSObject.Copy()
$malformed.status = 'PASS'
$malformed.services = @()
try { Assert-Report $malformed; throw 'Malformed zero-suite report was accepted.' }
catch { if ($_.Exception.Message -eq 'Malformed zero-suite report was accepted.') { throw } }

$qualification = Invoke-Case @('-Task','journal','-Profile','qualification','-Offline')
if ($qualification.ExitCode -eq 0 -or $qualification.Text -notmatch 'NOT_QUALIFIED') {
  throw 'Qualification profile did not fail closed.'
}
$unknown = Invoke-Case @('-Task','journal','-Profile','unknown','-Offline')
if ($unknown.ExitCode -eq 0 -or $unknown.Text -notmatch 'ValidateSet') {
  throw 'Unknown journal profile was accepted.'
}
Write-Host "R28a helper gates PASS: report=$($latest.FullName); qualification/unknown fail closed."
