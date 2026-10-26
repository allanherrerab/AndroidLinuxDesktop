package org.debiannoroot;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Date;

/** Keep only the current session's guest and native X11 output. */
final class SessionLogs {
    private SessionLogs() {}

    static void begin(File directory) throws IOException {
        Files.write(new File(directory, "desktop.log").toPath(),
                ("Starting X11 Debian session: " + new Date() + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.write(new File(directory, "x11.log").toPath(), new byte[0],
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }
}
