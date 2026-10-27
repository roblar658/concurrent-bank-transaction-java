# Powershell-script for å kjøre parallelle simuleringer med predefinert CSV-data
param (
    [string]$AccountsCsv = "data/accounts_100.csv",
    [string]$TransactionsCsv = "data/transactions_adversarial_10k.csv",
    [int]$Threads = 16
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
Set-Location $ProjectRoot

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Starter Banksimulering med CSV-data" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  Konto-fil        : $AccountsCsv"
Write-Host "  Transaksjons-fil : $TransactionsCsv"
Write-Host "  Antall tråder    : $Threads"

$TargetClasses = Join-Path $ProjectRoot "target/classes"
$LibJar = Join-Path $ProjectRoot "lib/junit-platform-console-standalone-1.10.2.jar"

# Kompiler om nødvendig
if (-not (Test-Path "$TargetClasses/no/bank/transfer/simulation/CsvSimulationRunner.class")) {
    Write-Host "`nKompilerer prosjektet først..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Force -Path $TargetClasses | Out-Null
    $Sources = Get-ChildItem -Path (Join-Path $ProjectRoot "src/main/java") -Filter *.java -Recurse | Select-Object -ExpandProperty FullName
    javac -d $TargetClasses $Sources
}

# Sjekk om CSV finnes, hvis ikke generer
if (-not (Test-Path (Join-Path $ProjectRoot $AccountsCsv)) -or -not (Test-Path (Join-Path $ProjectRoot $TransactionsCsv))) {
    Write-Host "`nGenererer CSV-datasett..." -ForegroundColor Yellow
    java -cp $TargetClasses no.bank.transfer.simulation.generator.CsvDataGenerator
}

Write-Host "`nKjører CsvSimulationRunner..." -ForegroundColor Green
java -cp $TargetClasses no.bank.transfer.simulation.CsvSimulationRunner $AccountsCsv $TransactionsCsv $Threads
