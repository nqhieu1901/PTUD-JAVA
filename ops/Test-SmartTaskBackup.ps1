#Requires -Version 5.1
# Explicit restore drill. Creates disposable databases/directories and deliberately retains them.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $MySqlBin,
    [Parameter(Mandatory)][string] $TestRoot,
    [string] $DatabaseHost = '127.0.0.1', [ValidateRange(1,65535)][int] $DatabasePort = 3306,
    [string] $Username = 'root', [pscredential] $Credential, [switch] $EmptyPassword,
    [switch] $RunIntegration, [switch] $BackendStopped,
    [ValidateRange(1,65535)][int] $BackendPort = 8080
)
. (Join-Path $PSScriptRoot 'SmartTask.Backup.Common.ps1')
if (-not $RunIntegration) { throw 'This drill creates TWO disposable MySQL databases. Review the script, then use -RunIntegration -BackendStopped.' }
Assert-SmartTaskOffline -BackendStopped:$BackendStopped -BackendHost '127.0.0.1' -BackendPort $BackendPort
$credentialValue = Get-SmartTaskCredential -Username $Username -Credential $Credential -EmptyPassword:$EmptyPassword
$connection = @{ DatabaseHost = $DatabaseHost; DatabasePort = $DatabasePort; Credential = $credentialValue }
$mysql = Join-Path (Get-SmartTaskSafePath $MySqlBin) 'mysql.exe'
$suffix = [guid]::NewGuid().ToString('N').Substring(0,12)
$sourceDatabase = "smarttask_ops_src_$suffix"
$targetDatabase = "smarttask_ops_dst_$suffix"
$root = Get-SmartTaskSafePath $TestRoot
if (-not (Test-Path -LiteralPath $root)) { New-SmartTaskPrivateDirectory $root | Out-Null }
$run = New-SmartTaskPrivateDirectory (Join-Path $root $suffix)
$sourceStorage = New-SmartTaskPrivateDirectory (Join-Path $run 'source-attachments')
$targetStorage = Join-Path $run 'restored-attachments'
$backupRoot = Join-Path $run 'backups'
$storedName = [guid]::NewGuid().ToString() + '.png'
[IO.File]::WriteAllBytes((Join-Path $sourceStorage $storedName), [byte[]]@(0, 1, 13, 10, 127, 128, 255))
$argsSql = @('--batch', '--skip-column-names', '--binary-mode', '--default-character-set=utf8mb4')
$unicodeText = 'SmartTask ' + [char]0x0111 + [char]0x1ED3 + ' ' + [char]0x00E1 + 'n'
Write-Output "Disposable test artifacts will be retained: $run; databases $sourceDatabase and $targetDatabase"
Invoke-SmartTaskMySql @connection -Executable $mysql -Arguments $argsSql -Sql "CREATE DATABASE ``$sourceDatabase`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" | Out-Null
Invoke-SmartTaskMySql @connection -Executable $mysql -Arguments ($argsSql + @("--database=$sourceDatabase")) -Sql "CREATE TABLE notes (id INT PRIMARY KEY, body VARCHAR(100), stored_name VARCHAR(100)); CREATE TRIGGER notes_guard BEFORE INSERT ON notes FOR EACH ROW SET NEW.body=CONCAT(NEW.body,'!'); INSERT INTO notes VALUES (1,'$unicodeText','$storedName');" | Out-Null
$common = @{ MySqlBin = $MySqlBin; DatabaseHost = $DatabaseHost; DatabasePort = $DatabasePort; Credential = $credentialValue; BackendPort = $BackendPort }
& (Join-Path $PSScriptRoot 'Backup-SmartTask.ps1') @common -Database $sourceDatabase -AttachmentPath $sourceStorage -BackupRoot $backupRoot -BackendStopped
$snapshot = (Get-ChildItem -LiteralPath $backupRoot -Directory | Select-Object -First 1).FullName
$restore = @{ BackupPath = $snapshot; TargetDatabase = $targetDatabase; TargetAttachmentPath = $targetStorage }
& (Join-Path $PSScriptRoot 'Restore-SmartTask.ps1') @common @restore
$exists = Invoke-SmartTaskMySql @connection -Executable $mysql -Arguments $argsSql -Sql "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$targetDatabase';"
if ([int]$exists.Trim() -ne 0 -or (Test-Path -LiteralPath $targetStorage)) { throw 'Dry-run wrote target data.' }
& (Join-Path $PSScriptRoot 'Restore-SmartTask.ps1') @common @restore -Execute -TrustedBackup -BackendStopped
$restored = Invoke-SmartTaskMySql @connection -Executable $mysql -Arguments ($argsSql + @("--database=$targetDatabase")) -Sql 'SELECT body FROM notes WHERE id=1;'
if ($restored.Trim() -cne ($unicodeText + '!')) { throw 'Unicode database round-trip failed.' }
if ((Get-FileHash -LiteralPath (Join-Path $sourceStorage $storedName)).Hash -ne (Get-FileHash -LiteralPath (Join-Path $targetStorage $storedName)).Hash) { throw 'Attachment binary round-trip failed.' }
Invoke-SmartTaskMySql @connection -Executable $mysql -Arguments ($argsSql + @("--database=$targetDatabase")) -Sql "INSERT INTO notes VALUES (2,'trigger',NULL);" | Out-Null
$trigger = Invoke-SmartTaskMySql @connection -Executable $mysql -Arguments ($argsSql + @("--database=$targetDatabase")) -Sql 'SELECT body FROM notes WHERE id=2;'
if ($trigger.Trim() -ne 'trigger!') { throw 'Restored trigger did not execute.' }

