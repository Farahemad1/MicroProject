package gui;

import core.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;

/**
 * Modern, enhanced GUI for Tomasulo Simulator with improved UX and visual design
 */
public class ModernSwingGUI {
    
    private final StateViewModel viewModel = new StateViewModel();
    
    // Main UI Components
    private JFrame frame;
    private JTabbedPane mainTabs;
    private JLabel cycleLabel;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JTextField fileField;
    private JButton playBtn;
    private Timer autoTimer;
    
    // Tables
    private JTable intRegTable, fpRegTable, rsTable, lbTable, sbTable, cacheTable, summaryTable;
    private JList<String> instrList;
    
    // Parameter controls
    private JSpinner spFpAddRS, spFpMulRS, spIntAluRS, spLoadBuf, spStoreBuf;
    private JSpinner spFpAddLat, spFpMulLat, spFpDivLat, spIntAluLat, spLoadLat, spStoreLat;
    private JSpinner spCacheSize, spBlockSize, spAssoc, spCacheHitLat, spCacheMissPen;
    
    // Statistics panels
    private JLabel cacheHitRateLabel, totalCyclesLabel, completedInstrLabel, ipcLabel;
    
    // Color scheme - Modern dark blue theme
    private static final Color PRIMARY = new Color(41, 128, 185);
    private static final Color SECONDARY = new Color(52, 73, 94);
    private static final Color ACCENT = new Color(46, 204, 113);
    private static final Color WARNING = new Color(230, 126, 34);
    private static final Color DANGER = new Color(231, 76, 60);
    private static final Color BG_LIGHT = new Color(236, 240, 241);
    private static final Color BG_WHITE = Color.WHITE;
    private static final Color TEXT_PRIMARY = new Color(44, 62, 80);
    private static final Color TEXT_SECONDARY = new Color(127, 140, 141);
    
