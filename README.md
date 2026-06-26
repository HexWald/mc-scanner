# 🎮 MC Scanner - Minecraft Server Scanner

[![License: MIT]](https://opensource.org/licenses/MIT)
[![Java]](https://www.java.com/)
[![Release]](https://github.com/HexWald/mc-scanner/releases)

**Professional Minecraft Server Scanner with WhiteList Detection**

A powerful, multi-threaded Java application for scanning and analyzing Minecraft servers. Features a modern GUI, real-time progress tracking, and automatic WhiteList detection.

---

## ✨ Features

- 🚀 **Multi-threaded Scanning** - Fast parallel port scanning
- 🔍 **WhiteList Detection** - Automatically detects if servers have whitelist enabled
- 📊 **Detailed Statistics** - Server version, player count, ping, MOTD
- 🎨 **Modern GUI** - Clean, intuitive interface with real-time updates
- 💾 **Export Results** - Save scan results to formatted text files
- ⚡ **Multiple Speed Modes** - From safe to aggressive scanning
- 🎯 **Smart Filtering** - Show only online servers option
- 🌟 **New function!** - Scan multi IP

---

## 🚀 Quick Start

### Prerequisites

- Java 8 or higher
- Internet connection

### Download

**Option 1: Download Pre-built JAR**
```bash
# Download from Releases page
# https://github.com/HexWald/mc-scanner/releases/latest

java -jar MCScanner.jar
```

**Option 2: Build from Source**
```bash
git clone https://github.com/HexWald/mc-scanner.git
cd mc-scanner
./build.bat    # Windows
./build.sh     # Linux/Mac
java -jar MCScanner.jar
```

---

## 🛠️ Building from Source

### Requirements

- JDK 8 or higher
- org.json library (included in `lib/`)

### Build Steps

**Windows:**
```batch
git clone https://github.com/HexWald/mc-scanner.git
cd mc-scanner
build.bat
```

**Linux/Mac:**
```bash
git clone https://github.com/HexWald/mc-scanner.git
cd mc-scanner
chmod +x build.sh
./build.sh
```

---

## 📖 Usage Guide

### Basic Scanning

1. **Enter Server IP** - Domain or IP address (e.g., `play.hypixel.net`)
2. **Set Start Port** - First port to scan (default: `25565`)
3. **Choose Amount** - Number of ports to scan (1-10000)
4. **Select Speed:**
   - `MEDIUM` - Safe, 20 threads (500ms delay)
   - `FAST` - Recommended, 50 threads (125ms delay)
   - `VERY_FAST` - Aggressive, 100 threads (50ms delay)
   - `DANGEROUS` - Maximum, 200 threads (10ms delay)
5. **Click "Start Scan"**

---

## 🔍 WhiteList Detection

The scanner automatically detects if a server has whitelist enabled by:

1. Attempting a login handshake with a fake username
2. Analyzing the disconnect message
3. Detecting keywords: "whitelist", "not whitelisted", "not allowed"

**Accuracy:** ~95% (depends on server configuration)

---

## ⚠️ Important Warnings

### Legal & Ethical Use

- ✅ **DO**: Scan your own servers
- ✅ **DO**: Test with permission from server owners
- ✅ **DO**: Use reasonable scan amounts (1-500 ports)
- ❌ **DON'T**: Scan without authorization
- ❌ **DON'T**: Use for malicious purposes
- ❌ **DON'T**: Scan entire port ranges (1-65535)

### Rate Limiting

Aggressive scanning may result in:
- IP address blocking by firewalls
- Temporary bans from servers
- ISP throttling or warnings

**Recommendation:** Use `FAST` or `MEDIUM` mode for public servers.

---

## 🐛 Known Issues

- WhiteList detection may fail on heavily customized servers
- High ping servers (>1000ms) may timeout
- Some Bedrock Edition servers may not respond correctly
- Large scans (5000+ ports) may consume significant memory

---

## 🔧 Troubleshooting

### "Command not found: javac"
**Solution:** Install JDK (not just JRE)
```bash
java -version
javac -version
```

### "package org.json does not exist"
**Solution:** Ensure `lib/json-20231013.jar` exists

### Program hangs during scan
**Solution:** Click "Cancel Scan" button or restart application

---

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📝 Changelog

### v2.0.3 (2026-01-26) True parallel multi-IP scanning, unified results output
- 🐛 True parallel multi-IP scanning, unified results output!

### v2.0.2 (2026-01-26) Multi-IP scanning, improved UI, instant cancel

- ✨ Added multi-IP and IP range scanning support (m28-m15, server1-server10)
- ✨ Increased font size in IP input field (14pt)
- 🐛 Added mouse wheel scrolling in IP text area
- 🐛 Improved results grouping by IP in output file
- 🐛 Instant scan cancellation (500ms vs 2-3s)
- 🐛 Better IP range parser: supports numbers anywhere in string

### v2.0.1 (2026-01-24)
 What has been fixed:

- 🐛 UUID in the Login Start package - added for versions 1.19+ (protocol 759+)
- 🐛 Smart protocol definition - from the server version string
- 🐛 Protocol priority is detectable first, then popular
- 🐛 Whitelist extended dictionary - added Russian variants of phrases
- 🐛 Improved diagnostics - detailed logs for debugging

### v2.0.0 (2026-01-23)
- ✨ Complete rewrite with clean architecture
- ✨ Added WhiteList detection
- ✨ Modern GUI with real-time updates
- ✨ Multi-threaded scanning engine
- ✨ Detailed statistics and reporting
- 🐛 Fixed race conditions
- 🐛 Fixed resource leaks
- 🐛 Fixed CountDownLatch bug

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE] file for details.

---

## 🙏 Acknowledgments

- **Original Inspiration:** Community need for reliable Minecraft server scanning
- **Libraries Used:**
  - [org.json](https://github.com/stleary/JSON-java) - JSON parsing
  - Java Swing - GUI framework
- **Testing:** Community contributors

---

**Made with ❤️ by the Minecraft community**
