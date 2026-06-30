import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class ServerInfo {
    private final String ip;
    private final int port;
    private final boolean online;
    private final String version;
    private final int playersOnline;
    private final int playersMax;
    private final String motd;
    private final boolean hasWhitelist;
    private final long ping;
    private final int protocolVersion;
    private final String screenshotPath;
    
    public ServerInfo(String ip, int port, boolean online, String version,
                      int playersOnline, int playersMax, String motd,
                      boolean hasWhitelist, long ping) {
        this(ip, port, online, version, playersOnline, playersMax, motd, hasWhitelist, ping, -1, "");
    }

    public ServerInfo(String ip, int port, boolean online, String version,
                      int playersOnline, int playersMax, String motd,
                      boolean hasWhitelist, long ping, int protocolVersion) {
        this(ip, port, online, version, playersOnline, playersMax, motd, hasWhitelist, ping, protocolVersion, "");
    }

    public ServerInfo(String ip, int port, boolean online, String version,
                      int playersOnline, int playersMax, String motd,
                      boolean hasWhitelist, long ping, String screenshotPath) {
        this(ip, port, online, version, playersOnline, playersMax, motd, hasWhitelist, ping, -1, screenshotPath);
    }

    public ServerInfo(String ip, int port, boolean online, String version,
                      int playersOnline, int playersMax, String motd,
                      boolean hasWhitelist, long ping, int protocolVersion, String screenshotPath) {
        this.ip = ip;
        this.port = port;
        this.online = online;
        this.version = version;
        this.playersOnline = playersOnline;
        this.playersMax = playersMax;
        this.motd = motd;
        this.hasWhitelist = hasWhitelist;
        this.ping = ping;
        this.protocolVersion = protocolVersion;
        this.screenshotPath = screenshotPath != null ? screenshotPath : "";
    }
    
    public ServerInfo(String ip, int port) {
        this(ip, port, false, "", 0, 0, "", false, -1);
    }
    
    public String getIp() { return ip; }
    public int getPort() { return port; }
    public boolean isOnline() { return online; }
    public String getVersion() { return version; }
    public int getPlayersOnline() { return playersOnline; }
    public int getPlayersMax() { return playersMax; }
    public String getMotd() { return motd; }
    public String getDisplayMotd() { return cleanMotd(motd); }
    public boolean hasWhitelist() { return hasWhitelist; }
    public long getPing() { return ping; }
    public int getProtocolVersion() { return protocolVersion; }
    public String getScreenshotPath() { return screenshotPath; }

    public ServerInfo withScreenshotPath(String screenshotPath) {
        return new ServerInfo(ip, port, online, version, playersOnline, playersMax,
            motd, hasWhitelist, ping, protocolVersion, screenshotPath);
    }
    
    @Override
    public String toString() {
        if (!online) {
            return ip + ":" + port + " is Offline!";
        }
        String cleanMotd = getDisplayMotd();
        if (cleanMotd.length() > 60) cleanMotd = cleanMotd.substring(0, 57) + "...";
        
        String screenshotInfo = screenshotPath.isEmpty() ? "" : " | Screenshot: " + screenshotPath;

        return String.format("%s:%-5d | %-15s | Players: %3d/%-3d | Ping: %4dms | WL: %-3s | %s%s",
            ip, port, version, playersOnline, playersMax, ping,
            hasWhitelist ? "YES" : "NO", cleanMotd, screenshotInfo);
    }

    private static String cleanMotd(String value) {
        String clean = value == null ? "" : value;
        clean = repairCommonMojibake(clean);
        clean = clean.replaceAll("(?i)\\u00A7[0-9A-FK-OR]", "");
        clean = clean.replace('\r', ' ').replace('\n', ' ');
        clean = clean.replaceAll("\\s+", " ").trim();
        return clean;
    }

    private static String repairCommonMojibake(String value) {
        String best = value;
        int bestScore = readabilityScore(best);

        String[] candidates = {
            recode(value, StandardCharsets.ISO_8859_1),
            recode(value, Charset.forName("windows-1251"))
        };

        for (String candidate : candidates) {
            int score = readabilityScore(candidate);
            if (score > bestScore + 3) {
                best = candidate;
                bestScore = score;
            }
        }

        return best;
    }

    private static String recode(String value, Charset sourceCharset) {
        try {
            return new String(value.getBytes(sourceCharset), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private static int readabilityScore(String value) {
        int score = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '\u0400' && c <= '\u04FF') {
                score += 2;
            }
            if (c == '\uFFFD') {
                score -= 10;
            }
            if (c == 'Ð' || c == 'Ñ') {
                score -= 6;
            }
        }

        String[] mojibakeMarkers = {
            "Рџ", "Рђ", "Р‘", "Р’", "Р“", "Рґ", "Рµ", "Рё", "Рѕ", "Р°",
            "РЅ", "Р»", "Рє", "Рј", "СЂ", "СЃ", "С‚", "СЊ", "С‹", "СЏ",
            "С‡", "С€", "С‰", "С†"
        };
        for (String marker : mojibakeMarkers) {
            if (value.contains(marker)) {
                score -= 8;
            }
        }
        return score;
    }
}
