param(
    [Parameter(Mandatory = $true)][string]$PrimarySerial,
    [Parameter(Mandatory = $true)][string]$SecondarySerial,
    [int]$FirestorePort = 58080,
    [int]$AuthPort = 59099,
    [string]$OutputDirectory = 'build/step13/recovery',
    [switch]$CrossDeviceOnly
)
# Run inside firebase emulators:exec with the matching demo project and ports.
# Only the isolated data-test APK is used; installed application data is untouched.
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$output = [IO.Path]::GetFullPath((Join-Path $taskRoot $OutputDirectory))
New-Item -ItemType Directory -Force -Path $output | Out-Null
if ($PrimarySerial -eq $SecondarySerial) { throw 'Recovery requires two different Android devices.' }
$runner = 'com.mantrasiddhi.data.test/androidx.test.runner.AndroidJUnitRunner'
$cloudArgs = @('-e', 'firebaseEmulator', 'true', '-e', 'firebaseEmulatorHost', '127.0.0.1',
    '-e', 'firebaseFirestorePort', "$FirestorePort", '-e', 'firebaseAuthPort', "$AuthPort")
function AssertPass([string]$Path, [int]$Count) {
    if ((Get-Content -LiteralPath $Path -Raw) -notmatch "(?m)^OK \($Count tests?\)") {
        throw "Instrumentation did not pass $Count tests: $Path"
    }
}
foreach ($serial in @($PrimarySerial, $SecondarySerial)) {
    if ((& adb -s $serial get-state) -ne 'device') { throw "ADB unavailable: $serial" }
    & adb -s $serial install --no-streaming -r -t "$taskRoot/core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk"
    if ($LASTEXITCODE -ne 0) { throw "Test APK install failed: $serial" }
    & adb -s $serial reverse "tcp:$FirestorePort" "tcp:$FirestorePort"
    if ($LASTEXITCODE -ne 0) { throw 'Firestore reverse failed' }
    & adb -s $serial reverse "tcp:$AuthPort" "tcp:$AuthPort"
    if ($LASTEXITCODE -ne 0) { throw 'Auth reverse failed' }
}
if (!$CrossDeviceOnly) {
$classes = 'com.mantrasiddhi.data.local.SessionCountingRecoveryTest,com.mantrasiddhi.data.local.BackupRecoveryTest,com.mantrasiddhi.data.local.RemoteDiscoveryTest'
& adb -s $PrimarySerial shell am instrument -w @cloudArgs -e class $classes $runner *> "$output/cloud.log"
AssertPass "$output/cloud.log" 8
Write-Output 'Eight cloud recovery regressions passed on the primary device.'

$crashClass = 'com.mantrasiddhi.data.local.SessionCountingCrashTest'
& adb -s $PrimarySerial shell am instrument -w -e countingCrashPhase save -e class $crashClass $runner *> "$output/crash-save.log"
if ((Get-Content "$output/crash-save.log" -Raw) -notmatch 'Process crashed') { throw 'Expected injected process death was not observed' }
& adb -s $PrimarySerial shell am instrument -w -e countingCrashPhase recover -e class $crashClass $runner *> "$output/crash-recover.log"
AssertPass "$output/crash-recover.log" 1
Write-Output 'A fresh process recovered the committed session and outbox without duplicate credit.'
}

foreach ($pair in @(@{name='primary-receives';receive=$PrimarySerial;send=$SecondarySerial},
                     @{name='secondary-receives';receive=$SecondarySerial;send=$PrimarySerial})) {
    $email = 'audit-' + [Guid]::NewGuid().ToString('N') + '@example.test'
    $receiveLog = "$output/$($pair.name).log"
    $sendLog = "$output/$($pair.name)-send.log"
    $crossClass = 'com.mantrasiddhi.data.local.CrossDeviceDiscoveryTest'
    $arguments = @('-s', $pair.receive, 'shell', 'am', 'instrument', '-w') + $cloudArgs +
        @('-e', 'crossDeviceEmail', $email, '-e', 'crossDeviceRole', 'receive', '-e', 'class', "$crossClass#receive", $runner)
    $receiver = Start-Process -FilePath (Get-Command adb).Source -ArgumentList $arguments -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $receiveLog -RedirectStandardError "$receiveLog.stderr"
    try {
        $deadline = [DateTime]::UtcNow.AddSeconds(45)
        # Get-Content on an empty file returns $null/an empty pipeline. Cast to a
        # scalar string so -notmatch cannot produce an empty (false) result and
        # incorrectly release the sender before receiver initialization.
        while (!(Test-Path $receiveLog) -or [string](Get-Content $receiveLog -Raw) -notmatch 'REMOTE_RECEIVER_READY') {
            if ($receiver.HasExited -or [DateTime]::UtcNow -gt $deadline) { throw "Receiver failed to become ready: $receiveLog" }
            Start-Sleep -Milliseconds 250
        }
        if ($receiver.HasExited) { throw "Receiver exited before sending: $receiveLog" }
        & adb -s $pair.send shell am instrument -w @cloudArgs -e crossDeviceEmail $email -e crossDeviceRole send -e class "$crossClass#send" $runner *> $sendLog
        AssertPass $sendLog 1
        Write-Output "$($pair.name): sender committed; waiting through the real retry deadline."
        $deadline = [DateTime]::UtcNow.AddSeconds(120)
        while (!$receiver.WaitForExit(500)) {
            if ([DateTime]::UtcNow -gt $deadline) { throw "Receiver timed out: $receiveLog" }
        }
        AssertPass $receiveLog 1
        Write-Output "$($pair.name): 72 japa recovered exactly once."
    } finally {
        if (!$receiver.HasExited) {
            & adb -s $pair.receive shell am force-stop com.mantrasiddhi.data.test
            $receiver.WaitForExit(5000) | Out-Null
        }
    }
}
