#requires -Version 7.4
[CmdletBinding()]
param(
  [string]$JavaHome,
  [string]$UnsupportedJavaHome,
  [switch]$RelayOnly,
  [switch]$CanonicalOnly,
  [switch]$PublicationOnly,
  [switch]$LearningPublicationOnly,
  [switch]$CoordinatorOnly,
  [ValidateSet('R01a', 'R02a', 'R02b','R02c','R03','R04a','R04b','R04c','R04d','R06a','R06b','R06c')]
  [string]$EvidenceTask = 'R01a'
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$focusedModeCount=@($RelayOnly,$CanonicalOnly,$PublicationOnly,$LearningPublicationOnly,$CoordinatorOnly).Where({$_}).Count
if($focusedModeCount -gt 1){throw 'Select only one focused helper mode.'}
if($PublicationOnly -and $EvidenceTask -ne 'R06a'){throw 'PublicationOnly is owned by R06a.'}
if($EvidenceTask -eq 'R06a' -and -not $PublicationOnly){throw 'R06a helper evidence requires PublicationOnly.'}
if($LearningPublicationOnly -and $EvidenceTask -ne 'R06b'){throw 'LearningPublicationOnly is owned by R06b.'}
if($EvidenceTask -eq 'R06b' -and -not $LearningPublicationOnly){throw 'R06b helper evidence requires LearningPublicationOnly.'}
if($CoordinatorOnly -and $EvidenceTask -ne 'R06c'){throw 'CoordinatorOnly is owned by R06c.'}
if($EvidenceTask -eq 'R06c' -and -not $CoordinatorOnly){throw 'R06c helper evidence requires CoordinatorOnly.'}
if (-not $IsWindows) { throw 'This R01a helper harness is qualified on Windows; Linux parity belongs to R31c.' }
$repoDirectory = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$manifest = Get-Content -LiteralPath (Join-Path $repoDirectory 'ops/release/dependencies.json') -Raw | ConvertFrom-Json
if (-not $JavaHome) { $JavaHome = Join-Path $repoDirectory $manifest.toolchain.jdk.installDirectory }
$testRoot = Join-Path $repoDirectory ".tools/test-r01a/$([Guid]::NewGuid().ToString('N'))"
foreach ($directory in @('scripts', 'backend/services/identity-service', 'ops/release')) {
  New-Item -ItemType Directory -Path (Join-Path $testRoot $directory) -Force | Out-Null
}
Copy-Item -LiteralPath (Join-Path $repoDirectory 'scripts/services.ps1') -Destination (Join-Path $testRoot 'scripts/services.ps1')
Copy-Item -LiteralPath (Join-Path $repoDirectory 'scripts/setup-backend.ps1') -Destination (Join-Path $testRoot 'scripts/setup-backend.ps1')
Copy-Item -LiteralPath (Join-Path $repoDirectory 'scripts/db_lab.py') -Destination (Join-Path $testRoot 'scripts/db_lab.py')
Copy-Item -LiteralPath (Join-Path $repoDirectory 'ops/release/dependencies.json') -Destination (Join-Path $testRoot 'ops/release/dependencies.json')
$fixturePom = Join-Path $testRoot 'backend/pom.xml'
@'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion><groupId>fixture</groupId><artifactId>parent</artifactId>
  <version>1</version><packaging>pom</packaging><modules><module>services/identity-service</module></modules>
</project>
'@ | Set-Content -LiteralPath $fixturePom -Encoding utf8
$servicePom = Join-Path $testRoot 'backend/services/identity-service/pom.xml'
'<project/>' | Set-Content -LiteralPath $servicePom -Encoding utf8
$fixtureWrapper = Join-Path $testRoot 'backend/mvnw.cmd'
# Command double (lệnh giả trong fixture riêng) kiểm gate đọc report, không chạy app Java.
# Ghi lại token thực nhận bởi process native để phát hiện selector bị PowerShell tách sai.
$nativeCaptureScript=Join-Path $testRoot 'scripts/capture-native.ps1'
@'
$ErrorActionPreference='Stop'
@{arguments=@($args)} | ConvertTo-Json -Compress | Add-Content -LiteralPath (Join-Path $PSScriptRoot 'native-arguments.jsonl') -Encoding utf8
exit 0
'@ | Set-Content -LiteralPath $nativeCaptureScript -Encoding utf8
"@echo off`r`npwsh -NoProfile -File `"%~dp0..\scripts\capture-native.ps1`" %*`r`nexit /b %ERRORLEVEL%`r`n" | Set-Content -LiteralPath $fixtureWrapper -Encoding ascii
$reportDirectory = Join-Path $testRoot 'backend/services/identity-service/target/surefire-reports'
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
$reportPath = Join-Path $reportDirectory 'TEST-lab.backend.identity.session.SessionHttpHandlerTests.xml'
$results = [Collections.Generic.List[object]]::new()
# Command doubles relay/canonical khởi động nhiều pwsh/JDK con tuần tự. Cho
# watchdog harness riêng đủ headroom khi Windows chậm; không sửa budget app,
# DB/HTTP/transaction hoặc biến timeout thành PASS. Diagnostics giữ trước throw.
$fixtureCommandTimeoutMs = 60000

function Check-Command {
  param([string]$Name, [string]$Script, [string[]]$Arguments, [int]$ExpectedExit, [string]$ExpectedText)
  $nativeCaptureLog=Join-Path $testRoot 'scripts/native-arguments.jsonl'
  if(Test-Path -LiteralPath $nativeCaptureLog) { Remove-Item -LiteralPath $nativeCaptureLog }
  $processInfo = [Diagnostics.ProcessStartInfo]::new()
  $processInfo.FileName = [Environment]::ProcessPath
  $processInfo.WorkingDirectory = $testRoot
  $processInfo.RedirectStandardOutput = $true
  $processInfo.RedirectStandardError = $true
  $processInfo.CreateNoWindow = $true
  foreach ($argument in @('-NoProfile', '-File', $Script) + $Arguments) { $processInfo.ArgumentList.Add($argument) }
  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $processInfo
  if (-not $process.Start()) { throw 'Failed to start helper test process.' }
  $stdoutTask = $process.StandardOutput.ReadToEndAsync()
  $stderrTask = $process.StandardError.ReadToEndAsync()
  if (-not $process.WaitForExit($fixtureCommandTimeoutMs)) {
    $process.Kill($true)
    $cleanupConfirmed = $process.WaitForExit(5000)
    $failureOutput = if($stdoutTask.IsCompleted){$stdoutTask.GetAwaiter().GetResult()}else{'stdout drain incomplete'}
    $failureOutput += if($stderrTask.IsCompleted){$stderrTask.GetAwaiter().GetResult()}else{'stderr drain incomplete'}
    @{scope='Helper fixture watchdog failure; partial cases are not full helper PASS';failedCase=$Name;
      timeoutMs=$fixtureCommandTimeoutMs;childExited=$cleanupConfirmed;output=$failureOutput;completedCases=$results} |
      ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $testRoot 'helper-failure.json') -Encoding utf8
    $process.Dispose()
    throw "Timeout: $Name; owned child exited=$cleanupConfirmed; diagnostics in fixture helper-failure.json"
  }
  $output = $stdoutTask.GetAwaiter().GetResult() + $stderrTask.GetAwaiter().GetResult()
  $matchedExit = if ($ExpectedExit -eq 1) { $process.ExitCode -ne 0 } else { $process.ExitCode -eq $ExpectedExit }
  if (-not $matchedExit -or $output -notmatch $ExpectedText) {
    # Count/output mismatch cũng là FAIL cần giữ evidence, không chỉ watchdog.
    # Lưu trong đúng disposable fixture root; không invent full helper report.
    $failureExit=$process.ExitCode
    @{scope='Helper report/command mismatch; partial cases are not full helper PASS';failedCase=$Name;
      expectedExit=$ExpectedExit;actualExit=$failureExit;expectedText=$ExpectedText;output=$output;
      childExited=$process.HasExited;completedCases=$results} | ConvertTo-Json -Depth 6 |
      Set-Content -LiteralPath (Join-Path $testRoot 'helper-failure.json') -Encoding utf8
    $process.Dispose()
    throw "Test '$Name' failed: exit=$failureExit, expected=$ExpectedExit; output=$output"
  }
  if($ExpectedExit -eq 0 -and $Arguments -contains '-Task' -and $Arguments[[Array]::IndexOf($Arguments,'-Task')+1] -eq 'relay' -and $Arguments -contains '-Case') {
    # Chỉ report XML xanh chưa chứng minh lệnh đúng: DATA selector phải tới native wrapper
    # dưới dạng đúng một token -Dit.test, không có ký tự '-' lẻ thành Maven lifecycle phase.
    $nativeCase=$Arguments[[Array]::IndexOf($Arguments,'-Case')+1]
    if($nativeCase -like 'DATA-*') {
      $capturedCommands=@(Get-Content -LiteralPath $nativeCaptureLog | ForEach-Object { ConvertFrom-Json $_ })
      $selectedNativeCommands=@($capturedCommands | Where-Object { $_.arguments -contains '-Prelay-it' })
      if($selectedNativeCommands.Count -ne 1) { throw "Expected exactly one native relay build: $Name" }
      $expectedNativeSelector="-Dit.test=DeliveryRuntimeIT#$($relaySelectorCases[$nativeCase] -join '+')"
      $actualNativeSelectors=@($selectedNativeCommands[0].arguments | Where-Object { $_ -like '-Dit.test=*' })
      if($actualNativeSelectors.Count -ne 1 -or $actualNativeSelectors[0] -cne $expectedNativeSelector -or $selectedNativeCommands[0].arguments -contains '-') { throw "Native selector argument was split or changed: $Name" }
    }
  }
  $results.Add([pscustomobject]@{case=$Name;exit=$process.ExitCode;result='PASS'})
  Write-Host "PASS: $Name"
  $process.Dispose()
}

$helper = Join-Path $testRoot 'scripts/services.ps1'
$javaArguments = @('-JavaHome', $JavaHome)
function Write-CaseFixture([string]$Path,[string[]]$Names) {
  ('<testsuite tests="'+$Names.Count+'" skipped="0" failures="0" errors="0">'+(($Names | ForEach-Object { '<testcase name="'+$_+'"/>' }) -join '')+'</testsuite>') | Set-Content -LiteralPath $Path -Encoding utf8
}
if($CoordinatorOnly) {
  # XML doubles chỉ chứng minh command/report gate fail closed. Actual
  # PostgreSQL/mTLS vẫn phải chạy qua managed CMD-03/04 R06c.
  $contentRoot=Join-Path $testRoot 'backend/services/content-service'
  $learningRoot=Join-Path $testRoot 'backend/services/learning-service'
  $unitDirectory="$contentRoot/target/surefire-reports"
  $integrationDirectory="$contentRoot/target/failsafe-reports"
  New-Item -ItemType Directory -Path $unitDirectory,$integrationDirectory,$learningRoot -Force | Out-Null
  '<project/>' | Set-Content -LiteralPath "$contentRoot/pom.xml" -Encoding utf8
  '<project/>' | Set-Content -LiteralPath "$learningRoot/pom.xml" -Encoding utf8
  (Get-Content -LiteralPath $fixturePom -Raw).Replace(
    '</modules>',
    '<module>services/content-service</module><module>services/learning-service</module></modules>') |
    Set-Content -LiteralPath $fixturePom -Encoding utf8

  $labDirectory=Join-Path $testRoot '.tools/db-lab/run-r06c-fixture'
  $trustDirectory=Join-Path $testRoot '.tools/trust-lab/run-r06c-fixture'
  New-Item -ItemType Directory -Path $labDirectory,$trustDirectory -Force | Out-Null
  $labConfig=Join-Path $labDirectory 'test-credentials.properties'
  $trustConfig=Join-Path $trustDirectory 'content.properties'
  'fixture=true' | Set-Content -LiteralPath $labConfig -Encoding ascii
  'fixture=true' | Set-Content -LiteralPath $trustConfig -Encoding ascii
  @{
    schemaVersion=1
    runId=[Guid]::NewGuid().ToString()
    workspace=$testRoot
    dataDirectory=(Join-Path $labDirectory 'data')
  } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $labDirectory 'ownership.json') -Encoding utf8
  @{
    schemaVersion=1
    runId=[Guid]::NewGuid().ToString()
    workspace=$testRoot
  } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $trustDirectory 'ownership.json') -Encoding utf8
  $dbArguments=@(
    '-Task','db','-Service','content','-EvidenceTask','R06c','-LabConfig',$labConfig
  )+$javaArguments
  $contractArguments=@(
    '-Task','contract','-Service','content','-EvidenceTask','R06c',
    '-LabConfig',$labConfig,'-TrustConfig',$trustConfig
  )+$javaArguments

  foreach($bad in @(
    @{Name='R06c wrong service rejected';Args=@('-Task','db','-Service','learning','-EvidenceTask','R06c');Text='Content CMD-03/04/05 only'},
    @{Name='R06c verify rejected';Args=@('-Task','verify','-Service','content','-EvidenceTask','R06c');Text='Content CMD-03/04/05 only'},
    @{Name='R06c fault missing selector rejected';Args=@('-Task','fault','-Service','content','-EvidenceTask','R06c');Text='implemented DATA selector'},
    @{Name='R06c unknown fault rejected';Args=@('-Task','fault','-Service','content','-EvidenceTask','R06c','-Case','DATA-01');Text='requires DATA-02'},
    @{Name='R06c DB case rejected';Args=$dbArguments+@('-Case','DATA-02');Text='full named baselines'},
    @{Name='R06c contract case rejected';Args=$contractArguments+@('-Case','DATA-02');Text='full named baselines'},
    @{Name='R06c contract missing trust rejected';Args=@('-Task','contract','-Service','content','-EvidenceTask','R06c','-LabConfig',$labConfig)+$javaArguments;Text='Required trust config missing'}
  )) {
    Check-Command $bad.Name $helper $bad.Args 1 $bad.Text
  }

  function Check-R06cParserFailure(
      [string]$Name,
      [string[]]$Arguments,
      [string]$ExpectedText) {
    $info=[Diagnostics.ProcessStartInfo]::new()
    $info.FileName=(Get-Command python -ErrorAction Stop).Source
    $info.WorkingDirectory=$testRoot
    $info.RedirectStandardOutput=$true
    $info.RedirectStandardError=$true
    $info.CreateNoWindow=$true
    foreach($argument in @('-X','utf8',(Join-Path $testRoot 'scripts/db_lab.py'))+$Arguments) {
      $info.ArgumentList.Add($argument)
    }
    $child=[Diagnostics.Process]::new()
    $child.StartInfo=$info
    if(-not $child.Start()){throw "Failed parser probe: $Name"}
    $stdout=$child.StandardOutput.ReadToEndAsync()
    $stderr=$child.StandardError.ReadToEndAsync()
    if(-not $child.WaitForExit($fixtureCommandTimeoutMs)) {
      $child.Kill($true)
      throw "Parser timeout: $Name"
    }
    $output=$stdout.GetAwaiter().GetResult()+$stderr.GetAwaiter().GetResult()
    if($child.ExitCode -eq 0 -or $output -notmatch $ExpectedText) {
      throw "Parser probe failed: $Name exit=$($child.ExitCode) output=$output"
    }
    $results.Add([pscustomobject]@{case=$Name;exit=$child.ExitCode;result='PASS'})
    $child.Dispose()
    Write-Host "PASS: $Name"
  }
  Check-R06cParserFailure 'db_lab R06c wrong service rejected' @('--service','learning','--task','db','--evidence-task','R06c') 'Content CMD-03/04/05 only'
  Check-R06cParserFailure 'db_lab R06c missing selector rejected' @('--service','content','--task','fault','--evidence-task','R06c') 'CMD-05 requires DATA-02'
  Check-R06cParserFailure 'db_lab R06c unknown selector rejected' @('--service','content','--task','fault','--case','DATA-01','--evidence-task','R06c') 'CMD-05 requires DATA-02'
  Check-R06cParserFailure 'db_lab R06c case rejected' @('--service','content','--task','contract','--case','DATA-02','--evidence-task','R06c') 'baseline takes no case'

  foreach($suite in @('DeliveryWireTests','DeliveryRuntimeConfigTests')) {
    Write-CaseFixture (Join-Path $unitDirectory "TEST-lab.backend.content.delivery.$suite.xml") @("$suite-fixture")
  }
  Write-CaseFixture (Join-Path $unitDirectory 'TEST-lab.backend.content.identityaccess.AdmissionWireTests.xml') @('admission-wire-fixture')
  $databaseReport=Join-Path $integrationDirectory 'TEST-lab.backend.content.database.DatabaseIsolationIT.xml'
  $deliveryReport=Join-Path $integrationDirectory 'TEST-lab.backend.content.delivery.DeliveryStoreIT.xml'
  $admissionReport=Join-Path $integrationDirectory 'TEST-lab.backend.content.identityaccess.AdmissionGateIT.xml'
  Write-CaseFixture $deliveryReport @('delivery-fixture')
  Write-CaseFixture $admissionReport @(
    'absentSessionFenceDeniesLateAdmissionWithoutProvisioningLearner',
    'olderCommandCannotLowerFloorAndNewEpochMayProceed',
    'duplicateCommandIsDurableAndChangedContextConflicts',
    'twoReplicasFenceWaitsForReadTransactionCommit',
    'rollbackKeepsAdmissionAndLocalEffectAtomic',
    'wrongContextNonceBindingAndOwnerCannotUseAdmission',
    'sameSessionUnderDifferentOwnerCannotReplaceTombstone',
    'runtimeRoleCannotEraseFenceReceiptOrLowerFloor',
    'wrongRealmGenerationAndNotReadyFailClosed',
    'cutoffRejectedBeforeEffectAndDatabaseBoundsHeldTransaction'
  )

  $coordinatorCase='publicationZCoordinatorCommitsFenceWithReleaseAndCompletesOnlyAfterReleasedReceipt'
  Check-Command 'R06c missing coordinator DB suite rejected' $helper $dbArguments 1 'required service DB suite missing'
  Write-CaseFixture $databaseReport @('unrelated')
  Check-Command 'R06c missing named coordinator case rejected' $helper $dbArguments 1 'Required R06c DB case missing'
  Write-CaseFixture $databaseReport @($coordinatorCase,$coordinatorCase)
  Check-Command 'R06c duplicate coordinator case rejected' $helper $dbArguments 1 'Invalid R06c coordinator DB case report'
  ('<testsuite tests="1" skipped="0" failures="0" errors="0"><testcase name="'+
    $coordinatorCase+'"><failure/></testcase></testsuite>') |
    Set-Content -LiteralPath $databaseReport -Encoding utf8
  Check-Command 'R06c hidden coordinator failure rejected' $helper $dbArguments 1 'Invalid R06c coordinator DB case report'
  Write-CaseFixture $databaseReport @($coordinatorCase)
  Check-Command 'R06c exact DB report accepted' $helper $dbArguments 0 'CMD-03 content completed: DB=12; bootstrap=3'

  $native=@(Get-Content -LiteralPath (Join-Path $testRoot 'scripts/native-arguments.jsonl') |
    ForEach-Object {ConvertFrom-Json $_})
  if($native.Count -ne 2 -or $native[0].arguments -notcontains '-Pdb-it' -or
      $native[0].arguments -notcontains 'clean' -or
      $native[1].arguments -notcontains '-Pdelivery-it' -or
      $native[1].arguments -notcontains '-Dtest=PublicationCoordinatorWireTests' -or
      $native[1].arguments -contains 'clean') {
    throw 'R06c DB native profile/phase order mismatch'
  }

  Get-ChildItem -LiteralPath $unitDirectory,$integrationDirectory -Filter 'TEST-*.xml' -File |
    ForEach-Object {Remove-Item -LiteralPath $_.FullName}
  $wireReport=Join-Path $unitDirectory 'TEST-lab.backend.content.publication.PublicationCoordinatorWireTests.xml'
  $runtimeReport=Join-Path $integrationDirectory 'TEST-lab.backend.content.publication.PublicationCoordinatorRuntimeIT.xml'
  $runtimeCase='packagedCoordinatorRecoversParticipantDownLeaseAndCompletesExactRelease'
  Write-CaseFixture $runtimeReport @($runtimeCase)
  $unrelatedUnit=Join-Path $unitDirectory 'TEST-unrelated.xml'
  Write-CaseFixture $unrelatedUnit @('unrelated')
  Check-Command 'R06c contract missing coordinator unit rejected' $helper $contractArguments 1 'coordinator unit suite missing'
  Remove-Item -LiteralPath $unrelatedUnit
  Write-CaseFixture $wireReport @('strictCommandReceiptAndStatusBindingsRoundTrip','unknownMissingTypeAndBindingConflictsFailClosed')
  Remove-Item -LiteralPath $runtimeReport
  Check-Command 'R06c contract missing runtime suite rejected' $helper $contractArguments 1 'Required trust/wire suite missing'
  Write-CaseFixture $runtimeReport @('unexpected')
  Check-Command 'R06c contract wrong runtime case rejected' $helper $contractArguments 1 'Invalid R06c coordinator runtime case report'
  Write-CaseFixture $runtimeReport @($runtimeCase,$runtimeCase)
  Check-Command 'R06c contract duplicate runtime case rejected' $helper $contractArguments 1 'Invalid R06c coordinator runtime case report'
  ('<testsuite tests="1" skipped="0" failures="0" errors="0"><testcase name="'+
    $runtimeCase+'"><failure/></testcase></testsuite>') |
    Set-Content -LiteralPath $runtimeReport -Encoding utf8
  Check-Command 'R06c contract hidden runtime failure rejected' $helper $contractArguments 1 'Invalid R06c coordinator runtime case report'
  Write-CaseFixture $runtimeReport @($runtimeCase)
  Check-Command 'R06c exact contract report accepted' $helper $contractArguments 0 'contract content completed: integration=1; unit/bootstrap=2'
  $contractNative=@(Get-Content -LiteralPath (Join-Path $testRoot 'scripts/native-arguments.jsonl') |
    ForEach-Object {ConvertFrom-Json $_})
  if($contractNative.Count -ne 2 -or
      $contractNative[0].arguments -notcontains 'services/learning-service' -or
      $contractNative[1].arguments -notcontains '-Pcoordinator-it' -or
      $contractNative[1].arguments -notcontains '-Dtest=PublicationCoordinatorWireTests' -or
      $contractNative[1].arguments -notcontains '-Dit.test=PublicationCoordinatorRuntimeIT#packagedCoordinatorRecoversParticipantDownLeaseAndCompletesExactRelease') {
    throw 'R06c CMD-04 native peer/profile/selector mismatch'
  }

  $coordinatorFaultCases=[ordered]@{
    'DATA-02'='data02GateBeforeCommitKeepsLearningPendingUntilExactRelease'
    'DATA-03'='data03ParticipantDownBeforeFenceAckKeepsCanonicalAndRecovers'
    'DATA-04'='data04CanonicalAheadPendingSurvivesRestartAndCompletesRelease'
    'DATA-05'='data05AbortAndNewOperationUseExactGlobalPrefix'
    'DATA-06'='data06WithdrawalManifestRemainsExactThroughCoordinator'
    'DATA-07'='data07InvalidRubricManifestRemainsExactThroughCoordinator'
    'DATA-14'='data14AuthenticatedWrongReceiptBindingsHaveNoEffect'
  }
  foreach($faultCase in $coordinatorFaultCases.GetEnumerator()) {
    Write-CaseFixture $runtimeReport @($faultCase.Value)
    $faultArguments=@(
      '-Task','fault','-Service','content','-EvidenceTask','R06c','-Case',$faultCase.Key,
      '-LabConfig',$labConfig,'-TrustConfig',$trustConfig
    )+$javaArguments
    Check-Command "R06c exact $($faultCase.Key) report accepted" $helper $faultArguments 0 'fault content completed: integration=1; unit/bootstrap=2'
    $faultNative=@(Get-Content -LiteralPath (Join-Path $testRoot 'scripts/native-arguments.jsonl') |
      ForEach-Object {ConvertFrom-Json $_}) | Select-Object -Last 2
    if($faultNative.Count -ne 2 -or
        $faultNative[1].arguments -notcontains "-Dit.test=PublicationCoordinatorRuntimeIT#$($faultCase.Value)" -or
        $faultNative[1].arguments -notcontains "-Dr06c.case=$($faultCase.Key)") {
      throw "R06c CMD-05 native selector mismatch: $($faultCase.Key)"
    }
  }

  $evidenceDirectory=Join-Path $repoDirectory 'artifacts/quality/R06c'
  New-Item -ItemType Directory -Path $evidenceDirectory -Force | Out-Null
  $inputHashes=@{}
  foreach($input in @('scripts/services.ps1','scripts/db_lab.py','scripts/tests/backend-build.Tests.ps1')) {
    $inputHashes[$input]=(Get-FileHash -LiteralPath (Join-Path $repoDirectory $input) -Algorithm SHA256).Hash.ToLowerInvariant()
  }
  @{
    status='PASS'
    scope='R06c CMD-03/04 helper/report gates with isolated command/XML doubles; not actual PostgreSQL or mTLS proof'
    caseCount=$results.Count
    cases=$results
    inputHashes=$inputHashes
    fixtureDirectory=$testRoot
  } | ConvertTo-Json -Depth 6 |
    Set-Content -LiteralPath (Join-Path $evidenceDirectory 'helper-coordinator-tests.json') -Encoding utf8
  Write-Host "R06c coordinator helper tests passed: $($results.Count)."
  exit 0
}
if($LearningPublicationOnly) {
  # Fixture này chỉ kiểm command/report gate R06b. Actual PG/mTLS vẫn thuộc
  # managed CMD-03/04/05 và không được suy PASS từ XML doubles.
  $learningRoot=Join-Path $testRoot 'backend/services/learning-service';$contentRoot=Join-Path $testRoot 'backend/services/content-service'
  New-Item -ItemType Directory -Path "$learningRoot/target/surefire-reports","$learningRoot/target/failsafe-reports",$contentRoot -Force | Out-Null
  '<project/>' | Set-Content -LiteralPath "$learningRoot/pom.xml" -Encoding utf8
  '<project/>' | Set-Content -LiteralPath "$contentRoot/pom.xml" -Encoding utf8
  (Get-Content -LiteralPath $fixturePom -Raw).Replace('</modules>','<module>services/learning-service</module><module>services/content-service</module></modules>') | Set-Content -LiteralPath $fixturePom -Encoding utf8
  $labDirectory=Join-Path $testRoot '.tools/db-lab/run-r06b-fixture';$trustDirectory=Join-Path $testRoot '.tools/trust-lab/run-r06b-fixture'
  New-Item -ItemType Directory -Path $labDirectory,$trustDirectory -Force | Out-Null
  $labConfig=Join-Path $labDirectory 'test-credentials.properties';$trustConfig=Join-Path $trustDirectory 'learning.properties'
  'fixture=true' | Set-Content -LiteralPath $labConfig -Encoding ascii;'fixture=true' | Set-Content -LiteralPath $trustConfig -Encoding ascii
  @{schemaVersion=1;runId=[Guid]::NewGuid().ToString();workspace=$testRoot;dataDirectory=(Join-Path $labDirectory 'data')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $labDirectory 'ownership.json') -Encoding utf8
  @{schemaVersion=1;runId=[Guid]::NewGuid().ToString();workspace=$testRoot} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $trustDirectory 'ownership.json') -Encoding utf8
  $dbArgs=@('-Task','db','-Service','learning','-EvidenceTask','R06b','-LabConfig',$labConfig)+$javaArguments
  $contractArgs=@('-Task','contract','-Service','learning','-EvidenceTask','R06b','-LabConfig',$labConfig,'-TrustConfig',$trustConfig)+$javaArguments
  foreach($bad in @(
    @{Name='R06b wrong Content service rejected';Args=@('-Task','db','-Service','content','-EvidenceTask','R06b');Text='Learning CMD-03/04/05 only'},
    @{Name='R06b verify task rejected';Args=@('-Task','verify','-Service','learning','-EvidenceTask','R06b');Text='Learning CMD-03/04/05 only'},
    @{Name='R06b contract case rejected';Args=$contractArgs+@('-Case','SEC-16');Text='contract is full baseline'},
    @{Name='R06b fault missing case rejected';Args=@('-Task','fault','-Service','learning','-EvidenceTask','R06b');Text='fault requires DATA-01'},
    @{Name='R06b unsupported fault rejected';Args=@('-Task','fault','-Service','learning','-EvidenceTask','R06b','-Case','WIRE-03');Text='fault requires DATA-01'},
    @{Name='R06b contract missing trust rejected';Args=@('-Task','contract','-Service','learning','-EvidenceTask','R06b','-LabConfig',$labConfig)+$javaArguments;Text='Required trust config missing'}
  )){Check-Command $bad.Name $helper $bad.Args 1 $bad.Text}

  function Check-R06bParserFailure([string]$Name,[string[]]$Arguments,[string]$ExpectedText){
    $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=(Get-Command python -ErrorAction Stop).Source;$info.WorkingDirectory=$testRoot;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true;$info.CreateNoWindow=$true
    foreach($argument in @('-X','utf8',(Join-Path $testRoot 'scripts/db_lab.py'))+$Arguments){$info.ArgumentList.Add($argument)}
    $child=[Diagnostics.Process]::new();$child.StartInfo=$info;if(-not $child.Start()){throw "Failed parser probe: $Name"};$stdout=$child.StandardOutput.ReadToEndAsync();$stderr=$child.StandardError.ReadToEndAsync();if(-not $child.WaitForExit($fixtureCommandTimeoutMs)){$child.Kill($true);throw "Parser timeout: $Name"};$output=$stdout.GetAwaiter().GetResult()+$stderr.GetAwaiter().GetResult();if($child.ExitCode -eq 0 -or $output -notmatch $ExpectedText){throw "Parser probe failed: $Name exit=$($child.ExitCode) output=$output"};$results.Add([pscustomobject]@{case=$Name;exit=$child.ExitCode;result='PASS'});$child.Dispose();Write-Host "PASS: $Name"
  }
  Check-R06bParserFailure 'db_lab R06b wrong service rejected' @('--service','content','--task','db','--evidence-task','R06b') 'Learning CMD-03/04/05'
  Check-R06bParserFailure 'db_lab R06b wrong task rejected' @('--service','learning','--task','relay','--evidence-task','R06b') 'Learning CMD-03/04/05'
  Check-R06bParserFailure 'db_lab R06b missing selector rejected' @('--service','learning','--task','fault','--evidence-task','R06b') 'fault requires DATA-01'
  Check-R06bParserFailure 'db_lab R06b unknown selector rejected' @('--service','learning','--task','fault','--case','WIRE-03','--evidence-task','R06b') 'fault requires DATA-01'

  $unitDirectory="$learningRoot/target/surefire-reports";$integrationDirectory="$learningRoot/target/failsafe-reports"
  $unitNames=@('ContentBundleClientTests','PublicationGateTests','PublicationWireTests','ReplicaValidatorTests')
  function Write-R06bUnits {foreach($name in $unitNames){Write-CaseFixture (Join-Path $unitDirectory "TEST-lab.backend.learning.contentreplica.$name.xml") @("$name-fixture")}}
  $dbReports=@{
    database=Join-Path $integrationDirectory 'TEST-lab.backend.learning.database.DatabaseIsolationIT.xml'
    delivery=Join-Path $integrationDirectory 'TEST-lab.backend.learning.delivery.DeliveryStoreIT.xml'
    admission=Join-Path $integrationDirectory 'TEST-lab.backend.learning.identityaccess.AdmissionGateIT.xml'
    publication=Join-Path $integrationDirectory 'TEST-lab.backend.learning.contentreplica.PublicationStoreIT.xml'
  }
  $dbCases=@('stageFenceReleaseAreAtomicDurableAndExactRetryConverges','changedRetryOutOfOrderAndFailureLeaveLastGoodReplicaUntouched','twoConnectionStageRaceCommitsOneReplicaAndOneReceipt','durableFenceReturnsPublicationPendingAfterStoreRestartAndAbortKeepsHead','appRoleCannotMutateImmutableRowsOrSecurityGeneration')
  Write-CaseFixture (Join-Path $unitDirectory 'TEST-fixture.xml') @('fixture');foreach($key in @('database','delivery','admission')){Write-CaseFixture $dbReports[$key] @("$key-fixture")}
  Check-Command 'R06b DB missing publication suite rejected' $helper $dbArgs 1 'required service DB suite missing'
  foreach($missing in $dbCases){Write-CaseFixture $dbReports.publication @($dbCases|Where-Object {$_ -cne $missing});Check-Command "R06b DB missing named case rejected: $missing" $helper $dbArgs 1 'Required R06b DB case missing'}
  foreach($bad in @(
    @{Name='zero';Xml='<testsuite tests="0" skipped="0" failures="0" errors="0"/>';Text='ran zero tests'},
    @{Name='duplicate';Xml='<testsuite tests="2" skipped="0" failures="0" errors="0"><testcase name="stageFenceReleaseAreAtomicDurableAndExactRetryConverges"/><testcase name="stageFenceReleaseAreAtomicDurableAndExactRetryConverges"/></testsuite>';Text='Invalid R06b publication DB case report'},
    @{Name='hidden failure';Xml=('<testsuite tests="5" skipped="0" failures="0" errors="0"><testcase name="'+$dbCases[0]+'"><failure/></testcase>'+((@($dbCases|Select-Object -Skip 1)|ForEach-Object {'<testcase name="'+$_+'"/>'})-join '')+'</testsuite>');Text='Invalid R06b publication DB case report'}
  )){$bad.Xml|Set-Content -LiteralPath $dbReports.publication -Encoding utf8;Check-Command "R06b DB $($bad.Name) rejected" $helper $dbArgs 1 $bad.Text}
  Write-CaseFixture $dbReports.publication $dbCases;Check-Command 'R06b complete DB reports accepted' $helper $dbArgs 0 'CMD-03 learning completed'

  Get-ChildItem $unitDirectory,$integrationDirectory -Filter 'TEST-*.xml' -File|Remove-Item -Force;Write-R06bUnits
  $runtime=Join-Path $integrationDirectory 'TEST-lab.backend.learning.contentreplica.PublicationRuntimeIT.xml'
  $runtimeCases=@('packagedStageFenceReleaseStatusAndLostAckRetriesConverge','privateProviderRejectsPublicWrongCallerHeadersRoutesAndCertificatesBeforeEffect','invalidPrivateBundleAndContentOutageLeaveLastGoodReplicaUntouched')
  Write-CaseFixture $runtime $runtimeCases
  foreach($missing in $unitNames){Remove-Item (Join-Path $unitDirectory "TEST-lab.backend.learning.contentreplica.$missing.xml");Check-Command "R06b contract missing unit suite rejected: $missing" $helper $contractArgs 1 'publication unit suite missing';Write-CaseFixture (Join-Path $unitDirectory "TEST-lab.backend.learning.contentreplica.$missing.xml") @("$missing-fixture")}
  foreach($missing in $runtimeCases){Write-CaseFixture $runtime @($runtimeCases|Where-Object {$_ -cne $missing});Check-Command "R06b contract missing runtime case rejected: $missing" $helper $contractArgs 1 'Invalid R06b publication runtime case report'}
  Write-CaseFixture $runtime @($runtimeCases+$runtimeCases[0]);Check-Command 'R06b contract duplicate runtime rejected' $helper $contractArgs 1 'Invalid R06b publication runtime case report'
  ('<testsuite tests="3" skipped="0" failures="0" errors="0"><testcase name="'+$runtimeCases[0]+'"><failure/></testcase><testcase name="'+$runtimeCases[1]+'"/><testcase name="'+$runtimeCases[2]+'"/></testsuite>')|Set-Content -LiteralPath $runtime -Encoding utf8;Check-Command 'R06b contract hidden runtime failure rejected' $helper $contractArgs 1 'Invalid R06b publication runtime case report'
  Write-CaseFixture $runtime $runtimeCases;Check-Command 'R06b complete contract reports accepted' $helper $contractArgs 0 'contract learning completed: integration=3; unit/bootstrap=4'
  $faultMap=@{'DATA-01'=$runtimeCases[2];'DATA-02'=$runtimeCases[0];'SEC-16'=$runtimeCases[1]}
  foreach($selector in $faultMap.Keys|Sort-Object){Write-CaseFixture $runtime @($faultMap[$selector]);$args=@('-Task','fault','-Service','learning','-EvidenceTask','R06b','-Case',$selector,'-LabConfig',$labConfig,'-TrustConfig',$trustConfig)+$javaArguments;Check-Command "R06b selector $selector exact report accepted" $helper $args 0 'fault learning completed: integration=1; unit/bootstrap=4';$captured=@(Get-Content (Join-Path $testRoot 'scripts/native-arguments.jsonl')|ForEach-Object {ConvertFrom-Json $_});$selectors=@($captured[-1].arguments|Where-Object {$_ -like '-Dit.test=*'});$expected="-Dit.test=PublicationRuntimeIT#$($faultMap[$selector])";if($selectors.Count -ne 1 -or $selectors[0] -cne $expected){throw "R06b native selector changed: $selector"};Write-CaseFixture $runtime $runtimeCases;Check-Command "R06b selector $selector rejects unselected report" $helper $args 1 'Invalid R06b publication runtime case report'}
  $evidenceDirectory=Join-Path $repoDirectory 'artifacts/quality/R06b';New-Item -ItemType Directory -Path $evidenceDirectory -Force|Out-Null
  $inputHashes=@{};foreach($input in @('scripts/services.ps1','scripts/db_lab.py','scripts/tests/backend-build.Tests.ps1')){$inputHashes[$input]=(Get-FileHash (Join-Path $repoDirectory $input) -Algorithm SHA256).Hash.ToLowerInvariant()}
  @{status='PASS';scope='R06b helper/report gates with isolated command/XML doubles; not actual DB/application/mTLS proof';caseCount=$results.Count;cases=$results;inputHashes=$inputHashes;fixtureDirectory=$testRoot}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $evidenceDirectory 'helper-publication-tests.json') -Encoding utf8
  Write-Host "R06b helper publication tests passed: $($results.Count).";exit 0
}
if($PublicationOnly) {
  # Report doubles dưới đây chỉ kiểm cổng command/XML. Actual PG/mTLS/CLI vẫn
  # phải chạy bằng CMD-03/CMD-04 sau khi source đã freeze.
  $contentRoot=Join-Path $testRoot 'backend/services/content-service'
  New-Item -ItemType Directory -Path "$contentRoot/target/surefire-reports","$contentRoot/target/failsafe-reports" -Force | Out-Null
  '<project/>' | Set-Content -LiteralPath "$contentRoot/pom.xml" -Encoding utf8
  (Get-Content -LiteralPath $fixturePom -Raw).Replace('</modules>','<module>services/content-service</module></modules>') | Set-Content -LiteralPath $fixturePom -Encoding utf8
  $labDirectory=Join-Path $testRoot '.tools/db-lab/run-publication-fixture'
  $trustDirectory=Join-Path $testRoot '.tools/trust-lab/run-publication-fixture'
  New-Item -ItemType Directory -Path $labDirectory,$trustDirectory -Force | Out-Null
  $labConfig=Join-Path $labDirectory 'test-credentials.properties'
  $trustConfig=Join-Path $trustDirectory 'content.properties'
  'fixture=true' | Set-Content -LiteralPath $labConfig -Encoding ascii
  'fixture=true' | Set-Content -LiteralPath $trustConfig -Encoding ascii
  $labMarker=@{schemaVersion=1;runId=[Guid]::NewGuid().ToString();workspace=$testRoot;dataDirectory=(Join-Path $labDirectory 'data')}
  $trustMarker=@{schemaVersion=1;runId=[Guid]::NewGuid().ToString();workspace=$testRoot}
  $labMarker | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $labDirectory 'ownership.json') -Encoding utf8
  $trustMarker | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $trustDirectory 'ownership.json') -Encoding utf8
  $publicationDbArguments=@('-Task','db','-Service','content','-EvidenceTask','R06a','-LabConfig',$labConfig)+$javaArguments
  $publicationContractArguments=@('-Task','contract','-Service','content','-EvidenceTask','R06a','-LabConfig',$labConfig,'-TrustConfig',$trustConfig)+$javaArguments

  foreach($badInvocation in @(
    @{Name='R06a missing service rejected';Args=@('-Task','db','-EvidenceTask','R06a');Text='only Content'},
    @{Name='R06a wrong Identity service rejected';Args=@('-Task','db','-Service','identity','-EvidenceTask','R06a');Text='only Content'},
    @{Name='R06a wrong Learning service rejected';Args=@('-Task','contract','-Service','learning','-EvidenceTask','R06a');Text='only Content'},
    @{Name='R06a all service rejected';Args=@('-Task','db','-Service','all','-EvidenceTask','R06a');Text='only Content'},
    @{Name='R06a verify task rejected';Args=@('-Task','verify','-Service','content','-EvidenceTask','R06a');Text='only Content'},
    @{Name='R06a fault task rejected';Args=@('-Task','fault','-Service','content','-EvidenceTask','R06a');Text='only Content'},
    @{Name='R06a relay task rejected';Args=@('-Task','relay','-Service','content','-EvidenceTask','R06a');Text='only Content'},
    @{Name='R06a selected case rejected';Args=$publicationContractArguments+@('-Case','SEC-02');Text='does not accept selected'},
    @{Name='R06a TrustConfig without LabConfig rejected';Args=@('-Task','contract','-Service','content','-EvidenceTask','R06a','-TrustConfig',$trustConfig);Text='without its owned LabConfig'},
    @{Name='R06a DB rejects ignored TrustConfig';Args=$publicationDbArguments+@('-TrustConfig',$trustConfig);Text='requires contract/fault/relay'}
  )) { Check-Command $badInvocation.Name $helper $badInvocation.Args 1 $badInvocation.Text }
  Check-Command 'R06a contract requires trust config' $helper (@('-Task','contract','-Service','content','-EvidenceTask','R06a','-LabConfig',$labConfig)+$javaArguments) 1 'Required trust config missing'
  $unownedConfig=Join-Path $testRoot 'unowned.properties';'fixture=true' | Set-Content -LiteralPath $unownedConfig -Encoding ascii
  Check-Command 'R06a rejects unowned DB config' $helper (@('-Task','db','-Service','content','-EvidenceTask','R06a','-LabConfig',$unownedConfig)+$javaArguments) 1 'owned workspace .tools/db-lab'
  Check-Command 'R06a rejects unowned trust config' $helper (@('-Task','contract','-Service','content','-EvidenceTask','R06a','-LabConfig',$labConfig,'-TrustConfig',$unownedConfig)+$javaArguments) 1 'inside owned .tools/trust-lab'
  $badLabMarker=$labMarker.Clone();$badLabMarker.workspace='wrong-workspace'
  $badLabMarker | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $labDirectory 'ownership.json') -Encoding utf8
  Check-Command 'R06a rejects mismatched DB ownership marker' $helper $publicationDbArguments 1 'ownership marker mismatch'
  $labMarker | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $labDirectory 'ownership.json') -Encoding utf8

  function Check-PublicationParserFailure([string]$Name,[string[]]$Arguments,[string]$ExpectedText) {
    $python=(Get-Command python -ErrorAction Stop).Source
    $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=$python;$info.WorkingDirectory=$testRoot
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true;$info.CreateNoWindow=$true
    foreach($argument in @('-X','utf8',(Join-Path $testRoot 'scripts/db_lab.py'))+$Arguments){$info.ArgumentList.Add($argument)}
    $child=[Diagnostics.Process]::new();$child.StartInfo=$info
    if(-not $child.Start()){throw "Failed to start parser probe: $Name"}
    $stdout=$child.StandardOutput.ReadToEndAsync();$stderr=$child.StandardError.ReadToEndAsync()
    if(-not $child.WaitForExit($fixtureCommandTimeoutMs)){$child.Kill($true);throw "Parser probe timeout: $Name"}
    $output=$stdout.GetAwaiter().GetResult()+$stderr.GetAwaiter().GetResult()
    if($child.ExitCode -eq 0 -or $output -notmatch $ExpectedText){throw "Parser probe failed: $Name exit=$($child.ExitCode) output=$output"}
    $results.Add([pscustomobject]@{case=$Name;exit=$child.ExitCode;result='PASS'});$child.Dispose();Write-Host "PASS: $Name"
  }
  Check-PublicationParserFailure 'db_lab R06a wrong service rejected' @('--service','identity','--task','db','--evidence-task','R06a') 'only Content'
  Check-PublicationParserFailure 'db_lab R06a wrong task rejected' @('--service','content','--task','relay','--evidence-task','R06a') 'only Content'
  Check-PublicationParserFailure 'db_lab R06a case rejected' @('--service','content','--task','contract','--case','SEC-02','--evidence-task','R06a') 'full named'
  Check-PublicationParserFailure 'db_lab R06a fail-before-build rejected' @('--service','content','--task','db','--evidence-task','R06a','--fail-before-build') 'R02a cleanup fixture'

  $unitDirectory="$contentRoot/target/surefire-reports"
  $integrationDirectory="$contentRoot/target/failsafe-reports"
  $deliveryWire=Join-Path $unitDirectory 'TEST-lab.backend.content.delivery.DeliveryWireTests.xml'
  $databaseReport=Join-Path $integrationDirectory 'TEST-lab.backend.content.database.DatabaseIsolationIT.xml'
  $deliveryReport=Join-Path $integrationDirectory 'TEST-lab.backend.content.delivery.DeliveryStoreIT.xml'
  $admissionReport=Join-Path $integrationDirectory 'TEST-lab.backend.content.identityaccess.AdmissionGateIT.xml'
  Write-CaseFixture $deliveryWire @('fixtureCase');Write-CaseFixture $deliveryReport @('fixtureCase');Write-CaseFixture $admissionReport @('fixtureCase')
  Check-Command 'R06a DB missing publication suite rejected' $helper $publicationDbArguments 1 'required service DB suite missing'
  $publicationDbCases=@('publicationDbFaultRollsBackEveryWriteAndKeepsGoodHead','publicationDifferentOperationsSerializeAndReplacementAfterReleasedReservationCanPrepare','publicationExactRetryDetectsCandidateAndManifestConflictAndKeepsGoodHead','publicationRuntimeRoleCannotMutateImmutableRowsHeadOrReservation')
  foreach($missingCase in $publicationDbCases){
    Write-CaseFixture $databaseReport @($publicationDbCases | Where-Object {$_ -cne $missingCase})
    Check-Command "R06a DB missing named case rejected: $missingCase" $helper $publicationDbArguments 1 'Required R06a DB case missing'
  }
  foreach($badReport in @(
    @{Name='zero';Xml='<testsuite tests="0" skipped="0" failures="0" errors="0"/>';Text='ran zero tests'},
    @{Name='skip';Xml='<testsuite tests="1" skipped="1" failures="0" errors="0"><testcase name="x"><skipped/></testcase></testsuite>';Text='did not fully run'},
    @{Name='failure';Xml='<testsuite tests="1" skipped="0" failures="1" errors="0"><testcase name="x"><failure/></testcase></testsuite>';Text='did not fully run'},
    @{Name='error';Xml='<testsuite tests="1" skipped="0" failures="0" errors="1"><testcase name="x"><error/></testcase></testsuite>';Text='did not fully run'},
    @{Name='malformed';Xml='<testsuite tests="NaN" skipped="0" failures="0" errors="0"/>';Text='Invalid JUnit report'},
    @{Name='count mismatch';Xml='<testsuite tests="5" skipped="0" failures="0" errors="0"><testcase name="publicationDbFaultRollsBackEveryWriteAndKeepsGoodHead"/></testsuite>';Text='Invalid R06a publication DB case report'},
    @{Name='duplicate';Xml='<testsuite tests="2" skipped="0" failures="0" errors="0"><testcase name="publicationDbFaultRollsBackEveryWriteAndKeepsGoodHead"/><testcase name="publicationDbFaultRollsBackEveryWriteAndKeepsGoodHead"/></testsuite>';Text='Invalid R06a publication DB case report'},
    @{Name='hidden failure';Xml='<testsuite tests="4" skipped="0" failures="0" errors="0"><testcase name="publicationDbFaultRollsBackEveryWriteAndKeepsGoodHead"><failure/></testcase><testcase name="publicationDifferentOperationsSerializeAndReplacementAfterReleasedReservationCanPrepare"/><testcase name="publicationExactRetryDetectsCandidateAndManifestConflictAndKeepsGoodHead"/><testcase name="publicationRuntimeRoleCannotMutateImmutableRowsHeadOrReservation"/></testsuite>';Text='Invalid R06a publication DB case report'}
  )){$badReport.Xml | Set-Content -LiteralPath $databaseReport -Encoding utf8;Check-Command "R06a DB $($badReport.Name) report rejected" $helper $publicationDbArguments 1 $badReport.Text}
  Write-CaseFixture $databaseReport $publicationDbCases
  Check-Command 'R06a complete named DB reports accepted' $helper $publicationDbArguments 0 'DB=6; bootstrap=1'
  $dbNative=@(Get-Content -LiteralPath (Join-Path $testRoot 'scripts/native-arguments.jsonl') | ForEach-Object {ConvertFrom-Json $_})
  if($dbNative.Count -ne 2 -or $dbNative[0].arguments -notcontains '-Pdb-it' -or $dbNative[0].arguments -notcontains 'clean' -or $dbNative[1].arguments -notcontains '-Pdelivery-it' -or $dbNative[1].arguments -contains 'clean'){throw 'R06a DB native profile/phase order mismatch'}

  Get-ChildItem -LiteralPath $unitDirectory,$integrationDirectory -Filter 'TEST-*.xml' -File | ForEach-Object {Remove-Item -LiteralPath $_.FullName}
  $validatorReport=Join-Path $unitDirectory 'TEST-lab.backend.content.publication.CandidateValidatorTests.xml'
  $runtimeReport=Join-Path $integrationDirectory 'TEST-lab.backend.content.publication.PublicationRuntimeIT.xml'
  $publicationValidatorCases=@('validCandidatePreservesInputAndUsesR05PrivateCaseContract','reviewSubjectBindsEverySemanticMetadataFieldAndValidatesReportIds','manifestSupportsMultipleVersionsAndRejectsEveryPolicyContradiction','referencesDagAndPrivatePayloadBindingsFailClosed','unknownDuplicateMalformedUtf8AndBoundsFailClosed','executableJsonSchemaMatchesStrictJavaStructuralCorpus')
  $publicationRuntimeCases=@('exactBundleAndManifestResourcesReturnStoredBytesAndBoundedMetadata','largestValidManifestUsesBodyResourceAndOversizeIsRejected','unknownUnvalidatedAndCorruptStoredRowsFailClosed','publicWrongCallerHeadersMethodQueryAndAmbiguousPathFailClosed','wrongCaEkuSanEnvironmentAndNoCertificateCannotReachPayload','packagedCliValidatesPreparesExactRetryAndRejectsChecksumMismatch','packagedCliRejectsDatabaseFaultAndConflictingRetryAtomically')
  Write-CaseFixture $runtimeReport $publicationRuntimeCases
  Write-CaseFixture (Join-Path $unitDirectory 'TEST-unrelated.xml') @('unrelated')
  Check-Command 'R06a contract missing validator suite rejected' $helper $publicationContractArguments 1 'validator suite missing'
  Remove-Item -LiteralPath (Join-Path $unitDirectory 'TEST-unrelated.xml');Write-CaseFixture $validatorReport $publicationValidatorCases;Remove-Item -LiteralPath $runtimeReport
  Check-Command 'R06a contract missing runtime suite rejected' $helper $publicationContractArguments 1 'Required trust/wire suite missing'
  Write-CaseFixture $runtimeReport $publicationRuntimeCases
  foreach($missingCase in $publicationValidatorCases){Write-CaseFixture $validatorReport @($publicationValidatorCases | Where-Object {$_ -cne $missingCase});Check-Command "R06a validator missing case rejected: $missingCase" $helper $publicationContractArguments 1 'Invalid R06a validator case report'}
  Write-CaseFixture $validatorReport $publicationValidatorCases
  foreach($missingCase in $publicationRuntimeCases){Write-CaseFixture $runtimeReport @($publicationRuntimeCases | Where-Object {$_ -cne $missingCase});Check-Command "R06a runtime missing case rejected: $missingCase" $helper $publicationContractArguments 1 'Invalid R06a publication runtime case report'}
  foreach($suite in @(@{Name='validator';Path=$validatorReport;Cases=$publicationValidatorCases},@{Name='runtime';Path=$runtimeReport;Cases=$publicationRuntimeCases})){
    $otherPath=if($suite.Name -eq 'validator'){$runtimeReport}else{$validatorReport};$otherCases=if($suite.Name -eq 'validator'){$publicationRuntimeCases}else{$publicationValidatorCases}
    $strictReportLabel=if($suite.Name -eq 'validator'){'validator case'}else{'publication runtime case'}
    foreach($bad in @(
      @{Name='zero';Xml='<testsuite tests="0" skipped="0" failures="0" errors="0"/>';Text='ran zero tests'},
      @{Name='skip';Xml='<testsuite tests="1" skipped="1" failures="0" errors="0"><testcase name="x"><skipped/></testcase></testsuite>';Text='did not fully run'},
      @{Name='failure';Xml='<testsuite tests="1" skipped="0" failures="1" errors="0"><testcase name="x"><failure/></testcase></testsuite>';Text='did not fully run'},
      @{Name='error';Xml='<testsuite tests="1" skipped="0" failures="0" errors="1"><testcase name="x"><error/></testcase></testsuite>';Text='did not fully run'},
      @{Name='malformed';Xml='<testsuite tests="NaN" skipped="0" failures="0" errors="0"/>';Text='Invalid JUnit report'}
    )){Write-CaseFixture $otherPath $otherCases;$bad.Xml | Set-Content -LiteralPath $suite.Path -Encoding utf8;Check-Command "R06a $($suite.Name) $($bad.Name) report rejected" $helper $publicationContractArguments 1 $bad.Text}
    Write-CaseFixture $otherPath $otherCases
    Write-CaseFixture $suite.Path @($suite.Cases+$suite.Cases[0]);Check-Command "R06a $($suite.Name) duplicate report rejected" $helper $publicationContractArguments 1 "Invalid R06a $strictReportLabel report"
    $hiddenFailureCases=@($suite.Cases | ForEach-Object {
      if($_ -ceq $suite.Cases[0]){'<testcase name="'+$_+'"><failure/></testcase>'}else{'<testcase name="'+$_+'"/>'}
    }) -join ''
    ('<testsuite tests="'+$suite.Cases.Count+'" skipped="0" failures="0" errors="0">'+$hiddenFailureCases+'</testsuite>') | Set-Content -LiteralPath $suite.Path -Encoding utf8
    Check-Command "R06a $($suite.Name) hidden failure rejected" $helper $publicationContractArguments 1 "Invalid R06a $strictReportLabel report"
    Write-CaseFixture $suite.Path $suite.Cases
  }
  Write-CaseFixture $validatorReport $publicationValidatorCases;Write-CaseFixture $runtimeReport $publicationRuntimeCases
  Check-Command 'R06a complete validator/runtime reports accepted' $helper $publicationContractArguments 0 'integration=7; unit/bootstrap=6'
  $contractNative=@(Get-Content -LiteralPath (Join-Path $testRoot 'scripts/native-arguments.jsonl') | ForEach-Object {ConvertFrom-Json $_})
  if($contractNative.Count -ne 1 -or $contractNative[0].arguments -notcontains '-Ptrust-it' -or $contractNative[0].arguments -notcontains '-Dtest=CandidateValidatorTests' -or $contractNative[0].arguments -notcontains '-Dit.test=PublicationRuntimeIT' -or $contractNative[0].arguments -contains '-Pdb-it'){throw 'R06a contract native profile/selector mismatch'}

  $evidenceDirectory=Join-Path $repoDirectory 'artifacts/quality/R06a';New-Item -ItemType Directory -Path $evidenceDirectory -Force | Out-Null
  $inputHashes=@{};foreach($input in @('scripts/services.ps1','scripts/db_lab.py','scripts/tests/backend-build.Tests.ps1')){$inputHashes[$input]=(Get-FileHash -LiteralPath (Join-Path $repoDirectory $input) -Algorithm SHA256).Hash.ToLowerInvariant()}
  @{status='PASS';scope='R06a helper/report gates with isolated command/XML doubles; not actual DB/application/mTLS proof';publicationOnly=$true;fixtureCommandTimeoutMs=$fixtureCommandTimeoutMs;caseCount=$results.Count;cases=$results;inputHashes=$inputHashes;fixtureDirectory=$testRoot} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'helper-publication-tests.json') -Encoding utf8
  Write-Host "Publication helper tests passed: $($results.Count). Evidence R06a."
  exit 0
}
if (-not ($RelayOnly -or $CanonicalOnly)) {
Check-Command 'Service selection required' $helper @('-Task','verify') 1 'verify requires -Service'
Check-Command 'Missing module fails before build' $helper @('-Task','verify','-Service','learning') 1 'has not been created'
Check-Command 'Unknown service rejected' $helper @('-Task','verify','-Service','../identity') 1 'ValidateSet'
Check-Command 'Unavailable suite rejected' $helper @('-Task','journal','-Service','identity') 1 'ValidateSet'
Check-Command 'DB service required' $helper @('-Task','db') 1 'db requires -Service'
Check-Command 'Unknown DB selector rejected' $helper @('-Task','db','-Service','unknown') 1 'ValidateSet'
Check-Command 'Selected verify cannot use all' $helper @('-Task','verify','-Service','all') 1 'all is only valid'
Check-Command 'Parent validate cannot claim selected service' $helper @('-Task','validate','-Service','identity') 1 'only valid with -Task verify'
Check-Command 'Invalid JavaHome fails' $helper @('-Task','toolchain','-JavaHome',(Join-Path $testRoot 'missing-jdk')) 1 'does not exist|Cannot find path'
if ($UnsupportedJavaHome) {
  Check-Command 'Java outside 21 rejected' $helper @('-Task','toolchain','-JavaHome',$UnsupportedJavaHome) 1 'not JDK 21'
}
Check-Command 'Missing test reports rejected' $helper (@('-Task','verify','-Service','identity')+$javaArguments) 1 'no JUnit reports'
foreach ($fixture in @(
  @{Name='Zero tests rejected';Xml='<testsuite tests="0" skipped="0" failures="0" errors="0"/>';Exit=1;Text='zero tests'},
  @{Name='Required skip rejected';Xml='<testsuite tests="1" skipped="1" failures="0" errors="0"/>';Exit=1;Text='did not fully run'},
  @{Name='Failed tests rejected despite command exit zero';Xml='<testsuite tests="1" skipped="0" failures="1" errors="0"/>';Exit=1;Text='did not fully run'},
  @{Name='Malformed report rejected';Xml='<testsuite tests="unknown" skipped="0" failures="0" errors="0"/>';Exit=1;Text='Invalid JUnit report'},
  @{Name='Complete report accepted by helper';Xml='<testsuite tests="2" skipped="0" failures="0" errors="0"/>';Exit=1;Text='delivery wire suite missing'}
)) {
  $fixture.Xml | Set-Content -LiteralPath $reportPath -Encoding utf8
  Check-Command $fixture.Name $helper (@('-Task','verify','-Service','identity')+$javaArguments) $fixture.Exit $fixture.Text
}
$deliveryUnit = Join-Path $reportDirectory 'TEST-lab.backend.identity.delivery.DeliveryWireTests.xml'
'<testsuite tests="1" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath $deliveryUnit -Encoding utf8
Check-Command 'Missing delivery config suite rejected' $helper (@('-Task','verify','-Service','identity')+$javaArguments) 1 'delivery config suite missing'
'<testsuite tests="1" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath (Join-Path $reportDirectory 'TEST-lab.backend.identity.delivery.DeliveryRuntimeConfigTests.xml') -Encoding utf8
Check-Command 'Missing canonical wire suite rejected despite legacy unit reports' $helper (@('-Task','verify','-Service','identity')+$javaArguments) 1 'canonical admission wire suite missing'
$canonicalUnit=Join-Path $reportDirectory 'TEST-lab.backend.identity.session.CanonicalAdmissionWireTests.xml'
'<testsuite tests="1" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath $canonicalUnit -Encoding utf8
# Một suite scanner thiếu vẫn phải fail dù parser đã có; report double này
# không là proof cursor SQL chạy thật hoặc worker chống starvation trên PG.
Check-Command 'Missing pending scanner rejected despite canonical parser' $helper (@('-Task','verify','-Service','identity')+$javaArguments) 1 'pending work scanner suite missing'
'<testsuite tests="1" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath (Join-Path $reportDirectory 'TEST-lab.backend.identity.session.AuthPendingWorkTests.xml') -Encoding utf8
# Missing adapter phải fail từng suite riêng dù legacy/parser units đều xanh.
foreach($adapterSuite in @('ReauthReservationWireTests','CanonicalAuthHttpHandlerTests','IdentitySensitiveIntentTests')) {
  Check-Command "Missing canonical adapter rejected: $adapterSuite" $helper (@('-Task','verify','-Service','identity')+$javaArguments) 1 "canonical adapter suite missing: $adapterSuite"
  '<testsuite tests="1" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath (Join-Path $reportDirectory "TEST-lab.backend.identity.session.$adapterSuite.xml") -Encoding utf8
}
Check-Command 'Complete unit suites including canonical parser and adapters accepted' $helper (@('-Task','verify','-Service','identity')+$javaArguments) 0 '9 tests; no required skips'
Check-Command 'R04d runtime rejects non-driver service' $helper (@('-Task','contract','-Service','identity','-EvidenceTask','R04d')+$javaArguments) 1 'contract Learning canonical driver'
$unrelatedReport = Join-Path $reportDirectory 'TEST-unrelated.xml'
Move-Item -LiteralPath $reportPath -Destination $unrelatedReport
Check-Command 'Missing HTTP adapter suite rejected despite other passing unit tests' $helper (@('-Task','verify','-Service','identity')+$javaArguments) 1 'HTTP adapter suite missing'
Move-Item -LiteralPath $unrelatedReport -Destination $reportPath
$labDirectory = Join-Path $testRoot '.tools/db-lab/run-fixture'
New-Item -ItemType Directory -Path $labDirectory -Force | Out-Null
$labConfig = Join-Path $labDirectory 'test-credentials.properties'
'fixture=true' | Set-Content -LiteralPath $labConfig -Encoding ascii
@{schemaVersion=1;runId=[Guid]::NewGuid().ToString();workspace=$testRoot;dataDirectory=(Join-Path $labDirectory 'data')} |
  ConvertTo-Json | Set-Content -LiteralPath (Join-Path $labDirectory 'ownership.json') -Encoding utf8
$dbArguments = @('-Task','db','-Service','identity','-LabConfig',$labConfig)+$javaArguments
Check-Command 'Missing required DB suite rejected' $helper $dbArguments 1 'required service DB suite missing'
$dbReportDirectory = Join-Path $testRoot 'backend/services/identity-service/target/failsafe-reports'
New-Item -ItemType Directory -Path $dbReportDirectory -Force | Out-Null
$dbReportPath = Join-Path $dbReportDirectory 'TEST-lab.backend.identity.database.DatabaseIsolationIT.xml'
$credentialReport = Join-Path $dbReportDirectory 'TEST-lab.backend.identity.credential.IdentityCredentialWorkflowIT.xml'
$packagedReport = Join-Path $dbReportDirectory 'TEST-lab.backend.identity.credential.CredentialPackagedIT.xml'
$sessionReport = Join-Path $dbReportDirectory 'TEST-lab.backend.identity.credential.IdentitySessionAuthorityIT.xml'
$deliveryReport = Join-Path $dbReportDirectory 'TEST-lab.backend.identity.delivery.DeliveryStoreIT.xml'
foreach ($path in @($credentialReport,$packagedReport)) {
  '<testsuite tests="1" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath $path -Encoding utf8
}
'<testsuite tests="3" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath $dbReportPath -Encoding utf8
Check-Command 'Missing session suite rejected despite DB credentials reports' $helper $dbArguments 1 'required service DB suite missing'
'<testsuite tests="1" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath $sessionReport -Encoding utf8
Check-Command 'Missing delivery DB suite rejected despite legacy suites' $helper $dbArguments 1 'required service DB suite missing'
'<testsuite tests="1" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath $deliveryReport -Encoding utf8
# Mỗi case thiếu phải bị helper reject riêng; đây là report double kiểm gate,
# không thay việc chạy Java/PG canonical authority. Giữ tên khớp suite thật.
$canonicalCases=@('canonicalReadBindsFreshNonceOwnerSessionRealmAndDeadlinesWithoutIdleTouch','canonicalDraftSaveVerifiesSynchronizerAndTouchesOnlyItsOwnSession','canonicalBadCsrfAndContextDoNotTouchIdleOrCreateOperation','canonicalUnknownCookieAndPreauthCannotCreateLearnerAdmission','canonicalExpiredMutationCommitsDenyAndCannotRepeatExpiryIntents','canonicalAbsoluteExpiryAndOrphanBackingAreDeniedDurably','canonicalLogoutFencePendingAlreadyDeniesAdmissionWithoutWaitingForAck','canonicalShortIdleBudgetDeniesReadWithoutRenewingIt','canonicalWrongRealmFailsClosedWithoutTouchOrLocalExpiry','canonicalBlockedAdmissionRereadsCommittedAccountTransition','canonicalExpiryIntentFailureRollsBackSessionAndOperation')
$canonicalCases+=@('canonicalReadDeadlineIsCappedByIdleAndAbsoluteIndependently','canonicalMutationInsufficientAbsoluteBudgetRollsBackIdleTouch')
$canonicalCases+=@('barrierLogoutAtomicCommandsAndBothAcksAreRequired','barrierSecondOutboxFailureRollsBackCanonicalDeny','barrierLegacyReconciliationPreservesOperationAndExactBytes','barrierPendingCannotClearOrCompleteByDirectSql','barrierResetCurrentEpochCompletesAndFreshLoginUsesNewEpoch','barrierOlderResetAckNeverClearsNewerTransition','barrierCurrentGenerationChangeRejectsOldAckWithoutRetargeting','barrierCompletionFailureRollsBackOperationAndCanonicalSession','barrierTwoConcurrentCompletersProduceOneDurableEffect','barrierLegacyCredentialResetReconciliationIsImmutableAndDigestOnly','barrierDisableAndEnableAuthCompletionKeepLifecycleGate','barrierRuntimeVerificationCannotRepairMissingCompletionGuard')
$canonicalCases+='barrierResetAfterEnableCannotBypassPendingLifecycle'
# Các cursor pha khác nhau đều phải có actual own-PG poison-batch proof.
$canonicalCases+=@('barrierPendingTransitionScanPassesSeventeenPoisonIds','barrierPendingReconcileScanPassesSeventeenPoisonIds','barrierPendingCompletionScanPassesSeventeenPoisonIds')
# Regression flow/expiry mới phải reject độc lập khi vắng, không suy từ suite cũ.
$canonicalCases+=@('barrierCompletedLogoutAllowsFreshFlowAndOldRetryCannotRevokeNewSession','barrierTerminalSessionWithoutCompletedFenceCannotCreateFreshFlow','barrierExpiryCommitsTwoCommandsAndCompletedFenceAllowsFreshFlow')
# Missing proof/race/rollback case phải fail riêng, không count thay coverage.
$canonicalCases+=@('reauthDigestOnlySameIntentReservationHasFixedReferenceAndTtl','reauthCallerAndEveryIntentBindingAreRequired','reauthBadCsrfAndContextCannotReachKdfOrThrottle','reauthWrongPasswordHasDedicatedThrottleAndCannotMint','reauthMintFailureRollsBackGrantAndIdle','reauthReservationRollbackAndRestartCannotRefundOrRetarget','reauthExpiredGrantAndChangedEpochAreDenied','reauthKdfLogoutRaceCannotMintFromOldSession','reauthRuntimeGuardAndImmutablePrivilegesFailClosed','reauthConcurrentSameIntentHasOneReservation','reauthKdfEpochChangeRaceCannotMintFromOldCredential','reauthKdfExpiryRaceCommitsDenyWithoutMintingProof','reauthConcurrentCapacityCannotOvershootGlobalBound')
# Missing sensitive atomic/race case phải fail riêng, không suy từ reset proof.
$canonicalCases+=@('sensitiveLogoutAllAtomicallyDeniesAllSessionsAndRetriesWithoutIdleOrCookieEffect','sensitivePasswordChangeBindsPayloadAndHashesOnlyOneDurableEffect','sensitiveEveryBindingAndLearningPurposeAreRequired','sensitiveSecondOutboxFailureRollsBackProofHashEpochTokensAndDeny','sensitiveAuditFailureRollsBackConsumeAndOutboxes','sensitiveConcurrentConsumeAndRestartReturnOneImmutableOperation','sensitiveDirectConsumeCannotCommitWithoutAtomicEffect','sensitiveMissingDeferredGuardAndChangedStorageKeyFailClosed','sensitivePasswordKdfResetRaceCannotConsumeOldProof','sensitiveExpiredProofAndCrossPurposeReservationCannotMutate')
foreach($missingCanonical in $canonicalCases) {
  Write-CaseFixture $sessionReport @($canonicalCases | Where-Object { $_ -ne $missingCanonical })
  Check-Command "Missing canonical case rejected: $missingCanonical" $helper $dbArguments 1 'Required canonical own-DB case missing'
}
Write-CaseFixture $sessionReport $canonicalCases
foreach ($fixture in @(
  @{Name='Zero DB tests rejected despite unit tests';Xml='<testsuite tests="0" skipped="0" failures="0" errors="0"/>';Exit=1;Text='suite ran zero tests'},
  @{Name='Required DB skip rejected';Xml='<testsuite tests="1" skipped="1" failures="0" errors="0"/>';Exit=1;Text='did not fully run'},
  @{Name='Failed DB suite rejected';Xml='<testsuite tests="1" skipped="0" failures="1" errors="0"/>';Exit=1;Text='did not fully run'},
  @{Name='Malformed DB report rejected';Xml='<testsuite tests="unknown" skipped="0" failures="0" errors="0"/>';Exit=1;Text='Invalid JUnit report'},
  @{Name='Complete DB report accepted by helper';Xml='<testsuite tests="3" skipped="0" failures="0" errors="0"/>';Exit=0;Text='DB=61; bootstrap=9'}
)) {
  $fixture.Xml | Set-Content -LiteralPath $dbReportPath -Encoding utf8
  Check-Command $fixture.Name $helper $dbArguments $fixture.Exit $fixture.Text
}
# Core fault chọn full own-DB bundle, giữ cả canonical clocks/KDF và delivery
# regression; không rơi về credential-only hoặc trust từ Case có cùng tên.
foreach($coreSelector in @('SEC-10','SEC-12')) {
  $coreArguments=@('-Task','fault','-Service','identity','-EvidenceTask','R04d','-Case',$coreSelector,'-LabConfig',$labConfig)+$javaArguments
  Check-Command "Canonical own-core $coreSelector full bundle accepted" $helper $coreArguments 0 'canonical own-core completed: DB=61; bootstrap=9'
  $coreNative=@(Get-Content -LiteralPath (Join-Path $testRoot 'scripts/native-arguments.jsonl') | ForEach-Object { ConvertFrom-Json $_ })
  if($coreNative.Count -ne 2 -or $coreNative[0].arguments -notcontains '-Pdb-it' -or $coreNative[0].arguments -notcontains 'clean' -or
    $coreNative[1].arguments -notcontains '-Pdelivery-it' -or $coreNative[1].arguments -contains 'clean' -or
    @($coreNative | Where-Object { $_.arguments -contains '-Ptrust-it' -or $_.arguments -contains '-Pcredential-it' }).Count) { throw 'Canonical core selector routed to wrong native owner layer' }
  foreach($requiredCore in @('canonicalShortIdleBudgetDeniesReadWithoutRenewingIt','sensitivePasswordKdfResetRaceCannotConsumeOldProof')) {
    Write-CaseFixture $sessionReport @($canonicalCases | Where-Object { $_ -cne $requiredCore })
    Check-Command "Canonical own-core $coreSelector missing $requiredCore rejected" $helper $coreArguments 1 'Required canonical own-DB case missing'
  }
  Write-CaseFixture $sessionReport $canonicalCases
  Check-Command "Canonical own-core $coreSelector rejects ignored trust config" $helper ($coreArguments+@('-TrustConfig','unused-config')) 1 'does not accept ignored TrustConfig'
}
# A Maven failure must survive the helper, even if a report double looks successful.
'<testsuite tests="0" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath $sessionReport -Encoding utf8
Check-Command 'Zero session suite rejected despite passing credentials' $helper $dbArguments 1 'suite ran zero tests'
'<testsuite tests="1" skipped="1" failures="0" errors="0"/>' | Set-Content -LiteralPath $sessionReport -Encoding utf8
Check-Command 'Skipped session suite rejected despite passing credentials' $helper $dbArguments 1 'did not fully run'
'<testsuite tests="1" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath $sessionReport -Encoding utf8
'<testsuite tests="0" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath $reportPath -Encoding utf8
Check-Command 'Zero bootstrap rejected despite DB tests' $helper $dbArguments 1 'suite ran zero tests'
'<testsuite tests="2" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath $reportPath -Encoding utf8
Check-Command 'Unowned DB config rejected' $helper (@('-Task','db','-Service','identity','-LabConfig',$reportPath)+$javaArguments) 1 'owned workspace .tools/db-lab'
Check-Command 'Fault requires an implemented case' $helper @('-Task','fault','-Service','identity') 1 'fault requires an implemented case'
Check-Command 'Unimplemented fault layer rejected' $helper @('-Task','fault','-Service','identity','-Case','WIRE-04') 1 'fault requires an implemented case'
Check-Command 'Unknown trust case rejected' $helper @('-Task','contract','-Service','identity','-Case','SEC-99') 1 'ValidateSet'
Check-Command 'Required trust config rejected before build' $helper (@('-Task','contract','-Service','identity','-LabConfig',$labConfig)+$javaArguments) 1 'Required trust config missing'
$trustDirectory = Join-Path $testRoot '.tools/trust-lab/run-fixture'
New-Item -ItemType Directory -Path $trustDirectory -Force | Out-Null
$trustConfig = Join-Path $trustDirectory 'identity.properties'
'fixture=true' | Set-Content -LiteralPath $trustConfig -Encoding ascii
@{schemaVersion=1;runId=[Guid]::NewGuid().ToString();workspace=$testRoot} |
  ConvertTo-Json | Set-Content -LiteralPath (Join-Path $trustDirectory 'ownership.json') -Encoding utf8
$trustArguments = @('-Task','contract','-Service','identity','-LabConfig',$labConfig,'-TrustConfig',$trustConfig)+$javaArguments
Remove-Item -LiteralPath $dbReportPath,$credentialReport,$packagedReport,$sessionReport,$deliveryReport
Check-Command 'Missing required trust suites rejected' $helper $trustArguments 1 'Required trust/wire suite missing'
$transportReport = Join-Path $dbReportDirectory 'TEST-lab.backend.identity.TransportTrustIT.xml'
$wireReport = Join-Path $dbReportDirectory 'TEST-lab.backend.identity.WireContractIT.xml'
$runtimeReport = Join-Path $dbReportDirectory 'TEST-lab.backend.identity.session.SessionRuntimeIT.xml'
'<testsuite tests="3" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath $transportReport -Encoding utf8
Check-Command 'Missing wire suite rejected despite TLS report' $helper $trustArguments 1 'Required trust/wire suite missing'
'<testsuite tests="4" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath $wireReport -Encoding utf8
Check-Command 'Missing session runtime suite rejected despite TLS and wire reports' $helper $trustArguments 1 'Required trust/wire suite missing'
$runtimeCases = @('aaReadOnlyBootstrapDoesNotCreateFlowOrCookie','abLoginRotatesCookieAndPollDoesNotRenewIdle','acLogoutRetryAndStatusAreBoundAndNeverClearNewCookie','adWrongOriginCannotMintFlow','afTrustedSourceMustBeOneLiteral','ahSchemaAndBodyNegatives','aiCsrfAndContextDenyBeforeCredentialBuckets','aiSlowUploadHasBoundedTimeoutBeforeAnyFlowEffect','ajActivationIsAtomicAndNeverAutoLogsIn','akResetKeepsPendingStatusAndDeniesOldSessions','alReauthUnsupportedCannotBecomePasswordOnlySuccess','amBadConfigFailsBeforeOpeningListener','anRealmProvisionCannotResetExistingState','azActualBrowserCookieReorderMultiTabAndOrigin','zzRestartRetainsCanonicalDenyAndStatusWithoutResurrection')
$runtimeFixture = '<testsuite tests="15" skipped="0" failures="0" errors="0">' + (($runtimeCases | ForEach-Object { '<testcase name="'+$_+'"/>' }) -join '') + '</testsuite>'
$runtimeFixture | Set-Content -LiteralPath $runtimeReport -Encoding utf8
foreach ($fixture in @(
  @{Name='Zero wire tests rejected';Xml='<testsuite tests="0" skipped="0" failures="0" errors="0"/>';Exit=1;Text='suite ran zero tests'},
  @{Name='Required wire skip rejected';Xml='<testsuite tests="1" skipped="1" failures="0" errors="0"/>';Exit=1;Text='did not fully run'},
  @{Name='Trust failure cannot pass on Maven exit zero';Xml='<testsuite tests="1" skipped="0" failures="1" errors="0"/>';Exit=1;Text='did not fully run'},
  @{Name='Complete owned trust layers accepted';Xml='<testsuite tests="4" skipped="0" failures="0" errors="0"/>';Exit=0;Text='integration=22; unit/bootstrap=9'}
)) {
  $fixture.Xml | Set-Content -LiteralPath $wireReport -Encoding utf8
  Check-Command $fixture.Name $helper $trustArguments $fixture.Exit $fixture.Text
}
'<testsuite tests="15" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath $runtimeReport -Encoding utf8
Check-Command 'Missing session cases rejected despite nonzero passing report' $helper $trustArguments 1 'Required session runtime case missing'
$runtimeFixture | Set-Content -LiteralPath $runtimeReport -Encoding utf8
'<testsuite tests="0" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath $transportReport -Encoding utf8
Check-Command 'Zero TLS suite rejected despite wire tests' $helper $trustArguments 1 'suite ran zero tests'
'<testsuite tests="3" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath $transportReport -Encoding utf8
Remove-Item -LiteralPath $wireReport,$runtimeReport
$faultArguments = @('-Task','fault','-Case','SEC-15','-Service','identity','-LabConfig',$labConfig,'-TrustConfig',$trustConfig)+$javaArguments
Check-Command 'Targeted receiver fault accepts its exact suite' $helper $faultArguments 0 'integration=3; unit/bootstrap=9'
Remove-Item -LiteralPath $transportReport
Check-Command 'Targeted receiver fault missing suite rejected' $helper $faultArguments 1 'Required trust/wire suite missing'
$credentialArguments = @('-Task','fault','-Case','SEC-22','-Service','identity','-LabConfig',$labConfig)+$javaArguments
Check-Command 'Credential fault missing suite rejected' $helper $credentialArguments 1 'Required trust/wire suite missing'
foreach ($path in @($dbReportPath,$credentialReport,$packagedReport)) {
  '<testsuite tests="1" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath $path -Encoding utf8
}
Check-Command 'Credential fault accepts exact suites without trust config' $helper $credentialArguments 0 'integration=3'
'<testsuite tests="0" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath $packagedReport -Encoding utf8
Check-Command 'Zero credential packaged suite rejected' $helper $credentialArguments 1 'suite ran zero tests'
'<testsuite tests="1" skipped="1" failures="0" errors="0"/>' | Set-Content -LiteralPath $packagedReport -Encoding utf8
Check-Command 'Skipped credential packaged suite rejected' $helper $credentialArguments 1 'did not fully run'
Check-Command 'Credential selector cannot run contract' $helper @('-Task','contract','-Service','identity','-Case','SEC-12') 1 'selectors require fault'
# Content receiver report doubles: helper correctness only, never DB/TLS proof.
$contentRoot = Join-Path $testRoot 'backend/services/content-service'
New-Item -ItemType Directory -Path "$contentRoot/target/surefire-reports","$contentRoot/target/failsafe-reports" -Force | Out-Null
'<project/>' | Set-Content -LiteralPath "$contentRoot/pom.xml" -Encoding utf8
(Get-Content -LiteralPath $fixturePom -Raw).Replace('</modules>','<module>services/content-service</module></modules>') | Set-Content -LiteralPath $fixturePom -Encoding utf8
$contentUnit = "$contentRoot/target/surefire-reports/TEST-lab.backend.content.identityaccess.AdmissionWireTests.xml"
$contentDb = "$contentRoot/target/failsafe-reports/TEST-lab.backend.content.database.DatabaseIsolationIT.xml"
$contentGate = "$contentRoot/target/failsafe-reports/TEST-lab.backend.content.identityaccess.AdmissionGateIT.xml"
$contentRuntime = "$contentRoot/target/failsafe-reports/TEST-lab.backend.content.identityaccess.AdmissionRuntimeIT.xml"
$contentTransport = "$contentRoot/target/failsafe-reports/TEST-lab.backend.content.TransportTrustIT.xml"
$contentWire = "$contentRoot/target/failsafe-reports/TEST-lab.backend.content.WireContractIT.xml"
$passingSuite = '<testsuite tests="1" skipped="0" failures="0" errors="0"/>'
$passingSuite | Set-Content -LiteralPath "$contentRoot/target/surefire-reports/TEST-lab.backend.content.delivery.DeliveryWireTests.xml" -Encoding utf8
$passingSuite | Set-Content -LiteralPath "$contentRoot/target/surefire-reports/TEST-lab.backend.content.delivery.DeliveryRuntimeConfigTests.xml" -Encoding utf8
$contentDelivery = "$contentRoot/target/failsafe-reports/TEST-lab.backend.content.delivery.DeliveryStoreIT.xml"
$passingSuite | Set-Content -LiteralPath $contentDelivery -Encoding utf8
$contentDbArgs = @('-Task','db','-Service','content','-LabConfig',$labConfig)+$javaArguments
$contentTrustArgs = @('-Task','contract','-Service','content','-LabConfig',$labConfig,'-TrustConfig',$trustConfig)+$javaArguments
$passingSuite | Set-Content -LiteralPath $contentUnit -Encoding utf8
$passingSuite | Set-Content -LiteralPath $contentDb -Encoding utf8
Check-Command 'Content missing gate suite rejected' $helper $contentDbArgs 1 'required service DB suite missing'
$gateCases = @('absentSessionFenceDeniesLateAdmissionWithoutProvisioningLearner','olderCommandCannotLowerFloorAndNewEpochMayProceed','duplicateCommandIsDurableAndChangedContextConflicts','twoReplicasFenceWaitsForReadTransactionCommit','rollbackKeepsAdmissionAndLocalEffectAtomic','wrongContextNonceBindingAndOwnerCannotUseAdmission','sameSessionUnderDifferentOwnerCannotReplaceTombstone','runtimeRoleCannotEraseFenceReceiptOrLowerFloor','wrongRealmGenerationAndNotReadyFailClosed','cutoffRejectedBeforeEffectAndDatabaseBoundsHeldTransaction')
foreach ($missingCase in $gateCases) {
  Write-CaseFixture $contentGate @($gateCases | Where-Object { $_ -ne $missingCase })
  Check-Command "Content gate missing $missingCase rejected" $helper $contentDbArgs 1 'Required admission case missing'
}
Write-CaseFixture $contentGate $gateCases
Check-Command 'Content complete DB suites accepted' $helper $contentDbArgs 0 'DB=12; bootstrap=3'
Remove-Item -LiteralPath $contentDelivery
Check-Command 'Content SEC04 requires actual gate plus isolation suites' $helper (@('-Task','fault','-Case','SEC-04','-Service','content','-LabConfig',$labConfig)+$javaArguments) 0 'integration=11'
Remove-Item -LiteralPath $contentDb,$contentGate
$passingSuite | Set-Content -LiteralPath $contentTransport -Encoding utf8
$passingSuite | Set-Content -LiteralPath $contentWire -Encoding utf8
Check-Command 'Content missing runtime suite rejected' $helper $contentTrustArgs 1 'Required trust/wire suite missing'
$admissionCases = @('aaOnlineAdmissionIsFreshAndUnavailableDoesNotUseCachedAllow','abWrongProviderResponseAndBoundedFailuresDenyBeforeLocalEffect','acOnlyIdentityCertificateMayFence','adPublicAndBrowserHeadersCannotReachFence','aeDurableDuplicateAndConflictingEnvelope','afSchemaDigestGenerationMethodAndBodyLimitsFailClosed','agAbsentSessionTombstoneBlocksActualOnlineAdmission','ahProvisionAndRuntimeMismatchCannotResetExistingSecurityState','aiStalledUploadHasBoundedIdleTimeoutAndNoFenceEffect','zzRestartAndLostAckPreserveReceiptAndDeny')
foreach ($missingCase in $admissionCases) {
  Write-CaseFixture $contentRuntime @($admissionCases | Where-Object { $_ -ne $missingCase })
  Check-Command "Content runtime missing $missingCase rejected" $helper $contentTrustArgs 1 'Required admission case missing'
}
Write-CaseFixture $contentRuntime $admissionCases
Check-Command 'Content full runtime report accepted' $helper $contentTrustArgs 0 'integration=12'
Move-Item -LiteralPath $contentUnit -Destination "$contentRoot/target/surefire-reports/TEST-unrelated.xml"
Check-Command 'Content missing admission wire unit rejected' $helper $contentTrustArgs 1 'admission wire suite missing'
Move-Item -LiteralPath "$contentRoot/target/surefire-reports/TEST-unrelated.xml" -Destination $contentUnit
Remove-Item -LiteralPath $contentWire
Check-Command 'Content SEC15 requires transport and receiver suites' $helper (@('-Task','fault','-Case','SEC-15','-Service','content','-LabConfig',$labConfig,'-TrustConfig',$trustConfig)+$javaArguments) 0 'integration=11'
Remove-Item -LiteralPath $contentTransport
foreach ($selector in @('SEC-02','SEC-09')) {
  Check-Command "Content $selector accepts exact receiver suite" $helper (@('-Task','fault','-Case',$selector,'-Service','content','-LabConfig',$labConfig,'-TrustConfig',$trustConfig)+$javaArguments) 0 'integration=10'
}
Check-Command 'Mutation selector rejects Content owner' $helper @('-Task','fault','-Case','SEC-03','-Service','content') 1 'Learning receiver'
# Learning report doubles qualify helper gates, not actual mutation/DB/TLS behavior.
$learningRoot = Join-Path $testRoot 'backend/services/learning-service'
New-Item -ItemType Directory -Path "$learningRoot/target/surefire-reports","$learningRoot/target/failsafe-reports" -Force | Out-Null
'<project/>' | Set-Content -LiteralPath "$learningRoot/pom.xml" -Encoding utf8
(Get-Content -LiteralPath $fixturePom -Raw).Replace('</modules>','<module>services/learning-service</module></modules>') | Set-Content -LiteralPath $fixturePom -Encoding utf8
$learningUnit = "$learningRoot/target/surefire-reports/TEST-lab.backend.learning.identityaccess.AdmissionWireTests.xml"
$learningDb = "$learningRoot/target/failsafe-reports/TEST-lab.backend.learning.database.DatabaseIsolationIT.xml"
$learningGate = "$learningRoot/target/failsafe-reports/TEST-lab.backend.learning.identityaccess.AdmissionGateIT.xml"
$learningRuntime = "$learningRoot/target/failsafe-reports/TEST-lab.backend.learning.identityaccess.AdmissionRuntimeIT.xml"
$learningTransport = "$learningRoot/target/failsafe-reports/TEST-lab.backend.learning.TransportTrustIT.xml"
$learningWire = "$learningRoot/target/failsafe-reports/TEST-lab.backend.learning.WireContractIT.xml"
$learningDbArgs = @('-Task','db','-Service','learning','-LabConfig',$labConfig)+$javaArguments
$learningTrustArgs = @('-Task','contract','-Service','learning','-LabConfig',$labConfig,'-TrustConfig',$trustConfig)+$javaArguments
$passingSuite | Set-Content -LiteralPath $learningUnit -Encoding utf8
$passingSuite | Set-Content -LiteralPath "$learningRoot/target/surefire-reports/TEST-lab.backend.learning.delivery.DeliveryWireTests.xml" -Encoding utf8
$passingSuite | Set-Content -LiteralPath "$learningRoot/target/surefire-reports/TEST-lab.backend.learning.delivery.DeliveryRuntimeConfigTests.xml" -Encoding utf8
$learningDelivery = "$learningRoot/target/failsafe-reports/TEST-lab.backend.learning.delivery.DeliveryStoreIT.xml"
$passingSuite | Set-Content -LiteralPath $learningDelivery -Encoding utf8
$passingSuite | Set-Content -LiteralPath $learningDb -Encoding utf8
Check-Command 'Learning missing gate suite rejected' $helper $learningDbArgs 1 'required service DB suite missing'
$learningGateCases = @($gateCases | ForEach-Object { $_.Replace('twoReplicasFenceWaitsForReadTransactionCommit','twoReplicasFenceWaitsForMutationTransactionCommit') }) + 'committedFenceDeniesConcurrentMutationWithoutEffect'
foreach ($missingCase in $learningGateCases) {
  Write-CaseFixture $learningGate @($learningGateCases | Where-Object { $_ -ne $missingCase })
  Check-Command "Learning gate missing $missingCase rejected" $helper $learningDbArgs 1 'Required admission case missing'
}
Write-CaseFixture $learningGate $learningGateCases
Check-Command 'Learning complete DB suites accepted' $helper $learningDbArgs 0 'DB=13; bootstrap=3'
Remove-Item -LiteralPath $learningDelivery
foreach ($selector in @('SEC-03','SEC-04')) {
  Check-Command "Learning $selector requires gate plus isolation suites" $helper (@('-Task','fault','-Case',$selector,'-Service','learning','-LabConfig',$labConfig)+$javaArguments) 0 'integration=12'
}
Remove-Item -LiteralPath $learningDb,$learningGate
$passingSuite | Set-Content -LiteralPath $learningTransport -Encoding utf8
$passingSuite | Set-Content -LiteralPath $learningWire -Encoding utf8
Check-Command 'Learning missing runtime suite rejected' $helper $learningTrustArgs 1 'Required trust/wire suite missing'
$learningRuntimeCases = @('aaMutationUsesFreshAdmissionAndCommitsEffectAtomically','abUnavailableProviderDoesNotUseCachedAllow','acWrongProviderResponseAndBoundedFailuresDenyBeforeEffect','adOnlyIdentityCertificateMayFence','aePublicAndBrowserHeadersCannotReachFence','afDurableDuplicateAndConflictingEnvelope','agAbsentSessionTombstoneBlocksActualOnlineMutation','ahProvisionAndRuntimeMismatchCannotResetSecurityState','aiSchemaDigestGenerationMethodAndBodyLimitsFailClosed','ajStalledUploadHasBoundedIdleTimeoutAndNoFenceEffect','akWrongOriginCannotCallProviderOrMutate','zzRestartAndLostAckPreserveReceiptAndDeny')
foreach ($missingCase in $learningRuntimeCases) {
  Write-CaseFixture $learningRuntime @($learningRuntimeCases | Where-Object { $_ -ne $missingCase })
  Check-Command "Learning runtime missing $missingCase rejected" $helper $learningTrustArgs 1 'Required admission case missing'
}
Write-CaseFixture $learningRuntime $learningRuntimeCases
Check-Command 'Learning full runtime report accepted' $helper $learningTrustArgs 0 'integration=14'
foreach ($fixture in @(
  @{Name='Learning zero runtime rejected';Xml='<testsuite tests="0" skipped="0" failures="0" errors="0"/>';Text='suite ran zero tests'},
  @{Name='Learning skipped runtime rejected';Xml='<testsuite tests="1" skipped="1" failures="0" errors="0"/>';Text='did not fully run'}
)) {
  $fixture.Xml | Set-Content -LiteralPath $learningRuntime -Encoding utf8
  Check-Command $fixture.Name $helper $learningTrustArgs 1 $fixture.Text
}
Write-CaseFixture $learningRuntime $learningRuntimeCases
Move-Item -LiteralPath $learningUnit -Destination "$learningRoot/target/surefire-reports/TEST-unrelated.xml"
Check-Command 'Learning missing admission wire unit rejected' $helper $learningTrustArgs 1 'admission wire suite missing'
Move-Item -LiteralPath "$learningRoot/target/surefire-reports/TEST-unrelated.xml" -Destination $learningUnit
Remove-Item -LiteralPath $learningWire
Check-Command 'Learning SEC15 requires transport plus receiver suites' $helper (@('-Task','fault','-Case','SEC-15','-Service','learning','-LabConfig',$labConfig,'-TrustConfig',$trustConfig)+$javaArguments) 0 'integration=13'
Remove-Item -LiteralPath $learningTransport
foreach ($selector in @('SEC-01','SEC-02','SEC-05','SEC-09')) {
  Check-Command "Learning $selector accepts exact receiver suite" $helper (@('-Task','fault','-Case',$selector,'-Service','learning','-LabConfig',$labConfig,'-TrustConfig',$trustConfig)+$javaArguments) 0 'integration=12'
}
Check-Command 'Admission fault rejects contract selector' $helper @('-Task','contract','-Case','SEC-02','-Service','content') 1 'selectors require fault'
} else {
  # Fast feedback for new relay gates. Closing DoD still requires the full run.
  $passingSuite='<testsuite tests="1" skipped="0" failures="0" errors="0"/>'
  '<testsuite tests="2" skipped="0" failures="0" errors="0"/>' | Set-Content -LiteralPath $reportPath -Encoding utf8
  $passingSuite | Set-Content -LiteralPath (Join-Path $reportDirectory 'TEST-lab.backend.identity.delivery.DeliveryWireTests.xml') -Encoding utf8
  $passingSuite | Set-Content -LiteralPath (Join-Path $reportDirectory 'TEST-lab.backend.identity.delivery.DeliveryRuntimeConfigTests.xml') -Encoding utf8
  $passingSuite | Set-Content -LiteralPath (Join-Path $reportDirectory 'TEST-lab.backend.identity.session.CanonicalAdmissionWireTests.xml') -Encoding utf8
  $passingSuite | Set-Content -LiteralPath (Join-Path $reportDirectory 'TEST-lab.backend.identity.session.AuthPendingWorkTests.xml') -Encoding utf8
  foreach($focusedAdapter in @('ReauthReservationWireTests','CanonicalAuthHttpHandlerTests','IdentitySensitiveIntentTests')) {
    $passingSuite | Set-Content -LiteralPath (Join-Path $reportDirectory "TEST-lab.backend.identity.session.$focusedAdapter.xml") -Encoding utf8
  }
  foreach($fixtureService in @('content','learning')) {
    $fixtureServiceRoot=Join-Path $testRoot "backend/services/$fixtureService-service"
    New-Item -ItemType Directory -Path "$fixtureServiceRoot/target/surefire-reports","$fixtureServiceRoot/target/failsafe-reports" -Force | Out-Null
    '<project/>' | Set-Content -LiteralPath "$fixtureServiceRoot/pom.xml" -Encoding utf8
    (Get-Content -LiteralPath $fixturePom -Raw).Replace('</modules>',"<module>services/$fixtureService-service</module></modules>") | Set-Content -LiteralPath $fixturePom -Encoding utf8
    foreach($fixtureSuite in @('delivery.DeliveryWireTests','delivery.DeliveryRuntimeConfigTests','identityaccess.AdmissionWireTests')) {
      $passingSuite | Set-Content -LiteralPath "$fixtureServiceRoot/target/surefire-reports/TEST-lab.backend.$fixtureService.$fixtureSuite.xml" -Encoding utf8
    }
  }
  New-Item -ItemType Directory -Path (Join-Path $testRoot 'backend/services/identity-service/target/failsafe-reports') -Force | Out-Null
  $labDirectory=Join-Path $testRoot '.tools/db-lab/run-fixture'
  $trustDirectory=Join-Path $testRoot '.tools/trust-lab/run-fixture'
  New-Item -ItemType Directory -Path $labDirectory,$trustDirectory -Force | Out-Null
  $labConfig=Join-Path $labDirectory 'test-credentials.properties'
  $trustConfig=Join-Path $trustDirectory 'identity.properties'
  'fixture=true' | Set-Content -LiteralPath $labConfig -Encoding ascii
  'fixture=true' | Set-Content -LiteralPath $trustConfig -Encoding ascii
  @{schemaVersion=1;runId=[Guid]::NewGuid().ToString();workspace=$testRoot;dataDirectory=(Join-Path $labDirectory 'data')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $labDirectory 'ownership.json') -Encoding utf8
  @{schemaVersion=1;runId=[Guid]::NewGuid().ToString();workspace=$testRoot} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $trustDirectory 'ownership.json') -Encoding utf8
}
# R02c strict relay report gates only; all commands still isolated doubles, no DB/mTLS claim.
if(-not $CanonicalOnly) {
$relayBaseCases = @('aaOwnProducerCliAndPackagedReceiverCommitBothLocalEffects','abDiscardedAckAndRestartResendExactMessageWithoutSecondEffect','acPublicWrongCallerAndBrowserHeadersCannotDeliver','adFutureGapAndBadDigestAreNoEffectDurablePoison','afActualProducerAbortAndReceiverRecoveryPreserveBytesAndNoEffect','agDispatcherProcessCrashBeforeAndAfterReceiverCommit','ahBoundedNetworkFailuresNeverAcknowledgeOrLeakMessage','aiWrongAckFromAuthenticatedPeerCannotMarkDelivered','ajWrongReceiverCertificateCannotMarkDelivered')
$relayVariantCounts = @{ 'agDispatcherProcessCrashBeforeAndAfterReceiverCommit'=2; 'ahBoundedNetworkFailuresNeverAcknowledgeOrLeakMessage'=4; 'aiWrongAckFromAuthenticatedPeerCannotMarkDelivered'=7; 'ajWrongReceiverCertificateCannotMarkDelivered'=2 }
function Expand-RelayCaseFixture([string[]]$Names) {
  foreach($fixtureCaseName in $Names) {
    $fixtureVariants = if($relayVariantCounts.ContainsKey($fixtureCaseName)) { $relayVariantCounts[$fixtureCaseName] } else { 1 }
    for($fixtureVariant=1;$fixtureVariant -le $fixtureVariants;$fixtureVariant++) { "$fixtureCaseName[$fixtureVariant]" }
  }
}
$relaySelectorCases = @{
  'DATA-10'=@('aaOwnProducerCliAndPackagedReceiverCommitBothLocalEffects','agDispatcherProcessCrashBeforeAndAfterReceiverCommit')
  'DATA-11'=@('abDiscardedAckAndRestartResendExactMessageWithoutSecondEffect')
  'DATA-12'=@('adFutureGapAndBadDigestAreNoEffectDurablePoison')
  'DATA-13'=@('acPublicWrongCallerAndBrowserHeadersCannotDeliver','adFutureGapAndBadDigestAreNoEffectDurablePoison','afActualProducerAbortAndReceiverRecoveryPreserveBytesAndNoEffect','ahBoundedNetworkFailuresNeverAcknowledgeOrLeakMessage','ajWrongReceiverCertificateCannotMarkDelivered')
  'DATA-14'=@('aiWrongAckFromAuthenticatedPeerCannotMarkDelivered')
  'DATA-15'=@('aeAuthLanePassesActualInt02WhileStrictPoisonRemains')
}
Check-Command 'Unimplemented relay case selector rejected' $helper @('-Task','relay','-Service','learning','-Case','WIRE-01') 1 'no implemented case selector'
Check-Command 'DATA selector rejects wrong command layer' $helper @('-Task','contract','-Service','learning','-Case','DATA-14') 1 'selectors require relay'
Check-Command 'DATA15 rejects Identity coordinator layer' $helper @('-Task','relay','-Service','identity','-Case','DATA-15') 1 'Content or Learning receiver'
foreach ($relayService in @('identity','content','learning')) {
  $relayReportPath = Join-Path $testRoot "backend/services/$relayService-service/target/failsafe-reports/TEST-lab.backend.$relayService.delivery.DeliveryRuntimeIT.xml"
  # Real clean verify removes old suites. The no-op wrapper double must mirror that isolation.
  $relayFixtureDirectory=Split-Path -Parent $relayReportPath
  if(-not [IO.Path]::GetFullPath($relayFixtureDirectory).StartsWith(([IO.Path]::GetFullPath($testRoot)+[IO.Path]::DirectorySeparatorChar),[StringComparison]::OrdinalIgnoreCase)) { throw 'Relay fixture path outside test workspace' }
  Get-ChildItem -LiteralPath $relayFixtureDirectory -Filter 'TEST-*.xml' -File | ForEach-Object { Remove-Item -LiteralPath $_.FullName }
  $relayArguments = @('-Task','relay','-Service',$relayService,'-LabConfig',$labConfig,'-TrustConfig',$trustConfig)+$javaArguments
  $relayNames = @($relayBaseCases)
  if ($relayService -in @('content','learning')) { $relayNames += 'aeAuthLanePassesActualInt02WhileStrictPoisonRemains' }
  Check-Command "$relayService missing relay runtime suite rejected" $helper $relayArguments 1 'Required trust/wire suite missing'
  foreach ($missingRelayCase in $relayNames) {
    Write-CaseFixture $relayReportPath @(Expand-RelayCaseFixture @($relayNames | Where-Object { $_ -ne $missingRelayCase }))
    $expectedRelayError = 'Required FX04 runtime case missing'
    Check-Command "$relayService relay missing $missingRelayCase rejected" $helper $relayArguments 1 $expectedRelayError
  }
  $expandedRelayNames = @(Expand-RelayCaseFixture $relayNames)
  Write-CaseFixture $relayReportPath $expandedRelayNames
  Check-Command "$relayService complete relay cases accepted" $helper $relayArguments 0 "integration=$($expandedRelayNames.Count)"
  foreach($variantCase in $relayVariantCounts.Keys) {
    Write-CaseFixture $relayReportPath @($expandedRelayNames | Where-Object { $_ -ne "$variantCase[1]" })
    Check-Command "$relayService missing $variantCase variant rejected" $helper $relayArguments 1 'Required FX04 variant missing'
  }
  foreach ($invalidRelayReport in @(
    @{Name='zero';Xml='<testsuite tests="0" skipped="0" failures="0" errors="0"/>';Text='suite ran zero tests'},
    @{Name='skip';Xml='<testsuite tests="1" skipped="1" failures="0" errors="0"/>';Text='did not fully run'},
    @{Name='failure';Xml='<testsuite tests="1" skipped="0" failures="1" errors="0"/>';Text='did not fully run'},
    @{Name='malformed';Xml='<testsuite tests="unknown" skipped="0" failures="0" errors="0"/>';Text='Invalid JUnit report'}
  )) {
    $invalidRelayReport.Xml | Set-Content -LiteralPath $relayReportPath -Encoding utf8
    Check-Command "$relayService relay $($invalidRelayReport.Name) rejected" $helper $relayArguments 1 $invalidRelayReport.Text
  }
  Write-CaseFixture $relayReportPath $expandedRelayNames
  foreach($relaySelector in $relaySelectorCases.Keys) {
    if($relaySelector -eq 'DATA-15' -and $relayService -eq 'identity') { continue }
    $selectedFixtureNames = @(Expand-RelayCaseFixture $relaySelectorCases[$relaySelector])
    Write-CaseFixture $relayReportPath $selectedFixtureNames
    Check-Command "$relayService exact $relaySelector selected cases accepted" $helper ($relayArguments+@('-Case',$relaySelector)) 0 "integration=$($selectedFixtureNames.Count)"
  }
  Write-CaseFixture $relayReportPath @('aiWrongAckFromAuthenticatedPeerCannotMarkDelivered[1]')
  Check-Command "$relayService DATA14 truncated variant matrix rejected" $helper ($relayArguments+@('-Case','DATA-14')) 1 'Required FX04 variant missing'
  Write-CaseFixture $relayReportPath (@(Expand-RelayCaseFixture $relaySelectorCases['DATA-14'])+@('aaOwnProducerCliAndPackagedReceiverCommitBothLocalEffects'))
  Check-Command "$relayService selected DATA14 rejects unrelated case" $helper ($relayArguments+@('-Case','DATA-14')) 1 'Unexpected FX04 case for selector'
  '<testsuite tests="2" skipped="0" failures="0" errors="0"><testcase name="aaOwnProducerCliAndPackagedReceiverCommitBothLocalEffects"/></testsuite>' | Set-Content -LiteralPath $relayReportPath -Encoding utf8
  Check-Command "$relayService mismatched relay report count rejected" $helper $relayArguments 1 'report/case count mismatch'
  Write-CaseFixture $relayReportPath $expandedRelayNames
}
"@echo off`r`nexit /b 7`r`n" | Set-Content -LiteralPath $fixtureWrapper -Encoding ascii
Check-Command 'Native build failure propagated' $helper (@('-Task','verify','-Service','identity')+$javaArguments) 7 'Task: verify'
$cachedArchive = Join-Path $testRoot ".tools/downloads/$($manifest.toolchain.jdk.archive)"
New-Item -ItemType Directory -Path (Split-Path -Parent $cachedArchive) -Force | Out-Null
'corrupt fixture archive' | Set-Content -LiteralPath $cachedArchive -Encoding ascii
Check-Command 'Corrupt JDK archive refused before extraction' (Join-Path $testRoot 'scripts/setup-backend.ps1') @() 1 'Cached JDK archive checksum mismatch'
}
# Native failure fixture cố ý để wrapper exit7. Khôi phục command double trước
# canonical phase; nếu quên, peer build giả sẽ fail thay vì kiểm report gate.
"@echo off`r`npwsh -NoProfile -File `"%~dp0..\scripts\capture-native.ps1`" %*`r`nexit /b %ERRORLEVEL%`r`n" | Set-Content -LiteralPath $fixtureWrapper -Encoding ascii
# Canonical report doubles chỉ kiểm helper, không chứng minh app/DB/mTLS.
# Wrapper giả không clean target, nên bỏ đúng XML IT trong owned fixture để
# baseline canonical không bị report relay cũ che thiếu suite.
$canonicalFixtureDirectory=Join-Path $testRoot 'backend/services/learning-service/target/failsafe-reports'
if(-not [IO.Path]::GetFullPath($canonicalFixtureDirectory).StartsWith(([IO.Path]::GetFullPath($testRoot)+[IO.Path]::DirectorySeparatorChar),[StringComparison]::OrdinalIgnoreCase)) { throw 'Canonical fixture outside test workspace' }
Get-ChildItem -LiteralPath $canonicalFixtureDirectory -Filter 'TEST-*.xml' -File | ForEach-Object { Remove-Item -LiteralPath $_.FullName }
# Peer verify gate cần các adapter units thật mới; RelayOnly fixtures trước đây
# không có chúng. Mọi XML mới có testcase để review không dựa chỉ summary count.
foreach($canonicalAdapter in @('ReauthReservationWireTests','CanonicalAuthHttpHandlerTests','IdentitySensitiveIntentTests')) {
  Write-CaseFixture (Join-Path $reportDirectory "TEST-lab.backend.identity.session.$canonicalAdapter.xml") @('fixtureCase')
}
Write-CaseFixture (Join-Path $reportDirectory 'TEST-lab.backend.identity.session.AuthPendingWorkTests.xml') @('fixtureCase')
$canonicalArguments=@('-Task','contract','-Service','learning','-EvidenceTask','R04d','-LabConfig',$labConfig,'-TrustConfig',$trustConfig)+$javaArguments
$canonicalReportPath=Join-Path $canonicalFixtureDirectory 'TEST-lab.backend.learning.identityaccess.CanonicalThreeServiceRuntimeIT.xml'
$canonicalFixtureNames=@('aaCanonicalProviderDrivesBothOwnAccessHooks','abMissingReceiverKeepsPendingAndRestartCompletesExactBarrier','acPublicProofAndLearningOnlyReservationAreDurablyBound','adActualMutationGateDelaysFenceAndCompletionUntilCommitOrRollback(String)[1]','adActualMutationGateDelaysFenceAndCompletionUntilCommitOrRollback(String)[2]','aeLostAckAndBothProcessRestartsReuseExactDurableCommand')
$canonicalFixtureNames+=@('afActualSensitiveProofAndAccountBarrierSurviveRestart(String)[1]','afActualSensitiveProofAndAccountBarrierSurviveRestart(String)[2]')
$canonicalFixtureNames+=@('agCanonicalProviderRejectsCallerContextCsrfAndRequestBindings','ahLearningReservationRejectsEveryBindingBeforeAndAfterRestart','aiSensitiveProofRejectsCrossPurposePayloadAndEpochOverTls(String)[1]','aiSensitiveProofRejectsCrossPurposePayloadAndEpochOverTls(String)[2]','ajWrongAuthenticatedAckCannotCompleteOrBlockIndependentBarrier(String)[1]','ajWrongAuthenticatedAckCannotCompleteOrBlockIndependentBarrier(String)[2]','akWrongReceiverCertificateCannotCompleteBarrier(String)[1]','akWrongReceiverCertificateCannotCompleteBarrier(String)[2]')
$canonicalFixtureNames+=@('azActualSensitiveBrowserCookieReorderAndMultiTab(String)[1]','azActualSensitiveBrowserCookieReorderAndMultiTab(String)[2]')
$canonicalFixtureNames+=@('alCanonicalCompletionPassesStrictPoisonWithoutClearingItsStream','amLegacyCredentialReconciliationUsesActualWorkerAndKeepsLifecycleGate(String)[1]','amLegacyCredentialReconciliationUsesActualWorkerAndKeepsLifecycleGate(String)[2]')
# Đây là report doubles trong testRoot, không khởi động browser hoặc service.
$canonicalBrowserLabels=@('browser-A-active','host-secure-httponly-cookie','browser-js-cannot-read-cookie','second-tab-A-binding','natural-origin-sensitive-proof','pending-without-cookie','pending-bootstrap-locked','pending-blocks-new-credential-flow','browser-B-independent-owner','late-A-no-cookie-B-survives','cross-cookie-retry-denied','cross-cookie-status-denied','second-tab-observes-B','old-CSRF-cannot-mint-under-B','Back-focus-no-cookie-renewal','initiating-status-completed-only','wrong-context-status-denied','original-bound-sensitive-retry-completed','old-A-cookie-never-resurrects','completed-A-does-not-revoke-B','foreign-origin-denied','no-status-proof-sensitive-cookie-emission','no-browser-runtime-errors')
foreach($canonicalPurpose in @('LOGOUT_ALL','PASSWORD_CHANGE')) {
  @{stage='RESULT';status='PASS';purpose=$canonicalPurpose;checks=$canonicalBrowserLabels.Count;cases=$canonicalBrowserLabels;browser='installed Microsoft Edge / Chromium';version='1.2.3.4';origin='https://localhost:17441';scope='helper report double only'} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $trustDirectory "canonical-browser-$canonicalPurpose.json") -Encoding utf8
}
Check-Command 'Canonical missing runtime suite rejected' $helper $canonicalArguments 1 'Required trust/wire suite missing'
foreach($missingCanonical in $canonicalFixtureNames) {
  Write-CaseFixture $canonicalReportPath @($canonicalFixtureNames | Where-Object { $_ -ne $missingCanonical })
  Check-Command "Canonical missing case/variant rejected: $missingCanonical" $helper $canonicalArguments 1 'Required canonical runtime case/variant missing'
}
Write-CaseFixture $canonicalReportPath $canonicalFixtureNames
Check-Command 'Canonical twenty-one-case runtime accepted' $helper $canonicalArguments 0 'integration=21'
# Native argument capture chứng minh profile riêng và ba build tuần tự; không
# dùng report xanh thay bằng chứng lệnh đã chọn đúng runtime driver.
$canonicalNative=@(Get-Content -LiteralPath (Join-Path $testRoot 'scripts/native-arguments.jsonl') | ForEach-Object { ConvertFrom-Json $_ })
if($canonicalNative.Count -ne 3 -or $canonicalNative[0].arguments -notcontains 'services/identity-service' -or $canonicalNative[1].arguments -notcontains 'services/content-service' -or $canonicalNative[2].arguments -notcontains '-Pcanonical-it' -or $canonicalNative[2].arguments -contains '-Ptrust-it') { throw 'Canonical native profile/peer build order mismatch' }
foreach($canonicalPurpose in @('LOGOUT_ALL','PASSWORD_CHANGE')) {
  $canonicalBrowserPath=Join-Path $trustDirectory "canonical-browser-$canonicalPurpose.json"
  $savedCanonicalBrowser=Get-Content -LiteralPath $canonicalBrowserPath -Raw
  Remove-Item -LiteralPath $canonicalBrowserPath
  Check-Command "Canonical missing browser $canonicalPurpose rejected" $helper $canonicalArguments 1 'Required canonical browser report missing'
  $savedCanonicalBrowser | Set-Content -LiteralPath $canonicalBrowserPath -Encoding utf8
  $badCanonicalBrowser=$savedCanonicalBrowser | ConvertFrom-Json -AsHashtable
  $badCanonicalBrowser.cases=@($canonicalBrowserLabels | Select-Object -Skip 1)
  $badCanonicalBrowser | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $canonicalBrowserPath -Encoding utf8
  Check-Command "Canonical missing browser case $canonicalPurpose rejected" $helper $canonicalArguments 1 'Invalid canonical browser report/case'
  $savedCanonicalBrowser | Set-Content -LiteralPath $canonicalBrowserPath -Encoding utf8
}
foreach($canonicalBad in @(
  @{Name='zero';Xml='<testsuite tests="0" failures="0" errors="0" skipped="0"/>';Text='zero tests'},
  @{Name='skip';Xml='<testsuite tests="6" failures="0" errors="0" skipped="1"/>';Text='did not fully run'},
  @{Name='failure';Xml='<testsuite tests="6" failures="1" errors="0" skipped="0"/>';Text='did not fully run'},
  @{Name='error';Xml='<testsuite tests="6" failures="0" errors="1" skipped="0"/>';Text='did not fully run'},
  @{Name='malformed count';Xml='<testsuite tests="NaN" failures="0" errors="0" skipped="0"/>';Text='Invalid JUnit report'},
  @{Name='summary without cases';Xml='<testsuite tests="6" failures="0" errors="0" skipped="0"/>';Text='Invalid canonical runtime case report'}
)) {
  $canonicalBad.Xml | Set-Content -LiteralPath $canonicalReportPath -Encoding utf8
  Check-Command "Canonical $($canonicalBad.Name) rejected" $helper $canonicalArguments 1 $canonicalBad.Text
}
Write-CaseFixture $canonicalReportPath @($canonicalFixtureNames + 'unexpectedMethod')
Check-Command 'Canonical unexpected case rejected' $helper $canonicalArguments 1 'Unexpected canonical runtime case'
Write-CaseFixture $canonicalReportPath @($canonicalFixtureNames + $canonicalFixtureNames[0])
Check-Command 'Canonical duplicate case rejected' $helper $canonicalArguments 1 'Invalid canonical runtime case report'
Write-CaseFixture $canonicalReportPath $canonicalFixtureNames
(Get-Content -LiteralPath $canonicalReportPath -Raw).Replace('<testcase name="aaCanonicalProviderDrivesBothOwnAccessHooks"/>','<testcase name="aaCanonicalProviderDrivesBothOwnAccessHooks"><failure/></testcase>') | Set-Content -LiteralPath $canonicalReportPath -Encoding utf8
Check-Command 'Canonical hidden failure despite zero summary rejected' $helper $canonicalArguments 1 'Invalid canonical runtime case report'
Write-CaseFixture $canonicalReportPath $canonicalFixtureNames
foreach($missingPeer in @('identity','content')) {
  $peerWire=Join-Path $testRoot "backend/services/$missingPeer-service/target/surefire-reports/TEST-lab.backend.$missingPeer.delivery.DeliveryWireTests.xml"
  $savedPeerWire=Get-Content -LiteralPath $peerWire -Raw
  Remove-Item -LiteralPath $peerWire
  Check-Command "Canonical missing $missingPeer peer unit rejected" $helper $canonicalArguments 1 'Required canonical peer build/report gate failed'
  $savedPeerWire | Set-Content -LiteralPath $peerWire -Encoding utf8
}
Check-Command 'Canonical contract cannot silently become selected fault' $helper ($canonicalArguments+@('-Case','SEC-20')) 1 'contract requires the full baseline'
# Oracle độc lập: chọn exact method/variants trên XML fixture và đối chiếu
# một token native -Dit.test; không chỉ thấy report/count màu xanh.
$canonicalSelectorFixtures=@{
  'SEC-01'=@('adActualMutationGateDelaysFenceAndCompletionUntilCommitOrRollback')
  'SEC-03'=@('adActualMutationGateDelaysFenceAndCompletionUntilCommitOrRollback')
  'SEC-05'=@('aaCanonicalProviderDrivesBothOwnAccessHooks','abMissingReceiverKeepsPendingAndRestartCompletesExactBarrier')
  'SEC-06'=@('abMissingReceiverKeepsPendingAndRestartCompletesExactBarrier','afActualSensitiveProofAndAccountBarrierSurviveRestart','azActualSensitiveBrowserCookieReorderAndMultiTab')
  'SEC-07'=@('aeLostAckAndBothProcessRestartsReuseExactDurableCommand','afActualSensitiveProofAndAccountBarrierSurviveRestart')
  'SEC-08'=@('ajWrongAuthenticatedAckCannotCompleteOrBlockIndependentBarrier')
  'SEC-11'=@('azActualSensitiveBrowserCookieReorderAndMultiTab')
  'SEC-12'=@('amLegacyCredentialReconciliationUsesActualWorkerAndKeepsLifecycleGate')
  'SEC-19'=@('agCanonicalProviderRejectsCallerContextCsrfAndRequestBindings','aiSensitiveProofRejectsCrossPurposePayloadAndEpochOverTls','azActualSensitiveBrowserCookieReorderAndMultiTab')
  'SEC-20'=@('agCanonicalProviderRejectsCallerContextCsrfAndRequestBindings','akWrongReceiverCertificateCannotCompleteBarrier')
  'SEC-23'=@('acPublicProofAndLearningOnlyReservationAreDurablyBound','ahLearningReservationRejectsEveryBindingBeforeAndAfterRestart','aiSensitiveProofRejectsCrossPurposePayloadAndEpochOverTls','azActualSensitiveBrowserCookieReorderAndMultiTab')
  'DATA-15'=@('alCanonicalCompletionPassesStrictPoisonWithoutClearingItsStream')
}
foreach($selectedCanonical in $canonicalSelectorFixtures.Keys | Sort-Object) {
  [string[]]$selectedMethods=@($canonicalSelectorFixtures[$selectedCanonical])
  $selectedNames=@($canonicalFixtureNames | Where-Object { ($_ -split '\(')[0] -cin $selectedMethods })
  $selectedArguments=@('-Task','fault','-Service','learning','-EvidenceTask','R04d','-Case',$selectedCanonical,'-LabConfig',$labConfig,'-TrustConfig',$trustConfig)+$javaArguments
  Write-CaseFixture $canonicalReportPath $selectedNames
  Check-Command "Canonical selector $selectedCanonical exact variants accepted" $helper $selectedArguments 0 "integration=$($selectedNames.Count)"
  $selectedCommands=@(Get-Content -LiteralPath (Join-Path $testRoot 'scripts/native-arguments.jsonl') | ForEach-Object { ConvertFrom-Json $_ })
  $selectedDriver=@($selectedCommands | Where-Object { $_.arguments -contains '-Pcanonical-it' })
  $selectedTokens=@($selectedDriver[0].arguments | Where-Object { $_ -like '-Dit.test=*' })
  if($selectedCommands.Count -ne 3 -or $selectedDriver.Count -ne 1 -or $selectedTokens.Count -ne 1 -or
    $selectedTokens[0] -cne "-Dit.test=CanonicalThreeServiceRuntimeIT#$($selectedMethods -join '+')" -or
    $selectedDriver[0].arguments -contains '-' -or $selectedDriver[0].arguments -contains '-Ptrust-it' -or
    $selectedDriver[0].arguments -contains '-Pdb-it') { throw 'Canonical native selector/profile was split or routed to legacy layer' }
  Write-CaseFixture $canonicalReportPath @($selectedNames | Select-Object -Skip 1)
  # Selector một variant (DATA15) mất ca duy nhất thì report0 bị cổng nonzero
  # chặn trước named-case gate. Không nới helper hoặc giả zero thành missing.
  $missingSelectedText=if($selectedNames.Count -eq 1){'suite ran zero tests'}else{'Required canonical runtime case/variant missing'}
  Check-Command "Canonical selector $selectedCanonical missing variant rejected" $helper $selectedArguments 1 $missingSelectedText
  Write-CaseFixture $canonicalReportPath @($selectedNames+'unexpectedMethod')
  Check-Command "Canonical selector $selectedCanonical extra variant rejected" $helper $selectedArguments 1 'Unexpected canonical runtime case'
  Write-CaseFixture $canonicalReportPath $canonicalFixtureNames
  Check-Command "Canonical selector $selectedCanonical rejects unselected baseline XML" $helper $selectedArguments 1 'Unexpected canonical runtime case'
}
Write-CaseFixture $canonicalReportPath $canonicalFixtureNames
Check-Command 'Canonical unknown owner layer still rejected' $helper @('-Task','fault','-Service','learning','-EvidenceTask','R04d','-Case','WIRE-03') 1 'other layers pending'
Check-Command 'Canonical selector cannot route through non-driver service' $helper @('-Task','fault','-Service','content','-EvidenceTask','R04d','-Case','SEC-08') 1 'other layers pending'
$evidenceDirectory = Join-Path $repoDirectory "artifacts/quality/$EvidenceTask"
New-Item -ItemType Directory -Path $evidenceDirectory -Force | Out-Null
$helperReportName=if($CanonicalOnly){'helper-canonical-tests.json'}elseif($RelayOnly){'helper-relay-tests.json'}else{'helper-tests.json'}
@{scope='Helper/setup report gates with isolated command/report doubles; not actual DB/application/distributed proof';relayOnly=[bool]$RelayOnly;canonicalOnly=[bool]$CanonicalOnly;fixtureCommandTimeoutMs=$fixtureCommandTimeoutMs;cases=$results;fixtureDirectory=$testRoot} |
  ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidenceDirectory $helperReportName) -Encoding utf8
Write-Host "Helper tests passed: $($results.Count). Fixtures retained in ignored repo cache; evidence $EvidenceTask."
