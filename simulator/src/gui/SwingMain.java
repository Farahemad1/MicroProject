package gui;

import core.CycleState;
import core.LoadBufferEntry;
import core.ReservationStation;
import core.StoreBufferEntry;
import core.CacheLine;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class SwingMain {

    private final StateViewModel viewModel = new StateViewModel();

    private JFrame frame;
    private JTable intRegTable;
    private JTable fpRegTable;
    private JTable rsTable;
    private JTable lbTable;
    private JTable sbTable;
    private JList<String> instrList;
    private JLabel cycleLabel;
    private JTextField fileField;

    // Parameter controls
    private JSpinner spFpAddRS, spFpMulRS, spIntAluRS, spLoadBuf, spStoreBuf;
    private JSpinner spFpAddLat, spFpMulLat, spIntAluLat, spLoadLat, spStoreLat;
    // Cache controls
    private JSpinner spCacheSize, spBlockSize, spAssoc, spCacheHitLat, spCacheMissPen;
    private JTable cacheTable;
    private JTable summaryTable;
    private JButton exportSummaryBtn;
    private JButton playBtn;
    private Timer autoTimer;

    public void initAndShow() {
        // Modern Look & Feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        frame = new JFrame("Tomasulo Simulator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1400, 900);
        frame.setLayout(new BorderLayout(10, 10));

        // Color scheme
        Color bgColor = new Color(245, 245, 250);
        Color panelBg = new Color(255, 255, 255);
        Color accentColor = new Color(70, 130, 180);
        frame.getContentPane().setBackground(bgColor);

        JPanel top = new JPanel(new BorderLayout(10, 10));
        top.setBackground(panelBg);
        top.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        // File / controls
        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        filePanel.setBackground(panelBg);
        fileField = new JTextField(35);
        fileField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        fileField.setToolTipText("Path to assembly program file");
        
        JButton browse = createStyledButton("[...] Browse", accentColor);
        JButton load = createStyledButton("> Load", new Color(34, 139, 34));
        JButton prev = createStyledButton("< Prev", new Color(100, 100, 100));
        JButton step = createStyledButton("Step >", accentColor);
        JButton reset = createStyledButton("[R] Reset", new Color(220, 100, 50));
        playBtn = createStyledButton(">> Play", new Color(34, 139, 34));
        
        browse.setToolTipText("Browse for program file");
        load.setToolTipText("Load and initialize the program");
        prev.setToolTipText("Go back one cycle");
        step.setToolTipText("Execute next cycle");
        reset.setToolTipText("Reset simulator with current parameters");
        playBtn.setToolTipText("Auto-run cycles continuously");
        
        cycleLabel = new JLabel("Cycle: 0");
        cycleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        cycleLabel.setForeground(accentColor);
        cycleLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(accentColor, 2, true),
            BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));

        JLabel progLabel = new JLabel("Program:");
        progLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        filePanel.add(progLabel);
        filePanel.add(fileField);
        filePanel.add(browse);
        filePanel.add(load);
        filePanel.add(Box.createHorizontalStrut(20));
        filePanel.add(prev);
        filePanel.add(step);
        filePanel.add(reset);
        filePanel.add(playBtn);
        filePanel.add(Box.createHorizontalStrut(20));
        filePanel.add(cycleLabel);

        top.add(filePanel, BorderLayout.NORTH);

        // Parameter panel
        JPanel params = new JPanel(new GridLayout(3, 1, 8, 8));
        params.setBackground(panelBg);
        params.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
                "Configuration Parameters",
                0, 0,
                new Font("SansSerif", Font.BOLD, 12),
                new Color(60, 60, 60)
            ),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        
        JPanel sizes = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        sizes.setBackground(panelBg);
        JPanel lats = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        lats.setBackground(panelBg);

        spFpAddRS = new JSpinner(new SpinnerNumberModel(viewModel.numFpAddRS, 1, 16, 1));
        spFpMulRS = new JSpinner(new SpinnerNumberModel(viewModel.numFpMulRS, 1, 16, 1));
        spIntAluRS = new JSpinner(new SpinnerNumberModel(viewModel.numIntAluRS, 1, 16, 1));
        spLoadBuf = new JSpinner(new SpinnerNumberModel(viewModel.numLoadBuffers, 1, 16, 1));
        spStoreBuf = new JSpinner(new SpinnerNumberModel(viewModel.numStoreBuffers, 1, 16, 1));

        sizes.add(createParamLabel("FP Add RS:")); sizes.add(spFpAddRS);
        sizes.add(createParamLabel("FP Mul RS:")); sizes.add(spFpMulRS);
        sizes.add(createParamLabel("Int ALU RS:")); sizes.add(spIntAluRS);
        sizes.add(createParamLabel("Load Buf:")); sizes.add(spLoadBuf);
        sizes.add(createParamLabel("Store Buf:")); sizes.add(spStoreBuf);

        spFpAddLat = new JSpinner(new SpinnerNumberModel(viewModel.fpAddLatency, 1, 100, 1));
        spFpMulLat = new JSpinner(new SpinnerNumberModel(viewModel.fpMulLatency, 1, 100, 1));
        spIntAluLat = new JSpinner(new SpinnerNumberModel(viewModel.intAluLatency, 1, 100, 1));
        spLoadLat = new JSpinner(new SpinnerNumberModel(viewModel.loadLatencyBase, 1, 100, 1));
        spStoreLat = new JSpinner(new SpinnerNumberModel(viewModel.storeLatencyBase, 1, 100, 1));

        lats.add(createParamLabel("FP Add Lat:")); lats.add(spFpAddLat);
        lats.add(createParamLabel("FP Mul Lat:")); lats.add(spFpMulLat);
        lats.add(createParamLabel("Int ALU Lat:")); lats.add(spIntAluLat);
        lats.add(createParamLabel("Load Lat:")); lats.add(spLoadLat);
        lats.add(createParamLabel("Store Lat:")); lats.add(spStoreLat);

        // Cache controls (sizes in bytes)
        spCacheSize = new JSpinner(new SpinnerNumberModel(viewModel.cacheSize, 16, 65536, 16));
        spBlockSize = new JSpinner(new SpinnerNumberModel(viewModel.blockSize, 4, 1024, 4));
        spAssoc = new JSpinner(new SpinnerNumberModel(viewModel.associativity, 1, 16, 1));
        spCacheHitLat = new JSpinner(new SpinnerNumberModel(viewModel.cacheHitLatency, 1, 100, 1));
        spCacheMissPen = new JSpinner(new SpinnerNumberModel(viewModel.cacheMissPenalty, 1, 500, 1));

        JPanel cachePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        cachePanel.setBackground(panelBg);
        cachePanel.add(createParamLabel("Cache Size:")); cachePanel.add(spCacheSize);
        cachePanel.add(createParamLabel("Blk Size:")); cachePanel.add(spBlockSize);
        cachePanel.add(createParamLabel("Assoc:")); cachePanel.add(spAssoc);
        cachePanel.add(createParamLabel("Hit Lat:")); cachePanel.add(spCacheHitLat);
        cachePanel.add(createParamLabel("Miss Pen:")); cachePanel.add(spCacheMissPen);

        params.add(sizes);
        params.add(lats);
        params.add(cachePanel);
        top.add(params, BorderLayout.SOUTH);

        frame.add(top, BorderLayout.NORTH);

        // Center split: left tables, right instruction list
        JSplitPane split = new JSplitPane();
        split.setDividerLocation(900);
        split.setBorder(null);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBackground(bgColor);
        left.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        intRegTable = new JTable();
        fpRegTable = new JTable();
        rsTable = new JTable();
        lbTable = new JTable();
        sbTable = new JTable();

        left.add(createSectionLabel("Integer Registers", new Color(70, 130, 180)));
        left.add(Box.createVerticalStrut(5));
        left.add(createStyledScrollPane(intRegTable));
        left.add(Box.createVerticalStrut(15));
        
        left.add(createSectionLabel("FP Registers", new Color(34, 139, 34)));
        left.add(Box.createVerticalStrut(5));
        left.add(createStyledScrollPane(fpRegTable));
        left.add(Box.createVerticalStrut(15));
        
        left.add(createSectionLabel("Reservation Stations", new Color(220, 100, 50)));
        left.add(Box.createVerticalStrut(5));
        left.add(createStyledScrollPane(rsTable));
        left.add(Box.createVerticalStrut(15));
        
        left.add(createSectionLabel("Load Buffers", new Color(148, 0, 211)));
        left.add(Box.createVerticalStrut(5));
        left.add(createStyledScrollPane(lbTable));
        left.add(Box.createVerticalStrut(15));
        
        left.add(createSectionLabel("Store Buffers", new Color(199, 21, 133)));
        left.add(Box.createVerticalStrut(5));
        left.add(createStyledScrollPane(sbTable));
        left.add(Box.createVerticalStrut(15));
        
        left.add(createSectionLabel("Data Cache (sets/ways)", new Color(0, 128, 128)));
        left.add(Box.createVerticalStrut(5));
        cacheTable = new JTable();
        left.add(createStyledScrollPane(cacheTable));

        DefaultListModel<String> listModel = new DefaultListModel<>();
        instrList = new JList<>(listModel);
        instrList.setBackground(panelBg);
        instrList.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.setBackground(bgColor);
        right.setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 15));
        
        // Top: Summary Table
        JPanel summaryPanel = new JPanel(new BorderLayout(5, 5));
        summaryPanel.setBackground(bgColor);
        JLabel summaryLabel = createSectionLabel("Instruction Summary", new Color(70, 130, 180));
        JPanel summaryTop = new JPanel(new BorderLayout());
        summaryTop.setOpaque(false);
        summaryTop.add(summaryLabel, BorderLayout.WEST);
        JPanel summaryToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        summaryToolbar.setOpaque(false);
        exportSummaryBtn = createStyledButton("Export CSV", new Color(60, 120, 180));
        summaryToolbar.add(exportSummaryBtn);
        summaryTop.add(summaryToolbar, BorderLayout.EAST);
        summaryPanel.add(summaryTop, BorderLayout.NORTH);
        
        summaryTable = new JTable();
        JScrollPane summaryScroll = new JScrollPane(summaryTable);
        summaryScroll.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180), 1));
        summaryScroll.setPreferredSize(new Dimension(summaryScroll.getPreferredSize().width, 220));
        summaryPanel.add(summaryScroll, BorderLayout.CENTER);
        // Make summary table sortable and selectable
        summaryTable.setAutoCreateRowSorter(true);
        summaryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        summaryTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) return;
                int viewRow = summaryTable.getSelectedRow();
                if (viewRow < 0) return;
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
                        } catch (NumberFormatException ex) {
                            // ignore
                        }
                    }
                }
            }
        });

        exportSummaryBtn.addActionListener(ev -> exportSummaryToCSV());
        
        right.add(summaryPanel, BorderLayout.NORTH);
        
        // Bottom: Instructions with timing details
        JPanel instrPanel = new JPanel(new BorderLayout(5, 5));
        instrPanel.setBackground(bgColor);
        instrPanel.setBorder(BorderFactory.createEmptyBorder(15, 0, 0, 0));
        
        JLabel instrLabel = createSectionLabel("Instructions (detailed timing)", new Color(34, 139, 34));
        instrPanel.add(instrLabel, BorderLayout.NORTH);
        
        JScrollPane instrScroll = new JScrollPane(instrList);
        instrScroll.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180), 1));
        instrPanel.add(instrScroll, BorderLayout.CENTER);
        
        right.add(instrPanel, BorderLayout.CENTER);

        split.setLeftComponent(new JScrollPane(left));
        split.setRightComponent(right);

        frame.add(split, BorderLayout.CENTER);

        // Actions
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(new File("."));
            int res = fc.showOpenDialog(frame);
            if (res == JFileChooser.APPROVE_OPTION) {
                fileField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });

        load.addActionListener(e -> {
            applyParamsToViewModel();
            String path = fileField.getText();
            if (path == null || path.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Select a program file first.");
                return;
            }
            try {
                viewModel.loadProgram(new File(path));
                viewModel.createEngineWithCurrentConfig();
                refreshAllViews();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "Failed to load program: " + ex.getMessage());
            }
        });

        step.addActionListener(e -> {
            if (!viewModel.hasProgram()) { JOptionPane.showMessageDialog(frame, "Load a program first."); return; }
            viewModel.nextCycle();
            refreshAllViews();
        });

        prev.addActionListener(e -> {
            if (!viewModel.hasProgram()) { JOptionPane.showMessageDialog(frame, "Load a program first."); return; }
            viewModel.previousCycle();
            refreshAllViews();
        });

        reset.addActionListener(e -> {
            if (!viewModel.hasProgram()) { JOptionPane.showMessageDialog(frame, "Load a program first."); return; }
            applyParamsToViewModel();
            viewModel.createEngineWithCurrentConfig();
            refreshAllViews();
        });

        // Play / auto-run
        playBtn.addActionListener(e -> {
            if (autoTimer != null && autoTimer.isRunning()) {
                autoTimer.stop();
                playBtn.setText(">> Play");
            } else {
                autoTimer = new Timer(400, ev -> {
                    if (!viewModel.hasProgram()) return;
                    viewModel.nextCycle();
                    refreshAllViews();
                });
                autoTimer.start();
                playBtn.setText("|| Stop");
            }
        });

        frame.setVisible(true);
    }

    private void refreshAllViews() {
        CycleState s = viewModel.getCurrentState();
        if (s == null) return;

        cycleLabel.setText("Cycle: " + s.getCycleNumber());

        // Int regs
        long[] ints = s.getIntRegs();
        String[] intCols = new String[]{"Idx", "Value", "Owner"};
        DefaultTableModel intModel = new DefaultTableModel(intCols, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        for (int i = 0; i < ints.length; i++) {
            String owner = viewModel.getRegisterStatus() == null ? "" : viewModel.getRegisterStatus().getIntOwner(i);
            intModel.addRow(new Object[]{"R" + i, Long.toString(ints[i]), owner == null ? "" : owner});
        }
        intRegTable.setModel(intModel);

        long[] fps = s.getFpRegs();
        DefaultTableModel fpModel = new DefaultTableModel(intCols, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        for (int i = 0; i < fps.length; i++) {
            String owner = viewModel.getRegisterStatus() == null ? "" : viewModel.getRegisterStatus().getFpOwner(i);
            fpModel.addRow(new Object[]{"F" + i, Long.toString(fps[i]), owner == null ? "" : owner});
        }
        fpRegTable.setModel(fpModel);

        // RS combined
        String[] rsCols = new String[]{"Name","Busy","Op","Vj","Vk","Qj","Qk","Dest","Rem"};
        DefaultTableModel rsModel = new DefaultTableModel(rsCols, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        for (ReservationStation r : s.getFpAddStations()) rsModel.addRow(rsRow(r));
        for (ReservationStation r : s.getFpMulStations()) rsModel.addRow(rsRow(r));
        for (ReservationStation r : s.getIntAluStations()) rsModel.addRow(rsRow(r));
        rsTable.setModel(rsModel);

        // Load buffers
        String[] lbCols = new String[]{"Name","Busy","Addr","AddrQ","Dest","Rem"};
        DefaultTableModel lbModel = new DefaultTableModel(lbCols, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        for (LoadBufferEntry lb : s.getLoadBuffers()) {
            lbModel.addRow(new Object[]{lb.getName(), lb.isBusy(), lb.getAddress(), lb.getAddressQ(), lb.getDestReg(), lb.getRemainingCycles()});
        }
        lbTable.setModel(lbModel);

        // Store buffers
        String[] sbCols = new String[]{"Name","Busy","Addr","AddrQ","ValQ"};
        DefaultTableModel sbModel = new DefaultTableModel(sbCols, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        for (StoreBufferEntry sb : s.getStoreBuffers()) {
            sbModel.addRow(new Object[]{sb.getName(), sb.isBusy(), sb.getAddress(), sb.getAddressQ(), sb.getValueQ()});
        }
        sbTable.setModel(sbModel);

        // Cache snapshot
        CacheLine[][] cs = s.getCacheSnapshot();
        String[] cacheCols = new String[]{"Set","Way","Valid","Tag","LRU"};
        DefaultTableModel cacheModel = new DefaultTableModel(cacheCols, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        if (cs != null) {
            for (int set = 0; set < cs.length; set++) {
                CacheLine[] ways = cs[set];
                for (int w = 0; w < ways.length; w++) {
                    CacheLine line = ways[w];
                    if (line == null) {
                        cacheModel.addRow(new Object[]{set, w, false, "-", "-"});
                    } else {
                        String tagStr = line.isValid() ? Long.toString(line.getTag()) : "-";
                        cacheModel.addRow(new Object[]{set, w, line.isValid(), tagStr, line.getLruCounter()});
                    }
                }
            }
        }
        cacheTable.setModel(cacheModel);

        // Summary table
        String[] summaryCols = new String[]{"Instruction", "Issue", "Execute Start", "Execute End", "Write Result"};
        DefaultTableModel summaryModel = new DefaultTableModel(summaryCols, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        for (core.Instruction instr : s.getInstructionsWithTiming()) {
            String issueStr = instr.getIssueCycle() > 0 ? String.valueOf(instr.getIssueCycle()) : "-";
            String startStr = instr.getStartExecCycle() > 0 ? String.valueOf(instr.getStartExecCycle()) : "-";
            String endStr = instr.getEndExecCycle() > 0 ? String.valueOf(instr.getEndExecCycle()) : "-";
            String wbStr = instr.getWriteBackCycle() > 0 ? String.valueOf(instr.getWriteBackCycle()) : "-";
            summaryModel.addRow(new Object[]{
                (instr.getPcIndex() + 1) + ". " + instr.getRawText(),
                issueStr,
                startStr,
                endStr,
                wbStr
            });
        }
        summaryTable.setModel(summaryModel);

        // Apply renderer to highlight completed write results
        SummaryCellRenderer renderer = new SummaryCellRenderer();
        for (int i = 0; i < summaryTable.getColumnModel().getColumnCount(); i++) {
            summaryTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        // Instructions
        DefaultListModel<String> lm = new DefaultListModel<>();
        for (core.Instruction instr : s.getInstructionsWithTiming()) {
            lm.addElement(instr.getPcIndex() + ": " + instr.getRawText() + "  [I=" + instr.getIssueCycle() + ", S=" + instr.getStartExecCycle() + ", E=" + instr.getEndExecCycle() + ", W=" + instr.getWriteBackCycle() + "]");
        }
        instrList.setModel(lm);

        // Visual tweaks
        Font mono = new Font("Consolas", Font.PLAIN, 12);
        instrList.setFont(mono);
        
        styleTable(intRegTable);
        styleTable(fpRegTable);
        styleTable(rsTable);
        styleTable(lbTable);
        styleTable(sbTable);
        styleTable(cacheTable);
        styleTable(summaryTable);

        // Column sizing hints
        if (intRegTable.getColumnModel().getColumnCount() > 0) {
            intRegTable.getColumnModel().getColumn(0).setPreferredWidth(50);
            intRegTable.getColumnModel().getColumn(1).setPreferredWidth(100);
            intRegTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        }
        if (fpRegTable.getColumnModel().getColumnCount() > 0) {
            fpRegTable.getColumnModel().getColumn(0).setPreferredWidth(50);
            fpRegTable.getColumnModel().getColumn(1).setPreferredWidth(100);
            fpRegTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        }
        if (summaryTable.getColumnModel().getColumnCount() > 0) {
            summaryTable.getColumnModel().getColumn(0).setPreferredWidth(250);
            summaryTable.getColumnModel().getColumn(1).setPreferredWidth(60);
            summaryTable.getColumnModel().getColumn(2).setPreferredWidth(110);
            summaryTable.getColumnModel().getColumn(3).setPreferredWidth(100);
            summaryTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        }
    }

    private Object[] rsRow(ReservationStation r) {
        return new Object[]{r.getName(), r.isBusy(), r.getOp(), r.getVj(), r.getVk(), r.getQj(), r.getQk(), r.getDest(), r.getRemainingCycles()};
    }

    private void applyParamsToViewModel() {
        viewModel.numFpAddRS = (Integer) spFpAddRS.getValue();
        viewModel.numFpMulRS = (Integer) spFpMulRS.getValue();
        viewModel.numIntAluRS = (Integer) spIntAluRS.getValue();
        viewModel.numLoadBuffers = (Integer) spLoadBuf.getValue();
        viewModel.numStoreBuffers = (Integer) spStoreBuf.getValue();

        viewModel.fpAddLatency = (Integer) spFpAddLat.getValue();
        viewModel.fpMulLatency = (Integer) spFpMulLat.getValue();
        viewModel.intAluLatency = (Integer) spIntAluLat.getValue();
        viewModel.loadLatencyBase = (Integer) spLoadLat.getValue();
        viewModel.storeLatencyBase = (Integer) spStoreLat.getValue();
        
        // Cache params
        viewModel.cacheSize = (Integer) spCacheSize.getValue();
        viewModel.blockSize = (Integer) spBlockSize.getValue();
        viewModel.associativity = (Integer) spAssoc.getValue();
        viewModel.cacheHitLatency = (Integer) spCacheHitLat.getValue();
        viewModel.cacheMissPenalty = (Integer) spCacheMissPen.getValue();
    }

    // Helper methods for styling
    private JButton createStyledButton(String text, Color color) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color.darker(), 1),
            BorderFactory.createEmptyBorder(5, 12, 5, 12)
        ));
        return btn;
    }

    private JLabel createSectionLabel(String text, Color color) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 14));
        lbl.setForeground(color);
        lbl.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, color),
            BorderFactory.createEmptyBorder(3, 0, 8, 0)
        ));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private JLabel createParamLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lbl.setForeground(new Color(60, 60, 60));
        return lbl;
    }

    private JScrollPane createStyledScrollPane(JTable table) {
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180), 1));
        sp.setAlignmentX(Component.LEFT_ALIGNMENT);
        sp.setPreferredSize(new Dimension(sp.getPreferredSize().width, 120));
        return sp;
    }

    private void styleTable(JTable table) {
        table.setFont(new Font("Consolas", Font.PLAIN, 12));
        table.setRowHeight(24);
        table.setGridColor(new Color(220, 220, 220));
        table.setBackground(Color.WHITE);
        table.setSelectionBackground(new Color(184, 207, 229));
        table.setSelectionForeground(Color.BLACK);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.getTableHeader().setBackground(new Color(240, 240, 245));
        table.getTableHeader().setForeground(new Color(60, 60, 60));
        table.setShowGrid(true);
    }

    private void exportSummaryToCSV() {
        if (summaryTable.getModel().getRowCount() == 0) {
            JOptionPane.showMessageDialog(frame, "No summary rows to export.");
            return;
        }
        JFileChooser fc = new JFileChooser(new File("."));
        int res = fc.showSaveDialog(frame);
        if (res != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
            DefaultTableModel dm = (DefaultTableModel) summaryTable.getModel();
            // header
            for (int c = 0; c < dm.getColumnCount(); c++) {
                if (c > 0) pw.print(',');
                pw.print('"' + dm.getColumnName(c) + '"');
            }
            pw.println();
            // rows
            for (int r = 0; r < dm.getRowCount(); r++) {
                for (int c = 0; c < dm.getColumnCount(); c++) {
                    if (c > 0) pw.print(',');
                    Object v = dm.getValueAt(r, c);
                    pw.print('"' + (v == null ? "" : v.toString()) + '"');
                }
                pw.println();
            }
            pw.flush();
            JOptionPane.showMessageDialog(frame, "Exported summary to: " + f.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Failed to export: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // Renderer that highlights rows whose Write Result is <= current cycle
    private class SummaryCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            try {
                int modelRow = table.convertRowIndexToModel(row);
                Object wb = table.getModel().getValueAt(modelRow, 4);
                int cur = viewModel.getCurrentState() == null ? 0 : viewModel.getCurrentState().getCycleNumber();
                if (wb != null && !"-".equals(wb.toString())) {
                    int w = Integer.parseInt(wb.toString());
                    if (!isSelected) {
                        if (w <= cur) c.setBackground(new Color(200, 255, 200));
                        else c.setBackground(Color.WHITE);
                    }
                } else {
                    if (!isSelected) c.setBackground(Color.WHITE);
                }
            } catch (Exception ex) {
                // ignore and leave default
            }
            return c;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SwingMain m = new SwingMain();
            m.initAndShow();
        });
    }
}
