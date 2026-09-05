param(
    [string]$Serial = 'emulator-5580',
    [string]$AdbPath = 'adb',
    [int]$AdbServerPort = 5037,
    [string[]]$Pages = @('welcome', 'home', 'settings', 'practice', 'progress', 'recitation'),
    [switch]$Dark,
    [string]$Suffix = ''
)

$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskOutput = Join-Path $taskRoot 'docs/ui-review'
New-Item -ItemType Directory -Force -Path $taskOutput | Out-Null
$taskMode = if ($Dark) { 'dark' } else { 'light' }
if ($Suffix) {
    if ($Suffix -notmatch '^[a-z-]+$') { throw 'Invalid capture suffix' }
    $taskMode = "$taskMode-$Suffix"
}
$taskDark = if ($Dark) { 'true' } else { 'false' }
foreach ($taskPage in $Pages) {
    if ($taskPage -notmatch '^[a-z-]+$') { throw 'Invalid review page name' }
    & $AdbPath -P $AdbServerPort -s $Serial shell am start -W -S -n shastralife.anushtanam/com.mantrasiddhi.review.DesignReviewActivity --es page $taskPage --ez dark $taskDark | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not open $taskPage" }
    Start-Sleep -Seconds 3
    # UIAutomator waits for an idle rendered window, avoiding the Android launch animation.
    & $AdbPath -P $AdbServerPort -s $Serial shell uiautomator dump /sdcard/lotus-review-window.xml | Out-Null
    $taskRemote = "/sdcard/lotus-review-$taskPage-$taskMode.png"
    & $AdbPath -P $AdbServerPort -s $Serial shell screencap -p $taskRemote
    if ($LASTEXITCODE -ne 0) { throw "Could not capture $taskPage" }
    & $AdbPath -P $AdbServerPort -s $Serial pull $taskRemote (Join-Path $taskOutput "$taskPage-$taskMode.png") | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not save $taskPage" }
    Write-Output "$taskPage-$taskMode.png"
}
