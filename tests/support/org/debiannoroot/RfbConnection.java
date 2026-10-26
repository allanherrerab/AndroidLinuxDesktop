package org.debiannoroot;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** RFB 3.8 client for the private, same-UID TigerVNC Unix socket. No TCP listener. */
final class RfbConnection implements Closeable {
    interface Display {
        void resize(int width, int height);
        void pixels(int x, int y, int width, int height, int[] argb);
        void copy(int sourceX, int sourceY, int x, int y, int width, int height);
        void updated();
    }

    private final DataInputStream input;
    private final DataOutputStream output;
    private final Closeable transport;
    private final Display display;
    private int width;
    private int height;
    private static final int MAX_DIMENSION = 4096;
    private static final int MAX_TEXT = 1024 * 1024;

    RfbConnection(InputStream input, OutputStream output, Closeable transport, Display display) {
        this.input = new DataInputStream(new BufferedInputStream(input, 64 * 1024));
        this.output = new DataOutputStream(new BufferedOutputStream(output));
        this.transport = transport;
        this.display = display;
    }

    void handshake() throws IOException {
        byte[] banner = new byte[12];
        input.readFully(banner);
        if (!Arrays.equals(banner, "RFB 003.008\n".getBytes(StandardCharsets.US_ASCII))) {
            throw new IOException("The desktop server requires RFB 3.8");
        }
        output.write(banner);
        output.flush();
        int count = input.readUnsignedByte();
        if (count == 0) throw new IOException(readText());
        boolean supported = false;
        for (int i = 0; i < count; i++) supported |= input.readUnsignedByte() == 1;
        if (!supported) throw new IOException("Private desktop socket rejected authentication");
        output.writeByte(1); // None: access is enforced by app-private filesystem permissions.
        output.flush();
        if (input.readInt() != 0) throw new IOException(readText());
        output.writeByte(1); // Shared: reconnecting must not terminate the session.
        output.flush();
        width = input.readUnsignedShort();
        height = input.readUnsignedShort();
        checkSize(width, height);
        byte[] pixelFormat = new byte[16];
        input.readFully(pixelFormat);
        readText(); // Desktop name.
        display.resize(width, height);
        synchronized (output) {
            output.write(new byte[]{0, 0, 0, 0, 32, 24, 0, 1,
                    0, (byte)255, 0, (byte)255, 0, (byte)255, 16, 8, 0, 0, 0, 0});
            output.writeByte(2); // SetEncodings: CopyRect, Raw, DesktopSize.
            output.writeByte(0);
            output.writeShort(3);
            output.writeInt(1);
            output.writeInt(0);
            output.writeInt(-223);
            output.flush();
        }
        requestUpdate(false);
    }

    void readUpdates() throws IOException {
        for (;;) {
            int type = input.readUnsignedByte();
            switch (type) {
                case 0:
                    input.readUnsignedByte();
                    int rectangles = input.readUnsignedShort();
                    for (int r = 0; r < rectangles; r++) {
                        int x = input.readUnsignedShort();
                        int y = input.readUnsignedShort();
                        int w = input.readUnsignedShort();
                        int h = input.readUnsignedShort();
                        int encoding = input.readInt();
                        if (encoding == -223) {
                            checkSize(w, h);
                            width = w;
                            height = h;
                            display.resize(w, h);
                        } else {
                            checkRectangle(x, y, w, h);
                            if (encoding == 0) {
                                // Decode one scanline at a time to bound transient allocations.
                                byte[] bytes = new byte[w * 4];
                                int[] pixels = new int[w];
                                for (int row = 0; row < h; row++) {
                                    input.readFully(bytes);
                                    for (int col = 0; col < w; col++) {
                                        int offset = col * 4;
                                        pixels[col] = 0xff000000 | (bytes[offset] & 255)
                                                | ((bytes[offset + 1] & 255) << 8)
                                                | ((bytes[offset + 2] & 255) << 16);
                                    }
                                    display.pixels(x, y + row, w, 1, pixels);
                                }
                            } else if (encoding == 1) {
                                int sx = input.readUnsignedShort();
                                int sy = input.readUnsignedShort();
                                checkRectangle(sx, sy, w, h);
                                display.copy(sx, sy, x, y, w, h);
                            } else {
                                throw new IOException("Unsupported desktop encoding: " + encoding);
                            }
                        }
                    }
                    display.updated();
                    requestUpdate(true);
                    break;
                case 2: // Bell.
                    break;
                case 3: // Ignore server clipboard until the user explicitly requests it.
                    input.readUnsignedByte();
                    input.readUnsignedShort();
                    readText();
                    break;
                default:
                    throw new IOException("Unexpected desktop message: " + type);
            }
        }
    }

    private static void checkSize(int width, int height) throws IOException {
        if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new IOException("Invalid desktop dimensions");
        }
    }

    private void checkRectangle(int x, int y, int w, int h) throws IOException {
        if (x + w > width || y + h > height) throw new IOException("Invalid desktop rectangle");
    }

    private String readText() throws IOException {
        int size = input.readInt();
        if (size < 0 || size > MAX_TEXT) throw new IOException("Desktop text exceeds limit");
        byte[] bytes = new byte[size];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void requestUpdate(boolean incremental) throws IOException {
        synchronized (output) {
            output.writeByte(3);
            output.writeByte(incremental ? 1 : 0);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(width);
            output.writeShort(height);
            output.flush();
        }
    }

    void key(int keysym, boolean pressed) throws IOException {
        synchronized (output) {
            output.writeByte(4);
            output.writeByte(pressed ? 1 : 0);
            output.writeShort(0);
            output.writeInt(keysym);
            output.flush();
        }
    }

    void pointer(int x, int y, int buttons) throws IOException {
        synchronized (output) {
            output.writeByte(5);
            output.writeByte(buttons);
            output.writeShort(Math.max(0, Math.min(width - 1, x)));
            output.writeShort(Math.max(0, Math.min(height - 1, y)));
            output.flush();
        }
    }

    void text(String text) throws IOException {
        for (int i = 0; i < text.length();) {
            int point = text.codePointAt(i);
            i += Character.charCount(point);
            int sym = point == '\n' ? 0xff0d : point == '\t' ? 0xff09
                    : point <= 255 ? point : 0x01000000 | point;
            key(sym, true);
            key(sym, false);
        }
    }

    @Override
    public void close() throws IOException {
        // Closing the transport interrupts a reader blocked waiting for the server.
        transport.close();
    }
}
