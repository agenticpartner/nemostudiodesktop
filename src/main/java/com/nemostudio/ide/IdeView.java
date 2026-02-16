package com.nemostudio.ide;

import java.util.Arrays;
import java.util.Collections;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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

        ToolBar toolBar = buildToolBar();
        VBox topBox = new VBox(menuBar, toolBar);
        root.setTop(topBox);

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

        HBox centerOverlay = new HBox();
        centerOverlay.setStyle("-fx-background-color: transparent;");
        centerOverlay.setMinSize(0, 0);
        centerOverlay.setMaxWidth(Double.MAX_VALUE);

        for (int i = 0; i < 8; i++) {
            Pane panel = new Pane();
            panel.setStyle("-fx-background-color: transparent;");
            panel.setMinWidth(0);
            panel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(panel, Priority.ALWAYS);
            if (i >= 1) {
                Button getReadyBtn = new Button("Get Ready");
                panel.getChildren().add(getReadyBtn);
                Runnable positionButton = () -> {
                    double pw = panel.getWidth();
                    double ph = panel.getHeight();
                    double bw = getReadyBtn.prefWidth(-1);
                    double bh = getReadyBtn.prefHeight(-1);
                    getReadyBtn.setLayoutX(Math.max(0, (pw - bw) / 2));
                    getReadyBtn.setLayoutY(Math.max(0, ph * (1 - BUTTON_BOTTOM_OFFSET_RATIO) - bh));
                };
                panel.widthProperty().addListener((o, old, v) -> positionButton.run());
                panel.heightProperty().addListener((o, old, v) -> positionButton.run());
                positionButton.run();
            }
            centerOverlay.getChildren().add(panel);
        }

        Pane bottomPanel = new Pane();
        bottomPanel.setStyle("-fx-background-color: yellow;");
        bottomPanel.setMinHeight(0);
        bottomPanel.setMaxWidth(Double.MAX_VALUE);

        // Strict 50/50 height: each half gets exactly half of the center area
        centerOverlay.prefHeightProperty().bind(centerSplit.heightProperty().multiply(0.5));
        centerOverlay.maxHeightProperty().bind(centerSplit.heightProperty().multiply(0.5));
        bottomPanel.prefHeightProperty().bind(centerSplit.heightProperty().multiply(0.5));
        bottomPanel.maxHeightProperty().bind(centerSplit.heightProperty().multiply(0.5));

        centerSplit.getChildren().addAll(centerOverlay, bottomPanel);
        root.setCenter(centerSplit);

        statusBarMonitor = new StatusBarMonitor();
        statusBarMonitor.start(connectionIndicator, connectionLabel, remoteFolderLabel);
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

    private ToolBar buildToolBar() {
        Button newBtn = new Button("New");
        Button openBtn = new Button("Open");
        Button saveBtn = new Button("Save");
        return new ToolBar(newBtn, openBtn, saveBtn);
    }

    public BorderPane getRoot() {
        return root;
    }
}
