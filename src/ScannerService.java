import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

public class ScannerService {
    private final List<String> targetIPs;
    private final int startPort;
    private final int limit;
    private final ScanSpeed scanSpeed;
    private final String checkUsername;
    private final ExecutorService executor;
    private final ConcurrentLinkedQueue<ServerInfo> results;
    private final AtomicInteger scannedCount;
    private final AtomicInteger onlineCount;
    private final AtomicInteger whitelistCount;
    private volatile boolean cancelled;
    
    public enum ScanSpeed {
        MEDIUM(500, 20),
        FAST(125, 50),
        VERY_FAST(50, 100),
        DANGEROUS(10, 200);
        
        private final long delayMs;
        private final int threadPoolSize;
        
        ScanSpeed(long delayMs, int threadPoolSize) {
            this.delayMs = delayMs;
            this.threadPoolSize = threadPoolSize;
        }
        
        public long getDelayMs() { return delayMs; }
        public int getThreadPoolSize() { return threadPoolSize; }
    }
    
    public ScannerService(List<String> targetIPs, int startPort, int limit, ScanSpeed scanSpeed) {
        this(targetIPs, startPort, limit, scanSpeed, "MCScanner");
    }

    public ScannerService(List<String> targetIPs, int startPort, int limit, ScanSpeed scanSpeed, String checkUsername) {
        if (targetIPs == null || targetIPs.isEmpty()) {
            throw new IllegalArgumentException("At least one target IP is required");
        }
        if (startPort < 1 || startPort > 65535) {
            throw new IllegalArgumentException("Start port must be between 1 and 65535");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Scan amount must be greater than 0");
        }
        if ((long) startPort + limit - 1 > 65535) {
            throw new IllegalArgumentException("Port range must end at 65535 or lower");
        }
        if (scanSpeed == null) {
            throw new IllegalArgumentException("Scan speed is required");
        }
        if (checkUsername == null || !checkUsername.matches("[A-Za-z0-9_]{3,16}")) {
            throw new IllegalArgumentException("Check nickname must be 3-16 characters: A-Z, 0-9 or _");
        }
        this.targetIPs = targetIPs;
        this.startPort = startPort;
        this.limit = limit;
        this.scanSpeed = scanSpeed;
        this.checkUsername = checkUsername;
        
        // Increase thread pool for multiple IPs
        int threadPoolSize = scanSpeed.threadPoolSize * Math.min(targetIPs.size(), 4);
        this.executor = Executors.newFixedThreadPool(threadPoolSize);
        
        this.results = new ConcurrentLinkedQueue<>();
        this.scannedCount = new AtomicInteger(0);
        this.onlineCount = new AtomicInteger(0);
        this.whitelistCount = new AtomicInteger(0);
        this.cancelled = false;
    }
    
    public void scan(Consumer<ScanProgress> progressCallback) throws InterruptedException {
        int totalScans = targetIPs.size() * limit;
        CountDownLatch latch = new CountDownLatch(totalScans);
        long startTime = System.currentTimeMillis();
        
        // Scan all IPs in parallel, not sequentially!
        for (String ip : targetIPs) {
            if (cancelled) break;
            
            // Each IP gets scanned in parallel
            for (int i = 0; i < limit; i++) {
                if (cancelled) break;
                
                final int port = startPort + i;
                final String targetIP = ip;
                
                executor.submit(() -> {
                    try {
                        if (cancelled) {
                            return;
                        }
                        
                        ServerInfo info = MinecraftProtocol.queryServer(targetIP, port, checkUsername);
                        
                        if (cancelled) {
                            return;
                        }
                        
                        if (info.isOnline()) {
                            results.add(info);
                            onlineCount.incrementAndGet();
                            if (info.hasWhitelist()) {
                                whitelistCount.incrementAndGet();
                            }
                        }
                        
                        int scanned = scannedCount.incrementAndGet();
                        if (progressCallback != null && !cancelled) {
                            progressCallback.accept(new ScanProgress(scanned, totalScans, info, 
                                onlineCount.get(), whitelistCount.get()));
                        }
                        
                    } catch (Exception e) {
                        if (!cancelled) {
                            System.err.println("Error: " + targetIP + ":" + port + " - " + e.getMessage());
                        }
                    } finally {
                        applyWorkerDelay();
                        latch.countDown();
                    }
                });
            }
        }
        
        if (!cancelled) {
            latch.await();
        } else {
            latch.await(500, TimeUnit.MILLISECONDS);
        }
        
        executor.shutdownNow();
        executor.awaitTermination(1, TimeUnit.SECONDS);
        
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("Scan completed in " + totalTime + "ms");
    }

