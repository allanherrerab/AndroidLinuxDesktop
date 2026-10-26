package org.debiannoroot.session;

import org.debiannoroot.audio.PulseAudioPlayer;
import org.debiannoroot.runtime.NativeBridge;
import org.debiannoroot.runtime.NativeCore;
import org.debiannoroot.runtime.RootfsImage;
import org.debiannoroot.runtime.SharedStorage;
import org.debiannoroot.runtime.VirglRenderer;
import org.debiannoroot.settings.RegionalConfig;
import org.debiannoroot.settings.RegionalSettings;
import org.debiannoroot.settings.UiText;
import org.debiannoroot.ui.DesktopActivity;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Process;
import android.os.IBinder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DesktopService extends Service {
    public static final String ACTION_START = "org.debiannoroot.START";
    public static final String ACTION_STOP = "org.debiannoroot.STOP";
    public static final String ACTION_STATUS = "org.debiannoroot.STATUS";
    public static final String ACTION_CONNECTED = "org.debiannoroot.CONNECTED";
    private static final String CHANNEL_ID = "debian-session";
    private static final int NOTIFICATION_ID = 22;
    private static final Object INSTALL_LOCK = new Object();

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean stopping;
    private boolean running;
    private volatile IBinder x11;
    private android.content.ServiceConnection x11Connection;
    private boolean x11Bound;
    private PulseAudioPlayer audio;
    private volatile VirglRenderer graphics;

    private void startX11(File rootfs) throws Exception {
        byte[] cookie=new byte[16];new java.security.SecureRandom().nextBytes(cookie);
        File auth=new File(rootfs,"tmp/debian-noroot.Xauthority");
        if(!auth.getParentFile().isDirectory()&&!auth.getParentFile().mkdirs())throw new java.io.IOException("Cannot create X11 authentication folder");
        try(java.io.DataOutputStream output=new java.io.DataOutputStream(new FileOutputStream(auth))) {
            output.writeShort(65535);output.writeShort(0);output.writeShort(1);output.writeByte('0');
            byte[] protocol="MIT-MAGIC-COOKIE-1".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            output.writeShort(protocol.length);output.write(protocol);output.writeShort(cookie.length);output.write(cookie);
        }
        android.system.Os.chmod(auth.getAbsolutePath(),0600);
        java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(1);
        x11Connection=new android.content.ServiceConnection() {
            public void onServiceConnected(android.content.ComponentName name,IBinder binder) { if(stopping)X11Service.stop(binder);else x11=binder;ready.countDown(); }
            public void onServiceDisconnected(android.content.ComponentName name) {
                x11=null;ready.countDown();
                if(!stopping && running) { publishStatus("error","X11 server stopped. See Android logcat; try compatibility drawing in Display settings.");NativeBridge.stopDesktop(); }
            }
            public void onNullBinding(android.content.ComponentName name) { ready.countDown(); }
        };
        new android.os.Handler(getMainLooper()).post(()-> {
            if(stopping){ready.countDown();return;}
            x11Bound=bindService(new Intent(this,X11Service.class),x11Connection,BIND_AUTO_CREATE|BIND_IMPORTANT);
            if(!x11Bound)ready.countDown();
        });
        for(int attempt=0;!ready.await(200,java.util.concurrent.TimeUnit.MILLISECONDS);attempt++) {
            if(stopping)throw new java.io.IOException("Startup stopped");
            if(attempt>=150)throw new java.io.IOException("Timed out binding native X11 server");
        }
        if(stopping)return;
        IBinder server=x11;if(server==null)throw new java.io.IOException("Could not bind native X11 server");
        X11Service.start(server,rootfs,new SessionSettings(this).flag("legacyDrawing",false));
        File socket=new File(rootfs,"tmp/.X11-unix/X0");
        for(int attempt=0;!socket.exists();attempt++) {
            if(stopping)return;
            if(!server.isBinderAlive()||attempt>=150)throw new java.io.IOException("Native X11 server did not become ready. Try compatibility drawing in Display settings and check Android logcat.");
            Thread.sleep(200);
        }
    }
    private void stopX11() {
        IBinder server=x11;x11=null;if(server!=null)X11Service.stop(server);
        if(x11Bound){unbindService(x11Connection);x11Bound=false;}
    }


    @Override protected void attachBaseContext(Context base){super.attachBaseContext(RegionalSettings.localized(base));}

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_CONNECTED.equals(action)) {
            if (running && !stopping) publishStatus("running", "Debian desktop is running.");
            if (!running) stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(action)) {
            stopping = true;
            NativeBridge.stopDesktop();
            publishStatus("stopping", "Stopping Debian session…");
            if (!running) stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (running) return START_NOT_STICKY;
        if (!ACTION_START.equals(action)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        running = true;
        stopping = false;
        NativeBridge.resetSession();

        startForeground(NOTIFICATION_ID, makeNotification("Starting Debian"));
        publishStatus("starting", "Preparing Debian. First start extracts the desktop image…");
        worker.execute(() -> {
            try {
                SessionLogs.begin(getFilesDir());
                File rootfs = new File(getFilesDir(), "rootfs");
                synchronized (INSTALL_LOCK) {
                    if (stopping) return;
                    prepareRootfs(rootfs);
                }
                if (stopping) return;
                if (!new File(rootfs, "usr/bin/xfce4-session").isFile()) {
                    throw new IllegalStateException("This is a base image without XFCE. " +
                            "Import the desktop image distributed with this APK.");
                }
                // Refresh only app-owned launch code; preserve the user's Debian files.
                copyAsset("rootfs/bin/debian-noroot-startx",
                        new File(rootfs, "usr/local/bin/debian-noroot-startx"), true);
                copyAsset("rootfs/bin/debian-noroot-setup-desktop",
                        new File(rootfs, "usr/local/bin/debian-noroot-setup-desktop"), true);
                copyAsset("rootfs/bin/debian-noroot-ensure-audio",
                        new File(rootfs, "usr/local/bin/debian-noroot-ensure-audio"), true);
                copyAsset("rootfs/bin/debian-noroot-setup-audio-panel",
                        new File(rootfs, "usr/local/bin/debian-noroot-setup-audio-panel"), true);
                copyAsset("rootfs/bin/debian-noroot-ensure-locale",new File(rootfs,"usr/local/bin/debian-noroot-ensure-locale"),true);
                copyAsset("rootfs/bin/debian-noroot-ensure-graphics",new File(rootfs,"usr/local/bin/debian-noroot-ensure-graphics"),true);
                copyAsset("rootfs/bin/debian-noroot-probe-graphics",new File(rootfs,"usr/local/bin/debian-noroot-probe-graphics"),true);
                copyAsset("rootfs/install/install-desktop.sh",
                        new File(rootfs, "root/debian-noroot-install-desktop.sh"), true);
                copyAssetTree("rootfs/defaults",
                        new File(rootfs, "usr/share/debian-noroot/desktop-defaults"));
                if (new File(rootfs, "usr/bin/firefox-esr").exists()) {
                    copyAsset("rootfs/bin/debian-noroot-firefox.js",
                            new File(rootfs, "usr/lib/firefox-esr/defaults/pref/debian-noroot.js"), false);
                }
                configureResolver(rootfs);
                configureTimezone(rootfs);
                String guestLanguage=new RegionalSettings(this).guestLanguage();
                java.nio.file.Files.write(new File(rootfs,"etc/default/locale").toPath(),("LANG="+RegionalConfig.locale(guestLanguage)+"\nLANGUAGE="+guestLanguage+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                java.nio.file.Files.write(new File(rootfs,"etc/profile.d/debian-noroot-locale.sh").toPath(),("export LANG='"+RegionalConfig.locale(guestLanguage)+"' LANGUAGE='"+guestLanguage+"' TZ=':/etc/localtime'\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                java.nio.file.Files.write(new File(rootfs,"tmp/debian-noroot-progress").toPath(),"locale_setup\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                if(stopping)return;
                startX11(rootfs);
                SessionSettings settings=new SessionSettings(this);
                boolean virglRequested=settings.value("graphics","software").equals("virgl");
                String guestGraphics="software";
                if(virglRequested && !stopping) {
                    try {
                        graphics=new VirglRenderer(this,rootfs,()->stopping);
                        guestGraphics="virgl";
                    } catch(Exception error) {
                        try(FileOutputStream log=new FileOutputStream(new File(getFilesDir(),"virgl.log"),true)) {
                            log.write(("\nVirGL unavailable; using software rendering: "+error+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        }
                    }
                } else {
                    java.nio.file.Files.write(new File(getFilesDir(),"virgl.log").toPath(),"VirGL is disabled for this session.\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                java.nio.file.Files.write(new File(rootfs,"tmp/debian-noroot-session.conf").toPath(),
                        ("NOROOT_AUDIO="+(settings.flag("audio",true)?"1":"0")+"\nNOROOT_GRAPHICS="+guestGraphics+"\nNOROOT_GRAPHICS_REQUESTED="+(virglRequested?"virgl":"software")+"\n"+RegionalConfig.guestSettings(new RegionalSettings(this).guestLanguage(),new RegionalSettings(this).keyboard())).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                if(settings.flag("audio",true))audio=new PulseAudioPlayer(this,rootfs);
                if (!stopping) {
                    publishStatus("running", "Starting XFCE desktop…");
                    File shared = getExternalFilesDir(null);
                    if (shared == null) shared = new File(getFilesDir(), "shared");
                    if (!shared.exists() && !shared.mkdirs()) {
                        throw new IllegalStateException("Cannot create shared files folder");
                    }
                    File proot = new File(getApplicationInfo().nativeLibraryDir, "libproot.so");
                    String hostStorage=null;
                    if(SharedStorage.allowed(this)) {
                        File storage=new File(rootfs,"storage");
                        if(!storage.isDirectory()&&!storage.mkdirs())throw new IllegalStateException("Cannot create guest storage folder");
                        hostStorage="/storage";
                    }
                    int exitCode = NativeBridge.runDesktop(getFilesDir().getAbsolutePath(),
                            rootfs.getAbsolutePath(), proot.getAbsolutePath(),
                            getApplicationInfo().nativeLibraryDir, shared.getAbsolutePath(),hostStorage);
                    if (!stopping) publishStatus(exitCode == 0 ? "ended" : "error",
                            "Debian session ended (" + exitCode + ")." +
                            (exitCode == 0 ? "" : " See session log for details."));
                }
            } catch (Exception error) {
                if (!stopping) {
                    try(FileOutputStream log=new FileOutputStream(new File(getFilesDir(),"desktop.log"),true)) {
                        log.write(("\nStartup error: "+error+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } catch(Exception ignored) { }
                    publishStatus("error", "Debian could not start: " + error.getMessage());
                }
            } finally {
                VirglRenderer renderer=graphics;graphics=null;if(renderer!=null)renderer.close();
                PulseAudioPlayer playback=audio;audio=null;if(playback!=null)playback.close();
                new android.os.Handler(getMainLooper()).post(() -> {
                    stopX11();
                    running = false;
                    if (stopping) publishStatus("ended", "Debian session stopped.");
                    stopForeground(true);
                    stopSelf();
                });
            }
        });
        return START_NOT_STICKY;
    }

    private void prepareRootfs(File rootfs) throws Exception {
        File previous = new File(getFilesDir(), "rootfs.previous");
        if (!rootfs.exists() && previous.isDirectory() && !previous.renameTo(rootfs)) {
            throw new IllegalStateException("Cannot recover previous Debian installation");
        }
        File marker = new File(rootfs, ".debian-noroot-ready");
        if (marker.isFile()) return;
        publishStatus("starting","Checking external Debian image…");
        File archive=new RootfsImage(this).findVerified();
        publishStatus("starting","Extracting verified Debian image…");
        File staging = new File(getFilesDir(), "rootfs.installing");
        deleteTree(staging);
        String error = NativeBridge.extractRootfs(archive.getAbsolutePath(),
                staging.getAbsolutePath());
        if (error != null && !error.isEmpty()) throw new IllegalStateException(error);
        if (stopping) { deleteTree(staging); return; }
        addDebianUser(staging, Process.myUid());
        File sdcard = new File(staging, "sdcard");
        if (!sdcard.exists() && !sdcard.mkdirs()) throw new IllegalStateException("Cannot create /sdcard");
        File oldRoot = new File(getFilesDir(), "rootfs.previous");
        deleteTree(oldRoot);
        if (!new File(staging, ".debian-noroot-ready").createNewFile()) {
            throw new IllegalStateException("Cannot mark rootfs initialization");
        }
        if (rootfs.exists() && !rootfs.renameTo(oldRoot)) throw new IllegalStateException("Cannot stage prior rootfs");
        if (!staging.renameTo(rootfs)) {
            if (oldRoot.exists()) oldRoot.renameTo(rootfs);
            throw new IllegalStateException("Cannot activate rootfs");
        }
        deleteTree(oldRoot);
    }

    private void copyAsset(String name, File destination, boolean executable) throws Exception {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create " + parent);
        }
        try (InputStream input = getAssets().open(name);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        }
        if (executable && !destination.setExecutable(true, true)) {
            throw new IllegalStateException("Cannot mark runtime executable");
        }
    }

    private void deleteTree(File path) throws java.io.IOException {
        NativeCore.discardRootfs(getFilesDir().getAbsolutePath(),path.getName());
    }

    private void copyAssetTree(String source, File destination) throws Exception {
        String[] entries = getAssets().list(source);
        if (entries != null && entries.length > 0) {
            if (!destination.exists() && !destination.mkdirs()) {
                throw new IllegalStateException("Cannot create desktop defaults folder");
            }
            for (String entry : entries) copyAssetTree(source + "/" + entry, new File(destination, entry));
        } else {
            copyAsset(source, destination, false);
        }
    }

    private void addDebianUser(File rootfs, int uid) throws Exception {
        NativeCore.initializeGuest(rootfs.getAbsolutePath(),uid,System.currentTimeMillis()/86400000L);
    }

    private void configureTimezone(File rootfs) throws Exception {
        NativeCore.configureTimezone(rootfs.getAbsolutePath(),new RegionalSettings(this).timezone());
    }

    private void configureResolver(File rootfs) throws Exception {
        ConnectivityManager manager=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network=manager==null?null:manager.getActiveNetwork();
        LinkProperties properties=manager==null||network==null?null:manager.getLinkProperties(network);
        java.util.ArrayList<String> servers=new java.util.ArrayList<>();
        if(properties!=null)for(java.net.InetAddress address:properties.getDnsServers())
            servers.add(address.getHostAddress());
        NativeCore.configureResolver(rootfs.getAbsolutePath(),android.system.Os.uname().nodename,
                servers.toArray(new String[0]));
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                UiText.get(this,"Debian session"), NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification makeNotification(String text) {
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID);
        return builder.setContentTitle("Debian Noroot")
                .setContentText(UiText.get(this,text))
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, DesktopActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .addAction(new Notification.Action.Builder(null, UiText.get(this,"Stop"),
                        PendingIntent.getService(this, 1, new Intent(this, DesktopService.class)
                                .setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT |
                                PendingIntent.FLAG_IMMUTABLE)).build())
                .build();
    }

    private void updateNotification(String text) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, makeNotification(text));
    }

    private void publishStatus(String phase, String text) {
        text=UiText.get(this,text);
        getSharedPreferences("session", MODE_PRIVATE).edit()
                .putString("phase", phase).putString("status", text).apply();
        updateNotification(text);
        Intent status = new Intent(ACTION_STATUS).setPackage(getPackageName());
        status.putExtra("status", text);
        sendBroadcast(status);
    }

    @Override
    public void onDestroy() {
        stopping = true;
        NativeBridge.stopDesktop();
        VirglRenderer renderer=graphics;graphics=null;if(renderer!=null)renderer.close();
        PulseAudioPlayer playback=audio;audio=null;if(playback!=null)playback.close();
        stopX11();
        stopForeground(true);
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
