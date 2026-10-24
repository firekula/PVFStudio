[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [Parameter(Mandatory = $true)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$sourceDirectory = Join-Path $PSScriptRoot 'windows\launcher'
$compiler = Get-Command gcc.exe -ErrorAction SilentlyContinue
if (-not $compiler) { throw 'Windows packaging requires MinGW-w64 GCC (gcc.exe and windres.exe on PATH).' }
$architecture = & $compiler.Source -dumpmachine
if ($LASTEXITCODE -ne 0 -or $architecture -ne 'x86_64-w64-mingw32') {
    throw "Expected an x64 MinGW-w64 compiler, got '$architecture'."
}
$resourceCompiler = Join-Path (Split-Path -Parent $compiler.Source) 'windres.exe'
if (-not (Test-Path -LiteralPath $resourceCompiler)) { throw "Missing resource compiler: $resourceCompiler" }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$resourceSource = Join-Path $OutputDirectory 'launcher.rc'
$resourceObject = Join-Path $OutputDirectory 'launcher-res.o'
$executable = Join-Path $OutputDirectory 'PVFStudio.exe'
$resource = [System.IO.File]::ReadAllText((Join-Path $sourceDirectory 'launcher.rc.in'))
$resource = $resource.Replace('@VERSION_COMMAS@', ($Version.Replace('.', ',') + ',0')).Replace('@VERSION@', $Version)
[System.IO.File]::WriteAllText($resourceSource, $resource, [System.Text.UTF8Encoding]::new($false))
& $resourceCompiler '-I' $sourceDirectory '-i' $resourceSource '-o' $resourceObject
if ($LASTEXITCODE -ne 0) { throw "Launcher resource compilation failed ($LASTEXITCODE)." }
& $compiler.Source '-std=c11' '-Os' '-s' '-Wall' '-Wextra' '-Werror' '-municode' '-mwindows' '-static' `
    '-Wl,--nxcompat,--dynamicbase,--high-entropy-va' (Join-Path $sourceDirectory 'launcher.c') `
    $resourceObject '-o' $executable '-lshell32' '-lole32' '-luuid' '-luser32'
if ($LASTEXITCODE -ne 0) { throw "Launcher compilation failed ($LASTEXITCODE)." }
Write-Host "Native Windows launcher: $executable"
