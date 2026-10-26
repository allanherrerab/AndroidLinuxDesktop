package org.debiannoroot;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Standalone protocol tests: javac RfbConnection.java RfbConnectionTest.java. */
public final class RfbConnectionTest {
    private static final class Screen implements RfbConnection.Display {
        int width, height, updates;
        int[] pixels;
        public void resize(int w, int h) { width = w; height = h; pixels = new int[w * h]; }
        public void pixels(int x, int y, int w, int h, int[] data) {
            for (int r = 0; r < h; r++) System.arraycopy(data, r * w, pixels, (y + r) * width + x, w);
        }
        public void copy(int sx, int sy, int x, int y, int w, int h) {
            int[] temp = new int[w * h];
            for (int r = 0; r < h; r++) System.arraycopy(pixels, (sy + r) * width + sx, temp, r * w, w);
            pixels(x, y, w, h, temp);
        }
        public void updated() { updates++; }
    }

    private static ByteArrayOutputStream server(int width, int height) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write("RFB 003.008\n".getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[]{1, 1});
        out.writeInt(0);
        out.writeShort(width);
        out.writeShort(height);
        out.write(new byte[16]);
        out.writeInt(4);
        out.writeBytes("test");
        return bytes;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void framesAndInput() throws Exception {
        ByteArrayOutputStream bytes = server(2, 1);
        DataOutputStream server = new DataOutputStream(bytes);
        server.write(new byte[]{0, 0, 0, 2}); // One update, two rectangles.
        server.writeShort(0); server.writeShort(0); server.writeShort(1); server.writeShort(1); server.writeInt(0);
        server.write(new byte[]{0x33, 0x22, 0x11, 0});
        server.writeShort(1); server.writeShort(0); server.writeShort(1); server.writeShort(1); server.writeInt(1);
        server.writeShort(0); server.writeShort(0);
        Screen screen = new Screen();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RfbConnection client = new RfbConnection(new ByteArrayInputStream(bytes.toByteArray()), output, () -> {}, screen);
        client.handshake();
        try { client.readUpdates(); } catch (EOFException expected) { }
        require(Arrays.equals(screen.pixels, new int[]{0xff112233, 0xff112233}), "Raw/CopyRect pixels");
        require(screen.updates == 1, "Frame completion");
        int offset = output.size();
        client.pointer(999, -1, 4);
        client.text("é😀\n");
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(output.toByteArray(), offset, output.size() - offset));
        require(input.readUnsignedByte() == 5 && input.readUnsignedByte() == 4 &&
                input.readUnsignedShort() == 1 && input.readUnsignedShort() == 0, "Clamped pointer");
        for (int symbol : new int[]{0xe9, 0x0101f600, 0xff0d}) {
            for (int pressed : new int[]{1, 0}) {
                require(input.readUnsignedByte() == 4 && input.readUnsignedByte() == pressed, "Key event");
                input.readUnsignedShort();
                require(input.readInt() == symbol, "Unicode keysym");
            }
        }
    }

    private static void rejectInvalidDimensions() throws Exception {
        RfbConnection client = new RfbConnection(new ByteArrayInputStream(server(65535, 65535).toByteArray()),
                new ByteArrayOutputStream(), () -> {}, new Screen());
        try { client.handshake(); throw new AssertionError("Unbounded framebuffer allocation"); }
        catch (IOException expected) { }
    }

    private static void rejectInvalidRectangle() throws Exception {
        ByteArrayOutputStream bytes = server(2, 1);
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(new byte[]{0, 0, 0, 1});
        out.writeShort(1); out.writeShort(0); out.writeShort(2); out.writeShort(1); out.writeInt(0);
        RfbConnection client = new RfbConnection(new ByteArrayInputStream(bytes.toByteArray()),
                new ByteArrayOutputStream(), () -> {}, new Screen());
        client.handshake();
        try { client.readUpdates(); throw new AssertionError("Out of bounds rectangle"); }
        catch (IOException expected) { require(!(expected instanceof EOFException), "Reject before pixels"); }
    }

    public static void main(String[] args) throws Exception {
        framesAndInput();
        rejectInvalidDimensions();
        rejectInvalidRectangle();
        System.out.println("RFB protocol tests passed");
    }
}
