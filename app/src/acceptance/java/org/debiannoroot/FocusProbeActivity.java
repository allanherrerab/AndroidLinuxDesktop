package org.debiannoroot;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.widget.Button;

/** Foreground competing media client, present only in the acceptance APK. */
public final class FocusProbeActivity extends Activity {
    private AudioManager audio;
    private AudioFocusRequest request;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener(change -> {}).build();
        int result = audio.requestAudioFocus(request);
        Button release = new Button(this);
        release.setText("Focus request: " + result + ". Release audio focus");
        release.setOnClickListener(view -> finish());
        setContentView(release);
    }

    @Override protected void onDestroy() {
        if (audio != null && request != null) audio.abandonAudioFocusRequest(request);
        super.onDestroy();
    }
}
