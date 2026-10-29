#requires -Version 7.4
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repoDirectory = Split-Path -Parent $PSScriptRoot
$manifest = Get-Content -LiteralPath (Join-Path $repoDirectory 'ops/release/postgresql.json') -Raw | ConvertFrom-Json
if (-not $IsWindows -or $manifest.version -ne '18.6' -or $manifest.vendorBuild -ne '18.6-5') {
  throw 'This qualification is PostgreSQL18.6-5 Windows x64 only.'
}
$portableRoot = [IO.Path]::GetFullPath((Join-Path $repoDirectory '.tools/postgresql/18.6-5'))
$installDirectory = [IO.Path]::GetFullPath((Join-Path $repoDirectory $manifest.installDirectory))
if ($installDirectory -ne (Join-Path $portableRoot 'pgsql')) { throw 'Unexpected PostgreSQL workspace install path.' }
$archive = Join-Path $repoDirectory '.tools/downloads/postgresql-18.6-5-windows-x64-binaries.zip'
New-Item -ItemType Directory -Path (Split-Path -Parent $archive) -Force | Out-Null
if (-not (Test-Path -LiteralPath $archive -PathType Leaf)) {
  Invoke-WebRequest -Uri $manifest.resolvedDownloadUrl -OutFile $archive
}
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.archiveSha256) {
  throw 'PostgreSQL archive hash mismatch; no extraction.'
}
function Test-QualifiedFiles {
  foreach ($property in $manifest.qualifiedFiles.PSObject.Properties) {
    $file = Join-Path $installDirectory $property.Name
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { return $false }
    if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $property.Value) { return $false }
  }
  return $true
}
if (-not (Test-QualifiedFiles)) {
  New-Item -ItemType Directory -Path $portableRoot -Force | Out-Null
  $zip = [IO.Compression.ZipFile]::OpenRead($archive)
  try {
    foreach ($entry in $zip.Entries) {
      if ($entry.FullName -notmatch '^pgsql/(bin|lib|share)/' -or $entry.FullName.EndsWith('/')) { continue }
      $target = [IO.Path]::GetFullPath((Join-Path $portableRoot $entry.FullName))
      if (-not $target.StartsWith($portableRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Archive entry escapes PostgreSQL workspace directory.'
      }
      New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
      [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
    }
  } finally { $zip.Dispose() }
}
if (-not (Test-QualifiedFiles)) { throw 'Extracted PostgreSQL files do not match qualified hashes.' }
$version = & (Join-Path $installDirectory 'bin/postgres.exe') --version
if ($LASTEXITCODE -ne 0 -or $version -ne 'postgres (PostgreSQL) 18.6') { throw 'PostgreSQL runtime version mismatch.' }
Write-Host "PostgreSQL18.6-5 portable ready: $installDirectory"
Write-Host 'Archive hash pinned after official HTTPS download; publisher checksum/signature not verified. No Windows service installed.'
