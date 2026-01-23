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
    
    public ServerInfo(String ip, int port, boolean online, String version,
                      int playersOnline, int playersMax, String motd,
                      boolean hasWhitelist, long ping) {
        this.ip = ip;
        this.port = port;
        this.online = online;
        this.version = version;
        this.playersOnline = playersOnline;
        this.playersMax = playersMax;
        this.motd = motd;
        this.hasWhitelist = hasWhitelist;
        this.ping = ping;
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
    public boolean hasWhitelist() { return hasWhitelist; }
    public long getPing() { return ping; }
    
    @Override
    public String toString() {
        if (!online) {
            return ip + ":" + port + " is Offline!";
        }
        String cleanMotd = motd.replaceAll("§[0-9a-fk-or]", "").replaceAll("\n", " ").trim();
        if (cleanMotd.length() > 60) cleanMotd = cleanMotd.substring(0, 57) + "...";
        
        return String.format("%s:%-5d | %-15s | Players: %3d/%-3d | Ping: %4dms | WL: %-3s | %s",
            ip, port, version, playersOnline, playersMax, ping,
            hasWhitelist ? "YES" : "NO", cleanMotd);
    }
}