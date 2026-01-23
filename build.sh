#!/bin/bash

echo "============================================"
echo "MC Scanner - Build Script"
echo "============================================"

# Create directories
mkdir -p build lib

# Check for JSON library
if [ ! -f "lib/json-20231013.jar" ]; then
    echo "Downloading JSON library..."
    wget https://repo1.maven.org/maven2/org/json/json/20231013/json-20231013.jar -P lib/
fi

echo ""
echo "Compiling Java files..."
javac -cp "lib/json-20231013.jar" -d build src/*.java

if [ $? -ne 0 ]; then
    echo "ERROR: Compilation failed!"
    exit 1
fi

echo ""
echo "Creating JAR file..."
cd build
jar cfm MCScanner.jar ../MANIFEST.MF *.class
mv MCScanner.jar ..
cd ..

echo ""
echo "============================================"
echo "Build Complete!"
echo "============================================"
echo "To run: java -jar MCScanner.jar"