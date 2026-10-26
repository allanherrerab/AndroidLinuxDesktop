package org.debiannoroot.ui;

import org.debiannoroot.R;
import org.debiannoroot.runtime.NativeCore;
import org.debiannoroot.session.DesktopService;
import org.debiannoroot.session.SessionSettings;
import org.debiannoroot.session.X11Service;

import android.content.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.termux.x11.LorieView;
import java.util.*;

/** Embedded native X11 surface with the app's persistent input/display controls. */
public final class DesktopActivity extends com.termux.x11.MainActivity implements DesktopInputView.Input {
    public static final String[] EXTRA_KEYS={"Esc","Tab","Ctrl","Alt","Shift","Space","Delete","Left","Right","Up","Down","PgUp","PgDn","F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12"};
    public static final String[] DEFAULT_KEYS={"Esc","Tab","Ctrl","Alt","Up","Down"};
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Map<Long,Integer> pressed=new HashMap<>();
    private LorieView x11;
    private DesktopInputView desktop;
    private TextView status;
    private LinearLayout toolbar,page;
    private HorizontalScrollView controlsBar,functionBar;
    private Button showControls;
    private IBinder server;
    private boolean bound,closed,restoreKeyboard;
    private int lastButtons;
    private SessionSettings settings;
    private final ServiceConnection binding=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){server=binder;connect();}
        public void onServiceDisconnected(ComponentName name){server=null;status.setText(R.string.x11_disconnected);status.setVisibility(View.VISIBLE);}
    };
    private final Runnable retry=this::connect;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);settings=new SessionSettings(this);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        restoreKeyboard=state!=null&&state.getBoolean("keyboard_visible",false);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(android.graphics.Color.BLACK);
        page.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        toolbar=new LinearLayout(this);controlsBar=new HorizontalScrollView(this);controlsBar.addView(toolbar);page.addView(controlsBar);
        functionBar=new HorizontalScrollView(this);page.addView(functionBar);
        status=new TextView(this);status.setTextColor(android.graphics.Color.WHITE);status.setText(R.string.waiting);page.addView(status);
        FrameLayout surface=new FrameLayout(this);x11=new LorieView(this);surface.addView(x11,new FrameLayout.LayoutParams(-1,-1));
        desktop=new DesktopInputView(this,this);desktop.nativeOverlay(x11);surface.addView(desktop,new FrameLayout.LayoutParams(-1,-1));
        showControls=new Button(this);showControls.setText("☰");showControls.setContentDescription(t("Show controls"));
        showControls.setOnClickListener(v->{settings.preferences.edit().putBoolean("fullscreen",false).apply();applyFullscreen();});
        FrameLayout.LayoutParams restore=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);surface.addView(showControls,restore);
        x11.setListener(desktop::viewport);page.addView(surface,new LinearLayout.LayoutParams(-1,0,1));setContentView(page);
        bound=bindService(new Intent(this,X11Service.class),binding,BIND_AUTO_CREATE);
    }
    private void style(TextView view){
        view.setTextSize(settings.number("keySize",14,10,26));view.setAlpha(settings.number("controlsOpacity",100,20,100)/100f);
        String theme=settings.value("controlsTheme","system");
        if(!theme.equals("system")){view.setTextColor(theme.equals("dark")?android.graphics.Color.WHITE:android.graphics.Color.BLACK);view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(theme.equals("dark")?0xff30343b:0xffeeeeee));}
    }
    private void button(String label,Runnable action){Button b=new Button(this);b.setText(t(label));style(b);b.setFocusable(false);b.setOnClickListener(v->action.run());toolbar.addView(b);}
    private void keyToggle(String label,int code){ToggleButton toggle=new ToggleButton(this);toggle.setTextOn(t(label)+" ✓");toggle.setTextOff(t(label));toggle.setText(t(label));toggle.setChecked(false);style(toggle);toggle.setFocusable(false);toggle.setOnCheckedChangeListener((b,on)->hardware(0,code,on));toolbar.addView(toggle);}
    private void mouseToggle(){ToggleButton toggle=new ToggleButton(this);toggle.setTextOn(t("Drag")+" ✓");toggle.setTextOff(t("Drag"));toggle.setText(t("Drag"));toggle.setChecked(false);style(toggle);toggle.setFocusable(false);toggle.setOnCheckedChangeListener((b,on)->desktop.mouseButton(1,on));toolbar.addView(toggle);}
    private int keyCode(String label){
        return NativeCore.shortcutKey(label);
    }
    private void toolbar(){
        toolbar.removeAllViews();button("Back",this::finish);button("Settings",()->startActivity(new Intent(this,SettingsActivity.class)));
        button("Keyboard",()->{desktop.requestFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).toggleSoftInput(InputMethodManager.SHOW_IMPLICIT,0);});
        button("Volume",this::showVolume);
        button("Zoom",this::showZoom);
        button("F1–F12",()->{settings.preferences.edit().putBoolean("functionKeys",!settings.flag("functionKeys",false)).apply();functionKeys();});
        button("Fullscreen",()->{settings.preferences.edit().putBoolean("fullscreen",true).apply();applyFullscreen();});
        functionKeys();
        Set<String> visible=settings.preferences.getStringSet("extraKeys",new HashSet<>(Arrays.asList(DEFAULT_KEYS)));
        for(String label:EXTRA_KEYS)if(visible.contains(label)){
            int code=keyCode(label);if(label.equals("Ctrl")||label.equals("Alt")||label.equals("Shift"))keyToggle(label,code);
            else button(label,()->{hardware(0,code,true);hardware(0,code,false);});
        }
        button("Left click",()->{desktop.mouseButton(1,true);desktop.mouseButton(1,false);});
        button("Right click",()->{desktop.mouseButton(4,true);desktop.mouseButton(4,false);});
        button("Middle click",()->{desktop.mouseButton(2,true);desktop.mouseButton(2,false);});mouseToggle();
        button("Paste",()->{ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(clipboard.hasPrimaryClip()&&clipboard.getPrimaryClip().getItemCount()>0){CharSequence value=clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);if(value!=null)text(value.subSequence(0,Math.min(value.length(),65536)).toString());}});
    }
    private void functionKeys(){
        functionBar.removeAllViews();LinearLayout keys=new LinearLayout(this);functionBar.addView(keys);
        for(int i=0;i<12;i++){final int code=KeyEvent.KEYCODE_F1+i;Button key=new Button(this);key.setText("F"+(i+1));style(key);key.setFocusable(false);key.setOnClickListener(v->{hardware(0,code,true);hardware(0,code,false);});keys.addView(key);}
        functionBar.setVisibility(settings.flag("functionKeys",false)&&!settings.flag("fullscreen",false)?View.VISIBLE:View.GONE);
    }
    private void showZoom(){
        int[] values={100,125,150,175,200,250,300,400};String[] labels=new String[values.length];int selected=0;
        for(int i=0;i<values.length;i++){labels[i]=values[i]+"%";if(values[i]==settings.number("zoom",100,100,400))selected=i;}
        new android.app.AlertDialog.Builder(this).setTitle(t("Desktop zoom")).setSingleChoiceItems(labels,selected,(dialog,index)->{
            settings.preferences.edit().putInt("zoom",values[index]).apply();x11.update();dialog.dismiss();
        }).setNegativeButton(t("Cancel"),null).show();
    }
    private void applyFullscreen(){
        boolean full=settings.flag("fullscreen",false);
        getWindow().getDecorView().setSystemUiVisibility(full||settings.flag("immersive",true)?View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION:View.SYSTEM_UI_FLAG_VISIBLE);
        controlsBar.setVisibility(full?View.GONE:View.VISIBLE);
        functionBar.setVisibility(!full&&settings.flag("functionKeys",false)?View.VISIBLE:View.GONE);
        showControls.setVisibility(full?View.VISIBLE:View.GONE);
        page.requestApplyInsets();x11.post(x11::update);
    }
    private final Runnable progress=new Runnable(){public void run(){
        if(closed)return;
        try{
            String phase=new String(java.nio.file.Files.readAllBytes(new java.io.File(getFilesDir(),"rootfs/tmp/debian-noroot-progress").toPath()),java.nio.charset.StandardCharsets.US_ASCII).trim();
            if(phase.equals("locale_setup")||phase.equals("locale_failed")){status.setText(t(phase.equals("locale_setup")?"Preparing language support. First launch may download packages; see the session log.":"Language setup failed. Check the network and session log, then restart."));status.setVisibility(View.VISIBLE);}
            else if(x11.isConnected())status.setVisibility(View.GONE);
        }catch(java.io.IOException ignored){}
        handler.postDelayed(this,1000);
    }};
    private void showVolume(){
        android.media.AudioManager audio=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(32,16,32,16);
        volumeSlider(controls,"Phone media",audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC),
                audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC),
                value->audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,value,0));
        volumeSlider(controls,"App playback",settings.number("volume",100,0,100),100,
                value->settings.preferences.edit().putInt("volume",value).apply());
        new android.app.AlertDialog.Builder(this).setTitle(t("Playback volume")).setView(controls)
                .setPositiveButton(android.R.string.ok,null).show();
    }
    private void volumeSlider(LinearLayout parent,String name,int current,int maximum,java.util.function.IntConsumer change){
        TextView label=new TextView(this);label.setText(t(name)+": "+Math.round(current*100f/Math.max(1,maximum))+"%");parent.addView(label);
        SeekBar slider=new SeekBar(this);slider.setMax(maximum);slider.setProgress(current);parent.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar view){}
            public void onStopTrackingTouch(SeekBar view){}
            public void onProgressChanged(SeekBar view,int value,boolean fromUser){
                if(fromUser){change.accept(value);label.setText(t(name)+": "+Math.round(value*100f/Math.max(1,maximum))+"%");}
            }
        });
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int code=event.getKeyCode();
        if(code==KeyEvent.KEYCODE_VOLUME_UP||code==KeyEvent.KEYCODE_VOLUME_DOWN||code==KeyEvent.KEYCODE_VOLUME_MUTE){
            if(event.getAction()==KeyEvent.ACTION_DOWN){
                int direction=code==KeyEvent.KEYCODE_VOLUME_UP?android.media.AudioManager.ADJUST_RAISE:
                        code==KeyEvent.KEYCODE_VOLUME_DOWN?android.media.AudioManager.ADJUST_LOWER:android.media.AudioManager.ADJUST_TOGGLE_MUTE;
                ((android.media.AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,direction,android.media.AudioManager.FLAG_SHOW_UI);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onResume(){
        super.onResume();if(settings==null)return;
        getWindow().setFlags(settings.flag("keepAwake",true)?WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON:0,WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyFullscreen();
        String orientation=settings.value("orientation","auto");setRequestedOrientation(orientation.equals("landscape")?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:orientation.equals("portrait")?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        toolbar();desktop.refreshSettings();x11.update();handler.removeCallbacks(progress);handler.post(progress);
        if(android.os.Build.VERSION.SDK_INT<28&&settings.number("magnifier",0,0,2)==2)x11.zoom(200);
    }
    private void connect(){
        handler.removeCallbacks(retry);if(closed||x11==null||x11.isConnected())return;
        if(server!=null)try{
            ParcelFileDescriptor fd=X11Service.connection(server);
            if(fd!=null){x11.attach(fd.detachFd());status.setVisibility(View.GONE);startService(new Intent(this,DesktopService.class).setAction(DesktopService.ACTION_CONNECTED));return;}
        }catch(Exception error){status.setText(getString(R.string.x11_connecting,error.getMessage()));}
        String phase=getSharedPreferences("session",MODE_PRIVATE).getString("phase","idle");
        if(phase.equals("error")||phase.equals("ended")||phase.equals("idle")){
            status.setText(getSharedPreferences("session",MODE_PRIVATE).getString("status",t("Start Debian from the main screen.")));return;
        }
        handler.postDelayed(retry,500);
    }
    @Override public void clientConnectedStateChanged(){
        if(closed)return;
        status.setVisibility(x11.isConnected()?View.GONE:View.VISIBLE);
        if(!x11.isConnected())handler.postDelayed(retry,500);
    }
    @Override public float[] cursor(){return x11==null||!x11.isConnected()?null:x11.cursor();}
    @Override public void pointer(int x,int y,int buttons){
        if(closed||!x11.isConnected())return;
        x11.mouse(x,y,0,false,false);
        int[] masks={1,2,4,8,16,32,64};int[] nativeButtons={1,2,3,4,5,6,7};
        for(int i=0;i<masks.length;i++)if(((lastButtons^buttons)&masks[i])!=0){
            if(i>=3){if((buttons&masks[i])!=0)x11.mouse(i>=5?(i==5?-120:120):0,i<5?(i==3?-120:120):0,4,false,true);}
            else x11.mouse(0,0,nativeButtons[i],(buttons&masks[i])!=0,true);
        }
        lastButtons=buttons;
    }
    @Override public boolean hardware(int scanCode,int keyCode,boolean down){
        if(keyCode==KeyEvent.KEYCODE_BACK)return false;
        if(closed||!x11.isConnected())return true;
        long key=((long)scanCode<<32)|(keyCode&0xffffffffL);
        if(down)pressed.put(key,keyCode);else pressed.remove(key);x11.key(scanCode,keyCode,down);return true;
    }
    @Override public void key(int symbol,boolean down){
        int code=NativeCore.androidKey(symbol);
        if(code!=0)hardware(0,code,down);else if(down)text(new String(Character.toChars((symbol&0xff000000)==0x01000000?symbol&0xffffff:symbol)));
    }
    @Override public void text(String value){if(!closed&&x11.isConnected())x11.text(value);}
    private void releaseInput(){
        if(x11==null)return;for(long key:new ArrayList<>(pressed.keySet()))x11.key((int)(key>>32),(int)key,false);pressed.clear();
        if(desktop!=null){desktop.mouseButton(1,false);desktop.mouseButton(2,false);desktop.mouseButton(4,false);}
    }
    @Override protected void onPause(){handler.removeCallbacks(progress);releaseInput();if(desktop!=null)desktop.suspendInput();super.onPause();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&restoreKeyboard){restoreKeyboard=false;desktop.requestFocus();desktop.post(()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(desktop,InputMethodManager.SHOW_IMPLICIT));}}
    @Override protected void onSaveInstanceState(Bundle state){if(android.os.Build.VERSION.SDK_INT>=30){WindowInsets insets=desktop.getRootWindowInsets();state.putBoolean("keyboard_visible",insets!=null&&insets.isVisible(WindowInsets.Type.ime()));}super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){releaseInput();closed=true;handler.removeCallbacksAndMessages(null);if(x11!=null)x11.close();if(bound)unbindService(binding);super.onDestroy();}
}
