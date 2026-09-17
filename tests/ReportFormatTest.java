import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

public class ReportFormatTest {
    public static void main(String[] args) throws Exception {
        File report = File.createTempFile("mcscanner-report-", ".txt");
        try {
            ScannerService scanner = new ScannerService(
                Arrays.asList("192.0.2.10", "198.51.100.20"),
                25565,
                1,
                ScannerService.ScanSpeed.MEDIUM
            );
            scanner.saveResults(report);

            String text = new String(Files.readAllBytes(report.toPath()), StandardCharsets.UTF_8);
            if (text.contains("Target IPs") || text.contains("RESULTS FOR ALL IPs")) {
                throw new AssertionError("The report still contains the target list heading");
            }
            if (text.contains("192.0.2.10") || text.contains("198.51.100.20")) {
                throw new AssertionError("The report still contains target addresses");
            }
            if (!text.contains("SCAN RESULTS")) {
                throw new AssertionError("The results heading is missing");
            }
        } finally {
            Files.deleteIfExists(report.toPath());
        }

        System.out.println("Report format check passed");
    }
}
