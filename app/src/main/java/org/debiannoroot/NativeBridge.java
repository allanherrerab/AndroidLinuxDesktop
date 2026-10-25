package org.debiannoroot;

final class NativeBridge {
    static {
        System.loadLibrary("debian_native");
    }

    private NativeBridge() {}

    static native String extractRootfs(String archivePath, String destinationPath);
    static native int runDesktop(String appFilesPath, String rootfsPath,
                                 String prootPath, String runtimePath,
                                 String sharedPath);
    static native void stopDesktop();
    static native void resetSession();
    static native int restrictX11Network();
}
