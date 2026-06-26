#!/bin/bash
set -e

JSON_JAR="lib/json-20231013.jar"
JSON_URL="https://repo1.maven.org/maven2/org/json/json/20231013/json-20231013.jar"

echo "============================================"
echo "MC Scanner - FAT JAR Build"
echo "============================================"

echo "Cleaning..."
rm -rf build
mkdir -p build/classes lib

if [ ! -f "$JSON_JAR" ]; then
    echo "Downloading JSON library..."
    if command -v curl >/dev/null 2>&1; then
        curl -L "$JSON_URL" -o "$JSON_JAR"
    elif command -v wget >/dev/null 2>&1; then
        wget "$JSON_URL" -O "$JSON_JAR"
    else
        echo "ERROR: curl or wget is required to download $JSON_JAR"
        exit 1
    fi
fi

echo ""
echo "Compiling Java files..."
javac -encoding UTF-8 -source 8 -target 8 -cp "lib/*" -d build/classes src/*.java

echo ""
echo "Extracting libraries..."
(
    cd build/classes
    jar xf "../../$JSON_JAR"
    rm -f META-INF/*.SF META-INF/*.RSA META-INF/*.DSA
)

echo ""
echo "Creating manifest..."
printf "Main-Class: ScannerGUI\n\n" > build/MANIFEST.MF

echo ""
echo "Building JAR..."
jar cfm MCScanner.jar build/MANIFEST.MF -C build/classes .

echo ""
echo "============================================"
echo "SUCCESS! JAR created: MCScanner.jar"
echo "============================================"
echo "To run: java -jar MCScanner.jar"
