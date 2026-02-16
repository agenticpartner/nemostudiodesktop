package com.nemostudio.ide;

/**
 * Application-wide state for the remote connection. Updated by RemoteFolderWindow
 * when it connects or disconnects; read by the status bar monitor.
 */
public final class ConnectionState {

    private static volatile boolean connected;

    private ConnectionState() {}

    public static boolean isConnected() {
        return connected;
    }

    public static void setConnected(boolean connected) {
        ConnectionState.connected = connected;
    }
}
