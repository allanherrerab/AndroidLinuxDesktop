package org.debiannoroot;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.File;
import java.io.InputStream;

/** Guest PulseAudio monitor -> private PCM socket -> Android AudioTrack. */
final class PulseAudioPlayer implements AutoCloseable {
    private final Context context;
    private final Thread worker;
    private volatile boolean closed;
    private volatile LocalSocket socket;
    private volatile AudioTrack track;
    private volatile boolean focus=true;
    private final AudioManager manager;
    private final AudioFocusRequest focusRequest;
    PulseAudioPlayer(Context context,File root) {
        this.context=context;
        manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focusRequest=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change->{focus=change>0;AudioTrack current=track;
                    if(current!=null)try{current.setVolume(focus?volume():0);}catch(IllegalStateException ignored){}}).build();
        worker=new Thread(()->play(root,attributes),"PulseAudio playback");worker.start();
    }
    private float volume() {return new SessionSettings(context).number("volume",100,0,100)/100f;}
    private void play(File root,AudioAttributes attributes) {
        while(!closed) {
            LocalSocket candidate=new LocalSocket();socket=candidate;
            AudioTrack output=null;
            try {
                candidate.connect(new LocalSocketAddress(new File(root,"tmp/debian-noroot-audio.sock").getAbsolutePath(),LocalSocketAddress.Namespace.FILESYSTEM));
                int minimum=AudioTrack.getMinBufferSize(48000,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
                int latency=new SessionSettings(context).number("audioLatency",80,20,300);
                output=new AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                        .setBufferSizeInBytes(Math.max(Math.max(minimum,4096),48000*4*latency/1000)).setTransferMode(AudioTrack.MODE_STREAM).build();
                if(output.getState()!=AudioTrack.STATE_INITIALIZED)throw new IllegalStateException("Android audio output could not be initialized");
                focus=manager.requestAudioFocus(focusRequest)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
                track=output;output.setVolume(focus?volume():0);output.play();
                InputStream input=candidate.getInputStream();byte[] bytes=new byte[8196];int count,carry=0;
                while(!closed && (count=input.read(bytes,carry,8192))!=-1) {
                    int total=count+carry,complete=total&~3;
                    output.setVolume(focus?volume():0);
                    int offset=0;
                    while(!closed && offset<complete) {
                        int written=output.write(bytes,offset,complete-offset,AudioTrack.WRITE_BLOCKING);
                        if(written<=0)throw new java.io.IOException("Android PCM write failed: "+written);
                        offset+=written;
                    }
                    carry=total-complete;System.arraycopy(bytes,complete,bytes,0,carry);
                }
            } catch(Exception error) {
                if(!closed)android.util.Log.d("DebianAudio","Waiting for guest PulseAudio: "+error.getMessage());
            } finally {
                track=null;if(output!=null){try {output.pause();output.flush();}catch(Exception ignored){}output.release();}
                manager.abandonAudioFocusRequest(focusRequest);
                try {candidate.close();}catch(Exception ignored){}
            }
            if(!closed)try {Thread.sleep(500);}catch(InterruptedException ignored){return;}
        }
    }
    @Override public void close() {
        closed=true;LocalSocket current=socket;if(current!=null)try{current.close();}catch(Exception ignored){}
        AudioTrack currentTrack=track;if(currentTrack!=null)try{currentTrack.pause();}catch(Exception ignored){}
        worker.interrupt();
    }
}
