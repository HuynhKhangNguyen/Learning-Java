#requires -Version 7.4
[CmdletBinding()]
param(
  [Parameter(Mandatory)]
  [ValidateSet('toolchain', 'validate', 'verify', 'db', 'contract', 'fault','relay')]
  [string]$Task,
  [ValidateSet('identity', 'content', 'learning', 'all')]
  [string]$Service,
  [string]$JavaHome,
  [string]$LabConfig,
  [string]$TrustConfig,
  [ValidateSet('SEC-01','SEC-02','SEC-03','SEC-04','SEC-05','SEC-06','SEC-07','SEC-08','SEC-09','SEC-10','SEC-11','SEC-12','SEC-14','SEC-15','SEC-16','SEC-19','SEC-20','SEC-22','SEC-23','WIRE-01','WIRE-02','WIRE-03','WIRE-04','WIRE-05','WIRE-06','DATA-01','DATA-02','DATA-03','DATA-04','DATA-05','DATA-06','DATA-07','DATA-10','DATA-11','DATA-12','DATA-13','DATA-14','DATA-15')]
  [string]$Case,
  [ValidateSet('R02a','R02b','R02c','R03','R04a','R04b','R04c','R04d','R05','R06a','R06b','R06c')]
  [string]$EvidenceTask,
  [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repoDirectory = Split-Path -Parent $PSScriptRoot
# Catalog R05 độc lập với Java/PG/trust: branch explicit trước mọi bootstrap
# bên dưới. Không đổi nghĩa CMD04 legacy, không gắn schema PASS thành HTTP PASS.
if ($EvidenceTask -eq 'R05') {
  if ($Task -ne 'contract' -or $Service -ne 'content' -or $LabConfig -or $TrustConfig -or $Case -or $JavaHome) {
    throw 'R05 requires standalone contract Content without Java/DB/trust/case overrides.'
  }
  $catalogNodeCandidates = @()
  $catalogInstalledNode = Get-Command node.exe -ErrorAction SilentlyContinue
  if ($catalogInstalledNode) { $catalogNodeCandidates += $catalogInstalledNode.Source }
  if ($env:USERPROFILE) { $catalogNodeCandidates += Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe' }
  $catalogNode = $catalogNodeCandidates | Where-Object {
    if (Test-Path -LiteralPath $_ -PathType Leaf) {
      $catalogVersion = & $_ --version
      $LASTEXITCODE -eq 0 -and $catalogVersion -match '^v(2[4-9]|[3-9][0-9])\.'
    }
  } | Select-Object -First 1
  if (-not $catalogNode) { throw 'Catalog requires existing Node24+; no runtime is downloaded.' }
  & $catalogNode (Join-Path $PSScriptRoot 'catalog/contract.mjs')
  exit $LASTEXITCODE
}
$backendDirectory = Join-Path $repoDirectory 'backend'
$manifestPath = Join-Path $repoDirectory 'ops/release/dependencies.json'
$exitCode = 1
$savedEnvironment = @{}
foreach ($variableName in @('JAVA_HOME', 'PATH', 'MAVEN_USER_HOME')) {
  $savedEnvironment[$variableName] = [Environment]::GetEnvironmentVariable($variableName, 'Process')
}
$locationPushed = $false
$relayBaseCases = @('aaOwnProducerCliAndPackagedReceiverCommitBothLocalEffects','abDiscardedAckAndRestartResendExactMessageWithoutSecondEffect','acPublicWrongCallerAndBrowserHeadersCannotDeliver','adFutureGapAndBadDigestAreNoEffectDurablePoison','afActualProducerAbortAndReceiverRecoveryPreserveBytesAndNoEffect','agDispatcherProcessCrashBeforeAndAfterReceiverCommit','ahBoundedNetworkFailuresNeverAcknowledgeOrLeakMessage','aiWrongAckFromAuthenticatedPeerCannotMarkDelivered','ajWrongReceiverCertificateCannotMarkDelivered')
$relayCaseMap = @{
  'DATA-10'=@('aaOwnProducerCliAndPackagedReceiverCommitBothLocalEffects','agDispatcherProcessCrashBeforeAndAfterReceiverCommit')
  'DATA-11'=@('abDiscardedAckAndRestartResendExactMessageWithoutSecondEffect')
  'DATA-12'=@('adFutureGapAndBadDigestAreNoEffectDurablePoison')
  'DATA-13'=@('acPublicWrongCallerAndBrowserHeadersCannotDeliver','adFutureGapAndBadDigestAreNoEffectDurablePoison','afActualProducerAbortAndReceiverRecoveryPreserveBytesAndNoEffect','ahBoundedNetworkFailuresNeverAcknowledgeOrLeakMessage','ajWrongReceiverCertificateCannotMarkDelivered')
  'DATA-14'=@('aiWrongAckFromAuthenticatedPeerCannotMarkDelivered')
  'DATA-15'=@('aeAuthLanePassesActualInt02WhileStrictPoisonRemains')
}
# Giữ kiểu mảng ngay cả khi selector chỉ chọn một method; PowerShell có thể
# tự bỏ lớp mảng của một phần tử, khiến native splat tách string thành ký tự.
[string[]]$relayRequiredCases = @()
$relayMinimumCases = @{ 'agDispatcherProcessCrashBeforeAndAfterReceiverCommit'=2; 'ahBoundedNetworkFailuresNeverAcknowledgeOrLeakMessage'=4; 'aiWrongAckFromAuthenticatedPeerCannotMarkDelivered'=7; 'ajWrongReceiverCertificateCannotMarkDelivered'=2 }
# CMD05 chỉ chọn lớp canonical đã có ca thật; không đổi SEC của receiver cũ
# thành canonical, hoặc gọi probe transaction là nghiệp vụ T1/T2 đã triển khai.
$canonicalCaseMap=@{
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
$canonicalRuntime = $EvidenceTask -eq 'R04d' -and $Service -eq 'learning' -and
  ($Task -eq 'contract' -or ($Task -eq 'fault' -and $Case -and $canonicalCaseMap.ContainsKey($Case)))
# Identity SEC10/12 chọn nguyên own-core regression bundle: canonical clocks,
# KDF/reset/disable/rollback cùng foundation suites. Không pretend một wire
# selector hoặc mTLS qualification; mọi required suite/case vẫn phải chạy đủ.
$canonicalCoreFault=$EvidenceTask -eq 'R04d' -and $Task -eq 'fault' -and $Service -eq 'identity' -and $Case -in @('SEC-10','SEC-12')
$databaseQualification=$Task -eq 'db' -or $canonicalCoreFault
if ($EvidenceTask -eq 'R06a' -and ($Service -ne 'content' -or $Task -notin @('db','contract'))) { throw 'R06a supports only Content CMD-03 db or CMD-04 contract.' }
if ($EvidenceTask -eq 'R06b' -and ($Service -ne 'learning' -or $Task -notin @('db','contract','fault'))) { throw 'R06b supports Learning CMD-03/04/05 only.' }
if ($EvidenceTask -eq 'R06b' -and (($Task -eq 'contract' -and $Case) -or ($Task -eq 'fault' -and $Case -notin @('DATA-01','DATA-02','SEC-16')))) { throw 'R06b contract is full baseline; fault requires DATA-01, DATA-02 or SEC-16.' }
if ($EvidenceTask -eq 'R06c' -and ($Service -ne 'content' -or $Task -notin @('db','contract','fault'))) { throw 'R06c supports Content CMD-03/04/05 only.' }
if ($EvidenceTask -eq 'R06c' -and $Task -in @('db','contract') -and $Case) { throw 'R06c CMD-03/04 require their full named baselines.' }
$publicationDbCases=@(
  'publicationDbFaultRollsBackEveryWriteAndKeepsGoodHead',
  'publicationDifferentOperationsSerializeAndReplacementAfterReleasedReservationCanPrepare',
  'publicationExactRetryDetectsCandidateAndManifestConflictAndKeepsGoodHead',
  'publicationRuntimeRoleCannotMutateImmutableRowsHeadOrReservation'
)
$coordinatorDbCases=@(
  'publicationZCoordinatorCommitsFenceWithReleaseAndCompletesOnlyAfterReleasedReceipt'
)
$coordinatorRuntimeCases=@(
  'packagedCoordinatorRecoversParticipantDownLeaseAndCompletesExactRelease'
)
$coordinatorFaultMap=@{
  'DATA-02'='data02GateBeforeCommitKeepsLearningPendingUntilExactRelease'
  'DATA-03'='data03ParticipantDownBeforeFenceAckKeepsCanonicalAndRecovers'
  'DATA-04'='data04CanonicalAheadPendingSurvivesRestartAndCompletesRelease'
  'DATA-05'='data05AbortAndNewOperationUseExactGlobalPrefix'
  'DATA-06'='data06WithdrawalManifestRemainsExactThroughCoordinator'
  'DATA-07'='data07InvalidRubricManifestRemainsExactThroughCoordinator'
  'DATA-14'='data14AuthenticatedWrongReceiptBindingsHaveNoEffect'
}
[string[]]$coordinatorRequiredCases=if($EvidenceTask -eq 'R06c' -and $Task -eq 'fault' -and $Case){
  if(-not $coordinatorFaultMap.ContainsKey($Case)){throw 'R06c CMD-05 requires DATA-02, DATA-03, DATA-04, DATA-05, DATA-06, DATA-07 or DATA-14.'}
  @($coordinatorFaultMap[$Case])
}else{@($coordinatorRuntimeCases)}
$learningPublicationDbCases=@(
  'stageFenceReleaseAreAtomicDurableAndExactRetryConverges',
  'changedRetryOutOfOrderAndFailureLeaveLastGoodReplicaUntouched',
  'twoConnectionStageRaceCommitsOneReplicaAndOneReceipt',
  'durableFenceReturnsPublicationPendingAfterStoreRestartAndAbortKeepsHead',
  'appRoleCannotMutateImmutableRowsOrSecurityGeneration'
)
$learningPublicationRuntimeCases=@(
  'packagedStageFenceReleaseStatusAndLostAckRetriesConverge',
  'privateProviderRejectsPublicWrongCallerHeadersRoutesAndCertificatesBeforeEffect',
  'invalidPrivateBundleAndContentOutageLeaveLastGoodReplicaUntouched'
)
$learningPublicationFaultMap=@{
  'DATA-01'='invalidPrivateBundleAndContentOutageLeaveLastGoodReplicaUntouched'
  'DATA-02'='packagedStageFenceReleaseStatusAndLostAckRetriesConverge'
  'SEC-16'='privateProviderRejectsPublicWrongCallerHeadersRoutesAndCertificatesBeforeEffect'
}
$publicationValidatorCases=@(
  'validCandidatePreservesInputAndUsesR05PrivateCaseContract',
  'reviewSubjectBindsEverySemanticMetadataFieldAndValidatesReportIds',
  'manifestSupportsMultipleVersionsAndRejectsEveryPolicyContradiction',
  'referencesDagAndPrivatePayloadBindingsFailClosed',
  'unknownDuplicateMalformedUtf8AndBoundsFailClosed',
  'executableJsonSchemaMatchesStrictJavaStructuralCorpus'
)
$publicationRuntimeCases=@(
  'exactBundleAndManifestResourcesReturnStoredBytesAndBoundedMetadata',
  'largestValidManifestUsesBodyResourceAndOversizeIsRejected',
  'unknownUnvalidatedAndCorruptStoredRowsFailClosed',
  'publicWrongCallerHeadersMethodQueryAndAmbiguousPathFailClosed',
  'wrongCaEkuSanEnvironmentAndNoCertificateCannotReachPayload',
  'packagedCliValidatesPreparesExactRetryAndRejectsChecksumMismatch',
  'packagedCliRejectsDatabaseFaultAndConflictingRetryAtomically'
)
# Learning giữ driver test, không sở hữu canonical authority. Required21cases
# gồm sensitive actions/binding/ACK/cert/browser, chưa toàn bộ AC/DoD.
$canonicalRuntimeCases = @{
  'aaCanonicalProviderDrivesBothOwnAccessHooks'=1
  'abMissingReceiverKeepsPendingAndRestartCompletesExactBarrier'=1
  'acPublicProofAndLearningOnlyReservationAreDurablyBound'=1
  'adActualMutationGateDelaysFenceAndCompletionUntilCommitOrRollback'=2
  'aeLostAckAndBothProcessRestartsReuseExactDurableCommand'=1
  'afActualSensitiveProofAndAccountBarrierSurviveRestart'=2
  'agCanonicalProviderRejectsCallerContextCsrfAndRequestBindings'=1
  'ahLearningReservationRejectsEveryBindingBeforeAndAfterRestart'=1
  'aiSensitiveProofRejectsCrossPurposePayloadAndEpochOverTls'=2
  'ajWrongAuthenticatedAckCannotCompleteOrBlockIndependentBarrier'=2
  'akWrongReceiverCertificateCannotCompleteBarrier'=2
  'alCanonicalCompletionPassesStrictPoisonWithoutClearingItsStream'=1
  'amLegacyCredentialReconciliationUsesActualWorkerAndKeepsLifecycleGate'=2
  'azActualSensitiveBrowserCookieReorderAndMultiTab'=2
}
$canonicalBrowserCases=@('browser-A-active','host-secure-httponly-cookie','browser-js-cannot-read-cookie','second-tab-A-binding','natural-origin-sensitive-proof','pending-without-cookie','pending-bootstrap-locked','pending-blocks-new-credential-flow','browser-B-independent-owner','late-A-no-cookie-B-survives','cross-cookie-retry-denied','cross-cookie-status-denied','second-tab-observes-B','old-CSRF-cannot-mint-under-B','Back-focus-no-cookie-renewal','initiating-status-completed-only','wrong-context-status-denied','original-bound-sensitive-retry-completed','old-A-cookie-never-resurrects','completed-A-does-not-revoke-B','foreign-origin-denied','no-status-proof-sensitive-cookie-emission','no-browser-runtime-errors')
[string[]]$canonicalRequiredMethods=if($Task -eq 'fault' -and $canonicalRuntime){@($canonicalCaseMap[$Case])}else{@($canonicalRuntimeCases.Keys | Sort-Object)}
$canonicalNeedsBrowser='azActualSensitiveBrowserCookieReorderAndMultiTab' -in $canonicalRequiredMethods

try {
  # Chỉ mở hai layer có suite riêng; không gắn nhãn canonical cho fixture cũ.
  if ($EvidenceTask -eq 'R04d' -and -not (($Task -eq 'db' -and $Service -eq 'identity') -or $canonicalRuntime -or $canonicalCoreFault)) { throw 'R04d requires db Identity or contract Learning canonical driver; other layers pending.' }
  if($canonicalCoreFault -and $TrustConfig){throw 'Identity canonical core fault does not accept ignored TrustConfig.'}
  if ($canonicalRuntime -and $Task -eq 'contract' -and $Case) { throw 'R04d contract requires the full baseline; use fault for implemented canonical selectors.' }
  if ($Task -notin @('db','contract','fault','relay') -and $LabConfig) { throw '-LabConfig is only valid with db/contract/fault/relay.' }
  if ($Task -notin @('contract','fault','relay') -and ($TrustConfig -or $Case)) { throw '-TrustConfig/-Case requires contract/fault/relay.' }
  if ($EvidenceTask -eq 'R06a') {
    if ($Case) { throw 'R06a does not accept selected -Case; CMD-03/CMD-04 require the full named baseline.' }
    if ($TrustConfig -and -not $LabConfig) { throw 'R06a TrustConfig cannot be supplied without its owned LabConfig.' }
    if ($Task -eq 'db' -and $TrustConfig) { throw 'R06a CMD-03 does not accept ignored TrustConfig.' }
  }
  if ($EvidenceTask -eq 'R06c') {
    if ($Task -in @('db','contract') -and $Case) {
      throw 'R06c CMD-03/04 require their full named baselines.'
    }
    if ($Task -eq 'fault' -and -not $Case) {
      throw 'R06c CMD-05 requires an implemented DATA selector.'
    }
  }
  if ($Task -eq 'relay' -and $Case -and -not $relayCaseMap.ContainsKey($Case)) { throw 'FX04 relay has no implemented case selector for that case.' }
  if ($EvidenceTask -notin @('R06b','R06c') -and -not $canonicalRuntime -and $Case -like 'DATA-*' -and $Task -ne 'relay') { throw 'DATA foundation selectors require relay.' }
  if ($Task -eq 'relay') {
    if ($Case -eq 'DATA-15' -and $Service -notin @('content','learning')) { throw 'DATA15 relay selector requires Content or Learning receiver; canonical coordinator is R04d.' }
    $relayRequiredCases = if ($Case) { @($relayCaseMap[$Case]) } else { @($relayBaseCases) }
    if (-not $Case -and $Service -in @('content','learning')) { $relayRequiredCases += 'aeAuthLanePassesActualInt02WhileStrictPoisonRemains' }
  }
  if ($EvidenceTask -notin @('R06b','R06c') -and -not $canonicalRuntime -and -not $canonicalCoreFault -and $Task -eq 'fault' -and $Case -notin @('SEC-01','SEC-02','SEC-03','SEC-04','SEC-05','SEC-09','SEC-12','SEC-14','SEC-15','SEC-20','SEC-22')) { throw 'fault requires an implemented case; other layers are pending.' }
  if (-not $canonicalRuntime -and $Case -in @('SEC-01','SEC-03','SEC-05') -and ($Task -ne 'fault' -or $Service -ne 'learning')) { throw 'Mutation admission selectors require fault and the Learning receiver.' }
  if ($Case -in @('SEC-02','SEC-04','SEC-09') -and ($Task -ne 'fault' -or $Service -notin @('content','learning'))) { throw 'Admission receiver selectors require fault and Content or Learning.' }
  $receiverDbFault = -not $canonicalRuntime -and $Task -eq 'fault' -and $Case -in @('SEC-03','SEC-04')
  $credentialFault = -not $canonicalRuntime -and -not $canonicalCoreFault -and $Task -eq 'fault' -and $Case -in @('SEC-12','SEC-22')
  if (-not $canonicalRuntime -and -not $canonicalCoreFault -and $Case -in @('SEC-12','SEC-22') -and -not $credentialFault) { throw 'Credential case selectors require fault.' }
  if ($Task -in @('db','contract','fault','relay') -and -not $LabConfig) {
    if (-not $Service) { throw 'db requires -Service identity, content, learning or all.' }
    $python = Get-Command python -ErrorAction Stop
    $labArguments = @('-X', 'utf8', (Join-Path $repoDirectory 'scripts/db_lab.py'), '--service', $Service)
    $labArguments += @('--task', $Task)
    if ($Case) { $labArguments += @('--case', $Case) }
    if ($EvidenceTask) { $labArguments += @('--evidence-task', $EvidenceTask) }
    if ($Offline) { $labArguments += '--offline' }
    if ($JavaHome) { throw 'Portable DB lab uses the pinned JDK; qualify overrides separately.' }
    & $python.Source @labArguments
    $exitCode = $LASTEXITCODE
    exit $exitCode
  }
  if ($Task -in @('verify', 'db', 'contract', 'fault','relay')) {
    if (-not $Service) { throw 'verify requires -Service identity, content or learning.' }
    if ($Service -eq 'all') { throw 'all is only valid for the managed db lab, not a selected build.' }
    $modulePath = "services/$Service-service"
    $servicePom = Join-Path $backendDirectory "$modulePath/pom.xml"
    if (-not (Test-Path -LiteralPath $servicePom -PathType Leaf)) {
      throw "Service $Service has not been created. Complete its R01 bootstrap task before CMD-02."
    }
    [xml]$parentPom = Get-Content -LiteralPath (Join-Path $backendDirectory 'pom.xml') -Raw
    $pomNamespaces = [Xml.XmlNamespaceManager]::new($parentPom.NameTable)
    $pomNamespaces.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')
    $declaredModules = @($parentPom.SelectNodes('/m:project/m:modules/m:module', $pomNamespaces) | ForEach-Object { $_.InnerText })
    if ($modulePath -notin $declaredModules) { throw "Module $modulePath is not registered in backend/pom.xml." }
    if ($Task -in @('db','contract','fault','relay')) {
      $resolvedConfig = (Resolve-Path -LiteralPath $LabConfig).Path
      $labRoot = [IO.Path]::GetFullPath((Join-Path $repoDirectory '.tools/db-lab')) + [IO.Path]::DirectorySeparatorChar
      if (-not $resolvedConfig.StartsWith($labRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'db config must be inside the owned workspace .tools/db-lab directory.'
      }
      $runDirectory = Split-Path -Parent $resolvedConfig
      $marker = Get-Content -LiteralPath (Join-Path $runDirectory 'ownership.json') -Raw | ConvertFrom-Json
      if ($marker.workspace -ne $repoDirectory -or $marker.dataDirectory -ne (Join-Path $runDirectory 'data') -or
          $marker.runId -notmatch '^[0-9a-f-]{36}$') { throw 'db lab ownership marker mismatch.' }
      if ($Task -in @('contract','fault','relay') -and -not $canonicalCoreFault -and -not $credentialFault -and -not $receiverDbFault) {
        if (-not $TrustConfig) { throw 'Required trust config missing.' }
        $resolvedTrust = (Resolve-Path -LiteralPath $TrustConfig).Path
        $trustRoot = [IO.Path]::GetFullPath((Join-Path $repoDirectory '.tools/trust-lab')) + [IO.Path]::DirectorySeparatorChar
        if (-not $resolvedTrust.StartsWith($trustRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'Trust config must be inside owned .tools/trust-lab.' }
        $trustMarker = Get-Content -LiteralPath (Join-Path (Split-Path -Parent $resolvedTrust) 'ownership.json') -Raw | ConvertFrom-Json
        if ($trustMarker.workspace -ne $repoDirectory -or $trustMarker.runId -notmatch '^[0-9a-f-]{36}$') { throw 'Trust ownership marker mismatch.' }
      }
    }
  } elseif ($Service) {
    throw '-Service is only valid with -Task verify; validate checks the parent foundation.'
  }

  $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
  $javaSuffix = if ($IsWindows) { '.exe' } else { '' }
  if ($JavaHome) {
    $selectedJavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
  } else {
    $portableJavaHome = Join-Path $repoDirectory $manifest.toolchain.jdk.installDirectory
    if ($IsWindows -and (Test-Path -LiteralPath $portableJavaHome -PathType Container)) {
      $selectedJavaHome = $portableJavaHome
    } elseif ($env:JAVA_HOME) {
      $selectedJavaHome = (Resolve-Path -LiteralPath $env:JAVA_HOME).Path
    } else {
      $javaCommand = Get-Command "java$javaSuffix" -ErrorAction SilentlyContinue
      if (-not $javaCommand) { throw 'JDK 21 not found. Run scripts/setup-backend.ps1 or specify -JavaHome.' }
      $javaSettings = & $javaCommand.Source -XshowSettings:properties -version 2>&1 | Out-String
      if ($LASTEXITCODE -ne 0 -or $javaSettings -notmatch '(?m)^\s*java\.home\s*=\s*(.+?)\s*$') {
        throw 'Could not determine java.home. Specify -JavaHome pointing to a JDK 21.'
      }
      $selectedJavaHome = $Matches[1].Trim()
    }
  }
  $javaExecutable = Join-Path $selectedJavaHome "bin/java$javaSuffix"
  $javacExecutable = Join-Path $selectedJavaHome "bin/javac$javaSuffix"
  if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf) -or
      -not (Test-Path -LiteralPath $javacExecutable -PathType Leaf)) {
    throw 'Selected JavaHome must contain both bin/java and bin/javac (a JDK, not a JRE).'
  }
  $javaVersion = & $javaExecutable -version 2>&1 | Out-String
  if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch 'version "21(?:\.|"|-)') {
    throw 'Selected JavaHome is not JDK 21. Use scripts/setup-backend.ps1 or -JavaHome.'
  }
  $compilerVersion = & $javacExecutable -version 2>&1 | Out-String
  if ($LASTEXITCODE -ne 0 -or $compilerVersion -notmatch '^javac 21(?:\.|\s|-)') {
    throw 'Selected compiler is not javac 21.'
  }

  $env:JAVA_HOME = $selectedJavaHome
  $env:PATH = "$(Join-Path $selectedJavaHome 'bin')$([IO.Path]::PathSeparator)$env:PATH"
  $env:MAVEN_USER_HOME = Join-Path $repoDirectory '.tools/maven-user-home'
  $localRepository = Join-Path $repoDirectory '.tools/m2/repository'
  $wrapper = Join-Path $backendDirectory $(if ($IsWindows) { 'mvnw.cmd' } else { 'mvnw' })
  Push-Location $backendDirectory
  $locationPushed = $true
  $mavenArguments = @('--batch-mode', '--no-transfer-progress', '-Dstyle.color=never', "-Dmaven.repo.local=$localRepository")
  if ($Offline) { $mavenArguments += '--offline' }
  Write-Host "Task: $Task; JDK: $selectedJavaHome"
  switch ($Task) {
    'toolchain' {
      Write-Host $javaVersion.Trim()
      Write-Host $compilerVersion.Trim()
      & $wrapper @mavenArguments --version
    }
    'validate' { & $wrapper @mavenArguments -N validate }
    'verify' { & $wrapper @mavenArguments -pl $modulePath -am clean verify }
    'relay' {
      # FX04 driver uses named peer artifacts but no shared Java imports/JAR.
      # Prepare siblings sequentially, then selected own packaged receiver/test.
      foreach ($relayPeer in @('identity','content','learning') | Where-Object { $_ -ne $Service }) {
        & $wrapper @mavenArguments -pl "services/$relayPeer-service" -am clean verify
        if ($LASTEXITCODE -ne 0) { throw 'Required own peer artifact build failed before relay.' }
      }
      # Một token -Dit.test chứa cả danh sách method; không để native splat tách token này.
      [string[]]$relaySelectorArguments = @()
      if ($Case) { $relaySelectorArguments = @("-Dit.test=DeliveryRuntimeIT#$($relayRequiredCases -join '+')") }
      & $wrapper @mavenArguments @relaySelectorArguments -pl $modulePath -am clean verify -Prelay-it "-Dtrust.config=$resolvedTrust" "-Dlab.config=$resolvedConfig"
    }
    { $_ -eq 'db' -or $canonicalCoreFault } {
      & $wrapper @mavenArguments -pl $modulePath -am clean verify -Pdb-it "-Dlab.config=$resolvedConfig"
      if ($LASTEXITCODE -ne 0) { throw 'Fresh DB/migration suite failed; delivery phase must not run.' }
      # No clean: preserve the first phase reports, migration proof and same owned DB.
      [string[]]$deliveryUnitSelector = @()
      if ($EvidenceTask -eq 'R06c') {
        # Phase DB đầu đã chạy full unit/bootstrap. R06c chỉ chạy lại codec mà
        # coordinator sở hữu trong phase regression, tránh mở thêm HTTP listener
        # không liên quan trong cùng candidate nhưng vẫn giữ DeliveryStoreIT.
        $deliveryUnitSelector = @('-Dtest=PublicationCoordinatorWireTests')
      }
      & $wrapper @mavenArguments @deliveryUnitSelector -pl $modulePath -am verify -Pdelivery-it "-Dlab.config=$resolvedConfig"
      if ($LASTEXITCODE -eq 0 -and $EvidenceTask -eq 'R06b') {
        & $wrapper @mavenArguments -pl $modulePath -am verify -Ppublication-db-it "-Dlab.config=$resolvedConfig"
      }
      break
    }
    { $canonicalRuntime -and $_ -in @('contract','fault') } {
        # Mỗi peer có fresh build và gate units của helper riêng trước launch.
        # Chạy process pwsh con để exit của helper không kết thúc driver cha.
        foreach ($canonicalPeer in @('identity','content')) {
          $peerArguments = @('-NoProfile','-File',$PSCommandPath,'-Task','verify','-Service',$canonicalPeer,'-JavaHome',$selectedJavaHome)
          if ($Offline) { $peerArguments += '-Offline' }
          & (Get-Command pwsh -ErrorAction Stop).Source @peerArguments
          if ($LASTEXITCODE -ne 0) { throw "Required canonical peer build/report gate failed: $canonicalPeer; not PASS." }
        }
        # Một token native chứa exact danh sách method, gồm đủ variants của
        # parameterized tests. Không tách string thành ký tự khi chỉ một method.
        [string[]]$canonicalSelectorArguments=@()
        if($Task -eq 'fault'){$canonicalSelectorArguments=@("-Dit.test=CanonicalThreeServiceRuntimeIT#$($canonicalRequiredMethods -join '+')")}
        & $wrapper @mavenArguments @canonicalSelectorArguments -pl $modulePath -am clean verify -Pcanonical-it "-Dtrust.config=$resolvedTrust" "-Dlab.config=$resolvedConfig"
        break
    }
    { $EvidenceTask -eq 'R06b' -and $_ -in @('contract','fault') } {
      # INT-03 consumer phải gọi packaged Content thật; build peer tuần tự để
      # không có hai writer và để Learning driver dùng exact sibling artifact.
      & $wrapper @mavenArguments -pl 'services/content-service' -am clean verify
      if ($LASTEXITCODE -ne 0) { throw 'Required R06b packaged Content peer build failed; not PASS.' }
      [string[]]$publicationSelector=@()
      if($Task -eq 'fault'){$publicationSelector=@("-Dit.test=PublicationRuntimeIT#$($learningPublicationFaultMap[$Case])")}
      & $wrapper @mavenArguments @publicationSelector -pl $modulePath -am clean verify -Ppublication-it '-Dtest=PublicationWireTests,ReplicaValidatorTests,PublicationGateTests,ContentBundleClientTests' "-Dtrust.config=$resolvedTrust" "-Dlab.config=$resolvedConfig"
      break
    }
    { $EvidenceTask -eq 'R06c' -and $_ -in @('contract','fault') } {
      # Coordinator driver dùng packaged Learning INT-04 và packaged Content
      # INT-03. Build peer trước, giữ artifact cho runtime test phía Content.
      & $wrapper @mavenArguments -pl 'services/learning-service' -am clean verify
      if ($LASTEXITCODE -ne 0) { throw 'Required R06c packaged Learning peer build failed; not PASS.' }
      [string[]]$coordinatorSelector=@(
        "-Dit.test=PublicationCoordinatorRuntimeIT#$($coordinatorRequiredCases -join '+')"
      )
      [string[]]$coordinatorCaseProperty=@()
      if($Task -eq 'fault'){
        $coordinatorCaseProperty=@("-Dr06c.case=$Case")
      }
      & $wrapper @mavenArguments @coordinatorSelector @coordinatorCaseProperty -pl $modulePath -am clean verify -Pcoordinator-it '-Dtest=PublicationCoordinatorWireTests' "-Dtrust.config=$resolvedTrust" "-Dlab.config=$resolvedConfig"
      break
    }
    'contract' {
      if($EvidenceTask -eq 'R06a'){
        & $wrapper @mavenArguments -pl $modulePath -am clean verify -Ptrust-it '-Dtest=CandidateValidatorTests' '-Dit.test=PublicationRuntimeIT' "-Dtrust.config=$resolvedTrust" "-Dlab.config=$resolvedConfig"
      } else {
        & $wrapper @mavenArguments -pl $modulePath -am clean verify -Ptrust-it "-Dtrust.artifact=true" "-Dtrust.config=$resolvedTrust" "-Dlab.config=$resolvedConfig"
      }
    }
    'fault' {
      if ($Case -in @('SEC-12','SEC-22')) {
        if ($Service -ne 'identity') { throw "$Case is owned by identity credential workflow." }
        & $wrapper @mavenArguments -pl $modulePath -am clean verify -Pcredential-it "-Dlab.config=$resolvedConfig" "-Dcredential.case=$Case"
      } elseif ($receiverDbFault) {
        & $wrapper @mavenArguments -pl $modulePath -am clean verify -Pdb-it "-Dlab.config=$resolvedConfig"
      } else {
        & $wrapper @mavenArguments -pl $modulePath -am clean verify -Ptrust-it "-Dtrust.artifact=true" "-Dtrust.config=$resolvedTrust" "-Dlab.config=$resolvedConfig" "-Dtrust.case=$Case"
      }
    }
  }
  $exitCode = $LASTEXITCODE
  if ($exitCode -eq 0 -and $Task -in @('verify', 'db', 'contract', 'fault','relay')) {
    $reports = @(Get-ChildItem -LiteralPath (Join-Path $backendDirectory "$modulePath/target/surefire-reports") -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)
    if ($reports.Count -eq 0) { throw 'CMD-02 has no JUnit reports. Missing or skipped suite is not PASS.' }
    if ($EvidenceTask -eq 'R06b' -and $Task -in @('contract','fault')) {
      $expectedPublicationUnitNames=@(
        'TEST-lab.backend.learning.contentreplica.ContentBundleClientTests.xml',
        'TEST-lab.backend.learning.contentreplica.PublicationGateTests.xml',
        'TEST-lab.backend.learning.contentreplica.PublicationWireTests.xml',
        'TEST-lab.backend.learning.contentreplica.ReplicaValidatorTests.xml'
      )
      if($reports.Count -ne $expectedPublicationUnitNames.Count -or @($reports | Where-Object Name -NotIn $expectedPublicationUnitNames).Count){throw 'R06b publication unit suite missing or unexpected; not PASS.'}
    }
    if ($EvidenceTask -eq 'R06c' -and $Task -in @('contract','fault')) {
      $expectedCoordinatorUnitNames=@(
        'TEST-lab.backend.content.publication.PublicationCoordinatorWireTests.xml'
      )
      if($reports.Count -ne $expectedCoordinatorUnitNames.Count -or
          @($reports | Where-Object Name -NotIn $expectedCoordinatorUnitNames).Count){
        throw 'R06c coordinator unit suite missing or unexpected; not PASS.'
      }
    }
    $dbTests = 0
    if ($databaseQualification) {
      $dbReports = @(Get-ChildItem -LiteralPath (Join-Path $backendDirectory "$modulePath/target/failsafe-reports") -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)
      $requiredDbReports = @("TEST-lab.backend.$Service.database.DatabaseIsolationIT.xml")
      $requiredDbReports += "TEST-lab.backend.$Service.delivery.DeliveryStoreIT.xml"
      if ($EvidenceTask -eq 'R06b') { $requiredDbReports += 'TEST-lab.backend.learning.contentreplica.PublicationStoreIT.xml' }
      if ($Service -eq 'identity') { $requiredDbReports += @('TEST-lab.backend.identity.credential.IdentityCredentialWorkflowIT.xml','TEST-lab.backend.identity.credential.CredentialPackagedIT.xml','TEST-lab.backend.identity.credential.IdentitySessionAuthorityIT.xml') }
      if ($Service -in @('content','learning')) { $requiredDbReports += "TEST-lab.backend.$Service.identityaccess.AdmissionGateIT.xml" }
      if ($dbReports.Count -ne $requiredDbReports.Count -or @($dbReports | Where-Object Name -NotIn $requiredDbReports).Count) {
        throw 'CMD-03 required service DB suite missing; no skipped/missing suite PASS.'
      }
      $reports += $dbReports
    }
    if (-not $canonicalCoreFault -and $Task -in @('contract','fault','relay')) {
      $trustReports = @(Get-ChildItem -LiteralPath (Join-Path $backendDirectory "$modulePath/target/failsafe-reports") -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)
      if ($EvidenceTask -eq 'R06a') {
        $expectedTrustNames = @('TEST-lab.backend.content.publication.PublicationRuntimeIT.xml')
      } elseif ($EvidenceTask -eq 'R06b') {
        $expectedTrustNames = @('TEST-lab.backend.learning.contentreplica.PublicationRuntimeIT.xml')
      } elseif ($EvidenceTask -eq 'R06c') {
        $expectedTrustNames = @('TEST-lab.backend.content.publication.PublicationCoordinatorRuntimeIT.xml')
      } elseif ($canonicalRuntime) {
        $expectedTrustNames = @('TEST-lab.backend.learning.identityaccess.CanonicalThreeServiceRuntimeIT.xml')
      } elseif ($Task -eq 'relay') {
        $expectedTrustNames = @("TEST-lab.backend.$Service.delivery.DeliveryRuntimeIT.xml")
      } elseif ($Task -eq 'fault' -and $Case -in @('SEC-12','SEC-22')) {
        $expectedTrustNames = @('TEST-lab.backend.identity.database.DatabaseIsolationIT.xml', 'TEST-lab.backend.identity.credential.IdentityCredentialWorkflowIT.xml','TEST-lab.backend.identity.credential.CredentialPackagedIT.xml')
      } elseif ($receiverDbFault) {
        $expectedTrustNames = @("TEST-lab.backend.$Service.database.DatabaseIsolationIT.xml","TEST-lab.backend.$Service.identityaccess.AdmissionGateIT.xml")
      } else {
        $expectedTrustNames = @("TEST-lab.backend.$Service.TransportTrustIT.xml")
        if ($Case -in @('SEC-01','SEC-02','SEC-05','SEC-09')) { $expectedTrustNames = @() }
        if ($Task -eq 'contract') { $expectedTrustNames += "TEST-lab.backend.$Service.WireContractIT.xml" }
        if ($Task -eq 'contract' -and $Service -eq 'identity') { $expectedTrustNames += 'TEST-lab.backend.identity.session.SessionRuntimeIT.xml' }
        if ($Service -in @('content','learning') -and ($Task -eq 'contract' -or $Case -in @('SEC-01','SEC-02','SEC-05','SEC-09','SEC-15'))) { $expectedTrustNames += "TEST-lab.backend.$Service.identityaccess.AdmissionRuntimeIT.xml" }
      }
      if ($trustReports.Count -ne $expectedTrustNames.Count -or @($trustReports | Where-Object Name -NotIn $expectedTrustNames).Count) { throw 'Required trust/wire suite missing; not PASS.' }
      $reports += $trustReports
    }
    $executedTests = 0
    foreach ($report in $reports) {
      [xml]$reportXml = Get-Content -LiteralPath $report.FullName -Raw
      $suite = $reportXml.testsuite
      if (-not $suite -or $suite.tests -notmatch '^\d+$' -or
          $suite.skipped -notmatch '^\d+$' -or $suite.failures -notmatch '^\d+$' -or $suite.errors -notmatch '^\d+$') {
        throw "Invalid JUnit report: $($report.Name)"
      }
      if ([long]$suite.skipped -gt 0 -or [long]$suite.failures -gt 0 -or [long]$suite.errors -gt 0) {
        throw "Required CMD-02 suite did not fully run successfully: $($report.Name)"
      }
      $executedTests += [long]$suite.tests
      if ($databaseQualification -and $report.Name -in $requiredDbReports) {
        if ([long]$suite.tests -eq 0) { throw 'CMD-03 required DB/credential suite ran zero tests.' }
        $dbTests += [long]$suite.tests
      }
      if ([long]$suite.tests -eq 0) { throw "Required suite ran zero tests: $($report.Name)" }
      if (-not $canonicalCoreFault -and $Task -in @('contract','fault','relay') -and $report.Name -in $expectedTrustNames) {
        if ([long]$suite.tests -eq 0) { throw 'Required trust/wire suite ran zero tests.' }
        $dbTests += [long]$suite.tests
      }
    }
    if ($executedTests -eq 0) { throw 'CMD-02 ran zero tests. This is not PASS.' }
    if ($canonicalRuntime) {
      [xml]$canonicalRuntimeXml = Get-Content -LiteralPath $trustReports[0].FullName -Raw
      $canonicalNodes = @($canonicalRuntimeXml.testsuite.testcase)
      $canonicalNames = @($canonicalNodes | ForEach-Object { $_.name })
      # Không tin summary xanh nếu node failure/skip bị khai sai, duplicate
      # hoặc tên gần giống thay thế case thật. Parameterized method có [1]/[2].
      if ($canonicalNodes.Count -ne [long]$canonicalRuntimeXml.testsuite.tests -or
          @($canonicalNodes | Where-Object { $_.SelectNodes('failure|error|skipped').Count }).Count -or
          @($canonicalNames | Select-Object -Unique).Count -ne $canonicalNames.Count) { throw 'Invalid canonical runtime case report; not PASS.' }
      $canonicalExpectedCount=0
      foreach ($canonicalMethod in $canonicalRequiredMethods) {
        $casePattern = '^' + [regex]::Escape($canonicalMethod) + '(?:\([^)]*\))?(?:\[\d+\])?$'
        if (@($canonicalNames | Where-Object { $_ -cmatch $casePattern }).Count -ne $canonicalRuntimeCases[$canonicalMethod]) { throw "Required canonical runtime case/variant missing: $canonicalMethod; not PASS." }
        $canonicalExpectedCount+=$canonicalRuntimeCases[$canonicalMethod]
      }
      if ($canonicalNames.Count -ne $canonicalExpectedCount) { throw 'Unexpected canonical runtime case; not PASS.' }
      # XML runtime xanh vẫn phải có actual browser result ở owned trust run.
      # Report doubles của helper chỉ kiểm cổng này, không được gọi browser PASS.
      foreach($purpose in $(if($canonicalNeedsBrowser){@('LOGOUT_ALL','PASSWORD_CHANGE')}else{@()})) {
        $browserPath=Join-Path (Split-Path -Parent $TrustConfig) "canonical-browser-$purpose.json"
        if(-not (Test-Path -LiteralPath $browserPath -PathType Leaf)) { throw 'Required canonical browser report missing; not PASS.' }
        $browser=Get-Content -LiteralPath $browserPath -Raw | ConvertFrom-Json -AsHashtable
        if($browser.stage -cne 'RESULT' -or $browser.status -cne 'PASS' -or $browser.purpose -cne $purpose -or
          $browser.origin -cne 'https://localhost:17441' -or $browser.browser -cne 'installed Microsoft Edge / Chromium' -or
          $browser.version -cnotmatch '^\d+\.\d+\.\d+\.\d+$' -or
          @((Compare-Object @($browser.Keys) @('stage','status','purpose','checks','cases','browser','version','origin','scope') -CaseSensitive)).Count -or
          $browser.cases -isnot [array] -or @($browser.cases).Count -ne $canonicalBrowserCases.Count -or
          @($browser.cases | Select-Object -Unique).Count -ne $canonicalBrowserCases.Count -or
          @((Compare-Object @($browser.cases) $canonicalBrowserCases -CaseSensitive)).Count -or
          ($browser.checks -isnot [long] -and $browser.checks -isnot [int]) -or $browser.checks -ne $canonicalBrowserCases.Count) { throw 'Invalid canonical browser report/case; not PASS.' }
      }
    }
    if ($EvidenceTask -eq 'R06a' -and $Task -eq 'db') {
      $publicationDbReport=$dbReports | Where-Object Name -EQ 'TEST-lab.backend.content.database.DatabaseIsolationIT.xml' | Select-Object -First 1
      if(-not $publicationDbReport){throw 'R06a publication DB suite missing; not PASS.'}
      [xml]$publicationDbXml=Get-Content -LiteralPath $publicationDbReport.FullName -Raw
      $publicationDbNodes=@($publicationDbXml.testsuite.testcase)
      $publicationDbNames=@($publicationDbNodes | ForEach-Object {$_.name})
      if($publicationDbNodes.Count -ne [long]$publicationDbXml.testsuite.tests -or
          @($publicationDbNames | Select-Object -Unique).Count -ne $publicationDbNames.Count -or
          @($publicationDbNodes | Where-Object {$_.SelectNodes('failure|error|skipped').Count}).Count){
        throw 'Invalid R06a publication DB case report; not PASS.'
      }
      foreach($requiredPublicationDbCase in $publicationDbCases){
        if($requiredPublicationDbCase -cnotin $publicationDbNames){throw "Required R06a DB case missing: $requiredPublicationDbCase; not PASS."}
      }
    }
    if ($EvidenceTask -eq 'R06c' -and $Task -eq 'db') {
      $coordinatorDbReport=$dbReports | Where-Object Name -EQ 'TEST-lab.backend.content.database.DatabaseIsolationIT.xml' | Select-Object -First 1
      if(-not $coordinatorDbReport){throw 'R06c coordinator DB suite missing; not PASS.'}
      [xml]$coordinatorDbXml=Get-Content -LiteralPath $coordinatorDbReport.FullName -Raw
      $coordinatorDbNodes=@($coordinatorDbXml.testsuite.testcase)
      $coordinatorDbNames=@($coordinatorDbNodes | ForEach-Object {$_.name})
      if($coordinatorDbNodes.Count -ne [long]$coordinatorDbXml.testsuite.tests -or
          @($coordinatorDbNames | Select-Object -Unique).Count -ne $coordinatorDbNames.Count -or
          @($coordinatorDbNodes | Where-Object {$_.SelectNodes('failure|error|skipped').Count}).Count){
        throw 'Invalid R06c coordinator DB case report; not PASS.'
      }
      foreach($requiredCoordinatorDbCase in $coordinatorDbCases){
        if($requiredCoordinatorDbCase -cnotin $coordinatorDbNames){throw "Required R06c DB case missing: $requiredCoordinatorDbCase; not PASS."}
      }
    }
    if ($EvidenceTask -eq 'R06b' -and $Task -eq 'db') {
      $learningPublicationReport=$dbReports | Where-Object Name -EQ 'TEST-lab.backend.learning.contentreplica.PublicationStoreIT.xml' | Select-Object -First 1
      if(-not $learningPublicationReport){throw 'R06b publication DB suite missing; not PASS.'}
      [xml]$learningPublicationXml=Get-Content -LiteralPath $learningPublicationReport.FullName -Raw
      $learningPublicationNodes=@($learningPublicationXml.testsuite.testcase)
      $learningPublicationNames=@($learningPublicationNodes | ForEach-Object {$_.name})
      if($learningPublicationNodes.Count -ne [long]$learningPublicationXml.testsuite.tests -or
          @($learningPublicationNames | Select-Object -Unique).Count -ne $learningPublicationNames.Count -or
          @($learningPublicationNodes | Where-Object {$_.SelectNodes('failure|error|skipped').Count}).Count){throw 'Invalid R06b publication DB case report; not PASS.'}
      foreach($requiredLearningPublicationCase in $learningPublicationDbCases){if($requiredLearningPublicationCase -cnotin $learningPublicationNames){throw "Required R06b DB case missing: $requiredLearningPublicationCase; not PASS."}}
    }
    if ($EvidenceTask -eq 'R06b' -and $Task -in @('contract','fault')) {
      $learningRuntimeReport=$trustReports | Where-Object Name -EQ 'TEST-lab.backend.learning.contentreplica.PublicationRuntimeIT.xml' | Select-Object -First 1
      if(-not $learningRuntimeReport){throw 'R06b publication runtime suite missing; not PASS.'}
      [xml]$learningRuntimeXml=Get-Content -LiteralPath $learningRuntimeReport.FullName -Raw
      $learningRuntimeNodes=@($learningRuntimeXml.testsuite.testcase);$learningRuntimeNames=@($learningRuntimeNodes | ForEach-Object {$_.name})
      $requiredLearningRuntimeCases=if($Task -eq 'fault'){@($learningPublicationFaultMap[$Case])}else{@($learningPublicationRuntimeCases)}
      if($learningRuntimeNodes.Count -ne [long]$learningRuntimeXml.testsuite.tests -or
          @($learningRuntimeNames | Select-Object -Unique).Count -ne $learningRuntimeNames.Count -or
          @($learningRuntimeNodes | Where-Object {$_.SelectNodes('failure|error|skipped').Count}).Count -or
          @((Compare-Object $learningRuntimeNames $requiredLearningRuntimeCases -CaseSensitive)).Count){throw 'Invalid R06b publication runtime case report; not PASS.'}
    }
    if ($EvidenceTask -eq 'R06c' -and $Task -in @('contract','fault')) {
      $coordinatorRuntimeReport=$trustReports | Where-Object Name -EQ 'TEST-lab.backend.content.publication.PublicationCoordinatorRuntimeIT.xml' | Select-Object -First 1
      if(-not $coordinatorRuntimeReport){throw 'R06c coordinator runtime suite missing; not PASS.'}
      [xml]$coordinatorRuntimeXml=Get-Content -LiteralPath $coordinatorRuntimeReport.FullName -Raw
      $coordinatorRuntimeNodes=@($coordinatorRuntimeXml.testsuite.testcase)
      $coordinatorRuntimeNames=@($coordinatorRuntimeNodes | ForEach-Object {$_.name})
      if($coordinatorRuntimeNodes.Count -ne [long]$coordinatorRuntimeXml.testsuite.tests -or
          @($coordinatorRuntimeNames | Select-Object -Unique).Count -ne $coordinatorRuntimeNames.Count -or
          @($coordinatorRuntimeNodes | Where-Object {$_.SelectNodes('failure|error|skipped').Count}).Count -or
          @((Compare-Object $coordinatorRuntimeNames $coordinatorRequiredCases -CaseSensitive)).Count){
        throw 'Invalid R06c coordinator runtime case report; not PASS.'
      }
    }
    if ($Task -eq 'relay') {
      [xml]$relayXml = Get-Content -LiteralPath ($reports | Where-Object Name -EQ "TEST-lab.backend.$Service.delivery.DeliveryRuntimeIT.xml").FullName -Raw
      $relayCases = @($relayXml.testsuite.testcase | ForEach-Object { $_.name })
      if ($relayCases.Count -ne [long]$relayXml.testsuite.tests) { throw 'FX04 report/case count mismatch; not PASS.' }
      foreach ($requiredRelayCase in $relayRequiredCases) {
        $executedVariantCount = @($relayCases | Where-Object { $_ -and $_.StartsWith($requiredRelayCase,[StringComparison]::Ordinal) }).Count
        if (-not $executedVariantCount) { throw "Required FX04 runtime case missing: $requiredRelayCase; not PASS." }
        if ($relayMinimumCases.ContainsKey($requiredRelayCase) -and $executedVariantCount -lt $relayMinimumCases[$requiredRelayCase]) { throw "Required FX04 variant missing: $requiredRelayCase; not PASS." }
      }
      if ($Case) {
        foreach ($executedRelayCase in $relayCases) {
          if (-not @($relayRequiredCases | Where-Object { $executedRelayCase.StartsWith($_,[StringComparison]::Ordinal) }).Count) { throw 'Unexpected FX04 case for selector; not PASS.' }
        }
      }
    }
    if ($EvidenceTask -eq 'R06a' -and $Task -eq 'contract') {
      $validatorReport=$reports | Where-Object Name -EQ 'TEST-lab.backend.content.publication.CandidateValidatorTests.xml' | Select-Object -First 1
      if(-not $validatorReport){throw 'R06a required validator suite missing; not PASS.'}
      $publicationReport=$trustReports | Where-Object Name -EQ 'TEST-lab.backend.content.publication.PublicationRuntimeIT.xml' | Select-Object -First 1
      if(-not $publicationReport){throw 'R06a publication runtime report missing; not PASS.'}
      [xml]$validatorXml=Get-Content -LiteralPath $validatorReport.FullName -Raw
      [xml]$publicationXml=Get-Content -LiteralPath $publicationReport.FullName -Raw
      $validatorNodes=@($validatorXml.testsuite.testcase);$validatorCases=@($validatorNodes | ForEach-Object {$_.name})
      $publicationNodes=@($publicationXml.testsuite.testcase)
      $publicationCases=@($publicationXml.testsuite.testcase | ForEach-Object {$_.name})
      if($validatorNodes.Count -ne [long]$validatorXml.testsuite.tests -or $validatorCases.Count -ne $publicationValidatorCases.Count -or
          @($validatorCases | Select-Object -Unique).Count -ne $validatorCases.Count -or
          @($validatorNodes | Where-Object {$_.SelectNodes('failure|error|skipped').Count}).Count -or
          @((Compare-Object $validatorCases $publicationValidatorCases -CaseSensitive)).Count){throw 'Invalid R06a validator case report; not PASS.'}
      if($publicationNodes.Count -ne [long]$publicationXml.testsuite.tests -or $publicationCases.Count -ne $publicationRuntimeCases.Count -or
          @($publicationCases | Select-Object -Unique).Count -ne $publicationCases.Count -or
          @($publicationNodes | Where-Object {$_.SelectNodes('failure|error|skipped').Count}).Count -or
          @((Compare-Object $publicationCases $publicationRuntimeCases -CaseSensitive)).Count){throw 'Invalid R06a publication runtime case report; not PASS.'}
      foreach($requiredValidatorCase in $publicationValidatorCases){if($requiredValidatorCase -cnotin $validatorCases){throw "Required R06a validator case missing: $requiredValidatorCase; not PASS."}}
      foreach($requiredPublicationCase in $publicationRuntimeCases){if($requiredPublicationCase -cnotin $publicationCases){throw "Required R06a runtime case missing: $requiredPublicationCase; not PASS."}}
    } elseif ($EvidenceTask -notin @('R06b','R06c') -and "TEST-lab.backend.$Service.delivery.DeliveryWireTests.xml" -notin $reports.Name) { throw 'Required delivery wire suite missing; not PASS.' }
    # Config sai phải bị chặn trước DB; thiếu suite kiểm ranh giới này không được tính PASS.
    if ($EvidenceTask -notin @('R06a','R06b','R06c') -and "TEST-lab.backend.$Service.delivery.DeliveryRuntimeConfigTests.xml" -notin $reports.Name) { throw 'Required delivery config suite missing; not PASS.' }
    if ($EvidenceTask -notin @('R06a','R06b','R06c') -and $Service -in @('content','learning') -and "TEST-lab.backend.$Service.identityaccess.AdmissionWireTests.xml" -notin $reports.Name) {
      throw "Required $Service admission wire suite missing; not PASS."
    }
    if ($Service -eq 'identity' -and 'TEST-lab.backend.identity.session.SessionHttpHandlerTests.xml' -notin $reports.Name) {
      throw 'Required Identity session HTTP adapter suite missing; not TLS/browser qualification.'
    }
    # Parser canonical là input boundary mới; không cho build current source PASS
    # nếu suite vắng, dù các suite lịch sử vẫn xanh.
    if ($Service -eq 'identity' -and 'TEST-lab.backend.identity.session.CanonicalAdmissionWireTests.xml' -notin $reports.Name) {
      throw 'Required Identity canonical admission wire suite missing; not PASS.'
    }
    # Cursor bounded là source mới của worker; không được bỏ suite chống
    # starvation để lấy PASS từ các tests authority/adapter lịch sử.
    if ($Service -eq 'identity' -and 'TEST-lab.backend.identity.session.AuthPendingWorkTests.xml' -notin $reports.Name) {
      throw 'Required Identity pending work scanner suite missing; not PASS.'
    }
    # Private/public adapter source không đủ proof nếu parsing/dispatch suite
    # không chạy. Các units này không thay actual TLS/DB/browser qualification.
    if ($Service -eq 'identity') {
      foreach($adapterSuite in @('ReauthReservationWireTests','CanonicalAuthHttpHandlerTests','IdentitySensitiveIntentTests')) {
        if("TEST-lab.backend.identity.session.$adapterSuite.xml" -notin $reports.Name) { throw "Required Identity canonical adapter suite missing: $adapterSuite; not PASS." }
      }
    }
    if ($databaseQualification -and $Service -eq 'identity') {
      [xml]$canonicalXml = Get-Content -LiteralPath ($reports | Where-Object Name -EQ 'TEST-lab.backend.identity.credential.IdentitySessionAuthorityIT.xml').FullName -Raw
      $canonicalCases = @($canonicalXml.testsuite.testcase | ForEach-Object { $_.name })
      foreach($scanCase in @('barrierPendingTransitionScanPassesSeventeenPoisonIds','barrierPendingReconcileScanPassesSeventeenPoisonIds','barrierPendingCompletionScanPassesSeventeenPoisonIds')) {
        if($scanCase -notin $canonicalCases){throw "Required canonical own-DB case missing: $scanCase; not PASS."}
      }
      # Identity sensitive proof cần own-PG atomicity/race riêng; không lấy
      # LEARNING_RESET reservation hoặc intent-unit thay consume/epoch evidence.
      foreach($sensitiveCase in @('sensitiveLogoutAllAtomicallyDeniesAllSessionsAndRetriesWithoutIdleOrCookieEffect','sensitivePasswordChangeBindsPayloadAndHashesOnlyOneDurableEffect','sensitiveEveryBindingAndLearningPurposeAreRequired','sensitiveSecondOutboxFailureRollsBackProofHashEpochTokensAndDeny','sensitiveAuditFailureRollsBackConsumeAndOutboxes','sensitiveConcurrentConsumeAndRestartReturnOneImmutableOperation','sensitiveDirectConsumeCannotCommitWithoutAtomicEffect','sensitiveMissingDeferredGuardAndChangedStorageKeyFailClosed','sensitivePasswordKdfResetRaceCannotConsumeOldProof','sensitiveExpiredProofAndCrossPurposeReservationCannotMutate')) {
        if($sensitiveCase -notin $canonicalCases){throw "Required canonical own-DB case missing: $sensitiveCase; not PASS."}
      }
      # Proof/reservation core phải có named cases trên actual own PG; không dùng
      # old session count hoặc parser unit để giả full reauth HTTP/browser PASS.
      foreach($proofCase in @('reauthDigestOnlySameIntentReservationHasFixedReferenceAndTtl','reauthCallerAndEveryIntentBindingAreRequired','reauthBadCsrfAndContextCannotReachKdfOrThrottle','reauthWrongPasswordHasDedicatedThrottleAndCannotMint','reauthMintFailureRollsBackGrantAndIdle','reauthReservationRollbackAndRestartCannotRefundOrRetarget','reauthExpiredGrantAndChangedEpochAreDenied','reauthKdfLogoutRaceCannotMintFromOldSession','reauthRuntimeGuardAndImmutablePrivilegesFailClosed','reauthConcurrentSameIntentHasOneReservation','reauthKdfEpochChangeRaceCannotMintFromOldCredential','reauthKdfExpiryRaceCommitsDenyWithoutMintingProof','reauthConcurrentCapacityCannotOvershootGlobalBound')) {
        if($proofCase -notin $canonicalCases){throw "Required canonical own-DB case missing: $proofCase; not PASS."}
      }
      # Flow mới chỉ mở sau COMPLETED; thiếu test cookie A/B phải fail dù suite
      # coordinator cũ vẫn nonzero. Gate này kiểm report, không thay browser proof.
      foreach($flowCase in @('barrierCompletedLogoutAllowsFreshFlowAndOldRetryCannotRevokeNewSession','barrierTerminalSessionWithoutCompletedFenceCannotCreateFreshFlow','barrierExpiryCommitsTwoCommandsAndCompletedFenceAllowsFreshFlow')) {
        if($flowCase -notin $canonicalCases){throw "Required canonical own-DB case missing: $flowCase; not PASS."}
      }
      if('barrierResetAfterEnableCannotBypassPendingLifecycle' -notin $canonicalCases){throw 'Required canonical own-DB case missing: barrierResetAfterEnableCannotBypassPendingLifecycle; not PASS.'}
      # Coordinator own-PG cases không được mất khi required legacy suite vẫn có
      # nonzero tests; actual mTLS/three-app qualification vẫn ở bước sau.
      foreach($barrierCase in @('barrierLogoutAtomicCommandsAndBothAcksAreRequired','barrierSecondOutboxFailureRollsBackCanonicalDeny','barrierLegacyReconciliationPreservesOperationAndExactBytes','barrierPendingCannotClearOrCompleteByDirectSql','barrierResetCurrentEpochCompletesAndFreshLoginUsesNewEpoch','barrierOlderResetAckNeverClearsNewerTransition','barrierCurrentGenerationChangeRejectsOldAckWithoutRetargeting','barrierCompletionFailureRollsBackOperationAndCanonicalSession','barrierTwoConcurrentCompletersProduceOneDurableEffect','barrierLegacyCredentialResetReconciliationIsImmutableAndDigestOnly','barrierDisableAndEnableAuthCompletionKeepLifecycleGate','barrierRuntimeVerificationCannotRepairMissingCompletionGuard')) {
        if($barrierCase -notin $canonicalCases){throw "Required canonical own-DB case missing: $barrierCase; not PASS."}
      }
      foreach($deadlineCase in @('canonicalReadDeadlineIsCappedByIdleAndAbsoluteIndependently','canonicalMutationInsufficientAbsoluteBudgetRollsBackIdleTouch')) {
        if ($deadlineCase -notin $canonicalCases) { throw "Required canonical own-DB case missing: $deadlineCase; not PASS." }
      }
      foreach ($requiredCase in @('canonicalReadBindsFreshNonceOwnerSessionRealmAndDeadlinesWithoutIdleTouch','canonicalDraftSaveVerifiesSynchronizerAndTouchesOnlyItsOwnSession','canonicalBadCsrfAndContextDoNotTouchIdleOrCreateOperation','canonicalUnknownCookieAndPreauthCannotCreateLearnerAdmission','canonicalExpiredMutationCommitsDenyAndCannotRepeatExpiryIntents','canonicalAbsoluteExpiryAndOrphanBackingAreDeniedDurably','canonicalLogoutFencePendingAlreadyDeniesAdmissionWithoutWaitingForAck','canonicalShortIdleBudgetDeniesReadWithoutRenewingIt','canonicalWrongRealmFailsClosedWithoutTouchOrLocalExpiry','canonicalBlockedAdmissionRereadsCommittedAccountTransition','canonicalExpiryIntentFailureRollsBackSessionAndOperation')) {
        if ($requiredCase -notin $canonicalCases) { throw "Required canonical own-DB case missing: $requiredCase; not PASS." }
      }
    }
    if ($EvidenceTask -notin @('R06a','R06b','R06c') -and $Service -in @('content','learning')) {
      $requiredCases = @{}
      if ($Task -eq 'db' -or $receiverDbFault) {
        $transactionCase = if ($Service -eq 'learning') { 'twoReplicasFenceWaitsForMutationTransactionCommit' } else { 'twoReplicasFenceWaitsForReadTransactionCommit' }
        $requiredCases['AdmissionGateIT'] = @('absentSessionFenceDeniesLateAdmissionWithoutProvisioningLearner','olderCommandCannotLowerFloorAndNewEpochMayProceed','duplicateCommandIsDurableAndChangedContextConflicts',$transactionCase,'rollbackKeepsAdmissionAndLocalEffectAtomic','wrongContextNonceBindingAndOwnerCannotUseAdmission','sameSessionUnderDifferentOwnerCannotReplaceTombstone','runtimeRoleCannotEraseFenceReceiptOrLowerFloor','wrongRealmGenerationAndNotReadyFailClosed','cutoffRejectedBeforeEffectAndDatabaseBoundsHeldTransaction')
        if ($Service -eq 'learning') { $requiredCases['AdmissionGateIT'] += 'committedFenceDeniesConcurrentMutationWithoutEffect' }
      }
      if (-not $canonicalRuntime -and ($Task -eq 'contract' -or ($Task -eq 'fault' -and $Case -in @('SEC-01','SEC-02','SEC-05','SEC-09','SEC-15')))) {
        $requiredCases['AdmissionRuntimeIT'] = @()
        if ($Service -eq 'content') {
          if ($Task -eq 'contract' -or $Case -eq 'SEC-02') { $requiredCases['AdmissionRuntimeIT'] += 'agAbsentSessionTombstoneBlocksActualOnlineAdmission' }
          if ($Task -eq 'contract' -or $Case -eq 'SEC-09') { $requiredCases['AdmissionRuntimeIT'] += @('aeDurableDuplicateAndConflictingEnvelope','zzRestartAndLostAckPreserveReceiptAndDeny') }
          if ($Task -eq 'contract' -or $Case -eq 'SEC-15') { $requiredCases['AdmissionRuntimeIT'] += @('aaOnlineAdmissionIsFreshAndUnavailableDoesNotUseCachedAllow','abWrongProviderResponseAndBoundedFailuresDenyBeforeLocalEffect','acOnlyIdentityCertificateMayFence','adPublicAndBrowserHeadersCannotReachFence','afSchemaDigestGenerationMethodAndBodyLimitsFailClosed','ahProvisionAndRuntimeMismatchCannotResetExistingSecurityState','aiStalledUploadHasBoundedIdleTimeoutAndNoFenceEffect') }
        } else {
          if ($Task -eq 'contract' -or $Case -eq 'SEC-01') { $requiredCases['AdmissionRuntimeIT'] += 'aaMutationUsesFreshAdmissionAndCommitsEffectAtomically' }
          if ($Task -eq 'contract' -or $Case -eq 'SEC-02') { $requiredCases['AdmissionRuntimeIT'] += 'agAbsentSessionTombstoneBlocksActualOnlineMutation' }
          if ($Task -eq 'contract' -or $Case -eq 'SEC-05') { $requiredCases['AdmissionRuntimeIT'] += @('abUnavailableProviderDoesNotUseCachedAllow','acWrongProviderResponseAndBoundedFailuresDenyBeforeEffect','akWrongOriginCannotCallProviderOrMutate') }
          if ($Task -eq 'contract' -or $Case -eq 'SEC-09') { $requiredCases['AdmissionRuntimeIT'] += @('afDurableDuplicateAndConflictingEnvelope','zzRestartAndLostAckPreserveReceiptAndDeny') }
          if ($Task -eq 'contract' -or $Case -eq 'SEC-15') { $requiredCases['AdmissionRuntimeIT'] += @('adOnlyIdentityCertificateMayFence','aePublicAndBrowserHeadersCannotReachFence','ahProvisionAndRuntimeMismatchCannotResetSecurityState','aiSchemaDigestGenerationMethodAndBodyLimitsFailClosed','ajStalledUploadHasBoundedIdleTimeoutAndNoFenceEffect') }
        }
      }
      foreach ($suiteName in $requiredCases.Keys) {
        [xml]$admissionXml = Get-Content -LiteralPath ($reports | Where-Object Name -EQ "TEST-lab.backend.$Service.identityaccess.$suiteName.xml").FullName -Raw
        $caseNames = @($admissionXml.testsuite.testcase | ForEach-Object { $_.name })
        foreach ($requiredCase in $requiredCases[$suiteName]) {
          if (-not @($caseNames | Where-Object { $_ -and $_.StartsWith($requiredCase,[StringComparison]::Ordinal) }).Count) { throw "Required admission case missing: $requiredCase; not PASS." }
        }
      }
    }
    if ($Task -eq 'contract' -and $Service -eq 'identity') {
      [xml]$runtimeXml = Get-Content -LiteralPath ($reports | Where-Object Name -EQ 'TEST-lab.backend.identity.session.SessionRuntimeIT.xml').FullName -Raw
      $runtimeCases = @($runtimeXml.testsuite.testcase | ForEach-Object { $_.name })
      foreach ($requiredCase in @('aaReadOnlyBootstrapDoesNotCreateFlowOrCookie','abLoginRotatesCookieAndPollDoesNotRenewIdle','acLogoutRetryAndStatusAreBoundAndNeverClearNewCookie','adWrongOriginCannotMintFlow','afTrustedSourceMustBeOneLiteral','ahSchemaAndBodyNegatives','aiCsrfAndContextDenyBeforeCredentialBuckets','aiSlowUploadHasBoundedTimeoutBeforeAnyFlowEffect','ajActivationIsAtomicAndNeverAutoLogsIn','akResetKeepsPendingStatusAndDeniesOldSessions','alReauthUnsupportedCannotBecomePasswordOnlySuccess','amBadConfigFailsBeforeOpeningListener','anRealmProvisionCannotResetExistingState','azActualBrowserCookieReorderMultiTabAndOrigin','zzRestartRetainsCanonicalDenyAndStatusWithoutResurrection')) {
        if (-not @($runtimeCases | Where-Object { $_ -and $_.StartsWith($requiredCase,[StringComparison]::Ordinal) }).Count) { throw "Required session runtime case missing: $requiredCase; not PASS." }
      }
    }
    if ($databaseQualification) {
      if ($dbTests -eq 0) { throw 'CMD-03 required DB suite ran zero tests.' }
      if (($executedTests - $dbTests) -eq 0) { throw 'CMD-03 bootstrap regression ran zero tests.' }
      if($canonicalCoreFault){Write-Host "CMD-05 $Service canonical own-core completed: DB=$dbTests; bootstrap=$($executedTests - $dbTests); Case=$Case; no required skips; not mTLS/browser qualification."}
      else{Write-Host "CMD-03 $Service completed: DB=$dbTests; bootstrap=$($executedTests - $dbTests); no required skips."}
    } elseif ($Task -in @('contract','fault','relay')) {
      if (($executedTests - $dbTests) -eq 0) { throw 'Trust bootstrap regression ran zero tests.' }
      Write-Host "$Task $Service completed: integration=$dbTests; unit/bootstrap=$($executedTests - $dbTests); no required skips. Case=$Case; full browser/session/edge cases remain pending."
    } else {
      Write-Host "CMD-02 $Service completed: $executedTests tests; no required skips."
    }
  }
} catch {
  Write-Error $_ -ErrorAction Continue
  $exitCode = 1
} finally {
  if ($locationPushed) { Pop-Location }
  foreach ($variableName in $savedEnvironment.Keys) {
    [Environment]::SetEnvironmentVariable($variableName, $savedEnvironment[$variableName], 'Process')
  }
}
exit $exitCode
