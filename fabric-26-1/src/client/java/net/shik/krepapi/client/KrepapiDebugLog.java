package net.shik.krepapi.client;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Append-only NDJSON debug log for a single KrepAPI client session.
 * Each line is a self-contained JSON object with {@code "ts"} and {@code "event"} fields.
 * <p>
 * Activation: place an empty {@code krepapi-debug.txt} inside the game config directory
 * ({@code <minecraft>/config/krepapi/}) or pass {@code -Dkrepapi.debug=true}.
 */
public final class KrepapiDebugLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("krepapi");
    private static final Object LOCK = new Object();
    private static Writer writer;
    private static boolean enabled;
    private static boolean checkedEnabled;

    private KrepapiDebugLog() {
    }

    private static boolean isDebugEnabled() {
        if (!checkedEnabled) {
            checkedEnabled = true;
            if ("true".equalsIgnoreCase(System.getProperty("krepapi.debug"))) {
                enabled = true;
            } else {
                Path configDir = FabricLoader.getInstance().getConfigDir().resolve("krepapi");
                enabled = configDir.resolve("krepapi-debug.txt").toFile().exists();
            }
        }
        return enabled;
    }

    public static void beginSession(String serverAddress, String modVersion, int protocolVersion) {
        if (!isDebugEnabled()) {
            return;
        }
        synchronized (LOCK) {
            closeWriter();
            try {
                Path dir = FabricLoader.getInstance().getConfigDir().resolve("krepapi");
                File dirFile = dir.toFile();
                if (!dirFile.exists()) {
                    dirFile.mkdirs();
                }
                int existing = 0;
                File[] files = dirFile.listFiles((d, n) -> n.startsWith("debug-") && n.endsWith(".json"));
                if (files != null) {
                    existing = files.length;
                }
                long epoch = System.currentTimeMillis();
                File logFile = dir.resolve("debug-" + epoch + ".json").toFile();
                writer = new FileWriter(logFile, true);
                LOGGER.info("[KrepAPI-Debug] Session log: {} ({} existing files)", logFile.getName(), existing);
            } catch (IOException ex) {
                LOGGER.warn("[KrepAPI-Debug] Failed to open debug log file", ex);
                writer = null;
                return;
            }
        }
        write("session_start",
                "\"serverAddress\":" + jsonString(serverAddress)
                        + ",\"modVersion\":" + jsonString(modVersion)
                        + ",\"protocolVersion\":" + protocolVersion);
    }

    public static void handshakeSent(int protocolVersion, int capabilities, String modVersion) {
        write("handshake_sent",
                "\"protocolVersion\":" + protocolVersion
                        + ",\"capabilities\":\"0x" + Integer.toHexString(capabilities) + "\""
                        + ",\"modVersion\":" + jsonString(modVersion));
    }

    public static void bindingsApplied(List<String> actionIds) {
        write("bindings_applied",
                "\"count\":" + actionIds.size()
                        + ",\"actionIds\":" + jsonStringArray(actionIds));
    }

    public static void rawCaptureApplied(boolean captureEnabled, byte mode, boolean consumeVanilla, List<Integer> whitelistKeys) {
        write("raw_capture_applied",
                "\"enabled\":" + captureEnabled
                        + ",\"mode\":" + mode
                        + ",\"consumeVanilla\":" + consumeVanilla
                        + ",\"whitelistKeys\":" + jsonIntArray(whitelistKeys));
    }

    public static void interceptKeysApplied(int entryCount) {
        write("intercept_keys_applied", "\"count\":" + entryCount);
    }

    public static void mouseCaptureApplied(boolean captureEnabled, byte captureFlags, boolean consumeVanilla) {
        write("mouse_capture_applied",
                "\"enabled\":" + captureEnabled
                        + ",\"flags\":\"0x" + Integer.toHexString(captureFlags & 0xFF) + "\""
                        + ",\"consumeVanilla\":" + consumeVanilla);
    }

    public static void keyActionSent(String actionId, String phase, int seq) {
        write("key_action_sent",
                "\"actionId\":" + jsonString(actionId)
                        + ",\"phase\":" + jsonString(phase)
                        + ",\"seq\":" + seq);
    }

    public static void rawKeySent(int key, int scancode, int glfwAction, int seq) {
        write("raw_key_sent",
                "\"key\":" + key
                        + ",\"scancode\":" + scancode
                        + ",\"glfwAction\":" + glfwAction
                        + ",\"seq\":" + seq);
    }

    public static void mouseActionSent(String kind, int button, int glfwAction, float scrollX, float scrollY, int seq) {
        write("mouse_action_sent",
                "\"kind\":" + jsonString(kind)
                        + ",\"button\":" + button
                        + ",\"glfwAction\":" + glfwAction
                        + ",\"scrollX\":" + scrollX
                        + ",\"scrollY\":" + scrollY
                        + ",\"seq\":" + seq);
    }

    public static void warn(String message) {
        write("warn", "\"message\":" + jsonString(message));
    }

    public static void endSession(String reason) {
        write("session_end", "\"reason\":" + jsonString(reason));
        synchronized (LOCK) {
            closeWriter();
        }
    }

    private static void write(String event, String fieldsJson) {
        synchronized (LOCK) {
            if (writer == null) {
                return;
            }
            try {
                writer.write("{\"ts\":" + System.currentTimeMillis()
                        + ",\"event\":" + jsonString(event)
                        + "," + fieldsJson + "}\n");
                writer.flush();
            } catch (IOException ex) {
                LOGGER.warn("[KrepAPI-Debug] Failed to write debug log entry", ex);
            }
        }
    }

    private static void closeWriter() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
            writer = null;
        }
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append("\\u").append(String.format("%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String jsonStringArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(jsonString(values.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    private static String jsonIntArray(List<Integer> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values.get(i));
        }
        sb.append(']');
        return sb.toString();
    }
}
