param(
  [ValidateSet('install', 'dev', 'check', 'build', 'preview', 'content', 'smoke', 'all')]
  [string]$Task = 'dev',
  [string]$NodeDirectory
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $projectRoot 'frontend'
if (-not (Test-Path -LiteralPath (Join-Path $frontendRoot 'package.json') -PathType Leaf)) {
  throw 'Không tìm thấy frontend/package.json. Chạy helper từ checkout đầy đủ của Backend Lab.'
}
$originalPath = $env:PATH

function Test-NodeRuntime([string]$Executable) {
  if (-not (Test-Path -LiteralPath $Executable -PathType Leaf)) { return $false }
  $versionText = & $Executable --version
  if ($LASTEXITCODE -ne 0) { return $false }
  $version = [version]($versionText.Trim().TrimStart('v'))
  return (($version.Major -eq 22 -and $version.Minor -ge 22) -or $version.Major -ge 24)
}

$candidates = @()
if ($NodeDirectory) {
  $explicitNode = Join-Path $NodeDirectory 'node.exe'
  if (-not (Test-NodeRuntime $explicitNode)) { throw 'NodeDirectory phải chứa Node 22.22+ (nhánh 22) hoặc Node 24+.' }
  $candidates += $explicitNode
} else {
  $installedNode = Get-Command node.exe -ErrorAction SilentlyContinue
  if ($installedNode) { $candidates += $installedNode.Source }
  # Optional local runtime already provided by Codex; not downloaded or changed.
  if ($env:USERPROFILE) {
    $candidates += Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
  }
}

$selectedNode = $candidates | Where-Object { Test-NodeRuntime $_ } | Select-Object -First 1
if (-not $selectedNode) { throw 'Chưa có Node phù hợp. Cài Node 24 rồi mở terminal mới, hoặc truyền -NodeDirectory. Xem README.md.' }

$npmCandidates = @((Join-Path (Split-Path $selectedNode -Parent) 'node_modules\npm\bin\npm-cli.js'))
$npmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
if ($npmCommand) {
  $npmCandidates += Join-Path (Split-Path $npmCommand.Source -Parent) 'node_modules\npm\bin\npm-cli.js'
}
$npmCli = $npmCandidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
if (-not $npmCli) { throw 'Không tìm thấy npm CLI. Dùng bản cài Node có npm đi kèm; xem README.md.' }

Push-Location $frontendRoot
try {
  $env:PATH = (Split-Path $selectedNode -Parent) + [IO.Path]::PathSeparator + $originalPath
  Write-Host "Runtime: $selectedNode"
  if ($Task -eq 'install') {
    & $selectedNode $npmCli ci
  } elseif ($Task -eq 'smoke') {
    & $selectedNode $npmCli run test:smoke
  } elseif ($Task -eq 'content') {
    & $selectedNode $npmCli run test:content
  } elseif ($Task -eq 'all') {
    & $selectedNode $npmCli run test:all
  } else {
    & $selectedNode $npmCli run $Task
  }
  $taskExitCode = $LASTEXITCODE
} finally {
  $env:PATH = $originalPath
  Pop-Location
}
exit $taskExitCode
