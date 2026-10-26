package org.debiannoroot.session;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SessionLogsTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("noroot-session-logs-");
        Path desktop = directory.resolve("desktop.log"), x11 = directory.resolve("x11.log"), virgl = directory.resolve("virgl.log");
        try {
            // First launch creates all three files. A subsequent launch must remove
            // previous output, including a longer native log's trailing bytes.
            SessionLogs.begin(directory.toFile());
            Files.writeString(desktop, "previous desktop session\n".repeat(100));
            Files.writeString(x11, "previous X11 session\n".repeat(100));
            Files.writeString(virgl, "previous VirGL session\n".repeat(100));
            SessionLogs.begin(directory.toFile());
            String current = Files.readString(desktop);
            if (!current.startsWith("Starting X11 Debian session: ") || current.contains("previous")
                    || current.lines().count() != 1 || Files.size(x11) != 0 || Files.size(virgl) != 0) {
                throw new AssertionError("Old session log content survived a new session");
            }
        } finally {
            Files.deleteIfExists(desktop);
            Files.deleteIfExists(x11);
            Files.deleteIfExists(virgl);
            Files.delete(directory);
        }
        System.out.println("Session logs replace previous desktop, X11 and VirGL output on each new session");
    }
}
