#requires -Version 7.4
[CmdletBinding()]
param(
  [Parameter(Mandatory)][ValidateSet('db','contract','fault')][string]$Task,
  [Parameter(Mandatory)][string]$LabConfig,
  [ValidateSet('learning','identity','content')][string]$RequestedService = 'learning',
  [string]$TrustConfig,
  [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$allocationRepo = Split-Path -Parent $PSScriptRoot
if ($Task -eq 'db') {
  if ($TrustConfig) { throw 'R08 CMD-03 does not accept ignored trust config.' }
  # Foundation runner kiểm own marker/migration/roles/checksum/rollback và required suites.
  & (Get-Command pwsh -ErrorAction Stop).Source -NoProfile -File (Join-Path $PSScriptRoot 'services.ps1') `
    -Task db -Service learning -LabConfig $LabConfig -EvidenceTask R02a -Offline:$Offline
  if ($LASTEXITCODE -ne 0) { throw 'R08 own DB baseline failed; not PASS.' }
} else {
  if (-not $TrustConfig) { throw 'R08 packaged runtime requires owned trust config.' }
  foreach ($peer in @('identity','content','learning')) {
    & (Get-Command pwsh -ErrorAction Stop).Source -NoProfile -File (Join-Path $PSScriptRoot 'services.ps1') `
      -Task verify -Service $peer -Offline:$Offline
    if ($LASTEXITCODE -ne 0) { throw "R08 peer current build failed: $peer; not PASS." }
  }
}
$allocationSaved = @{}
foreach ($key in @('JAVA_HOME','MAVEN_USER_HOME','PATH')) {
  $allocationSaved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}
try {
  $manifest = Get-Content (Join-Path $allocationRepo 'ops/release/dependencies.json') -Raw | ConvertFrom-Json
  $env:JAVA_HOME = Join-Path $allocationRepo $manifest.toolchain.jdk.installDirectory
  $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + [IO.Path]::PathSeparator + $env:PATH
  $env:MAVEN_USER_HOME = Join-Path $allocationRepo '.tools/maven-user-home'
  $arguments = @('--batch-mode','--no-transfer-progress','-Dstyle.color=never',
    ('-Dmaven.repo.local=' + (Join-Path $allocationRepo '.tools/m2/repository')),
    '-pl','services/learning-service','-am','verify',
    $(if ($Task -eq 'db') { '-Pallocation-db-it' } else { '-Pallocation-runtime-it' }),
    '-Dtest=AllocationWireTests,AllocationAdmissionConsumerTests,AllocationV3CompatibilityTests',
    ('-Dlab.config=' + (Resolve-Path -LiteralPath $LabConfig).Path))
  if ($Task -ne 'db') {
    $selected = & (Get-Command python -ErrorAction Stop).Source -X utf8 (Join-Path $PSScriptRoot 'r08_report.py') `
      --task $Task --service $RequestedService --select
    if ($LASTEXITCODE -ne 0) { throw 'R08 registered case selector missing; not PASS.' }
    $selection = $selected | ConvertFrom-Json
    $arguments += ('-Dtrust.config=' + (Resolve-Path -LiteralPath $TrustConfig).Path)
    $arguments += ('-Dit.test=AllocationRuntimeIT#' + ($selection.methods -join '+'))
  }
  if ($Offline) { $arguments += '--offline' }
  Push-Location (Join-Path $allocationRepo 'backend')
  try {
    & '.\mvnw.cmd' @arguments
    if ($LASTEXITCODE -ne 0) { throw 'R08 allocation own-DB suite failed; not PASS.' }
  } finally { Pop-Location }
  & (Get-Command python -ErrorAction Stop).Source -X utf8 (Join-Path $PSScriptRoot 'r08_report.py') `
    --task $Task --service $RequestedService --target (Join-Path $allocationRepo 'backend/services/learning-service/target')
  exit $LASTEXITCODE
} finally {
  foreach ($key in $allocationSaved.Keys) {
    [Environment]::SetEnvironmentVariable($key, $allocationSaved[$key], 'Process')
  }
}
