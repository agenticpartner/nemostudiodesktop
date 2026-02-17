package com.nemostudio.ide;

import javafx.application.Platform;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Handles logic for the first Get Ready button. Runs GetReady01.sh on the remote terminal.
 * Script files from resources are uploaded to remote scripts/example before running.
 * Use the "Upload Files" button to upload sample data to data/sample.
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
        runScript(terminal, remotePath.trim());
    }

    private static void runScript(RemoteTerminalPanel terminal, String remotePath) {
        // Upload script files from resources to remote scripts/example directory
        uploadScriptFiles(terminal, remotePath, () -> {
            // After upload completes, run the main script
            String script = loadScript(SCRIPT_RESOURCE);
            if (script != null) {
                String safePath = remotePath.trim().replace("'", "'\"'\"'");
                script = "REMOTE_PATH='" + safePath + "'\n" + script;
                terminal.runScript(script);
            } else {
                terminal.appendOutput("[GetReady01] Could not load script " + SCRIPT_RESOURCE + "\n");
            }
        });
    }

    private static void uploadScriptFiles(RemoteTerminalPanel terminal, String remotePath, Runnable onComplete) {
        String scriptsResourcePath = "/data/getready01";
        String remoteScriptsPath = remotePath.trim() + "/example";
        
        terminal.appendOutput("[GetReady01] Uploading example folder to " + remoteScriptsPath + "...\n");
        
        new Thread(() -> {
            try {
                java.net.URL resourceUrl = GetReady01.class.getResource(scriptsResourcePath);
                if (resourceUrl == null) {
                    Platform.runLater(() -> {
                        terminal.appendOutput("[GetReady01] Resource not found: " + scriptsResourcePath + "\n");
                        onComplete.run();
                    });
                    return;
                }
                
                SftpHelper.ProgressCallback callback = new SftpHelper.ProgressCallback() {
                    @Override
                    public void onFileStart(String fileName) {
                        Platform.runLater(() -> terminal.appendOutput("[GetReady01] Uploading " + fileName + "\n"));
                    }
                    @Override
                    public void onFileProgress(long bytesTransferred, long totalBytes) {}
                    @Override
                    public void onFileComplete(String fileName, long fileSize) {
                        Platform.runLater(() -> terminal.appendOutput("[GetReady01] Uploaded " + fileName + " (" + fileSize + " bytes)\n"));
                    }
                };
                
                if ("file".equals(resourceUrl.getProtocol())) {
                    // Running from IDE: resource is a real folder — upload it directly (whole nested tree)
                    File localFolder;
                    try {
                        localFolder = new File(resourceUrl.toURI());
                    } catch (URISyntaxException e) {
                        throw new IOException("Invalid resource URI", e);
                    }
                    if (!localFolder.exists() || !localFolder.isDirectory()) {
                        Platform.runLater(() -> {
                            terminal.appendOutput("[GetReady01] Resource path is not a directory.\n");
                            onComplete.run();
                        });
                        return;
                    }
                    File[] topLevel = localFolder.listFiles();
                    if (topLevel == null || topLevel.length == 0) {
                        Platform.runLater(() -> {
                            terminal.appendOutput("[GetReady01] Example folder is empty.\n");
                            onComplete.run();
                        });
                        return;
                    }
                    for (File child : topLevel) {
                        if (child.getName().equals(".gitkeep")) continue;
                        SftpHelper.upload(child, remoteScriptsPath, callback);
                    }
                    Platform.runLater(() -> {
                        terminal.appendOutput("[GetReady01] Example folder uploaded successfully.\n");
                        onComplete.run();
                    });
                    return;
                }
                
                // Running from JAR: zip the example folder and upload example.zip; script will unzip and remove it
                if ("jar".equals(resourceUrl.getProtocol())) {
                    uploadExampleFromJar(scriptsResourcePath, remotePath.trim(), terminal, callback, onComplete);
                } else {
                    Platform.runLater(() -> {
                        terminal.appendOutput("[GetReady01] Unsupported resource protocol: " + resourceUrl.getProtocol() + "\n");
                        onComplete.run();
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    terminal.appendOutput("[GetReady01] Error uploading example folder: " + e.getMessage() + "\n");
                    onComplete.run();
                });
            }
        }).start();
    }
    
    /** When running from JAR: extract resource tree to temp dir, zip it, upload the zip to remote; script will unzip and remove it. */
    private static void uploadExampleFromJar(String scriptsResourcePath, String remotePath,
            RemoteTerminalPanel terminal, SftpHelper.ProgressCallback callback, Runnable onComplete) {
        try {
            String normalizedPath = scriptsResourcePath.startsWith("/") ? scriptsResourcePath.substring(1) : scriptsResourcePath;
            if (!normalizedPath.endsWith("/")) normalizedPath += "/";
            java.net.URL resourceUrl = GetReady01.class.getResource("/" + normalizedPath);
            if (resourceUrl == null) {
                Platform.runLater(() -> { terminal.appendOutput("[GetReady01] Resource not found.\n"); onComplete.run(); });
                return;
            }
            String jarPath = resourceUrl.getPath().substring(5, resourceUrl.getPath().indexOf("!"));
            List<String> filePaths = new ArrayList<>();
            List<String> dirPaths = new ArrayList<>();
            try (JarFile jar = new JarFile(jarPath)) {
                final String prefix = normalizedPath;
                jar.stream()
                        .filter(entry -> entry.getName().startsWith(prefix) && !entry.getName().equals(prefix))
                        .forEach(entry -> {
                            String name = entry.getName().substring(prefix.length());
                            if (name.isEmpty() || name.equals(".gitkeep")) return;
                            if (name.endsWith("/")) dirPaths.add(name.substring(0, name.length() - 1));
                            else filePaths.add(name);
                        });
            }
            if (filePaths.isEmpty() && dirPaths.isEmpty()) {
                Platform.runLater(() -> { terminal.appendOutput("[GetReady01] No content in resource.\n"); onComplete.run(); });
                return;
            }
            Path tempDir = Files.createTempDirectory("nemostudio-example-");
            File tempDirFile = tempDir.toFile();
            try {
                dirPaths.sort(Comparator.comparingInt(String::length));
                for (String d : dirPaths) {
                    if (d.equals(".gitkeep")) continue;
                    Files.createDirectories(new File(tempDirFile, d).toPath());
                }
                for (String relativePath : filePaths) {
                    if (relativePath.equals(".gitkeep")) continue;
                    try (InputStream in = GetReady01.class.getResourceAsStream("/" + normalizedPath + relativePath)) {
                        if (in != null) {
                            File dest = new File(tempDirFile, relativePath);
                            Files.createDirectories(dest.getParentFile().toPath());
                            Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
                // Zip the contents of temp dir (so zip has top-level entries: input/, scripts/, etc.)
                File zipFile = new File(tempDir.toFile().getParent(), "example.zip");
                createZipFromDir(tempDirFile, zipFile);
                Platform.runLater(() -> terminal.appendOutput("[GetReady01] Uploading example.zip...\n"));
                // Upload zip to remote folder root (script will unzip -d example and remove zip)
                SftpHelper.upload(zipFile, remotePath.trim(), callback);
                Files.deleteIfExists(zipFile.toPath());
                Platform.runLater(() -> {
                    terminal.appendOutput("[GetReady01] Example zip uploaded. Script will unzip it on the remote.\n");
                    onComplete.run();
                });
            } finally {
                deleteRecursively(tempDirFile);
            }
        } catch (Exception e) {
            Platform.runLater(() -> {
                terminal.appendOutput("[GetReady01] Error uploading from JAR: " + e.getMessage() + "\n");
                onComplete.run();
            });
        }
    }
    
    /** Create a zip file with the contents of dir (entry names are relative to dir, e.g. input/foo, scripts/bar). */
    private static void createZipFromDir(File dir, File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile.toPath()))) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.getName().equals(".gitkeep")) continue;
                    addToZip(zos, child, "");
                }
            }
        }
    }
    
    private static void addToZip(ZipOutputStream zos, File file, String prefix) throws IOException {
        String entryName = prefix.isEmpty() ? file.getName() : prefix + "/" + file.getName();
        if (file.isDirectory()) {
            zos.putNextEntry(new ZipEntry(entryName + "/"));
            zos.closeEntry();
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.getName().equals(".gitkeep")) continue; // skip so remote gets empty dirs
                    addToZip(zos, child, entryName);
                }
            }
        } else {
            if (file.getName().equals(".gitkeep")) return; // don't add .gitkeep to zip; dir already added
            zos.putNextEntry(new ZipEntry(entryName));
            try (FileInputStream in = new FileInputStream(file)) {
                in.transferTo(zos);
            }
            zos.closeEntry();
        }
    }
    
    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        try { Files.deleteIfExists(file.toPath()); } catch (IOException ignored) {}
    }

    private static String loadScript(String resource) {
        try (InputStream in = GetReady01.class.getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
