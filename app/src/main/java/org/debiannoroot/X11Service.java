package org.debiannoroot;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.system.Os;
import com.termux.x11.CmdEntryPoint;
import java.io.File;

/** Separate process: Xorg's fatal-error exit must never kill the Android UI. */
public final class X11Service extends Service {
    static final String TOKEN="org.debiannoroot.X11";
    static final int START=IBinder.FIRST_CALL_TRANSACTION;
    static final int CONNECTION=START+1;
    static final int STOP=START+2;
    private volatile CmdEntryPoint server;
    private volatile String failure;
    private volatile File endpoint;
    private final Binder binder=new Binder() {
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags) throws RemoteException {
            if(code<START || code>STOP)return super.onTransact(code,data,reply,flags);
            data.enforceInterface(TOKEN);
            if(code==START) {
                String root=data.readString();boolean legacy=data.readInt()!=0;
                if(server==null && failure==null) {
                    try {
                        File tmp=new File(root,"tmp");
                        if(!tmp.isDirectory()&&!tmp.mkdirs())throw new IllegalStateException("Cannot create X11 runtime folder");
                        new File(tmp,".X11-unix").mkdirs();
                        new File(tmp,".X0-lock").delete();new File(tmp,".X11-unix/X0").delete();
                        Os.setenv("TMPDIR",tmp.getAbsolutePath(),true);
                        Os.setenv("XKB_CONFIG_ROOT",new File(root,"usr/share/X11/xkb").getAbsolutePath(),true);
                        endpoint=new File(tmp,".X11-unix/X0");
                        int networkPolicy=NativeBridge.restrictX11Network();
                        if(networkPolicy!=0)throw new IllegalStateException("Cannot disable X11 network discovery ("+networkPolicy+")");
                        CmdEntryPoint candidate=new CmdEntryPoint();
                        String[] args=legacy?new String[]{":0","-nolisten","tcp","-noreset","-auth",new File(tmp,"debian-noroot.Xauthority").getAbsolutePath(),"-legacy-drawing","-disable-dri3","-extension","MIT-SHM"}:
                                new String[]{":0","-nolisten","tcp","-noreset","-auth",new File(tmp,"debian-noroot.Xauthority").getAbsolutePath(),"-disable-dri3","-extension","MIT-SHM"};
                        if(!candidate.start(args))throw new IllegalStateException("X11 native server rejected startup; see Android logcat");
                        server=candidate;
                    } catch(Exception | LinkageError error) { failure=error.toString(); }
                }
                reply.writeNoException();reply.writeString(failure);
            } else if(code==CONNECTION) {
                reply.writeNoException();
                ParcelFileDescriptor fd=server==null||endpoint==null||!endpoint.exists()?null:server.getXConnection();
                reply.writeInt(fd==null?0:1);
                if(fd!=null) { fd.writeToParcel(reply,0);try {fd.close();}catch(Exception ignored){} }
            } else {
                reply.writeNoException();
                new android.os.Handler(getMainLooper()).postDelayed(()->Process.killProcess(Process.myPid()),100);
            }
            return true;
        }
    };
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public void onDestroy() { super.onDestroy();Process.killProcess(Process.myPid()); }
    static void start(IBinder remote,File root,boolean legacy) throws Exception {
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try {data.writeInterfaceToken(TOKEN);data.writeString(root.getAbsolutePath());data.writeInt(legacy?1:0);
            if(!remote.transact(START,data,reply,0))throw new IllegalStateException("X11 startup transaction failed");
            reply.readException();String error=reply.readString();if(error!=null)throw new IllegalStateException(error);
        }finally {data.recycle();reply.recycle();}
    }
    static ParcelFileDescriptor connection(IBinder remote) throws Exception {
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try {data.writeInterfaceToken(TOKEN);if(!remote.transact(CONNECTION,data,reply,0))return null;
            reply.readException();return reply.readInt()==0?null:ParcelFileDescriptor.CREATOR.createFromParcel(reply);
        } finally {data.recycle();reply.recycle();}
    }
    static void stop(IBinder remote) {
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try {data.writeInterfaceToken(TOKEN);remote.transact(STOP,data,reply,0);reply.readException();}
        catch(Exception ignored){}finally {data.recycle();reply.recycle();}
    }
}
