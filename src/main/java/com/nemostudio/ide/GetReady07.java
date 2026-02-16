package com.nemostudio.ide;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Handles logic for the seventh Get Ready button. Runs GetReady07.sh on the remote terminal.
 */
public final class GetReady07 {

    private static final String SCRIPT_RESOURCE = "/scripts/GetReady07.sh";

    private GetReady07() {}

    public static void execute(RemoteTerminalPanel terminal) {
        String script = loadScript(SCRIPT_RESOURCE);
        if (script != null) {
            terminal.runScript(script);
        } else {
            terminal.appendOutput("[GetReady07] Could not load script " + SCRIPT_RESOURCE + "\n");
        }
    }

    private static String loadScript(String resource) {
        try (InputStream in = GetReady07.class.getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
