@echo off
echo ============================================
echo MC Scanner - FAT JAR Build
echo ============================================

echo Cleaning...
rmdir /s /q build-fat 2>nul
mkdir build-fat
mkdir build-fat\temp

echo.
echo Compiling...
javac -cp "lib\json-20231013.jar" -d build-fat src\*.java

if errorlevel 1 (
    echo ERROR: Compilation failed!
    pause
    exit /b 1
)

echo.
echo Extracting libraries...
cd build-fat\temp
jar xf ..\..\lib\json-20231013.jar
del /q META-INF\*.SF META-INF\*.RSA META-INF\*.DSA 2>nul
cd ..\..

echo.
echo Creating FAT JAR...
cd build-fat
xcopy /s /q temp\* .
rmdir /s /q temp
jar cfm MCScanner-fat.jar ..\MANIFEST.MF *.class org\
move MCScanner-fat.jar ..
cd ..

echo.
echo ============================================
echo FAT JAR Created: MCScanner-fat.jar
echo ============================================
echo To run: java -jar MCScanner-fat.jar
pause