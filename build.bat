@echo off
chcp 65001 >nul
echo ============================================
echo MC Scanner - FAT JAR Build
echo ============================================

echo Cleaning...
if exist build rmdir /s /q build
mkdir build
mkdir build\classes

echo Compiling...
javac -encoding UTF-8 -d build\classes -cp "lib\*" src\*.java
if errorlevel 1 (
    echo ERROR: Compilation failed!
    pause
    exit /b 1
)

echo Extracting libraries...
cd build\classes
jar xf ..\..\lib\json-20231013.jar
del /q META-INF\*.SF META-INF\*.RSA META-INF\*.DSA 2>nul
cd ..\..

echo Creating manifest...
echo Main-Class: ScannerGUI > build\MANIFEST.MF
echo. >> build\MANIFEST.MF

echo Building JAR...
jar cfm MCScanner.jar build\MANIFEST.MF -C build\classes .

if exist MCScanner.jar (
    echo.
    echo ============================================
    echo SUCCESS! JAR created: MCScanner.jar
    echo ============================================
    echo.
    echo To run: java -jar MCScanner.jar
) else (
    echo ERROR: JAR creation failed!
    pause
    exit /b 1
)

pause