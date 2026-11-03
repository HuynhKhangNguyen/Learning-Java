#requires -Version 7.4
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runner = Join-Path $repo 'scripts/services.ps1'
$requiredCases = @(
  'immutableIdempotentSnapshotAndCorruptionFailClosed',
  'concurrentProcessesExerciseCasLoserRetryWithoutLostRecord',
  'processCrashBoundariesRemainUnacknowledgedThenConverge',
  'missingRecordAndNodeFailClosedAsTypedCorruption',
  'malformedSequenceNamespaceAndPredecessorFailClosed',
  'forgedHeadAndGapFailClosed',
  'traversalCollisionAndReparseEscapeAreRejected',
  'orphanRecordAndNodeStayOutsideVerifiedPrefix'
)

function Get-Sha256 {
  param([Parameter(Mandatory)][string]$Path)

  return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-ExpectedInputPaths {
  $journalDirectories = @(
    'backend/services/identity-service/src/main/java/lab/backend/identity/journal',
    'backend/services/identity-service/src/test/java/lab/backend/identity/journal',
    'backend/services/learning-service/src/main/java/lab/backend/learning/journal',
    'backend/services/learning-service/src/test/java/lab/backend/learning/journal'
  )
  $files = foreach ($directory in $journalDirectories) {
    Get-ChildItem -LiteralPath (Join-Path $repo $directory) -File -Filter '*.java'
  }
  $files += @(
    Get-Item -LiteralPath (Join-Path $repo 'backend/services/identity-service/pom.xml')
    Get-Item -LiteralPath (Join-Path $repo 'backend/services/learning-service/pom.xml')
    Get-Item -LiteralPath (Join-Path $repo 'contracts/journal/README.md')
    Get-Item -LiteralPath (Join-Path $repo 'ops/journal/README.md')
    Get-Item -LiteralPath (Join-Path $repo 'scripts/services.ps1')
    Get-Item -LiteralPath (Join-Path $repo 'scripts/journal-lab.ps1')
    Get-Item -LiteralPath (Join-Path $repo 'scripts/tests/journal-lab.Tests.ps1')
  )
  return @($files | Sort-Object FullName -Unique | ForEach-Object {
    $_.FullName.Substring($repo.Length + 1).Replace('\', '/')
  })
}

function Get-ManifestDigest {
  param([Parameter(Mandatory)][object[]]$Manifest)

  $canonical = ($Manifest | ForEach-Object { "$($_.path)|$($_.sha256)`n" }) -join ''
  $bytes = [Text.Encoding]::UTF8.GetBytes($canonical)
  return [Convert]::ToHexString(
    [Security.Cryptography.SHA256]::HashData($bytes)
  ).ToLowerInvariant()
}

function Invoke-Case {
  param([Parameter(Mandatory)][string[]]$Arguments)

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
  if (-not $process.WaitForExit(60000)) {
    $process.Kill($true)
    throw 'Journal helper case exceeded 60 seconds.'
  }
  return [ordered]@{
    ExitCode = $process.ExitCode
    Text = $stdout.Result + $stderr.Result
  }
}

function Assert-ExactSet {
  param(
    [Parameter(Mandatory)][object[]]$Actual,
    [Parameter(Mandatory)][object[]]$Expected,
    [Parameter(Mandatory)][string]$Label
  )

  $difference = @(Compare-Object `
    ($Actual | Sort-Object) `
    ($Expected | Sort-Object) `
    -CaseSensitive)
  if ($Actual.Count -ne $Expected.Count -or
      @($Actual | Sort-Object -Unique).Count -ne $Expected.Count -or
      $difference.Count -ne 0) {
    throw "$Label does not match the exact required inventory."
  }
}

function Assert-Report {
  param([Parameter(Mandatory)][pscustomobject]$Report)

  if ($Report.schemaVersion -ne 2 -or
      $Report.task -ne 'R28a' -or
      $Report.profile -ne 'lab' -or
      $Report.status -ne 'PASS' -or
      $Report.exitCode -ne 0 -or
      $Report.productionQualified -ne $false -or
      $Report.failure) {
    throw 'Malformed CMD-06 report header is not PASS.'
  }
  if ($Report.command -ne
      'pwsh -NoProfile -File scripts/services.ps1 -Task journal -Profile lab -Offline') {
    throw 'CMD-06 report command binding is not exact.'
  }

  $regressionServices = @($Report.affectedRegression | ForEach-Object { $_.service })
  Assert-ExactSet -Actual $regressionServices -Expected @('identity', 'learning') `
    -Label 'Affected regression service report'
  foreach ($service in @('identity', 'learning')) {
    $regression = @($Report.affectedRegression | Where-Object service -CEQ $service)[0]
    $excludedSuites = if ($service -eq 'identity') {
      @('IdentityBootstrapHttpTests')
    } else {
      @('LearningBootstrapHttpTests', 'ContentBundleClientTests')
    }
    $expectedSources = @(Get-ChildItem -LiteralPath (
      Join-Path $repo "backend/services/$service-service/src/test/java"
    ) -Recurse -Filter '*Tests.java' -File |
      Where-Object BaseName -NotIn $excludedSuites |
      Sort-Object BaseName)
    Assert-ExactSet -Actual @($regression.selectedSuites) `
      -Expected @($expectedSources | ForEach-Object BaseName) `
      -Label "$service affected regression suite"
    Assert-ExactSet -Actual @($regression.excludedLoopbackSuites) `
      -Expected $excludedSuites `
      -Label "$service explicit loopback exclusion"
    Assert-ExactSet `
      -Actual @($regression.selectedSourceManifest | ForEach-Object path) `
      -Expected @($expectedSources | ForEach-Object {
        $_.FullName.Substring($repo.Length + 1).Replace('\', '/')
      }) `
      -Label "$service regression source manifest"
    foreach ($source in $regression.selectedSourceManifest) {
      $sourcePath = Join-Path $repo $source.path
      if ((Get-Sha256 -Path $sourcePath) -cne $source.sha256) {
        throw "$service regression source hash is stale."
      }
    }
    if ($regression.exitCode -ne 0 -or $regression.tests -le 0 -or
        $regression.failures -ne 0 -or $regression.errors -ne 0 -or
        $regression.skipped -ne 0 -or
        @($regression.retainedReports).Count -ne @($regression.selectedSuites).Count) {
      throw "$service affected regression counters/reports are not PASS."
    }
    $retainedTests = 0
    foreach ($retained in $regression.retainedReports) {
      $retainedPath = Join-Path $repo $retained.path
      if (-not (Test-Path -LiteralPath $retainedPath -PathType Leaf) -or
          (Get-Sha256 -Path $retainedPath) -cne $retained.sha256) {
        throw "$service retained regression report hash is stale."
      }
      [xml]$retainedXml = Get-Content -LiteralPath $retainedPath -Raw
      if ([int]$retainedXml.testsuite.failures -ne 0 -or
          [int]$retainedXml.testsuite.errors -ne 0 -or
          [int]$retainedXml.testsuite.skipped -ne 0) {
        throw "$service retained regression XML is not PASS."
      }
      $retainedTests += [int]$retainedXml.testsuite.tests
    }
    $agentPath = Join-Path $repo $regression.agentPath
    if (-not (Test-Path -LiteralPath $agentPath -PathType Leaf) -or
        (Get-Sha256 -Path $agentPath) -cne $regression.agentHash -or
        $retainedTests -ne $regression.tests) {
      throw "$service regression agent/test-count binding is stale."
    }
  }

  $expectedPaths = @(Get-ExpectedInputPaths)
  $manifestPaths = @($Report.inputManifest | ForEach-Object { [string]$_.path })
  Assert-ExactSet -Actual $manifestPaths -Expected $expectedPaths -Label 'Input manifest'
  foreach ($input in $Report.inputManifest) {
    if ($input.path -match '(^[A-Za-z]:|^/|(^|/)\.\.(/|$))' -or
        $input.sha256 -notmatch '^[0-9a-f]{64}$') {
      throw 'Input manifest contains an unsafe path or malformed hash.'
    }
    $currentPath = Join-Path $repo $input.path
    if (-not (Test-Path -LiteralPath $currentPath -PathType Leaf) -or
        (Get-Sha256 -Path $currentPath) -cne $input.sha256) {
      throw "Stale input manifest entry is not PASS: $($input.path)"
    }
  }
  $currentManifestDigest = Get-ManifestDigest -Manifest @($Report.inputManifest)
  if ($Report.inputManifestDigest -cne $currentManifestDigest) {
    throw 'Input manifest digest does not bind current file hashes.'
  }

  $serviceNames = @($Report.services | ForEach-Object { [string]$_.service })
  Assert-ExactSet -Actual $serviceNames -Expected @('identity', 'learning') `
    -Label 'Service report'
  foreach ($service in @('identity', 'learning')) {
    $outcome = @($Report.services | Where-Object service -CEQ $service)[0]
    if ($outcome.suite -ne "lab.backend.$service.journal.FilesystemJournalStoreIT" -or
        $outcome.tests -ne $requiredCases.Count -or
        $outcome.failures -ne 0 -or
        $outcome.errors -ne 0 -or
        $outcome.skipped -ne 0 -or
        $outcome.exitCode -ne 0) {
      throw "$service suite counters are not PASS."
    }
    Assert-ExactSet -Actual @($outcome.cases) -Expected $requiredCases `
      -Label "$service case report"

    $suitePath = Join-Path $repo $outcome.reportPath
    $artifactPath = Join-Path $repo $outcome.artifactPath
    if (-not (Test-Path -LiteralPath $suitePath -PathType Leaf) -or
        (Get-Sha256 -Path $suitePath) -cne $outcome.reportHash) {
      throw "$service current suite report hash is stale."
    }
    if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf) -or
        (Get-Sha256 -Path $artifactPath) -cne $outcome.artifactHash) {
      throw "$service current artifact hash is stale."
    }
    [xml]$suiteXml = Get-Content -LiteralPath $suitePath -Raw
    $xmlCases = @($suiteXml.testsuite.testcase | ForEach-Object { [string]$_.name })
    Assert-ExactSet -Actual $xmlCases -Expected $requiredCases `
      -Label "$service current XML case report"

    $archive = [IO.Compression.ZipFile]::OpenRead($artifactPath)
    try {
      $archiveEntries = @($archive.Entries | ForEach-Object { $_.FullName })
    } finally {
      $archive.Dispose()
    }
    $mainPrefix = "BOOT-INF/classes/lab/backend/$service/journal"
    foreach ($entry in @(
      "$mainPrefix/JournalStore.class",
      "$mainPrefix/FilesystemJournalStore.class"
    )) {
      if ($entry -notin $archiveEntries) {
        throw "$service main journal implementation is absent from the artifact."
      }
    }
    $currentForbidden = @($archiveEntries | Where-Object {
      $_ -match '(JournalFixtureMain|FilesystemJournalStoreIT)\.class$'
    })
    if (@($outcome.artifactInspection.missingMainEntries).Count -ne 0 -or
        @($outcome.artifactInspection.forbiddenEntries).Count -ne 0 -or
        $currentForbidden.Count -ne 0) {
      throw "$service artifact contains a missing main class or test/fault hook."
    }

    $joinedEvidence = @($outcome.evidence.lines) -join "`n"
    foreach ($fragment in @(
      'type=snapshot',
      'type=concurrency',
      'type=crash',
      'missing-record,missing-node',
      'malformed-sequence,wrong-namespace,wrong-predecessor',
      'forged-head,head-gap',
      'traversal,collision,reparse',
      'type=orphan'
    )) {
      if (-not $joinedEvidence.Contains($fragment, [StringComparison]::Ordinal)) {
        throw "$service required fault/evidence outcome is missing: $fragment"
      }
    }
    if ($outcome.evidence.casRetries -lt 1 -or
        $outcome.evidence.orphanNodes -lt 1 -or
        @($outcome.evidence.fixedHeads).Count -lt 3 -or
        @($outcome.evidence.inventoryDigests).Count -lt 3 -or
        -not $outcome.evidence.childProcessesExited -or
        -not $outcome.evidence.fixtureRootsRemoved -or
        $outcome.evidence.fixtureRootCount -ne $requiredCases.Count) {
      throw "$service CAS/digest/cleanup evidence is not PASS."
    }
  }

  if (-not $Report.cleanup.childProcessesExited -or
      -not $Report.cleanup.rootsOwnedByTempFixtures -or
      $Report.cleanup.fixtureRootCount -ne (2 * $requiredCases.Count) -or
      $Report.limitations.Count -lt 2 -or
      ($Report.limitations -join ' ') -notmatch 'Local filesystem' -or
      ($Report.limitations -join ' ') -notmatch 'No off-host') {
    throw 'Cleanup/local-only limitation gate is not PASS.'
  }
}

function Copy-Report {
  param([Parameter(Mandatory)][pscustomobject]$Report)

  return $Report | ConvertTo-Json -Depth 14 | ConvertFrom-Json
}

function Assert-Rejected {
  param(
    [Parameter(Mandatory)][scriptblock]$Check,
    [Parameter(Mandatory)][string]$Label
  )

  try {
    & $Check
  } catch {
    return
  }
  throw "$Label malformed report was accepted."
}

$latest = Get-ChildItem (Join-Path $repo 'artifacts/quality/R28a') `
  -Filter report.json -Recurse |
  Where-Object {
    (Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json).profile -eq 'lab'
  } |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1
if (-not $latest) {
  throw 'Current CMD-06 lab report is missing.'
}
$report = Get-Content -LiteralPath $latest.FullName -Raw | ConvertFrom-Json
Assert-Report -Report $report

$zeroSuite = Copy-Report -Report $report
$zeroSuite.services = @()
Assert-Rejected -Label 'Zero-suite' -Check { Assert-Report -Report $zeroSuite }

$staleInput = Copy-Report -Report $report
$staleInput.inputManifest[0].sha256 = '0' * 64
Assert-Rejected -Label 'Stale-input' -Check { Assert-Report -Report $staleInput }

$missingCase = Copy-Report -Report $report
$missingCase.services[0].cases = @($missingCase.services[0].cases | Select-Object -Skip 1)
Assert-Rejected -Label 'Missing-case' -Check { Assert-Report -Report $missingCase }

$unknownCleanup = Copy-Report -Report $report
$unknownCleanup.cleanup.childProcessesExited = $false
Assert-Rejected -Label 'Unknown-cleanup' -Check { Assert-Report -Report $unknownCleanup }

$qualification = Invoke-Case @('-Task', 'journal', '-Profile', 'qualification', '-Offline')
if ($qualification.ExitCode -eq 0 -or $qualification.Text -notmatch 'NOT_QUALIFIED') {
  throw 'Qualification profile did not fail closed.'
}
$unknown = Invoke-Case @('-Task', 'journal', '-Profile', 'unknown', '-Offline')
if ($unknown.ExitCode -eq 0 -or $unknown.Text -notmatch 'ValidateSet') {
  throw 'Unknown journal profile was accepted.'
}
Write-Host (
  "R28a helper gates PASS: report=$($latest.FullName); current manifest/artifacts, " +
  'exact cases, malformed reports, qualification and unknown profile are fail closed.'
)
