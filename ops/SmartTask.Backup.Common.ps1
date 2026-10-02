Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-SmartTaskDatabaseName([string] $Name) {
    $reserved = @(
        'information_schema', 'mysql', 'ndbinfo', 'performance_schema', 'sys',
        'mysql_innodb_cluster_metadata', 'mysql_innodb_cluster_metadata_bkp',
        'mysql_innodb_cluster_metadata_previous'
    )
    if ($Name -notmatch '^[a-z][a-z0-9_]{0,63}$' -or $Name -in $reserved) {
        throw 'Use a non-system database name: lowercase letters, digits, underscores, starting with a letter (1-64 characters).'
    }
}

function Get-SmartTaskSafePath([string] $Path) {
    if (-not [IO.Path]::IsPathRooted($Path)) { throw 'Use an absolute filesystem path.' }
    $full = [IO.Path]::GetFullPath($Path).TrimEnd('\', '/')
    if ($full -eq [IO.Path]::GetPathRoot($full).TrimEnd('\', '/')) { throw 'A filesystem root is not an allowed target.' }
    $cursor = $full
    while ($cursor) {
        if (Test-Path -LiteralPath $cursor) {
            $item = Get-Item -LiteralPath $cursor -Force
            if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Links/junctions are not supported: $cursor" }
        }
        $cursor = [IO.Path]::GetDirectoryName($cursor)
    }
    return $full
}

function New-SmartTaskPrivateDirectory([string] $Path) {
    $full = Get-SmartTaskSafePath $Path
    if (Test-Path -LiteralPath $full) { throw "Target already exists: $full" }
    # Apply a user-only ACL before writing credentials or backup contents.
    $acl = New-Object Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true, $false)
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')))
    [IO.Directory]::CreateDirectory($full) | Out-Null
    Set-Acl -LiteralPath $full -AclObject $acl
    return $full
}

function Assert-SmartTaskOffline([switch] $BackendStopped, [string] $BackendHost, [int] $BackendPort) {
    if (-not $BackendStopped) { throw 'Stop ALL backend instances and other writers first, then supply -BackendStopped to confirm.' }
    if ($BackendHost -notin @('127.0.0.1', 'localhost', '::1')) { throw 'Run this offline tool on the local backend host. Remote backend reachability is not a reliable offline check.' }
    $listeners = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    if ($listeners | Where-Object Port -eq $BackendPort) { throw "A local service is listening on port $BackendPort. Stop the backend before continuing." }
}

function Get-SmartTaskCredential([string] $Username, [pscredential] $Credential, [switch] $EmptyPassword) {
    if ($Credential) { return $Credential }
    if ($EmptyPassword) { return New-Object Management.Automation.PSCredential($Username, (New-Object Security.SecureString)) }
    return New-Object Management.Automation.PSCredential($Username, (Read-Host 'MySQL password (not echoed or saved in command history)' -AsSecureString))
}

function ConvertTo-SmartTaskProcessArgument([string] $Value) {
    # Windows CommandLineToArgvW quoting; no shell is invoked.
    return '"' + [regex]::Replace([regex]::Replace($Value, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1') + '"'
}

function Invoke-SmartTaskMySql {
    param(
        [string] $Executable, [string] $DatabaseHost, [int] $DatabasePort,
        [pscredential] $Credential, [string[]] $Arguments,
        [string] $InputFile, [string] $Sql
    )
    $executablePath = Get-SmartTaskSafePath $Executable
    if (-not (Test-Path -LiteralPath $executablePath -PathType Leaf)) { throw 'MySQL executable was not found.' }
    if ($DatabaseHost -notmatch '^[a-zA-Z0-9.:-]+$') { throw 'Invalid MySQL host.' }
    $temporary = New-SmartTaskPrivateDirectory (Join-Path ([IO.Path]::GetTempPath()) ('smarttask-mysql-' + [guid]::NewGuid().ToString('N')))
    $options = Join-Path $temporary 'client.cnf'
    $process = $null
    try {
        $password = $Credential.GetNetworkCredential().Password
        $username = $Credential.UserName
        foreach ($value in @($username, $password)) {
            if ($value.Contains("`0") -or $value.Contains("`r") -or $value.Contains("`n")) { throw 'Newline/NUL credentials are not supported by this backup tool.' }
        }
        $username = $username.Replace('\', '\\').Replace('"', '\"')
        $password = $password.Replace('\', '\\').Replace('"', '\"')
        [IO.File]::WriteAllText($options, "[client]`nuser=`"$username`"`npassword=`"$password`"`nhost=$DatabaseHost`nport=$DatabasePort`nprotocol=TCP`n", (New-Object Text.UTF8Encoding($false)))
        $password = $null
        $start = New-Object Diagnostics.ProcessStartInfo
        $start.FileName = $executablePath
        $allArguments = @("--defaults-file=$options", '--no-login-paths') + $Arguments
        $start.Arguments = (($allArguments | ForEach-Object { ConvertTo-SmartTaskProcessArgument $_ }) -join ' ')
        $start.UseShellExecute = $false
        $start.CreateNoWindow = $true
        $start.RedirectStandardInput = $true
        $start.RedirectStandardOutput = $true
        $start.RedirectStandardError = $true
        $start.StandardOutputEncoding = New-Object Text.UTF8Encoding($false)
        # Do not inherit an unrelated password or login-path override from the caller.
        $start.EnvironmentVariables.Remove('MYSQL_PWD')
        $start.EnvironmentVariables.Remove('MYSQL_TEST_LOGIN_FILE')
        $process = New-Object Diagnostics.Process
        $process.StartInfo = $start
        $process.Start() | Out-Null
        $errorTask = $process.StandardError.ReadToEndAsync()
        $outputTask = $process.StandardOutput.ReadToEndAsync()
        try {
            if ($InputFile) {
                $inputStream = [IO.File]::OpenRead($InputFile)
                try { $inputStream.CopyTo($process.StandardInput.BaseStream) } finally { $inputStream.Dispose() }
            } elseif ($Sql) {
                $bytes = [Text.Encoding]::UTF8.GetBytes($Sql)
                $process.StandardInput.BaseStream.Write($bytes, 0, $bytes.Length)
            }
        } finally {
            $process.StandardInput.Close()
        }
        $process.WaitForExit()
        $output = $outputTask.GetAwaiter().GetResult()
        $null = $errorTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) {
            # Client stderr can contain SQL, credentials or user data. Do not log it.
            throw "MySQL client failed (exit $($process.ExitCode)); check connectivity, permissions, database names and client/server compatibility. Raw client output is suppressed."
        }
        return $output
    } finally {
        if ($process) {
            try {
                if (-not $process.HasExited) {
                    $process.Kill()
                    $process.WaitForExit()
                }
            } catch {
                # Preserve the primary error; this process was created only by this invocation.
            }
            $process.Dispose()
        }
        if (Test-Path -LiteralPath $options) { Remove-Item -LiteralPath $options -Force }
        # Non-recursive: this can only remove the empty, uniquely created directory.
        if (Test-Path -LiteralPath $temporary) { [IO.Directory]::Delete($temporary, $false) }
    }
}

function Get-SmartTaskBackupFiles([string] $Directory) {
    $pending = New-Object 'Collections.Generic.Stack[string]'
    $pending.Push($Directory)
    while ($pending.Count -gt 0) {
        foreach ($item in Get-ChildItem -LiteralPath $pending.Pop() -Force) {
            if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Backup/attachment trees may not contain links or junctions.' }
            if ($item.PSIsContainer) { $pending.Push($item.FullName) } else { Write-Output $item }
        }
    }
}

function Get-SmartTaskFileRecord([string] $Root, [IO.FileInfo] $File) {
    return [ordered]@{
        path = $File.FullName.Substring($Root.Length + 1).Replace('\', '/')
        length = $File.Length
        sha256 = (Get-FileHash -LiteralPath $File.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

function Assert-SmartTaskSqlDumpSafe([string] $Path) {
    # This is defense in depth for a trusted mysqldump, not a general SQL parser.
    # Block client/file/server/database commands that a SmartTask table dump never needs.
    $patterns = @(
        '^\s*(?:/\*![0-9]{5}\s*)?(?:USE\b|(?:CREATE|DROP|ALTER)\s+(?:DATABASE|SCHEMA)\b)',
        '^\s*(?:SOURCE\b|\\\.)',
        '\b(?:LOAD\s+DATA(?:\s+LOCAL)?\s+INFILE|INTO\s+(?:OUTFILE|DUMPFILE))\b',
        '^\s*(?:/\*![0-9]{5}\s*)?(?:GRANT\b|REVOKE\b|(?:CREATE|ALTER|DROP)\s+USER\b|SET\s+(?:GLOBAL|PERSIST)\b|(?:INSTALL|UNINSTALL)\s+PLUGIN\b|SHUTDOWN\b|RESET\s+MASTER\b)'
    )
    $reader = New-Object IO.StreamReader($Path, (New-Object Text.UTF8Encoding($false)), $true)
    try {
        while (($line = $reader.ReadLine()) -ne $null) {
            foreach ($pattern in $patterns) {
                if ($line -match $pattern) {
                    throw 'SQL dump contains a command outside the target database scope. Restore was refused.'
                }
            }
        }
    } finally {
        $reader.Dispose()
    }
}
