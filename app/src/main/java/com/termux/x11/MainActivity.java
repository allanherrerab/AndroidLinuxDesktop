package com.termux.x11;

/** Native X11 callback contract; the app supplies its own UI in DesktopActivity. */
public abstract class MainActivity extends android.app.Activity {
    public long lastServerHeartbeat;
    public void clientConnectedStateChanged() { }
    public static native void nativeSetLaunchWindowingModeFreeform(Object options);
}
