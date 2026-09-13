param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9-]+$')][string]$RunId,
    [ValidateRange(1, 100)][int]$Iterations = 20,
    [string]$Adb = 'adb',
    [string]$Apk = 'frontend/android/build/outputs/apk/benchmark/app-benchmark.apk',
    [switch]$TraceCompositions,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskOutput = Join-Path $taskRoot "build/tab-jitter/$RunId"
if (Test-Path -LiteralPath $taskOutput) { throw 'RunId already exists; choose a new ID to preserve its evidence.' }
New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
function Invoke-Adb([string[]]$Arguments) {
    $result = & $Adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($Arguments -join ' ')" }
    return $result
}
Push-Location $taskRoot
try {
    if ((Invoke-Adb @('get-state')) -ne 'device') { throw 'Selected device is unavailable.' }
    $taskEmulator = (Invoke-Adb @('shell', 'getprop', 'ro.kernel.qemu')) -eq '1'
    @{
        commit = (& git rev-parse HEAD)
        fixture = $true; emulator = $taskEmulator; serial = $Serial
        iterations = $Iterations; compilation = 'None'; traceCompositions = [bool]$TraceCompositions
        fingerprint = (Invoke-Adb @('shell', 'getprop', 'ro.build.fingerprint'))
        size = (Invoke-Adb @('shell', 'wm', 'size')); density = (Invoke-Adb @('shell', 'wm', 'density'))
        fontScale = (Invoke-Adb @('shell', 'settings', 'get', 'system', 'font_scale'))
        animatorScale = (Invoke-Adb @('shell', 'settings', 'get', 'global', 'animator_duration_scale'))
        thermal = (Invoke-Adb @('shell', 'dumpsys', 'thermalservice'))
        display = (Invoke-Adb @('shell', 'dumpsys', 'display'))
        graphics = @((Invoke-Adb @('shell', 'dumpsys', 'SurfaceFlinger')) | Select-String 'GLES:|GL_RENDERER' | ForEach-Object { $_.Line })
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $taskOutput 'environment.json')
    if (!$SkipBuild) {
        & .\gradlew.bat :app:assembleBenchmark :macrobenchmark:assembleBenchmark --console=plain *> (Join-Path $taskOutput 'build.log')
        if ($LASTEXITCODE -ne 0) { throw 'Build failed; inspect build.log.' }
    }
    $taskApk = $Apk
    Copy-Item -LiteralPath $taskApk -Destination (Join-Path $taskOutput 'app.apk')
    Get-FileHash -LiteralPath $taskApk | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskOutput 'apk-hash.json')
    Invoke-Adb @('install', '--no-streaming', '-r', $taskApk) | Set-Content -LiteralPath (Join-Path $taskOutput 'install.log')
    Invoke-Adb @('install', '--no-streaming', '-r', '-t', 'macrobenchmark/build/outputs/apk/benchmark/macrobenchmark-benchmark.apk') | Add-Content -LiteralPath (Join-Path $taskOutput 'install.log')
    $taskTraceDir = "/sdcard/Android/media/com.mantrasiddhi.macrobenchmark/tab-$RunId"
    $taskArgs = @('shell', 'am', 'instrument', '-w', '-e', 'class', 'com.mantrasiddhi.macrobenchmark.TabNavigationBenchmark',
        '-e', 'navigationFixture', 'true', '-e', 'iterations', "$Iterations", '-e', 'runId', $RunId,
        '-e', 'traceCompositions', "$([bool]$TraceCompositions)".ToLowerInvariant(),
        '-e', 'additionalTestOutputDir', $taskTraceDir)
    if ($taskEmulator) { $taskArgs += @('-e', 'androidx.benchmark.suppressErrors', 'EMULATOR') }
    $taskArgs += 'com.mantrasiddhi.macrobenchmark/androidx.test.runner.AndroidJUnitRunner'
    & $Adb -s $Serial @taskArgs *> (Join-Path $taskOutput 'instrumentation.log')
    if ($LASTEXITCODE -ne 0 -or !(Select-String -LiteralPath (Join-Path $taskOutput 'instrumentation.log') -Pattern '^OK \(')) {
        throw 'Benchmark failed; inspect instrumentation.log.'
    }
    Invoke-Adb @('pull', $taskTraceDir, (Join-Path $taskOutput 'traces'))
    Invoke-Adb @('pull', "/sdcard/Android/data/com.mantrasiddhi.macrobenchmark/files/tab-navigation/$RunId", (Join-Path $taskOutput 'readiness'))
    Invoke-Adb @('shell', 'dumpsys', 'thermalservice') | Set-Content -LiteralPath (Join-Path $taskOutput 'thermal-after.txt')
    Write-Output "Navigation fixture results: $taskOutput"
} finally { Pop-Location }
