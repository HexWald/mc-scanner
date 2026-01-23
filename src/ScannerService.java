import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ScannerService {
    private final String startIP;
    private final int startPort;
    private final int limit;
    private final ScanSpeed scanSpeed;
    private final boolean onlyOnline;
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
    
    public ScannerService(String startIP, int startPort, int limit,
                          ScanSpeed scanSpeed, boolean onlyOnline) {
        this.startIP = startIP;
        this.startPort = startPort;
        this.limit = limit;
        this.scanSpeed = scanSpeed;
        this.onlyOnline = onlyOnline;
        this.executor = Executors.newFixedThreadPool(scanSpeed.threadPoolSize);
        this.results = new ConcurrentLinkedQueue<>();
        this.scannedCount = new AtomicInteger(0);
        this.onlineCount = new AtomicInteger(0);
        this.whitelistCount = new AtomicInteger(0);
        this.cancelled = false;
    }
    
    public void scan(Consumer<ScanProgress> progressCallback) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(limit);
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < limit && !cancelled; i++) {
            final int port = startPort + i;
            
            executor.submit(() -> {
                try {
                    ServerInfo info = MinecraftProtocol.queryServer(startIP, port);
                    
                    if (info.isOnline()) {
                        onlineCount.incrementAndGet();
                        if (info.hasWhitelist()) {
                            whitelistCount.incrementAndGet();
                        }
                    }
                    
                    if (!onlyOnline || info.isOnline()) {
                        results.add(info);
                    }
                    
                    int scanned = scannedCount.incrementAndGet();
                    if (progressCallback != null) {
                        progressCallback.accept(new ScanProgress(scanned, limit, info, 
                            onlineCount.get(), whitelistCount.get()));
                    }
                    
                } catch (Exception e) {
                    System.err.println("Error: " + startIP + ":" + port + " - " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
            
            if (i > 0 && i % 100 == 0) {
                Thread.sleep(scanSpeed.delayMs);
            }
        }
        
        latch.await();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
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
            writer.println("Target IP:    " + startIP);
            writer.println("Port Range:   " + startPort + " - " + (startPort + limit - 1));
            writer.println("Scan Speed:   " + scanSpeed);
            writer.println("Filter:       " + (onlyOnline ? "Only Online Servers" : "All Servers"));
            writer.println("=".repeat(100));
            writer.println();
            
            List<ServerInfo> sortedResults = new ArrayList<>(results);
            sortedResults.sort(Comparator.comparingInt(ServerInfo::getPort));
            
            Map<String, Integer> versionCount = new HashMap<>();
            
            for (ServerInfo info : sortedResults) {
                writer.println(info.toString());
                if (info.isOnline()) {
                    versionCount.put(info.getVersion(), versionCount.getOrDefault(info.getVersion(), 0) + 1);
                }
            }
            
            writer.println();
            writer.println("=".repeat(100));
            writer.println("                              SCAN STATISTICS");
            writer.println("=".repeat(100));
            writer.println("Total Ports Scanned:          " + scannedCount.get());
            writer.println("Online Servers Found:         " + onlineCount.get());
            writer.println("Servers with WhiteList:       " + whitelistCount.get());
            writer.println("Offline/Unreachable:          " + (scannedCount.get() - onlineCount.get()));
            
            if (!versionCount.isEmpty()) {
                writer.println();
                writer.println("Version Distribution:");
                versionCount.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .forEach(e -> writer.println("  " + e.getKey() + ": " + e.getValue() + " server(s)"));
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