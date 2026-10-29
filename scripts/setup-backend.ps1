#requires -Version 7.4
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $IsWindows -or [Runtime.InteropServices.RuntimeInformation]::OSArchitecture -ne 'X64') {
  throw 'This portable setup is qualified only for Windows x64. Use an explicit JDK 21 and the Maven Wrapper on other platforms.'
}
$repoDirectory = Split-Path -Parent $PSScriptRoot
$manifest = Get-Content -LiteralPath (Join-Path $repoDirectory 'ops/release/dependencies.json') -Raw | ConvertFrom-Json
$jdk = $manifest.toolchain.jdk
$downloadDirectory = Join-Path $repoDirectory '.tools/downloads'
$archivePath = Join-Path $downloadDirectory $jdk.archive
$installDirectory = Join-Path $repoDirectory $jdk.installDirectory
New-Item -ItemType Directory -Path $downloadDirectory -Force | Out-Null

# Verify the pinned archive even on a repeat run; do not accept a partial download.
if (-not (Test-Path -LiteralPath $archivePath -PathType Leaf)) {
  $partialPath = "$archivePath.partial"
  Write-Host "Downloading $($jdk.distribution) $($jdk.version) to repo cache."
  Invoke-WebRequest -Uri $jdk.downloadUrl -OutFile $partialPath
  if ((Get-FileHash -LiteralPath $partialPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $jdk.sha256) {
    throw 'JDK archive checksum mismatch; the archive was not installed.'
  }
  Move-Item -LiteralPath $partialPath -Destination $archivePath -Force
}
if ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $jdk.sha256) {
  throw 'Cached JDK archive checksum mismatch; repair the download cache before retrying.'
}
if (-not (Test-Path -LiteralPath (Join-Path $installDirectory 'bin/javac.exe') -PathType Leaf)) {
  Write-Host 'Extracting verified portable JDK inside .tools/jdk.'
  Expand-Archive -LiteralPath $archivePath -DestinationPath (Join-Path $repoDirectory '.tools/jdk') -Force
}
if (-not (Test-Path -LiteralPath (Join-Path $installDirectory 'bin/javac.exe') -PathType Leaf)) {
  throw 'Expected JDK directory missing after extraction; check the pinned manifest.'
}
$runtimeInfo = & (Join-Path $installDirectory 'bin/java.exe') -XshowSettings:properties -version 2>&1 | Out-String
if ($LASTEXITCODE -ne 0 -or $runtimeInfo -notmatch ('java\.runtime\.version\s*=\s*' + [regex]::Escape($jdk.runtimeVersion) + '\s')) {
  throw 'Installed JDK runtime does not match the pinned baseline.'
}
Write-Host "Portable JDK ready: $installDirectory"
Write-Host 'Next: pwsh -File scripts/services.ps1 -Task toolchain; then -Task validate.'
