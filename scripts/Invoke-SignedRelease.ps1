# Windows-only: DPAPI decrypts these passwords for the account that saved them.
[CmdletBinding()]
param(
    [ValidateSet('Validate', 'Bundle', 'Apk')]
    [string]$Task = 'Bundle',
    [string]$CredentialPath = (Join-Path $env:LOCALAPPDATA 'Anushtanam\signing\signing-credentials.clixml'),
    [string]$JavaHome = $env:JAVA_HOME
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw 'This credential store requires Windows DPAPI. Use your CI secret manager on other platforms.'
}
if (!(Test-Path -LiteralPath $CredentialPath -PathType Leaf)) {
    throw 'Signing credentials not found. See the release-signing section in README.md.'
}
$repositoryPath = Split-Path $PSScriptRoot -Parent
$names = @('ANDROID_KEYSTORE_PATH', 'ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_ALIAS',
    'ANDROID_KEY_PASSWORD', 'ANDROID_REQUIRE_RELEASE_SIGNING', 'JAVA_HOME')
$previousEnvironment = @{}
foreach ($name in $names) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$credentials = $null
try {
    $credentials = Import-Clixml -LiteralPath $CredentialPath
    if ($credentials.Version -ne 1 -or
        $credentials.StorePassword -isnot [Security.SecureString] -or
        $credentials.KeyPassword -isnot [Security.SecureString] -or
        [string]::IsNullOrWhiteSpace($credentials.KeyAlias) -or
        ![IO.Path]::IsPathRooted($credentials.KeyStorePath) -or
        !(Test-Path -LiteralPath $credentials.KeyStorePath -PathType Leaf)) {
        throw 'Invalid signing credential store.'
    }
    if ($JavaHome) {
        $javaExecutable = Join-Path $JavaHome 'bin\java.exe'
        if (!(Test-Path -LiteralPath $javaExecutable -PathType Leaf)) { throw 'JavaHome must point to a JDK.' }
        $env:JAVA_HOME = $JavaHome
    } else {
        $javaExecutable = (Get-Command java.exe -ErrorAction Stop).Source
    }
    $env:ANDROID_KEYSTORE_PATH = $credentials.KeyStorePath
    $env:ANDROID_KEY_ALIAS = $credentials.KeyAlias
    $env:ANDROID_KEYSTORE_PASSWORD = [Net.NetworkCredential]::new('', $credentials.StorePassword).Password
    $env:ANDROID_KEY_PASSWORD = [Net.NetworkCredential]::new('', $credentials.KeyPassword).Password
    $env:ANDROID_REQUIRE_RELEASE_SIGNING = 'true'

    # Verify both passwords and the private-key alias, without passwords in arguments.
    & $javaExecutable (Join-Path $PSScriptRoot 'VerifySigningCredentials.java')
    if ($LASTEXITCODE -ne 0) { throw 'Signing credential validation failed; no build was started.' }
    if ($Task -ne 'Validate') {
        $gradleTask = if ($Task -eq 'Bundle') { ':app:bundleRelease' } else { ':app:assembleRelease' }
        Push-Location $repositoryPath
        try {
            & (Join-Path $repositoryPath 'gradlew.bat') --no-daemon --no-configuration-cache $gradleTask
            if ($LASTEXITCODE -ne 0) { throw 'Signed release build failed.' }
        } finally {
            Pop-Location
        }
    }
} finally {
    foreach ($name in $names) {
        if ($null -eq $previousEnvironment[$name]) {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
        }
    }
    if ($null -ne $credentials) {
        foreach ($field in @('StorePassword', 'KeyPassword')) {
            $property = $credentials.PSObject.Properties[$field]
            if ($null -ne $property -and $property.Value -is [Security.SecureString]) {
                $property.Value.Dispose()
            }
        }
    }
}
