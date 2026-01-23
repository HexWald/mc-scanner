@echo off
echo ============================================
echo MC Scanner - Build Script
echo ============================================

echo.
echo Compiling Java files...
javac -cp "lib\json-20231013.jar" -d build src\*.java

if errorlevel 1 (
    echo ERROR: Compilation failed!
    pause
    exit /b 1
)

echo.
echo Creating JAR file...
cd build
jar cfm MCScanner.jar ..\MANIFEST.MF *.class
move MCScanner.jar ..
cd ..

echo.
echo ============================================
echo Build Complete!
echo ============================================
echo To run: java -jar MCScanner.jar
pause