    private void applyWorkerDelay() {
        if (cancelled || scanSpeed.getDelayMs() <= 0) {
            return;
        }

        try {
            Thread.sleep(scanSpeed.getDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public void cancel() {
        cancelled = true;
        executor.shutdownNow();
    }

    public List<ServerInfo> getResultsSnapshot() {
        List<ServerInfo> snapshot = new ArrayList<>(results);
        snapshot.sort(Comparator
            .comparing(ServerInfo::getIp)
            .thenComparingInt(ServerInfo::getPort));
        return snapshot;
    }
    
    public void saveResults(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            parent.mkdirs();
        }

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)), true)) {
            writer.write('\ufeff');
            
            writer.println(repeat("=", 100));
            writer.println("                    MINECRAFT SERVER SCANNER - DETAILED RESULTS");
            writer.println(repeat("=", 100));
            writer.println("Scan Date:    " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            writer.println("Port Range:   " + startPort + " - " + (startPort + limit - 1));
            writer.println("Scan Speed:   " + scanSpeed);
            writer.println("Check Nick:   " + checkUsername);
            writer.println(repeat("=", 100));
            writer.println();
            
            List<ServerInfo> sortedResults = getResultsSnapshot();
            
            // Categorize ALL servers together (not by IP)
            List<ServerInfo> onlineWithPlayers = new ArrayList<>();
            List<ServerInfo> whitelistWithPlayers = new ArrayList<>();
            List<ServerInfo> whitelistNoPlayers = new ArrayList<>();
            List<ServerInfo> onlineNoPlayers = new ArrayList<>();
            
            for (ServerInfo info : sortedResults) {
                if (info.hasWhitelist()) {
                    if (info.getPlayersOnline() > 0) {
                        whitelistWithPlayers.add(info);
                    } else {
                        whitelistNoPlayers.add(info);
                    }
                } else {
                    if (info.getPlayersOnline() > 0) {
                        onlineWithPlayers.add(info);
                    } else {
                        onlineNoPlayers.add(info);
                    }
                }
            }
            
            writer.println(repeat("-", 100));
            writer.println("SCAN RESULTS");
            writer.println(repeat("-", 100));
            writer.println();
            
            // Output categorized results
            if (!onlineWithPlayers.isEmpty()) {
                writer.println("Online servers with players:");
                for (ServerInfo info : onlineWithPlayers) {
                    writer.println("  " + info.toString());
                }
                writer.println();
            }
            
            if (!whitelistWithPlayers.isEmpty()) {
                writer.println("Whitelist servers with players:");
                for (ServerInfo info : whitelistWithPlayers) {
                    writer.println("  " + info.toString());
                }
                writer.println();
            }
            
            if (!whitelistNoPlayers.isEmpty()) {
                writer.println("Whitelist servers (0 players):");
                for (ServerInfo info : whitelistNoPlayers) {
                    writer.println("  " + info.toString());
                }
                writer.println();
            }
            
            if (!onlineNoPlayers.isEmpty()) {
                writer.println("Online servers (0 players):");
                for (ServerInfo info : onlineNoPlayers) {
                    writer.println("  " + info.toString());
                }
                writer.println();
            }
            
            writer.println(repeat("=", 100));
        }
    }

    public void saveCsvResults(File file) throws IOException {
        ensureParentDirectory(file);

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)), true)) {
            writer.write('\ufeff');
            writer.println("ip,port,version,protocol,playersOnline,playersMax,pingMs,serverType,clientModsRequired,whitelist,access,kickReason,motd");
            for (ServerInfo info : getResultsSnapshot()) {
                writer.println(String.join(",",
                    csv(info.getIp()),
                    String.valueOf(info.getPort()),
                    csv(info.getVersion()),
                    String.valueOf(info.getProtocolVersion()),
                    String.valueOf(info.getPlayersOnline()),
                    String.valueOf(info.getPlayersMax()),
                    String.valueOf(info.getPing()),
                    csv(info.getServerPlatform().getLabel()),
                    csv(info.getClientModsText()),
                    csv(whitelistText(info)),
                    csv(info.getJoinStatus().name()),
                    csv(info.getKickReason()),
                    csv(info.getDisplayMotd())
                ));
            }
        }
    }

    public void saveJsonResults(File file) throws IOException {
        ensureParentDirectory(file);

        JSONObject root = new JSONObject();
        root.put("scanDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        root.put("targetIPs", new JSONArray(targetIPs));
        root.put("startPort", startPort);
        root.put("endPort", startPort + limit - 1);
        root.put("scanSpeed", String.valueOf(scanSpeed));
        root.put("checkNick", checkUsername);

        JSONArray servers = new JSONArray();
        for (ServerInfo info : getResultsSnapshot()) {
            JSONObject server = new JSONObject();
            server.put("ip", info.getIp());
            server.put("port", info.getPort());
            server.put("version", info.getVersion());
            server.put("protocol", info.getProtocolVersion());
            server.put("playersOnline", info.getPlayersOnline());
            server.put("playersMax", info.getPlayersMax());
            server.put("pingMs", info.getPing());
            server.put("serverType", info.getServerPlatform().getLabel());
            Boolean clientModsRequired = info.getClientModsRequired();
            server.put("clientModsRequired", clientModsRequired != null ? clientModsRequired : JSONObject.NULL);
            Boolean whitelist = info.getWhitelistResult();
            server.put("whitelist", whitelist != null ? whitelist : JSONObject.NULL);
            server.put("access", info.getJoinStatus().name());
            server.put("kickReason", info.getKickReason());
            server.put("motd", info.getDisplayMotd());
            servers.put(server);
        }
        root.put("servers", servers);

        try (Writer writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write('\ufeff');
            writer.write(root.toString(2));
            writer.write(System.lineSeparator());
        }
    }

    private static void ensureParentDirectory(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create directory: " + parent.getAbsolutePath());
        }
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static String whitelistText(ServerInfo info) {
        Boolean whitelist = info.getWhitelistResult();
        if (whitelist == null) {
            return "UNKNOWN";
        }
        return whitelist ? "YES" : "NO";
    }

    private static String repeat(String text, int count) {
        StringBuilder builder = new StringBuilder(text.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(text);
        }
        return builder.toString();
    }
    
    public static class ScanProgress {
        private final int scanned;
        private final int total;
        private final ServerInfo lastResult;
        private final int onlineTotal;
        private final int whitelistTotal;
        
        public ScanProgress(int scanned, int total, ServerInfo lastResult, int onlineTotal, int whitelistTotal) {
            this.scanned = scanned;
            this.total = total;
            this.lastResult = lastResult;
            this.onlineTotal = onlineTotal;
            this.whitelistTotal = whitelistTotal;
        }
        
        public int getScanned() { return scanned; }
        public int getTotal() { return total; }
        public ServerInfo getLastResult() { return lastResult; }
        public int getOnlineTotal() { return onlineTotal; }
        public int getWhitelistTotal() { return whitelistTotal; }

        public int getProgress() {
            if (total <= 0) {
                return 0;
            }
            return (int) ((scanned / (double) total) * 100);
        }
    }
}
