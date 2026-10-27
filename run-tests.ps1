# Powershell script for å kompilere og kjøre alle bankoverføringstester
$ErrorActionPreference = "Stop"

$ProjectRoot = $PSScriptRoot
Set-Location $ProjectRoot

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Bygger og tester Bank Transfer (Vranglåsforebygging & Rettferdighet)" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$TargetClasses = Join-Path $ProjectRoot "target/classes"
$TargetTestClasses = Join-Path $ProjectRoot "target/test-classes"
$LibJar = Join-Path $ProjectRoot "lib/junit-platform-console-standalone-1.10.2.jar"

New-Item -ItemType Directory -Force -Path $TargetClasses, $TargetTestClasses | Out-Null

Write-Host "`n1. Kompilerer hovedkode (src/main/java)..." -ForegroundColor Yellow
$MainSources = Get-ChildItem -Path (Join-Path $ProjectRoot "src/main/java") -Filter *.java -Recurse | Select-Object -ExpandProperty FullName
javac -d $TargetClasses $MainSources
Write-Host "   -> Hovedkode kompilert OK." -ForegroundColor Green

Write-Host "`n2. Kompilerer testkode (src/test/java) mot JUnit 5..." -ForegroundColor Yellow
$TestSources = Get-ChildItem -Path (Join-Path $ProjectRoot "src/test/java") -Filter *.java -Recurse | Select-Object -ExpandProperty FullName
javac -cp "$TargetClasses;$LibJar" -d $TargetTestClasses $TestSources
Write-Host "   -> Testkode kompilert OK." -ForegroundColor Green

Write-Host "`n3. Kjører StandaloneTestRunner (15 scenarier inkl. deadlock & fairness)..." -ForegroundColor Yellow
java -cp "$TargetClasses;$TargetTestClasses" no.bank.transfer.StandaloneTestRunner

Write-Host "`n4. Kjører full JUnit 5 Jupiter testsuite..." -ForegroundColor Yellow
java -jar $LibJar --class-path "$TargetClasses;$TargetTestClasses" --scan-class-path --fail-if-no-tests

Write-Host "`n=================================================================" -ForegroundColor Green
Write-Host " ALLE TESTER BESTÅTT SUKSESSFULLT!" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Green
