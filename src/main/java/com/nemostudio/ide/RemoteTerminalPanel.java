package com.nemostudio.ide;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * JavaFX panel that shows a terminal connected to the remote machine via SSH.
 * Output is displayed in a TextArea; commands can be sent with sendCommand().
 */
public class RemoteTerminalPanel extends StackPane {

    private static final int SSH_PORT = 22;
    private static final int CONNECT_TIMEOUT_MS = 10000;

    /** Strip ANSI escape sequences (e.g. [?2004h, [?2004l, other CSI) so the TextArea shows clean text. */
    private static final Pattern ANSI_CSI = Pattern.compile("\u001B\\[[^a-zA-Z]*[a-zA-Z]");

    private final TextArea textArea;
    private Session session;
    private ChannelShell channel;
    private InputStream channelInput;
    private OutputStream channelOutput;
    private Thread readerThread;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    public RemoteTerminalPanel() {
        this("Terminal will connect when you press a Get Ready button.");
    }

    /**
     * Create a terminal panel with a custom prompt (placeholder text when empty).
     */
    public RemoteTerminalPanel(String promptText) {
        textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setStyle("-fx-font-family: monospace; -fx-font-size: 13px;");
        textArea.setPromptText(promptText != null ? promptText : "");
        getChildren().add(textArea);
        setStyle("-fx-background-color: #1e1e1e;");
    }

    public void setPromptText(String promptText) {
        textArea.setPromptText(promptText != null ? promptText : "");
    }

    /**
     * Connect to the remote host using saved credentials. Idempotent if already connected.
     */
    public void connect() {
        connect(null);
    }

    /**
     * Connect to the remote host; when connected, run onConnected on the FX thread (e.g. to run a script).
     * If already connected, onConnected is run immediately.
     */
    public void connect(Runnable onConnected) {
        if (connected.get()) {
            if (onConnected != null) Platform.runLater(onConnected);
            return;
        }
        String host = ConnectionStore.loadHost();
        String user = ConnectionStore.loadUsername();
        if (host == null || host.trim().isEmpty() || user == null || user.trim().isEmpty()) {
            appendOutput("Cannot connect: no host or username saved. Use Project → Connect or Open Remote Folder first.\n");
            return;
        }
        char[] passChars = SecurePasswordStore.loadPassword();
        String pass = (passChars != null && passChars.length > 0) ? new String(passChars) : null;
        if (passChars != null) java.util.Arrays.fill(passChars, '\0');

        appendOutput("Connecting to " + host + " as " + user + " ...\n");
        new Thread(() -> {
            try {
                JSch jsch = new JSch();
                Session s = jsch.getSession(user, host.trim(), SSH_PORT);
                if (pass != null && !pass.isEmpty()) s.setPassword(pass);
                s.setConfig("StrictHostKeyChecking", "no");
                s.connect(CONNECT_TIMEOUT_MS);
                ChannelShell ch = (ChannelShell) s.openChannel("shell");
                ch.connect(CONNECT_TIMEOUT_MS);
                session = s;
                channel = ch;
                channelInput = ch.getInputStream();
                channelOutput = ch.getOutputStream();
                connected.set(true);
                Platform.runLater(() -> {
                    appendOutput("Connected. You can run commands from Get Ready buttons.\n");
                    if (onConnected != null) onConnected.run();
                });
                startReader();
            } catch (Exception e) {
                Platform.runLater(() -> appendOutput("Connection failed: " + e.getMessage() + "\n"));
            }
        }).start();
    }

    private void startReader() {
        readerThread = new Thread(() -> {
            byte[] buf = new byte[1024];
            try {
                while (connected.get() && channelInput != null) {
                    int n = channelInput.read(buf);
                    if (n <= 0) break;
                    String line = new String(buf, 0, n, StandardCharsets.UTF_8);
                    Platform.runLater(() -> appendOutput(line));
                }
            } catch (Exception ignored) {
                if (connected.get()) {
                    Platform.runLater(() -> appendOutput("\n[Connection closed]\n"));
                }
            }
        }, "terminal-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Filter out ANSI escape sequences (bracketed paste [?2004h/l, other CSI) so the terminal output is readable.
     */
    private static String filterAnsi(String raw) {
        if (raw == null) return "";
        return ANSI_CSI.matcher(raw).replaceAll("");
    }

    public void appendOutput(String text) {
        textArea.appendText(filterAnsi(text));
    }

    /**
     * Clear all text in the terminal. Safe to call from FX thread.
     */
    public void clearOutput() {
        textArea.clear();
    }

    /**
     * Send a command to the remote shell (adds newline). Safe to call from FX thread.
     */
    public void sendCommand(String command) {
        if (!isRemoteConnected() || channelOutput == null) {
            appendOutput("[Not connected. Connect first.]\n");
            return;
        }
        String line = command + "\n";
        new Thread(() -> {
            try {
                channelOutput.write(line.getBytes(StandardCharsets.UTF_8));
                channelOutput.flush();
            } catch (Exception e) {
                Platform.runLater(() -> appendOutput("Send failed: " + e.getMessage() + "\n"));
            }
        }).start();
    }

    /**
     * Send raw text to the remote shell (no automatic newline). Used for piping script content.
     */
    public void sendRaw(String text) {
        if (!isRemoteConnected() || channelOutput == null) {
            appendOutput("[Not connected. Connect first.]\n");
            return;
        }
        new Thread(() -> {
            try {
                channelOutput.write(text.getBytes(StandardCharsets.UTF_8));
                channelOutput.flush();
            } catch (Exception e) {
                Platform.runLater(() -> appendOutput("Send failed: " + e.getMessage() + "\n"));
            }
        }).start();
    }

    /**
     * Run a shell script on the remote machine by sending its content via a heredoc.
     * Sends the whole heredoc in one write so the remote receives it atomically.
     * Safe to call from FX thread.
     */
    public void runScript(String scriptContent) {
        if (!isRemoteConnected() || channelOutput == null) {
            appendOutput("[Not connected. Connect first.]\n");
            return;
        }
        String delimiter = "SCRIPT_END_" + System.currentTimeMillis();
        String fullPayload = "bash -s << '" + delimiter + "'\n" + scriptContent + "\n" + delimiter + "\n";
        new Thread(() -> {
            try {
                channelOutput.write(fullPayload.getBytes(StandardCharsets.UTF_8));
                channelOutput.flush();
            } catch (Exception e) {
                Platform.runLater(() -> appendOutput("Send failed: " + e.getMessage() + "\n"));
            }
        }).start();
    }

    public boolean isRemoteConnected() {
        return connected.get();
    }

    public void disconnect() {
        connected.set(false);
        try {
            if (channel != null) channel.disconnect();
        } catch (Exception ignored) {}
        try {
            if (session != null) session.disconnect();
        } catch (Exception ignored) {}
        channel = null;
        session = null;
        channelInput = null;
        channelOutput = null;
    }
}
