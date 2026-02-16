package com.nemostudio.ide;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.util.Duration;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Vector;
import java.util.Optional;

/**
 * Window to browse folders on the remote host (from Connect). Uses SFTP over SSH.
 * User enters username/password (or uses default SSH key), then navigates the remote file system.
 */
public class RemoteFolderWindow {

    private static final int SSH_PORT = 22;
    private static final int CONNECT_TIMEOUT_MS = 10000;

    public static void show(Stage owner) {
        Stage stage = new Stage();
        stage.initModality(Modality.NONE);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle("Open Remote Folder");

        String savedHost = ConnectionStore.loadHost();
        String savedUser = ConnectionStore.loadUsername();
        TextField hostField = new TextField(savedHost);
        hostField.setPromptText("Hostname or IP");
        hostField.setPrefWidth(200);
        TextField userField = new TextField(savedUser);
        userField.setPromptText("Username");
        userField.setPrefWidth(120);
        PasswordField passField = new PasswordField();
        passField.setPromptText("Password (empty = use SSH key)");
        passField.setPrefWidth(140);
        char[] savedPassword = SecurePasswordStore.loadPassword();
        if (savedPassword != null && savedPassword.length > 0) {
            passField.setText(new String(savedPassword));
            java.util.Arrays.fill(savedPassword, '\0');
        }
        Button connectBtn = new Button("Connect");
        Label statusLabel = new Label("Enter credentials and click Connect. Password is saved automatically after a successful connection.");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(500);

        Label pathLabel = new Label("/");
        pathLabel.setWrapText(true);
        Button upBtn = new Button("Up");
        upBtn.setDisable(true);
        Button selectBtn = new Button("Select");
        selectBtn.setDisable(true);
        Button newFolderBtn = new Button("New folder");
        newFolderBtn.setDisable(true);
        Button renameBtn = new Button("Rename");
        renameBtn.setDisable(true);
        Button deleteBtn = new Button("Delete");
        deleteBtn.setDisable(true);
        Label selectionLabel = new Label("");
        selectionLabel.setWrapText(true);
        ListView<RemoteEntry> listView = new ListView<>();
        listView.setPrefSize(520, 280);
        listView.setPlaceholder(new Label("Not connected."));

        GridPane connectPane = new GridPane();
        connectPane.setHgap(8);
        connectPane.setVgap(8);
        connectPane.setPadding(new Insets(12));
        connectPane.add(new Label("Host:"), 0, 0);
        connectPane.add(hostField, 1, 0);
        connectPane.add(new Label("Username:"), 0, 1);
        connectPane.add(userField, 1, 1);
        connectPane.add(new Label("Password:"), 0, 2);
        connectPane.add(passField, 1, 2);
        connectPane.add(connectBtn, 1, 3);
        connectPane.add(statusLabel, 0, 4, 2, 1);

        VBox listPane = new VBox(6);
        listPane.setPadding(new Insets(8));
        HBox pathBar = new HBox(8, new Label("Path:"), pathLabel, upBtn, selectBtn);
        pathBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox folderToolbar = new HBox(8, newFolderBtn, renameBtn, deleteBtn);
        folderToolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        listPane.getChildren().addAll(pathBar, folderToolbar, selectionLabel, listView);
        listPane.setVisible(false);

        BorderPane root = new BorderPane();
        root.setTop(connectPane);
        root.setCenter(listPane);

        Session[] sessionHolder = { null };
        ChannelSftp[] channelHolder = { null };

        Runnable disconnect = () -> {
            if (channelHolder[0] != null) {
                try {
                    channelHolder[0].disconnect();
                } catch (Exception ignored) {}
                channelHolder[0] = null;
            }
            if (sessionHolder[0] != null) {
                sessionHolder[0].disconnect();
                sessionHolder[0] = null;
            }
            listPane.setVisible(false);
            listView.setPlaceholder(new Label("Not connected."));
            connectPane.setDisable(false);
            upBtn.setDisable(true);
            selectBtn.setDisable(true);
            newFolderBtn.setDisable(true);
            renameBtn.setDisable(true);
            deleteBtn.setDisable(true);
            statusLabel.setText("Disconnected.");
            ConnectionState.setConnected(false);
        };

        stage.setOnCloseRequest(e -> disconnect.run());

        Runnable doConnect = () -> {
            String host = hostField.getText().trim();
            String user = userField.getText().trim();
            String pass = passField.getText();
            if (host.isEmpty() || user.isEmpty()) {
                statusLabel.setText("Please enter host and username.");
                return;
            }
            statusLabel.setText("Connecting...");
            connectPane.setDisable(true);
            new Thread(() -> {
                try {
                    JSch jsch = new JSch();
                    Session session = jsch.getSession(user, host, SSH_PORT);
                    if (pass != null && !pass.isEmpty()) {
                        session.setPassword(pass);
                    }
                    session.setConfig("StrictHostKeyChecking", "no");
                    session.connect(CONNECT_TIMEOUT_MS);
                    ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
                    channel.connect(CONNECT_TIMEOUT_MS);
                    sessionHolder[0] = session;
                    channelHolder[0] = channel;
                    final String hostToSave = host;
                    final String userToSave = user;
                    final String passToSave = pass;
                    Platform.runLater(() -> {
                        ConnectionState.setConnected(true);
                        statusLabel.setText("Connected. Credentials saved.");
                        listPane.setVisible(true);
                        listView.setPlaceholder(new Label("No folders in this directory."));
                        selectBtn.setDisable(false);
                        newFolderBtn.setDisable(false);
                        String savedPath = ConnectionStore.loadRemoteFolder();
                        if (savedPath != null && !savedPath.trim().isEmpty()) {
                            loadList(channel, savedPath.trim(), listView, pathLabel, upBtn);
                        } else {
                            loadList(channel, null, listView, pathLabel, upBtn);
                        }
                        try {
                            ConnectionStore.saveHostAndUser(hostToSave, userToSave);
                            if (passToSave != null && !passToSave.isEmpty()) {
                                SecurePasswordStore.savePassword(passToSave.toCharArray());
                            } else {
                                SecurePasswordStore.deleteSavedPassword();
                            }
                        } catch (Exception ignored) {
                            statusLabel.setText("Connected. (Could not save credentials.)");
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        ConnectionState.setConnected(false);
                        statusLabel.setText("Connection failed: " + ex.getMessage());
                        connectPane.setDisable(false);
                    });
                }
            }).start();
        };

        connectBtn.setOnAction(e -> doConnect.run());

        selectBtn.setOnAction(ev -> {
            String currentPath = pathLabel.getText();
            try {
                ConnectionStore.saveRemoteFolder(currentPath);
                selectionLabel.setText("Selected and saved: " + currentPath);
            } catch (Exception ex) {
                selectionLabel.setText("Could not save: " + ex.getMessage());
            }
        });

        upBtn.setOnAction(ev -> {
            ChannelSftp ch = channelHolder[0];
            if (ch == null) return;
            String current = pathLabel.getText();
            if ("/".equals(current)) return;
            String parent = current.endsWith("/") ? current.substring(0, current.length() - 1) : current;
            int last = parent.lastIndexOf('/');
            parent = last <= 0 ? "/" : parent.substring(0, last);
            if (parent.isEmpty()) parent = "/";
            loadList(ch, parent, listView, pathLabel, upBtn);
        });

        listView.getSelectionModel().selectedItemProperty().addListener((o, old, item) -> {
            boolean hasSelection = item != null;
            renameBtn.setDisable(!hasSelection);
            deleteBtn.setDisable(!hasSelection);
        });

        listView.setOnMouseClicked(me -> {
            if (me.getButton() != MouseButton.PRIMARY || me.getClickCount() != 2) return;
            RemoteEntry item = listView.getSelectionModel().getSelectedItem();
            if (item == null || !item.isDir) return;
            ChannelSftp ch = channelHolder[0];
            if (ch == null) return;
            String base = pathLabel.getText();
            if (!base.endsWith("/")) base += "/";
            String next = base + item.name;
            loadList(ch, next, listView, pathLabel, upBtn);
        });

        Runnable refreshCurrentList = () -> {
            ChannelSftp ch = channelHolder[0];
            if (ch != null) loadList(ch, pathLabel.getText(), listView, pathLabel, upBtn);
        };

        newFolderBtn.setOnAction(ev -> {
            ChannelSftp ch = channelHolder[0];
            if (ch == null) return;
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("New folder");
            dialog.setHeaderText("Create a new folder in the current directory.");
            dialog.setContentText("Folder name:");
            dialog.getEditor().setText("");
            Optional<String> result = dialog.showAndWait();
            result.filter(s -> s != null && !(s = s.trim()).isEmpty())
                    .ifPresent(name -> {
                        if (name.contains("/") || name.equals(".") || name.equals("..")) {
                            showError("Invalid name", "Folder name cannot contain '/' or be '.' or '..'.");
                            return;
                        }
                        try {
                            ch.mkdir(name);
                            refreshCurrentList.run();
                        } catch (Exception ex) {
                            showError("Could not create folder", ex.getMessage());
                        }
                    });
        });

        renameBtn.setOnAction(ev -> {
            ChannelSftp ch = channelHolder[0];
            if (ch == null) return;
            RemoteEntry item = listView.getSelectionModel().getSelectedItem();
            if (item == null) return;
            String currentPath = pathLabel.getText();
            String base = currentPath.endsWith("/") ? currentPath : currentPath + "/";
            String oldFull = base + item.name;
            TextInputDialog dialog = new TextInputDialog(item.name);
            dialog.setTitle("Rename folder");
            dialog.setHeaderText("Enter the new name for \"" + item.name + "\".");
            dialog.setContentText("New name:");
            Optional<String> result = dialog.showAndWait();
            result.filter(s -> s != null && !(s = s.trim()).isEmpty())
                    .ifPresent(newName -> {
                        if (newName.contains("/") || newName.equals(".") || newName.equals("..")) {
                            showError("Invalid name", "Folder name cannot contain '/' or be '.' or '..'.");
                            return;
                        }
                        if (newName.equals(item.name)) return;
                        String newFull = base + newName;
                        try {
                            ch.rename(oldFull, newFull);
                            refreshCurrentList.run();
                        } catch (Exception ex) {
                            showError("Could not rename folder", ex.getMessage());
                        }
                    });
        });

        deleteBtn.setOnAction(ev -> {
            ChannelSftp ch = channelHolder[0];
            if (ch == null) return;
            RemoteEntry item = listView.getSelectionModel().getSelectedItem();
            if (item == null) return;
            String currentPath = pathLabel.getText();
            String base = currentPath.endsWith("/") ? currentPath : currentPath + "/";
            String fullPath = base + item.name;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete folder");
            confirm.setHeaderText("Delete \"" + item.name + "\"?");
            confirm.setContentText("This folder and all its contents will be permanently removed. This cannot be undone.");
            Optional<ButtonType> choice = confirm.showAndWait();
            if (choice.isPresent() && choice.get() == ButtonType.OK) {
                try {
                    deleteRecursive(ch, fullPath);
                    refreshCurrentList.run();
                } catch (Exception ex) {
                    showError("Could not delete folder", ex.getMessage());
                }
            }
        });

        Scene scene = new Scene(root, 560, 520);
        java.net.URL css = RemoteFolderWindow.class.getResource("/styles/ide.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(400);
        stage.setMinHeight(400);
        stage.show();

        // If we have saved host and user (from a previous connection), auto-connect and list folders (password or SSH key)
        boolean hasSavedHostAndUser = savedHost != null && !savedHost.trim().isEmpty() && savedUser != null && !savedUser.trim().isEmpty();
        if (hasSavedHostAndUser) {
            PauseTransition delay = new PauseTransition(Duration.millis(300));
            delay.setOnFinished(e -> {
                if (!hostField.getText().trim().isEmpty() && !userField.getText().trim().isEmpty()) {
                    statusLabel.setText("Connecting automatically...");
                    doConnect.run();
                }
            });
            delay.play();
        }
    }

    private static void showError(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }

    /** Recursively delete a folder and all its contents over SFTP. */
    private static void deleteRecursive(ChannelSftp channel, String fullPath) throws Exception {
        Vector<?> list = channel.ls(fullPath);
        for (Object o : list) {
            ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) o;
            String name = entry.getFilename();
            if (".".equals(name) || "..".equals(name)) continue;
            String childPath = fullPath.endsWith("/") ? fullPath + name : fullPath + "/" + name;
            if (entry.getAttrs().isDir()) {
                deleteRecursive(channel, childPath);
            } else {
                channel.rm(childPath);
            }
        }
        channel.rmdir(fullPath);
    }

    private static void loadList(ChannelSftp channel, String path, ListView<RemoteEntry> listView,
                                 Label pathLabel, Button upBtn) {
        try {
            if (path == null || path.isEmpty()) {
                path = channel.pwd();
            } else {
                channel.cd(path);
            }
            pathLabel.setText(path);
            upBtn.setDisable("/".equals(path));

            listView.getItems().clear();
            Vector<?> list = channel.ls(".");
            for (Object o : list) {
                ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) o;
                String name = entry.getFilename();
                if (".".equals(name) || "..".equals(name)) continue;
                SftpATTRS attrs = entry.getAttrs();
                if (!attrs.isDir()) continue; // list only folders
                listView.getItems().add(new RemoteEntry(name, true));
            }
            listView.getItems().sort((a, b) -> {
                if (a.isDir != b.isDir) return a.isDir ? -1 : 1;
                return String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name);
            });
        } catch (Exception e) {
            pathLabel.setText(path + " — error: " + e.getMessage());
        }
    }

    private static class RemoteEntry {
        final String name;
        final boolean isDir;
        RemoteEntry(String name, boolean isDir) {
            this.name = name;
            this.isDir = isDir;
        }
        @Override
        public String toString() {
            return (isDir ? "[DIR]  " : "       ") + name;
        }
    }
}
