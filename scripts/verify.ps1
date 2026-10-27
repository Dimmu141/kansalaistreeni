param(
    [string]$Python = 'python',
    [switch]$Offline,
    [switch]$SkipDeviceTests
)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
Push-Location $workspace
try {
    & (Join-Path $PSScriptRoot 'verify-source.ps1')
    & $Python (Join-Path $PSScriptRoot 'verify-architecture.py')
    if ($LASTEXITCODE -ne 0) { throw 'Architecture contracts failed' }
    & $Python (Join-Path $PSScriptRoot 'verify-content.py')
    if ($LASTEXITCODE -ne 0) { throw 'Content review gate failed' }
    & $Python (Join-Path $PSScriptRoot 'verify-translations.py')
    if ($LASTEXITCODE -ne 0) { throw 'Translation review gate failed' }
    & $Python (Join-Path $PSScriptRoot 'test_content_review.py')
    if ($LASTEXITCODE -ne 0) { throw 'Content gate regression tests failed' }
    $tasks = @(':app:testDebugUnitTest', ':app:lintDebug', ':app:lintRelease', ':app:assembleDebug', ':app:assembleRelease', ':app:bundleRelease')
    if (-not $SkipDeviceTests) { $tasks += ':app:connectedDebugAndroidTest' }
    else { Write-Warning 'Device tests explicitly skipped; runtime verification is incomplete.' }
    $tasks += '--console=plain'
    if ($Offline) { $tasks += '--offline' }
    & (Join-Path $workspace 'gradlew.bat') @tasks
    if ($LASTEXITCODE -ne 0) { throw 'Android verification failed' }
    $signing = if ((Test-Path (Join-Path $workspace 'keystore.properties')) -or $env:KANSALAISTREENI_KEYSTORE_PROPERTIES) { 'signed with the local upload key' } else { 'unsigned' }
    Write-Output "Verification passed. Release artifacts are $signing; no content or app was published."
} finally { Pop-Location }
