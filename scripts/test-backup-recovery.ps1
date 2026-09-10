$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
Push-Location $taskRoot
try {
    & .\gradlew.bat :core:data:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.mantrasiddhi.data.local.BackupRecoveryTest' '-Pandroid.testInstrumentationRunnerArguments.firebaseEmulator=true' --console=plain
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally { Pop-Location }
