#Requires -Version 7.0
<#
Create:  .\scripts\Manage-SigningBackup.ps1
Verify:  .\scripts\Manage-SigningBackup.ps1 -Action Verify -Path C:\Backups\signing.age
Restore: .\scripts\Manage-SigningBackup.ps1 -Action Restore -Path C:\Backups\signing.age
Run in an interactive PowerShell terminal. age reads the recovery passphrase directly
from the terminal; this script never receives it. Never redirect decrypted data to logs.
#>
[CmdletBinding()]
param(
    [ValidateSet('Create', 'Verify', 'Restore')]
    [string]$Action = 'Create',
    [string]$Path,
    [string]$SigningDirectory = (Join-Path $env:LOCALAPPDATA 'Anushtanam\signing'),
    [string]$JavaHome = $env:JAVA_HOME
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw 'This Windows helper requires DPAPI. The encrypted age backup itself is portable.'
}
$repositoryPath = Split-Path $PSScriptRoot -Parent
if (!$Path) {
    if ($Action -ne 'Create') { throw 'Specify the encrypted backup using -Path.' }
    $Path = Join-Path $repositoryPath 'build-logs\backups\anushtanam-signing-backup.age'
}
$Path = [IO.Path]::GetFullPath($Path)
$SigningDirectory = [IO.Path]::GetFullPath($SigningDirectory)
$credentialPath = Join-Path $SigningDirectory 'signing-credentials.clixml'

function Get-AgeExecutable {
    # Official release archive digest, independently checked against the GitHub release.
    $toolDirectory = Join-Path $repositoryPath 'build-logs\tools\age-1.3.2'
    $executable = Join-Path $toolDirectory 'age\age.exe'
    $expectedExecutableHash = '2821A4ED191DA07372ACD302E5F6FEAE7A7985E285E1417765EBE74025AF45F0'
    if (Test-Path -LiteralPath $executable) {
        if ((Get-FileHash -LiteralPath $executable -Algorithm SHA256).Hash -ne $expectedExecutableHash) {
            throw 'The cached age executable failed checksum verification.'
        }
        return $executable
    }
    New-Item -ItemType Directory -Path $toolDirectory -Force | Out-Null
    $archive = Join-Path $toolDirectory 'age.zip'
    Invoke-WebRequest -Uri 'https://github.com/FiloSottile/age/releases/download/v1.3.2/age-v1.3.2-windows-amd64.zip' -OutFile $archive
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne
        'F48D8F8F9EBE903AB5027ED067652F2CC1DB94BC206976430133B905DCD8E8C7') {
        throw 'The age download failed checksum verification.'
    }
    Expand-Archive -LiteralPath $archive -DestinationPath $toolDirectory -Force
    if ((Get-FileHash -LiteralPath $executable -Algorithm SHA256).Hash -ne $expectedExecutableHash) {
        throw 'The extracted age executable failed checksum verification.'
    }
    return $executable
}

function Read-Backup([string]$BackupPath) {
    # Keep stdout in memory: it contains decrypted key material and passwords.
    $decrypted = & $age --decrypt $BackupPath
    if ($LASTEXITCODE -ne 0) { throw 'Backup decryption failed. Check the recovery passphrase and file.' }
    try {
        $backup = ($decrypted -join "`n") | ConvertFrom-Json
        if ($backup.Format -ne 'anushtanam-signing-backup' -or $backup.Version -ne 1 -or
            [string]::IsNullOrWhiteSpace($backup.KeyAlias) -or
            [string]::IsNullOrEmpty($backup.StorePassword) -or
            [string]::IsNullOrEmpty($backup.KeyPassword)) {
            throw 'Invalid backup fields.'
        }
        $keyBytes = [Convert]::FromBase64String($backup.KeyStoreBase64)
        $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($keyBytes))
        if ($hash -ne $backup.KeyStoreSha256) { throw 'Keystore hash mismatch.' }
        return $backup
    } catch {
        # JSON parser errors can contain source text; never expose decrypted fields.
        throw 'The decrypted backup format or keystore integrity is invalid.'
    } finally {
        $decrypted = $null
    }
}

function Protect-Directory([string]$Directory) {
    New-Item -ItemType Directory -Path $Directory -Force | Out-Null
    $owner = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl = [Security.AccessControl.DirectorySecurity]::new()
    $acl.SetOwner($owner)
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($sid in @($owner, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
        $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new(
            $sid, 'FullControl', 'ContainerInherit, ObjectInherit', 'None', 'Allow'))
    }
    Set-Acl -LiteralPath $Directory -AclObject $acl
}

