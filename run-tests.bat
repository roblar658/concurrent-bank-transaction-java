@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"
echo =================================================================
echo  Kompilerer og tester Bank Transfer i Java (Windows CMD)
echo =================================================================

if not exist target\classes mkdir target\classes
if not exist target\test-classes mkdir target\test-classes

echo.
echo 1. Kompilerer hovedkode...
dir /s /b src\main\java\*.java > sources_main.txt
javac -d target\classes @sources_main.txt
del sources_main.txt
if %errorlevel% neq 0 (
    echo Feil ved kompilering av hovedkode!
    exit /b %errorlevel%
)

echo.
echo 2. Kompilerer testkode...
dir /s /b src\test\java\*.java > sources_test.txt
javac -cp "target\classes;lib\junit-platform-console-standalone-1.10.2.jar" -d target\test-classes @sources_test.txt
del sources_test.txt
if %errorlevel% neq 0 (
    echo Feil ved kompilering av testkode!
    exit /b %errorlevel%
)

echo.
echo 3. Kjoerer StandaloneTestRunner...
java -cp "target\classes;target\test-classes" no.bank.transfer.StandaloneTestRunner
if %errorlevel% neq 0 (
    echo Noen tester feilet!
    exit /b %errorlevel%
)

echo.
echo 4. Kjoerer JUnit 5 Console Runner...
java -jar lib\junit-platform-console-standalone-1.10.2.jar --class-path "target\classes;target\test-classes" --scan-class-path --fail-if-no-tests
if %errorlevel% neq 0 (
    echo JUnit tester feilet!
    exit /b %errorlevel%
)

echo.
echo =================================================================
echo  ALLE TESTER BESTAATT SUKSESSFULLT!
echo =================================================================
