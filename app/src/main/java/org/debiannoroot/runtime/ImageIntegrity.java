package org.debiannoroot.runtime;
import java.io.InputStream;
import java.io.OutputStream;

/** Streaming image verifier, also used while importing a selected external asset. */
public final class ImageIntegrity {
    public static void verify(InputStream source,OutputStream copy,String expected,long expectedSize) throws Exception {
        NativeCore.verifyImage(source,copy,expected,expectedSize);
    }
}
