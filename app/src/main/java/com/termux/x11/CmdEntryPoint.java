package com.termux.x11;
import android.os.ParcelFileDescriptor;
import dalvik.annotation.optimization.CriticalNative;

/** Registered ABI of the pinned, unmodified Termux:X11 engine. */
public final class CmdEntryPoint {
    public CmdEntryPoint() { NativeLibrary.load(); }
    public native boolean start(String[] args);
    public native ParcelFileDescriptor getXConnection();
    public native ParcelFileDescriptor getLogcatOutput();
    public native void reportFatalError(String message);
    @CriticalNative private static native boolean connected();
    // Discovery is handled by a private Binder rather than upstream broadcasts.
    private void sendBroadcast() { }
    private void sendBroadcastDelayed() { }
    private void setDocumentTag(String tag) { }
}
