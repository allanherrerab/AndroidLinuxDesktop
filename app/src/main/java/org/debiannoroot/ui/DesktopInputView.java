package org.debiannoroot.ui;

import org.debiannoroot.runtime.DisplayGeometry;
import org.debiannoroot.runtime.NativeCore;
import org.debiannoroot.session.SessionSettings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

@android.annotation.SuppressLint("ViewConstructor") // Created programmatically with its input owner.
final class DesktopInputView extends View {
    interface Input {
        void pointer(int x, int y, int buttons);
        void key(int symbol, boolean pressed);
        void text(String text);
        default boolean hardware(int scanCode,int keyCode,boolean down) { return false; }
        default float[] cursor() { return null; }
    }

    private final Input input;
    private final SessionSettings settings;
    private boolean nativeDisplay;
    private float sourceLeft,sourceTop,sourceWidth=1280,sourceHeight=800;
    private float lastX,lastY,scrollRemainder;
    private long lastTime;
    private boolean multiTouch,multiScrolled,longConsumed,clickPending;
    private int heldButtons;
    private android.widget.Magnifier magnifier;
    private View magnifierSource;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private float joyX,joyY;
    private long joyStart;
    private android.hardware.SensorManager sensors;
    private long gyroTime;
    private final Runnable joystickTick=new Runnable(){public void run(){
        if(Math.abs(joyX)+Math.abs(joyY)<0.1f)return;
        float held=Math.min(2,(android.os.SystemClock.uptimeMillis()-joyStart)/1000f);
        float gain=DisplayGeometry.pointerGain(settings.number("joystickSpeed",2,0,4),settings.number("joystickAccel",0,0,3),held*8,16);
        moveRelative(joyX*12*gain,joyY*12*gain,16,false);handler.postDelayed(this,16);
    }};
    private final android.hardware.SensorEventListener gyro=new android.hardware.SensorEventListener(){
        public void onAccuracyChanged(android.hardware.Sensor sensor,int accuracy){}
        public void onSensorChanged(android.hardware.SensorEvent event){
            if(!settings.flag("gyroscope",false))return;
            float dt=gyroTime==0?0:Math.min(0.05f,(event.timestamp-gyroTime)/1e9f);gyroTime=event.timestamp;
            float gain=DisplayGeometry.pointerGain(settings.number("gyroSpeed",2,0,4),0,0,16)*800*dt;
            float dx=-event.values[1]*gain,dy=-event.values[0]*gain;
            int rotation=getDisplay()==null?0:getDisplay().getRotation();
            if(rotation==1){float swap=dx;dx=-dy;dy=swap;}else if(rotation==3){float swap=dx;dx=dy;dy=-swap;}
            else if(rotation==2){dx=-dx;dy=-dy;}
            moveRelative(dx,dy,16,false);
        }
    };

    private final RectF viewport = new RectF();
    private int remoteWidth = 1280;
    private int remoteHeight = 800;
    private int deadAccent;
    private int pointerX = 640;
    private int pointerY = 400;
    private final android.util.SparseIntArray pressedKeys = new android.util.SparseIntArray();
    private final GestureDetector gestures;
    private MotionEvent longEvent;
    private final Runnable hold=this::performHold;
    private void performHold() {
        if(longEvent!=null&&!multiTouch) {
            String right=settings.value("rightClick","hold_two"),left=settings.value("leftClick","tap_hold");
            if(right.equals("hold")||right.equals("hold_two")){click(4);longConsumed=true;}
            else if(left.equals("hold")||left.equals("tap_hold")){heldButtons|=1;emit(heldButtons);longConsumed=true;}
        }
    }

