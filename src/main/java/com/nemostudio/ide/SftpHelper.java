package com.nemostudio.ide;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;

import java.io.File;
import java.io.FileInputStream;
import java.util.Vector;

/**
 * Helper for SFTP operations: check directory contents, upload files with progress tracking.
 */
public final class SftpHelper {

    private static final int SSH_PORT = 22;
    private static final int CONNECT_TIMEOUT_MS = 10000;

    private SftpHelper() {}

    /**
     * Create an SFTP session using saved credentials. Caller must disconnect when done.
     */
    public static Session createSftpSession() throws Exception {
        String host = ConnectionStore.loadHost();
        String user = ConnectionStore.loadUsername();
        if (host == null || host.trim().isEmpty() || user == null || user.trim().isEmpty()) {
            throw new Exception("No host or username saved. Use Project → Connect first.");
        }
        char[] passChars = SecurePasswordStore.loadPassword();
        String pass = (passChars != null && passChars.length > 0) ? new String(passChars) : null;
        if (passChars != null) java.util.Arrays.fill(passChars, '\0');

        JSch jsch = new JSch();
        Session session = jsch.getSession(user, host.trim(), SSH_PORT);
        if (pass != null && !pass.isEmpty()) {
            session.setPassword(pass);
        }
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(CONNECT_TIMEOUT_MS);
        return session;
    }

    /**
     * Check if a remote directory exists and is empty (no files or subdirectories except . and ..).
     * Returns true if directory doesn't exist or is empty.
     */
    public static boolean isDirectoryEmpty(String remotePath) throws Exception {
        Session session = createSftpSession();
        try {
            ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(CONNECT_TIMEOUT_MS);
            try {
                try {
                    Vector<?> list = channel.ls(remotePath);
                    for (Object o : list) {
                        ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) o;
                        String name = entry.getFilename();
                        if (!".".equals(name) && !"..".equals(name)) {
                            return false;
                        }
                    }
                    return true;
                } catch (Exception e) {
                    // Directory doesn't exist, consider it empty
                    return true;
                }
            } finally {
                channel.disconnect();
            }
        } finally {
            session.disconnect();
        }
    }

    /**
     * Ensure a remote directory exists, creating parent directories if needed.
     */
    private static void ensureDirectoryExists(ChannelSftp channel, String remotePath) throws Exception {
        if (remotePath == null || remotePath.isEmpty() || remotePath.equals("/")) {
            return;
        }
        try {
            SftpATTRS attrs = channel.stat(remotePath);
            if (!attrs.isDir()) {
                throw new Exception("Remote path exists but is not a directory: " + remotePath);
            }
        } catch (Exception e) {
            // Directory doesn't exist, create it
            String parent = remotePath.substring(0, remotePath.lastIndexOf('/'));
            if (!parent.isEmpty() && !parent.equals("/")) {
                ensureDirectoryExists(channel, parent);
            }
            channel.mkdir(remotePath);
        }
    }

    /**
     * Upload a file or directory recursively to the remote path. Reports progress via callback.
     */
    public static void upload(File localFile, String remotePath, ProgressCallback callback) throws Exception {
        Session session = createSftpSession();
        try {
            ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(CONNECT_TIMEOUT_MS);
            try {
                ensureDirectoryExists(channel, remotePath);
                uploadRecursive(channel, localFile, remotePath, callback);
            } finally {
                channel.disconnect();
            }
        } finally {
            session.disconnect();
        }
    }

    private static void uploadRecursive(ChannelSftp channel, File localFile, String remotePath, ProgressCallback callback) throws Exception {
        String remoteFile = remotePath.endsWith("/") ? remotePath + localFile.getName() : remotePath + "/" + localFile.getName();
        if (callback != null) callback.onFileStart(localFile.getName());

        if (localFile.isDirectory()) {
            try {
                channel.mkdir(remoteFile);
            } catch (Exception e) {
                try {
                    SftpATTRS attrs = channel.stat(remoteFile);
                    if (!attrs.isDir()) {
                        throw new Exception("Remote path exists but is not a directory: " + remoteFile);
                    }
                } catch (Exception statEx) {
                    // Directory doesn't exist, try to create parent directories
                    String parent = remoteFile.substring(0, remoteFile.lastIndexOf('/'));
                    if (!parent.isEmpty() && !parent.equals("/")) {
                        try {
                            channel.mkdir(parent);
                        } catch (Exception ignored) {}
                    }
                    channel.mkdir(remoteFile);
                }
            }
            File[] children = localFile.listFiles();
            if (children != null) {
                for (File child : children) {
                    uploadRecursive(channel, child, remoteFile, callback);
                }
            }
        } else {
            long fileSize = localFile.length();
            try (FileInputStream in = new FileInputStream(localFile)) {
                channel.put(in, remoteFile, new com.jcraft.jsch.SftpProgressMonitor() {
                    private long transferred = 0;
                    @Override
                    public void init(int op, String src, String dest, long max) {
                        if (callback != null) callback.onFileProgress(0, fileSize);
                    }
                    @Override
                    public boolean count(long count) {
                        transferred += count;
                        if (callback != null) callback.onFileProgress(transferred, fileSize);
                        return true;
                    }
                    @Override
                    public void end() {
                        if (callback != null) callback.onFileComplete(localFile.getName(), fileSize);
                    }
                });
            }
        }
    }

    public interface ProgressCallback {
        void onFileStart(String fileName);
        void onFileProgress(long bytesTransferred, long totalBytes);
        void onFileComplete(String fileName, long fileSize);
    }
}
