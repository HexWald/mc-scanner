import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class MinecraftScreenshotService {
    private static final int MAX_PARALLEL_CAPTURES = 2;
    private static final Semaphore CAPTURE_LOCK = new Semaphore(MAX_PARALLEL_CAPTURES);

    private final File projectDir;
    private final File scriptFile;
    private final File outputDir;
    private final String nodeExecutable;
    private final int waitMs;
    private final int timeoutMs;

    public MinecraftScreenshotService(File projectDir, File outputDir, int waitMs) {
        this.projectDir = projectDir;
        this.scriptFile = new File(projectDir, "tools/screenshot-bot/capture.js");
        this.outputDir = outputDir;
        this.nodeExecutable = findNodeExecutable(projectDir);
        this.waitMs = waitMs;
        this.timeoutMs = Math.max(12000, waitMs + 12000);
    }

    public String capture(ServerInfo serverInfo, String username) {
        outputDir.mkdirs();
        File outputFile = new File(outputDir, buildFileName(serverInfo));

        if (nodeExecutable == null) {
            System.err.println("[Screenshot] Node.js not found. Set MC_SCANNER_NODE or install Node.js.");
            deletePartial(outputFile);
            return "";
        }
        if (!scriptFile.isFile()) {
            System.err.println("[Screenshot] capture.js not found: " + scriptFile.getAbsolutePath());
            deletePartial(outputFile);
            return "";
        }

        boolean locked = false;
        try {
            CAPTURE_LOCK.acquire();
            locked = true;

            List<String> command = new ArrayList<>();
            command.add(nodeExecutable);
            command.add(scriptFile.getAbsolutePath());
            command.add("--host");
            command.add(serverInfo.getIp());
            command.add("--port");
            command.add(String.valueOf(serverInfo.getPort()));
            command.add("--username");
            command.add(username);
            command.add("--out");
            command.add(outputFile.getAbsolutePath());
            command.add("--wait-ms");
            command.add(String.valueOf(waitMs));
            command.add("--timeout-ms");
            command.add(String.valueOf(timeoutMs));

            String clientVersion = MinecraftProtocol.getClientVersionName(
                serverInfo.getVersion(), serverInfo.getProtocolVersion());
            if (!clientVersion.isEmpty()) {
                command.add("--version");
                command.add(clientVersion);
            }

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(scriptFile.getParentFile());
            builder.redirectErrorStream(true);

            Process process = builder.start();
            ExecutorService outputReader = Executors.newSingleThreadExecutor();
            Future<String> outputFuture = outputReader.submit(() -> readProcessOutput(process));

            boolean finished = process.waitFor(timeoutMs + 5000L, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                outputReader.shutdownNow();
                System.err.println("[Screenshot] Timed out for " + serverInfo.getIp() + ":" + serverInfo.getPort());
                deletePartial(outputFile);
                return "";
            }

            String output = outputFuture.get(2, TimeUnit.SECONDS);
            outputReader.shutdownNow();

            if (process.exitValue() == 0 && outputFile.isFile()) {
                return outputFile.getAbsolutePath();
            }

            System.err.println("[Screenshot] Failed for " + serverInfo.getIp() + ":" + serverInfo.getPort() + ": " + output);
            deletePartial(outputFile);
            return "";
        } catch (Exception e) {
            System.err.println("[Screenshot] Failed for " + serverInfo.getIp() + ":" + serverInfo.getPort() + ": " + e.getMessage());
            deletePartial(outputFile);
            return "";
        } finally {
            if (locked) {
                CAPTURE_LOCK.release();
            }
        }
    }

    private static String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) {
                    output.append(" | ");
                }
                output.append(line);
            }
        }
        return output.toString();
    }

    private static void deletePartial(File outputFile) {
        try {
            if (outputFile != null && outputFile.isFile() && !outputFile.delete()) {
                System.err.println("[Screenshot] Could not delete partial screenshot: " + outputFile.getAbsolutePath());
            }
        } catch (Exception ignored) {
        }
    }

    private static String findNodeExecutable(File projectDir) {
        String fromEnv = System.getenv("MC_SCANNER_NODE");
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            File envFile = new File(fromEnv);
            return envFile.isFile() ? envFile.getAbsolutePath() : fromEnv;
        }

        String[] localCandidates = isWindows()
            ? new String[] {"node/node.exe", "node/bin/node.exe", "node.exe"}
            : new String[] {"node/bin/node", "node"};

        for (String candidate : localCandidates) {
            File localNode = new File(projectDir, candidate);
            if (localNode.isFile()) {
                return localNode.getAbsolutePath();
            }
        }

        return "node";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static String buildFileName(ServerInfo serverInfo) {
        String host = serverInfo.getIp().replaceAll("[^A-Za-z0-9._-]", "_");
        return host + "_" + serverInfo.getPort() + ".png";
    }
}
