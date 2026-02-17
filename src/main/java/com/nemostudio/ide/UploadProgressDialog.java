package com.nemostudio.ide;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Progress dialog for file uploads. Shows current file, progress bar, and status messages.
 */
public class UploadProgressDialog {

    private final Stage stage;
    private final Label statusLabel;
    private final Label currentFileLabel;
    private final ProgressBar progressBar;
    private final Button cancelButton;
    private volatile boolean cancelled = false;

    public UploadProgressDialog(Window owner) {
        stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle("Uploading Files");
        stage.setResizable(false);

        statusLabel = new Label("Preparing upload...");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(500);

        currentFileLabel = new Label("");
        currentFileLabel.setWrapText(true);
        currentFileLabel.setMaxWidth(500);
        currentFileLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888;");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(500);
        progressBar.setPrefHeight(20);

        cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> {
            cancelled = true;
            statusLabel.setText("Cancelling...");
            cancelButton.setDisable(true);
        });

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().addAll(statusLabel, currentFileLabel, progressBar);

        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 20, 20, 20));
        buttonBox.getChildren().add(cancelButton);

        BorderPane root = new BorderPane();
        root.setCenter(content);
        root.setBottom(buttonBox);

        Scene scene = new Scene(root);
        java.net.URL css = UploadProgressDialog.class.getResource("/styles/ide.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene);
    }

    public void show() {
        stage.show();
    }

    public void close() {
        Platform.runLater(() -> stage.close());
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    public void setCurrentFile(String fileName) {
        Platform.runLater(() -> {
            if (fileName != null && !fileName.isEmpty()) {
                currentFileLabel.setText("File: " + fileName);
            } else {
                currentFileLabel.setText("");
            }
        });
    }

    public void setProgress(double progress) {
        Platform.runLater(() -> progressBar.setProgress(Math.max(0, Math.min(1, progress))));
    }

    public void onFileStart(String fileName) {
        setCurrentFile(fileName);
        setProgress(0);
    }

    public void onFileProgress(long bytesTransferred, long totalBytes) {
        if (totalBytes > 0) {
            double progress = (double) bytesTransferred / totalBytes;
            setProgress(progress);
            String sizeStr = formatBytes(bytesTransferred) + " / " + formatBytes(totalBytes);
            Platform.runLater(() -> {
                String currentText = currentFileLabel.getText();
                if (currentText.startsWith("File: ")) {
                    String fileName = fileNameFromLabel(currentText);
                    currentFileLabel.setText("File: " + fileName + " (" + sizeStr + ")");
                }
            });
        }
    }

    public void onFileComplete(String fileName, long fileSize) {
        setProgress(1.0);
        Platform.runLater(() -> {
            currentFileLabel.setText("Completed: " + fileName + " (" + formatBytes(fileSize) + ")");
        });
    }

    private String fileNameFromLabel(String label) {
        if (label.startsWith("File: ")) {
            int idx = label.indexOf(" (");
            if (idx > 0) {
                return label.substring(6, idx);
            }
            return label.substring(6);
        }
        if (label.startsWith("Completed: ")) {
            int idx = label.indexOf(" (");
            if (idx > 0) {
                return label.substring(11, idx);
            }
            return label.substring(11);
        }
        return "";
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
