package org.debiannoroot.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.Button;

/** Touch holds, including multiple fingers; accessibility clicks remain key taps. */
final class HoldKeyButton extends Button {
    private final HeldKeys held;
    private final long[] keys;
    HoldKeyButton(Context context,HeldKeys held,int... codes){
        super(context);this.held=held;keys=new long[codes.length];
        for(int i=0;i<codes.length;i++)keys[i]=HeldKeys.key(0,codes[i]);
        setFocusable(false);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN)getParent().requestDisallowInterceptTouchEvent(true);
        boolean down=false;
        if(action!=MotionEvent.ACTION_CANCEL&&action!=MotionEvent.ACTION_UP){
            for(int i=0;i<event.getPointerCount();i++){
                if(action==MotionEvent.ACTION_POINTER_UP&&i==event.getActionIndex())continue;
                if(event.getX(i)>=0&&event.getX(i)<getWidth()&&event.getY(i)>=0&&event.getY(i)<getHeight())down=true;
            }
        }
        held.set(this,down?keys:new long[0]);setPressed(down);
        if(action==MotionEvent.ACTION_UP)super.performClick();
        return true;
    }
    @Override public boolean performClick(){
        Object click=new Object();held.set(click,keys);held.set(click);super.performClick();return true;
    }
    @Override protected void onDetachedFromWindow(){held.set(this);super.onDetachedFromWindow();}
}
