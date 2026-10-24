[CmdletBinding()]
param(
    [string]$IsccPath = $env:ISCC_PATH,
    [string]$IntellijLocalPath,
    [string]$RuntimePath,
    [string]$StandaloneDirectory,
    [switch]$ZipOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
$distributionDirectory = Join-Path $projectRoot 'pvf-product\build\distributions'

if (-not $ZipOnly) {
    if (-not $IsccPath) {
        $compilerCommand = Get-Command ISCC.exe -ErrorAction SilentlyContinue
        if ($compilerCommand) {
            $IsccPath = $compilerCommand.Source
        } else {
            $compilerCandidates = @(
                (Join-Path $projectRoot 'build\tools\innosetup\ISCC.exe'),
                "$env:LOCALAPPDATA\Programs\Inno Setup 7\ISCC.exe",
                "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe",
                "$env:ProgramFiles\Inno Setup 7\ISCC.exe",
                "${env:ProgramFiles(x86)}\Inno Setup 7\ISCC.exe",
                "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe"
            )
            $IsccPath = $compilerCandidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
                Select-Object -First 1
        }
    }
    if (-not $IsccPath -or -not (Test-Path -LiteralPath $IsccPath -PathType Leaf)) {
        throw 'Install Inno Setup 6.3+ (https://jrsoftware.org/isdl.php), pass -IsccPath, or use -ZipOnly.'
    }
    $IsccPath = (Resolve-Path -LiteralPath $IsccPath).Path
}

Push-Location $projectRoot
try {
    if (-not $IntellijLocalPath) {
        $IntellijLocalPath = Join-Path $projectRoot 'build\sdk\community-2025.2.6.3'
        if (-not (Test-Path -LiteralPath (Join-Path $IntellijLocalPath 'product-info.json'))) {
            & python -B (Join-Path $PSScriptRoot 'fetch_community.py')
            if ($LASTEXITCODE -ne 0) { throw 'Pinned platform download failed.' }
            & python -B -c 'import sys,zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])' `
                (Join-Path $projectRoot 'build\sdk\ideaIC-2025.2.6.3.win.zip') $IntellijLocalPath
            if ($LASTEXITCODE -ne 0) { throw 'Pinned platform extraction failed.' }
        }
    }
    if (-not $RuntimePath) {
        & python -B (Join-Path $PSScriptRoot 'fetch_runtime.py')
        if ($LASTEXITCODE -ne 0) { throw 'Pinned runtime verification failed.' }
        $RuntimePath = Join-Path $projectRoot 'build\sdk\jbr-21.0.9-b1038.78'
    }
    $gradleArguments = @('--console=plain', '-PwithIntellij=true')
    if ($StandaloneDirectory) {
        $gradleArguments += "-PstandaloneOutputDir=$([System.IO.Path]::GetFullPath($StandaloneDirectory))"
    }
    $gradleArguments += "-PintellijLocalPath=$((Resolve-Path -LiteralPath $IntellijLocalPath).Path)"
    $gradleArguments += "-PpvfRuntimePath=$((Resolve-Path -LiteralPath $RuntimePath).Path)"
    $gradleArguments += @('check', ':pvf-product:productZip')
    & (Join-Path $projectRoot 'gradlew.bat') @gradleArguments
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed ($LASTEXITCODE)." }

    $standaloneDirectory = (Get-Content -LiteralPath (Join-Path $projectRoot 'pvf-product\build\product-location.txt') -Raw).Trim()
    $productInfo = Get-Content -LiteralPath (Join-Path $standaloneDirectory 'app\product-info.json') -Raw | ConvertFrom-Json
    if ($productInfo.productCode -ne 'PVS' -or $productInfo.mainModule -ne 'studio.pvf.product') {
        throw 'The assembled directory is not a PVF Studio product.'
    }
    $appVersion = [string]$productInfo.version
    if ($appVersion -notmatch '^\d+\.\d+\.\d+$') {
        throw "Windows release requires a numeric major.minor.patch version, got '$appVersion'."
    }

    $zipPath = Join-Path $distributionDirectory "PVFStudio-$appVersion-windows-x64-portable.zip"
    if (-not (Test-Path -LiteralPath $zipPath -PathType Leaf)) { throw "Missing ZIP: $zipPath" }
    $gameData = Get-ChildItem -LiteralPath $standaloneDirectory -Recurse -File -Filter '*.pvf' |
        Select-Object -First 1
    if ($gameData) { throw "Game PVF must not be shipped: $($gameData.FullName)" }
    foreach ($unwanted in @('app\docs','app\README.md','app\CHANGELOG.md','app\runtime','app\plugins')) {
        if (Test-Path -LiteralPath (Join-Path $standaloneDirectory $unwanted)) {
            throw "Unexpected development files or legacy host directory: $unwanted"
        }
    }
    $releaseFiles = @($zipPath)

    if (-not $ZipOnly) {
        $compilerArguments = @(
            '/Qp',
            "/DAppVersion=$appVersion",
            "/DSourceDir=$standaloneDirectory",
            "/DReleaseDir=$distributionDirectory",
            (Join-Path $PSScriptRoot 'windows\PVFStudio.iss')
        )
        & $IsccPath @compilerArguments
        if ($LASTEXITCODE -ne 0) { throw "Inno Setup compilation failed ($LASTEXITCODE)." }
        $installerPath = Join-Path $distributionDirectory "PVFStudio-$appVersion-windows-x64-setup.exe"
        if (-not (Test-Path -LiteralPath $installerPath -PathType Leaf)) {
            throw "Missing installer: $installerPath"
        }
        $releaseFiles += $installerPath
    }

    $checksumPath = Join-Path $distributionDirectory "PVFStudio-$appVersion-SHA256SUMS.txt"
    $checksums = foreach ($releaseFile in $releaseFiles) {
        $hash = (Get-FileHash -LiteralPath $releaseFile -Algorithm SHA256).Hash.ToLowerInvariant()
        '{0}  {1}' -f $hash, (Split-Path -Leaf $releaseFile)
    }
    [System.IO.File]::WriteAllText($checksumPath, ($checksums -join "`n") + "`n", [System.Text.UTF8Encoding]::new($false))
    Write-Host "`nRelease files (upload these files together):"
    Get-Item -LiteralPath @($releaseFiles + $checksumPath) | Select-Object Name, Length, FullName
} finally {
    Pop-Location
}
