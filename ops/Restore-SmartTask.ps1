#Requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $BackupPath,
    [Parameter(Mandatory)][string] $TargetDatabase,
    [Parameter(Mandatory)][string] $TargetAttachmentPath,
    [Parameter(Mandatory)][string] $MySqlBin,
    [string] $DatabaseHost = '127.0.0.1', [ValidateRange(1,65535)][int] $DatabasePort = 3306,
    [string] $Username = 'root', [pscredential] $Credential, [switch] $EmptyPassword,
    [switch] $Execute, [switch] $TrustedBackup, [switch] $BackendStopped,
    [string] $BackendHost = '127.0.0.1', [ValidateRange(1,65535)][int] $BackendPort = 8080
)
. (Join-Path $PSScriptRoot 'SmartTask.Backup.Common.ps1')
Assert-SmartTaskDatabaseName $TargetDatabase
$backup = Get-SmartTaskSafePath $BackupPath
$storage = Get-SmartTaskSafePath $TargetAttachmentPath
if (-not (Test-Path -LiteralPath $backup -PathType Container)) { throw 'BackupPath must be an existing snapshot directory.' }
if (Test-Path -LiteralPath $storage) { throw 'Target attachment path already exists. Use a new directory; existing data is never overwritten.' }
if ($storage.StartsWith($backup + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Restore storage must be outside the backup.' }
$backupFiles = @(Get-SmartTaskBackupFiles $backup)
$manifestPath = Join-Path $backup 'manifest.json'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw 'Backup manifest is missing.' }
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($manifest.format -ne 'smarttask-offline-backup' -or $manifest.version -ne 1 -or $manifest.consistency -ne 'all-writers-stopped') { throw 'Unsupported or incomplete backup manifest.' }
Assert-SmartTaskDatabaseName $manifest.sourceDatabase
if ($manifest.sourceDatabase -eq $TargetDatabase) { throw 'Restore requires a different, NEW database name, never the original source name.' }
$expectedPaths = @{}
foreach ($entry in $manifest.files) {
    $relative = [string]$entry.path
    if ($relative -ne 'database.sql' -and $relative -notmatch '^attachments/[a-zA-Z0-9_. /-]+$') { throw 'Unexpected path in backup manifest.' }
    if ($relative -match '(^|/)\.\.?(/|$)' -or $expectedPaths.ContainsKey($relative)) { throw 'Unsafe or duplicate manifest path.' }
    if ([string]$entry.sha256 -notmatch '^[0-9a-f]{64}$' -or $entry.length -isnot [ValueType] -or [long]$entry.length -lt 0) { throw 'Invalid file metadata in backup manifest.' }
    $expectedPaths[$relative] = $true
    $file = Get-Item -LiteralPath (Join-Path $backup $relative.Replace('/', '\')) -Force
    if ($file.PSIsContainer -or $file.Length -ne $entry.length -or (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -ne $entry.sha256) { throw "Backup checksum/length mismatch: $relative" }
}
if (-not $expectedPaths.ContainsKey('database.sql')) { throw 'Backup contains no SQL dump.' }
foreach ($file in $backupFiles) {
    $relative = $file.FullName.Substring($backup.Length + 1).Replace('\', '/')
    if ($relative -ne 'manifest.json' -and -not $expectedPaths.ContainsKey($relative)) { throw "Unlisted backup file: $relative" }
}
$sqlPath = Join-Path $backup 'database.sql'
Assert-SmartTaskSqlDumpSafe $sqlPath
$credentialValue = Get-SmartTaskCredential -Username $Username -Credential $Credential -EmptyPassword:$EmptyPassword
$connection = @{ DatabaseHost = $DatabaseHost; DatabasePort = $DatabasePort; Credential = $credentialValue }
$mysql = Join-Path (Get-SmartTaskSafePath $MySqlBin) 'mysql.exe'
$clientArguments = @('--batch', '--skip-column-names', '--binary-mode', '--local-infile=0', '--default-character-set=utf8mb4')
$exists = Invoke-SmartTaskMySql @connection -Executable $mysql -Arguments $clientArguments -Sql "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$TargetDatabase';"
if ([int]$exists.Trim() -ne 0) { throw 'Target database already exists, even if empty. Choose a NEW database name.' }
if (-not $Execute) {
    Write-Output "DRY RUN OK: $($manifest.files.Count) checksums verified. Would create database '$TargetDatabase' and '$storage'. No database or attachment data was changed."
    Write-Output 'After reviewing the trusted backup, repeat with -Execute -TrustedBackup -BackendStopped.'
    return
}
if (-not $TrustedBackup) { throw 'SQL is executable database code. Only continue with -TrustedBackup for a snapshot from a trusted source.' }
Assert-SmartTaskOffline -BackendStopped:$BackendStopped -BackendHost $BackendHost -BackendPort $BackendPort
try {
    # No IF NOT EXISTS: a concurrent creator causes a failure, never a merge.
    Invoke-SmartTaskMySql @connection -Executable $mysql -Arguments $clientArguments -Sql "CREATE DATABASE ``$TargetDatabase`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" | Out-Null
    New-SmartTaskPrivateDirectory $storage | Out-Null
    Invoke-SmartTaskMySql @connection -Executable $mysql -Arguments ($clientArguments + @("--database=$TargetDatabase")) -InputFile $sqlPath | Out-Null
    foreach ($entry in $manifest.files | Where-Object { $_.path.StartsWith('attachments/') }) {
        $relative = $entry.path.Substring('attachments/'.Length)
        $target = Join-Path $storage $relative
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
        [IO.File]::Copy((Join-Path $backup $entry.path), $target, $false)
        if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $entry.sha256) { throw 'Restored attachment checksum mismatch.' }
    }
    $restoredPaths = @(Get-SmartTaskBackupFiles $storage | ForEach-Object { $_.FullName.Substring($storage.Length + 1).Replace('\', '/') } | Sort-Object)
    $expectedAttachmentPaths = @($manifest.files | Where-Object { $_.path.StartsWith('attachments/') } | ForEach-Object { $_.path.Substring('attachments/'.Length) } | Sort-Object)
    if ($expectedAttachmentPaths.Count -ne $restoredPaths.Count -or
        ($expectedAttachmentPaths.Count -gt 0 -and @(Compare-Object -ReferenceObject $expectedAttachmentPaths -DifferenceObject $restoredPaths).Count -gt 0)) {
        throw 'Restored attachment file set mismatch.'
    }
    Write-Output "Restore complete: database '$TargetDatabase', attachments '$storage'."
    Write-Output 'Keep the backend stopped until DB_URL and ATTACHMENT_STORAGE_PATH both point to this matching restored pair.'
} catch {
    Write-Warning "Restore failed. Any partial database '$TargetDatabase' or directory '$storage' was RETAINED. Do not start the app on it. Investigate, then retry with new targets. No database was dropped."
    throw
}
