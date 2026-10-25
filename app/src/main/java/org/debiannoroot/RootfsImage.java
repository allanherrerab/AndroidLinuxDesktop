package org.debiannoroot;
import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import org.json.JSONObject;

final class RootfsImage {
    private final Context context;
    private final String checksum;
    private final long size;
    RootfsImage(Context context) throws Exception {
        this.context=context;
        try(InputStream input=context.getAssets().open("rootfs/image.json")) {
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);
            JSONObject metadata=new JSONObject(bytes.toString("UTF-8"));
            checksum=metadata.getString("sha256");size=metadata.optLong("size",-1);
        }
    }
    private File imported() {return new File(context.getFilesDir(),"images/rootfs.tar.xz");}
    File findVerified() throws Exception {
        File file=imported();
        if(file.isFile()) {
            try(InputStream input=new FileInputStream(file)){ImageIntegrity.verify(input,null,checksum,size);}
            return file;
        }
        File obb=context.getObbDir();File expansion=obb==null?null:new File(obb,"main.2.org.debiannoroot.obb");
        if(expansion==null||!expansion.isFile())throw new java.io.FileNotFoundException("Rootfs asset missing. Tap Import Debian image and select main.2.org.debiannoroot.obb (or debian-rootfs.tar.xz) supplied with this APK, then tap Start again.");
        // Extract only the private verified copy, so external replacement after
        // verification cannot swap the archive under the native extractor.
        try(InputStream input=new FileInputStream(expansion)){install(input);}
        return imported();
    }

    void importFrom(Uri uri) throws Exception {
        try(InputStream input=context.getContentResolver().openInputStream(uri)) {
            if(input==null)throw new java.io.IOException("Cannot read selected image");
            install(input);
        }
    }
    private void install(InputStream input) throws Exception {
        synchronized(RootfsImage.class) {
        File destination=imported();File parent=destination.getParentFile();
        if(!parent.isDirectory()&&!parent.mkdirs())throw new java.io.IOException("Cannot create image storage folder");
        File pending=new File(parent,"rootfs.tar.xz.importing");
        try {
            try(FileOutputStream output=new FileOutputStream(pending)) {
                ImageIntegrity.verify(input,output,checksum,size);output.getFD().sync();
            }
            java.nio.file.Files.move(pending.toPath(),destination.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } finally {pending.delete();}
        }
    }
}