$age = Get-AgeExecutable
if ($Action -eq 'Create') {
    if (Test-Path -LiteralPath $Path) { throw 'Backup already exists. Choose another -Path; nothing was overwritten.' }
    if (!(Test-Path -LiteralPath $credentialPath -PathType Leaf)) {
        throw "Cannot find or access signing credentials at '$credentialPath'. Run in a regular Windows terminal as the account that saved the credentials. Use -SigningDirectory to specify the signing folder explicitly."
    }
    $credentials = Import-Clixml -LiteralPath $credentialPath
    $partialPath = $Path + '.' + [Guid]::NewGuid().ToString('N') + '.partial'
    try {
        & (Join-Path $PSScriptRoot 'Invoke-SignedRelease.ps1') -Task Validate -CredentialPath $credentialPath -JavaHome $JavaHome
        $payload = [ordered]@{
            Format = 'anushtanam-signing-backup'
            Version = 1
            CreatedUtc = [DateTime]::UtcNow.ToString('o')
            KeyAlias = $credentials.KeyAlias
            KeyStoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($credentials.KeyStorePath))
            KeyStoreSha256 = (Get-FileHash -LiteralPath $credentials.KeyStorePath -Algorithm SHA256).Hash
            StorePassword = [Net.NetworkCredential]::new('', $credentials.StorePassword).Password
            KeyPassword = [Net.NetworkCredential]::new('', $credentials.KeyPassword).Password
        }
        New-Item -ItemType Directory -Path (Split-Path $Path -Parent) -Force | Out-Null
        Write-Host 'Choose a NEW recovery passphrase. Keep it in your password manager or a safe place separate from Google Drive.'
        Write-Host 'age can generate a strong passphrase if you leave its prompt empty; save it privately when shown.'
        # Only an encrypted .partial file touches disk. age reads its passphrase from CONIN$.
        $payload | ConvertTo-Json -Compress | & $age --passphrase --output $partialPath
        if ($LASTEXITCODE -ne 0) { throw 'Backup encryption failed.' }
        Write-Host 'Enter the recovery passphrase again to verify the encrypted backup.'
        $verified = Read-Backup $partialPath
        foreach ($field in @('KeyAlias', 'KeyStoreBase64', 'KeyStoreSha256', 'StorePassword', 'KeyPassword')) {
            if ($verified.$field -cne $payload[$field]) { throw 'Backup round-trip verification failed.' }
        }
        Move-Item -LiteralPath $partialPath -Destination $Path
        Write-Host 'Backup encryption and recovery verification passed. Upload only this encrypted file to Google Drive:'
        Write-Output $Path
        Write-Host ('SHA-256: ' + (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash)
    } finally {
        # Exact, task-created temporary file only; never delete an existing backup.
        if (Test-Path -LiteralPath $partialPath) { Remove-Item -LiteralPath $partialPath }
        $credentials.StorePassword.Dispose()
        $credentials.KeyPassword.Dispose()
        $payload = $null
        $verified = $null
    }
} else {
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { throw 'Encrypted backup file not found.' }
    $backup = Read-Backup $Path
    try {
        if ($Action -eq 'Verify') {
            Write-Output 'Backup decrypts successfully and its keystore SHA-256 matches.'
        } else {
            $keyPath = Join-Path $SigningDirectory 'upload-keystore.jks'
            if ((Test-Path -LiteralPath $keyPath) -or (Test-Path -LiteralPath $credentialPath)) {
                throw 'Signing files already exist. Restore refuses to overwrite them; use an empty -SigningDirectory.'
            }
            Protect-Directory $SigningDirectory
            $id = [Guid]::NewGuid().ToString('N')
            $temporaryKey = Join-Path $SigningDirectory ($id + '.jks')
            $temporaryCredentials = Join-Path $SigningDirectory ($id + '.clixml')
            $restored = $null
            try {
                [IO.File]::WriteAllBytes($temporaryKey, [Convert]::FromBase64String($backup.KeyStoreBase64))
                $restored = [pscustomobject]@{
                    Version = 1
                    KeyStorePath = $temporaryKey
                    KeyAlias = $backup.KeyAlias
                    StorePassword = (ConvertTo-SecureString $backup.StorePassword -AsPlainText -Force)
                    KeyPassword = (ConvertTo-SecureString $backup.KeyPassword -AsPlainText -Force)
                }
                $restored | Export-Clixml -LiteralPath $temporaryCredentials -Depth 3
                & (Join-Path $PSScriptRoot 'Invoke-SignedRelease.ps1') -Task Validate -CredentialPath $temporaryCredentials -JavaHome $JavaHome
                $restored.KeyStorePath = $keyPath
                $restored | Export-Clixml -LiteralPath $temporaryCredentials -Depth 3
                Move-Item -LiteralPath $temporaryKey -Destination $keyPath
                Move-Item -LiteralPath $temporaryCredentials -Destination $credentialPath
                Write-Output 'Restored and verified the original signing key. Passwords are DPAPI-encrypted for this Windows account.'
                Write-Output $SigningDirectory
            } finally {
                foreach ($temporaryFile in @($temporaryKey, $temporaryCredentials)) {
                    if (Test-Path -LiteralPath $temporaryFile) { Remove-Item -LiteralPath $temporaryFile }
                }
                if ($null -ne $restored) { $restored.StorePassword.Dispose(); $restored.KeyPassword.Dispose() }
            }
        }
    } finally {
        $backup = $null
    }
}