function Assert-Refused([scriptblock] $Action, [string] $Expected) {
    try { & $Action | Out-Null } catch { if ($_.Exception.Message -match $Expected) { return }; throw }
    throw "Safety check did not refuse: $Expected"
}
Assert-Refused { & (Join-Path $PSScriptRoot 'Restore-SmartTask.ps1') @common @restore -Execute -TrustedBackup -BackendStopped } 'path already exists'
Assert-Refused { & (Join-Path $PSScriptRoot 'Restore-SmartTask.ps1') @common -BackupPath $snapshot -TargetDatabase $targetDatabase -TargetAttachmentPath (Join-Path $run 'must-not-create') } 'database already exists'
Assert-Refused { & (Join-Path $PSScriptRoot 'Backup-SmartTask.ps1') @common -Database $sourceDatabase -AttachmentPath $sourceStorage -BackupRoot $backupRoot } 'Stop ALL backend'
Assert-Refused { Assert-SmartTaskDatabaseName 'mysql' } 'non-system database'
# Replace the valid checksum after adding unsafe SQL: validation must still refuse it.
[IO.File]::AppendAllText((Join-Path $snapshot 'database.sql'), "`nDROP DATABASE mysql;`n")
$testManifestPath = Join-Path $snapshot 'manifest.json'
$testManifest = Get-Content -LiteralPath $testManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$databaseEntry = $testManifest.files | Where-Object path -eq 'database.sql'
$databaseFile = Get-Item -LiteralPath (Join-Path $snapshot 'database.sql')
$databaseEntry.length = $databaseFile.Length
$databaseEntry.sha256 = (Get-FileHash -LiteralPath $databaseFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText($testManifestPath, ($testManifest | ConvertTo-Json -Depth 5), (New-Object Text.UTF8Encoding($false)))
Assert-Refused { & (Join-Path $PSScriptRoot 'Restore-SmartTask.ps1') @common -BackupPath $snapshot -TargetDatabase ('smarttask_ops_unused_' + $suffix) -TargetAttachmentPath (Join-Path $run 'must-not-create') } 'outside the target database scope'
# Corrupt only this drill's backup after the successful restore, then check fail-closed verification.
[IO.File]::AppendAllText((Join-Path $snapshot 'database.sql'), 'corrupted-test-only')
Assert-Refused { & (Join-Path $PSScriptRoot 'Restore-SmartTask.ps1') @common -BackupPath $snapshot -TargetDatabase ('smarttask_ops_unused_' + $suffix) -TargetAttachmentPath (Join-Path $run 'must-not-create') } 'checksum/length mismatch'
Write-Output 'PASS: Unicode SQL, binary attachments, trigger, dry-run, existing directory/database refusal, offline assertion, system-name guard, out-of-scope SQL rejection and tamper detection.'
Write-Output "RETAINED FOR REVIEW: $run; $sourceDatabase; $targetDatabase. This drill deliberately corrupted its test backup; do not use it for recovery."
