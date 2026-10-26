package org.debiannoroot;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** Exercise the host-only RFB decoder against an isolated guest desktop. */
public final class DesktopSmoke implements RfbConnection.Display {
    private static final int TIMEOUT_SCALE = Integer.getInteger("noroot.smoke.timeoutScale", 1);
    private BufferedImage image;
    private final CountDownLatch firstFrame = new CountDownLatch(1);
    private int frames;
    public synchronized void resize(int w, int h) { image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB); }
    public synchronized void pixels(int x, int y, int w, int h, int[] argb) { image.setRGB(x, y, w, h, argb, 0, w); }
    public synchronized void copy(int sx, int sy, int x, int y, int w, int h) {
        image.setRGB(x, y, w, h, image.getRGB(sx, sy, w, h, null, 0, w), 0, w);
    }
    public synchronized void updated() { frames++; firstFrame.countDown(); }
    private synchronized void save(String path) throws IOException {
        int first = image.getRGB(0, 0);
        boolean varied = false;
        for (int y = 0; y < image.getHeight(); y += 5)
            for (int x = 0; x < image.getWidth(); x += 5) varied |= image.getRGB(x, y) != first;
        if (!varied) throw new IOException("Desktop is a blank frame");
        ImageIO.write(image, "png", new File(path));
        System.out.println("Decoded " + frames + " frames, " + image.getWidth() + "x" + image.getHeight());
    }
    private static void waitFor(File marker, int seconds) throws InterruptedException, IOException {
        System.out.println("Waiting for " + marker.getName());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos((long)seconds * TIMEOUT_SCALE);
        while (!marker.isFile() && System.nanoTime() < deadline) Thread.sleep(500);
        if (!marker.isFile()) throw new IOException("Application readiness timed out: " + marker.getName());
        System.out.println("Passed " + marker.getName());
    }
    public static void main(String[] args) throws Exception {
        DesktopSmoke screen = new DesktopSmoke();
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(args[0]));
            InputStream input = new InputStream() {
                public int read() throws IOException { byte[] b = new byte[1]; return read(b, 0, 1) < 0 ? -1 : b[0] & 255; }
                public int read(byte[] b, int off, int size) throws IOException { return channel.read(ByteBuffer.wrap(b, off, size)); }
            };
            OutputStream output = new OutputStream() {
                public void write(int b) throws IOException { write(new byte[]{(byte)b}); }
                public void write(byte[] b, int off, int size) throws IOException {
                    ByteBuffer buffer = ByteBuffer.wrap(b, off, size);
                    while (buffer.hasRemaining()) channel.write(buffer);
                }
            };
            RfbConnection client = new RfbConnection(input, output, channel, screen);
            client.handshake();
            Thread receiver = new Thread(() -> {
                try { client.readUpdates(); } catch (IOException error) {
                    if (channel.isOpen()) error.printStackTrace();
                }
            });
            receiver.setDaemon(true);
            receiver.start();
            if (!screen.firstFrame.await(30L * TIMEOUT_SCALE, TimeUnit.SECONDS)) throw new IOException("No desktop frame");
            if (args.length > 3 && args[3].equals("--snapshot")) {
                Thread.sleep(2000);
                screen.save(args[1]);
                return;
            }
            File terminalReady = new File(args[2]);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90L * TIMEOUT_SCALE);
            while (!terminalReady.isFile() && System.nanoTime() < deadline) Thread.sleep(500);
            if (!terminalReady.isFile()) {
                screen.save(args[1]);
                throw new IOException("XFCE did not autostart its terminal");
            }
            // Under QEMU the session manager can take longer than the terminal
            // to finish configuring focus and keyboard mappings.
            Thread.sleep(30000);
            client.pointer(300, 40, 1);
            Thread.sleep(150);
            client.pointer(300, 40, 0);
            Thread.sleep(1000);
            client.pointer(120, 140, 1);
            Thread.sleep(150);
            client.pointer(120, 140, 0);
            Thread.sleep(1000);
            // Exercise modifier press/release through the same client as Android.
            client.key(0xffe3, true); client.key('l', true);
            client.key('l', false); client.key(0xffe3, false);
            Thread.sleep(500);
            client.text("sudo -n id -u > /home/debian/desktop-sudo-uid; " +
                    "printf shared-ok > /sdcard/desktop-shared-ok; " +
                    "notify-send -t 2500 'Debian Noroot' 'Desktop notifications are working' && " +
                    "touch /home/debian/desktop-notification-ok; " +
                    "printf 'desktop-input-ok\\n' > /home/debian/desktop-input-ok\n");
            File sentinel = new File(terminalReady.getParentFile(), "desktop-input-ok");
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30L * TIMEOUT_SCALE);
            while (!sentinel.isFile() && System.nanoTime() < deadline) Thread.sleep(500);
            Thread.sleep(5000); // Let the remaining XFCE autostart components paint.
            screen.save(args[1]);
            int width = screen.image.getWidth(), height = screen.image.getHeight();
            // Exercise the actual Show Desktop and Whisker Menu panel plugins.
            client.pointer(width - 16, height - 16, 1);
            Thread.sleep(150);
            client.pointer(width - 16, height - 16, 0);
            Thread.sleep(3000);
            screen.save(args[1].replace(".png", "-panel.png"));
            client.pointer(16, height - 16, 1);
            Thread.sleep(150);
            client.pointer(16, height - 16, 0);
            Thread.sleep(6000);
            screen.save(args[1].replace(".png", "-menu.png"));
            client.key(0xff1b, true); client.key(0xff1b, false);
            Thread.sleep(1000);
            client.pointer(48, height - 16, 1);
            Thread.sleep(150);
            client.pointer(48, height - 16, 0);
            waitFor(new File(terminalReady.getParentFile(), ".noroot-terminal-launcher-ready"), 60);
            client.pointer(80, height - 16, 1);
            Thread.sleep(150);
            client.pointer(80, height - 16, 0);
            waitFor(new File(terminalReady.getParentFile(), ".noroot-thunar-ready"), 60);
            Thread.sleep(3000);
            screen.save(args[1].replace(".png", "-thunar.png"));
            client.pointer(112, height - 16, 1);
            Thread.sleep(150);
            client.pointer(112, height - 16, 0);
            waitFor(new File(terminalReady.getParentFile(), ".noroot-firefox-ready"), 180);
            Thread.sleep(10000);
            client.key(0xffe3, true); client.key('l', true);
            client.key('l', false); client.key(0xffe3, false);
            Thread.sleep(500);
            client.text("file:///home/debian/browser-check.html\n");
            waitFor(new File(terminalReady.getParentFile(), ".noroot-browser-page-ready"), 90);
            Thread.sleep(5000);
            screen.save(args[1].replace(".png", "-firefox.png"));
        } catch (Exception failure) {
            // Preserve the actual screen on timeout instead of losing the most
            // useful evidence when the Python harness removes its guest root.
            try { screen.save(args[1].replace(".png", "-failure.png")); }
            catch (Exception snapshotFailure) { failure.addSuppressed(snapshotFailure); }
            throw failure;
        }
    }
}
