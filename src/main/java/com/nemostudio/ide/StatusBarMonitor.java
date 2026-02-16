package com.nemostudio.ide;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Background monitor that runs every 5 seconds. In each cycle it:
 * 1) Checks connectivity to the saved remote host (TCP port 22) and updates the status bar
 *    ("Connected" + green icon or "Disconnected" + gray icon).
 * 2) Re-reads the remote folder path from the config file and updates the status bar
 *    ("Remote folder: not set" or "Remote folder: &lt;path&gt;").
 */
public final class StatusBarMonitor {

    private static final double POLL_INTERVAL_SECONDS = 5.0;
    private static final int CONNECT_CHECK_TIMEOUT_MS = 3000;
    private static final int SSH_PORT = 22;

    private Timeline timeline;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "status-bar-connectivity");
        t.setDaemon(true);
        return t;
    });

    public void start(Region connectionIndicator, Label connectionLabel, Label remoteFolderLabel) {
        stop();
        timeline = new Timeline(
                new KeyFrame(Duration.seconds(POLL_INTERVAL_SECONDS), e -> {
                    String host = ConnectionStore.loadHost();
                    if (host == null || host.trim().isEmpty()) {
                        Platform.runLater(() -> updateUi(false, connectionIndicator, connectionLabel, remoteFolderLabel));
                        return;
                    }
                    executor.execute(() -> {
                        boolean reachable = checkConnectivity(host.trim());
                        Platform.runLater(() -> updateUi(reachable, connectionIndicator, connectionLabel, remoteFolderLabel));
                    });
                })
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        // Run once immediately
        executor.execute(() -> {
            String host = ConnectionStore.loadHost();
            boolean reachable = host != null && !host.trim().isEmpty() && checkConnectivity(host.trim());
            Platform.runLater(() -> updateUi(reachable, connectionIndicator, connectionLabel, remoteFolderLabel));
        });
    }

    private static boolean checkConnectivity(String host) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, SSH_PORT), CONNECT_CHECK_TIMEOUT_MS);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Updates connection status and remote folder path (both refreshed every 5s from the same cycle). */
    private static void updateUi(boolean connected, Region connectionIndicator, Label connectionLabel, Label remoteFolderLabel) {
        connectionLabel.setText(connected ? "Connected" : "Disconnected");
        connectionIndicator.getStyleClass().removeAll("status-connected", "status-disconnected");
        connectionIndicator.getStyleClass().add(connected ? "status-connected" : "status-disconnected");
        // Re-read remote folder path every 5s so status bar stays in sync with config file
        String path = ConnectionStore.loadRemoteFolder();
        remoteFolderLabel.setText(path == null || path.trim().isEmpty()
                ? "Remote folder: not set"
                : "Remote folder: " + path.trim());
    }

    public void stop() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
    }
}
