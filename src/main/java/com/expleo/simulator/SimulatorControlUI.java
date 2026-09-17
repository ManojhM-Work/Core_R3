package com.expleo.simulator;

import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.logging.Logger;

public class SimulatorControlUI extends JFrame {

    private final Color bgColor = new Color(244, 245, 248);
    private final Color panelColor = new Color(255, 255, 255);
    private final Color fgColor = new Color(12, 8, 20);
    private final Color accentColor = new Color(128, 92, 229);
    private final Color successColor = new Color(0, 214, 57);
    private final Color dangerColor = new Color(230, 57, 70);
    private final Color warningColor = new Color(244, 162, 97);

    private JLabel statusLabel;
    private JTextField hostField;
    private JTextField portField;
    private JTextField userField;
    private JPasswordField passField;
    private JTextField inQField;
    private JTextField outQField;
    private JTextField tsField;
    private JPasswordField tsPassField;
    private JComboBox<String> responseBox;
    private JCheckBox perfModeCheck;
    private JCheckBox enableStsCheck;
    private JTextField cprLimitField;

    private JButton btnStart;
    private JButton btnStop;
    private JTextArea logArea;

    private java.util.List<JmsListenerWorker> workers = new java.util.ArrayList<>();
    private java.util.List<Thread> workerThreads = new java.util.ArrayList<>();