    public void initAndShow() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            // Customize UI defaults
            UIManager.put("TabbedPane.selected", BG_WHITE);
            UIManager.put("TabbedPane.contentAreaColor", BG_WHITE);
        } catch (Exception ignored) {}
        
        frame = new JFrame("Tomasulo Simulator - Modern Edition");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1600, 950);
        frame.setLayout(new BorderLayout(0, 0));
        frame.setBackground(BG_LIGHT);
        
        // Create menu bar
        createMenuBar();
        
        // Create top toolbar
        JPanel toolbar = createToolbar();
        frame.add(toolbar, BorderLayout.NORTH);
        
        // Create main tabbed interface
        mainTabs = new JTabbedPane();
        mainTabs.setFont(new Font("Segoe UI", Font.BOLD, 12));
        mainTabs.setBackground(BG_WHITE);
        
        // Tab 1: Execution View
        mainTabs.addTab("  Execution  ", createExecutionTab());
        
        // Tab 2: Statistics
        mainTabs.addTab("  Statistics  ", createStatisticsTab());
        
        // Tab 3: Configuration
        mainTabs.addTab("  Configuration  ", createConfigTab());
        
        frame.add(mainTabs, BorderLayout.CENTER);
        
        // Create bottom status bar
        JPanel statusBar = createStatusBar();
        frame.add(statusBar, BorderLayout.SOUTH);
        
        // Setup actions
        setupActions();
        
        // Center on screen
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
    
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        
        // File menu
        JMenu fileMenu = new JMenu("File");
        JMenuItem openItem = new JMenuItem("Open Program...", KeyEvent.VK_O);
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        openItem.addActionListener(e -> browseFile());
        JMenuItem exportItem = new JMenuItem("Export Results...", KeyEvent.VK_E);
        exportItem.addActionListener(e -> exportSummaryToCSV());
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(openItem);
        fileMenu.add(exportItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        
        // Simulation menu
        JMenu simMenu = new JMenu("Simulation");
        JMenuItem stepItem = new JMenuItem("Step Forward", KeyEvent.VK_N);
        stepItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, 0));
        stepItem.addActionListener(e -> stepForward());
        JMenuItem prevItem = new JMenuItem("Step Backward", KeyEvent.VK_P);
        prevItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, 0));
        prevItem.addActionListener(e -> stepBackward());
        JMenuItem resetItem = new JMenuItem("Reset", KeyEvent.VK_R);
        resetItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK));
        resetItem.addActionListener(e -> resetSimulation());
        simMenu.add(stepItem);
        simMenu.add(prevItem);
        simMenu.addSeparator();
        simMenu.add(resetItem);
        
        // Help menu
        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> showAbout());
        helpMenu.add(aboutItem);
        
        menuBar.add(fileMenu);
        menuBar.add(simMenu);
        menuBar.add(helpMenu);
        
        frame.setJMenuBar(menuBar);
    }
    
    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(10, 10));
        toolbar.setBackground(BG_WHITE);
        toolbar.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 2, 0, new Color(189, 195, 199)),
            new EmptyBorder(15, 20, 15, 20)
        ));
        
        // Left side - File selection
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftPanel.setBackground(BG_WHITE);
        
        JLabel fileLabel = new JLabel("Program File:");
        fileLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        fileLabel.setForeground(TEXT_PRIMARY);
        
        fileField = new JTextField(40);
        fileField.setFont(new Font("Consolas", Font.PLAIN, 12));
        fileField.setBorder(new CompoundBorder(
            new LineBorder(new Color(189, 195, 199), 1, true),
            new EmptyBorder(5, 10, 5, 10)
        ));
        
        JButton browseBtn = createModernButton("Browse", PRIMARY, "folder");
        JButton loadBtn = createModernButton("Load", ACCENT, "play");
        
        leftPanel.add(fileLabel);
        leftPanel.add(fileField);
        leftPanel.add(browseBtn);
        leftPanel.add(loadBtn);
        
        // Center - Control buttons
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        centerPanel.setBackground(BG_WHITE);
        
        JButton prevBtn = createModernButton("◄ Previous", SECONDARY, "back");
        JButton stepBtn = createModernButton("Step ►", PRIMARY, "forward");
        playBtn = createModernButton("▶ Play", ACCENT, "play");
        JButton resetBtn = createModernButton("⟲ Reset", WARNING, "reset");
        
        centerPanel.add(prevBtn);
        centerPanel.add(stepBtn);
        centerPanel.add(playBtn);
        centerPanel.add(resetBtn);
        
        // Right side - Cycle display
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.setBackground(BG_WHITE);
        
        cycleLabel = new JLabel("Cycle: 0");
        cycleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        cycleLabel.setForeground(PRIMARY);
        cycleLabel.setBorder(new CompoundBorder(
            new LineBorder(PRIMARY, 2, true),
            new EmptyBorder(8, 20, 8, 20)
        ));
        
        rightPanel.add(cycleLabel);
        
        toolbar.add(leftPanel, BorderLayout.WEST);
        toolbar.add(centerPanel, BorderLayout.CENTER);
        toolbar.add(rightPanel, BorderLayout.EAST);
        
        // Setup button actions
        browseBtn.addActionListener(e -> browseFile());
        loadBtn.addActionListener(e -> loadProgram());
        prevBtn.addActionListener(e -> stepBackward());
        stepBtn.addActionListener(e -> stepForward());
        resetBtn.addActionListener(e -> resetSimulation());
        playBtn.addActionListener(e -> toggleAutoRun());
        
        return toolbar;
    }
    
    private JPanel createExecutionTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG_LIGHT);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Left: Registers and RS
        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setBackground(BG_LIGHT);
        
        JPanel intRegPanel = createRegisterPanel(true);
        intRegTable.setToolTipText("Click on Value cell to edit (when not in use)");
        leftCol.add(createCardWithButton("Integer Registers", intRegPanel, "Edit", e -> editRegisterValues(true)));
        leftCol.add(Box.createVerticalStrut(12));
        
        JPanel fpRegPanel = createRegisterPanel(false);
        fpRegTable.setToolTipText("Click on Value cell to edit (when not in use)");
        leftCol.add(createCardWithButton("FP Registers", fpRegPanel, "Edit", e -> editRegisterValues(false)));
        leftCol.add(Box.createVerticalStrut(12));
        
        JLabel rsInfo = new JLabel("<html><b>Reservation Stations</b> <span style='color:#7f8c8d; font-size:10px;'>(INT ALU: Integer ALU operations)</span></html>");
        rsInfo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JPanel rsInfoPanel = new JPanel(new BorderLayout());
        rsInfoPanel.setBackground(BG_WHITE);
        rsInfoPanel.add(rsInfo, BorderLayout.NORTH);
        rsInfoPanel.add(createRSPanel(), BorderLayout.CENTER);
        
        JPanel rsCard = new JPanel(new BorderLayout(0, 12));
        rsCard.setBackground(BG_WHITE);
        rsCard.setBorder(new CompoundBorder(
            new CompoundBorder(
                new LineBorder(new Color(189, 195, 199), 1, true),
                new LineBorder(BG_WHITE, 2)
            ),
            new EmptyBorder(12, 12, 12, 12)
        ));
        rsCard.add(rsInfoPanel);
        
        leftCol.add(rsCard);
        
        // Right: Instructions, Buffers, Cache
        JPanel rightCol = new JPanel();
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
        rightCol.setBackground(BG_LIGHT);
        
        rightCol.add(createCard("Instructions", createInstructionPanel()));
        rightCol.add(Box.createVerticalStrut(12));
        rightCol.add(createBuffersPanel());
        rightCol.add(Box.createVerticalStrut(12));
        rightCol.add(createCard("Cache", createCachePanel()));
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftCol, rightCol);
        splitPane.setDividerLocation(700);
        splitPane.setBorder(null);
        splitPane.setBackground(BG_LIGHT);
        
        panel.add(splitPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createStatisticsTab() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BG_LIGHT);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // Top: Metric cards
        JPanel statsRow = new JPanel(new GridLayout(1, 4, 15, 0));
        statsRow.setBackground(BG_LIGHT);
        
        cacheHitRateLabel = new JLabel("0%");
        JPanel card1 = createMetricCardWithLabel("Cache Hit Rate", cacheHitRateLabel, PRIMARY);
        
        completedInstrLabel = new JLabel("0 / 0");
        JPanel card2 = createMetricCardWithLabel("Instructions Completed", completedInstrLabel, ACCENT);
        
        ipcLabel = new JLabel("0.00");
        JPanel card3 = createMetricCardWithLabel("IPC (Instr/Cycle)", ipcLabel, WARNING);
        
        totalCyclesLabel = new JLabel("0");
        JPanel card4 = createMetricCardWithLabel("Total Cycles", totalCyclesLabel, SECONDARY);
        
        statsRow.add(card1);
        statsRow.add(card2);
        statsRow.add(card3);
        statsRow.add(card4);
        
        panel.add(statsRow, BorderLayout.NORTH);
        
        // Center: Summary table
        JPanel summaryCard = createCard("Instruction Timing Summary", createSummaryPanel());
        panel.add(summaryCard, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createConfigTab() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BG_LIGHT);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        JPanel configContent = new JPanel();
        configContent.setLayout(new BoxLayout(configContent, BoxLayout.Y_AXIS));
        configContent.setBackground(BG_LIGHT);
        
        configContent.add(createCompactConfigPanel());
        
        JScrollPane scroll = new JScrollPane(configContent);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBackground(BG_LIGHT);
        
        panel.add(scroll, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createExecutionPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG_LIGHT);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Left: Pipeline state
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setBackground(BG_LIGHT);
        
        JPanel intRegPanel = createRegisterPanel(true);
        intRegTable.setToolTipText("Click on Value cell to edit (when not in use)");
        JPanel intRegCard = createCardWithButton("Integer Registers", intRegPanel, "Edit Values", e -> editRegisterValues(true));
        leftPanel.add(intRegCard);
        leftPanel.add(Box.createVerticalStrut(10));
        
        JPanel fpRegPanel = createRegisterPanel(false);
        fpRegTable.setToolTipText("Click on Value cell to edit (when not in use)");
        JPanel fpRegCard = createCardWithButton("Floating-Point Registers", fpRegPanel, "Edit Values", e -> editRegisterValues(false));
        leftPanel.add(fpRegCard);
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(createCard("Reservation Stations", createRSPanel()));
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(createCard("Load/Store Buffers", createBuffersPanel()));
        
        // Right: Instructions and cache
        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setBackground(BG_LIGHT);
        
        rightPanel.add(createCard("Instruction Timing", createInstructionPanel()), BorderLayout.CENTER);
        rightPanel.add(createCard("Data Cache State", createCachePanel()), BorderLayout.SOUTH);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(leftPanel), rightPanel);
        splitPane.setDividerLocation(850);
        splitPane.setBorder(null);
        splitPane.setBackground(BG_LIGHT);
        
        panel.add(splitPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createStatisticsPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BG_LIGHT);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // Top: Key metrics
        JPanel metricsPanel = new JPanel(new GridLayout(1, 4, 15, 0));
        metricsPanel.setBackground(BG_LIGHT);
        
        // Create metric cards with labels
        cacheHitRateLabel = new JLabel("0%");
        JPanel card1 = createMetricCardWithLabel("Cache Hit Rate", cacheHitRateLabel, PRIMARY);
        
        completedInstrLabel = new JLabel("0 / 0");
        JPanel card2 = createMetricCardWithLabel("Instructions", completedInstrLabel, ACCENT);
        
        ipcLabel = new JLabel("0.00");
        JPanel card3 = createMetricCardWithLabel("IPC", ipcLabel, WARNING);
        
        totalCyclesLabel = new JLabel("0");
        JPanel card4 = createMetricCardWithLabel("Total Cycles", totalCyclesLabel, SECONDARY);
        
        metricsPanel.add(card1);
        metricsPanel.add(card2);
        metricsPanel.add(card3);
        metricsPanel.add(card4);
        
        panel.add(metricsPanel, BorderLayout.NORTH);
        
        // Center: Detailed instruction summary
        JPanel summaryPanel = createCard("Instruction Summary", createSummaryPanel());
        panel.add(summaryPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createConfigurationPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BG_LIGHT);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        JPanel configContent = new JPanel();
        configContent.setLayout(new BoxLayout(configContent, BoxLayout.Y_AXIS));
        configContent.setBackground(BG_LIGHT);
        
        // Reservation Station Sizes
        JPanel rsConfig = createConfigSection("Reservation Station Configuration");
        rsConfig.add(createSpinnerRow("FP Add/Sub Stations:", spFpAddRS = createSpinner(3, 1, 16)));
        rsConfig.add(createSpinnerRow("FP Mul/Div Stations:", spFpMulRS = createSpinner(2, 1, 16)));
        rsConfig.add(createSpinnerRow("Integer ALU Stations:", spIntAluRS = createSpinner(3, 1, 16)));
        rsConfig.add(createSpinnerRow("Load Buffers:", spLoadBuf = createSpinner(3, 1, 16)));
        rsConfig.add(createSpinnerRow("Store Buffers:", spStoreBuf = createSpinner(3, 1, 16)));
        configContent.add(rsConfig);
        configContent.add(Box.createVerticalStrut(15));
        
        // Latencies
        JPanel latConfig = createConfigSection("Execution Latencies (cycles)");
        latConfig.add(createSpinnerRow("FP Add/Sub:", spFpAddLat = createSpinner(2, 1, 100)));
        latConfig.add(createSpinnerRow("FP Multiply:", spFpMulLat = createSpinner(4, 1, 100)));
        latConfig.add(createSpinnerRow("FP Divide:", spFpDivLat = createSpinner(40, 1, 500)));
        latConfig.add(createSpinnerRow("Integer ALU:", spIntAluLat = createSpinner(1, 1, 100)));
        latConfig.add(createSpinnerRow("Load (base):", spLoadLat = createSpinner(2, 1, 100)));
        latConfig.add(createSpinnerRow("Store (base):", spStoreLat = createSpinner(2, 1, 100)));
        configContent.add(latConfig);
        configContent.add(Box.createVerticalStrut(15));
        
        // Cache Configuration
        JPanel cacheConfig = createConfigSection("Cache Configuration");
        cacheConfig.add(createSpinnerRow("Cache Size (bytes):", spCacheSize = createSpinner(1024, 16, 65536)));
        cacheConfig.add(createSpinnerRow("Block Size (bytes):", spBlockSize = createSpinner(16, 4, 1024)));
        cacheConfig.add(createSpinnerRow("Associativity:", spAssoc = createSpinner(2, 1, 16)));
        cacheConfig.add(createSpinnerRow("Hit Latency:", spCacheHitLat = createSpinner(1, 1, 100)));
        cacheConfig.add(createSpinnerRow("Miss Penalty:", spCacheMissPen = createSpinner(10, 1, 500)));
        configContent.add(cacheConfig);
        
        JScrollPane scrollPane = new JScrollPane(configContent);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Apply button at bottom
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setBackground(BG_LIGHT);
        JButton applyBtn = createModernButton("Apply Configuration", PRIMARY, "check");
        applyBtn.addActionListener(e -> {
            applyParamsToViewModel();
            JOptionPane.showMessageDialog(frame, "Configuration updated. Click 'Reset' to apply changes.", 
                "Configuration Applied", JOptionPane.INFORMATION_MESSAGE);
        });
        btnPanel.add(applyBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout(10, 0));
        statusBar.setBackground(SECONDARY);
        statusBar.setBorder(new EmptyBorder(8, 20, 8, 20));
        
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(BG_WHITE);
        
        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(200, 18));
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        
        JLabel versionLabel = new JLabel("Tomasulo Simulator v2.0");
        versionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        versionLabel.setForeground(TEXT_SECONDARY);
        
        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(progressBar, BorderLayout.CENTER);
        statusBar.add(versionLabel, BorderLayout.EAST);
        
        return statusBar;
    }
    
    // Helper methods for creating UI components
    
    private JPanel createCard(String title, JPanel content) {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(BG_WHITE);
        card.setBorder(new CompoundBorder(
            new CompoundBorder(
                new LineBorder(new Color(189, 195, 199), 1, true),
                new LineBorder(BG_WHITE, 2)
            ),
            new EmptyBorder(12, 12, 12, 12)
        ));
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        titleLabel.setForeground(TEXT_PRIMARY);
        
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        
        return card;
    }
    
    private JPanel createCardWithButton(String title, JPanel content, String buttonText, ActionListener action) {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(BG_WHITE);
        card.setBorder(new CompoundBorder(
            new CompoundBorder(
                new LineBorder(new Color(189, 195, 199), 1, true),
                new LineBorder(BG_WHITE, 2)
            ),
            new EmptyBorder(12, 12, 12, 12)
        ));
        
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(BG_WHITE);
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        titleLabel.setForeground(TEXT_PRIMARY);
        
        JButton btn = new JButton(buttonText);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btn.setBackground(PRIMARY);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(5, 10, 5, 10));
        btn.addActionListener(action);
        
        titlePanel.add(titleLabel, BorderLayout.WEST);
        titlePanel.add(btn, BorderLayout.EAST);
        
        card.add(titlePanel, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        
        return card;
    }
    
    private JPanel createMetricCardWithLabel(String label, JLabel valueLabel, Color color) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(BG_WHITE);
        card.setBorder(new CompoundBorder(
            new CompoundBorder(
                new LineBorder(color, 3, true),
                new LineBorder(new Color(245, 245, 245), 2)
            ),
            new EmptyBorder(15, 15, 15, 15)
        ));
        
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG_WHITE);
        
        JLabel lblLabel = new JLabel(label);
        lblLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblLabel.setForeground(TEXT_SECONDARY);
        lblLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        valueLabel.setForeground(color);
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        content.add(lblLabel);
        content.add(Box.createVerticalStrut(5));
        content.add(valueLabel);
        
        card.add(content);
        
        return card;
    }
    
    private JPanel createConfigSection(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_WHITE);
        panel.setBorder(new CompoundBorder(
            new TitledBorder(new LineBorder(new Color(189, 195, 199), 1), title,
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13), TEXT_PRIMARY),
            new EmptyBorder(10, 10, 10, 10)
        ));
        return panel;
    }
    
    private JPanel createSpinnerRow(String label, JSpinner spinner) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        row.setBackground(BG_WHITE);
        
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lbl.setPreferredSize(new Dimension(200, 25));
        
        spinner.setPreferredSize(new Dimension(80, 30));
        spinner.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        
        row.add(lbl);
        row.add(spinner);
        
        return row;
    }
    
    private JSpinner createSpinner(int initial, int min, int max) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(initial, min, max, 1));
        return spinner;
    }
    
    private JButton createModernButton(String text, Color bgColor, String icon) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(10, 20, 10, 20));
        
        // Hover effect
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(bgColor.brighter());
            }
            public void mouseExited(MouseEvent e) {
                btn.setBackground(bgColor);
            }
        });
        
        return btn;
    }
    
    private JPanel createRegisterPanel(boolean isInt) {
        JTable table;
        if (isInt) {
            intRegTable = new JTable();
            table = intRegTable;
        } else {
            fpRegTable = new JTable();
            table = fpRegTable;
        }
        
        styleModernTable(table);
        table.setFont(new Font("Consolas", Font.PLAIN, 12));
        table.setRowHeight(28);
        
        return wrapInScrollPane(table, 250);
    }
    
    private JPanel createRSPanel() {
        rsTable = new JTable();
        styleModernTable(rsTable);
        rsTable.setFont(new Font("Consolas", Font.PLAIN, 11));
        rsTable.setRowHeight(26);
        return wrapInScrollPane(rsTable, 180);
    }
    
    private JPanel createBuffersPanel() {
        JPanel outerPanel = new JPanel(new BorderLayout());
        outerPanel.setBackground(BG_WHITE);
        outerPanel.setBorder(new CompoundBorder(
            new CompoundBorder(
                new LineBorder(new Color(189, 195, 199), 1, true),
                new LineBorder(BG_WHITE, 2)
            ),
            new EmptyBorder(12, 12, 12, 12)
        ));
        
        JLabel title = new JLabel("Load & Store Buffers");
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(TEXT_PRIMARY);
        
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_WHITE);
        
        lbTable = new JTable();
        sbTable = new JTable();
        styleModernTable(lbTable);
        styleModernTable(sbTable);
        lbTable.setFont(new Font("Consolas", Font.PLAIN, 11));
        sbTable.setFont(new Font("Consolas", Font.PLAIN, 11));
        lbTable.setRowHeight(26);
        sbTable.setRowHeight(26);
        
        JPanel lbCard = new JPanel(new BorderLayout());
        lbCard.setBackground(BG_WHITE);
        lbCard.setBorder(new TitledBorder(new LineBorder(new Color(189, 195, 199), 1), "Load Buffers",
            TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 11), TEXT_PRIMARY));
        lbCard.add(wrapInScrollPane(lbTable, 100));
        
        JPanel sbCard = new JPanel(new BorderLayout());
        sbCard.setBackground(BG_WHITE);
        sbCard.setBorder(new TitledBorder(new LineBorder(new Color(189, 195, 199), 1), "Store Buffers",
            TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 11), TEXT_PRIMARY));
        sbCard.add(wrapInScrollPane(sbTable, 100));
        
        panel.add(lbCard);
        panel.add(Box.createVerticalStrut(10));
        panel.add(sbCard);
        
        outerPanel.add(title, BorderLayout.NORTH);
        outerPanel.add(panel, BorderLayout.CENTER);
        
        return outerPanel;
    }
    
    private JPanel createInstructionPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_WHITE);
        
        DefaultListModel<String> model = new DefaultListModel<>();
        instrList = new JList<>(model);
        instrList.setFont(new Font("Consolas", Font.PLAIN, 12));
        instrList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        instrList.setBorder(new EmptyBorder(8, 12, 8, 12));
        
        JScrollPane scroll = new JScrollPane(instrList);
        scroll.setBorder(new LineBorder(new Color(189, 195, 199), 1));
        scroll.setPreferredSize(new Dimension(scroll.getPreferredSize().width, 250));
        
        panel.add(scroll);
        
        return panel;
    }
    
    private JPanel createCachePanel() {
        cacheTable = new JTable();
        styleModernTable(cacheTable);
        cacheTable.setFont(new Font("Consolas", Font.PLAIN, 11));
        cacheTable.setRowHeight(26);
        return wrapInScrollPane(cacheTable, 150);
    }
    
    private JPanel createSummaryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBackground(BG_WHITE);
        
        summaryTable = new JTable();
        styleModernTable(summaryTable);
        summaryTable.setAutoCreateRowSorter(true);
        summaryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        summaryTable.setFont(new Font("Consolas", Font.PLAIN, 11));
        summaryTable.setRowHeight(26);
        
        // Link to instruction list
        summaryTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int viewRow = summaryTable.getSelectedRow();
            if (viewRow >= 0) {
                int modelRow = summaryTable.convertRowIndexToModel(viewRow);
                Object cell = summaryTable.getModel().getValueAt(modelRow, 0);
                if (cell != null) {
                    String s = cell.toString();
                    int dot = s.indexOf('.');
                    if (dot > 0) {
                        try {
                            int idx = Integer.parseInt(s.substring(0, dot).trim()) - 1;
                            if (idx >= 0 && idx < instrList.getModel().getSize()) {
                                instrList.setSelectedIndex(idx);
                                instrList.ensureIndexIsVisible(idx);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        });
        
        JScrollPane scroll = new JScrollPane(summaryTable);
        scroll.setBorder(new LineBorder(new Color(189, 195, 199), 1));
        
        JButton exportBtn = new JButton("Export");
        exportBtn.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        exportBtn.setBackground(PRIMARY);
        exportBtn.setForeground(Color.WHITE);
        exportBtn.setFocusPainted(false);
        exportBtn.setBorder(new EmptyBorder(3, 8, 3, 8));
        exportBtn.addActionListener(e -> exportSummaryToCSV());
        
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        toolbar.setBackground(BG_WHITE);
        toolbar.add(exportBtn);
        
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createCompactConfigPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_WHITE);
        
        // RS Configuration
        JPanel rsPanel = new JPanel(new GridLayout(5, 2, 5, 2));
        rsPanel.setBackground(BG_WHITE);
        rsPanel.setBorder(new TitledBorder(new LineBorder(new Color(189, 195, 199), 1), "Reservation Stations",
            TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 11), TEXT_PRIMARY));
        
        rsPanel.add(createCompactLabel("FP Add:"));
        rsPanel.add(spFpAddRS = createCompactSpinner(3, 1, 16));
        rsPanel.add(createCompactLabel("FP Mul:"));
        rsPanel.add(spFpMulRS = createCompactSpinner(2, 1, 16));
        rsPanel.add(createCompactLabel("Int ALU:"));
        rsPanel.add(spIntAluRS = createCompactSpinner(3, 1, 16));
        rsPanel.add(createCompactLabel("Load Buf:"));
        rsPanel.add(spLoadBuf = createCompactSpinner(3, 1, 16));
        rsPanel.add(createCompactLabel("Store Buf:"));
        rsPanel.add(spStoreBuf = createCompactSpinner(3, 1, 16));
        
        // Latency Configuration
        JPanel latPanel = new JPanel(new GridLayout(6, 2, 5, 2));
        latPanel.setBackground(BG_WHITE);
        latPanel.setBorder(new TitledBorder(new LineBorder(new Color(189, 195, 199), 1), "Latencies",
            TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 11), TEXT_PRIMARY));
        
        latPanel.add(createCompactLabel("FP Add:"));
        latPanel.add(spFpAddLat = createCompactSpinner(2, 1, 100));
        latPanel.add(createCompactLabel("FP Mul:"));
        latPanel.add(spFpMulLat = createCompactSpinner(4, 1, 100));
        latPanel.add(createCompactLabel("FP Div:"));
        latPanel.add(spFpDivLat = createCompactSpinner(40, 1, 500));
        latPanel.add(createCompactLabel("Int ALU:"));
        latPanel.add(spIntAluLat = createCompactSpinner(1, 1, 100));
        latPanel.add(createCompactLabel("Load:"));
        latPanel.add(spLoadLat = createCompactSpinner(2, 1, 100));
        latPanel.add(createCompactLabel("Store:"));
        latPanel.add(spStoreLat = createCompactSpinner(2, 1, 100));
        
        // Cache Configuration
        JPanel cachePanel = new JPanel(new GridLayout(5, 2, 5, 2));
        cachePanel.setBackground(BG_WHITE);
        cachePanel.setBorder(new TitledBorder(new LineBorder(new Color(189, 195, 199), 1), "Cache",
            TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 11), TEXT_PRIMARY));
        
        cachePanel.add(createCompactLabel("Size:"));
        cachePanel.add(spCacheSize = createCompactSpinner(1024, 16, 65536));
        cachePanel.add(createCompactLabel("Block:"));
        cachePanel.add(spBlockSize = createCompactSpinner(16, 4, 1024));
        cachePanel.add(createCompactLabel("Assoc:"));
        cachePanel.add(spAssoc = createCompactSpinner(2, 1, 16));
        cachePanel.add(createCompactLabel("Hit Lat:"));
        cachePanel.add(spCacheHitLat = createCompactSpinner(1, 1, 100));
        cachePanel.add(createCompactLabel("Miss Pen:"));
        cachePanel.add(spCacheMissPen = createCompactSpinner(10, 1, 500));
        
        // Apply button
        JButton applyBtn = new JButton("Apply Config");
        applyBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        applyBtn.setBackground(ACCENT);
        applyBtn.setForeground(Color.WHITE);
        applyBtn.setFocusPainted(false);
        applyBtn.setBorderPainted(false);
        applyBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        applyBtn.setBorder(new EmptyBorder(8, 15, 8, 15));
        applyBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        applyBtn.addActionListener(e -> {
            applyParamsToViewModel();
            statusLabel.setText("Configuration updated. Reset to apply.");
        });
        
        panel.add(rsPanel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(latPanel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(cachePanel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(applyBtn);
        
        return panel;
    }
    
    private JLabel createCompactLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        return label;
    }
    
    private JSpinner createCompactSpinner(int initial, int min, int max) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(initial, min, max, 1));
        spinner.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(5);
        return spinner;
    }
    
    private JPanel wrapInScrollPane(JTable table, int height) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_WHITE);
        
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(new Color(189, 195, 199), 1));
        if (height > 0) {
            scroll.setPreferredSize(new Dimension(scroll.getPreferredSize().width, height));
        }
        
        panel.add(scroll);
        return panel;
    }
    
    private JPanel wrapTableNoScroll(JTable table) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_WHITE);
        panel.setBorder(new LineBorder(new Color(189, 195, 199), 1));
        
        // Add table header
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBackground(BG_WHITE);
        tablePanel.add(table.getTableHeader(), BorderLayout.NORTH);
        tablePanel.add(table, BorderLayout.CENTER);
        
        panel.add(tablePanel);
        return panel;
    }
    
    private void styleModernTable(JTable table) {
        table.setFont(new Font("Consolas", Font.PLAIN, 12));
        table.setRowHeight(28);
        table.setGridColor(new Color(230, 230, 230));
        table.setBackground(BG_WHITE);
        table.setSelectionBackground(new Color(184, 207, 229));
        table.setSelectionForeground(Color.BLACK);
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(8, 4));
        
        // Style header
        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.setBackground(new Color(240, 243, 244));
        header.setForeground(TEXT_PRIMARY);
        header.setBorder(new LineBorder(new Color(189, 195, 199), 1));
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 32));
    }
    
    // Action handlers
    
    private void setupActions() {
        // Keyboard shortcuts
        KeyStroke stepKey = KeyStroke.getKeyStroke(KeyEvent.VK_N, 0);
        KeyStroke prevKey = KeyStroke.getKeyStroke(KeyEvent.VK_P, 0);
        
        frame.getRootPane().registerKeyboardAction(e -> stepForward(),
            stepKey, JComponent.WHEN_IN_FOCUSED_WINDOW);
        frame.getRootPane().registerKeyboardAction(e -> stepBackward(),
            prevKey, JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
    
    private void browseFile() {
        JFileChooser fc = new JFileChooser(new File("."));
        fc.setDialogTitle("Select Assembly Program");
        fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".txt");
            }
            public String getDescription() {
                return "Assembly Files (*.txt)";
            }
        });
        
        int result = fc.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            fileField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }
    
    private void loadProgram() {
        applyParamsToViewModel();
        String path = fileField.getText();
        if (path == null || path.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please select a program file first.",
                "No File Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        try {
            statusLabel.setText("Loading program...");
            viewModel.loadProgram(new File(path));
            viewModel.createEngineWithCurrentConfig();
            refreshAllViews();
            statusLabel.setText("Program loaded successfully: " + new File(path).getName());
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, 
                "Failed to load program:\n" + ex.getMessage(),
                "Load Error", JOptionPane.ERROR_MESSAGE);
            statusLabel.setText("Error loading program");
        }
    }
    
    private void stepForward() {
        if (!viewModel.hasProgram()) {
            JOptionPane.showMessageDialog(frame, "Please load a program first.",
                "No Program", JOptionPane.WARNING_MESSAGE);
            return;
        }
        viewModel.nextCycle();
        refreshAllViews();
        statusLabel.setText("Stepped forward to cycle " + viewModel.getCurrentState().getCycleNumber());
    }
    
    private void stepBackward() {
        if (!viewModel.hasProgram()) {
            JOptionPane.showMessageDialog(frame, "Please load a program first.",
                "No Program", JOptionPane.WARNING_MESSAGE);
            return;
        }
        viewModel.previousCycle();
        refreshAllViews();
        statusLabel.setText("Stepped back to cycle " + viewModel.getCurrentState().getCycleNumber());
    }
    
    private void resetSimulation() {
        if (!viewModel.hasProgram()) {
            JOptionPane.showMessageDialog(frame, "Please load a program first.",
                "No Program", JOptionPane.WARNING_MESSAGE);
            return;
        }
        applyParamsToViewModel();
        viewModel.createEngineWithCurrentConfig();
        refreshAllViews();
        statusLabel.setText("Simulation reset");
    }
    
    private void toggleAutoRun() {
        if (autoTimer != null && autoTimer.isRunning()) {
            autoTimer.stop();
            playBtn.setText("▶ Play");
            statusLabel.setText("Auto-run stopped");
        } else {
            if (!viewModel.hasProgram()) {
                JOptionPane.showMessageDialog(frame, "Please load a program first.",
                    "No Program", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            autoTimer = new Timer(300, e -> {
                viewModel.nextCycle();
                refreshAllViews();
            });
            autoTimer.start();
            playBtn.setText("⏸ Pause");
            statusLabel.setText("Auto-running...");
        }
    }
    
    private void applyParamsToViewModel() {
        viewModel.numFpAddRS = (Integer) spFpAddRS.getValue();
        viewModel.numFpMulRS = (Integer) spFpMulRS.getValue();
        viewModel.numIntAluRS = (Integer) spIntAluRS.getValue();
        viewModel.numLoadBuffers = (Integer) spLoadBuf.getValue();
        viewModel.numStoreBuffers = (Integer) spStoreBuf.getValue();
        
        viewModel.fpAddLatency = (Integer) spFpAddLat.getValue();
        viewModel.fpMulLatency = (Integer) spFpMulLat.getValue();
        viewModel.fpDivLatency = (Integer) spFpDivLat.getValue();
        viewModel.intAluLatency = (Integer) spIntAluLat.getValue();
        viewModel.loadLatencyBase = (Integer) spLoadLat.getValue();
        viewModel.storeLatencyBase = (Integer) spStoreLat.getValue();
        
        viewModel.cacheSize = (Integer) spCacheSize.getValue();
        viewModel.blockSize = (Integer) spBlockSize.getValue();
        viewModel.associativity = (Integer) spAssoc.getValue();
        viewModel.cacheHitLatency = (Integer) spCacheHitLat.getValue();
        viewModel.cacheMissPenalty = (Integer) spCacheMissPen.getValue();
    }
    
    private void refreshAllViews() {
        CycleState s = viewModel.getCurrentState();
        if (s == null) return;
        
        cycleLabel.setText("Cycle: " + s.getCycleNumber());
        
        // Update register tables
        updateRegisterTable(intRegTable, s.getIntRegs(), true);
        updateRegisterTable(fpRegTable, s.getFpRegs(), false);
        
        // Update RS table
        updateRSTable(s);
        
        // Update buffer tables
        updateLoadBufferTable(s);
        updateStoreBufferTable(s);
        
        // Update cache table
        updateCacheTable(s);
        
        // Update instruction list
        updateInstructionList(s);
        
        // Update summary table
        updateSummaryTable(s);
        
        // Update statistics
        updateStatistics(s);
    }
    
    private void updateRegisterTable(JTable table, long[] regs, boolean isInt) {
        String[] cols = {"Register", "Value", "Owner"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int row, int col) {
                // Only Value column (col 1) is editable, and only when Owner is empty
                if (col != 1) return false;
                Object owner = getValueAt(row, 2);
                return owner == null || owner.toString().isEmpty();
            }
        };
        
        for (int i = 0; i < regs.length; i++) {
            String owner = "";
            if (viewModel.getRegisterStatus() != null) {
                owner = isInt ? viewModel.getRegisterStatus().getIntOwner(i) :
                               viewModel.getRegisterStatus().getFpOwner(i);
            }
            model.addRow(new Object[]{
                (isInt ? "R" : "F") + i,
                Long.toString(regs[i]),
                owner == null ? "" : owner
            });
        }
        
        table.setModel(model);
        
        // Configure renderer to highlight editable cells with alternating rows
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                
                if (!isSelected) {
                    // Highlight editable Value cells (column 1) with light yellow background
                    if (column == 1 && tbl.isCellEditable(row, column)) {
                        c.setBackground(new Color(255, 255, 220)); // Light yellow
                        c.setForeground(new Color(0, 100, 0)); // Dark green text
                    } else {
                        // Alternating row colors
                        c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(250, 250, 252));
                        c.setForeground(Color.BLACK);
                    }
                }
                
                return c;
            }
        });
        
        // Add listener to handle value changes
        model.addTableModelListener(e -> {
            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE) {
                int row = e.getFirstRow();
                int col = e.getColumn();
                if (col == 1 && row >= 0) { // Value column changed
                    try {
                        String regName = model.getValueAt(row, 0).toString();
                        String valueStr = model.getValueAt(row, 1).toString();
                        long value = Long.parseLong(valueStr);
                        
                        int regIndex = Integer.parseInt(regName.substring(1));
                        RegisterFile rf = viewModel.getRegisterFile();
                        if (rf != null) {
                            if (isInt) {
                                rf.setInt(regIndex, value);
                            } else {
                                rf.setFp(regIndex, value);
                            }
                            statusLabel.setText("Updated " + regName + " = " + value);
                        }
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(frame,
                            "Invalid number format. Please enter a valid integer.",
                            "Input Error", JOptionPane.ERROR_MESSAGE);
                        refreshAllViews(); // Reset to original value
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        statusLabel.setText("Error updating register");
                    }
                }
            }
        });
    }
    
    private void updateRSTable(CycleState s) {
        String[] cols = {"Name", "Busy", "Op", "Vj", "Vk", "Qj", "Qk", "Dest", "Cycles"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        
        for (ReservationStation rs : s.getFpAddStations()) {
            model.addRow(new Object[]{rs.getName(), rs.isBusy(), rs.getOp(),
                rs.getVj(), rs.getVk(), rs.getQj(), rs.getQk(), rs.getDest(), rs.getRemainingCycles()});
        }
        for (ReservationStation rs : s.getFpMulStations()) {
            model.addRow(new Object[]{rs.getName(), rs.isBusy(), rs.getOp(),
                rs.getVj(), rs.getVk(), rs.getQj(), rs.getQk(), rs.getDest(), rs.getRemainingCycles()});
        }
        for (ReservationStation rs : s.getIntAluStations()) {
            model.addRow(new Object[]{rs.getName(), rs.isBusy(), rs.getOp(),
                rs.getVj(), rs.getVk(), rs.getQj(), rs.getQk(), rs.getDest(), rs.getRemainingCycles()});
        }
        
        rsTable.setModel(model);
    }
    
    private void updateLoadBufferTable(CycleState s) {
        String[] cols = {"Name", "Busy", "Address", "AddrQ", "Dest", "Cycles"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        
        for (LoadBufferEntry lb : s.getLoadBuffers()) {
            model.addRow(new Object[]{lb.getName(), lb.isBusy(), lb.getAddress(),
                lb.getAddressQ(), lb.getDestReg(), lb.getRemainingCycles()});
        }
        
        lbTable.setModel(model);
    }
    
    private void updateStoreBufferTable(CycleState s) {
        String[] cols = {"Name", "Busy", "Address", "AddrQ", "ValueQ"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        
        for (StoreBufferEntry sb : s.getStoreBuffers()) {
            model.addRow(new Object[]{sb.getName(), sb.isBusy(), sb.getAddress(),
                sb.getAddressQ(), sb.getValueQ()});
        }
        
        sbTable.setModel(model);
    }
    
    private void updateCacheTable(CycleState s) {
        String[] cols = {"Set", "Way", "Valid", "Tag", "LRU"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        
        CacheLine[][] cache = s.getCacheSnapshot();
        if (cache != null) {
            for (int set = 0; set < cache.length; set++) {
                for (int way = 0; way < cache[set].length; way++) {
                    CacheLine line = cache[set][way];
                    if (line != null) {
                        model.addRow(new Object[]{set, way, line.isValid(),
                            line.isValid() ? line.getTag() : "-", line.getLruCounter()});
                    } else {
                        model.addRow(new Object[]{set, way, false, "-", "-"});
                    }
                }
            }
        }
        
        cacheTable.setModel(model);
    }
    
    private void updateInstructionList(CycleState s) {
        DefaultListModel<String> model = new DefaultListModel<>();
        
        for (Instruction instr : s.getInstructionsWithTiming()) {
            model.addElement(String.format("%d: %-30s [I=%d, S=%d, E=%d, W=%d]",
                instr.getPcIndex(),
                instr.getRawText(),
                instr.getIssueCycle(),
                instr.getStartExecCycle(),
                instr.getEndExecCycle(),
                instr.getWriteBackCycle()));
        }
        
        instrList.setModel(model);
    }
    
    private void updateSummaryTable(CycleState s) {
        String[] cols = {"Instruction", "Issue", "Exec Start", "Exec End", "Write Back"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        
        for (Instruction instr : s.getInstructionsWithTiming()) {
            model.addRow(new Object[]{
                (instr.getPcIndex() + 1) + ". " + instr.getRawText(),
                instr.getIssueCycle() > 0 ? instr.getIssueCycle() : "-",
                instr.getStartExecCycle() > 0 ? instr.getStartExecCycle() : "-",
                instr.getEndExecCycle() > 0 ? instr.getEndExecCycle() : "-",
                instr.getWriteBackCycle() > 0 ? instr.getWriteBackCycle() : "-"
            });
        }
        
        summaryTable.setModel(model);
        
        // Apply custom renderer for completed instructions with alternating rows
        summaryTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                try {
                    int modelRow = table.convertRowIndexToModel(row);
                    Object wb = table.getModel().getValueAt(modelRow, 4);
                    int currentCycle = s.getCycleNumber();
                    
                    if (wb != null && !"-".equals(wb.toString())) {
                        int wbCycle = Integer.parseInt(wb.toString());
                        if (!isSelected) {
                            if (wbCycle <= currentCycle) {
                                c.setBackground(new Color(200, 255, 200)); // Light green for completed
                            } else {
                                c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(250, 250, 252));
                            }
                        }
                    } else {
                        if (!isSelected) c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(250, 250, 252));
                    }
                } catch (Exception ignored) {}
                
                return c;
            }
        });
    }
    
    private void updateStatistics(CycleState s) {
        // Calculate cache hit rate
        if (viewModel.getCache() != null) {
            long hits = viewModel.getCache().getHits();
            long misses = viewModel.getCache().getMisses();
            long total = hits + misses;
            double hitRate = total > 0 ? (100.0 * hits / total) : 0.0;
            cacheHitRateLabel.setText(String.format("%.1f%%", hitRate));
        }
        
        // Calculate instruction completion
        int total = s.getInstructionsWithTiming().size();
        int completed = 0;
        for (Instruction instr : s.getInstructionsWithTiming()) {
            if (instr.getWriteBackCycle() > 0 && instr.getWriteBackCycle() <= s.getCycleNumber()) {
                completed++;
            }
        }
        completedInstrLabel.setText(completed + " / " + total);
        
        // Calculate IPC
        int cycles = s.getCycleNumber();
        double ipc = cycles > 0 ? ((double) completed / cycles) : 0.0;
        ipcLabel.setText(String.format("%.2f", ipc));
        
        // Total cycles
        totalCyclesLabel.setText(String.valueOf(cycles));
    }
    
    private void exportSummaryToCSV() {
        if (summaryTable.getModel().getRowCount() == 0) {
            JOptionPane.showMessageDialog(frame, "No data to export.",
                "Export Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser fc = new JFileChooser(new File("."));
        fc.setDialogTitle("Save Summary as CSV");
        fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".csv");
            }
            public String getDescription() {
                return "CSV Files (*.csv)";
            }
        });
        
        int result = fc.showSaveDialog(frame);
        if (result != JFileChooser.APPROVE_OPTION) return;
        
        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new File(file.getAbsolutePath() + ".csv");
        }
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            DefaultTableModel model = (DefaultTableModel) summaryTable.getModel();
            
            // Write header
            for (int c = 0; c < model.getColumnCount(); c++) {
                if (c > 0) pw.print(",");
                pw.print("\"" + model.getColumnName(c) + "\"");
            }
            pw.println();
            
            // Write rows
            for (int r = 0; r < model.getRowCount(); r++) {
                for (int c = 0; c < model.getColumnCount(); c++) {
                    if (c > 0) pw.print(",");
                    Object v = model.getValueAt(r, c);
                    pw.print("\"" + (v == null ? "" : v.toString()) + "\"");
                }
                pw.println();
            }
            
            pw.flush();
            JOptionPane.showMessageDialog(frame,
                "Summary exported successfully to:\n" + file.getAbsolutePath(),
                "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            statusLabel.setText("Exported to " + file.getName());
            
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame,
                "Failed to export:\n" + ex.getMessage(),
                "Export Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }
    
    private void editRegisterValues(boolean isInt) {
        if (viewModel.getRegisterFile() == null) {
            JOptionPane.showMessageDialog(frame, "Please load a program first.",
                "No Program Loaded", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JDialog dialog = new JDialog(frame, (isInt ? "Edit Integer" : "Edit Floating-Point") + " Registers", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(500, 600);
        
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.setBackground(BG_WHITE);
        
        CycleState state = viewModel.getCurrentState();
        long[] regs = isInt ? state.getIntRegs() : state.getFpRegs();
        JTextField[] fields = new JTextField[regs.length];
        
        for (int i = 0; i < regs.length; i++) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            row.setBackground(BG_WHITE);
            
            JLabel label = new JLabel((isInt ? "R" : "F") + i + ":");
            label.setFont(new Font("Segoe UI", Font.BOLD, 13));
            label.setPreferredSize(new Dimension(50, 25));
            
            fields[i] = new JTextField(String.valueOf(regs[i]), 15);
            fields[i].setFont(new Font("Consolas", Font.PLAIN, 13));
            
            // Disable R0/F0 editing (always 0)
            if (i == 0 && isInt) {
                fields[i].setEnabled(false);
                fields[i].setBackground(new Color(220, 220, 220));
            }
            
            row.add(label);
            row.add(fields[i]);
            panel.add(row);
        }
        
        JScrollPane scroll = new JScrollPane(panel);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        dialog.add(scroll, BorderLayout.CENTER);
        
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnPanel.setBackground(BG_WHITE);
        
        JButton applyBtn = createModernButton("Apply", ACCENT, "check");
        JButton cancelBtn = createModernButton("Cancel", SECONDARY, "cancel");
        
        applyBtn.addActionListener(e -> {
            try {
                RegisterFile rf = viewModel.getRegisterFile();
                int updated = 0;
                for (int i = 0; i < fields.length; i++) {
                    long newValue = Long.parseLong(fields[i].getText().trim());
                    if (newValue != regs[i]) {
                        if (isInt) {
                            rf.setInt(i, newValue);
                        } else {
                            rf.setFp(i, newValue);
                        }
                        updated++;
                    }
                }
                refreshAllViews();
                statusLabel.setText("Updated " + updated + " register(s)");
                dialog.dispose();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog,
                    "Invalid number format in one or more fields.\nPlease enter valid integers.",
                    "Input Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        cancelBtn.addActionListener(e -> dialog.dispose());
        
        btnPanel.add(applyBtn);
        btnPanel.add(cancelBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }
    
    private void showAbout() {
        String message = "Tomasulo Simulator - Modern Edition\n\n" +
                        "A comprehensive simulator for the Tomasulo algorithm\n" +
                        "with dynamic scheduling, reservation stations,\n" +
                        "and cache memory modeling.\n\n" +
                        "Version 2.0\n" +
                        "December 2025";
        
        JOptionPane.showMessageDialog(frame, message,
            "About Tomasulo Simulator", JOptionPane.INFORMATION_MESSAGE);
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ModernSwingGUI gui = new ModernSwingGUI();
            gui.initAndShow();
        });
    }
}
