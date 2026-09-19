import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private final JoinStatus joinStatus;
    private final String kickReason;
    private final ServerPlatform serverPlatform;
    private final Boolean clientModsRequired;
    private final List<DetectedMod> mods;
    private final boolean modListTruncated;
    
    public ServerInfo(String ip, int port, boolean online, String version,
                      int playersOnline, int playersMax, String motd,
                      boolean hasWhitelist, long ping) {
        this(ip, port, online, version, playersOnline, playersMax, motd, hasWhitelist, ping, -1);
    }

    public ServerInfo(String ip, int port, boolean online, String version,
                      int playersOnline, int playersMax, String motd,
                      boolean hasWhitelist, long ping, int protocolVersion) {
        this(ip, port, online, version, playersOnline, playersMax, motd, ping, protocolVersion,
            hasWhitelist ? JoinStatus.WHITELIST : JoinStatus.UNKNOWN, "");
    }

    public ServerInfo(String ip, int port, boolean online, String version,
                      int playersOnline, int playersMax, String motd,
                      long ping, int protocolVersion, JoinStatus joinStatus, String kickReason) {
        this(ip, port, online, version, playersOnline, playersMax, motd, ping, protocolVersion,
            joinStatus, kickReason, ServerPlatform.UNKNOWN, null);
    }

    public ServerInfo(String ip, int port, boolean online, String version,
                      int playersOnline, int playersMax, String motd,
                      long ping, int protocolVersion, JoinStatus joinStatus, String kickReason,
                      ServerPlatform serverPlatform, Boolean clientModsRequired) {
        this(ip, port, online, version, playersOnline, playersMax, motd, ping, protocolVersion,
            joinStatus, kickReason, serverPlatform, clientModsRequired,
            Collections.<DetectedMod>emptyList(), false);
    }

    public ServerInfo(String ip, int port, boolean online, String version,
                      int playersOnline, int playersMax, String motd,
                      long ping, int protocolVersion, JoinStatus joinStatus, String kickReason,
                      ServerPlatform serverPlatform, Boolean clientModsRequired,
                      List<DetectedMod> mods, boolean modListTruncated) {
        this.ip = ip;
        this.port = port;
        this.online = online;
        this.version = version;
        this.playersOnline = playersOnline;
        this.playersMax = playersMax;
        this.motd = motd;
        this.hasWhitelist = joinStatus == JoinStatus.WHITELIST;
        this.ping = ping;
        this.protocolVersion = protocolVersion;
        this.joinStatus = joinStatus != null ? joinStatus : JoinStatus.UNKNOWN;
        this.kickReason = cleanText(kickReason);
        this.serverPlatform = serverPlatform != null ? serverPlatform : ServerPlatform.UNKNOWN;
        this.clientModsRequired = clientModsRequired;
        List<DetectedMod> safeMods = mods != null ? mods : Collections.<DetectedMod>emptyList();
        this.mods = Collections.unmodifiableList(new ArrayList<>(safeMods));
        this.modListTruncated = modListTruncated;
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
    public Boolean getWhitelistResult() {
        if (joinStatus == JoinStatus.WHITELIST) {
            return Boolean.TRUE;
        }
        if (joinStatus == JoinStatus.OPEN) {
            return Boolean.FALSE;
        }
        return null;
    }
    public long getPing() { return ping; }
    public int getProtocolVersion() { return protocolVersion; }
    public JoinStatus getJoinStatus() { return joinStatus; }
    public String getKickReason() { return kickReason; }
    public ServerPlatform getServerPlatform() { return serverPlatform; }
    public Boolean getClientModsRequired() { return clientModsRequired; }
    public String getClientModsText() {
        if (clientModsRequired == null) return "UNKNOWN";
        return clientModsRequired ? "YES" : "NO";
    }
    public List<DetectedMod> getMods() { return mods; }
    public int getModCount() { return mods.size(); }
    public boolean isModListTruncated() { return modListTruncated; }
    public String getModListText() {
        StringBuilder text = new StringBuilder();
        for (DetectedMod mod : mods) {
            if (text.length() > 0) text.append("; ");
            text.append(mod);
        }
        return text.toString();
    }
    public String getCompactModSummary() {
        if (mods.isEmpty()) return "";
        int shown = Math.min(8, mods.size());
        StringBuilder text = new StringBuilder("Mods (")
            .append(mods.size()).append(modListTruncated ? "+" : "").append("): ");
        for (int i = 0; i < shown; i++) {
            if (i > 0) text.append(", ");
            text.append(mods.get(i).getId());
        }
        if (mods.size() > shown) {
            text.append(" (+").append(mods.size() - shown).append(" more)");
        }
        return text.toString();
    }
    
    @Override
    public String toString() {
        if (!online) {
            return ip + ":" + port + " is Offline!";
        }
        String cleanMotd = getDisplayMotd();
        if (cleanMotd.length() > 60) cleanMotd = cleanMotd.substring(0, 57) + "...";
        
        String reason = kickReason.isEmpty() ? "" : " | Reason: " + shorten(kickReason, 100);
        return String.format("%s:%-5d | %-15s | Players: %3d/%-3d | Ping: %4dms | Core: %-16s | Mods: %-7s | Access: %-20s | %s%s",
            ip, port, version, playersOnline, playersMax, ping,
            serverPlatform.getLabel(), getClientModsText(), joinStatus.getLabel(), cleanMotd, reason);
    }

    private static String cleanMotd(String value) {
        return cleanText(value);
    }

    private static String cleanText(String value) {
        String clean = value == null ? "" : value;
        clean = repairCommonMojibake(clean);
        clean = clean.replaceAll("(?i)\\u00A7[0-9A-FK-OR]", "");
        clean = clean.replace('\r', ' ').replace('\n', ' ');
        clean = clean.replaceAll("\\s+", " ").trim();
        return clean;
    }

    private static String shorten(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
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
