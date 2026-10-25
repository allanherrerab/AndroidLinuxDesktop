package org.debiannoroot;

/** Shared rendering/input geometry; no Android dependency. */
public final class DisplayGeometry {
    public final float left, top, width, height;
    private DisplayGeometry(float left, float top, float width, float height) {
        this.left=left; this.top=top; this.width=width; this.height=height;
    }
    public static DisplayGeometry compute(int viewWidth, int viewHeight, int guestWidth,
            int guestHeight, String mode, float customScale, int border, boolean aspect43) {
        float availableW=Math.max(1,viewWidth)*(1-border/50f);
        float availableH=Math.max(1,viewHeight)*(1-border/50f);
        float ratio=aspect43 ? 4f/3f : (float)guestWidth/guestHeight;
        float width=availableW, height=availableH;
        float fit=Math.min(availableW/guestWidth,availableH/guestHeight);
        if (mode.equals("integer")) {
            float scale=Math.max(1,(float)Math.floor(fit));
            width=guestWidth*scale; height=guestHeight*scale;
        } else if (mode.equals("custom")) {
            width=guestWidth*customScale; height=guestHeight*customScale;
        } else if (!mode.equals("stretch")) {
            width=Math.min(availableW,availableH*ratio); height=width/ratio;
        }
        return new DisplayGeometry((viewWidth-width)/2,(viewHeight-height)/2,width,height);
    }
    public static boolean validResolution(int width,int height) {
        return width>=320 && height>=240 && width<=7680 && height<=4320 && (long)width*height<=16777216;
    }
    public static float pointerGain(int speed,int acceleration,float distance,float elapsedMillis) {
        float[] speeds={0.25f,0.5f,1,1.5f,2};
        float gain=speeds[Math.max(0,Math.min(4,speed))];
        float velocity=distance/Math.max(4,elapsedMillis);
        return gain*(1+Math.max(0,Math.min(3,acceleration))*Math.min(2,velocity)*0.35f);
    }
}
