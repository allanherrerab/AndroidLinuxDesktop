package com.termux.x11;

/** Native X11 callback contract; the app supplies its own UI in DesktopActivity. */
public abstract class MainActivity extends org.debiannoroot.LocalizedActivity {
    public long lastServerHeartbeat;
    public void clientConnectedStateChanged() { }
    public static native void nativeSetLaunchWindowingModeFreeform(Object options);
}
