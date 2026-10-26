package org.debiannoroot.ui;

import org.debiannoroot.R;
import org.debiannoroot.runtime.RootfsImage;
import org.debiannoroot.session.DesktopService;
import org.debiannoroot.settings.RegionalSettings;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends LocalizedActivity {
    private TextView status;
    private volatile boolean importing;
    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (status != null) status.setText(t(intent.getStringExtra("status")));
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        getWindow().setStatusBarColor(Color.rgb(32, 32, 32));
        getWindow().setNavigationBarColor(Color.rgb(32, 32, 32));
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        }

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(24), dp(32), dp(24), dp(24));
        page.setBackgroundColor(Color.rgb(242, 242, 242));
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(24) + insets.getSystemWindowInsetLeft(),
                    dp(32) + insets.getSystemWindowInsetTop(),
                    dp(24) + insets.getSystemWindowInsetRight(),
                    dp(24) + insets.getSystemWindowInsetBottom());
            return insets;
        });

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(24);
        title.setTextColor(Color.rgb(35, 35, 35));
        page.addView(title, matchWrap());

        status = new TextView(this);
        status.setText(R.string.ready);
        status.setTextSize(15);
        status.setTextColor(Color.DKGRAY);
        status.setPadding(0, dp(18), 0, dp(22));
        page.addView(status, matchWrap());

        Button start = new Button(this);
        start.setText(R.string.start);
        start.setOnClickListener(view -> startSession());
        page.addView(start, matchWrap());

        Button stop = new Button(this);
        stop.setText(R.string.stop);
        stop.setOnClickListener(view -> stopSession());
        page.addView(stop, matchWrap());

        Button settings=new Button(this);settings.setText(R.string.settings);
        settings.setOnClickListener(view->startActivity(new Intent(this,SettingsActivity.class)));
        page.addView(settings,matchWrap());
        Button region=new Button(this);region.setText(t("Language, keyboard & timezone"));region.setOnClickListener(v->startActivity(new Intent(this,SetupActivity.class)));page.addView(region,matchWrap());
        Button image=new Button(this);image.setText(R.string.import_image);
        image.setOnClickListener(view->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),41));
        page.addView(image,matchWrap());

        Button log = new Button(this);
        log.setText(R.string.view_log);
        log.setOnClickListener(view -> showLog());
        page.addView(log, matchWrap());

        TextView note = new TextView(this);
        note.setText(R.string.session_note);
        if(new java.io.File(getFilesDir(),"rootfs/.debian-noroot-ready").isFile() && !new java.io.File(getFilesDir(),"rootfs/usr/bin/pulseaudio").isFile())
            note.append("\n"+t("First launch will install missing audio packages automatically. Internet is required; setup progress appears in the session log."));
        note.setTextSize(13);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(24), 0, 0);
        page.addView(note, matchWrap());

        android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.setFillViewport(true);scroll.addView(page);
        setContentView(scroll,new ViewGroup.LayoutParams(-1,-1));
        if(!new RegionalSettings(this).complete() && state==null)startActivity(new Intent(this,SetupActivity.class));
    }

    @Override
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(DesktopService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            // API 26–32 has no NOT_EXPORTED flag. A signature permission protects
            // this legacy registration; API 33+ uses the explicit flag above.
            registerReceiver(statusReceiver, filter, "org.debiannoroot.permission.INTERNAL", null);
        }
        status.setText(t(getSharedPreferences("session", MODE_PRIVATE).getString("status",
                getString(R.string.ready))));
    }

    @Override
    protected void onStop() {
        unregisterReceiver(statusReceiver);
        super.onStop();
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=41||result!=RESULT_OK||data==null||data.getData()==null)return;
        if(importing){android.widget.Toast.makeText(this,R.string.import_in_progress,android.widget.Toast.LENGTH_SHORT).show();return;}
        importing=true;
        android.net.Uri uri=data.getData();
        status.setText(R.string.importing_image);
        new Thread(()-> {
            String message;
            try {new RootfsImage(getApplicationContext()).importFrom(uri);message=t("Image verified and ready. Tap Start. Existing Debian files are preserved.");}
            catch(Exception error){message=t("Image import failed")+": "+error.getMessage();}
            importing=false;
            final String text=message;
            getSharedPreferences("session",MODE_PRIVATE).edit().putString("status",text).apply();
            runOnUiThread(()->{if(!isDestroyed())status.setText(text);});
        },"Debian image import").start();
    }

    private void startSession() {
        if(!new RegionalSettings(this).complete()){startActivity(new Intent(this,SetupActivity.class));return;}
        if(importing){status.setText(R.string.wait_for_import);return;}
        status.setText(R.string.starting);
        // The service owns the persisted phase. Reopening an already running
        // DesktopActivity may reuse its connection without another CONNECTED
        // event, so the launcher must not replace "running" with "starting".
        Intent intent = new Intent(this, DesktopService.class)
                .setAction(DesktopService.ACTION_START);
        startForegroundService(intent);
        startActivity(new Intent(this, DesktopActivity.class));
    }

    private void stopSession() {
        startService(new Intent(this, DesktopService.class).setAction(DesktopService.ACTION_STOP));
        status.setText(R.string.stopping);
    }

    private void showLog() {
        StringBuilder text = new StringBuilder();
        for(String name:new String[]{"desktop.log","x11.log"}) {
            java.io.File file = new java.io.File(getFilesDir(), name);
            try (java.io.RandomAccessFile log = new java.io.RandomAccessFile(file, "r")) {
                long offset = Math.max(0, log.length() - 65536);
                log.seek(offset);
                byte[] bytes = new byte[(int)(log.length() - offset)];
                log.readFully(bytes);
                text.append("\n=== ").append(name).append(" ===\n")
                        .append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            } catch (java.io.IOException ignored) { }
        }
        if(text.length()==0)text.append(t("No session log yet."));
        TextView content = new TextView(this);
        content.setText(text);
        content.setTextIsSelectable(true);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(content);
        new android.app.AlertDialog.Builder(this).setTitle(R.string.log_title)
                .setView(scroll).setPositiveButton(R.string.close, null).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
