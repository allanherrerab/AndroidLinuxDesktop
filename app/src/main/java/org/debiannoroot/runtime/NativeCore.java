package org.debiannoroot.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Android-independent first-party C logic; shared by the app and host tests. */
public final class NativeCore {
    static { System.loadLibrary("debian_core"); }
    private NativeCore() {}
    public static native float[] geometry(int vw,int vh,int gw,int gh,String mode,float scale,int border,boolean aspect43);
    public static native boolean validResolution(int width,int height);
    public static native float pointerGain(int speed,int acceleration,float distance,float elapsed);
    public static native String language(String value);
    public static native String locale(String value);
    public static native String keyboard(String value);
    /** 0: app language codes, 1: corresponding guest locales, 2: keyboard codes. */
    public static native String[] regionalCodes(int kind);
    public static native boolean timezoneSafe(String value);
    public static native String guestSettings(String language,String keyboard);
    public static native int keySymbol(int androidCode,int unicode);
    public static native int androidKey(int symbol);
    public static native int shortcutKey(String label);
    public static native void verifyImage(InputStream source,OutputStream copy,String expected,long expectedSize) throws IOException;
    public static native void beginLogs(String directory,String header) throws IOException;
    public static native void discardRootfs(String directory,String name) throws IOException;
    public static native void initializeGuest(String root,int uid,long day) throws IOException;
    public static native void configureTimezone(String root,String zone) throws IOException;
    public static native void configureResolver(String root,String hostname,String[] servers) throws IOException;
}
