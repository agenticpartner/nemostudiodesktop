package com.nemostudio.ide;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Handles logic for the fifth Get Ready button. Runs GetReady05.sh on the remote terminal.
 */
public final class GetReady05 {

    private static final String SCRIPT_RESOURCE = "/scripts/GetReady05.sh";

    private GetReady05() {}

    public static void execute(RemoteTerminalPanel terminal) {
        String script = loadScript(SCRIPT_RESOURCE);
        if (script != null) {
            terminal.runScript(script);
        } else {
            terminal.appendOutput("[GetReady05] Could not load script " + SCRIPT_RESOURCE + "\n");
        }
    }

    private static String loadScript(String resource) {
        try (InputStream in = GetReady05.class.getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
