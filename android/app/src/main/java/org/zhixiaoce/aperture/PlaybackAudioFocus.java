package org.zhixiaoce.aperture;

import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;

/** API26+ adapter. No microphone, assistant replacement, background service or delayed gain. */
final class PlaybackAudioFocus implements PlaybackSession.Focus {
    private final AudioManager manager;
    private final Handler main;
    private AudioFocusRequest request;
    private long token;
    PlaybackAudioFocus(AudioManager manager, Handler main) { this.manager = manager; this.main = main; }
    static AudioAttributes attributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build();
    }
    @Override public boolean request(long next, Runnable lost) {
        if (manager == null) return false;
        abandon(token);
        token = next;
        request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes()).setWillPauseWhenDucked(true)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(change -> {
                if (token == next && (change == AudioManager.AUDIOFOCUS_LOSS
                    || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)) lost.run();
                // AUDIOFOCUS_GAIN is deliberately not a playback command.
            }, main).build();
        return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }
    @Override public void abandon(long expected) {
        if (expected != token) return;
        AudioFocusRequest old = request; request = null; token = 0;
        if (old != null && manager != null) manager.abandonAudioFocusRequest(old);
    }
}
