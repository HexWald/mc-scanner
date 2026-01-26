import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ScannerService {
    private final List<String> targetIPs;
    private final int startPort;
    private final int limit;
    private final ScanSpeed scanSpeed;
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
        this.targetIPs = targetIPs;
        this.startPort = startPort;
        this.limit = limit;
        this.scanSpeed = scanSpeed;
        
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
                        
                        ServerInfo info = MinecraftProtocol.queryServer(targetIP, port);
                        
                        if (cancelled) {
                            return;
                        }
                        
                        if (info.isOnline()) {
                            onlineCount.incrementAndGet();
                            if (info.hasWhitelist()) {
                                whitelistCount.incrementAndGet();
                            }
                            results.add(info);
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
    
    public void cancel() {
        cancelled = true;
        executor.shutdownNow();
    }
    
    public void saveResults(File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file)), true)) {
            
            writer.println("=".repeat(100));
            writer.println("                    MINECRAFT SERVER SCANNER - DETAILED RESULTS");
            writer.println("=".repeat(100));
            writer.println("Scan Date:    " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            writer.println("Target IPs:   " + String.join(", ", targetIPs));
            writer.println("Port Range:   " + startPort + " - " + (startPort + limit - 1));
            writer.println("Scan Speed:   " + scanSpeed);
            writer.println("=".repeat(100));
            writer.println();
            
            // Sort all results by port first
            List<ServerInfo> sortedResults = new ArrayList<>(results);
            sortedResults.sort(Comparator.comparingInt(ServerInfo::getPort));
            
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
            
            writer.println("━".repeat(100));
            writer.println("RESULTS FOR ALL IPs: " + String.join(", ", targetIPs));
            writer.println("━".repeat(100));
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
            
            writer.println("=".repeat(100));
        }
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
        public int getProgress() { return (int) ((scanned / (double) total) * 100); }
    }
}