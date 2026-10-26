package org.debiannoroot.runtime;

public final class NativeBridge {
    static {
        System.loadLibrary("debian_native");
    }

    private NativeBridge() {}

    public static native String extractRootfs(String archivePath, String destinationPath);
    public static native int runDesktop(String appFilesPath, String rootfsPath,
                                 String prootPath, String runtimePath,
                                 String sharedPath);
    public static native void stopDesktop();
    public static native void resetSession();
    public static native int restrictX11Network();
}
