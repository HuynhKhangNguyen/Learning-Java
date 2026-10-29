#requires -Version 7.4
[CmdletBinding()]
param([string]$NodeDirectory)

if ($MyInvocation.InvocationName -ne '.') {
  throw 'Run by dot-sourcing: . .\scripts\enter-dev-shell.ps1 (the first dot keeps the environment in this terminal).'
}

# Keep script variables/preferences out of the caller; only process environment persists.
& {
  param($TaskScriptRoot, $TaskNodeDirectory)
  $ErrorActionPreference = 'Stop'
  $PSNativeCommandUseErrorActionPreference = $false
  if (-not $IsWindows) { throw 'This development shell is qualified for Windows only.' }
  $taskRepoRoot = Split-Path -Parent $TaskScriptRoot
  $taskManifest = Get-Content -LiteralPath (Join-Path $taskRepoRoot 'ops/release/dependencies.json') -Raw | ConvertFrom-Json
  $taskJavaHome = Join-Path $taskRepoRoot $taskManifest.toolchain.jdk.installDirectory
  foreach ($taskExecutable in @('java.exe', 'javac.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $taskJavaHome "bin/$taskExecutable") -PathType Leaf)) {
      throw 'Portable JDK missing. Run scripts/setup-backend.ps1 first.'
    }
  }
  $taskJavaInfo = & (Join-Path $taskJavaHome 'bin/java.exe') -XshowSettings:properties -version 2>&1 | Out-String
  if ($LASTEXITCODE -ne 0 -or $taskJavaInfo -notmatch ('java\.runtime\.version\s*=\s*' + [regex]::Escape($taskManifest.toolchain.jdk.runtimeVersion) + '\s')) {
    throw 'Portable JDK does not match the pinned runtime.'
  }
  $taskCompilerInfo = & (Join-Path $taskJavaHome 'bin/javac.exe') -version 2>&1 | Out-String
  if ($LASTEXITCODE -ne 0 -or $taskCompilerInfo -notmatch '^javac 21(?:\.|\s|-)') { throw 'JDK compiler must be javac 21.' }

  $taskNodeCandidates = @()
  if ($TaskNodeDirectory) {
    $taskNodeCandidates += Join-Path $TaskNodeDirectory 'node.exe'
  } else {
    $taskNodeCommand = Get-Command node.exe -ErrorAction SilentlyContinue
    if ($taskNodeCommand) { $taskNodeCandidates += $taskNodeCommand.Source }
    if ($env:USERPROFILE) {
      $taskNodeCandidates += Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe'
    }
  }
  $taskSelectedNode = $null
  foreach ($taskCandidate in $taskNodeCandidates) {
    if (-not (Test-Path -LiteralPath $taskCandidate -PathType Leaf)) { continue }
    $taskVersionText = & $taskCandidate --version 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0 -or $taskVersionText.Trim() -notmatch '^v(\d+)\.(\d+)\.(\d+)$') { continue }
    $taskVersion = [version]$taskVersionText.Trim().Substring(1)
    if (($taskVersion.Major -eq 22 -and $taskVersion.Minor -ge 22) -or $taskVersion.Major -ge 24) {
      $taskSelectedNode = (Resolve-Path -LiteralPath $taskCandidate).Path
      break
    }
  }
  if (-not $taskSelectedNode) { throw 'No supported Node found. Use -NodeDirectory pointing to Node 22.22+ or 24+.' }

  $taskNpmCandidates = @((Join-Path (Split-Path -Parent $taskSelectedNode) 'node_modules/npm/bin/npm-cli.js'))
  # npm.cmd beside an older global node.exe would use that old runtime, even with a new PATH.
  $taskNpmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
  if ($taskNpmCommand) { $taskNpmCandidates += Join-Path (Split-Path -Parent $taskNpmCommand.Source) 'node_modules/npm/bin/npm-cli.js' }
  if ($env:BACKEND_LAB_NPM_CLI) { $taskNpmCandidates += $env:BACKEND_LAB_NPM_CLI }
  $taskNpmCli = $taskNpmCandidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
  if (-not $taskNpmCli) { throw 'npm CLI missing; use a Node distribution with npm.' }
  $taskNpmCli = (Resolve-Path -LiteralPath $taskNpmCli).Path
  $taskNpmVersionText = & $taskSelectedNode $taskNpmCli --version 2>&1 | Out-String
  if ($LASTEXITCODE -ne 0 -or $taskNpmVersionText.Trim() -notmatch '^\d+\.\d+\.\d+$' -or [version]$taskNpmVersionText.Trim() -lt [version]'8.5.0') {
    throw 'npm 8.5+ is required.'
  }
  $taskNpxCli = Join-Path (Split-Path -Parent $taskNpmCli) 'npx-cli.js'
  if (-not (Test-Path -LiteralPath $taskNpxCli -PathType Leaf)) { throw 'npx CLI missing from the selected npm distribution.' }

  $taskShimRoot = Join-Path $taskRepoRoot '.tools/dev-shell/bin'
  foreach ($taskRelativePath in @('.tools', '.tools/dev-shell', '.tools/dev-shell/bin')) {
    $taskDirectory = Join-Path $taskRepoRoot $taskRelativePath
    if ((Test-Path -LiteralPath $taskDirectory) -and ((Get-Item -LiteralPath $taskDirectory).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
      throw 'Development shell directory must not be a junction or symbolic link.'
    }
  }
  New-Item -ItemType Directory -Path $taskShimRoot -Force | Out-Null
  foreach ($taskShim in @('npm', 'npx')) {
    $taskShimPath = Join-Path $taskShimRoot "$taskShim.cmd"
    if ((Test-Path -LiteralPath $taskShimPath) -and ((Get-Item -LiteralPath $taskShimPath).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
      throw 'Development shell shim must not be a symbolic link.'
    }
    $taskCliVariable = if ($taskShim -eq 'npm') { 'BACKEND_LAB_NPM_CLI' } else { 'BACKEND_LAB_NPX_CLI' }
    $taskShimText = '@echo off' + "`r`n" + '"%BACKEND_LAB_NODE_EXE%" "%' + $taskCliVariable + '%" %*' + "`r`n" + 'exit /b %ERRORLEVEL%' + "`r`n"
    [IO.File]::WriteAllText($taskShimPath, $taskShimText, [Text.Encoding]::ASCII)
  }

  $env:JAVA_HOME = $taskJavaHome
  $env:MAVEN_USER_HOME = Join-Path $taskRepoRoot '.tools/maven-user-home'
  $env:BACKEND_LAB_NODE_EXE = $taskSelectedNode
  $env:BACKEND_LAB_NPM_CLI = $taskNpmCli
  $env:BACKEND_LAB_NPX_CLI = $taskNpxCli
  $taskPrefix = @($taskShimRoot, (Split-Path -Parent $taskSelectedNode), (Join-Path $taskJavaHome 'bin'))
  $taskRemainingPath = @($env:PATH -split [regex]::Escape([IO.Path]::PathSeparator) | Where-Object { $_ -notin $taskPrefix })
  $env:PATH = ($taskPrefix + $taskRemainingPath) -join [IO.Path]::PathSeparator
  Write-Host "Development shell ready: Java $($taskManifest.toolchain.jdk.runtimeVersion), Node $taskVersion, npm $($taskNpmVersionText.Trim())."
  Write-Host 'Only this terminal and its child processes are affected. Close the terminal to leave.'
  Write-Host 'FE: .\scripts\project.ps1 -Task dev; BE: pwsh -File scripts/services.ps1 -Task toolchain -Offline'
} $PSScriptRoot $NodeDirectory
