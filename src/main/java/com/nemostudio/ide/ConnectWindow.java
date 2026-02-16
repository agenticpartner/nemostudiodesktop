package com.nemostudio.ide;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Window to specify hostname or IP, test connection, and save only when test succeeds.
 * Loads saved host from file on open; saves to file only when user clicks Save after a successful test.
 */
public class ConnectWindow {

    private static final int TEST_PORT = 443;
    private static final int TEST_TIMEOUT_MS = 5000;

    public static void show(Stage owner) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle("Connect");

        TextField hostField = new TextField();
        hostField.setPromptText("Hostname or IP address");
        hostField.setPrefWidth(320);
        String savedHost = ConnectionStore.loadHost();
        if (!savedHost.isEmpty()) {
            hostField.setText(savedHost);
        }

        Label messageLabel = new Label();
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(400);
        messageLabel.getStyleClass().add("connect-message");

        Button testButton = new Button("Test Connect");
        Button saveButton = new Button("Save");
        saveButton.setDisable(true);
        Button cancelButton = new Button("Cancel");

        Runnable updateMessage = () -> { messageLabel.setText(""); };

        hostField.textProperty().addListener((o, a, b) -> {
            saveButton.setDisable(true);
            updateMessage.run();
        });

        testButton.setOnAction(e -> {
            String host = hostField.getText().trim();
            messageLabel.setText("Testing...");
            messageLabel.setStyle("-fx-text-fill: gray;");
            testButton.setDisable(true);

            CompletableFuture.runAsync(() -> {
                String result = testConnection(host);
                boolean success = result == null;
                javafx.application.Platform.runLater(() -> {
                    testButton.setDisable(false);
                    if (success) {
                        messageLabel.setText("Connection successful.");
                        messageLabel.setStyle("-fx-text-fill: green;");
                        saveButton.setDisable(false);
                    } else {
                        messageLabel.setText("Connection failed: " + result);
                        messageLabel.setStyle("-fx-text-fill: #c00;");
                        saveButton.setDisable(true);
                    }
                });
            });
        });

        saveButton.setOnAction(ev -> {
            String host = hostField.getText().trim();
            try {
                ConnectionStore.saveHost(host);
                messageLabel.setText("Saved.");
                messageLabel.setStyle("-fx-text-fill: green;");
                stage.close();
            } catch (IOException ex) {
                messageLabel.setText("Could not save: " + ex.getMessage());
                messageLabel.setStyle("-fx-text-fill: #c00;");
            }
        });

        cancelButton.setOnAction(e -> stage.close());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));
        grid.add(new Label("Hostname or IP:"), 0, 0);
        grid.add(hostField, 1, 0);
        grid.add(messageLabel, 0, 1, 2, 1);
        grid.add(new HBox(10, testButton, saveButton, cancelButton), 0, 2, 2, 1);

        HBox.setMargin(testButton, new Insets(0, 0, 0, 0));
        HBox.setMargin(saveButton, new Insets(0, 0, 0, 0));
        HBox.setMargin(cancelButton, new Insets(0, 0, 0, 0));

        Scene scene = new Scene(grid);
        java.net.URL css = ConnectWindow.class.getResource("/styles/ide.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);
        stage.setMinWidth(420);
        stage.setResizable(false);
        stage.show();
    }

    /**
     * Validates host: uses system ping (so .local/mDNS works like in terminal), then fallback to
     * Java resolve + reachability or TCP port. Returns null on success, error message on failure.
     */
    private static String testConnection(String host) {
        if (host == null || host.isBlank()) {
            return "Please enter a hostname or IP address.";
        }
        // Prefer system ping so .local (mDNS) and behavior match the OS terminal
        String pingError = runSystemPing(host);
        if (pingError == null) {
            return null;
        }
        // Fallback: Java resolution + reachability or TCP (for environments where ping is blocked)
        InetAddress addr;
        try {
            addr = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            return "Unknown host (and ping failed): " + e.getMessage();
        }
        try {
            if (addr.isReachable(TEST_TIMEOUT_MS)) {
                return null;
            }
        } catch (IOException ignored) {
            // try TCP next
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(addr, TEST_PORT), TEST_TIMEOUT_MS);
            return null;
        } catch (IOException e) {
            return "Reachable via ping in terminal but not from this app. " + pingError;
        }
    }

    /**
     * Run OS ping to host. Returns null if ping succeeded, otherwise an error string.
     * Uses the same resolver as the terminal (so .local / mDNS works on macOS).
     */
    private static String runSystemPing(String host) {
        List<String> command;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            command = List.of("ping", "-n", "1", "-w", String.valueOf(TEST_TIMEOUT_MS), host);
        } else {
            command = List.of("ping", "-c", "1", "-t", "3", host);
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            p.getInputStream().readAllBytes(); // consume output
            int exit = p.waitFor();
            return exit == 0 ? null : "ping exit " + exit;
        } catch (IOException e) {
            return e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
    }
}
