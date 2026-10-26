param([switch]$CheckOnly)

# Windows PowerShell 5.1 compatible; ASCII messages work on any console code page.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Set-Location -LiteralPath $PSScriptRoot

function Import-DotEnv {
    $path = Join-Path $PSScriptRoot '.env'
    if (!(Test-Path -LiteralPath $path)) { return }
    foreach ($line in [IO.File]::ReadAllLines($path)) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
        if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') { throw 'Invalid .env line. Use NAME=value.' }
        $name = $Matches[1]
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        # Treat values as literal text, never as shell commands.
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}

function Get-Setting([string]$Name, [string]$Default = '') {
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) { return $Default }
    return $value
}

function Invoke-Database([string]$Sql) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $script:mysql
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = New-Object System.Text.UTF8Encoding($false)
    $info.Arguments = "--no-defaults --protocol=TCP --host=$script:dbHost --port=$script:dbPort --user=$script:dbUser --default-character-set=utf8mb4 --batch --skip-column-names --connect-timeout=10"
    # Password is passed only to the child, not printed or placed on its command line.
    $info.EnvironmentVariables['MYSQL_PWD'] = $env:DB_PASSWORD
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $info
    try {
        [void]$process.Start()
        $output = $process.StandardOutput.ReadToEndAsync()
        $errors = $process.StandardError.ReadToEndAsync()
        # .NET Framework (Windows PowerShell 5.1) has no StandardInputEncoding.
        # Write UTF-8 bytes directly so non-ASCII SQL is preserved.
        $utf8 = New-Object System.Text.UTF8Encoding($false)
        $sqlBytes = $utf8.GetBytes($Sql + "`n")
        $inputStream = $process.StandardInput.BaseStream
        $inputStream.Write($sqlBytes, 0, $sqlBytes.Length)
        $inputStream.Flush()
        $inputStream.Close()
        $process.WaitForExit()
        $result = $output.GetAwaiter().GetResult()
        $errorText = $errors.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw "MySQL failed: $errorText" }
        return $result.Trim()
    } finally { $process.Dispose() }
}

function Assert-Schema {
    $expected = @{
        chat_call_log = 'id request_id model_alias channel stream prompt_tokens completion_tokens total_tokens status error_code duration_ms created_at user_id api_key_id cached_input_tokens price_version input_units_per_million cached_input_units_per_million output_units_per_million cost_units cost_calculation_status reserved_units billing_status updated_at release_reason'
        router_user = 'id username status created_at updated_at'
        router_api_key = 'id user_id key_name key_hash key_prefix status created_at updated_at'
        router_wallet = 'user_id balance_units frozen_units created_at updated_at'
        router_wallet_transaction = 'id user_id business_id transaction_type balance_change_units frozen_change_units balance_after_units frozen_after_units created_at'
        router_session_binding = 'id api_key_id model_alias session_id channel expires_at created_at updated_at'
        router_prefix_history = 'id api_key_id model_alias prefix_hash channel last_success_at expires_at'
        router_explicit_cache = 'id cache_id user_id api_key_id model_alias channel upstream_model upstream_response_id status expires_at created_at updated_at'
    }
    $actual = (Invoke-Database "SELECT CONCAT(table_name, '.', column_name) FROM information_schema.columns WHERE table_schema='nothing_router';") -split '\r?\n'
    $missing = @()
    foreach ($table in $expected.Keys) {
        foreach ($column in $expected[$table].Split(' ')) {
            if ($actual -notcontains "$table.$column") { $missing += "$table.$column" }
        }
    }
    if ($missing.Count -gt 0) {
        throw "Existing schema is incomplete: $($missing -join ', '). Apply missing sql/001..016 migrations after checking the schema. No existing tables were changed."
    }
    $nullable = Invoke-Database "SELECT is_nullable FROM information_schema.columns WHERE table_schema='nothing_router' AND table_name='chat_call_log' AND column_name='duration_ms';"
    if ($nullable -ne 'YES') { throw 'Apply sql/015_allow_pending_call_duration.sql before starting.' }
}

