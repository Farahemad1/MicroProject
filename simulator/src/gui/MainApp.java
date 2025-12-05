package gui;

// MainApp.java
import core.CycleState;
import core.LoadBufferEntry;
import core.ReservationStation;
import core.StoreBufferEntry;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class MainApp extends Application {

    private StateViewModel viewModel = new StateViewModel();

    private TableView<String[]> regTableInt;
    private TableView<String[]> regTableFp;
    private TableView<ReservationStation> rsTable;
    private TableView<LoadBufferEntry> lbTable;
    private TableView<StoreBufferEntry> sbTable;
    private ListView<String> instrList;
    private Label cycleLabel;

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(8));

        // Top controls
        HBox top = new HBox(8);

        TextField filePath = new TextField();
        filePath.setPrefColumnCount(30);
        Button browse = new Button("Browse");
        Button load = new Button("Load");

        Button prev = new Button("< Prev");
        Button step = new Button("Step >");
        Button reset = new Button("Reset");

        cycleLabel = new Label("Cycle: 0");

        top.getChildren().addAll(new Label("Program:"), filePath, browse, load, new Separator(), prev, step, reset, new Separator(), cycleLabel);
        top.setPadding(new Insets(6));

        root.setTop(top);

        // Center: split pane for left tables and right instruction list
        SplitPane center = new SplitPane();
        center.setDividerPositions(0.7);

        // Left: register panes and RS/tables
        VBox left = new VBox(8);

        // Registers
        regTableInt = new TableView<>();
        regTableFp = new TableView<>();

        setupRegisterTable(regTableInt, "Int");
        setupRegisterTable(regTableFp, "FP");

        HBox regs = new HBox(8, new VBox(new Label("Integer Registers"), regTableInt), new VBox(new Label("FP Registers"), regTableFp));
        regs.setPadding(new Insets(4));

        // Reservation stations
        rsTable = new TableView<>();
        setupReservationStationTable(rsTable);

        lbTable = new TableView<>();
        setupLoadBufferTable(lbTable);

        sbTable = new TableView<>();
        setupStoreBufferTable(sbTable);

        left.getChildren().addAll(regs, new Label("Reservation Stations / Int ALU / FP"), rsTable, new Label("Load Buffers"), lbTable, new Label("Store Buffers"), sbTable);
        left.setPadding(new Insets(6));

        // Right: instruction list and details
        VBox right = new VBox(8);
        instrList = new ListView<>();
        right.getChildren().addAll(new Label("Instructions (timing)"), instrList);
        right.setPadding(new Insets(6));

        center.getItems().addAll(left, right);
        root.setCenter(center);

        // Browse file
        browse.setOnAction(ev -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Open MIPS program");
            File f = fc.showOpenDialog(primaryStage);
            if (f != null) {
                filePath.setText(f.getAbsolutePath());
            }
        });

        load.setOnAction(ev -> {
            String path = filePath.getText();
            if (path == null || path.isEmpty()) {
                showAlert("Select a program file first.");
                return;
            }
            try {
                viewModel.loadProgram(new File(path));
                viewModel.createEngineWithCurrentConfig();
                refreshAllViews();
            } catch (Exception ex) {
                showAlert("Failed to load program: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        step.setOnAction(ev -> {
            if (!viewModel.hasProgram()) { showAlert("Load a program first."); return; }
            viewModel.nextCycle();
            refreshAllViews();
        });

        prev.setOnAction(ev -> {
            if (!viewModel.hasProgram()) { showAlert("Load a program first."); return; }
            viewModel.previousCycle();
            refreshAllViews();
        });

        reset.setOnAction(ev -> {
            if (!viewModel.hasProgram()) { showAlert("Load a program first."); return; }
            viewModel.createEngineWithCurrentConfig();
            refreshAllViews();
        });

        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setTitle("Tomasulo Simulator - GUI");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void showAlert(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
            a.showAndWait();
        });
    }

    private void setupRegisterTable(TableView<String[]> table, String kind) {
        TableColumn<String[], String> idxCol = new TableColumn<>("Idx");
        idxCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[0]));

        TableColumn<String[], String> valCol = new TableColumn<>("Value");
        valCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[1]));

        TableColumn<String[], String> ownerCol = new TableColumn<>("Owner");
        ownerCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[2]));

        table.getColumns().setAll(idxCol, valCol, ownerCol);
        table.setPrefHeight(180);
    }

    private void setupReservationStationTable(TableView<ReservationStation> table) {
        TableColumn<ReservationStation, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));

        TableColumn<ReservationStation, String> busy = new TableColumn<>("Busy");
        busy.setCellValueFactory(c -> new SimpleStringProperty(Boolean.toString(c.getValue().isBusy())));

        TableColumn<ReservationStation, String> op = new TableColumn<>("Op");
        op.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getOp())));

        TableColumn<ReservationStation, String> vj = new TableColumn<>("Vj");
        vj.setCellValueFactory(c -> new SimpleStringProperty(Long.toString(c.getValue().getVj())));

        TableColumn<ReservationStation, String> vk = new TableColumn<>("Vk");
        vk.setCellValueFactory(c -> new SimpleStringProperty(Long.toString(c.getValue().getVk())));

        TableColumn<ReservationStation, String> qj = new TableColumn<>("Qj");
        qj.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getQj())));

        TableColumn<ReservationStation, String> qk = new TableColumn<>("Qk");
        qk.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getQk())));

        TableColumn<ReservationStation, String> dest = new TableColumn<>("Dest");
        dest.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getDest())));

        TableColumn<ReservationStation, String> rem = new TableColumn<>("RemCycles");
        rem.setCellValueFactory(c -> new SimpleStringProperty(Integer.toString(c.getValue().getRemainingCycles())));

        table.getColumns().setAll(name, busy, op, vj, vk, qj, qk, dest, rem);
        table.setPrefHeight(220);
    }

    private void setupLoadBufferTable(TableView<LoadBufferEntry> table) {
        TableColumn<LoadBufferEntry, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));

        TableColumn<LoadBufferEntry, String> busy = new TableColumn<>("Busy");
        busy.setCellValueFactory(c -> new SimpleStringProperty(Boolean.toString(c.getValue().isBusy())));

        TableColumn<LoadBufferEntry, String> addr = new TableColumn<>("Addr");
        addr.setCellValueFactory(c -> new SimpleStringProperty(Long.toString(c.getValue().getAddress())));

        TableColumn<LoadBufferEntry, String> addrQ = new TableColumn<>("AddrQ");
        addrQ.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getAddressQ())));

        TableColumn<LoadBufferEntry, String> dest = new TableColumn<>("Dest");
        dest.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getDestReg())));

        TableColumn<LoadBufferEntry, String> rem = new TableColumn<>("Rem");
        rem.setCellValueFactory(c -> new SimpleStringProperty(Integer.toString(c.getValue().getRemainingCycles())));

        table.getColumns().setAll(name, busy, addr, addrQ, dest, rem);
        table.setPrefHeight(140);
    }

    private void setupStoreBufferTable(TableView<StoreBufferEntry> table) {
        TableColumn<StoreBufferEntry, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));

        TableColumn<StoreBufferEntry, String> busy = new TableColumn<>("Busy");
        busy.setCellValueFactory(c -> new SimpleStringProperty(Boolean.toString(c.getValue().isBusy())));

        TableColumn<StoreBufferEntry, String> addr = new TableColumn<>("Addr");
        addr.setCellValueFactory(c -> new SimpleStringProperty(Long.toString(c.getValue().getAddress())));

        TableColumn<StoreBufferEntry, String> addrQ = new TableColumn<>("AddrQ");
        addrQ.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getAddressQ())));

        TableColumn<StoreBufferEntry, String> valQ = new TableColumn<>("ValQ");
        valQ.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getValueQ())));

        table.getColumns().setAll(name, busy, addr, addrQ, valQ);
        table.setPrefHeight(140);
    }

    private void refreshAllViews() {
        CycleState s = viewModel.getCurrentState();
        if (s == null) return;

        cycleLabel.setText("Cycle: " + s.getCycleNumber());

        // Registers
        long[] ints = s.getIntRegs();
        long[] fps = s.getFpRegs();
        RegisterStatusWrapper rsw = new RegisterStatusWrapper(viewModel.getRegisterStatus());

        ObservableList<String[]> intRows = FXCollections.observableArrayList();
        for (int i = 0; i < ints.length; i++) {
            String owner = rsw.getIntOwner(i);
            intRows.add(new String[]{"R" + i, Long.toString(ints[i]), owner == null ? "" : owner});
        }
        regTableInt.setItems(intRows);

        ObservableList<String[]> fpRows = FXCollections.observableArrayList();
        for (int i = 0; i < fps.length; i++) {
            String owner = rsw.getFpOwner(i);
            fpRows.add(new String[]{"F" + i, Long.toString(fps[i]), owner == null ? "" : owner});
        }
        regTableFp.setItems(fpRows);

        // Reservation stations: combine
        ObservableList<ReservationStation> rsRows = FXCollections.observableArrayList();
        rsRows.addAll(s.getFpAddStations());
        rsRows.addAll(s.getFpMulStations());
        rsRows.addAll(s.getIntAluStations());
        rsTable.setItems(rsRows);

        // Load/Store buffers
        ObservableList<LoadBufferEntry> lbRows = FXCollections.observableArrayList(s.getLoadBuffers());
        lbTable.setItems(lbRows);

        ObservableList<StoreBufferEntry> sbRows = FXCollections.observableArrayList(s.getStoreBuffers());
        sbTable.setItems(sbRows);

        // Instructions
        instrList.getItems().clear();
        for (core.Instruction instr : s.getInstructionsWithTiming()) {
            instrList.getItems().add(instr.getPcIndex() + ": " + instr.getRawText()
                    + "  [I=" + instr.getIssueCycle()
                    + ", S=" + instr.getStartExecCycle()
                    + ", E=" + instr.getEndExecCycle()
                    + ", W=" + instr.getWriteBackCycle() + "]");
        }
    }

    // Small wrapper so we can safely query null engine
    private static class RegisterStatusWrapper {
        private final core.RegisterStatus rs;
        RegisterStatusWrapper(core.RegisterStatus rs) { this.rs = rs; }
        String getIntOwner(int i) { if (rs == null) return null; String o = rs.getIntOwner(i); return o; }
        String getFpOwner(int i) { if (rs == null) return null; String o = rs.getFpOwner(i); return o; }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
