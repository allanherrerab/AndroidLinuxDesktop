package org.debiannoroot.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import org.debiannoroot.settings.UiText;

/** Only the two control islands consume touches; the desktop between them stays usable. */
@android.annotation.SuppressLint("ViewConstructor")
final class GameControlsView extends FrameLayout {
    private final HeldKeys held;
    private final DirectionPad pad;
    GameControlsView(Context context,HeldKeys held,java.util.function.Consumer<android.widget.TextView> style,Runnable hide){
        super(context);this.held=held;setMotionEventSplittingEnabled(true);
        pad=new DirectionPad(context);LayoutParams left=new LayoutParams(dp(156),dp(156),Gravity.BOTTOM|Gravity.START);
        left.setMargins(dp(4),0,0,dp(8));addView(pad,left);
        LinearLayout actions=new LinearLayout(context);actions.setOrientation(LinearLayout.VERTICAL);
        Button close=new Button(context);close.setText("×");close.setContentDescription(UiText.get(context,"Hide game controls"));
        close.setFocusable(false);style.accept(close);close.setOnClickListener(v->hide.run());
        actions.addView(close,new LinearLayout.LayoutParams(-1,dp(40)));
        String[] labels={"Space","N","V","Ctrl","Enter","Esc"};
        int[] codes={KeyEvent.KEYCODE_SPACE,KeyEvent.KEYCODE_N,KeyEvent.KEYCODE_V,KeyEvent.KEYCODE_CTRL_LEFT,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_ESCAPE};
        for(int row=0;row<2;row++){
            LinearLayout line=new LinearLayout(context);line.setMotionEventSplittingEnabled(true);
            for(int col=0;col<3;col++){
                int i=row*3+col;HoldKeyButton button=new HoldKeyButton(context,held,codes[i]);
                button.setText(UiText.get(context,labels[i]));style.accept(button);button.setTextSize(12);button.setPadding(0,0,0,0);button.setMinWidth(0);button.setMinimumWidth(0);
                line.addView(button,new LinearLayout.LayoutParams(dp(52),dp(52)));
            }
            actions.addView(line);
        }
        LayoutParams right=new LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);right.setMargins(0,0,dp(4),dp(8));addView(actions,right);
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    void release(){pad.release();for(int i=0;i<getChildCount();i++)clearPressed(getChildAt(i));}
    private void clearPressed(View view){
        held.set(view);view.setPressed(false);
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)clearPressed(group.getChildAt(i));}
    }
    private final class DirectionPad extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int mask;
        private final int[] codes={KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN};
        DirectionPad(Context context){super(context);setContentDescription(UiText.get(context,"Directional pad"));}
        void release(){held.set(this);mask=0;invalidate();}
        @Override public boolean onTouchEvent(MotionEvent event){
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN)getParent().requestDisallowInterceptTouchEvent(true);
            int next=0,action=event.getActionMasked();
            if(action!=MotionEvent.ACTION_CANCEL&&action!=MotionEvent.ACTION_UP)for(int i=0;i<event.getPointerCount();i++){
                if(action==MotionEvent.ACTION_POINTER_UP&&i==event.getActionIndex())continue;
                float x=event.getX(i)/getWidth(),y=event.getY(i)/getHeight();
                if(x<0||x>=1||y<0||y>=1)continue;
                if(x<1f/3)next|=1;else if(x>=2f/3)next|=2;
                if(y<1f/3)next|=4;else if(y>=2f/3)next|=8;
            }
            ArrayList<Long> keys=new ArrayList<>();for(int i=0;i<4;i++)if((next&(1<<i))!=0)keys.add(HeldKeys.key(0,codes[i]));
            long[] values=new long[keys.size()];for(int i=0;i<values.length;i++)values[i]=keys.get(i);
            held.set(this,values);mask=next;invalidate();
            if(action==MotionEvent.ACTION_UP)performClick();return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float w=getWidth()/3f,h=getHeight()/3f;
            String[] labels={"↖","↑","↗","←","•","→","↙","↓","↘"};int[] masks={5,4,6,1,0,2,9,8,10};
            paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(24));
            for(int i=0;i<9;i++){
                float x=(i%3)*w,y=(i/3)*h;int bits=masks[i];
                paint.setColor(bits!=0&&(mask&bits)==bits?0xdd3874b8:0xbb30343b);
                canvas.drawRoundRect(x+2,y+2,x+w-2,y+h-2,dp(8),dp(8),paint);
                paint.setColor(0xffffffff);canvas.drawText(labels[i],x+w/2,y+h/2-(paint.ascent()+paint.descent())/2,paint);
            }
        }
    }
}
