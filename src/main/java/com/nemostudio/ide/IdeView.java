package com.nemostudio.ide;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Main IDE layout: menu bar, toolbar, status bar; center has 8 invisible panels over the workflow diagram.
 */
public class IdeView {

    /** Distance of the "Get Ready" button from the bottom of each panel, as a fraction of panel height (0.3 = 30%). */
    private static final double BUTTON_BOTTOM_OFFSET_RATIO = 0.25;

    private final BorderPane root;
    private final StatusBarMonitor statusBarMonitor;

    public IdeView() {
        root = new BorderPane();

        MenuBar menuBar = buildMenuBar();
        root.setTop(menuBar);

        // Status bar: "Connected"/"Disconnected" + icon, remote folder path (updated by background monitor)
        Region connectionIndicator = new Region();
        connectionIndicator.getStyleClass().add("status-connection-dot");
        connectionIndicator.setPrefSize(10, 10);
        connectionIndicator.setMinSize(10, 10);
        connectionIndicator.setMaxSize(10, 10);
        Label connectionLabel = new Label("Disconnected");
        connectionLabel.getStyleClass().add("status-bar-text");
        Label remoteFolderLabel = new Label("Remote folder: not set");
        remoteFolderLabel.getStyleClass().add("status-bar-text");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox statusBar = new HBox(8, connectionLabel, connectionIndicator, spacer, remoteFolderLabel);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(4, 8, 4, 8));
        statusBar.getStyleClass().add("status-bar");
        root.setBottom(statusBar);

        // Center: two horizontal parts — top = 8 panels (full width), bottom = yellow panel
        VBox centerSplit = new VBox();
        centerSplit.setStyle("-fx-background-color: transparent;");