    DesktopInputView(Context context, Input input) {
        super(context);
        this.input = input;
        settings=new SessionSettings(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundColor(Color.BLACK);
        gestures = new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            public boolean onDown(MotionEvent event){return true;}
            public boolean onSingleTapUp(MotionEvent event){
                String method=settings.value("leftClick","tap_hold");
                if(!longConsumed && (method.equals("tap")||method.equals("tap_hold")||method.equals("near"))) {
                    if(!method.equals("near")||Math.hypot(event.getX()-viewX(),event.getY()-viewY())<60)clickPending=true;
                }
                return true;
            }
            public void onLongPress(MotionEvent event){
                String right=settings.value("rightClick","hold_two"),left=settings.value("leftClick","tap_hold");
                if(right.equals("hold")||right.equals("hold_two")){click(4);longConsumed=true;}
                else if(left.equals("hold")||left.equals("tap_hold")){heldButtons|=1;emit(heldButtons);longConsumed=true;}
            }
            public boolean onScroll(MotionEvent start,MotionEvent event,float dx,float dy){
                if(settings.value("mouseMode","relative").equals("relative"))moveRelative(-dx,-dy,Math.max(4,event.getEventTime()-lastTime),true);
                else absolute(event);
                lastTime=event.getEventTime();return true;
            }
            public boolean onDoubleTap(MotionEvent event){heldButtons|=1;emit(heldButtons);return true;}
        });
        gestures.setIsLongpressEnabled(false);
    }

    void nativeOverlay(View source) {
        nativeDisplay=true;magnifierSource=source;setBackgroundColor(Color.TRANSPARENT);
        remoteWidth=settings.width();remoteHeight=settings.height();
        pointerX=remoteWidth/2;pointerY=remoteHeight/2;
    }
    void viewport(int x,int y,int w,int h,float sx,float sy,float sw,float sh){
        viewport.set(x,y,x+w,y+h);sourceLeft=sx;sourceTop=sy;sourceWidth=sw;sourceHeight=sh;
        remoteWidth=settings.width();remoteHeight=settings.height();
    }
    private float viewX(){return viewport.left+(pointerX-sourceLeft)*viewport.width()/Math.max(1,sourceWidth);}
    private float viewY(){return viewport.top+(pointerY-sourceTop)*viewport.height()/Math.max(1,sourceHeight);}
    private void emit(int mask){
        pointerX=Math.max(0,Math.min(remoteWidth-1,pointerX));pointerY=Math.max(0,Math.min(remoteHeight-1,pointerY));
        input.pointer(pointerX,pointerY,mask);invalidate();
        if(android.os.Build.VERSION.SDK_INT>=28 && nativeDisplay && settings.number("magnifier",0,0,2)==2 && magnifierSource!=null){
            if(magnifier==null)magnifier=new android.widget.Magnifier(magnifierSource);
            magnifier.show(Math.max(0,Math.min(getWidth()-1,viewX())),Math.max(0,Math.min(getHeight()-1,viewY())));
        } else dismissMagnifier();
    }
    private void click(int mask){emit(heldButtons|mask);emit(heldButtons);}
    void mouseButton(int mask,boolean down){if(down)heldButtons|=mask;else heldButtons&=~mask;emit(heldButtons);}
    private void moveRelative(float dx,float dy,float elapsed,boolean accelerate){
        float gain=accelerate?DisplayGeometry.pointerGain(settings.number("mouseSpeed",2,0,4),settings.number("mouseAccel",0,0,3),(float)Math.hypot(dx,dy),elapsed):1;
        pointerX+=Math.round(dx*gain);pointerY+=Math.round(dy*gain);emit(heldButtons);
    }
    private void absolute(MotionEvent event){
        if(viewport.width()<=0||viewport.height()<=0)return;
        pointerX=Math.round(sourceLeft+(event.getX()-viewport.left)*sourceWidth/viewport.width());
        pointerY=Math.round(sourceTop+(event.getY()-viewport.top)*sourceHeight/viewport.height());emit(heldButtons);
    }
    void refreshSettings(){
        setPointerIcon(android.view.PointerIcon.getSystemIcon(getContext(),settings.flag("hideSystemPointer",true)?android.view.PointerIcon.TYPE_NULL:android.view.PointerIcon.TYPE_ARROW));
        if(sensors==null)sensors=(android.hardware.SensorManager)getContext().getSystemService(Context.SENSOR_SERVICE);
        sensors.unregisterListener(gyro);gyroTime=0;
        if(settings.flag("gyroscope",false)){
            android.hardware.Sensor sensor=sensors.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE);
            if(sensor!=null)sensors.registerListener(gyro,sensor,android.hardware.SensorManager.SENSOR_DELAY_GAME);
        }
    }
    private void dismissMagnifier(){
        if(android.os.Build.VERSION.SDK_INT>=28 && magnifier!=null){magnifier.dismiss();magnifier=null;}
    }
    void suspendInput(){
        if(sensors!=null)sensors.unregisterListener(gyro);joyX=joyY=0;handler.removeCallbacks(joystickTick);
        handler.removeCallbacks(hold);heldButtons=0;emit(0);dismissMagnifier();
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();refreshSettings();}
    @Override protected void onDetachedFromWindow(){
        emit(0);heldButtons=0;handler.removeCallbacksAndMessages(null);
        if(sensors!=null)sensors.unregisterListener(gyro);dismissMagnifier();
        if(longEvent!=null){longEvent.recycle();longEvent=null;}super.onDetachedFromWindow();
    }

    @Override public boolean performClick(){super.performClick();click(1);return true;}
    @Override public boolean onTouchEvent(MotionEvent event){
        requestFocus();int action=event.getActionMasked();
        if(event.isFromSource(InputDevice.SOURCE_MOUSE)||event.getToolType(0)==MotionEvent.TOOL_TYPE_STYLUS){
            int buttons=event.getButtonState();
            heldButtons=((buttons&MotionEvent.BUTTON_PRIMARY)!=0?1:0)|((buttons&MotionEvent.BUTTON_TERTIARY)!=0?2:0)|((buttons&MotionEvent.BUTTON_SECONDARY)!=0?4:0);
            if(event.getToolType(0)==MotionEvent.TOOL_TYPE_STYLUS&&action!=MotionEvent.ACTION_UP&&action!=MotionEvent.ACTION_CANCEL&&heldButtons==0)heldButtons=1;
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)heldButtons=0;
            absolute(event);return true;
        }
        String left=settings.value("leftClick","tap_hold");
        if(action==MotionEvent.ACTION_DOWN){
            float[] real=input.cursor();if(real!=null){pointerX=Math.round(real[0]);pointerY=Math.round(real[1]);}
            lastX=event.getX();lastY=event.getY();lastTime=event.getEventTime();multiTouch=false;multiScrolled=false;longConsumed=false;
            if(settings.value("mouseMode","relative").equals("absolute"))absolute(event);
            if(left.equals("normal")){heldButtons|=1;emit(heldButtons);}
            if(longEvent!=null)longEvent.recycle();longEvent=MotionEvent.obtain(event);
            int[] delays={300,500,700,1000,1500};handler.postDelayed(hold,delays[settings.number("holdDelay",1,0,4)]);
        }
        if(action==MotionEvent.ACTION_POINTER_DOWN){
            multiTouch=true;multiScrolled=false;handler.removeCallbacks(hold);lastX=event.getX();lastY=event.getY();scrollRemainder=0;
            MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);gestures.onTouchEvent(cancel);cancel.recycle();
            if(left.equals("second")){heldButtons|=1;emit(heldButtons);}return true;
        }
        if(multiTouch){
            if(action==MotionEvent.ACTION_MOVE && event.getPointerCount()>1){
                float dy=event.getY()-lastY;
                if(left.equals("second"))moveRelative(event.getX()-lastX,dy,16,true);
                else {scrollRemainder+=dy;while(Math.abs(scrollRemainder)>12){click(scrollRemainder>0?8:16);scrollRemainder+=scrollRemainder>0?-12:12;multiScrolled=true;}}
                lastX=event.getX();lastY=event.getY();
            }
            if(action==MotionEvent.ACTION_POINTER_UP && !multiScrolled && !left.equals("second")){
                String right=settings.value("rightClick","hold_two");if(right.equals("second")||right.equals("hold_two"))click(4);
            }
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_POINTER_UP){heldButtons=0;emit(0);}
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE && longEvent!=null && Math.hypot(event.getX()-longEvent.getX(),event.getY()-longEvent.getY())>12)handler.removeCallbacks(hold);
        boolean pressure=left.equals("pressure")||settings.value("rightClick","hold_two").equals("pressure");
        if(pressure){
            int mask=event.getPressure()>=settings.number("pressure",70,10,100)/100f?(left.equals("pressure")?1:4):0;
            heldButtons=(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)?0:mask;
            if(settings.value("mouseMode","relative").equals("relative"))moveRelative(event.getX()-lastX,event.getY()-lastY,event.getEventTime()-lastTime,true);else absolute(event);
            lastX=event.getX();lastY=event.getY();lastTime=event.getEventTime();
        }else gestures.onTouchEvent(event);
        if(clickPending){clickPending=false;performClick();}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            handler.removeCallbacks(hold);heldButtons=0;emit(0);
            if(longEvent!=null){longEvent.recycle();longEvent=null;}
            dismissMagnifier();
        }
        return true;
    }
    @Override public boolean onGenericMotionEvent(MotionEvent event){
        if(event.isFromSource(InputDevice.SOURCE_JOYSTICK)&&settings.flag("joystick",false)){
            joyX=event.getAxisValue(MotionEvent.AXIS_X);joyY=event.getAxisValue(MotionEvent.AXIS_Y);
            if(Math.abs(joyX)<0.15)joyX=0;if(Math.abs(joyY)<0.15)joyY=0;
            handler.removeCallbacks(joystickTick);joyStart=android.os.SystemClock.uptimeMillis();handler.post(joystickTick);return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_SCROLL){
            float scroll=event.getAxisValue(MotionEvent.AXIS_VSCROLL);if(scroll!=0)click(scroll>0?8:16);
            float horizontal=event.getAxisValue(MotionEvent.AXIS_HSCROLL);if(horizontal!=0)click(horizontal>0?64:32);return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_HOVER_MOVE&&settings.flag("hover",true)){
            float dx=event.getX()-viewX(),dy=event.getY()-viewY();
            if(!settings.flag("hoverFilter",true)||Math.hypot(dx,dy)>2)absolute(event);return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_BUTTON_PRESS||event.getActionMasked()==MotionEvent.ACTION_BUTTON_RELEASE){
            int b=event.getButtonState();heldButtons=((b&1)!=0?1:0)|((b&2)!=0?4:0)|((b&4)!=0?2:0);absolute(event);return true;
        }
        return super.onGenericMotionEvent(event);
    }
    @Override public boolean onTrackballEvent(MotionEvent event){
        if(!settings.flag("joystick",false))return super.onTrackballEvent(event);
        moveRelative(event.getX()*30,event.getY()*30,16,true);
        if(event.getAction()==MotionEvent.ACTION_DOWN)mouseButton(1,true);
        if(event.getAction()==MotionEvent.ACTION_UP)mouseButton(1,false);return true;
    }

    private int symbol(KeyEvent event) {
        int point = event.getUnicodeChar(event.getMetaState() &
                ~(KeyEvent.META_CTRL_MASK | KeyEvent.META_ALT_MASK | KeyEvent.META_META_MASK));
        return NativeCore.keySymbol(event.getKeyCode(),point);
    }

    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if(code==KeyEvent.KEYCODE_MENU && !settings.value("rightClick","hold_two").equals("none")){mouseButton(4,true);return true;}
        if(code==KeyEvent.KEYCODE_DPAD_CENTER && (settings.flag("dpadClick",false)||settings.value("leftClick","tap_hold").equals("dpad"))){mouseButton(1,true);return true;}
        if(settings.preferences.contains("map_"+code)){int mapped=settings.preferences.getInt("map_"+code,0);pressedKeys.put(code,mapped);if((mapped&0xff000000)==0x02000000)mouseButton(mapped&255,true);else input.key(mapped,true);return true;}
        if(input.hardware(event.getScanCode(),code,true))return true;
        int unicode = event.getUnicodeChar();
        if ((unicode & android.view.KeyCharacterMap.COMBINING_ACCENT) != 0) {
            deadAccent = unicode & android.view.KeyCharacterMap.COMBINING_ACCENT_MASK;
            return true;
        }
        int symbol = symbol(event);
        if (symbol == 0) return super.onKeyDown(code, event);
        if (deadAccent != 0 && unicode != 0) {
            int combined = android.view.KeyCharacterMap.getDeadChar(deadAccent, unicode);
            if (combined != 0) symbol = combined <= 255 ? combined : 0x01000000 | combined;
            deadAccent = 0;
        }
        pressedKeys.put(code, symbol);
        input.key(symbol, true);
        return true;
    }

    @Override public boolean onKeyUp(int code, KeyEvent event) {
        if(code==KeyEvent.KEYCODE_MENU && !settings.value("rightClick","hold_two").equals("none")){mouseButton(4,false);return true;}
        if(code==KeyEvent.KEYCODE_DPAD_CENTER && (settings.flag("dpadClick",false)||settings.value("leftClick","tap_hold").equals("dpad"))){mouseButton(1,false);return true;}
        int symbol = pressedKeys.get(code, 0);
        pressedKeys.delete(code);
        if(symbol==0 && input.hardware(event.getScanCode(),code,false))return true;
        if (symbol == 0) return super.onKeyUp(code, event);
        if((symbol&0xff000000)==0x02000000)mouseButton(symbol&255,false);else input.key(symbol, false);
        return true;
    }

    @Override public boolean onCheckIsTextEditor() { return true; }

    @Override public InputConnection onCreateInputConnection(EditorInfo info) {
        info.inputType = android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        info.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NONE;
        return new BaseInputConnection(this, false) {
            private String composing = "";
            @Override public boolean setComposingText(CharSequence text, int cursor) {
                composing = text.toString();
                return true;
            }
            @Override public boolean finishComposingText() {
                if (!composing.isEmpty()) input.text(composing);
                composing = "";
                return true;
            }
            @Override public boolean commitText(CharSequence text, int cursor) {
                composing = "";
                input.text(text.toString());
                return true;
            }
            @Override public boolean deleteSurroundingText(int before, int after) {
                for (int i = 0; i < Math.min(before, 1024); i++) {
                    input.key(0xff08, true);
                    input.key(0xff08, false);
                }
                for (int i = 0; i < Math.min(after, 1024); i++) {
                    input.key(0xffff, true);
                    input.key(0xffff, false);
                }
                return true;
            }
            @Override public boolean sendKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_MULTIPLE && event.getCharacters() != null) {
                    input.text(event.getCharacters());
                    return true;
                }
                return event.getAction() == KeyEvent.ACTION_DOWN
                        ? onKeyDown(event.getKeyCode(), event) : onKeyUp(event.getKeyCode(), event);
            }
        };
    }
}
