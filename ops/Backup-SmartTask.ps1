#Requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Database,
    [Parameter(Mandatory)][string] $AttachmentPath,
    [Parameter(Mandatory)][string] $BackupRoot,
    [Parameter(Mandatory)][string] $MySqlBin,
    [string] $DatabaseHost = '127.0.0.1',
    [ValidateRange(1,65535)][int] $DatabasePort = 3306,
    [string] $Username = 'root', [pscredential] $Credential, [switch] $EmptyPassword,
    [switch] $BackendStopped,
    [string] $BackendHost = '127.0.0.1', [ValidateRange(1,65535)][int] $BackendPort = 8080
)
. (Join-Path $PSScriptRoot 'SmartTask.Backup.Common.ps1')
Assert-SmartTaskDatabaseName $Database
Assert-SmartTaskOffline -BackendStopped:$BackendStopped -BackendHost $BackendHost -BackendPort $BackendPort
$source = Get-SmartTaskSafePath $AttachmentPath
$root = Get-SmartTaskSafePath $BackupRoot
if (-not (Test-Path -LiteralPath $source -PathType Container)) { throw 'AttachmentPath must be the existing storage directory for this database (including an empty one).' }
if ($root -eq $source -or $root.StartsWith($source + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Backups must be outside the live attachment directory.' }
$sourceFiles = @(Get-SmartTaskBackupFiles $source)
$sourceRecords = @($sourceFiles | ForEach-Object {
    [pscustomobject]@{
        FullName = $_.FullName
        Relative = $_.FullName.Substring($source.Length + 1).Replace('\', '/')
        Length = $_.Length
        Sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    }
})
$credentialValue = Get-SmartTaskCredential -Username $Username -Credential $Credential -EmptyPassword:$EmptyPassword
$connection = @{ DatabaseHost = $DatabaseHost; DatabasePort = $DatabasePort; Credential = $credentialValue }
$mysql = Join-Path (Get-SmartTaskSafePath $MySqlBin) 'mysql.exe'
$dump = Join-Path $MySqlBin 'mysqldump.exe'
$tableCount = Invoke-SmartTaskMySql @connection -Executable $mysql -Arguments @('--batch', '--skip-column-names', '--binary-mode') -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database';"
if ([int]$tableCount.Trim() -eq 0) { throw 'Source database is missing or contains no tables.' }
if (-not (Test-Path -LiteralPath $root)) { New-SmartTaskPrivateDirectory $root | Out-Null }
$snapshot = New-SmartTaskPrivateDirectory (Join-Path $root ((Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8)))
try {
    $sqlPath = Join-Path $snapshot 'database.sql'
    $dumpArguments = @('--single-transaction', '--quick', '--hex-blob', '--default-character-set=utf8mb4', '--no-tablespaces', '--set-gtid-purged=OFF', '--skip-add-drop-table', '--skip-add-locks', '--skip-comments', '--triggers', "--result-file=$sqlPath", $Database)
    Invoke-SmartTaskMySql @connection -Executable $dump -Arguments $dumpArguments | Out-Null
    $storage = New-SmartTaskPrivateDirectory (Join-Path $snapshot 'attachments')
    foreach ($record in $sourceRecords) {
        $target = Join-Path $storage $record.Relative.Replace('/', '\')
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
        [IO.File]::Copy($record.FullName, $target, $false)
        $current = Get-Item -LiteralPath $record.FullName -Force
        if ($current.Length -ne $record.Length -or
            (Get-FileHash -LiteralPath $record.FullName -Algorithm SHA256).Hash -ne $record.Sha256 -or
            (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $record.Sha256) {
            throw 'Attachment changed during backup. Keep every writer stopped and retry.'
        }
    }
    $finalRelativePaths = @(Get-SmartTaskBackupFiles $source | ForEach-Object { $_.FullName.Substring($source.Length + 1).Replace('\', '/') } | Sort-Object)
    $initialRelativePaths = @($sourceRecords | ForEach-Object { $_.Relative } | Sort-Object)
    if ($initialRelativePaths.Count -ne $finalRelativePaths.Count -or
        ($initialRelativePaths.Count -gt 0 -and @(Compare-Object -ReferenceObject $initialRelativePaths -DifferenceObject $finalRelativePaths).Count -gt 0)) {
        throw 'Attachment file set changed during backup. Keep every writer stopped and retry.'
    }
    Assert-SmartTaskOffline -BackendStopped:$BackendStopped -BackendHost $BackendHost -BackendPort $BackendPort
    $records = @(Get-SmartTaskBackupFiles $snapshot | ForEach-Object { Get-SmartTaskFileRecord $snapshot $_ })
    $manifest = [ordered]@{
        format = 'smarttask-offline-backup'; version = 1
        createdAtUtc = [DateTime]::UtcNow.ToString('o')
        sourceDatabase = $Database; consistency = 'all-writers-stopped'
        files = $records
    }
    [IO.File]::WriteAllText((Join-Path $snapshot 'manifest.json'), ($manifest | ConvertTo-Json -Depth 5), (New-Object Text.UTF8Encoding($false)))
    Write-Output "Backup complete: $snapshot"
    Write-Output 'The snapshot contains personal data and password hashes. Keep it private and encrypted at rest.'
} catch {
    Write-Warning "Incomplete snapshot retained for inspection (do not restore it): $snapshot"
    throw
}