try {
    Write-Host '[1/4] Loading configuration and checking prerequisites ...'
    Import-DotEnv
    foreach ($name in @('DB_PASSWORD', 'ROUTER_ADMIN_KEY', 'DEEPSEEK_API_KEY', 'ARK_API_KEY')) {
        $value = Get-Setting $name
        if (!$value -or $value -match '^(change-me|your[-_]|replace[-_]|<)') {
            throw "Set $name in .env or environment variables. See .env.example and STARTUP.md."
        }
    }
    $script:dbUser = Get-Setting 'DB_USERNAME' 'root'
    if ($dbUser -notmatch '^[A-Za-z0-9_.-]+$') { throw 'DB_USERNAME must contain only letters, numbers, _, . or -.' }
    $env:DB_USERNAME = $dbUser
    $dbUrl = Get-Setting 'DB_URL' 'jdbc:mysql://localhost:3306/nothing_router?characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true'
    # Existing SQL scripts explicitly target nothing_router. Do not initialize a different database.
    if ($dbUrl -notmatch '^jdbc:mysql://([A-Za-z0-9.-]+)(?::([0-9]+))?/nothing_router(?:\?[^\r\n]*)?$') {
        throw 'DB_URL must use jdbc:mysql://HOST:PORT/nothing_router (optional query parameters).'
    }
    $script:dbHost = $Matches[1]
    $script:dbPort = if ($Matches[2]) { [int]$Matches[2] } else { 3306 }
    if ($dbPort -lt 1 -or $dbPort -gt 65535) { throw 'Invalid MySQL port.' }
    $env:DB_URL = $dbUrl
    if ($env:JAVA_HOME) {
        if (!(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) { throw 'JAVA_HOME must point to a JDK.' }
        $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
    }
    $java = (Get-Command java.exe -ErrorAction Stop).Source
    $javac = (Get-Command javac.exe -ErrorAction Stop).Source
    $compilerVersion = & $javac -version 2>&1
    if ($LASTEXITCODE -ne 0 -or "$compilerVersion" -notmatch 'javac (\d+)' -or [int]$Matches[1] -lt 17) { throw 'JDK 17 or newer is required.' }
    $script:mysql = (Get-Command mysql.exe -ErrorAction Stop).Source
    if (!(Test-Path -LiteralPath 'mvnw.cmd')) { throw 'Missing Maven wrapper: mvnw.cmd.' }
    if ($CheckOnly) {
        Write-Host '[OK] Configuration and tools checked. No build, database connection or application startup performed.'
        exit 0
    }

    Write-Host '[2/4] Building application (tests are skipped) ...'
    # Always package; Maven handles incremental compilation, including pom/resources changes.
    & (Join-Path $PSScriptRoot 'mvnw.cmd') '-DskipTests' 'package'
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed. Application was not started.' }
    [xml]$pom = Get-Content -LiteralPath 'pom.xml' -Raw
    $jar = Join-Path $PSScriptRoot ("target/{0}-{1}.jar" -f $pom.project.artifactId, $pom.project.version)
    if (!(Test-Path -LiteralPath $jar)) { throw 'Expected application JAR was not produced.' }

    Write-Host '[3/4] Checking database ...'
    $count = [int](Invoke-Database "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='nothing_router';")
    if ($count -eq 0) {
        [void](Invoke-Database 'CREATE DATABASE IF NOT EXISTS nothing_router DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;')
        $migrations = @(Get-ChildItem -LiteralPath 'sql' -File | Where-Object { $_.Name -match '^\d{3}_.*\.sql$' } | Sort-Object Name)
        if ($migrations.Count -ne 16) { throw 'Expected SQL migrations 001..016. Check the sql directory.' }
        foreach ($file in $migrations) {
            Write-Host "      Applying $($file.Name)"
            try {
                # Execute separately; also terminate migration 007, which lacks a trailing semicolon.
                [void](Invoke-Database ([IO.File]::ReadAllText($file.FullName) + "`n;`n"))
            } catch {
                throw "Migration $($file.Name) failed. MySQL DDL may already be committed; inspect the schema before retrying. $($_.Exception.Message)"
            }
        }
    }
    Assert-Schema

    Write-Host '[4/4] Starting application. Use Ctrl+C to stop.'
    & $java '-jar' $jar
    if ($LASTEXITCODE -ne 0) { throw "Application exited with code $LASTEXITCODE. See application logs above." }
    exit 0
} catch {
    Write-Host ("[FAIL] " + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
