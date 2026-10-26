package org.debiannoroot.runtime;

/** Java value object for the first-party C rendering/input geometry. */
public final class DisplayGeometry {
    public final float left, top, width, height;
    private DisplayGeometry(float left, float top, float width, float height) {
        this.left=left; this.top=top; this.width=width; this.height=height;
    }
    public static DisplayGeometry compute(int viewWidth, int viewHeight, int guestWidth,
            int guestHeight, String mode, float customScale, int border, boolean aspect43) {
        float[] value=NativeCore.geometry(viewWidth,viewHeight,guestWidth,guestHeight,mode,customScale,border,aspect43);
        return new DisplayGeometry(value[0],value[1],value[2],value[3]);
    }
    public static boolean validResolution(int width,int height) {
        return NativeCore.validResolution(width,height);
    }
    public static float pointerGain(int speed,int acceleration,float distance,float elapsedMillis) {
        return NativeCore.pointerGain(speed,acceleration,distance,elapsedMillis);
    }
}
