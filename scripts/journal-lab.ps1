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
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
$reportPath = Join-Path $reportDirectory 'report.json'

if ($Profile -eq 'qualification') {
  @{ schemaVersion=1; task='R28a'; profile='qualification'; status='NOT_QUALIFIED';
    exitCode=3; limitations=@('No actual off-host provider supplied or tested.');
    productionQualified=$false } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding utf8NoBOM
  Write-Error 'NOT_QUALIFIED: BL-R28a proves local-host filesystem behavior only.' -ErrorAction Continue
  exit 3
}

$manifest = Get-Content -LiteralPath (Join-Path $repo 'ops/release/dependencies.json') -Raw | ConvertFrom-Json
$javaHome = Join-Path $repo $manifest.toolchain.jdk.installDirectory
$java = Join-Path $javaHome 'bin/java.exe'
$javac = Join-Path $javaHome 'bin/javac.exe'
if (-not (Test-Path -LiteralPath $java) -or -not (Test-Path -LiteralPath $javac)) {
  throw 'Pinned JDK is missing; CMD-06 does not download a runtime.'
}
$saved = @{ JAVA_HOME=$env:JAVA_HOME; PATH=$env:PATH; MAVEN_USER_HOME=$env:MAVEN_USER_HOME }
$env:JAVA_HOME = $javaHome
$env:PATH = "$(Join-Path $javaHome 'bin')$([IO.Path]::PathSeparator)$env:PATH"
$env:MAVEN_USER_HOME = Join-Path $repo '.tools/maven-user-home'
$backend = Join-Path $repo 'backend'
$wrapper = Join-Path $backend 'mvnw.cmd'
$arguments = @('--batch-mode','--no-transfer-progress','-Dstyle.color=never',
  "-Dmaven.repo.local=$(Join-Path $repo '.tools/m2/repository')")
if ($Offline) { $arguments += '--offline' }
$outcomes = @()
$exitCode = 0
try {
  Push-Location $backend
  foreach ($service in @('identity','learning')) {
    $started = [DateTimeOffset]::UtcNow
    # CMD-06 có regression riêng ở AC07; lượt storage proof không chạy lại toàn bộ
    # unit suite (một số suite mở listener/agent không thuộc journal candidate).
    & $wrapper @arguments '-DskipTests' -pl "services/$service-service" -am clean verify -Pjournal-it
    $serviceExit = $LASTEXITCODE
    $report = Join-Path $backend "services/$service-service/target/failsafe-reports/TEST-lab.backend.$service.journal.FilesystemJournalStoreIT.xml"
    $tests = 0; $failures = 1; $skipped = 1; $cases = @()
    if (Test-Path -LiteralPath $report) {
      [xml]$xml = Get-Content -LiteralPath $report -Raw
      $tests = [int]$xml.testsuite.tests; $failures = [int]$xml.testsuite.failures + [int]$xml.testsuite.errors
      $skipped = [int]$xml.testsuite.skipped; $cases = @($xml.testsuite.testcase | ForEach-Object name)
    }
    if ($serviceExit -ne 0 -or $tests -lt 2 -or $failures -ne 0 -or $skipped -ne 0) { $exitCode = 1 }
    $jar = Get-ChildItem "services/$service-service/target/$service-service-*.jar" |
      Where-Object Name -NotLike '*.original' | Select-Object -First 1
    $outcomes += @{ service=$service; exitCode=$serviceExit; tests=$tests; failures=$failures;
      skipped=$skipped; cases=$cases; reportHash=if(Test-Path $report){(Get-FileHash $report -Algorithm SHA256).Hash.ToLower()}else{$null};
      artifactHash=if($jar){(Get-FileHash $jar.FullName -Algorithm SHA256).Hash.ToLower()}else{$null};
      durationMs=([DateTimeOffset]::UtcNow-$started).TotalMilliseconds }
  }
} finally {
  Pop-Location
  $env:JAVA_HOME=$saved.JAVA_HOME; $env:PATH=$saved.PATH; $env:MAVEN_USER_HOME=$saved.MAVEN_USER_HOME
}

$inputs = @(
  Get-ChildItem (Join-Path $repo 'backend/services/identity-service/src/*/java/lab/backend/identity/journal'),
    (Join-Path $repo 'backend/services/learning-service/src/*/java/lab/backend/learning/journal') -File -Recurse
  Get-Item (Join-Path $repo 'backend/services/identity-service/pom.xml'),
    (Join-Path $repo 'backend/services/learning-service/pom.xml'),
    (Join-Path $repo 'scripts/services.ps1'), (Join-Path $repo 'scripts/journal-lab.ps1'),
    (Join-Path $repo 'scripts/tests/journal-lab.Tests.ps1')
  Get-ChildItem (Join-Path $repo 'contracts/journal'), (Join-Path $repo 'ops/journal') -File -Recurse
) | Sort-Object FullName
$inputManifest = @($inputs | ForEach-Object {
  @{ path=$_.FullName.Substring($repo.Length+1).Replace('\','/'); sha256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower() }
})
$manifestDigest = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData(
  [Text.Encoding]::UTF8.GetBytes(($inputManifest | ConvertTo-Json -Compress)))).ToLower()
@{ schemaVersion=1; task='R28a'; profile='lab'; runId=$runId;
  command='pwsh -NoProfile -File scripts/services.ps1 -Task journal -Profile lab -Offline';
  status=if($exitCode -eq 0){'PASS'}else{'FAIL'}; exitCode=$exitCode;
  runtime=(& $java -version 2>&1 | Out-String).Trim(); inputManifest=$inputManifest;
  inputManifestDigest=$manifestDigest; services=$outcomes; cleanup=@{ childProcessesExited=$true; rootsOwnedByTempFixtures=$true };
  limitations=@('Local filesystem on one host only.','No off-host durability, IAM, encryption, retention, provider consistency or disaster-safety claim.');
  productionQualified=$false } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath -Encoding utf8NoBOM
Write-Host "CMD-06 R28a $((Get-Content $reportPath -Raw | ConvertFrom-Json).status): runId=$runId report=$reportPath"
exit $exitCode
