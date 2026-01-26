import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class ScannerGUI extends JFrame {
    private final JTextArea ipArea;
    private final JTextField portField;
    private final JTextField amountField;
    private final JComboBox<ScannerService.ScanSpeed> speedCombo;
    private final JButton scanButton;
    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final JLabel statsLabel;
    
    private ScannerService currentScanner;
    
    public ScannerGUI() {
        super("MC Scanner - Professional Edition v2.0.2");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.NORTHWEST;
        JLabel ipLabel = new JLabel("Server IPs:");
        inputPanel.add(ipLabel, gbc);
        
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.BOTH;
        ipArea = new JTextArea(5, 20);
        ipArea.setLineWrap(true);
        ipArea.setWrapStyleWord(true);
        ipArea.setText("localhost");
        ipArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(ipArea);
        scrollPane.setPreferredSize(new Dimension(250, 120));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        
        // Enable mouse wheel scrolling
        ipArea.addMouseWheelListener(e -> {
            JScrollBar scrollBar = scrollPane.getVerticalScrollBar();
            int scrollAmount = e.getUnitsToScroll() * 3;
            scrollBar.setValue(scrollBar.getValue() + scrollAmount);
        });
        
        inputPanel.add(scrollPane, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel formatLabel = new JLabel("<html><small>Format: IP or IP1-IP10 or IP1 IP2 IP3</small></html>");
        formatLabel.setForeground(Color.GRAY);
        inputPanel.add(formatLabel, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2;
        inputPanel.add(new JLabel("Start Port:"), gbc);
        
        gbc.gridx = 1;
        portField = new JTextField("25565", 10);
        portField.setFont(new Font("Monospaced", Font.PLAIN, 14));
        inputPanel.add(portField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 3;
        inputPanel.add(new JLabel("Scan Amount:"), gbc);
        
        gbc.gridx = 1;
        amountField = new JTextField("100", 10);
        amountField.setFont(new Font("Monospaced", Font.PLAIN, 14));
        inputPanel.add(amountField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 4;
        inputPanel.add(new JLabel("Speed:"), gbc);
        
        gbc.gridx = 1;
        speedCombo = new JComboBox<>(ScannerService.ScanSpeed.values());
        speedCombo.setSelectedItem(ScannerService.ScanSpeed.FAST);
        inputPanel.add(speedCombo, gbc);
        
        mainPanel.add(inputPanel, BorderLayout.NORTH);
        
        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressPanel.setBorder(BorderFactory.createTitledBorder("Scan Progress"));
        
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Ready to scan");
        progressBar.setPreferredSize(new Dimension(400, 25));
        progressPanel.add(progressBar, BorderLayout.NORTH);
        
        statusLabel = new JLabel("Status: Waiting for input...", SwingConstants.CENTER);
        statusLabel.setForeground(new Color(0, 100, 200));
        progressPanel.add(statusLabel, BorderLayout.CENTER);
        
        statsLabel = new JLabel("Online: 0 | WhiteList: 0", SwingConstants.CENTER);
        statsLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        statsLabel.setForeground(new Color(0, 150, 0));
        progressPanel.add(statsLabel, BorderLayout.SOUTH);
        
        mainPanel.add(progressPanel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        scanButton = new JButton("Start Scan");
        scanButton.setPreferredSize(new Dimension(150, 35));
        scanButton.setFont(new Font("Arial", Font.BOLD, 14));
        scanButton.addActionListener(e -> handleScanButton());
        buttonPanel.add(scanButton);
        
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        pack();
        setLocationRelativeTo(null);
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
        } catch (NumberFormatException e) {
            showError("Invalid amount");
            return;
        }
        
        setInputsEnabled(false);
        scanButton.setText("Cancel Scan");
        scanButton.setBackground(new Color(255, 100, 100));
        progressBar.setValue(0);
        progressBar.setString("Initializing...");
        statusLabel.setText("Status: Starting scan for " + ips.size() + " IP(s)...");
        statsLabel.setText("Online: 0 | WhiteList: 0");
        
        ScannerService.ScanSpeed speed = (ScannerService.ScanSpeed) speedCombo.getSelectedItem();
        
        currentScanner = new ScannerService(ips, port, amount, speed);
        
        new Thread(() -> {
            try {
                currentScanner.scan(progress -> {
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(progress.getProgress());
                        progressBar.setString(progress.getScanned() + " / " + progress.getTotal());
                        
                        statsLabel.setText(String.format("Online: %d | WhiteList: %d",
                            progress.getOnlineTotal(), progress.getWhitelistTotal()));
                        
                        ServerInfo info = progress.getLastResult();
                        if (info.isOnline()) {
                            statusLabel.setText(String.format("Found: %s:%d [%s] WL:%s",
                                info.getIp(), info.getPort(), info.getVersion(),
                                info.hasWhitelist() ? "YES" : "NO"));
                        } else {
                            statusLabel.setText("Scanning: " + info.getIp() + ":" + info.getPort());
                        }
                    });
                });
                
                String timestamp = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
                File outputFile = new File("MCScanner_Results_" + timestamp + ".txt");
                currentScanner.saveResults(outputFile);
                
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(100);
                    progressBar.setString("Scan Complete!");
                    statusLabel.setText("✓ Results saved successfully!");
                    
                    JOptionPane.showMessageDialog(this,
                        "Scan completed!\n\nScanned " + ips.size() + " IP(s)\nResults saved to:\n" + outputFile.getAbsolutePath(),
                        "Scan Complete",
                        JOptionPane.INFORMATION_MESSAGE);
                    resetUI();
                });
                
            } catch (InterruptedException e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Scan cancelled by user");
                    progressBar.setString("Cancelled");
                    resetUI();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    showError("Scan error: " + e.getMessage());
                    resetUI();
                });
                e.printStackTrace();
            }
        }, "ScannerThread").start();
    }
    
    private void cancelScan() {
        if (currentScanner != null) {
            currentScanner.cancel();
            statusLabel.setText("Cancelling scan...");
            scanButton.setEnabled(false);
        }
    }
    
    private void resetUI() {
        setInputsEnabled(true);
        scanButton.setText("Start Scan");
        scanButton.setBackground(null);
        scanButton.setEnabled(true);
        currentScanner = null;
    }
    
    private void setInputsEnabled(boolean enabled) {
        ipArea.setEnabled(enabled);
        portField.setEnabled(enabled);
        amountField.setEnabled(enabled);
        speedCombo.setEnabled(enabled);
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