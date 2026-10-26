package org.debiannoroot.session;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import org.debiannoroot.runtime.NativeCore;

/** Keep only the current session's guest, native X11 and VirGL output. */
final class SessionLogs {
    private SessionLogs() {}

    static void begin(File directory) throws IOException {
        NativeCore.beginLogs(directory.getAbsolutePath(), "Starting X11 Debian session: " + new Date() + "\n");
    }
}
