param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$OutputDirectory = 'build/step13/screens'
)
# Requires the debug APK. Capture the original step-10 density/layout fixtures.
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$output = [IO.Path]::GetFullPath((Join-Path $taskRoot $OutputDirectory))
New-Item -ItemType Directory -Force -Path $output | Out-Null
function Adb([string[]]$Arguments) {
    $value = & adb.exe -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($Arguments -join ' ')" }
    return $value
}
$sizeBefore = (Adb @('shell','wm','size')) -join "`n"
$densityBefore = (Adb @('shell','wm','density')) -join "`n"
$fontBefore = (Adb @('shell','settings','get','system','font_scale')).Trim()
@{size=$sizeBefore;density=$densityBefore;font=$fontBefore} | ConvertTo-Json | Set-Content "$output/display-before.json"
try {
    foreach ($config in @(
        @{name='mdpi-compact-light';size='360x800';density='160';font='1.0';dark='false'},
        @{name='xxxhdpi-compact-dark';size='1440x3200';density='640';font='1.0';dark='true'},
        @{name='xxhdpi-wide-largefont-light';size='1800x2880';density='480';font='1.3';dark='false'})) {
        Adb @('shell','wm','size',$config.size) | Out-Null
        Adb @('shell','wm','density',$config.density) | Out-Null
        Adb @('shell','settings','put','system','font_scale',$config.font) | Out-Null
        foreach ($page in @('welcome','home-cards','messages','family','settings','purashcharana','recitation','practice-guide')) {
            Adb @('shell','input','keyevent','KEYCODE_WAKEUP') | Out-Null
            Adb @('shell','wm','dismiss-keyguard') | Out-Null
            $launch = Adb @('shell','am','start','-W','-S','-n','shastralife.anushtanam/com.mantrasiddhi.review.DesignReviewActivity','--es','page',$page,'--ez','dark',$config.dark)
            if (($launch -join "`n") -notmatch 'Status: ok') { throw "Preview failed: $page" }
            Start-Sleep -Seconds 2
            Adb @('shell','screencap','-p','/sdcard/audit-artwork.png') | Out-Null
            Adb @('pull','/sdcard/audit-artwork.png',"$output/$page-$($config.name).png") | Out-Null
            Write-Output "$page-$($config.name)"
        }
    }
} finally {
    if ($sizeBefore -match 'Override size: (\d+x\d+)') { Adb @('shell','wm','size',$Matches[1]) | Out-Null }
    else { Adb @('shell','wm','size','reset') | Out-Null }
    if ($densityBefore -match 'Override density: (\d+)') { Adb @('shell','wm','density',$Matches[1]) | Out-Null }
    else { Adb @('shell','wm','density','reset') | Out-Null }
    Adb @('shell','settings','put','system','font_scale',$fontBefore) | Out-Null
}