        // Remote terminal: visible at startup; connect at startup
        RemoteTerminalPanel terminalPanel = new RemoteTerminalPanel();
        terminalPanel.appendOutput("Waiting for user action.\n");
        Pane terminalPlaceholder = new Pane();
        terminalPlaceholder.setStyle("-fx-background-color: transparent;");
        StackPane terminalStack = new StackPane();
        terminalStack.getChildren().addAll(terminalPlaceholder, terminalPanel);
        final boolean[] leftTerminalConnectOnce = { false };
        terminalStack.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && !leftTerminalConnectOnce[0]) {
                leftTerminalConnectOnce[0] = true;
                terminalPanel.connect(null);
            }
        });

        // Right panel: terminal that shows only docker ps output, refreshed every 5s (clear then run)
        RemoteTerminalPanel dockerStatusTerminal = new RemoteTerminalPanel("Docker status (refreshes every 5s). Connecting...");
        StackPane rightTerminalStack = new StackPane();
        rightTerminalStack.getChildren().add(dockerStatusTerminal);
        final boolean[] dockerTerminalConnectOnce = { false };
        rightTerminalStack.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && !dockerTerminalConnectOnce[0]) {
                dockerTerminalConnectOnce[0] = true;
                dockerStatusTerminal.connect(() -> {
                    dockerStatusTerminal.clearOutput();
                    java.util.concurrent.ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
                    scheduler.scheduleAtFixedRate(() -> javafx.application.Platform.runLater(() -> {
                        dockerStatusTerminal.clearOutput();
                        dockerStatusTerminal.sendCommand("docker ps");
                    }), 0, 5, TimeUnit.SECONDS);
                });
            }
        });

        // Bottom half: left = terminal (50%), right = docker status terminal (50%)
        HBox bottomHalf = new HBox();
        bottomHalf.setStyle("-fx-background-color: transparent;");
        bottomHalf.getChildren().addAll(terminalStack, rightTerminalStack);
        terminalStack.setMinWidth(0);
        terminalStack.setMaxWidth(Double.MAX_VALUE);
        rightTerminalStack.setMinWidth(0);
        rightTerminalStack.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(terminalStack, Priority.ALWAYS);
        HBox.setHgrow(rightTerminalStack, Priority.ALWAYS);
        terminalStack.prefWidthProperty().bind(bottomHalf.widthProperty().multiply(0.5));
        rightTerminalStack.prefWidthProperty().bind(bottomHalf.widthProperty().multiply(0.5));

        HBox centerOverlay = new HBox();
        centerOverlay.setStyle("-fx-background-color: transparent;");
        centerOverlay.setMinSize(0, 0);
        centerOverlay.setMaxWidth(Double.MAX_VALUE);

        for (int i = 0; i < 8; i++) {
            final int panelIndex = i;
            Pane panel = new Pane();
            panel.setStyle("-fx-background-color: transparent;");
            panel.setMinWidth(0);
            panel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(panel, Priority.ALWAYS);
            if (i >= 1) {
                Button getReadyBtn = new Button("Get Ready");
                getReadyBtn.setOnAction(e -> {
                    Executors.newSingleThreadScheduledExecutor()
                            .schedule(() -> javafx.application.Platform.runLater(() -> IdeView.this.runGetReady(panelIndex, terminalPanel)),
                                    terminalPanel.isRemoteConnected() ? 0 : 400, TimeUnit.MILLISECONDS);
                });
                panel.getChildren().add(getReadyBtn);

                // Panel 1 only: add "Upload Files" button below "Get Ready"
                Button uploadFilesBtn = null;
                if (i == 1) {
                    uploadFilesBtn = new Button("Upload Files");
                    uploadFilesBtn.setOnAction(e -> UploadSampleFiles.execute(terminalPanel));
                    panel.getChildren().add(uploadFilesBtn);
                }

                final Button uploadBtn = uploadFilesBtn;
                Runnable positionButton = () -> {
                    double pw = panel.getWidth();
                    double ph = panel.getHeight();
                    double bw = getReadyBtn.prefWidth(-1);
                    double bh = getReadyBtn.prefHeight(-1);
                    double gap = 6;
                    // Get Ready: same as before (25% from bottom, centered)
                    getReadyBtn.setLayoutX(Math.max(0, (pw - bw) / 2));
                    getReadyBtn.setLayoutY(Math.max(0, ph * (1 - BUTTON_BOTTOM_OFFSET_RATIO) - bh));
                    if (uploadBtn != null) {
                        double ubw = uploadBtn.prefWidth(-1);
                        uploadBtn.setLayoutX(Math.max(0, (pw - ubw) / 2));
                        uploadBtn.setLayoutY(getReadyBtn.getLayoutY() + bh + gap);
                    }
                };
                panel.widthProperty().addListener((o, old, v) -> positionButton.run());
                panel.heightProperty().addListener((o, old, v) -> positionButton.run());
                positionButton.run();
            }
            centerOverlay.getChildren().add(panel);
        }

        terminalStack.setMinHeight(0);
        rightTerminalStack.setMinHeight(0);

        // Strict 50/50 height: top = 8 panels, bottom = terminal (left 50%) + docker status terminal (right 50%)
        centerOverlay.prefHeightProperty().bind(centerSplit.heightProperty().multiply(0.5));
        centerOverlay.maxHeightProperty().bind(centerSplit.heightProperty().multiply(0.5));
        bottomHalf.prefHeightProperty().bind(centerSplit.heightProperty().multiply(0.5));
        bottomHalf.maxHeightProperty().bind(centerSplit.heightProperty().multiply(0.5));

        centerSplit.getChildren().addAll(centerOverlay, bottomHalf);
        root.setCenter(centerSplit);

        statusBarMonitor = new StatusBarMonitor();
        statusBarMonitor.start(connectionIndicator, connectionLabel, remoteFolderLabel);
    }

    private void runGetReady(int panelIndex, RemoteTerminalPanel terminal) {
        switch (panelIndex) {
            case 1 -> GetReady01.execute(terminal);
            case 2, 3, 4, 5, 6, 7 -> showComingSoonAlert();
            default -> {}
        }
    }

    private void showComingSoonAlert() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Coming Soon");
        alert.setHeaderText("Coming Soon");
        alert.setContentText("This feature is not available yet. Stay tuned!");
        alert.showAndWait();
    }

    private MenuBar buildMenuBar() {
        Menu fileMenu = new Menu("File");
        MenuItem newItem = new MenuItem("New");
        MenuItem openItem = new MenuItem("Open...");
        MenuItem saveItem = new MenuItem("Save");
        MenuItem saveAsItem = new MenuItem("Save As...");
        SeparatorMenuItem sep = new SeparatorMenuItem();
        MenuItem exitItem = new MenuItem("Exit");
        fileMenu.getItems().addAll(newItem, openItem, saveItem, saveAsItem, sep, exitItem);

        Menu projectMenu = new Menu("Project");
        MenuItem connectItem = new MenuItem("Connect...");
        connectItem.setOnAction(e -> ConnectWindow.show(null));
        MenuItem openRemoteFolderItem = new MenuItem("Open Remote Folder");
        openRemoteFolderItem.setOnAction(e -> RemoteFolderWindow.show(null));
        projectMenu.getItems().addAll(connectItem, openRemoteFolderItem);

        Menu workflowMenu = buildWorkflowMenu();

        Menu editMenu = new Menu("Edit");
        editMenu.getItems().addAll(
                new MenuItem("Undo"),
                new MenuItem("Redo"),
                new SeparatorMenuItem(),
                new MenuItem("Cut"),
                new MenuItem("Copy"),
                new MenuItem("Paste")
        );

        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");
        helpMenu.getItems().add(aboutItem);

        exitItem.setOnAction(e -> javafx.application.Platform.exit());
        aboutItem.setOnAction(e -> showAbout());

        return new MenuBar(fileMenu, projectMenu, workflowMenu, editMenu, helpMenu);
    }

    /**
     * Workflow menu matching the diagram: Prepare, Generate Data → … → Optimize, with correct labels and tools.
     */
    private Menu buildWorkflowMenu() {
        Menu workflowMenu = new Menu("Workflow");

        MenuItem prepareGenerateData = new MenuItem("Prepare, Generate Data");
        prepareGenerateData.setOnAction(e -> WorkflowStepWindow.show("Prepare, Generate Data",
                Arrays.asList("NeMo Curator", "NeMo Data Designer")));

        MenuItem selectModel = new MenuItem("Select Model");
        selectModel.setOnAction(e -> WorkflowStepWindow.show("Select Model",
                Arrays.asList("Nemotron", "NeMo Retriever", "NeMo Evaluator")));

        MenuItem buildAgent = new MenuItem("Build Agent");
        buildAgent.setOnAction(e -> WorkflowStepWindow.show("Build Agent",
                Collections.singletonList("NeMo Agent Toolkit")));

        MenuItem connectToData = new MenuItem("Connect to Data");
        connectToData.setOnAction(e -> WorkflowStepWindow.show("Connect to Data",
                Collections.singletonList("NeMo Retriever")));

        MenuItem guardrail = new MenuItem("Guardrail");
        guardrail.setOnAction(e -> WorkflowStepWindow.show("Guardrail",
                Collections.singletonList("NeMo Guardrails")));

        MenuItem deploy = new MenuItem("Deploy");
        deploy.setOnAction(e -> WorkflowStepWindow.show("Deploy",
                Collections.singletonList("NVIDIA NIM")));

        MenuItem monitor = new MenuItem("Monitor");
        monitor.setOnAction(e -> WorkflowStepWindow.show("Monitor", Collections.emptyList()));

        MenuItem optimize = new MenuItem("Optimize");
        optimize.setOnAction(e -> WorkflowStepWindow.show("Optimize",
                Arrays.asList("NeMo Agent Toolkit", "NeMo RL", "NeMo Customizer", "NeMo Evaluator")));

        workflowMenu.getItems().addAll(
                prepareGenerateData,
                selectModel,
                buildAgent,
                connectToData,
                guardrail,
                deploy,
                monitor,
                optimize
        );
        return workflowMenu;
    }

    private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Nemo Studio");
        alert.setHeaderText("Nemo Studio Desktop");
        alert.setContentText("A basic demo IDE built with JavaFX.\nVersion 1.0.0");
        alert.showAndWait();
    }

    public BorderPane getRoot() {
        return root;
    }
}
