package com.nemostudio.ide;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

/**
 * Handles uploading user-selected files to the remote data/sample directory.
 * Used by the "Upload Files" button (e.g. below Get Ready in panel 1).
 */
public final class UploadSampleFiles {

    private UploadSampleFiles() {}

    /**
     * Prompts the user to select files or folders, then uploads them to remote-path/data/sample.
     * Shows progress dialog and writes status to the terminal.
     */
    public static void execute(RemoteTerminalPanel terminal) {
        String remotePath = ConnectionStore.loadRemoteFolder();
        if (remotePath == null || remotePath.trim().isEmpty()) {
            terminal.appendOutput("[Upload Files] No remote folder selected. Use Project → Open Remote Folder first.\n");
            return;
        }

        String dataSamplePath = remotePath.trim() + "/data/sample";
        terminal.appendOutput("[Upload Files] Upload destination: " + dataSamplePath + "\n");

        Alert infoDialog = new Alert(Alert.AlertType.INFORMATION);
        infoDialog.setTitle("Select Files for Nemo Curator");
        infoDialog.setHeaderText("Upload Files or Folder");
        infoDialog.setContentText("Please choose files or a folder that will be processed for Nemo Curator.\n\n" +
                "These files will be uploaded to: " + dataSamplePath);
        infoDialog.showAndWait();

        Stage stage = new Stage();
        stage.setTitle("Select Files or Folder to Upload");

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Files or Folder to Upload to " + dataSamplePath);
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            terminal.appendOutput("[Upload Files] No files selected. Cancelled.\n");
            return;
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
                            terminal.appendOutput("[Upload Files] Upload cancelled.\n");
                        });
                        return;
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
                    terminal.appendOutput("[Upload Files] Upload completed. " + totalFiles + " file(s) uploaded.\n");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressDialog.setStatus("Upload failed: " + e.getMessage());
                    progressDialog.close();
                    terminal.appendOutput("[Upload Files] Upload failed: " + e.getMessage() + "\n");
                });
            }
        }).start();
    }
}
