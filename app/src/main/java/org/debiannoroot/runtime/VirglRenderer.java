package org.debiannoroot.runtime;

import android.content.Context;
import android.system.Os;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Own a session-scoped Bionic vtest server. The guest uses only its private Unix socket. */
public final class VirglRenderer implements AutoCloseable {
    private final Process process;
    private final File socket;

    public VirglRenderer(Context context, File rootfs, BooleanSupplier cancelled) throws Exception {
        socket = new File(rootfs, "tmp/.virgl_test");
        if (socket.exists() && !socket.delete()) throw new IOException("Cannot clear old VirGL socket");
        File binary = new File(context.getApplicationInfo().nativeLibraryDir, "libvirgl_server.so");
        ProcessBuilder command = new ProcessBuilder(binary.getAbsolutePath(), "--no-fork", "--multi-clients",
                "--use-egl-surfaceless", "--use-gles", "--socket-path", socket.getAbsolutePath());
        command.environment().put("TMPDIR", socket.getParent());
        command.redirectErrorStream(true);
        command.redirectOutput(new File(context.getFilesDir(), "virgl.log"));
        process = command.start();
        try {
            for (int attempt = 0; !socket.exists(); attempt++) {
                if (cancelled.getAsBoolean()) throw new IOException("VirGL startup cancelled");
                if (!process.isAlive() || attempt >= 100) throw new IOException("VirGL server did not start; see virgl.log");
                Thread.sleep(100);
            }
            Os.chmod(socket.getAbsolutePath(), 0600);
        } catch (Exception error) {
            close();
            throw error;
        }
    }

    @Override public void close() {
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
        socket.delete();
    }
}
