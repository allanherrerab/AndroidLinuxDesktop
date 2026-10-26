package com.termux.x11;
import android.content.Context;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.inputmethod.InputMethodManager;
import android.graphics.RectF;
import dalvik.annotation.optimization.FastNative;
import dalvik.annotation.optimization.CriticalNative;
import org.debiannoroot.DisplayGeometry;
import org.debiannoroot.SessionSettings;

/** Small Android host for the pinned upstream Xorg renderer and input ABI. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic host needs the Activity native callback contract.
public final class LorieView extends SurfaceView implements SurfaceHolder.Callback {
    public interface Listener { void viewport(int x,int y,int w,int h,float sx,float sy,float sw,float sh); }
    private final MainActivity activity;
    private long ptr;
    private Listener listener;
    private final SessionSettings settings;
    public LorieView(MainActivity context) {
        super(context);
        activity=context;
        settings=new SessionSettings(context);
        NativeLibrary.load();
        ptr=nativeInit();
        getHolder().addCallback(this);
    }
    public void setListener(Listener value) { listener=value; }
    public void attach(int fd) { connect(ptr,fd); update(); }
    public float[] cursor() { long value=getCursorPosition(ptr);return new float[]{(float)(value>>>32),(float)(value&0xffffffffL)}; }
    public boolean isConnected() { return ptr!=0 && connected(ptr); }
    public void mouse(float x,float y,int button,boolean down,boolean relative) { if(ptr!=0)sendMouseEvent(ptr,x,y,button,down,relative); }
    public void key(int scan,int code,boolean down) { if(ptr!=0)sendKeyEvent(ptr,scan,code,down); }
    public void text(String value) { if(ptr!=0)sendTextEvent(ptr,value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    public void zoom(int percent) { if(ptr!=0) { setRendererZoom(ptr,percent); setFollowCursorPan(ptr,percent>100); } }
    public void update() {
        if(ptr==0 || getWidth()==0 || getHeight()==0) return;
        int width=settings.width(),height=settings.height();
        DisplayGeometry g=DisplayGeometry.compute(getWidth(),getHeight(),width,height,settings.scaling(),settings.scale(),settings.number("border",0,0,10),settings.flag("aspect43",false));
        setViewport(ptr,Math.round(g.left),Math.round(g.top),Math.round(g.width),Math.round(g.height),width,height,0);
        setFiltering(ptr,settings.flag("filtering",true)?android.opengl.GLES20.GL_LINEAR:android.opengl.GLES20.GL_NEAREST);
        setClipboardSyncEnabled(ptr,false,false);
        sendWindowChange(ptr,width,height,settings.number("refresh",60,15,120),"Debian Noroot");
        zoom(Math.min(400,settings.number("zoom",100,100,400)*(settings.number("magnifier",0,0,2)==1?2:1)));
    }
    @Override public void surfaceCreated(SurfaceHolder holder) { }
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int w,int h) { if(ptr!=0)surfaceChanged(ptr,holder.getSurface());update(); }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { if(ptr!=0)surfaceChanged(ptr,null); }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh) { update(); }
    public void close() { if(ptr!=0) { nativeDestroy(ptr);ptr=0; } }
    @Override protected void onDetachedFromWindow() { close();super.onDetachedFromWindow(); }
    private void setRendererViewport(int x,int y,int w,int h,float sx,float sy,float sw,float sh) {
        post(()-> { if(listener!=null)listener.viewport(x,y,w,h,sx,sy,sw,sh); });
    }
    private void setClipboardText(String value) { /* Explicit paste only; clipboard sync is disabled. */ }
    private void requestClipboard() { if(ptr!=0)sendClipboardEvent(ptr,new byte[0]); }
    private void onSyncReply(int serial) { }
    private void resetIme() { ((InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).restartInput(activity.getCurrentFocus()==null?this:activity.getCurrentFocus()); }
    @FastNative private native long nativeInit();
    @FastNative private native void nativeDestroy(long ptr);
    @FastNative private native void surfaceChanged(long ptr, Surface surface);
    @FastNative private native void setFiltering(long ptr, int filtering);
    @FastNative private native void setClipboardSyncEnabled(long ptr, boolean enabled, boolean ignored);
    @FastNative private native void sendClipboardAnnounce(long ptr);
    @FastNative private native void sendClipboardEvent(long ptr, byte[] text);
    @FastNative private native void sendWindowChange(long ptr, int width, int height, int framerate, String name);
    @FastNative private native void setViewport(long ptr, int x, int y, int width, int height, int expectedWidth, int expectedHeight, int hiddenBottom);
    @FastNative private native void setRendererZoom(long ptr, int percent);
    @FastNative private native void setZoomAnchor(long ptr, float sourceX, float sourceY, float fracX, float fracY);
    @FastNative private native void clearZoomAnchor(long ptr);
    @FastNative private native void setFollowCursorPan(long ptr, boolean enabled);
    @FastNative private native long getCursorPosition(long ptr);
    @FastNative private native void sendSync(long ptr, int serial);
    @FastNative private static native void connect(long ptr, int fd);
    @CriticalNative private static native boolean connected(long ptr);
    @FastNative private static native void startLogcat(long ptr, int fd);
    @FastNative private native void sendMouseEvent(long ptr, float x, float y, int whichButton, boolean buttonDown, boolean relative);
    @FastNative private native void sendTouchEvent(long ptr, int action, int id, int x, int y);
    @FastNative private native void sendStylusEvent(long ptr, float x, float y, int pressure, int tiltX, int tiltY, int orientation, int buttons, boolean eraser, boolean mouseMode);
    @FastNative private native void requestStylusEnabled(long ptr, boolean enabled);
    @FastNative private native void sendLockKeysState(long ptr, int state);
    @FastNative private native boolean sendKeyEvent(long ptr, int scanCode, int keyCode, boolean keyDown);
    @FastNative private native void sendTextEvent(long ptr, byte[] text);
    @CriticalNative private static native boolean requestConnection(long ptr);
    @CriticalNative public static native long getLastInputTimestamp();
    @CriticalNative public static native void markUserActivity();
}
