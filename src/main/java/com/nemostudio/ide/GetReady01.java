package com.nemostudio.ide;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Handles logic for the first Get Ready button. Runs GetReady01.sh on the remote terminal.
 * If remote-path/data/sample is empty, prompts user to upload files first.
 * If user cancels file selection and directory is empty, execution stops.
 */
public final class GetReady01 {

    private static final String SCRIPT_RESOURCE = "/scripts/GetReady01.sh";

    private GetReady01() {}

    public static void execute(RemoteTerminalPanel terminal) {
        String remotePath = ConnectionStore.loadRemoteFolder();
        if (remotePath == null || remotePath.trim().isEmpty()) {
            terminal.appendOutput("[GetReady01] No remote folder selected. Use Project → Open Remote Folder first.\n");
            return;
        }

        // FIRST: Validate if files exist in remote-path/data/sample
        String dataSamplePath = remotePath.trim() + "/data/sample";
        terminal.appendOutput("[GetReady01] Validating if " + dataSamplePath + " contains files...\n");

        final String baseRemotePath = remotePath.trim();
        new Thread(() -> {
            boolean isEmptyResult = true;
            try {
                isEmptyResult = SftpHelper.isDirectoryEmpty(dataSamplePath);
                final boolean isEmpty = isEmptyResult;
                Platform.runLater(() -> {
                    terminal.appendOutput("[GetReady01] Directory " + (isEmpty ? "is empty" : "contains files") + ".\n");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    terminal.appendOutput("[GetReady01] Error validating directory: " + e.getMessage() + "\n");
                    terminal.appendOutput("[GetReady01] Assuming directory is empty and proceeding...\n");
                });
                isEmptyResult = true;
            }

            final boolean needsUpload = isEmptyResult;
            Platform.runLater(() -> {
                if (needsUpload) {
                    promptAndUpload(terminal, dataSamplePath, baseRemotePath);
                } else {
                    runScript(terminal, baseRemotePath);
                }
            });
        }).start();
    }

    private static void promptAndUpload(RemoteTerminalPanel terminal, String dataSamplePath, String baseRemotePath) {
        // Show informational dialog first
        Alert infoDialog = new Alert(Alert.AlertType.INFORMATION);
        infoDialog.setTitle("Select Files for Nemo Curator");
        infoDialog.setHeaderText("Upload Files or Folder");
        infoDialog.setContentText("Please choose a folder or files that will be processed for Nemo Curator.\n\n" +
                "These files will be uploaded to: " + dataSamplePath);
        infoDialog.showAndWait();

        Stage stage = new Stage();
        stage.setTitle("Select Files or Folder to Upload");

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Files or Folder to Upload to " + dataSamplePath);
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            terminal.appendOutput("[GetReady01] No files selected. Directory is empty. Stopping execution.\n");
            return; // STOP execution - don't run script
        }

        UploadProgressDialog progressDialog = new UploadProgressDialog(stage);
        progressDialog.show();
        progressDialog.setStatus("Uploading to " + dataSamplePath + "...");

        new Thread(() -> {
            try {
                long totalFiles = selectedFiles.size();
                final long[] currentFile = {0};
                for (File file : selectedFiles) {
                    if (progressDialog.isCancelled()) {
                        Platform.runLater(() -> {
                            progressDialog.setStatus("Upload cancelled.");
                            progressDialog.close();
                            terminal.appendOutput("[GetReady01] Upload cancelled. Stopping execution.\n");
                        });
                        return; // STOP execution - don't run script
                    }
                    currentFile[0]++;
                    final long fileNum = currentFile[0];
                    Platform.runLater(() -> {
                        progressDialog.setStatus(String.format("Uploading file %d of %d...", fileNum, totalFiles));
                    });
                    SftpHelper.upload(file, dataSamplePath, new SftpHelper.ProgressCallback() {
                        @Override
                        public void onFileStart(String fileName) {
                            progressDialog.onFileStart(fileName);
                        }
                        @Override
                        public void onFileProgress(long bytesTransferred, long totalBytes) {
                            progressDialog.onFileProgress(bytesTransferred, totalBytes);
                        }
                        @Override
                        public void onFileComplete(String fileName, long fileSize) {
                            progressDialog.onFileComplete(fileName, fileSize);
                        }
                    });
                }
                Platform.runLater(() -> {
                    progressDialog.setStatus("Upload completed successfully.");
                    progressDialog.setProgress(1.0);
                    progressDialog.close();
                    terminal.appendOutput("[GetReady01] Upload completed. " + totalFiles + " file(s) uploaded.\n");
                    runScript(terminal, baseRemotePath);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressDialog.setStatus("Upload failed: " + e.getMessage());
                    progressDialog.close();
                    terminal.appendOutput("[GetReady01] Upload failed: " + e.getMessage() + "\n");
                    terminal.appendOutput("[GetReady01] Stopping execution due to upload failure.\n");
                });
                // STOP execution - don't run script if upload fails
            }
        }).start();
    }

    private static void runScript(RemoteTerminalPanel terminal, String remotePath) {
        String script = loadScript(SCRIPT_RESOURCE);
        if (script != null) {
            String safePath = remotePath.trim().replace("'", "'\"'\"'");
            script = "REMOTE_PATH='" + safePath + "'\n" + script;
            terminal.runScript(script);
        } else {
            terminal.appendOutput("[GetReady01] Could not load script " + SCRIPT_RESOURCE + "\n");
        }
    }

    private static String loadScript(String resource) {
        try (InputStream in = GetReady01.class.getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