    public SimulatorControlUI() {
        setTitle("Expleo PT AMQ Pacs Simulator");
        setSize(750, 720);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null); // Center on screen
        getContentPane().setBackground(bgColor);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClosing();
            }
        });

        setupUI();
    }

    private void onClosing() {
        appendLog("Window closing... Ensuring shutdown...");
        stopServer();
        for (Thread t : workerThreads) {
            if (t != null && t.isAlive()) {
                try {
                    t.join(1000);
                } catch (InterruptedException ignored) {
                }
            }
        }
        dispose();
        System.exit(0);
    }

    private void setupUI() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(bgColor);
        mainPanel.setBorder(new EmptyBorder(25, 25, 25, 25));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(bgColor);
        JLabel titleLabel = new JLabel("Expleo PT AMQ Pacs Simulator");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(accentColor);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        statusPanel.setBackground(bgColor);
        JLabel statusTitle = new JLabel("Status: ");
        statusTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusLabel = new JLabel("● STOPPED");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusLabel.setForeground(dangerColor);
        statusPanel.add(statusTitle);
        statusPanel.add(statusLabel);
        headerPanel.add(statusPanel, BorderLayout.EAST);

        mainPanel.add(headerPanel);
        mainPanel.add(Box.createVerticalStrut(15));

        // Settings Panel
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBackground(panelColor);
        configPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(224, 224, 224)),
                "Connection Settings", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12), accentColor));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 10, 8, 10);

        JSONObject mqConfig = Config.getJSONObject("mq_config");
        boolean isDevMode = "DEV".equalsIgnoreCase((String) Config.get("mode"));

        addFormRow(configPanel, "Broker (Host:Port)", gbc, 0, mqConfig.optString("host", "localhost"),
                mqConfig.optString("port", "61616"), isDevMode);
        addFormRow(configPanel, "Username", gbc, 1, mqConfig.optString("username", "admin"), null, isDevMode);
        addFormRow(configPanel, "Password", gbc, 2, mqConfig.optString("password", "master"), null, isDevMode);
        addFormRow(configPanel, "Input Queue", gbc, 3, mqConfig.optString("input_queue", ""), null, isDevMode);
        addFormRow(configPanel, "Output Queue", gbc, 4, mqConfig.optString("output_queue", ""), null, isDevMode);
        addFormRow(configPanel, "TrustStore Path", gbc, 5, mqConfig.optString("trust_store_path", ""), null, isDevMode);
        addFormRow(configPanel, "TrustStore Pass", gbc, 6, mqConfig.optString("trust_store_pass", ""), null, isDevMode);

        mainPanel.add(configPanel);
        mainPanel.add(Box.createVerticalStrut(15));

        // Options Panel
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        optionsPanel.setBackground(panelColor);
        optionsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        if (isDevMode) {
            JButton btnSave = new JButton("Save Config");
            btnSave.setForeground(new Color(0, 0, 150)); // Dark green
            btnSave.setFont(new Font("Segoe UI", Font.BOLD, 12));
            btnSave.setPreferredSize(new Dimension(130, 30));
//            btnSave.setBackground(accentColor);
//            btnSave.setForeground(Color.WHITE);
            btnSave.addActionListener(e -> saveConfig());
            optionsPanel.add(btnSave);
        }

        JLabel responseLbl = new JLabel("Response:");
        optionsPanel.add(responseLbl);

        String[] stsOptions = { "ACCP", "RJCT", "PDNG" };
        responseBox = new JComboBox<>(stsOptions);
        responseBox.setPreferredSize(new Dimension(80, 25));
        String currentSts = Config.getJSONObject("response_defaults").optString("TxSts", "ACCP");
        responseBox.setSelectedItem(currentSts);
        responseBox.addActionListener(e -> {
            JSONObject defs = Config.getJSONObject("response_defaults");
            defs.put("TxSts", responseBox.getSelectedItem());
            Config.set("response_defaults", defs);
            Config.saveConfig();
            appendLog("Config Update: Response Message changed to '" + responseBox.getSelectedItem() + "'");
        });
        optionsPanel.add(responseBox);

        perfModeCheck = new JCheckBox("High-Perf Mode (Skip I/O)", Config.getBoolean("high_perf_mode", false));
        perfModeCheck.setBackground(panelColor);
        perfModeCheck.addActionListener(e -> {
            Config.set("high_perf_mode", perfModeCheck.isSelected());
            Config.saveConfig();
            appendLog("Config Update: High-Perf Mode set to '" + perfModeCheck.isSelected() + "'");
        });
        optionsPanel.add(Box.createHorizontalStrut(30));
        optionsPanel.add(perfModeCheck);

        enableStsCheck = new JCheckBox("Enable STS", Config.getBoolean("enable_sts", true));
        enableStsCheck.setBackground(panelColor);
        enableStsCheck.addActionListener(e -> {
            Config.set("enable_sts", enableStsCheck.isSelected());
            Config.saveConfig();
            appendLog("Config Update: Enable STS set to '" + enableStsCheck.isSelected() + "'");
        });
        optionsPanel.add(Box.createHorizontalStrut(30));
        optionsPanel.add(enableStsCheck);

        mainPanel.add(optionsPanel);
        mainPanel.add(Box.createVerticalStrut(15));

        // Batch Options Panel (CPR Limit)
        JPanel batchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 8));
        batchPanel.setBackground(panelColor);
        batchPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(224, 224, 224)),
                "Batch Limit (CPR)", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12), accentColor));

        batchPanel.add(new JLabel("CPR Batch Limit:"));
        cprLimitField = new JTextField(String.valueOf(Config.getInt("cpr_batch_limit", 1000)), 6);
        cprLimitField.addActionListener(e -> updateBatchLimits());
        batchPanel.add(cprLimitField);

        JButton btnApplyBatch = new JButton("Apply Limit");
        btnApplyBatch.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnApplyBatch.addActionListener(e -> updateBatchLimits());
        batchPanel.add(Box.createHorizontalStrut(15));
        batchPanel.add(btnApplyBatch);

        mainPanel.add(batchPanel);
        mainPanel.add(Box.createVerticalStrut(15));

        // Action Buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionPanel.setBackground(bgColor);

        btnStart = new JButton("START SERVER");
        btnStart.setForeground(new Color(0, 150, 0)); // Dark green
        btnStart.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnStart.setPreferredSize(new Dimension(150, 40));
        btnStart.addActionListener(e -> startServer());
        actionPanel.add(btnStart);

        btnStop = new JButton("STOP SERVER");
        btnStop.setForeground(dangerColor); // Red
        btnStop.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnStop.setPreferredSize(new Dimension(150, 40));
        btnStop.setEnabled(false);
        btnStop.addActionListener(e -> stopServer());
        actionPanel.add(btnStop);

        mainPanel.add(actionPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // Log Area
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBackground(bgColor);

        JPanel logHeader = new JPanel(new BorderLayout());
        logHeader.setBackground(bgColor);
        JLabel logTitle = new JLabel("Activity Log");
        logTitle.setForeground(new Color(100, 100, 100));
        JButton btnClear = new JButton("Clear");
        btnClear.addActionListener(e -> logArea.setText(""));
        logHeader.add(logTitle, BorderLayout.WEST);
        logHeader.add(btnClear, BorderLayout.EAST);
        logPanel.add(logHeader, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        logPanel.add(scrollPane, BorderLayout.CENTER);

        mainPanel.add(logPanel);

        setContentPane(mainPanel);
    }

    private void addFormRow(JPanel panel, String labelText, GridBagConstraints gbc, int row, String val1, String val2,
                            boolean isDevMode) {
        gbc.gridy = row;

        gbc.gridx = 0;
        gbc.weightx = 0.2;
        JLabel lbl = new JLabel(labelText);
        lbl.setHorizontalAlignment(SwingConstants.RIGHT);
        panel.add(lbl, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.8;
        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        inputPanel.setBackground(panelColor);

        if (labelText.contains("Password") || labelText.contains("Pass")) {
            JPasswordField pf = new JPasswordField(val1, 35);
            pf.setEditable(isDevMode);
            inputPanel.add(pf);
            if (row == 2)
                passField = pf;
            else
                tsPassField = pf;
        } else {
            JTextField tf1 = new JTextField(val1, val2 != null ? 25 : 35);
            tf1.setEditable(isDevMode);
            inputPanel.add(tf1);
            if (row == 0)
                hostField = tf1;
            else if (row == 1)
                userField = tf1;
            else if (row == 3)
                inQField = tf1;
            else if (row == 4)
                outQField = tf1;
            else if (row == 5)
                tsField = tf1;

            if (val2 != null) {
                inputPanel.add(new JLabel(" : "));
                portField = new JTextField(val2, 8);
                portField.setEditable(isDevMode);
                inputPanel.add(portField);
            }
        }

        panel.add(inputPanel, gbc);
    }

    private void updateBatchLimits() {
        try {
            int cpr = Integer.parseInt(cprLimitField.getText().trim());
            if (cpr <= 0) {
                throw new NumberFormatException("Limit must be a positive integer");
            }
            Config.set("cpr_batch_limit", cpr);
            Config.saveConfig();
            appendLog("Config Update: CPR Batch Limit set to " + cpr);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Batch limit must be a valid positive integer.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveConfig() {
        JSONObject mq = Config.getJSONObject("mq_config");
        mq.put("host", hostField.getText());
        mq.put("port", portField.getText());
        mq.put("username", userField.getText());
        mq.put("password", new String(passField.getPassword()));
        mq.put("input_queue", inQField.getText());
        mq.put("output_queue", outQField.getText());
        mq.put("trust_store_path", tsField.getText());
        mq.put("trust_store_pass", new String(tsPassField.getPassword()));

        Config.set("mq_config", mq);
        updateBatchLimits();
        Config.saveConfig();

        appendLog("Note: Restart server to apply MQ changes.");
        JOptionPane.showMessageDialog(this, "Configuration saved! Please Stop and Start Server.");
    }

    public synchronized void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("> " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void startServer() {
        appendLog("Initializing Expleo PT AMQ Connections for multiple participants...");
        try {
            stopServer();
            workers.clear();
            workerThreads.clear();

            String inQueuesStr = inQField.getText().trim();
            String outQueuesStr = outQField.getText().trim();

            String[] inQueues = inQueuesStr.split(",");
            String[] outQueues = outQueuesStr.split(",");

            if (inQueues.length != outQueues.length || inQueuesStr.isEmpty()) {
                throw new Exception("Input and Output queues must be provided and equal in number (comma-separated).");
            }

            for (int i = 0; i < inQueues.length; i++) {
                JmsListenerWorker w = new JmsListenerWorker(this, inQueues[i], outQueues[i]);
                workers.add(w);
                Thread t = new Thread(w);
                t.setDaemon(true);
                workerThreads.add(t);
                t.start();
                appendLog("Launched thread for Participant: " + inQueues[i] + " -> " + outQueues[i]);
            }

            statusLabel.setText("● ACTIVE (" + workers.size() + " queues)");
            statusLabel.setForeground(successColor);

            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            appendLog("All server threads launched. Listening for messages...");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not start the MQ listener(s):\n" + e.getMessage(),
                    "Startup Error", JOptionPane.ERROR_MESSAGE);
            appendLog("Error: " + e.getMessage());
            stopServer();
        }
    }

    public void stopServer() {
        appendLog("Requesting servers shutdown...");
        for (JmsListenerWorker w : workers) {
            if (w != null)
                w.stopConnections();
        }
        workers.clear();
        workerThreads.clear();
        CprBatchProcessor.flushRemaining(LoggerHelper.getLogger("UI"));
        statusLabel.setText("● STOPPED");
        statusLabel.setForeground(dangerColor);
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
    }

    public static void main(String[] args) {
        try {
            // Use the system look and feel for a more modern appearance than Metal
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        Config.loadConfig();
        Logger logger = LoggerHelper.getLogger("UI");
        logger.info("Starting Expleo PT AMQ Simulator UI");

        SwingUtilities.invokeLater(() -> {
            SimulatorControlUI ui = new SimulatorControlUI();
            ui.setVisible(true);
        });
    }
}
