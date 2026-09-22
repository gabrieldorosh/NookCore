param([switch]$Offline)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path (Join-Path $PSScriptRoot 'target\test-tmp') | Out-Null
$nookTools = Join-Path $PSScriptRoot '..\tools'
$env:JAVA_HOME = Join-Path $nookTools 'jdk\jdk-25.0.4.1+1'
$nookMaven = Join-Path $nookTools 'maven\apache-maven-3.9.11\bin\mvn.cmd'
$nookArguments = @('-B', '-f', (Join-Path $PSScriptRoot 'pom.xml'), "-Dmaven.repo.local=$nookTools\m2", 'verify')
if ($Offline) { $nookArguments += '-o' }
& $nookMaven @nookArguments
exit $LASTEXITCODE
