package org.debiannoroot.ui;

import org.debiannoroot.runtime.DisplayGeometry;
import org.debiannoroot.runtime.SharedStorage;
import org.debiannoroot.session.SessionSettings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import java.util.Arrays;

public final class SettingsActivity extends LocalizedActivity {
    private SessionSettings settings;
    private LinearLayout page;
    private TextView storageStatus;
    @Override public void onCreate(Bundle state) {super.onCreate(state);settings=new SessionSettings(this);render();}
    @Override protected void onResume() {super.onResume();updateStorageStatus();}
    private void updateStorageStatus() {
        if(storageStatus!=null)storageStatus.setText(t(SharedStorage.allowed(this)?"Device file access is allowed.":"Device file access is not allowed."));
    }
    private void heading(String title) {TextView text=new TextView(this);text.setText(t(title));text.setTextSize(21);text.setPadding(0,28,0,8);page.addView(text);}
    private void button(String title,Runnable action) {Button b=new Button(this);b.setText(t(title));b.setOnClickListener(v->action.run());page.addView(b);}
    private void flag(String title,String key,boolean fallback) {
        Switch value=new Switch(this);value.setText(t(title));value.setPadding(0,12,0,12);value.setChecked(settings.flag(key,fallback));
        value.setOnCheckedChangeListener((v,on)->settings.preferences.edit().putBoolean(key,on).apply());page.addView(value);
    }
    private void choice(String title,String key,String fallback,String[] labels,String[] values) {
        String current=settings.value(key,fallback);int selected=Arrays.asList(values).indexOf(current);
        button(t(title)+": "+t(labels[Math.max(0,selected)]),()->new AlertDialog.Builder(this).setTitle(t(title))
                .setSingleChoiceItems(t(labels),selected,(dialog,index)->{settings.preferences.edit().putString(key,values[index]).apply();dialog.dismiss();render();})
                .setNegativeButton(t("Cancel"),null).show());
    }
    private void number(String title,String key,int fallback,int min,int max) {
        button(t(title)+": "+settings.number(key,fallback,min,max),()-> {
            EditText value=new EditText(this);value.setInputType(2);value.setText(String.valueOf(settings.number(key,fallback,min,max)));
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle(t(title)+" ("+min+"–"+max+")").setView(value)
                    .setPositiveButton(t("Save"),null).setNegativeButton(t("Cancel"),null).create();dialog.show();
            dialog.getButton(-1).setOnClickListener(v->{try{int n=Integer.parseInt(value.getText().toString());if(n<min||n>max)throw new NumberFormatException();settings.preferences.edit().putInt(key,n).apply();dialog.dismiss();render();}catch(NumberFormatException ex){value.setError(tf("Enter a value between %1$d and %2$d",min,max));}});
        });
    }
    private void indexed(String title,String key,int fallback,String[] labels) {
        int selected=settings.number(key,fallback,0,labels.length-1);
        button(t(title)+": "+t(labels[selected]),()->new AlertDialog.Builder(this).setTitle(t(title)).setSingleChoiceItems(t(labels),selected,(d,i)->{settings.preferences.edit().putInt(key,i).apply();d.dismiss();render();}).setNegativeButton(t("Cancel"),null).show());
    }
    private void resolution() {
        String[] options={"800 × 600","1024 × 768","1280 × 720","1280 × 800","1600 × 900","1920 × 1080","Custom…"};
        new AlertDialog.Builder(this).setTitle(t("Guest display resolution")).setItems(t(options),(dialog,index)-> {
            if(index<6) {String[] parts=options[index].split(" × ");saveResolution(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]));return;}
            LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);EditText width=new EditText(this),height=new EditText(this);
            width.setInputType(2);height.setInputType(2);width.setText(String.valueOf(settings.width()));height.setText(String.valueOf(settings.height()));width.setHint(t("Width"));height.setHint(t("Height"));fields.addView(width);fields.addView(height);
            AlertDialog custom=new AlertDialog.Builder(this).setTitle(t("Custom resolution")).setView(fields).setPositiveButton(t("Save"),null).setNegativeButton(t("Cancel"),null).create();custom.show();
            custom.getButton(-1).setOnClickListener(v->{try{int w=Integer.parseInt(width.getText().toString()),h=Integer.parseInt(height.getText().toString());if(!DisplayGeometry.validResolution(w,h))throw new NumberFormatException();saveResolution(w,h);custom.dismiss();}catch(NumberFormatException error){width.setError(t("320–7680 × 240–4320, up to 16 megapixels"));}});
        }).show();
    }
    private void saveResolution(int width,int height){settings.preferences.edit().putInt("width",width).putInt("height",height).apply();render();}
    private void remap() {
        AlertDialog prompt=new AlertDialog.Builder(this).setTitle(t("Remap physical key")).setMessage(t("Press a hardware key (except Home or Power).")).setNegativeButton(t("Cancel"),null).create();
        prompt.setOnKeyListener((dialog,code,event)-> {
            if(event.getAction()!=android.view.KeyEvent.ACTION_DOWN)return true;
            String[] names={"Default","Escape","Tab","Ctrl","Alt","Shift","Space","Delete","F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12","Left mouse","Right mouse","Middle mouse"};
            int[] symbols={0,0xff1b,0xff09,0xffe3,0xffe9,0xffe1,32,0xffff,0xffbe,0xffbf,0xffc0,0xffc1,0xffc2,0xffc3,0xffc4,0xffc5,0xffc6,0xffc7,0xffc8,0xffc9,0x02000001,0x02000004,0x02000002};
            dialog.dismiss();new AlertDialog.Builder(this).setTitle(android.view.KeyEvent.keyCodeToString(code)).setItems(t(names),(d,i)->{
                SharedPreferences.Editor e=settings.preferences.edit();if(i==0)e.remove("map_"+code);else e.putInt("map_"+code,symbols[i]);e.apply();}).show();return true;
        });prompt.show();
    }
    private void render() {
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(24,24,24,48);
        ScrollView scroll=new ScrollView(this);scroll.addView(page);setContentView(scroll);
        page.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(24+insets.getSystemWindowInsetLeft(),24+insets.getSystemWindowInsetTop(),24+insets.getSystemWindowInsetRight(),48+insets.getSystemWindowInsetBottom());return insets;});
        button("Done",this::finish);heading("Display settings");
        button(t("Resolution")+": "+settings.width()+" × "+settings.height(),this::resolution);
        choice("Scaling","scaling","fit",new String[]{"Fit","Stretch","Integer scale (crop if needed)","Custom"},new String[]{"fit","stretch","integer","custom"});
        number("Custom scale (%)","scale",100,25,400);
        flag("Linear video filtering","filtering",true);flag("Keep 4:3 screen aspect ratio (Fit)","aspect43",false);
        flag("Hide system navigation buttons / immersive mode","immersive",true);flag("Keep screen awake","keepAwake",true);
        choice("Screen orientation","orientation","auto",new String[]{"Automatic","Landscape","Portrait"},new String[]{"auto","landscape","portrait"});
        number("TV / overscan border (%)","border",0,0,10);number("Refresh rate (Hz)","refresh",60,15,120);
        flag("Compatibility drawing (restart desktop to apply)","legacyDrawing",false);
        choice("Guest graphics (restart desktop to apply)","graphics","software",new String[]{"Software rendering","VirGL (Android GPU)"},new String[]{"software","virgl"});
        heading("Android files");
        storageStatus=new TextView(this);page.addView(storageStatus);updateStorageStatus();
        TextView storageHelp=new TextView(this);storageHelp.setText(t("Allow shared storage access to read device files at /storage/emulated/0 and removable volumes under /storage. Android restrictions still apply. All-files access also allows changes to shared files. Restart the desktop after granting access. /sdcard remains the app shared folder."));page.addView(storageHelp);
        TextView storageWarning=new TextView(this);storageWarning.setText(t("Save guest work and stop the session before changing file access: Android may restart the app."));page.addView(storageWarning);
        button("Manage device file access",()->SharedStorage.request(this));
        heading("Mouse emulation");
        choice("Mouse movement","mouseMode","relative",new String[]{"Relative (laptop / touchpad)","Absolute (touch screen)"},new String[]{"relative","absolute"});
        indexed("Relative mouse speed","mouseSpeed",2,new String[]{"Very slow","Slow","Medium","Fast","Very fast"});
        indexed("Relative mouse acceleration","mouseAccel",0,new String[]{"None","Slow","Medium","Fast"});
        choice("Left mouse click","leftClick","tap_hold",new String[]{"Tap","Tap or hold","Normal (touch-down / drag)","Hold at the same spot","Second finger","Touch pressure","Touch near cursor","Joystick / D-pad center"},new String[]{"tap","tap_hold","normal","hold","second","pressure","near","dpad"});
        choice("Right mouse click","rightClick","hold_two",new String[]{"Hold or two-finger tap","Hold","Second-finger tap","Menu / mouse key","Touch pressure","Disabled"},new String[]{"hold_two","hold","second","menu","pressure","none"});
        indexed("Holding timeout","holdDelay",1,new String[]{"0.3 sec","0.5 sec","0.7 sec","1 sec","1.5 sec"});
        number("Touch pressure threshold (%)","pressure",70,10,100);
        indexed("Magnification","magnifier",0,new String[]{"Off","2× cursor-following zoom","Floating magnifying glass (Android 9+)"});
        flag("Hide Android mouse pointer (use guest cursor)","hideSystemPointer",true);
        flag("Filter stylus / hover jitter","hoverFilter",true);flag("Move pointer when hovering","hover",true);
        flag("Move mouse with joystick / trackball","joystick",false);flag("Click with joystick / D-pad center","dpadClick",false);
        indexed("Joystick speed","joystickSpeed",2,new String[]{"Very slow","Slow","Medium","Fast","Very fast"});
        indexed("Joystick acceleration","joystickAccel",0,new String[]{"None","Slow","Medium","Fast"});
        flag("Control mouse with gyroscope","gyroscope",false);
        indexed("Gyroscope sensitivity","gyroSpeed",2,new String[]{"Very slow","Slow","Medium","Fast","Very fast"});
        heading("Keyboard & controls");button("Language, keyboard & timezone",()->startActivity(new android.content.Intent(this,SetupActivity.class)));
        number("Desktop zoom (%)","zoom",100,100,400);
        flag("Show F1–F12 key row","functionKeys",false);flag("Fullscreen desktop","fullscreen",false);button("Remap physical keys",this::remap);
        button("On-screen keys",()->{
            String[] keys=DesktopActivity.EXTRA_KEYS;java.util.Set<String> current=settings.preferences.getStringSet("extraKeys",new java.util.HashSet<>(Arrays.asList(DesktopActivity.DEFAULT_KEYS)));
            boolean[] selected=new boolean[keys.length];for(int i=0;i<keys.length;i++)selected[i]=current.contains(keys[i]);
            new AlertDialog.Builder(this).setTitle(t("On-screen keys")).setMultiChoiceItems(t(keys),selected,(d,i,on)->selected[i]=on).setPositiveButton(t("Save"),(d,which)->{java.util.Set<String> saved=new java.util.HashSet<>();for(int i=0;i<keys.length;i++)if(selected[i])saved.add(keys[i]);settings.preferences.edit().putStringSet("extraKeys",saved).apply();}).setNegativeButton(t("Cancel"),null).show();
        });
        number("On-screen key size (sp)","keySize",14,10,26);
        number("On-screen controls opacity (%)","controlsOpacity",100,20,100);
        choice("On-screen controls theme","controlsTheme","system",new String[]{"System","Dark","Light"},new String[]{"system","dark","light"});
        heading("PulseAudio playback");flag("Enable guest audio (restart desktop to apply)","audio",true);
        number("Playback volume (%)","volume",100,0,100);number("Audio buffer (ms)","audioLatency",200,20,500);
        heading("Reset");button("Reset display, mouse & audio settings",()->new AlertDialog.Builder(this).setTitle(t("Reset settings?")).setMessage(t("Debian files and imported images are preserved.")).setPositiveButton(t("Reset"),(d,i)->{settings.preferences.edit().clear().apply();render();}).setNegativeButton(t("Cancel"),null).show());
    }
}
