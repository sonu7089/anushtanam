param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [ValidateSet('welcome', 'home')][string]$AccountState = 'welcome',
    [ValidateRange(1, 100)][int]$Iterations = 20,
    [switch]$Emulator,
    [switch]$HistoryOnly,
    [switch]$FreshHistoryProcesses,
    [ValidateSet('all', '0', '1000', '10000')][string]$HistorySize = 'all'
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$output = Join-Path $taskRoot ('build/perf-baseline/' + $runId)
New-Item -ItemType Directory -Path $output -Force | Out-Null
function AdbRead([string[]]$Arguments) {
    $value = & adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($Arguments -join ' ')" }
    return $value
}
Push-Location $taskRoot
$previousSerial = $env:ANDROID_SERIAL
try {
    $env:ANDROID_SERIAL = $Serial
    if ((AdbRead @('get-state')) -ne 'device') { throw 'Unlock and authorize the selected device first.' }
    $virtual = (AdbRead @('shell', 'getprop', 'ro.kernel.qemu')) -eq '1'
    if ($virtual -and !$Emulator) { throw 'Pass -Emulator to explicitly label emulator-only evidence.' }
    @{
        commit = (& git rev-parse HEAD); dirty = @(& git status --short)
        serial = $Serial; accountState = $AccountState; iterations = $Iterations
        emulator = $virtual; timestamp = (Get-Date).ToString('o')
        historyOnly = [bool]$HistoryOnly; historySize = $HistorySize
        freshHistoryProcesses = [bool]$FreshHistoryProcesses
        fingerprint = (AdbRead @('shell', 'getprop', 'ro.build.fingerprint'))
        size = (AdbRead @('shell', 'wm', 'size')); density = (AdbRead @('shell', 'wm', 'density'))
        timezone = (AdbRead @('shell', 'getprop', 'persist.sys.timezone'))
        fontScale = (AdbRead @('shell', 'settings', 'get', 'system', 'font_scale'))
        animatorScale = (AdbRead @('shell', 'settings', 'get', 'global', 'animator_duration_scale'))
        transitionScale = (AdbRead @('shell', 'settings', 'get', 'global', 'transition_animation_scale'))
        windowScale = (AdbRead @('shell', 'settings', 'get', 'global', 'window_animation_scale'))
        battery = (AdbRead @('shell', 'dumpsys', 'battery'))
        display = (AdbRead @('shell', 'dumpsys', 'display'))
        thermal = (AdbRead @('shell', 'dumpsys', 'thermalservice'))
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $output 'environment.json')
    # Run installed instrumentation directly: Gradle's connected-test cleanup removes
    # the driver and its external-files results. Keep the test account and raw files.
    if ($HistoryOnly) {
        & .\gradlew.bat :core:data:assembleDebugAndroidTest --console=plain *> (Join-Path $output 'gradle.log')
    } else {
        & .\gradlew.bat :app:assembleBenchmark :macrobenchmark:assembleBenchmark --console=plain *> (Join-Path $output 'gradle.log')
    }
    if ($LASTEXITCODE -ne 0) { throw 'Baseline build failed; inspect gradle.log.' }
    if ($FreshHistoryProcesses -and !$HistoryOnly) { throw '-FreshHistoryProcesses requires -HistoryOnly.' }
    $sampleIterations = if ($FreshHistoryProcesses) { 1 } else { $Iterations }
    $instrumentArgs = @('shell', 'am', 'instrument', '-w', '-e', 'iterations', "$sampleIterations", '-e', 'runId', $runId)
    if ($HistoryOnly) {
        AdbRead @('install', '--no-streaming', '-r', '-t', 'core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk') | Set-Content (Join-Path $output 'install.log')
        if ($HistorySize -ne 'all') { $instrumentArgs += @('-e', 'historySizes', $HistorySize) }
        $instrumentArgs += @('-e', 'perfBaseline', 'true')
        $instrumentArgs += @('-e', 'class', 'com.mantrasiddhi.data.local.HistoryBaselineTest', 'com.mantrasiddhi.data.test/androidx.test.runner.AndroidJUnitRunner')
    } else {
        AdbRead @('install', '--no-streaming', '-r', 'frontend/android/build/outputs/apk/benchmark/app-benchmark.apk') | Set-Content (Join-Path $output 'install.log')
        AdbRead @('install', '--no-streaming', '-r', '-t', 'macrobenchmark/build/outputs/apk/benchmark/macrobenchmark-benchmark.apk') | Add-Content (Join-Path $output 'install.log')
        AdbRead @('shell', 'pm', 'grant', 'shastralife.anushtanam', 'android.permission.POST_NOTIFICATIONS') | Add-Content (Join-Path $output 'install.log')
        $instrumentArgs += @('-e', 'accountState', $AccountState, '-e', 'additionalTestOutputDir', "/sdcard/Android/media/com.mantrasiddhi.macrobenchmark/perf-$runId")
        if ($Emulator) { $instrumentArgs += @('-e', 'androidx.benchmark.suppressErrors', 'EMULATOR') }
        $instrumentArgs += 'com.mantrasiddhi.macrobenchmark/androidx.test.runner.AndroidJUnitRunner'
    }
    $apkPaths = if ($HistoryOnly) { @('core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk') }
        else { @('frontend/android/build/outputs/apk/benchmark/app-benchmark.apk', 'macrobenchmark/build/outputs/apk/benchmark/macrobenchmark-benchmark.apk') }
    @($apkPaths | ForEach-Object { Get-FileHash -LiteralPath $_ }) | ConvertTo-Json | Set-Content (Join-Path $output 'build-hashes.json')
    $pullExit = 0
    $passed = $true
    $processCount = if ($FreshHistoryProcesses) { $Iterations } else { 1 }
    for ($sample = 1; $sample -le $processCount; $sample++) {
        $sampleArgs = $instrumentArgs.Clone()
        $sampleId = if ($FreshHistoryProcesses) { "$runId-sample-$sample" } else { $runId }
        $sampleArgs[[Array]::IndexOf($sampleArgs, 'runId') + 1] = $sampleId
        $testLog = Join-Path $output "instrumentation-$sample.log"
        & adb -s $Serial @sampleArgs *> $testLog
        # ADB can exit zero even when Android reports a native crash or failed tests.
        $samplePassed = Select-String -LiteralPath $testLog -Pattern '^OK \([0-9]+ tests?\)'
        if ($FreshHistoryProcesses) {
            & adb -s $Serial pull "/sdcard/Android/data/com.mantrasiddhi.data.test/files/perf-baseline/$sampleId" $output
            if ($LASTEXITCODE -ne 0) { $pullExit = 1 }
            Write-Output "History process $sample/$processCount completed; results: $output"
        }
        if (!$samplePassed) { $passed = $false; break }
    }
    if ($HistoryOnly) {
        if (!$FreshHistoryProcesses) {
            & adb -s $Serial pull "/sdcard/Android/data/com.mantrasiddhi.data.test/files/perf-baseline/$runId" $output
            $pullExit = $LASTEXITCODE
        }
    } else {
        & adb -s $Serial pull "/sdcard/Android/data/com.mantrasiddhi.macrobenchmark/files/perf-baseline/$runId" $output
        $pullExit = $LASTEXITCODE
        $apk = Join-Path $taskRoot 'frontend/android/build/outputs/apk/benchmark/app-benchmark.apk'
        if (Test-Path -LiteralPath $apk) { Get-FileHash -LiteralPath $apk | ConvertTo-Json | Set-Content (Join-Path $output 'apk-hash.json') }
        & adb -s $Serial pull "/sdcard/Android/media/com.mantrasiddhi.macrobenchmark/perf-$runId" (Join-Path $output 'traces')
        if ($LASTEXITCODE -ne 0) { $pullExit = 1 }
    }
    AdbRead @('shell', 'dumpsys', 'thermalservice') | Set-Content (Join-Path $output 'thermal-after.txt')
    Write-Output "Baseline artifacts: $output"
    if (!$passed) { throw 'Baseline tests failed or crashed; inspect instrumentation-*.log and any partial results.' }
    if ($pullExit -ne 0) { throw 'Tests completed but raw result retrieval failed.' }
} finally {
    $env:ANDROID_SERIAL = $previousSerial
    Pop-Location
}
