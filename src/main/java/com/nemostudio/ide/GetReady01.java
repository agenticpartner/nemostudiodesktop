package com.nemostudio.ide;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Handles logic for the first Get Ready button. Runs GetReady01.sh on the remote terminal.
 */
public final class GetReady01 {

    private static final String SCRIPT_RESOURCE = "/scripts/GetReady01.sh";

    private GetReady01() {}

    public static void execute(RemoteTerminalPanel terminal) {
        String script = loadScript(SCRIPT_RESOURCE);
        if (script != null) {
            String remotePath = ConnectionStore.loadRemoteFolder();
            if (remotePath != null && !remotePath.trim().isEmpty()) {
                String safePath = remotePath.trim().replace("'", "'\"'\"'");
                script = "REMOTE_PATH='" + safePath + "'\n" + script;
            }
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
