package org.debiannoroot;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.security.MessageDigest;

/** Streaming image verifier, also used while importing a selected external asset. */
public final class ImageIntegrity {
    public static void verify(InputStream source,OutputStream copy,String expected,long expectedSize) throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];long size=0;int count;
        while((count=source.read(buffer))!=-1) {
            if(Thread.currentThread().isInterrupted())throw new IOException("Image import cancelled");
            size+=count;if(expectedSize>0&&size>expectedSize)throw new IOException("Image is larger than the expected rootfs asset");
            hash.update(buffer,0,count);if(copy!=null)copy.write(buffer,0,count);
        }
        StringBuilder actual=new StringBuilder();for(byte value:hash.digest())actual.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
        if(!actual.toString().equalsIgnoreCase(expected)||(expectedSize>0&&size!=expectedSize))
            throw new IOException("Rootfs checksum/size mismatch. Select the image distributed with this APK.");
    }
}
