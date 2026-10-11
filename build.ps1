param([switch]$Offline)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path (Join-Path $PSScriptRoot 'target\test-tmp') | Out-Null
$nookTools = Join-Path $PSScriptRoot '..\tools'
$env:JAVA_HOME = Join-Path $nookTools 'jdk\jdk-25.0.4.1+1'
$nookMaven = Join-Path $nookTools 'maven\apache-maven-3.9.11\bin\mvn.cmd'
$nookArguments = @('-B', '-f', (Join-Path $PSScriptRoot 'pom.xml'), "-Dmaven.repo.local=$nookTools\m2", 'verify')
# Embed the source checkout and a deterministic hash of runtime build inputs.
$nookCommit = & git -c "safe.directory=$PSScriptRoot" -C $PSScriptRoot rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'Cannot identify the source checkout.' }
$nookChanges = & git -c "safe.directory=$PSScriptRoot" -C $PSScriptRoot status --porcelain
if ($LASTEXITCODE -ne 0) { throw 'Cannot read source checkout status.' }
$nookState = if ($nookChanges) { 'dirty' } else { 'clean' }
$nookInputFiles = @(Get-ChildItem (Join-Path $PSScriptRoot 'src/main') -File -Recurse) + @(Get-Item (Join-Path $PSScriptRoot 'pom.xml'), (Join-Path $PSScriptRoot 'build.ps1'))
$nookInputLines = $nookInputFiles | ForEach-Object { $_.FullName.Substring($PSScriptRoot.Length + 1).Replace('\','/') + ':' + (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash } | Sort-Object
$nookHasher = [System.Security.Cryptography.SHA256]::Create()
try { $nookInputHash = ([BitConverter]::ToString($nookHasher.ComputeHash([Text.Encoding]::UTF8.GetBytes(($nookInputLines -join "`n"))))).Replace('-','').ToLowerInvariant() } finally { $nookHasher.Dispose() }
$nookBuilt = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
$nookArguments += @("-Dnook.commit=$nookCommit", "-Dnook.state=$nookState", "-Dnook.inputs=$nookInputHash", "-Dnook.built=$nookBuilt")
if ($Offline) { $nookArguments += '-o' }
& $nookMaven @nookArguments
exit $LASTEXITCODE
