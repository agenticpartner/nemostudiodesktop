package com.nemostudio.ide;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Saves and loads non-sensitive connection settings to an editable properties file (no database).
 * File: {@code ~/.nemostudio/connection.properties} — host, user, remote folder path, etc. User can inspect or edit.
 * Password is stored separately (encrypted) by SecurePasswordStore.
 */
public final class ConnectionStore {

    private static final String DIR_NAME = ".nemostudio";
    private static final String FILE_NAME = "connection.properties";
    private static final String KEY_HOST = "host";
    private static final String KEY_USER = "user";
    private static final String KEY_REMOTE_FOLDER = "remoteFolder";

    private ConnectionStore() {}

    public static Path getConfigPath() {
        String home = System.getProperty("user.home");
        return Path.of(home).resolve(DIR_NAME).resolve(FILE_NAME);
    }

    public static String loadHost() {
        return loadProperty(KEY_HOST);
    }

    public static String loadUsername() {
        String u = loadProperty(KEY_USER);
        return u.isEmpty() ? System.getProperty("user.name", "") : u;
    }

    public static String loadRemoteFolder() {
        return loadProperty(KEY_REMOTE_FOLDER);
    }

    private static String loadProperty(String key) {
        Path path = getConfigPath();
        if (!Files.isRegularFile(path)) {
            return "";
        }
        Properties p = new Properties();
        try (var in = Files.newInputStream(path)) {
            p.load(in);
            String v = p.getProperty(key, "").trim();
            return v == null ? "" : v;
        } catch (IOException e) {
            return "";
        }
    }

    public static void saveHost(String host) throws IOException {
        saveHostAndUser(host == null ? "" : host.trim(), null);
    }

    /**
     * Saves host and optionally username. Pass null for user to leave it unchanged.
     */
    public static void saveHostAndUser(String host, String user) throws IOException {
        Path path = getConfigPath();
        Path dir = path.getParent();
        if (dir != null && !Files.isDirectory(dir)) {
            Files.createDirectories(dir);
        }
        Properties p = new Properties();
        if (Files.isRegularFile(path)) {
            try (var in = Files.newInputStream(path)) {
                p.load(in);
            }
        }
        if (host != null) {
            p.setProperty(KEY_HOST, host.trim());
        }
        if (user != null) {
            p.setProperty(KEY_USER, user.trim());
        }
        try (var out = Files.newOutputStream(path)) {
            p.store(out, "Nemo Studio connection (editable)");
        }
    }

    /**
     * Saves the selected remote folder path. Other keys (host, user, etc.) are preserved.
     */
    public static void saveRemoteFolder(String remoteFolder) throws IOException {
        Path path = getConfigPath();
        Path dir = path.getParent();
        if (dir != null && !Files.isDirectory(dir)) {
            Files.createDirectories(dir);
        }
        Properties p = new Properties();
        if (Files.isRegularFile(path)) {
            try (var in = Files.newInputStream(path)) {
                p.load(in);
            }
        }
        p.setProperty(KEY_REMOTE_FOLDER, remoteFolder == null ? "" : remoteFolder.trim());
        try (var out = Files.newOutputStream(path)) {
            p.store(out, "Nemo Studio connection (editable)");
        }
    }
}
