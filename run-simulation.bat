@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

set ACCOUNTS_CSV=%1
if "%ACCOUNTS_CSV%"=="" set ACCOUNTS_CSV=data\accounts_100.csv

set TX_CSV=%2
if "%TX_CSV%"=="" set TX_CSV=data\transactions_adversarial_10k.csv

set THREADS=%3
if "%THREADS%"=="" set THREADS=16

echo =================================================================
echo  Starter Banksimulering med CSV-data (Windows CMD)
echo =================================================================
echo  Konto-fil        : %ACCOUNTS_CSV%
echo  Transaksjons-fil : %TX_CSV%
echo  Antall traader   : %THREADS%
echo.

if not exist target\classes (
    mkdir target\classes
)

if not exist target\classes\no\bank\transfer\simulation\CsvSimulationRunner.class (
    echo Kompilerer Java-koden...
    dir /s /b src\main\java\*.java > sources_main.txt
    javac -d target\classes @sources_main.txt
    del sources_main.txt
)

if not exist %ACCOUNTS_CSV% (
    echo Genererer standard CSV-datasett...
    java -cp target\classes no.bank.transfer.simulation.generator.CsvDataGenerator
)

echo Kjoerer simulering...
java -cp target\classes no.bank.transfer.simulation.CsvSimulationRunner "%ACCOUNTS_CSV%" "%TX_CSV%" %THREADS%
