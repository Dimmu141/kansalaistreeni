param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
$evidence = Join-Path $workspace '.verification/architecture'
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
Push-Location $workspace
try {
    # Separate invocations avoid a multi-class filter being silently truncated by the runner.
    foreach ($class in @('ArchitectureRegressionTest', 'TopicRoutingFlowTest')) {
        $arguments = @(':app:connectedDebugAndroidTest', "-Pandroid.testInstrumentationRunnerArguments.class=fi.kansalaisuuskoe.$class", '--console=plain')
        if ($Offline) { $arguments += '--offline' }
        & (Join-Path $workspace 'gradlew.bat') @arguments | Tee-Object -FilePath (Join-Path $evidence "$class-run.log")
        $probeExit = $LASTEXITCODE
        $results = Get-ChildItem 'app/build/outputs/androidTest-results' -Recurse -Filter 'TEST-*.xml'
        $observed = $false
        foreach ($result in $results) {
            [xml]$resultXml = Get-Content -LiteralPath $result.FullName
            $suites = @($resultXml.SelectNodes('//testsuite') | Where-Object { $_.name -eq "fi.kansalaisuuskoe.$class" })
            if ($suites | Where-Object { [int]$_.tests -gt 0 }) { $observed = $true }
            Copy-Item -LiteralPath $result.FullName -Destination (Join-Path $evidence ("$class-" + $result.Name))
        }
        if ($probeExit -ne 0 -or -not $observed) { throw "Architecture regression class $class failed or did not execute. See .verification/architecture." }
    }
} finally { Pop-Location }
