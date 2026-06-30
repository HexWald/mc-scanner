import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.io.IOException;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class ScannerGUI extends JFrame {
    private static Color BG = new Color(14, 15, 18);
    private static Color PANEL_BG = new Color(31, 32, 36, 238);
    private static Color PANEL_BORDER = new Color(63, 65, 72, 210);
    private static Color FIELD_BG = new Color(20, 21, 25);
    private static Color FIELD_FG = new Color(232, 234, 238);
    private static Color MUTED_FG = new Color(158, 163, 172);
    private static Color ACCENT = new Color(118, 127, 145);
    private static Color ACCENT_HOVER = new Color(142, 150, 166);
    private static Color SUCCESS = new Color(111, 194, 139);
    private static Color DANGER = new Color(203, 92, 102);
    private static final String FONT = "Segoe UI";

    private final JTextArea ipArea;
    private final JTextField portField;
    private final JTextField amountField;
    private final JTextField usernameField;
    private final JCheckBox screenshotsCheckBox;
    private final JTextField screenshotWaitField;
    private final JCheckBox monitoringCheckBox;
    private final JTextField monitoringIntervalField;
    private final JComboBox<ThemeMode> themeCombo;
    private final JComboBox<ScannerService.ScanSpeed> speedCombo;
    private final JButton scanButton;
    private final JButton openResultsButton;
    private final JButton openScreenshotsButton;
    private final JButton cleanOutputButton;
    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final JLabel statsLabel;
    private final JTextArea changesArea;
    
    private volatile ScannerService currentScanner;
    private volatile Thread workerThread;
    private volatile boolean stopRequested;
    private File lastResultsDir;
    private File lastScreenshotsDir;

    private enum ThemeMode {
        DARK("Dark"),
        LIGHT("Light"),
        SYSTEM("System");

        private final String label;

        ThemeMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
    
    public ScannerGUI() {
        super("MC Scanner");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(true);
        getContentPane().setBackground(BG);
        
        AmbientPanel mainPanel = new AmbientPanel(new BorderLayout(18, 18));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);
        GridBagConstraints layout = new GridBagConstraints();
        layout.insets = new Insets(0, 0, 0, 0);
        layout.fill = GridBagConstraints.BOTH;
        layout.weightx = 0.46;
        layout.weighty = 1.0;
        
        JPanel inputPanel = new GlassPanel(new BorderLayout(0, 18));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        inputPanel.add(sectionTitle("Scanner setup"), BorderLayout.NORTH);

        JPanel setupContent = new JPanel(new BorderLayout(0, 16));
        setupContent.setOpaque(false);

        JPanel ipBlock = new JPanel(new BorderLayout(0, 8));
        ipBlock.setOpaque(false);
        JLabel ipLabel = new JLabel("Server IPs:");
        ipBlock.add(ipLabel, BorderLayout.NORTH);

        ipArea = new JTextArea(6, 28);
        ipArea.setLineWrap(true);
        ipArea.setWrapStyleWord(true);
        ipArea.setText("localhost");
        ipArea.setFont(new Font(FONT, Font.PLAIN, 13));
        JScrollPane scrollPane = new JScrollPane(ipArea);
        scrollPane.setPreferredSize(new Dimension(410, 136));
        scrollPane.setMinimumSize(new Dimension(350, 120));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // Enable mouse wheel scrolling
        ipArea.addMouseWheelListener(e -> {
            JScrollBar scrollBar = scrollPane.getVerticalScrollBar();
            int scrollAmount = e.getUnitsToScroll() * 3;
            scrollBar.setValue(scrollBar.getValue() + scrollAmount);
        });

        ipBlock.add(scrollPane, BorderLayout.CENTER);
        JLabel formatLabel = new JLabel("Format: IP, IP1-IP10, or IP1 IP2 IP3");
        formatLabel.putClientProperty("mutedLabel", Boolean.TRUE);
        ipBlock.add(formatLabel, BorderLayout.SOUTH);
        setupContent.add(ipBlock, BorderLayout.NORTH);

        JPanel fieldsPanel = new JPanel(new GridLayout(4, 2, 18, 10));
        fieldsPanel.setOpaque(false);

        portField = new JTextField("25565", 10);
        portField.setFont(new Font(FONT, Font.PLAIN, 13));
        fieldsPanel.add(fieldBlock("Start Port", portField));

        amountField = new JTextField("100", 10);
        amountField.setFont(new Font(FONT, Font.PLAIN, 13));
        fieldsPanel.add(fieldBlock("Scan Amount", amountField));

        usernameField = new JTextField("MCScanner", 10);
        usernameField.setFont(new Font(FONT, Font.PLAIN, 13));
        fieldsPanel.add(fieldBlock("Check Nickname", usernameField));

        screenshotWaitField = new JTextField("3", 10);
        screenshotWaitField.setFont(new Font(FONT, Font.PLAIN, 13));
        fieldsPanel.add(fieldBlock("Screenshot Wait", screenshotWaitField));

        speedCombo = new ModernComboBox<>(ScannerService.ScanSpeed.values());
        speedCombo.setSelectedItem(ScannerService.ScanSpeed.FAST);
        fieldsPanel.add(fieldBlock("Speed", speedCombo));

        themeCombo = new ModernComboBox<>(ThemeMode.values());
        themeCombo.setSelectedItem(ThemeMode.DARK);
        themeCombo.addActionListener(e -> {
            ThemeMode mode = (ThemeMode) themeCombo.getSelectedItem();
            applyThemeMode(mode != null ? mode : ThemeMode.DARK);
        });
        fieldsPanel.add(fieldBlock("Theme", themeCombo));

        screenshotsCheckBox = new JCheckBox("Always save");
        screenshotsCheckBox.setSelected(true);
        screenshotsCheckBox.addActionListener(e -> screenshotsCheckBox.setSelected(true));
        fieldsPanel.add(checkBlock("Screenshots", screenshotsCheckBox));

        monitoringCheckBox = new JCheckBox("Repeat scans");
        monitoringCheckBox.addActionListener(e -> updateMonitoringIntervalEnabled());

        monitoringIntervalField = new JTextField("5", 10);
        monitoringIntervalField.setFont(new Font(FONT, Font.PLAIN, 13));
        monitoringIntervalField.setPreferredSize(new Dimension(64, 36));
        monitoringIntervalField.setMinimumSize(new Dimension(58, 34));
        monitoringIntervalField.setEnabled(false);

        JPanel monitoringBlock = new JPanel(new BorderLayout(0, 6));
        monitoringBlock.setOpaque(false);
        monitoringBlock.add(new JLabel("Monitoring:"), BorderLayout.NORTH);
        JPanel monitoringControls = new JPanel(new GridBagLayout());
        monitoringControls.setOpaque(false);
        GridBagConstraints monitoringGbc = new GridBagConstraints();
        monitoringGbc.gridy = 0;
        monitoringGbc.fill = GridBagConstraints.HORIZONTAL;
        monitoringGbc.insets = new Insets(0, 0, 0, 8);
        monitoringGbc.weightx = 1.0;
        monitoringControls.add(monitoringCheckBox, monitoringGbc);
        monitoringGbc.gridx = 1;
        monitoringGbc.weightx = 0.0;
        JLabel intervalInlineLabel = new JLabel("Every min:");
        intervalInlineLabel.putClientProperty("mutedLabel", Boolean.TRUE);
        monitoringControls.add(intervalInlineLabel, monitoringGbc);
        monitoringGbc.gridx = 2;
        monitoringGbc.insets = new Insets(0, 0, 0, 0);
        monitoringControls.add(monitoringIntervalField, monitoringGbc);
        monitoringBlock.add(monitoringControls, BorderLayout.CENTER);
        fieldsPanel.add(monitoringBlock);

        setupContent.add(fieldsPanel, BorderLayout.CENTER);
        inputPanel.add(setupContent, BorderLayout.CENTER);
        
        layout.gridx = 0;
        layout.gridy = 0;
        layout.insets = new Insets(0, 0, 0, 12);
        centerPanel.add(inputPanel, layout);
        
        JPanel progressPanel = new GlassPanel(new BorderLayout(12, 14));
        progressPanel.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        
        progressBar = new ModernProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Ready to scan");
        progressBar.setPreferredSize(new Dimension(420, 28));
        JPanel progressHeader = new JPanel(new BorderLayout(0, 12));
        progressHeader.setOpaque(false);
        progressHeader.add(sectionTitle("Scan progress"), BorderLayout.NORTH);
        progressHeader.add(progressBar, BorderLayout.CENTER);
        progressPanel.add(progressHeader, BorderLayout.NORTH);

        JPanel progressContentPanel = new JPanel(new BorderLayout(8, 8));
        progressContentPanel.setOpaque(false);
        
        statusLabel = new JLabel("Status: Waiting for input...", SwingConstants.CENTER);
        progressContentPanel.add(statusLabel, BorderLayout.NORTH);

        changesArea = new JTextArea(5, 40);
        changesArea.setEditable(false);
        changesArea.setLineWrap(true);
        changesArea.setWrapStyleWord(true);
        changesArea.setFont(new Font(FONT, Font.PLAIN, 13));
        changesArea.setText("Last changes will appear here.");
        JScrollPane changesScrollPane = new JScrollPane(changesArea);
        changesScrollPane.setBorder(BorderFactory.createLineBorder(PANEL_BORDER));
        progressContentPanel.add(changesScrollPane, BorderLayout.CENTER);
        
        statsLabel = new JLabel("Online: 0 | WhiteList: 0", SwingConstants.CENTER);
        statsLabel.setFont(new Font(FONT, Font.BOLD, 13));
        progressContentPanel.add(statsLabel, BorderLayout.SOUTH);

        progressPanel.add(progressContentPanel, BorderLayout.CENTER);
        
        layout.gridx = 1;
        layout.gridy = 0;
        layout.weightx = 0.54;
        layout.insets = new Insets(0, 12, 0, 0);
        centerPanel.add(progressPanel, layout);

        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new GlassPanel(new GridBagLayout());
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        GridBagConstraints buttonGbc = new GridBagConstraints();
        buttonGbc.gridy = 0;
        buttonGbc.fill = GridBagConstraints.HORIZONTAL;
        buttonGbc.insets = new Insets(0, 5, 0, 5);

        buttonGbc.gridx = 0;
        buttonGbc.weightx = 1.0;
        buttonPanel.add(Box.createHorizontalGlue(), buttonGbc);

        scanButton = new ModernButton("Start Scan");
        scanButton.setPreferredSize(new Dimension(198, 44));
        scanButton.setFont(new Font(FONT, Font.BOLD, 14));
        scanButton.addActionListener(e -> handleScanButton());
        buttonGbc.gridx = 1;
        buttonGbc.weightx = 0.0;
        buttonPanel.add(scanButton, buttonGbc);

        openResultsButton = new ModernButton("Open Results");
        openResultsButton.setPreferredSize(new Dimension(144, 40));
        openResultsButton.setEnabled(false);
        openResultsButton.addActionListener(e -> openDirectory(lastResultsDir));
        buttonGbc.gridx = 2;
        buttonPanel.add(openResultsButton, buttonGbc);

        openScreenshotsButton = new ModernButton("Open Screenshots");
        openScreenshotsButton.setPreferredSize(new Dimension(168, 40));
        openScreenshotsButton.setEnabled(false);
        openScreenshotsButton.addActionListener(e -> openDirectory(lastScreenshotsDir));
        buttonGbc.gridx = 3;
        buttonPanel.add(openScreenshotsButton, buttonGbc);

        cleanOutputButton = new ModernButton("Clean Output");
        cleanOutputButton.setPreferredSize(new Dimension(144, 40));
        cleanOutputButton.addActionListener(e -> cleanOutput());
        buttonGbc.gridx = 4;
        buttonPanel.add(cleanOutputButton, buttonGbc);

        buttonGbc.gridx = 5;
        buttonGbc.weightx = 1.0;
        buttonPanel.add(Box.createHorizontalGlue(), buttonGbc);
        
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        lastResultsDir = AppPaths.resultsDir();
        lastScreenshotsDir = AppPaths.screenshotsDir();
        stopRequested = false;

        applyModernTheme(mainPanel);
        stylePrimaryButton(scanButton);
        styleSecondaryButton(openResultsButton);
        styleSecondaryButton(openScreenshotsButton);
        styleDangerButton(cleanOutputButton);

        add(mainPanel);
        pack();
        setMinimumSize(new Dimension(1040, 720));
        setSize(new Dimension(1100, 760));
        setLocationRelativeTo(null);
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty("sectionTitle", Boolean.TRUE);
        label.setFont(new Font(FONT, Font.BOLD, 16));
        return label;
    }

    private JPanel fieldBlock(String labelText, JComponent input) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel label = new JLabel(labelText + ":");
        input.setPreferredSize(new Dimension(190, 36));
        input.setMinimumSize(new Dimension(150, 34));
        panel.add(label, BorderLayout.NORTH);
        panel.add(input, BorderLayout.CENTER);
        return panel;
    }

    private JPanel checkBlock(String labelText, JCheckBox checkBox) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel label = new JLabel(labelText + ":");
        checkBox.setPreferredSize(new Dimension(190, 36));
        checkBox.setMinimumSize(new Dimension(150, 34));
        panel.add(label, BorderLayout.NORTH);
        panel.add(checkBox, BorderLayout.CENTER);
        return panel;
    }

    private List<String> parseIPs(String input) {
        List<String> ips = new ArrayList<>();
        String[] parts = input.trim().split("[\\s,;]+");
        
        for (String part : parts) {
            if (part.isEmpty()) continue;
            
            // Check for range format
            if (part.contains("-")) {
                String[] range = part.split("-", 2);
                String start = range[0].trim();
                String end = range[1].trim();
                
                // Try to find numbers anywhere in the string
                java.util.regex.Pattern numberPattern = java.util.regex.Pattern.compile("(\\d+)");
                java.util.regex.Matcher startMatcher = numberPattern.matcher(start);
                java.util.regex.Matcher endMatcher = numberPattern.matcher(end);
                
                // Find all numbers in start string
                List<Integer> startNumbers = new ArrayList<>();
                List<Integer> startPositions = new ArrayList<>();
                while (startMatcher.find()) {
                    startNumbers.add(Integer.parseInt(startMatcher.group()));
                    startPositions.add(startMatcher.start());
                }
                
                // Find all numbers in end string
                List<Integer> endNumbers = new ArrayList<>();
                while (endMatcher.find()) {
                    endNumbers.add(Integer.parseInt(endMatcher.group()));
                }
                
                // If both have at least one number
                if (!startNumbers.isEmpty() && !endNumbers.isEmpty()) {
                    // Use the LAST number from each string
                    int startNum = startNumbers.get(startNumbers.size() - 1);
                    int endNum = endNumbers.get(endNumbers.size() - 1);
                    int lastNumPos = startPositions.get(startPositions.size() - 1);
                    
                    // Extract prefix and suffix
                    String prefix = start.substring(0, lastNumPos);
                    String suffix = start.substring(lastNumPos + String.valueOf(startNum).length());
                    
                    // Generate range
                    if (startNum <= endNum) {
                        for (int i = startNum; i <= endNum; i++) {
                            ips.add(prefix + i + suffix);
                        }
                    } else {
                        // Reverse range
                        for (int i = startNum; i >= endNum; i--) {
                            ips.add(prefix + i + suffix);
                        }
                    }
                } else {
                    // No numbers found, add as-is
                    ips.add(start);
                    ips.add(end);
                }
            } else {
                ips.add(part);
            }
        }
        
        return ips;
    }
    
    private void handleScanButton() {
        if (scanButton.getText().equals("Start Scan")) {
            startScan();
        } else {
            cancelScan();
        }
    }
    
    private void startScan() {
        String ipInput = ipArea.getText().trim();
        if (ipInput.isEmpty()) {
            showError("Please enter at least one IP address");
            return;
        }
        
        List<String> ips = parseIPs(ipInput);
        if (ips.isEmpty()) {
            showError("No valid IPs found");
            return;
        }
        
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) {
                showError("Port must be between 1 and 65535");
                return;
            }
        } catch (NumberFormatException e) {
            showError("Invalid port number");
            return;
        }
        
        int amount;
        try {
            amount = Integer.parseInt(amountField.getText().trim());
            if (amount < 1 || amount > 10000) {
                showError("Amount must be between 1 and 10000");
                return;
            }
            if ((long) port + amount - 1 > 65535) {
                showError("Port range must end at 65535 or lower");
                return;
            }
        } catch (NumberFormatException e) {
            showError("Invalid amount");
            return;
        }

        String checkUsername = usernameField.getText().trim();
        if (!checkUsername.matches("[A-Za-z0-9_]{3,16}")) {
            showError("Check nickname must be 3-16 characters: A-Z, 0-9 or _");
            return;
        }

        boolean screenshotsEnabled = true;
        int screenshotWaitMs;
        try {
            int screenshotWaitSeconds = Integer.parseInt(screenshotWaitField.getText().trim());
            if (screenshotWaitSeconds < 1 || screenshotWaitSeconds > 30) {
                showError("Screenshot wait must be between 1 and 30 seconds");
                return;
            }
            screenshotWaitMs = screenshotWaitSeconds * 1000;
        } catch (NumberFormatException e) {
            showError("Invalid screenshot wait");
            return;
        }

        boolean monitoringEnabled = monitoringCheckBox.isSelected();
        int monitoringIntervalMinutes = 0;
        if (monitoringEnabled) {
            try {
                monitoringIntervalMinutes = Integer.parseInt(monitoringIntervalField.getText().trim());
                if (monitoringIntervalMinutes < 1 || monitoringIntervalMinutes > 1440) {
                    showError("Monitoring interval must be between 1 and 1440 minutes");
                    return;
                }
            } catch (NumberFormatException e) {
                showError("Invalid monitoring interval");
                return;
            }
        }
        
        stopRequested = false;
        setInputsEnabled(false);
        scanButton.setText(monitoringEnabled ? "Cancel Monitoring" : "Cancel Scan");
        styleButton(scanButton, DANGER, Color.WHITE, new Color(248, 113, 113));
        progressBar.setValue(0);
        progressBar.setString("Initializing...");
        statusLabel.setText("Status: Starting scan for " + ips.size() + " IP(s)...");
        statsLabel.setText("Online: 0 | WhiteList: 0");
        changesArea.setText(monitoringEnabled
            ? "Monitoring started. First run becomes the baseline."
            : "Single scan started.");
        
        ScannerService.ScanSpeed speed = (ScannerService.ScanSpeed) speedCombo.getSelectedItem();
        final int monitoringInterval = monitoringIntervalMinutes;

        workerThread = new Thread(() -> runScanLoop(ips, port, amount, speed, checkUsername,
            screenshotsEnabled, screenshotWaitMs, monitoringEnabled, monitoringInterval), "ScannerThread");
        workerThread.start();
    }

    private void runScanLoop(List<String> ips, int port, int amount, ScannerService.ScanSpeed speed,
                             String checkUsername, boolean screenshotsEnabled, int screenshotWaitMs,
                             boolean monitoringEnabled, int monitoringIntervalMinutes) {
        Map<String, ServerInfo> previousResults = null;
        int runNumber = 1;

        try {
            while (!stopRequested) {
                final int currentRun = runNumber;
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(0);
                    progressBar.setString("Initializing...");
                    statusLabel.setText(monitoringEnabled
                        ? "Monitoring run #" + currentRun + " started..."
                        : "Scan started...");
                });

                ScannerService scanner = new ScannerService(ips, port, amount, speed,
                    checkUsername, screenshotsEnabled, screenshotWaitMs);
                currentScanner = scanner;

                scanner.scan(progress -> updateProgress(progress, monitoringEnabled, currentRun));
                if (stopRequested) {
                    break;
                }

                String timestamp = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
                File txtFile = new File(AppPaths.resultsDir(), "MCScanner_Results_" + timestamp + ".txt");
                File csvFile = new File(AppPaths.resultsDir(), "MCScanner_Results_" + timestamp + ".csv");
                File jsonFile = new File(AppPaths.resultsDir(), "MCScanner_Results_" + timestamp + ".json");

                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(100);
                    progressBar.setString("Saving exports...");
                    statusLabel.setText("Saving TXT, CSV and JSON...");
                });

                scanner.saveResults(txtFile);
                scanner.saveCsvResults(csvFile);
                scanner.saveJsonResults(jsonFile);

                List<ServerInfo> currentResults = scanner.getResultsSnapshot();
                String changeText = buildChangeSummary(previousResults, currentResults, currentRun, monitoringEnabled);
                previousResults = toServerMap(currentResults);

                File screenshotFolder = scanner.getScreenshotOutputDir();
                lastResultsDir = AppPaths.resultsDir();
                lastScreenshotsDir = screenshotFolder;
                currentScanner = null;

                showRunComplete(ips, txtFile, csvFile, jsonFile, screenshotFolder,
                    changeText, monitoringEnabled, currentRun);

                if (!monitoringEnabled) {
                    break;
                }

                if (!waitForNextMonitoringRun(monitoringIntervalMinutes, currentRun + 1)) {
                    break;
                }
                runNumber++;
            }

            SwingUtilities.invokeLater(() -> {
                if (monitoringEnabled && stopRequested) {
                    statusLabel.setText("Monitoring stopped");
                    progressBar.setString("Stopped");
                }
                resetUI();
            });
        } catch (InterruptedException e) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText(monitoringEnabled ? "Monitoring cancelled" : "Scan cancelled by user");
                progressBar.setString("Cancelled");
                resetUI();
            });
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                showError("Scan error: " + e.getMessage());
                resetUI();
            });
            e.printStackTrace();
        } finally {
            currentScanner = null;
            workerThread = null;
        }
    }

    private void updateProgress(ScannerService.ScanProgress progress, boolean monitoringEnabled, int runNumber) {
        SwingUtilities.invokeLater(() -> {
            String prefix = monitoringEnabled ? "Run #" + runNumber + " - " : "";
            progressBar.setValue(progress.getProgress());
            statsLabel.setText(String.format("Online: %d | WhiteList: %d",
                progress.getOnlineTotal(), progress.getWhitelistTotal()));

            if (progress.isScreenshotStage()) {
                progressBar.setString(String.format("%sScreenshots %d / %d", prefix,
                    progress.getScreenshotsDone(), progress.getScreenshotsTotal()));
                statusLabel.setText(String.format("%sSaving screenshots: %d / %d", prefix,
                    progress.getScreenshotsDone(), progress.getScreenshotsTotal()));
                return;
            }

            progressBar.setString(prefix + "Scan " + progress.getScanned() + " / " + progress.getTotal());

            ServerInfo info = progress.getLastResult();
            if (info != null && info.isOnline()) {
                statusLabel.setText(String.format("%sFound: %s:%d [%s] WL:%s",
                    prefix, info.getIp(), info.getPort(), info.getVersion(),
                    info.hasWhitelist() ? "YES" : "NO"));
            } else if (info != null) {
                statusLabel.setText(prefix + "Scanning: " + info.getIp() + ":" + info.getPort());
            }
        });
    }

    private void showRunComplete(List<String> ips, File txtFile, File csvFile, File jsonFile,
                                 File screenshotFolder, String changeText,
                                 boolean monitoringEnabled, int runNumber) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(100);
            progressBar.setString(monitoringEnabled ? "Run #" + runNumber + " Complete" : "Scan Complete!");
            statusLabel.setText(monitoringEnabled
                ? "Monitoring run #" + runNumber + " saved successfully"
                : "Results saved successfully!");
            changesArea.setText(changeText);
            openResultsButton.setEnabled(true);
            openScreenshotsButton.setEnabled(true);

            if (!monitoringEnabled) {
                JOptionPane.showMessageDialog(this,
                    "Scan completed!\n\nScanned " + ips.size() + " IP(s)"
                        + "\nTXT:\n" + txtFile.getAbsolutePath()
                        + "\n\nCSV:\n" + csvFile.getAbsolutePath()
                        + "\n\nJSON:\n" + jsonFile.getAbsolutePath()
                        + "\n\nScreenshots folder:\n" + screenshotFolder.getAbsolutePath(),
                    "Scan Complete",
                    JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }

    private boolean waitForNextMonitoringRun(int monitoringIntervalMinutes, int nextRunNumber) throws InterruptedException {
        long totalMillis = monitoringIntervalMinutes * 60L * 1000L;
        long endAt = System.currentTimeMillis() + totalMillis;

        while (!stopRequested) {
            long remaining = endAt - System.currentTimeMillis();
            if (remaining <= 0) {
                return true;
            }

            long seconds = Math.max(1L, remaining / 1000L);
            long minutesPart = seconds / 60L;
            long secondsPart = seconds % 60L;
            int progress = (int) Math.min(100L, Math.max(0L, ((totalMillis - remaining) * 100L) / totalMillis));

            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(progress);
                progressBar.setString(String.format("Next scan in %02d:%02d", minutesPart, secondsPart));
                statusLabel.setText("Waiting for monitoring run #" + nextRunNumber + "...");
            });

            Thread.sleep(Math.min(1000L, remaining));
        }

        return false;
    }

    private String buildChangeSummary(Map<String, ServerInfo> previousResults,
                                      List<ServerInfo> currentResults,
                                      int runNumber,
                                      boolean monitoringEnabled) {
        Map<String, ServerInfo> currentMap = toServerMap(currentResults);
        if (!monitoringEnabled) {
            return "Scan complete. Online servers: " + currentResults.size();
        }

        if (previousResults == null) {
            return "Run #" + runNumber + " baseline saved.\nOnline servers: " + currentResults.size()
                + "\nNext run will show added, removed and changed servers.";
        }

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> changed = new ArrayList<>();

        for (Map.Entry<String, ServerInfo> entry : currentMap.entrySet()) {
            String key = entry.getKey();
            ServerInfo current = entry.getValue();
            ServerInfo previous = previousResults.get(key);
            if (previous == null) {
                added.add("+ " + key + " [" + current.getVersion() + "] " + current.getPlayersOnline() + "/" + current.getPlayersMax());
            } else {
                String diff = describeServerChange(previous, current);
                if (!diff.isEmpty()) {
                    changed.add("* " + key + " " + diff);
                }
            }
        }

        for (String key : previousResults.keySet()) {
            if (!currentMap.containsKey(key)) {
                removed.add("- " + key);
            }
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Run #").append(runNumber)
            .append(" | Online: ").append(currentResults.size())
            .append(" | Added: ").append(added.size())
            .append(" | Removed: ").append(removed.size())
            .append(" | Changed: ").append(changed.size());

        appendChangeLines(summary, added);
        appendChangeLines(summary, removed);
        appendChangeLines(summary, changed);

        if (added.isEmpty() && removed.isEmpty() && changed.isEmpty()) {
            summary.append("\nNo changes since previous run.");
        }

        return summary.toString();
    }

    private static void appendChangeLines(StringBuilder summary, List<String> lines) {
        int maxLines = 20;
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            summary.append('\n').append(lines.get(i));
        }
        if (lines.size() > maxLines) {
            summary.append("\n...and ").append(lines.size() - maxLines).append(" more");
        }
    }

    private static Map<String, ServerInfo> toServerMap(List<ServerInfo> servers) {
        Map<String, ServerInfo> map = new TreeMap<>();
        for (ServerInfo server : servers) {
            map.put(serverKey(server), server);
        }
        return map;
    }

    private static String serverKey(ServerInfo server) {
        return server.getIp() + ":" + server.getPort();
    }

    private static String describeServerChange(ServerInfo previous, ServerInfo current) {
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(previous.getVersion(), current.getVersion())) {
            changes.add("version " + previous.getVersion() + " -> " + current.getVersion());
        }
        if (previous.getPlayersOnline() != current.getPlayersOnline()
                || previous.getPlayersMax() != current.getPlayersMax()) {
            changes.add("players " + previous.getPlayersOnline() + "/" + previous.getPlayersMax()
                + " -> " + current.getPlayersOnline() + "/" + current.getPlayersMax());
        }
        if (previous.hasWhitelist() != current.hasWhitelist()) {
            changes.add("WL " + (previous.hasWhitelist() ? "YES" : "NO")
                + " -> " + (current.hasWhitelist() ? "YES" : "NO"));
        }
        if (!Objects.equals(previous.getDisplayMotd(), current.getDisplayMotd())) {
            changes.add("MOTD changed");
        }
        return String.join("; ", changes);
    }
    
    private void cancelScan() {
        stopRequested = true;
        ScannerService scanner = currentScanner;
        if (scanner != null) {
            scanner.cancel();
        }
        Thread thread = workerThread;
        if (thread != null) {
            thread.interrupt();
        }
        statusLabel.setText("Cancelling...");
        scanButton.setEnabled(false);
    }
    
    private void resetUI() {
        setInputsEnabled(true);
        scanButton.setText("Start Scan");
        stylePrimaryButton(scanButton);
        scanButton.setEnabled(true);
        cleanOutputButton.setEnabled(true);
        currentScanner = null;
    }
    
    private void setInputsEnabled(boolean enabled) {
        ipArea.setEnabled(enabled);
        portField.setEnabled(enabled);
        amountField.setEnabled(enabled);
        usernameField.setEnabled(enabled);
        screenshotsCheckBox.setEnabled(true);
        screenshotWaitField.setEnabled(enabled);
        monitoringCheckBox.setEnabled(enabled);
        monitoringIntervalField.setEnabled(enabled && monitoringCheckBox.isSelected());
        speedCombo.setEnabled(enabled);
        cleanOutputButton.setEnabled(enabled);
    }

    private void updateMonitoringIntervalEnabled() {
        monitoringIntervalField.setEnabled(monitoringCheckBox.isEnabled() && monitoringCheckBox.isSelected());
    }

    private void applyThemeMode(ThemeMode mode) {
        ThemeMode effectiveMode = mode == ThemeMode.SYSTEM ? detectSystemTheme() : mode;
        if (effectiveMode == ThemeMode.LIGHT) {
            BG = new Color(238, 240, 243);
            PANEL_BG = new Color(255, 255, 255, 244);
            PANEL_BORDER = new Color(199, 204, 214, 225);
            FIELD_BG = new Color(247, 249, 252);
            FIELD_FG = new Color(31, 35, 43);
            MUTED_FG = new Color(92, 99, 112);
            ACCENT = new Color(84, 94, 110);
            ACCENT_HOVER = new Color(108, 118, 136);
            SUCCESS = new Color(64, 154, 91);
            DANGER = new Color(176, 74, 84);
        } else {
            BG = new Color(14, 15, 18);
            PANEL_BG = new Color(31, 32, 36, 238);
            PANEL_BORDER = new Color(63, 65, 72, 210);
            FIELD_BG = new Color(20, 21, 25);
            FIELD_FG = new Color(232, 234, 238);
            MUTED_FG = new Color(158, 163, 172);
            ACCENT = new Color(118, 127, 145);
            ACCENT_HOVER = new Color(142, 150, 166);
            SUCCESS = new Color(111, 194, 139);
            DANGER = new Color(203, 92, 102);
        }

        getContentPane().setBackground(BG);
        applyModernTheme(getContentPane());
        refreshButtonStyles();
        repaint();
    }

    private static ThemeMode detectSystemTheme() {
        Color panel = UIManager.getColor("Panel.background");
        if (panel == null) {
            return ThemeMode.DARK;
        }
        int brightness = (panel.getRed() + panel.getGreen() + panel.getBlue()) / 3;
        return brightness > 150 ? ThemeMode.LIGHT : ThemeMode.DARK;
    }

    private void refreshButtonStyles() {
        stylePrimaryButton(scanButton);
        styleSecondaryButton(openResultsButton);
        styleSecondaryButton(openScreenshotsButton);
        styleDangerButton(cleanOutputButton);
    }

    private void openDirectory(File dir) {
        if (dir == null) {
            showError("Folder is not ready yet");
            return;
        }

        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                showError("Could not create folder: " + dir.getAbsolutePath());
                return;
            }
            Desktop.getDesktop().open(dir);
        } catch (Exception e) {
            showError("Could not open folder: " + e.getMessage());
        }
    }

    private void cleanOutput() {
        if (workerThread != null && workerThread.isAlive()) {
            showError("Stop the scan before cleaning output");
            return;
        }

        File resultsDir = AppPaths.resultsDir();
        File screenshotsDir = AppPaths.screenshotsDir();
        int answer = JOptionPane.showConfirmDialog(this,
            "Delete all files from:\n" + resultsDir.getAbsolutePath()
                + "\n\nand:\n" + screenshotsDir.getAbsolutePath(),
            "Clean Output",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            deleteContents(resultsDir);
            deleteContents(screenshotsDir);
            changesArea.setText("Output cleaned.");
            statusLabel.setText("Results and screenshots cleaned");
            openResultsButton.setEnabled(false);
            openScreenshotsButton.setEnabled(false);
        } catch (IOException e) {
            showError("Clean error: " + e.getMessage());
        }
    }

    private static void deleteContents(File dir) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            deleteRecursively(child);
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) {
            deleteContents(file);
        }
        if (file.exists() && !file.delete()) {
            throw new IOException("Could not delete: " + file.getAbsolutePath());
        }
    }

    private void applyModernTheme(Component component) {
        if (component instanceof GlassPanel) {
            component.setForeground(FIELD_FG);
        } else if (component instanceof AmbientPanel) {
            component.setForeground(FIELD_FG);
        } else if (component instanceof JPanel) {
            ((JPanel) component).setOpaque(false);
        }

        if (component instanceof JLabel) {
            JLabel label = (JLabel) component;
            boolean sectionTitle = Boolean.TRUE.equals(label.getClientProperty("sectionTitle"));
            boolean muted = Boolean.TRUE.equals(label.getClientProperty("mutedLabel"));
            int style = (sectionTitle || label.getFont().isBold()) ? Font.BOLD : Font.PLAIN;
            int size = sectionTitle ? 16 : Math.max(13, label.getFont().getSize());
            label.setFont(new Font(FONT, style, size));
            if (label == statusLabel) {
                label.setForeground(ACCENT_HOVER);
            } else if (label == statsLabel) {
                label.setForeground(SUCCESS);
            } else if (muted) {
                label.setForeground(MUTED_FG);
            } else {
                label.setForeground(FIELD_FG);
            }
        } else if (component instanceof JTextArea) {
            styleTextComponent((JTextArea) component);
        } else if (component instanceof JTextField) {
            styleTextComponent((JTextField) component);
        } else if (component instanceof JCheckBox) {
            JCheckBox checkBox = (JCheckBox) component;
            checkBox.setOpaque(false);
            checkBox.setForeground(FIELD_FG);
            checkBox.setFocusPainted(false);
        } else if (component instanceof JComboBox) {
            JComboBox<?> comboBox = (JComboBox<?>) component;
            comboBox.setBackground(FIELD_BG);
            comboBox.setForeground(FIELD_FG);
            comboBox.setFont(new Font(FONT, Font.PLAIN, 13));
            ((JComboBox) comboBox).setRenderer(new ModernComboRenderer());
            comboBox.setUI(new ModernComboBoxUI());
        } else if (component instanceof JProgressBar) {
            JProgressBar bar = (JProgressBar) component;
            bar.setBackground(FIELD_BG);
            bar.setForeground(ACCENT);
            bar.setBorder(BorderFactory.createLineBorder(PANEL_BORDER));
            bar.setFont(new Font(FONT, Font.BOLD, 12));
        } else if (component instanceof JScrollPane) {
            JScrollPane scrollPane = (JScrollPane) component;
            scrollPane.setOpaque(false);
            scrollPane.getViewport().setOpaque(false);
            if (!(scrollPane.getBorder() instanceof javax.swing.border.TitledBorder)) {
                scrollPane.setBorder(BorderFactory.createLineBorder(PANEL_BORDER));
            }
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);
            styleScrollBar(scrollPane.getVerticalScrollBar());
            styleScrollBar(scrollPane.getHorizontalScrollBar());
        }

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                applyModernTheme(child);
            }
        }
    }

    private static void styleTextComponent(JTextComponent textComponent) {
        textComponent.setBackground(FIELD_BG);
        textComponent.setForeground(FIELD_FG);
        textComponent.setCaretColor(ACCENT_HOVER);
        textComponent.setSelectionColor(ACCENT);
        textComponent.setSelectedTextColor(Color.WHITE);
        textComponent.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PANEL_BORDER),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        textComponent.setFont(new Font(FONT, Font.PLAIN, Math.max(13, textComponent.getFont().getSize())));
    }

    private static void styleScrollBar(JScrollBar scrollBar) {
        if (scrollBar == null) {
            return;
        }
        scrollBar.setUI(new ModernScrollBarUI());
        scrollBar.setBackground(new Color(FIELD_BG.getRed(), FIELD_BG.getGreen(), FIELD_BG.getBlue()));
        scrollBar.setPreferredSize(new Dimension(11, 11));
        scrollBar.setUnitIncrement(16);
    }

    private static void stylePrimaryButton(JButton button) {
        styleButton(button, ACCENT, Color.WHITE, ACCENT_HOVER);
    }

    private static void styleSecondaryButton(JButton button) {
        Color background = isCurrentLightTheme()
            ? new Color(250, 251, 253)
            : new Color(39, 40, 45);
        styleButton(button, background, FIELD_FG, PANEL_BORDER);
    }

    private static void styleDangerButton(JButton button) {
        Color background = isCurrentLightTheme()
            ? new Color(255, 244, 245)
            : new Color(62, 37, 42);
        Color foreground = isCurrentLightTheme() ? new Color(142, 48, 58) : new Color(255, 222, 226);
        styleButton(button, background, foreground, DANGER);
    }

    private static void styleButton(JButton button, Color background, Color foreground, Color border) {
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setOpaque(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(border),
            BorderFactory.createEmptyBorder(7, 12, 7, 12)));
        button.setFont(new Font(FONT, Font.BOLD, 12));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static boolean isCurrentLightTheme() {
        int brightness = (BG.getRed() + BG.getGreen() + BG.getBlue()) / 3;
        return brightness > 120;
    }

    private static class ModernButton extends JButton {
        ModernButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = getBackground();
                if (!isEnabled()) {
                    base = isCurrentLightTheme() ? new Color(228, 231, 237) : new Color(39, 40, 45);
                } else if (getModel().isPressed()) {
                    base = base.darker();
                } else if (getModel().isRollover()) {
                    base = brighten(base, isCurrentLightTheme() ? 8 : 14);
                }

                g.setColor(base);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                Color border = isEnabled() ? getBorderColor() : PANEL_BORDER;
                g.setColor(border);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);

                FontMetrics metrics = g.getFontMetrics(getFont());
                String text = getText();
                int x = (getWidth() - metrics.stringWidth(text)) / 2;
                int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                g.setFont(getFont());
                g.setColor(isEnabled() ? getForeground() : MUTED_FG);
                g.drawString(text, x, y);
            } finally {
                g.dispose();
            }
        }

        private Color getBorderColor() {
            Border border = getBorder();
            if (border instanceof javax.swing.border.CompoundBorder) {
                Border outside = ((javax.swing.border.CompoundBorder) border).getOutsideBorder();
                if (outside instanceof javax.swing.border.LineBorder) {
                    return ((javax.swing.border.LineBorder) outside).getLineColor();
                }
            } else if (border instanceof javax.swing.border.LineBorder) {
                return ((javax.swing.border.LineBorder) border).getLineColor();
            }
            return PANEL_BORDER;
        }
    }

    private static Color brighten(Color color, int amount) {
        return new Color(
            Math.min(255, color.getRed() + amount),
            Math.min(255, color.getGreen() + amount),
            Math.min(255, color.getBlue() + amount),
            color.getAlpha());
    }

    private static class ModernProgressBar extends JProgressBar {
        ModernProgressBar(int min, int max) {
            super(min, max);
            setOpaque(false);
            setBorderPainted(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = getWidth();
                int height = getHeight();
                g.setColor(FIELD_BG);
                g.fillRoundRect(0, 0, width, height, 16, 16);

                double ratio = (getValue() - getMinimum()) / (double) Math.max(1, getMaximum() - getMinimum());
                int fillWidth = Math.max(0, (int) Math.round(width * ratio));
                if (fillWidth > 0) {
                    GradientPaint fill = new GradientPaint(0, 0, ACCENT,
                        width, 0, ACCENT_HOVER);
                    g.setPaint(fill);
                    g.fillRoundRect(0, 0, fillWidth, height, 16, 16);
                    g.setColor(new Color(255, 255, 255, 30));
                    g.drawLine(8, 3, Math.max(8, fillWidth - 8), 3);
                }

                g.setColor(PANEL_BORDER);
                g.drawRoundRect(0, 0, width - 1, height - 1, 16, 16);

                String text = getString();
                if (text != null && !text.isEmpty()) {
                    g.setFont(getFont());
                    FontMetrics metrics = g.getFontMetrics();
                    int x = (width - metrics.stringWidth(text)) / 2;
                    int y = (height - metrics.getHeight()) / 2 + metrics.getAscent();
                    g.setColor(isCurrentLightTheme() ? FIELD_FG : Color.WHITE);
                    g.drawString(text, x, y);
                }
            } finally {
                g.dispose();
            }
        }
    }

    private static class ModernComboBox<E> extends JComboBox<E> {
        ModernComboBox(E[] items) {
            super(items);
            setOpaque(false);
            setBackground(FIELD_BG);
            setForeground(FIELD_FG);
            setFont(new Font(FONT, Font.PLAIN, 13));
            setBorder(BorderFactory.createLineBorder(PANEL_BORDER));
            setRenderer(new ModernComboRenderer());
            setUI(new ModernComboBoxUI());
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setForeground(FIELD_FG);
            setBackground(FIELD_BG);
            repaint();
        }
    }

    private static class ModernComboRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(6, 9, 6, 9));
            label.setFont(new Font(FONT, Font.PLAIN, 13));
            label.setBackground(isSelected ? ACCENT : FIELD_BG);
            label.setForeground(isSelected ? Color.WHITE : FIELD_FG);
            return label;
        }
    }

    private static class ModernComboBoxUI extends BasicComboBoxUI {
        @Override
        protected JButton createArrowButton() {
            JButton button = new ModernButton("v");
            styleButton(button, FIELD_BG, MUTED_FG, PANEL_BORDER);
            button.setPreferredSize(new Dimension(34, 28));
            return button;
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(FIELD_BG);
                g2.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 10, 10);
                g2.setColor(PANEL_BORDER);
                g2.drawRoundRect(bounds.x, bounds.y, Math.max(0, bounds.width - 1), Math.max(0, bounds.height - 1), 10, 10);
            } finally {
                g2.dispose();
            }
        }

        @Override
        protected ComboPopup createPopup() {
            BasicComboPopup popup = new BasicComboPopup(comboBox) {
                @Override
                protected JScrollPane createScroller() {
                    JScrollPane scroller = super.createScroller();
                    scroller.setBorder(BorderFactory.createLineBorder(PANEL_BORDER));
                    scroller.getViewport().setBackground(FIELD_BG);
                    styleScrollBar(scroller.getVerticalScrollBar());
                    return scroller;
                }
            };
            popup.getList().setBackground(FIELD_BG);
            popup.getList().setForeground(FIELD_FG);
            popup.getList().setSelectionBackground(ACCENT);
            popup.getList().setSelectionForeground(Color.WHITE);
            popup.setBorder(BorderFactory.createLineBorder(PANEL_BORDER));
            return popup;
        }
    }

    private static class ModernScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            trackColor = FIELD_BG;
            thumbColor = new Color(
                Math.min(255, FIELD_BG.getRed() + (isCurrentLightTheme() ? 24 : 54)),
                Math.min(255, FIELD_BG.getGreen() + (isCurrentLightTheme() ? 24 : 54)),
                Math.min(255, FIELD_BG.getBlue() + (isCurrentLightTheme() ? 24 : 54)));
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        private JButton createZeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
            g.setColor(FIELD_BG);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle bounds) {
            if (bounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(thumbColor);
                g2.fillRoundRect(bounds.x + 2, bounds.y + 2,
                    Math.max(4, bounds.width - 4), Math.max(4, bounds.height - 4), 8, 8);
            } finally {
                g2.dispose();
            }
        }
    }

    private static class GlassPanel extends JPanel {
        GlassPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(isCurrentLightTheme() ? new Color(0, 0, 0, 18) : new Color(0, 0, 0, 42));
                g.fillRoundRect(4, 6, Math.max(0, getWidth() - 8), Math.max(0, getHeight() - 8), 20, 20);
                g.setColor(PANEL_BG);
                g.fillRoundRect(0, 0, getWidth() - 2, getHeight() - 3, 20, 20);
                g.setColor(isCurrentLightTheme() ? new Color(255, 255, 255, 150) : new Color(255, 255, 255, 14));
                g.drawLine(18, 1, Math.max(18, getWidth() - 20), 1);
                g.setColor(PANEL_BORDER);
                g.drawRoundRect(0, 0, getWidth() - 3, getHeight() - 4, 20, 20);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private static class AmbientPanel extends JPanel {
        private double phase;

        AmbientPanel(LayoutManager layout) {
            super(layout);
            setOpaque(true);
            setBackground(BG);
            javax.swing.Timer timer = new javax.swing.Timer(33, e -> {
                phase += 0.0028;
                repaint();
            });
            timer.setCoalesce(true);
            timer.start();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                int width = getWidth();
                int height = getHeight();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color baseA = isLightTheme() ? new Color(236, 238, 242) : new Color(12, 13, 16);
                Color baseB = isLightTheme() ? new Color(247, 248, 250) : new Color(20, 21, 25);
                GradientPaint base = new GradientPaint(0, 0, baseA, width, height, baseB);
                g.setPaint(base);
                g.fillRect(0, 0, width, height);

                paintLightRibbon(g, width, height, 0, new Color(92, 94, 102, isLightTheme() ? 18 : 22));
                paintLightRibbon(g, width, height, 1, new Color(58, 61, 69, isLightTheme() ? 12 : 18));
                paintLightRibbon(g, width, height, 2, new Color(120, 123, 132, isLightTheme() ? 12 : 14));

                g.setColor(new Color(255, 255, 255, isLightTheme() ? 12 : 3));
                for (int y = 0; y < height; y += 56) {
                    g.drawLine(0, y, width, y);
                }
            } finally {
                g.dispose();
            }
        }

        private void paintLightRibbon(Graphics2D g, int width, int height, int index, Color color) {
            double local = phase + index * 1.7;
            int ribbonWidth = width + 260;
            int ribbonHeight = 74 + index * 18;
            int centerX = width / 2 + (int) (Math.sin(local * 0.48) * 110);
            int centerY = (int) (height * (0.20 + index * 0.22) + Math.cos(local * 0.42) * 42);
            double angle = -0.22 + index * 0.14 + Math.sin(local * 0.24) * 0.035;

            Graphics2D copy = (Graphics2D) g.create();
            try {
                copy.rotate(angle, centerX, centerY);
                for (int layer = 5; layer >= 0; layer--) {
                    int inset = layer * 28;
                    int alpha = Math.max(2, color.getAlpha() - layer * 3);
                    copy.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
                    copy.fillRoundRect(centerX - ribbonWidth / 2 - inset,
                        centerY - ribbonHeight / 2 - inset,
                        ribbonWidth + inset * 2,
                        ribbonHeight + inset * 2,
                        160,
                        160);
                }

                copy.setColor(new Color(255, 255, 255, isLightTheme() ? 8 : 5));
                copy.drawLine(centerX - ribbonWidth / 2, centerY, centerX + ribbonWidth / 2, centerY + 24);
            } finally {
                copy.dispose();
            }
        }

        private boolean isLightTheme() {
            int brightness = (BG.getRed() + BG.getGreen() + BG.getBlue()) / 3;
            return brightness > 120;
        }
    }
    
    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Input Error", JOptionPane.ERROR_MESSAGE);
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            
            new ScannerGUI().setVisible(true);
        });
    }
}
