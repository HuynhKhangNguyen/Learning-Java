#requires -Version 7.4
[CmdletBinding()]
param(
  [Parameter(Mandatory)]
  [ValidateSet('lab', 'qualification')]
  [string]$Profile,
  [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repo = Split-Path -Parent $PSScriptRoot
$runId = [DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
$reportDirectory = Join-Path $repo "artifacts/quality/R28a/$runId"
$reportPath = Join-Path $reportDirectory 'report.json'
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null

function Get-Sha256 {
  param([Parameter(Mandatory)][string]$Path)

  return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-InputFiles {
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
  return @($files | Sort-Object FullName -Unique)
}

function Get-InputManifest {
  return @(Get-InputFiles | ForEach-Object {
    [ordered]@{
      path = $_.FullName.Substring($repo.Length + 1).Replace('\', '/')
      sha256 = Get-Sha256 -Path $_.FullName
    }
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

function Assert-ServiceEvidence {
  param(
    [Parameter(Mandatory)][string]$Service,
    [Parameter(Mandatory)][string[]]$Lines
  )

  $joined = $Lines -join "`n"
  $requiredFragments = @(
    'type=snapshot',
    'type=concurrency',
    'type=threads writers=12 adapterInstances=14 appendInventory=true cleanup=true',
    'type=crash',
    'type=corruption outcomes=missing-record,missing-node',
    'type=corruption outcomes=malformed-sequence,wrong-namespace,wrong-predecessor',
    'type=corruption outcomes=forged-head,head-gap',
    'type=path outcomes=traversal,collision,reparse',
    'type=orphan'
  )
  foreach ($fragment in $requiredFragments) {
    if (-not $joined.Contains($fragment, [StringComparison]::Ordinal)) {
      throw "$Service evidence is missing required fragment: $fragment"
    }
  }
  if ($joined -notmatch (
      'faultPoints=after-record-before-node,after-node-before-publish,' +
      'after-publish-before-receipt cleanup=true'
  )) {
    throw "$Service crash/process cleanup evidence is incomplete."
  }
  if ($joined -notmatch 'type=concurrency[^\r\n]*casRetries=([1-9][0-9]*)') {
    throw "$Service did not prove a CAS loser/retry."
  }
  $casRetries = [int]$Matches[1]
  if ($joined -notmatch 'type=concurrency[^\r\n]*orphanNodes=([1-9][0-9]*)[^\r\n]*cleanup=true') {
    throw "$Service did not prove CAS orphan/process cleanup."
  }
  $orphanNodes = [int]$Matches[1]

  $fixtureRoots = @()
  foreach ($line in $Lines) {
    if ($line -notmatch 'fixtureRootBase64=([A-Za-z0-9+/=]+)$') {
      throw "$Service evidence line does not bind its owned fixture root."
    }
    $fixtureRoots += [Text.Encoding]::UTF8.GetString(
      [Convert]::FromBase64String($Matches[1])
    )
  }
  $uniqueRoots = @($fixtureRoots | Sort-Object -Unique)
  if ($uniqueRoots.Count -ne $Lines.Count) {
    throw "$Service fixture root inventory is incomplete or duplicated."
  }
  $remainingRoots = @($uniqueRoots | Where-Object { Test-Path -LiteralPath $_ })
  if ($remainingRoots.Count -ne 0) {
    throw "$Service JUnit-owned fixture root was not removed."
  }

  $fixedHeads = @([regex]::Matches($joined, 'fixedHead=([0-9a-f]{64})') |
    ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
  $inventoryDigests = @([regex]::Matches($joined, 'inventoryDigest=([0-9a-f]{64})') |
    ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
  if ($fixedHeads.Count -lt 3 -or $inventoryDigests.Count -lt 3) {
    throw "$Service fixed-head/inventory digest evidence is incomplete."
  }

  return [ordered]@{
    lines = $Lines
    fixedHeads = $fixedHeads
    inventoryDigests = $inventoryDigests
    casRetries = $casRetries
    orphanNodes = $orphanNodes
    childProcessesExited = $true
    fixtureRootsRemoved = $true
    fixtureRootCount = $uniqueRoots.Count
  }
}

$command = 'pwsh -NoProfile -File scripts/services.ps1 -Task journal -Profile lab -Offline'
if ($Profile -eq 'qualification') {
  $command = $command.Replace('-Profile lab', '-Profile qualification')
  [ordered]@{
    schemaVersion = 2
    task = 'R28a'
    profile = 'qualification'
    runId = $runId
    command = $command
    status = 'NOT_QUALIFIED'
    exitCode = 3
    limitations = @('No actual off-host provider supplied or tested.')
    productionQualified = $false
  } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding utf8NoBOM
  Write-Error 'NOT_QUALIFIED: BL-R28a proves local-host filesystem behavior only.' `
    -ErrorAction Continue
  exit 3
}

$requiredCases = @(
  'immutableIdempotentSnapshotAndCorruptionFailClosed',
  'concurrentProcessesExerciseCasLoserRetryWithoutLostRecord',
  'sameJvmThreadsAcrossInstancesCoordinateAppendAndInventory',
  'processCrashBoundariesRemainUnacknowledgedThenConverge',
  'missingRecordAndNodeFailClosedAsTypedCorruption',
  'malformedSequenceNamespaceAndPredecessorFailClosed',
  'forgedHeadAndGapFailClosed',
  'traversalCollisionAndReparseEscapeAreRejected',
  'orphanRecordAndNodeStayOutsideVerifiedPrefix'
)
$inputManifest = @(Get-InputManifest)
$inputManifestDigest = Get-ManifestDigest -Manifest $inputManifest
$outcomes = @()
$regressionOutcomes = @()
$runtime = $null
$failureMessage = $null
$exitCode = 0
$locationPushed = $false
$savedEnvironment = [ordered]@{
  JAVA_HOME = $env:JAVA_HOME
  PATH = $env:PATH
  MAVEN_USER_HOME = $env:MAVEN_USER_HOME
}

try {
  $dependencyManifest = Get-Content -LiteralPath (
    Join-Path $repo 'ops/release/dependencies.json'
  ) -Raw | ConvertFrom-Json
  $javaHome = Join-Path $repo $dependencyManifest.toolchain.jdk.installDirectory
  $java = Join-Path $javaHome 'bin/java.exe'
  $jarTool = Join-Path $javaHome 'bin/jar.exe'
  if (-not (Test-Path -LiteralPath $java -PathType Leaf) -or
      -not (Test-Path -LiteralPath $jarTool -PathType Leaf)) {
    throw 'Pinned JDK is missing; CMD-06 does not download a runtime.'
  }
  $runtime = (& $java -version 2>&1 | Out-String).Trim()
  $env:JAVA_HOME = $javaHome
  $env:PATH = "$(Join-Path $javaHome 'bin')$([IO.Path]::PathSeparator)$env:PATH"
  $env:MAVEN_USER_HOME = Join-Path $repo '.tools/maven-user-home'

  $reparseRoot = Join-Path $repo '.tools/db-lab'
  $reparseFixture = Get-ChildItem -LiteralPath $reparseRoot -Directory -Recurse -Force |
    Where-Object {
      ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0
    } |
    Select-Object -First 1
  if (-not $reparseFixture) {
    throw 'Owned workspace reparse fixture is missing; path proof cannot PASS.'
  }

  $backend = Join-Path $repo 'backend'
  $wrapper = Join-Path $backend 'mvnw.cmd'
  $mavenArguments = @(
    '--batch-mode',
    '--no-transfer-progress',
    '-Dstyle.color=never',
    "-Dmaven.repo.local=$(Join-Path $repo '.tools/m2/repository')"
  )
  if ($Offline) {
    $mavenArguments += '--offline'
  }
  Push-Location $backend
  $locationPushed = $true

  $agent = Get-ChildItem -LiteralPath (
    Join-Path $repo '.tools/m2/repository/net/bytebuddy/byte-buddy-agent'
  ) -Recurse -Filter 'byte-buddy-agent-*.jar' -File |
    Sort-Object FullName -Descending |
    Select-Object -First 1
  if (-not $agent) {
    throw 'Existing Byte Buddy agent is missing; affected regression cannot run in this sandbox.'
  }
  $shortAgent = (& $env:ComSpec /d /c (
    "for %I in (`"$($agent.FullName)`") do @echo %~sI"
  ) | Out-String).Trim()
  if (-not $shortAgent) {
    throw 'Cannot resolve the existing Byte Buddy agent for the test JVM.'
  }

  foreach ($service in @('identity', 'learning')) {
    $excludedSuites = if ($service -eq 'identity') {
      @('IdentityBootstrapHttpTests')
    } else {
      @('LearningBootstrapHttpTests', 'ContentBundleClientTests')
    }
    $testSources = @(Get-ChildItem -LiteralPath (
      Join-Path $backend "services/$service-service/src/test/java"
    ) -Recurse -Filter '*Tests.java' -File |
      Where-Object BaseName -NotIn $excludedSuites |
      Sort-Object BaseName)
    $selectedSuites = @($testSources | ForEach-Object BaseName)
    if ($selectedSuites.Count -eq 0) {
      throw "$service affected regression selected zero suites."
    }
    $selector = $selectedSuites -join ','
    & $wrapper @mavenArguments `
      '-pl' "services/$service-service" `
      '-am' 'clean' 'package' `
      "-Dtest=$selector" `
      "-DargLine=-javaagent:$shortAgent"
    $regressionExit = $LASTEXITCODE
    if ($regressionExit -ne 0) {
      throw "$service affected regression failed with exit $regressionExit."
    }

    $rawReports = @(Get-ChildItem -LiteralPath (
      Join-Path $backend "services/$service-service/target/surefire-reports"
    ) -Filter 'TEST-*.xml' -File | Sort-Object Name)
    $regressionReports = @()
    $actualSuites = @()
    $tests = 0
    $failures = 0
    $errors = 0
    $skipped = 0
    $retainedDirectory = Join-Path $reportDirectory "regression/$service"
    New-Item -ItemType Directory -Path $retainedDirectory -Force | Out-Null
    foreach ($rawReport in $rawReports) {
      [xml]$regressionXml = Get-Content -LiteralPath $rawReport.FullName -Raw
      $suite = $regressionXml.testsuite
      $actualSuites += ([string]$suite.name -split '\.')[-1]
      $tests += [int]$suite.tests
      $failures += [int]$suite.failures
      $errors += [int]$suite.errors
      $skipped += [int]$suite.skipped
      $retainedPath = Join-Path $retainedDirectory $rawReport.Name
      Copy-Item -LiteralPath $rawReport.FullName -Destination $retainedPath
      $regressionReports += [ordered]@{
        path = $retainedPath.Substring($repo.Length + 1).Replace('\', '/')
        sha256 = Get-Sha256 -Path $retainedPath
      }
    }
    $suiteDifference = @(Compare-Object `
      ($selectedSuites | Sort-Object) `
      ($actualSuites | Sort-Object) `
      -CaseSensitive)
    if ($tests -eq 0 -or $failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0 -or
        $suiteDifference.Count -ne 0 -or
        @($actualSuites | Sort-Object -Unique).Count -ne $selectedSuites.Count) {
      throw "$service affected regression report inventory is not PASS."
    }

    $regressionOutcomes += [ordered]@{
      service = $service
      command = (
        "backend/mvnw.cmd --offline -pl services/$service-service -am clean package " +
        "-Dtest=<selected *Tests.java> -DargLine=-javaagent:<existing agent>"
      )
      exitCode = $regressionExit
      selectedSuites = $selectedSuites
      excludedLoopbackSuites = $excludedSuites
      selectedSourceManifest = @($testSources | ForEach-Object {
        [ordered]@{
          path = $_.FullName.Substring($repo.Length + 1).Replace('\', '/')
          sha256 = Get-Sha256 -Path $_.FullName
        }
      })
      tests = $tests
      failures = $failures
      errors = $errors
      skipped = $skipped
      retainedReports = $regressionReports
      agentPath = $agent.FullName.Substring($repo.Length + 1).Replace('\', '/')
      agentHash = Get-Sha256 -Path $agent.FullName
    }
  }

  foreach ($service in @('identity', 'learning')) {
    $started = [DateTimeOffset]::UtcNow
    & $wrapper @mavenArguments `
      '-pl' "services/$service-service" `
      '-am' 'clean' 'package' '-Pjournal-it' `
      "-Djournal.reparse.fixture=$($reparseFixture.FullName)"
    $serviceExit = $LASTEXITCODE
    if ($serviceExit -ne 0) {
      throw "$service journal Maven build failed with exit $serviceExit."
    }

    $suitePath = Join-Path $backend (
      "services/$service-service/target/surefire-reports/" +
      "TEST-lab.backend.$service.journal.FilesystemJournalStoreIT.xml"
    )
    if (-not (Test-Path -LiteralPath $suitePath -PathType Leaf)) {
      throw "$service required journal suite report is missing."
    }
    [xml]$suiteXml = Get-Content -LiteralPath $suitePath -Raw
    $suite = $suiteXml.testsuite
    $caseNodes = @($suite.testcase)
    $cases = @($caseNodes | ForEach-Object { [string]$_.name })
    $caseDifference = @(Compare-Object `
      ($requiredCases | Sort-Object) `
      ($cases | Sort-Object) `
      -CaseSensitive)
    if ([int]$suite.tests -ne $requiredCases.Count -or
        [int]$suite.failures -ne 0 -or
        [int]$suite.errors -ne 0 -or
        [int]$suite.skipped -ne 0 -or
        $caseNodes.Count -ne $requiredCases.Count -or
        @($cases | Sort-Object -Unique).Count -ne $requiredCases.Count -or
        $caseDifference.Count -ne 0) {
      throw "$service required named case gate is not PASS."
    }

    $evidenceLines = @($caseNodes | ForEach-Object {
      @([string]$_.'system-out'.InnerText -split "`r?`n")
    } | Where-Object { $_ -like 'R28A_EVIDENCE type=*' })
    $evidence = Assert-ServiceEvidence -Service $service -Lines $evidenceLines

    $artifact = Get-ChildItem -LiteralPath (
      Join-Path $backend "services/$service-service/target"
    ) -Filter "$service-service-*.jar" -File |
      Where-Object Name -NotLike '*.original' |
      Select-Object -First 1
    if (-not $artifact) {
      throw "$service packaged artifact is missing."
    }
    $entries = @(& $jarTool tf $artifact.FullName)
    if ($LASTEXITCODE -ne 0) {
      throw "$service packaged artifact cannot be inspected."
    }
    $mainPrefix = "BOOT-INF/classes/lab/backend/$service/journal"
    $requiredMainEntries = @(
      "$mainPrefix/JournalStore.class",
      "$mainPrefix/FilesystemJournalStore.class"
    )
    $missingMainEntries = @($requiredMainEntries | Where-Object { $_ -notin $entries })
    $forbiddenEntries = @($entries | Where-Object {
      $_ -match '(JournalFixtureMain|FilesystemJournalStoreIT)\.class$'
    })
    if ($missingMainEntries.Count -ne 0 -or $forbiddenEntries.Count -ne 0) {
      throw "$service packaged journal class/fault-hook gate is not PASS."
    }

    $outcomes += [ordered]@{
      service = $service
      exitCode = $serviceExit
      suite = [string]$suite.name
      tests = [int]$suite.tests
      failures = [int]$suite.failures
      errors = [int]$suite.errors
      skipped = [int]$suite.skipped
      cases = $cases
      reportPath = $suitePath.Substring($repo.Length + 1).Replace('\', '/')
      reportHash = Get-Sha256 -Path $suitePath
      artifactPath = $artifact.FullName.Substring($repo.Length + 1).Replace('\', '/')
      artifactHash = Get-Sha256 -Path $artifact.FullName
      artifactInspection = [ordered]@{
        requiredMainEntries = $requiredMainEntries
        missingMainEntries = $missingMainEntries
        forbiddenEntries = $forbiddenEntries
      }
      evidence = $evidence
      durationMs = [long]([DateTimeOffset]::UtcNow - $started).TotalMilliseconds
    }
  }

  $currentManifest = @(Get-InputManifest)
  $currentDigest = Get-ManifestDigest -Manifest $currentManifest
  if ($currentManifest.Count -ne $inputManifest.Count -or
      $currentDigest -ne $inputManifestDigest) {
    throw 'Current CMD-06 inputs changed during the run; stale evidence cannot PASS.'
  }
} catch {
  $exitCode = 1
  $failureMessage = $_.Exception.Message
} finally {
  if ($locationPushed) {
    Pop-Location
  }
  $env:JAVA_HOME = $savedEnvironment.JAVA_HOME
  $env:PATH = $savedEnvironment.PATH
  $env:MAVEN_USER_HOME = $savedEnvironment.MAVEN_USER_HOME
}

$cleanup = [ordered]@{
  childProcessesExited = (
    $outcomes.Count -eq 2 -and
    @($outcomes | Where-Object { -not $_.evidence.childProcessesExited }).Count -eq 0
  )
  rootsOwnedByTempFixtures = (
    $outcomes.Count -eq 2 -and
    @($outcomes | Where-Object { -not $_.evidence.fixtureRootsRemoved }).Count -eq 0
  )
  fixtureRootCount = [int](
    ($outcomes | ForEach-Object { $_.evidence.fixtureRootCount } |
      Measure-Object -Sum).Sum
  )
}
if (-not $cleanup.childProcessesExited -or -not $cleanup.rootsOwnedByTempFixtures) {
  $exitCode = 1
  if (-not $failureMessage) {
    $failureMessage = 'Process/root cleanup was not verified.'
  }
}

$report = [ordered]@{
  schemaVersion = 2
  task = 'R28a'
  profile = 'lab'
  runId = $runId
  command = $command
  status = if ($exitCode -eq 0) { 'PASS' } else { 'FAIL' }
  exitCode = $exitCode
  runtime = $runtime
  inputManifestAlgorithm = 'SHA-256 over UTF-8 path|sha256 LF records in report order'
  inputManifest = $inputManifest
  inputManifestDigest = $inputManifestDigest
  requiredCases = $requiredCases
  affectedRegression = $regressionOutcomes
  services = $outcomes
  cleanup = $cleanup
  limitations = @(
    'Local filesystem on one host only.',
    'No off-host durability, IAM, encryption, retention, provider consistency or disaster-safety claim.'
  )
  productionQualified = $false
  failure = $failureMessage
}
$report | ConvertTo-Json -Depth 12 |
  Set-Content -LiteralPath $reportPath -Encoding utf8NoBOM

if ($exitCode -ne 0) {
  Write-Error "CMD-06 R28a FAIL: runId=$runId report=$reportPath; $failureMessage" `
    -ErrorAction Continue
} else {
  Write-Host "CMD-06 R28a PASS: runId=$runId report=$reportPath"
}
exit $exitCode
