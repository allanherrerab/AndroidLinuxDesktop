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
    private final SessionSettings settings;
    private final Thread worker;
    private volatile boolean closed;
    private volatile LocalSocket socket;
    private volatile AudioTrack track;
    private volatile boolean focus=true;
    private final AudioManager manager;
    private final AudioFocusRequest focusRequest;
    PulseAudioPlayer(Context context,File root) {
        settings=new SessionSettings(context);
        manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focusRequest=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change->focus=change>0).build();
        worker=new Thread(()->play(root,attributes),"PulseAudio playback");worker.start();
    }
    private float volume() {return focus?settings.number("volume",100,0,100)/100f:0;}
    private void write(AudioTrack output,byte[] bytes,int length) throws java.io.IOException {
        int offset=0;
        while(!closed && offset<length) {
            int count=output.write(bytes,offset,length-offset,AudioTrack.WRITE_BLOCKING);
            if(count<=0)throw new java.io.IOException("Android PCM write failed: "+count);
            offset+=count;
        }
    }
    private void play(File root,AudioAttributes attributes) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        while(!closed) {
            LocalSocket candidate=new LocalSocket();socket=candidate;
            AudioTrack output=null;boolean requestedFocus=false;
            try {
                candidate.connect(new LocalSocketAddress(new File(root,"tmp/debian-noroot-audio.sock").getAbsolutePath(),LocalSocketAddress.Namespace.FILESYSTEM));
                int minimum=AudioTrack.getMinBufferSize(48000,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
                int latency=settings.number("audioLatency",200,20,500);
                output=new AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                        .setBufferSizeInBytes(Math.max(Math.max(minimum,4096),48000*4*latency/1000)).setTransferMode(AudioTrack.MODE_STREAM).build();
                if(output.getState()!=AudioTrack.STATE_INITIALIZED)throw new IllegalStateException("Android audio output could not be initialized");
                focus=manager.requestAudioFocus(focusRequest)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
                requestedFocus=true;track=output;
                float appliedVolume=volume();output.setVolume(appliedVolume);
                InputStream input=candidate.getInputStream();
                // Fill half the actual device buffer before playback starts. This
                // absorbs PRoot scheduling bursts without starting an empty track.
                int prefill=(output.getBufferSizeInFrames()*4/2)&~3;
                byte[] bytes=new byte[Math.max(8196,prefill)];int filled=0;
                while(!closed && filled<prefill) {
                    int count=input.read(bytes,filled,prefill-filled);
                    if(count<0)throw new java.io.EOFException("PulseAudio closed during buffering");
                    filled+=count;
                }
                if(closed)break;
                write(output,bytes,prefill);output.play();
                android.util.Log.i("DebianAudio","Playing 48 kHz stereo; buffer="+output.getBufferSizeInFrames()+" frames");
                int count,carry=0;
                while(!closed && (count=input.read(bytes,carry,8192))!=-1) {
                    int total=count+carry,complete=total&~3;
                    float desiredVolume=volume();
                    if(desiredVolume!=appliedVolume){output.setVolume(desiredVolume);appliedVolume=desiredVolume;}
                    write(output,bytes,complete);
                    carry=total-complete;System.arraycopy(bytes,complete,bytes,0,carry);
                }
            } catch(Exception error) {
                if(!closed)android.util.Log.d("DebianAudio","Waiting for guest PulseAudio: "+error.getMessage());
            } finally {
                track=null;if(output!=null){try {output.pause();output.flush();}catch(Exception ignored){}output.release();}
                if(requestedFocus)manager.abandonAudioFocusRequest(focusRequest);
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
